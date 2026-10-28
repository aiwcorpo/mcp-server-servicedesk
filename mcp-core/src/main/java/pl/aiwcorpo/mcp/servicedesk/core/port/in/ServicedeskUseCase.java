package pl.aiwcorpo.mcp.servicedesk.core.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;

/**
 * What the server can do, in business terms. Implemented in mcp-application, called by mcp-tools and by the resource
 * resolvers. The lookups return "empty" for a ticket or queue that does not exist, that the service desk hides, or
 * that this server does not serve (a service account sees more than the platform allows).
 */
public interface ServicedeskUseCase {

    /** Search hits limit; fixed because it is part of the tool description. */
    int SEARCH_LIMIT = 20;

    /** The queue as the policy judges it, for a queue id the caller named. */
    Optional<Queue> findQueue(String queueId);

    /**
     * The tickets the caller may be shown, with their queues, keyed by the requested id; absent for the ones that are
     * missing, hidden or in a queue this server does not serve. The queue of a result is looked up once per queue.
     */
    Map<String, ClassifiedTicket> findTickets(Collection<String> ticketIds);

    /** Whether this call runs on the platform's service account, for which the grants of the caller bound the search. */
    boolean serviceAccountCall();

    TicketSearch searchTickets(String queue, String text);

    NoteReceipt addNote(String ticketId, String body);
}
