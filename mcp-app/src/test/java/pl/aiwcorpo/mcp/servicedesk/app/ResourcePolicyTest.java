package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * The policy (C7) on the queue and the ticket, with the example policies of the contracts: an object is judged by what
 * the service desk says about it now, and every refusal looks like a missing object. The server's configuration opens
 * no queue (CC-163): the grants of the bundle are the only source.
 */
class ResourcePolicyTest extends ServerTestSupport {

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void aTicketInARestrictedQueueIsNotFoundOrDeniedLikeAMissingOne() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult missing = call(client, "get_ticket", Map.of("ticketId", "NOPE-1"));
            McpSchema.CallToolResult restricted = call(client, "get_ticket", Map.of("ticketId", "SEC-1"));
            McpSchema.CallToolResult note = call(client, "add_note", Map.of("ticketId", "SEC-1", "body", "x"));

            assertNotFoundOrDenied(restricted);
            assertNotFoundOrDenied(note);
            assertThat(text(restricted)).isEqualTo(text(missing)).isEqualTo(text(note));
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aRestrictedQueueCannotBeSearchedAndTheServiceDeskIsNotAsked() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult restricted = call(client, "search_tickets", Map.of("queue", "SEC", "text", "x"));
            McpSchema.CallToolResult missing = call(client, "search_tickets", Map.of("queue", "GONE", "text", "x"));

            assertNotFoundOrDenied(restricted);
            assertThat(text(restricted)).isEqualTo(text(missing));
        }
        assertThat(BACKEND.requests()).noneMatch(request -> request.contains("/tickets?"));
    }

    @Test
    void aTicketMovedIntoARestrictedQueueIsJudgedByTheQueueItIsInNow() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertThat(call(client, "get_ticket", Map.of("ticketId", "IT-1")).isError()).isFalse();

            BACKEND.move("IT-1", "SEC");

            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "IT-1")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")));
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aTicketFromAQueueTheCallerIsNotGrantedIsNotFoundOrDenied() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult outside = call(client, "get_ticket", Map.of("ticketId", "FIN-7"));
            McpSchema.CallToolResult missing = call(client, "get_ticket", Map.of("ticketId", "NOPE-1"));

            assertNotFoundOrDenied(outside);
            assertThat(text(outside)).isEqualTo(text(missing));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "FIN-7", "body", "x")));
        }
        assertThat(BACKEND.requests()).as("FIN-7 is read to learn its queue and judged there; nothing is written")
                .noneMatch(request -> request.startsWith("POST"));
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aQueueWithoutAClassificationIsNotRestricted() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertThat(call(client, "get_ticket", Map.of("ticketId", "IT-1")).isError())
                    .as("IT reports no classification").isFalse();
            assertThat(call(client, "get_ticket", Map.of("ticketId", "OPS-5")).isError())
                    .as("OPS reports Internal").isFalse();
        }
    }

    @Test
    void aCallerWithoutTheGroupOfTheToolGetsAccessDeniedAndNothingReachesTheServiceDesk() {
        try (McpSyncClient client = clientAs(user -> user.groups(List.of("mcp-nobody")), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            for (McpSchema.CallToolResult result : List.of(
                    call(client, "get_ticket", Map.of("ticketId", "IT-1")),
                    call(client, "search_tickets", Map.of("queue", "IT", "text", "x")),
                    call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")))) {
                assertThat(result.isError()).isTrue();
                assertThat(text(result)).contains("\"error\":\"access_denied\"");
            }
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void aReaderCannotWrite() {
        try (McpSyncClient client = clientAs(user -> user.groups(List.of("mcp-servicedesk-read")), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertThat(text(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x"))))
                    .contains("\"error\":\"access_denied\"");
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aServiceDeskThatFailsLooksLikeAMissingTicketToTheServiceAccount() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult result = call(client, "get_ticket", Map.of("ticketId", "IT-500"));

            assertNotFoundOrDenied(result);
        }
    }

    @Test
    void aLinkThatNarrowsToOneQueueHidesTheTicketsAndQueuesOfTheOthers() {
        try (McpSyncClient client = clientAs(
                user -> user.virtualServer("it-only", Map.of("servicedeskQueues", List.of("IT"))), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertThat(call(client, "get_ticket", Map.of("ticketId", "IT-1")).isError()).isFalse();
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "OPS-5")));
            McpSchema.CallToolResult outsideLink = call(client, "search_tickets", Map.of("queue", "OPS", "text", "x"));
            McpSchema.CallToolResult missing = call(client, "search_tickets", Map.of("queue", "NOPE", "text", "x"));
            assertNotFoundOrDenied(outsideLink);
            assertThat(text(outsideLink)).as("CC-146 c: the queue outside the link is not told from a missing one")
                    .isEqualTo(text(missing));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "OPS-5", "body", "x")));
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aSearchThroughALinkDoesNotReturnTicketsOutsideIt() {
        try (McpSyncClient client = clientAs(
                user -> user.virtualServer("it-only", Map.of("servicedeskQueues", List.of("IT"))), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            McpSchema.CallToolResult result = call(client, "search_tickets", Map.of("queue", "IT", "text", "printer"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"id\":\"IT-1\"", "\"id\":\"IT-2\"")
                    .as("STALE-1 is in OPS, outside the link, although the index lists it under IT")
                    .doesNotContain("STALE-1");
        }
    }

    @Test
    void aQueueTheCallerIsNotGrantedAnswersLikeAMissingQueueAndIsNotSearched() {
        try (McpSyncClient client = clientAs(
                user -> user.groups(List.of("mcp-servicedesk-read", "mcp-servicedesk-queue-IT")), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            McpSchema.CallToolResult granted = call(client, "search_tickets", Map.of("queue", "IT", "text", "printer"));
            BACKEND.reset();
            McpSchema.CallToolResult notGranted = call(client, "search_tickets", Map.of("queue", "OPS", "text", "printer"));
            McpSchema.CallToolResult missing = call(client, "search_tickets", Map.of("queue", "NOPE", "text", "printer"));

            assertThat(text(granted)).contains("\"id\":\"IT-1\"");
            assertNotFoundOrDenied(notGranted);
            assertThat(text(notGranted)).isEqualTo(text(missing));
        }
        assertThat(BACKEND.requests()).noneMatch(request -> request.contains("/tickets?"));
    }

    @Test
    void aSearchHitWhoseCurrentQueueIsNotGrantedIsLeftOut() {
        try (McpSyncClient client = clientAs(
                user -> user.groups(List.of("mcp-servicedesk-read", "mcp-servicedesk-queue-IT")), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            McpSchema.CallToolResult result = call(client, "search_tickets", Map.of("queue", "IT", "text", "printer"));

            assertThat(text(result)).contains("\"id\":\"IT-1\"", "\"id\":\"IT-2\"")
                    .as("STALE-1 is in OPS now, which the caller is not granted, whatever the index says")
                    .doesNotContain("STALE-1");
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "STALE-1")));
        }
    }

    @Test
    void aCallerGrantedNoQueueIsRefusedTheSearchLikeAMissingQueue() {
        try (McpSyncClient client = clientAs(user -> user.groups(List.of("mcp-servicedesk-read")), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertNotFoundOrDenied(call(client, "search_tickets", Map.of("queue", "IT", "text", "printer")));
        }
        assertThat(BACKEND.requests()).noneMatch(request -> request.contains("/tickets?"));
    }

    @Test
    void theServiceAccountReadsAndWritesOnlyInQueuesTheCallerIsGranted() {
        List<String> itOnly = List.of("mcp-servicedesk-read", "mcp-servicedesk-write", "mcp-servicedesk-queue-IT");
        try (McpSyncClient client = clientAs(user -> user.groups(itOnly), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            McpSchema.CallToolResult missing = call(client, "get_ticket", Map.of("ticketId", "NOPE-1"));
            McpSchema.CallToolResult otherQueue = call(client, "get_ticket", Map.of("ticketId", "OPS-5"));
            McpSchema.CallToolResult note = call(client, "add_note", Map.of("ticketId", "OPS-5", "body", "x"));

            assertNotFoundOrDenied(otherQueue);
            assertNotFoundOrDenied(note);
            assertThat(text(otherQueue)).isEqualTo(text(missing));
            assertThat(call(client, "get_ticket", Map.of("ticketId", "IT-1")).isError()).isFalse();
            assertThat(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")).isError()).isFalse();
        }
        assertThat(BACKEND.noteBodies()).hasSize(1).allMatch(note -> note.startsWith("IT-1 "));

        List<String> withOps = List.of("mcp-servicedesk-read", "mcp-servicedesk-write", "mcp-servicedesk-queue-OPS");
        try (McpSyncClient client = clientAs(user -> user.groups(withOps), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertThat(call(client, "get_ticket", Map.of("ticketId", "OPS-5")).isError()).isFalse();
            assertThat(call(client, "add_note", Map.of("ticketId", "OPS-5", "body", "x")).isError()).isFalse();
        }
    }

    @Test
    void aCallerWithoutAnyQueueGroupGetsNothingThroughTheServiceAccount() {
        try (McpSyncClient client = clientAs(
                user -> user.groups(List.of("mcp-servicedesk-read", "mcp-servicedesk-write")), SERVICE_KEY,
                BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "IT-1")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")));
            assertNotFoundOrDenied(call(client, "search_tickets", Map.of("queue", "IT", "text", "x")));
        }
        assertThat(BACKEND.requests()).as("no queue scope: refused before the resolver, the service desk is not asked")
                .isEmpty();
    }
}
