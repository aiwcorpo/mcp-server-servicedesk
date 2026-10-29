package pl.aiwcorpo.mcp.servicedesk.tools;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.platform.error.McpErrorMapper.SafeError;
import pl.aiwcorpo.mcp.platform.error.ToolErrorCode;
import pl.aiwcorpo.mcp.platform.error.ToolErrorMapping;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketNotFoundException;

/** Domain exceptions of this server as C10 errors; everything else falls through to the starter's defaults. */
@Component
public class ServicedeskToolErrors implements ToolErrorMapping {

    @Override
    public Optional<SafeError> map(Throwable ex) {
        if (ex instanceof TicketNotFoundException) {
            return Optional.of(new SafeError(ToolErrorCode.NOT_FOUND_OR_DENIED.code(),
                    ToolErrorCode.NOT_FOUND_OR_DENIED_MESSAGE));
        }
        return Optional.empty();
    }
}
