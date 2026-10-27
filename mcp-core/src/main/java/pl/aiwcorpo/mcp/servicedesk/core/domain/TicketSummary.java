package pl.aiwcorpo.mcp.servicedesk.core.domain;

/** One search hit: enough to pick a ticket for {@code get_ticket}, without the description. */
public record TicketSummary(String id, String queue, String subject, String status, String updated) {

    public TicketSummary {
        Identifiers.requireTicketId(id);
        Identifiers.requireQueueId(queue);
    }
}
