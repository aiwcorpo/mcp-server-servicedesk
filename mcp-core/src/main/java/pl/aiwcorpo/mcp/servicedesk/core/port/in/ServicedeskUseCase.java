package pl.aiwcorpo.mcp.servicedesk.core.port.in;

import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskRecord;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface ServicedeskUseCase {

    /**
     * @throws pl.aiwcorpo.mcp.servicedesk.core.domain.RecordNotFoundException when the record does not exist or
     *         the caller's backend account may not see it
     */
    ServicedeskRecord getRecord(String recordId);
}
