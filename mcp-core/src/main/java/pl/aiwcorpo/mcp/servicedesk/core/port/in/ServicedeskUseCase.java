package pl.aiwcorpo.mcp.servicedesk.core.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;

/**
 * What the server can do, in business terms. Implemented in mcp-application, called by mcp-tools and by the resource
 * resolvers. The lookups return "empty" for a ticket or queue that does not exist, that the service desk hides, or
 * that the service desk reports in an unusable shape.
 */
public interface ServicedeskUseCase {

    /** Search hits limit; fixed because it is part of the tool description. */
    int SEARCH_LIMIT = 20;

    /** The queue as the policy judges it, for a queue id the caller named. */
    Optional<Queue> findQueue(String queueId);

    /**
     * The tickets the caller may be shown, with their queues, keyed by the requested id; absent for the ones that are
     * missing, hidden or reported without a usable queue. The queue of a result is looked up once per queue.
     */
    Map<String, ClassifiedTicket> findTickets(Collection<String> ticketIds);

    /**
     * @param currentQueueAllowed judges the queue the service desk reports for a hit now; a hit it refuses is left out
     *                            (the caller's grants and link, which the index may lag behind)
     */
    TicketSearch searchTickets(String queue, String text, Predicate<String> currentQueueAllowed);

    /**
     * Reads the ticket again just before writing and refuses when its queue is no longer one {@code currentQueueAllowed}
     * accepts; a move between that read and the write stays possible (no conditional write in generic REST).
     */
    NoteReceipt addNote(String ticketId, String body, Predicate<String> currentQueueAllowed);
}
