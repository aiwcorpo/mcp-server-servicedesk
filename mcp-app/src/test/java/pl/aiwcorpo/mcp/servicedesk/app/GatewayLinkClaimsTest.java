package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * The gateway puts {@code vs} and an empty {@code vsc} into every JWT of a call made through a link (C2, CC-027);
 * the server has to accept them. Narrowing by link scopes is not implemented here (M2).
 */
class GatewayLinkClaimsTest extends ServerTestSupport {

    @Test
    void aCallThroughALinkWithoutNarrowingIsServed() {
        try (McpSyncClient client = clientAs(
                user -> user.username(CALLER).virtualServer("it-helpdesk", Map.of()),
                SERVICE_KEY, BackendCredential.Type.SERVICE_ACCOUNT)) {
            McpSchema.CallToolResult result = call(client, "get_ticket", Map.of("ticketId", "IT-1"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"id\":\"IT-1\"");
        }
    }
}
