package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.util.List;

/**
 * Result of {@code search_tickets}.
 *
 * @param truncated the service desk returned more hits than the server hands out
 */
public record TicketSearch(String queue, List<TicketSummary> tickets, boolean truncated) {

    public TicketSearch {
        tickets = List.copyOf(tickets);
    }
}
