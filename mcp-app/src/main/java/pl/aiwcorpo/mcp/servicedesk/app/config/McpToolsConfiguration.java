package pl.aiwcorpo.mcp.servicedesk.app.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;
import pl.aiwcorpo.mcp.servicedesk.tools.ServicedeskTool;

/**
 * The tool set of the server, as an explicit list: the MCP server exposes only tools registered through a
 * {@link ToolCallbackProvider} (an {@code @Tool} bean alone answers "Unknown tool"), and a reviewed list
 * keeps the public surface from growing by accident. The starter adds the argument limits and
 * {@code additionalProperties: false} to each input schema and checks every call against it.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    ToolCallbackProvider mcpTools(ServicedeskTool servicedeskTool) {
        return SchemaLimitedToolCallbacks.of(servicedeskTool);
    }
}
