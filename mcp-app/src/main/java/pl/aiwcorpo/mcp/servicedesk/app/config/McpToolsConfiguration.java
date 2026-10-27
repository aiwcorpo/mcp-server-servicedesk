package pl.aiwcorpo.mcp.servicedesk.app.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import pl.aiwcorpo.mcp.servicedesk.tools.ServicedeskTool;
import pl.aiwcorpo.mcp.servicedesk.tools.ServicedeskToolSchemas;

/**
 * The tool set of the server, as an explicit list: the MCP server exposes only tools registered through a
 * {@link ToolCallbackProvider} (an {@code @Tool} bean alone answers "Unknown tool"), and a reviewed list
 * keeps the public surface from growing by accident.
 *
 * <p>Callbacks are built per method rather than by {@code MethodToolCallbackProvider} so that each tool gets
 * its hand-written input schema with length limits and patterns; they stay {@link MethodToolCallback}s, which
 * is what the starter reads {@code @ToolRisk} from.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    ToolCallbackProvider mcpTools(ServicedeskTool servicedeskTool) {
        return ToolCallbackProvider.from(callbacks(servicedeskTool, ServicedeskToolSchemas.INPUT_SCHEMAS));
    }

    static List<ToolCallback> callbacks(Object toolObject, Map<String, String> inputSchemas) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Method method : ReflectionUtils.getDeclaredMethods(AopUtils.getTargetClass(toolObject))) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }
            String name = ToolUtils.getToolName(method);
            String schema = inputSchemas.get(name);
            if (schema == null) {
                throw new IllegalStateException("tool " + name + " has no input schema in ServicedeskToolSchemas");
            }
            callbacks.add(MethodToolCallback.builder()
                    .toolDefinition(ToolDefinitions.builder(method).inputSchema(schema).build())
                    .toolMethod(method)
                    .toolObject(toolObject)
                    .build());
        }
        if (callbacks.size() != inputSchemas.size()) {
            throw new IllegalStateException("ServicedeskToolSchemas lists a tool that does not exist");
        }
        return callbacks;
    }
}
