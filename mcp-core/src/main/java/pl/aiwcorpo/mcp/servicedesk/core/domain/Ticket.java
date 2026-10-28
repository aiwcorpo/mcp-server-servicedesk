package pl.aiwcorpo.mcp.servicedesk.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * A ticket reduced to the fields {@code get_ticket} hands out. {@code subject} and {@code description} are text
 * written by people in the service desk, already converted to plain text by the adapter and marked as untrusted
 * data; the other fields are short values of the system. Any field the service desk leaves out is {@code null}.
 *
 * @param queue the queue the service desk reports the ticket in right now; access is decided on this value
 */
public record Ticket(String id, String queue, @Nullable UntrustedText subject, @Nullable String status,
        @Nullable String priority, @Nullable String created, @Nullable String updated,
        @Nullable UntrustedText description) {

    public Ticket {
        Identifiers.requireTicketId(id);
        Identifiers.requireQueueId(queue);
    }
}
