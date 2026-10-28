package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/** {@code search_tickets}: the queue is mandatory, the server builds the request, hits stay in the queue. */
class SearchTicketsToolTest extends ServerTestSupport {

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void findsTicketsOfTheQueueOnly() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult result = call(client, "search_tickets", Map.of("queue", "IT", "text", "printer"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result))
                    .contains("\"queue\":\"IT\"", "\"id\":\"IT-1\"", "\"id\":\"IT-2\"", "\"id\":\"MOVED-3\"",
                            "\"subject\":{\"kind\":\"untrusted-data\",\"format\":\"text/plain\","
                                    + "\"text\":\"Printer on floor 3 (IT-1)\",\"truncated\":false}")
                    .as("hits of another queue (allowed or not) and a hit without a queue are dropped")
                    .doesNotContain("FIN-7", "OPS-5", "IT-9")
                    .doesNotContain("jkowalski@corp.internal");
            assertThat(text(result)).as("STALE-1 is listed under IT by a lagging index but is in OPS, which is served")
                    .contains("\"id\":\"STALE-1\"");
            assertThat(BACKEND.requests()).containsExactly("GET /api/v1/queues/IT",
                    "GET /api/v1/queues/IT/tickets?q=printer&limit=21",
                    "GET /api/v1/tickets/IT-1", "GET /api/v1/queues/IT", "GET /api/v1/tickets/IT-2",
                    "GET /api/v1/tickets/MOVED-3", "GET /api/v1/tickets/STALE-1", "GET /api/v1/queues/OPS");
            assertThat(BACKEND.authorizations()).hasSize(8).containsOnly(SERVICE_KEY);
        }
    }

    @Test
    void theTextIsAValueNotAQuery() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult result = call(client, "search_tickets",
                    Map.of("queue", "IT", "text", "x&limit=1000&queue=FIN #/../FIN"));

            assertThat(result.isError()).isFalse();
            assertThat(BACKEND.requests().get(1))
                    .startsWith("GET /api/v1/queues/IT/tickets?q=x%26limit%3D1000%26queue%3DFIN%20%23%2F..%2FFIN")
                    .endsWith("&limit=21");
        }
    }

    @Test
    void anEmptyResultIsNotAnError() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult result = call(client, "search_tickets", Map.of("queue", "IT", "text", "nothing"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"tickets\":[]");
        }
    }

    @Test
    void withoutCredentialAsksToConnect() {
        try (McpSyncClient client = clientWithCredential(null, BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertCredentialRequired(call(client, "search_tickets", Map.of("queue", "IT", "text", "printer")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void credentialRejectedByTheBackendAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential(StubBackend.EXPIRED_CREDENTIAL, BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertCredentialRequired(call(client, "search_tickets", Map.of("queue", "IT", "text", "printer")));
        }
    }

    @Test
    void aQueueTheServiceDeskDoesNotKnowIsNotFoundOrDenied() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertNotFoundOrDenied(call(client, "search_tickets", Map.of("queue", "GONE", "text", "printer")));
        }
    }

    @Test
    void invalidArgumentsAreRejectedBeforeTheBackend() {
        try (McpSyncClient client = serviceAccountClient()) {
            for (String queue : new String[] {"IT/../FIN", "IT,FIN", "*", "", "IT FIN", "q".repeat(65)}) {
                assertInvalidArgument(call(client, "search_tickets", Map.of("queue", queue, "text", "x")), queue);
            }
            for (String text : new String[] {"", "t".repeat(201)}) {
                assertInvalidArgument(call(client, "search_tickets", Map.of("queue", "IT", "text", text)), text);
            }
            for (String text : new String[] {"   ", "a\u0000b", "a\u202Eb"}) {
                assertThat(text(call(client, "search_tickets", Map.of("queue", "IT", "text", text))))
                        .as("blank text passes the schema and is stopped by the tool, control characters by the starter")
                        .contains("\"error\":\"invalid_argument\"");
            }
            assertInvalidArgument(call(client, "search_tickets", Map.of("text", "printer")), "");
            assertInvalidArgument(call(client, "search_tickets", Map.of("queue", "IT", "text", "x", "jql", "1=1")), "");
        }
        assertThat(BACKEND.requests()).as("the blank text got as far as the queue lookup and no further")
                .containsExactly("GET /api/v1/queues/IT");
    }
}
