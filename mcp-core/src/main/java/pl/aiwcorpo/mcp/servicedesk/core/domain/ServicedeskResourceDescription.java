package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.cedar.entities.Scopes;

/**
 * The queue and the ticket as the policy sees them (C7, entity-builder.md). Ids and the queue of a ticket are the
 * ones the service desk reported, not the ones the caller named. The classification is left out when the service
 * desk reports none, so such a queue is not restricted. Both types carry the one scope of their queue
 * ({@code ServiceDesk::Queue/<id>}): the policies grant changes with {@code principal in resource.scopes}, and the
 * forbid the platform generates for the service account (which sees every queue) lets a user through only inside the
 * queues granted to them.
 */
public final class ServicedeskResourceDescription {

    /**
     * A queue id no mapping gives to a caller who holds no queue scope at all; the resource built from it has no scope,
     * so the service account rule judges it as a queue the caller is not granted.
     */
    private static final String UNGRANTED_QUEUE = "ungranted";

    private ServicedeskResourceDescription() {
    }

    public static ResolvedResource of(Queue queue) {
        return queueOf(queue.id(), queue.classification());
    }

    public static ResolvedResource of(ClassifiedTicket classified) {
        return ticketOf(classified.ticket().id(), of(classified.queue()));
    }

    /** The queue as its id alone describes it: no classification, so the grant can be judged before the service desk is asked. */
    public static ResolvedResource probeOfQueue(String queueId) {
        return queueOf(queueId, null);
    }

    /**
     * The ticket for a caller with no queue scope: in a queue nothing grants, because the id of a ticket does not name its
     * queue.
     */
    public static ResolvedResource probeOfTicketOutsideScopes(String ticketId) {
        return ResolvedResource.of(ServicedeskResourceModel.TICKET_TYPE, ticketId, Map.of("id", ticketId))
                .withParent(ResolvedResource.of(ServicedeskResourceModel.QUEUE_TYPE, UNGRANTED_QUEUE,
                        Map.of("id", UNGRANTED_QUEUE)).withScopes(List.of()))
                .withScopes(List.of());
    }

    private static ResolvedResource queueOf(String id, List<String> classification) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", id);
        if (classification != null) {
            attributes.put("classification", classification);
        }
        return ResolvedResource.of(ServicedeskResourceModel.QUEUE_TYPE, id, attributes)
                .withScopes(List.of(Scopes.id(ServicedeskResourceModel.QUEUE_TYPE, id)));
    }

    private static ResolvedResource ticketOf(String id, ResolvedResource queue) {
        return ResolvedResource.of(ServicedeskResourceModel.TICKET_TYPE, id, Map.of("id", id))
                .withParent(queue)
                .withScopes(queue.scopes());
    }
}
