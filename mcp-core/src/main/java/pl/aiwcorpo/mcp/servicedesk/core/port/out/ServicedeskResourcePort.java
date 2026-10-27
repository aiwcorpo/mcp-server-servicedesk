package pl.aiwcorpo.mcp.servicedesk.core.port.out;

import java.util.Optional;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskRecord;

/** Outbound port to the backend system, implemented in mcp-integration. */
public interface ServicedeskResourcePort {

    /** Empty when the backend does not show the record to the caller, whether it exists or not. */
    Optional<ServicedeskRecord> findById(String recordId);
}
