package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.HashMap;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskResourceModel;

/**
 * The queue and the ticket as the policy sees them (C7, entity-builder.md). Ids and the queue of a ticket are the
 * ones the service desk reported, not the ones the caller named. The classification is left out when the service
 * desk reports none, so such a queue is not restricted.
 */
final class ServicedeskResourceDescription {

    private ServicedeskResourceDescription() {
    }

    static ResolvedResource of(Queue queue) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", queue.id());
        if (queue.classification() != null) {
            attributes.put("classification", queue.classification());
        }
        return ResolvedResource.of(ServicedeskResourceModel.QUEUE_TYPE, queue.id(), attributes);
    }

    static ResolvedResource of(ClassifiedTicket classified) {
        return ResolvedResource.of(ServicedeskResourceModel.TICKET_TYPE, classified.ticket().id(),
                Map.of("id", classified.ticket().id())).withParent(of(classified.queue()));
    }
}
