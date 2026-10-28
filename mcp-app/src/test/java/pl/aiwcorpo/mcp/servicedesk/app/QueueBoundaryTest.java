package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * With the service account the service desk shows everything, so the server is the access boundary
 * (allowed queues IT, OPS, GONE in {@link ServerTestSupport}). Every way of reaching a ticket outside them must
 * give exactly the answer of a ticket that does not exist, and no note may be written.
 */
class QueueBoundaryTest extends ServerTestSupport {

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void ticketsOutsideTheAllowedQueuesAnswerLikeMissingOnes() {
        List<String> answers = new ArrayList<>();
        try (McpSyncClient client = serviceAccountClient()) {
            for (String ticketId : new String[] {"NOPE-1", "FIN-7", "ALIAS-1", "NOQ-1", "IT-403"}) {
                McpSchema.CallToolResult read = call(client, "get_ticket", Map.of("ticketId", ticketId));
                McpSchema.CallToolResult note = call(client, "add_note", Map.of("ticketId", ticketId, "body", "x"));
                assertNotFoundOrDenied(read);
                assertNotFoundOrDenied(note);
                answers.add(text(read));
                answers.add(text(note));
            }
        }
        assertThat(answers).containsOnly(NOT_FOUND_OR_DENIED);
        assertThat(BACKEND.noteBodies()).isEmpty();
        assertThat(BACKEND.requests()).noneMatch(request -> request.startsWith("POST"));
    }

    @Test
    void aTicketMovedOutOfAnAllowedQueueIsNoLongerServed() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertThat(call(client, "get_ticket", Map.of("ticketId", "MOVED-3")).isError()).isFalse();

            BACKEND.move("MOVED-3", "FIN");

            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "MOVED-3")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "MOVED-3", "body", "x")));
            assertThat(text(call(client, "search_tickets", Map.of("queue", "IT", "text", "printer"))))
                    .as("the stale search index still lists it under IT").doesNotContain("MOVED-3");
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aTicketMovedIntoAnAllowedQueueIsServed() {
        BACKEND.move("FIN-7", "OPS");
        try (McpSyncClient client = serviceAccountClient()) {
            assertThat(text(call(client, "get_ticket", Map.of("ticketId", "FIN-7")))).contains("\"queue\":\"OPS\"");
        }
    }

    @Test
    void searchInAQueueOutsideTheListNeverReachesTheServiceDesk() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult otherQueue = call(client, "search_tickets", Map.of("queue", "FIN", "text", "x"));
            McpSchema.CallToolResult caseVariant = call(client, "search_tickets", Map.of("queue", "it", "text", "x"));
            McpSchema.CallToolResult missingQueue = call(client, "search_tickets", Map.of("queue", "GONE", "text", "x"));

            assertNotFoundOrDenied(otherQueue);
            assertNotFoundOrDenied(caseVariant);
            assertThat(text(otherQueue)).isEqualTo(text(missingQueue));
        }
        assertThat(BACKEND.requests()).containsExactly("GET /api/v1/queues/GONE");
    }

    @Test
    void aUserCredentialDoesNotWidenTheServiceAccountMode() {
        try (McpSyncClient client = clientWithCredential("Bearer user-token-of-jkowalski", BackendCredential.Type.USER_SECRET)) {
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "FIN-7")));
            assertNotFoundOrDenied(call(client, "search_tickets", Map.of("queue", "FIN", "text", "x")));
        }
    }
}
