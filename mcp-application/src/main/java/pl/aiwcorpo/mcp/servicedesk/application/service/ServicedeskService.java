package pl.aiwcorpo.mcp.servicedesk.application.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketNotFoundException;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

/**
 * Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring.
 *
 * <p>The service desk answers for the platform's service account, not for the user, so what the caller may see is
 * decided outside this class: by the grants of the policy bundle, which the resolvers' callers and the
 * tools apply (the server's configuration opens no queue). Queue checks here run on the queue the service desk reports
 * for the ticket at the time of the call (a ticket moved to another queue is judged by its new queue), never on what
 * the caller claims. The lookups of a queue and a ticket are what the resolvers of the policy call, so a ticket in a
 * queue the caller is not granted never reaches a tool: the starter ends the call with {@code not_found_or_denied}.
 */
public class ServicedeskService implements ServicedeskUseCase {

    private final ServicedeskResourcePort resource;

    public ServicedeskService(ServicedeskResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public Optional<Queue> findQueue(String queueId) {
        if (!Identifiers.isQueueId(queueId)) {
            return Optional.empty();
        }
        return resource.findQueue(queueId).filter(queue -> queue.id().equals(queueId));
    }

    @Override
    public Map<String, ClassifiedTicket> findTickets(Collection<String> ticketIds, Predicate<String> queueAllowed) {
        Map<String, ClassifiedTicket> found = new LinkedHashMap<>();
        Map<String, Optional<Queue>> queues = new HashMap<>();
        for (String ticketId : ticketIds) {
            Optional<Ticket> ticket = resource.findTicket(ticketId);
            // A different id means the service desk resolved an alias or a merge; the gateway's policy saw only the
            // requested id, so such an answer is not served.
            if (ticket.isEmpty() || !ticket.get().id().equals(ticketId) || !queueAllowed.test(ticket.get().queue())) {
                continue;
            }
            Optional<Queue> queue = queues.computeIfAbsent(ticket.get().queue(),
                    id -> resource.findQueue(id).filter(q -> q.id().equals(id)));
            queue.ifPresent(q -> found.put(ticketId, new ClassifiedTicket(ticket.get(), q)));
        }
        return found;
    }

    @Override
    public TicketSearch searchTickets(String queue, String text, Predicate<ClassifiedTicket> currentlyAllowed) {
        if (!Identifiers.isQueueId(queue)) {
            throw new TicketNotFoundException();
        }
        List<TicketSummary> hits = resource.searchTickets(queue, text, SEARCH_LIMIT)
                .orElseThrow(TicketNotFoundException::new);
        // The service desk's search may lag behind moves or match across queues; only tickets it reports in the
        // requested queue are handed out, and each of them must still be allowed as the service desk describes it now.
        List<TicketSummary> inQueue = hits.stream().filter(hit -> hit.queue().equals(queue)).toList();
        Map<String, ClassifiedTicket> current = findTickets(inQueue.stream().map(TicketSummary::id).toList(), anyQueue -> true);
        inQueue = inQueue.stream().filter(hit -> {
            ClassifiedTicket ticket = current.get(hit.id());
            return ticket != null && currentlyAllowed.test(ticket);
        }).toList();
        boolean truncated = hits.size() > SEARCH_LIMIT || inQueue.size() > SEARCH_LIMIT;
        return new TicketSearch(queue, inQueue.stream().limit(SEARCH_LIMIT).toList(), truncated);
    }

    /**
     * The ticket was resolved and judged before this runs (the id is a {@code @ResourceRef}); it is read once more
     * right before the write, because a ticket moved in between would otherwise get the note.
     */
    @Override
    public NoteReceipt addNote(String ticketId, String body, Predicate<ClassifiedTicket> currentlyAllowed) {
        ClassifiedTicket ticket = findTickets(List.of(ticketId), queue -> true).get(ticketId);
        if (ticket == null || !currentlyAllowed.test(ticket)) {
            throw new TicketNotFoundException();
        }
        String noteId = resource.addNote(ticketId, body).orElseThrow(TicketNotFoundException::new);
        return new NoteReceipt(ticketId, noteId.isEmpty() ? null : noteId);
    }
}
