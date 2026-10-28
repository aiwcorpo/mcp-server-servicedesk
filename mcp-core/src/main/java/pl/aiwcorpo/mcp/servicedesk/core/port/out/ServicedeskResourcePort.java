package pl.aiwcorpo.mcp.servicedesk.core.port.out;

import java.util.List;
import java.util.Optional;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;

/**
 * Outbound port to the service desk, implemented in mcp-integration. "Empty" always means the service desk did
 * not show the object to this credential, whether it exists or not.
 */
public interface ServicedeskResourcePort {

    /**
     * Hits of a text search in one queue, as the service desk lists them, at most {@code limit} plus one so
     * the caller can tell the list was cut. Hits whose id or queue is not a valid identifier are dropped.
     */
    Optional<List<TicketSummary>> searchTickets(String queue, String text, int limit);

    Optional<Ticket> findTicket(String ticketId);

    /** The queue with its classification, empty when the service desk does not show it to this credential. */
    Optional<Queue> findQueue(String queueId);

    /** @return the note id the service desk reported (empty string when none), empty when the ticket is hidden */
    Optional<String> addNote(String ticketId, String body);

    /** The credential of the current call is labelled {@code service-account} by the gateway (C3). */
    boolean callerIsServiceAccount();
}
