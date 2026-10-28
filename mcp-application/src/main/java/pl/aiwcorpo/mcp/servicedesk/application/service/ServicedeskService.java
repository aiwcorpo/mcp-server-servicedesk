package pl.aiwcorpo.mcp.servicedesk.application.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.QueueAccessPolicy;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketNotFoundException;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

/**
 * Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring.
 *
 * <p>With a service account the service desk answers for the platform, not for the user, so the queue checks
 * here are the access boundary. They run on the queue the service desk reports for the ticket at the time of
 * the call (a ticket moved to another queue is judged by its new queue), never on what the caller claims. The
 * lookups of a queue and a ticket are what the resolvers of the policy call, so a ticket the server does not serve
 * never reaches a tool: the starter ends the call with {@code not_found_or_denied} before the method runs.
 */
public class ServicedeskService implements ServicedeskUseCase {

    private final ServicedeskResourcePort resource;
    private final QueueAccessPolicy policy;

    public ServicedeskService(ServicedeskResourcePort resource, QueueAccessPolicy policy) {
        this.resource = resource;
        this.policy = policy;
    }

    @Override
    public boolean serviceAccountCall() {
        return policy.serviceAccountCall(resource.callerIsServiceAccount());
    }

    @Override
    public Optional<Queue> findQueue(String queueId) {
        if (!policy.permits(queueId, resource.callerIsServiceAccount())) {
            return Optional.empty();
        }
        return resource.findQueue(queueId).filter(queue -> queue.id().equals(queueId));
    }

    @Override
    public Map<String, ClassifiedTicket> findTickets(Collection<String> ticketIds) {
        boolean serviceAccount = resource.callerIsServiceAccount();
        Map<String, ClassifiedTicket> found = new LinkedHashMap<>();
        if (!policy.servesAnyQueue(serviceAccount)) {
            return found;
        }
        Map<String, Optional<Queue>> queues = new HashMap<>();
        for (String ticketId : ticketIds) {
            Optional<Ticket> ticket = resource.findTicket(ticketId);
            // A different id means the service desk resolved an alias or a merge; the gateway's policy saw only the
            // requested id, so such an answer is not served.
            if (ticket.isEmpty() || !ticket.get().id().equals(ticketId)
                    || !policy.permits(ticket.get().queue(), serviceAccount)) {
                continue;
            }
            Optional<Queue> queue = queues.computeIfAbsent(ticket.get().queue(),
                    id -> resource.findQueue(id).filter(q -> q.id().equals(id)));
            queue.ifPresent(q -> found.put(ticketId, new ClassifiedTicket(ticket.get(), q)));
        }
        return found;
    }

    @Override
    public TicketSearch searchTickets(String queue, String text, Predicate<String> currentQueueAllowed) {
        boolean serviceAccount = resource.callerIsServiceAccount();
        if (!policy.permits(queue, serviceAccount)) {
            throw new TicketNotFoundException();
        }
        List<TicketSummary> hits = resource.searchTickets(queue, text, SEARCH_LIMIT)
                .orElseThrow(TicketNotFoundException::new);
        // The service desk's search may lag behind moves or match across queues; only tickets it reports in the
        // requested queue are handed out, and the policy then judges each of them by the ticket itself.
        List<TicketSummary> inQueue = hits.stream()
                .filter(hit -> hit.queue().equals(queue) && policy.permits(hit.queue(), serviceAccount))
                .toList();
        if (policy.serviceAccountCall(serviceAccount)) {
            Map<String, ClassifiedTicket> current = findTickets(inQueue.stream().map(TicketSummary::id).toList());
            inQueue = inQueue.stream().filter(hit -> {
                ClassifiedTicket ticket = current.get(hit.id());
                return ticket != null && currentQueueAllowed.test(ticket.ticket().queue());
            }).toList();
        }
        boolean truncated = hits.size() > SEARCH_LIMIT || inQueue.size() > SEARCH_LIMIT;
        return new TicketSearch(queue, inQueue.stream().limit(SEARCH_LIMIT).toList(), truncated);
    }

    /**
     * The ticket was resolved and judged before this runs (the id is a {@code @ResourceRef}); it is read once more
     * right before the write, because a ticket moved in between would otherwise get the note.
     */
    @Override
    public NoteReceipt addNote(String ticketId, String body, Predicate<String> currentQueueAllowed) {
        ClassifiedTicket ticket = findTickets(List.of(ticketId)).get(ticketId);
        if (ticket == null || !currentQueueAllowed.test(ticket.ticket().queue())) {
            throw new TicketNotFoundException();
        }
        String noteId = resource.addNote(ticketId, body).orElseThrow(TicketNotFoundException::new);
        return new NoteReceipt(ticketId, noteId.isEmpty() ? null : noteId);
    }
}
