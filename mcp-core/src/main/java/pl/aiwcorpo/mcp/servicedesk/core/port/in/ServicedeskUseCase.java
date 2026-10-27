package pl.aiwcorpo.mcp.servicedesk.core.port.in;

import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;

/**
 * What the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. Every method
 * throws {@link pl.aiwcorpo.mcp.servicedesk.core.domain.TicketNotFoundException} for a ticket or queue that
 * does not exist or may not be served.
 */
public interface ServicedeskUseCase {

    /** Search hits limit; fixed because it is part of the tool description. */
    int SEARCH_LIMIT = 20;

    TicketSearch searchTickets(String queue, String text);

    Ticket getTicket(String ticketId);

    NoteReceipt addNote(String ticketId, String body);
}
