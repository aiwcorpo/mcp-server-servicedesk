package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Service-account mode without a queue list: fail-closed, every call refused, the service desk never asked. */
@TestPropertySource(properties = "servicedesk.access.allowed-queues=")
class NoAllowedQueuesTest extends ServerTestSupport {

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void everyToolAnswersNotFoundOrDenied() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "IT-1")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")));
            assertNotFoundOrDenied(call(client, "search_tickets", Map.of("queue", "IT", "text", "x")));
        }
        try (McpSyncClient client = clientWithCredential("Bearer user-token-of-jkowalski", USER_SECRET)) {
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "IT-1")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }
}
