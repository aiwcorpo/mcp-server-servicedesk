package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code user-secret} mode without a queue list: the user's own token decides what the service desk shows. A
 * credential the gateway labels {@code service-account} is still held to the (empty) queue list.
 */
@TestPropertySource(properties = {"servicedesk.access.credential-mode=user-secret", "servicedesk.access.allowed-queues="})
class UserSecretModeTest extends ServerTestSupport {

    private static final String USER_TOKEN = "Bearer user-token-of-jkowalski";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void theUsersTokenReachesAnyQueueTheServiceDeskShowsIt() {
        try (McpSyncClient client = clientWithCredential(USER_TOKEN, USER_SECRET)) {
            assertThat(text(call(client, "get_ticket", Map.of("ticketId", "FIN-7")))).contains("\"queue\":\"FIN\"");
            assertThat(text(call(client, "search_tickets", Map.of("queue", "FIN", "text", "x")))).contains("FIN-7");
            assertThat(call(client, "add_note", Map.of("ticketId", "FIN-7", "body", "x")).isError()).isFalse();
        }
        assertThat(BACKEND.authorizations()).containsOnly(USER_TOKEN);
    }

    @Test
    void hiddenTicketsStillLookLikeMissingOnes() {
        try (McpSyncClient client = clientWithCredential(USER_TOKEN, USER_SECRET)) {
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "IT-403")));
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "ALIAS-1")));
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "NOQ-1")));
        }
    }

    @Test
    void aServiceAccountCredentialIsRefusedWithoutAQueueList() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertNotFoundOrDenied(call(client, "get_ticket", Map.of("ticketId", "IT-1")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")));
            assertNotFoundOrDenied(call(client, "search_tickets", Map.of("queue", "IT", "text", "x")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }
}
