package pl.aiwcorpo.mcp.servicedesk.application.service;

import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskRecord;
import pl.aiwcorpo.mcp.servicedesk.core.domain.RecordNotFoundException;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

/** Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring. */
public class ServicedeskService implements ServicedeskUseCase {

    private final ServicedeskResourcePort resource;

    public ServicedeskService(ServicedeskResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public ServicedeskRecord getRecord(String recordId) {
        return resource.findById(recordId).orElseThrow(RecordNotFoundException::new);
    }
}
