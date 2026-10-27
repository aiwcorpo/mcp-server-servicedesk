package pl.aiwcorpo.mcp.servicedesk.application.service;

import java.util.List;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
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
 * the call (a ticket moved to another queue is judged by its new queue), never on what the caller claims.
 */
public class ServicedeskService implements ServicedeskUseCase {

    private final ServicedeskResourcePort resource;
    private final QueueAccessPolicy policy;

    public ServicedeskService(ServicedeskResourcePort resource, QueueAccessPolicy policy) {
        this.resource = resource;
        this.policy = policy;
    }

    @Override
    public TicketSearch searchTickets(String queue, String text) {
        boolean serviceAccount = resource.callerIsServiceAccount();
        if (!policy.permits(queue, serviceAccount)) {
            throw new TicketNotFoundException();
        }
        List<TicketSummary> hits = resource.searchTickets(queue, text, SEARCH_LIMIT)
                .orElseThrow(TicketNotFoundException::new);
        // The service desk's search may lag behind moves or match across queues; only tickets it reports in the
        // requested queue are handed out.
        List<TicketSummary> inQueue = hits.stream()
                .filter(hit -> hit.queue().equals(queue) && policy.permits(hit.queue(), serviceAccount))
                .toList();
        boolean truncated = hits.size() > SEARCH_LIMIT || inQueue.size() > SEARCH_LIMIT;
        return new TicketSearch(queue, inQueue.stream().limit(SEARCH_LIMIT).toList(), truncated);
    }

    @Override
    public Ticket getTicket(String ticketId) {
        return visibleTicket(ticketId);
    }

    @Override
    public NoteReceipt addNote(String ticketId, String body) {
        visibleTicket(ticketId);
        String noteId = resource.addNote(ticketId, body).orElseThrow(TicketNotFoundException::new);
        return new NoteReceipt(ticketId, noteId.isEmpty() ? null : noteId);
    }

    private Ticket visibleTicket(String ticketId) {
        boolean serviceAccount = resource.callerIsServiceAccount();
        if (!policy.servesAnyQueue(serviceAccount)) {
            throw new TicketNotFoundException();
        }
        Ticket ticket = resource.findTicket(ticketId).orElseThrow(TicketNotFoundException::new);
        // A different id means the service desk resolved an alias or a merge; the gateway's policy saw only the
        // requested id, so such an answer is not served.
        if (!ticket.id().equals(ticketId) || !policy.permits(ticket.queue(), serviceAccount)) {
            throw new TicketNotFoundException();
        }
        return ticket;
    }
}
