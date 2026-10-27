package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** What a client sees in tools/list: names, risk hints and input schemas with limits. */
class ToolDefinitionsTest extends ServerTestSupport {

    private static final String ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";

    @Test
    void exactlyTheThreeToolsWithTheirRisk() {
        Map<String, McpSchema.Tool> tools = tools();

        assertThat(tools).containsOnlyKeys("search_tickets", "get_ticket", "add_note");
        assertThat(tools.get("search_tickets").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("get_ticket").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("add_note").annotations().readOnlyHint()).isFalse();
    }

    @Test
    void schemasAreClosedAndCarryTheContractPatternsAndLimits() {
        Map<String, McpSchema.Tool> tools = tools();

        assertSchema(tools.get("search_tickets"), List.of("queue", "text"));
        assertSchema(tools.get("get_ticket"), List.of("ticketId"));
        assertSchema(tools.get("add_note"), List.of("ticketId", "body"));
        assertThat(property(tools.get("search_tickets"), "queue")).containsEntry("pattern", ID_PATTERN)
                .containsEntry("maxLength", 64);
        assertThat(property(tools.get("search_tickets"), "text")).containsEntry("maxLength", 200)
                .containsEntry("minLength", 1);
        assertThat(property(tools.get("get_ticket"), "ticketId")).containsEntry("pattern", ID_PATTERN);
        assertThat(property(tools.get("add_note"), "body")).containsEntry("maxLength", 10000);
    }

    @Test
    void descriptionsDescribeAndDoNotInstruct() {
        for (McpSchema.Tool tool : tools().values()) {
            assertThat(tool.description()).doesNotContainIgnoringCase("always").doesNotContainIgnoringCase("must")
                    .doesNotContainIgnoringCase("first call").doesNotContainIgnoringCase("you should");
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertSchema(McpSchema.Tool tool, List<String> required) {
        assertThat(tool.inputSchema()).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat((Map<String, Object>) tool.inputSchema().get("properties"))
                .containsOnlyKeys(required.toArray(String[]::new));
        assertThat((List<String>) tool.inputSchema().get("required")).containsExactlyInAnyOrderElementsOf(required);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(McpSchema.Tool tool, String name) {
        return (Map<String, Object>) ((Map<String, Object>) tool.inputSchema().get("properties")).get(name);
    }

    private Map<String, McpSchema.Tool> tools() {
        try (McpSyncClient client = serviceAccountClient()) {
            return client.listTools().tools().stream().collect(Collectors.toMap(McpSchema.Tool::name, t -> t));
        }
    }
}
