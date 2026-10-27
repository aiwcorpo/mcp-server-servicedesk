package pl.aiwcorpo.mcp.servicedesk.core.domain;

/**
 * A ticket reduced to the fields {@code get_ticket} hands out. {@code subject} and {@code description} are text
 * written by people in the service desk, already converted to plain text by the adapter; the tool layer marks
 * them as untrusted data.
 *
 * @param queue the queue the service desk reports the ticket in right now; access is decided on this value
 */
public record Ticket(String id, String queue, String subject, String status, String priority, String created,
        String updated, String description) {

    /** Appended by the adapter to text cut at its length limit; the tool layer reports it as {@code truncated}. */
    public static final String TEXT_CUT_MARK = " ...[cut]";

    public Ticket {
        Identifiers.requireTicketId(id);
        Identifiers.requireQueueId(queue);
    }
}
