package pl.aiwcorpo.mcp.servicedesk.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** One search hit: enough to pick a ticket for {@code get_ticket}, without the description. */
public record TicketSummary(String id, String queue, @Nullable UntrustedText subject, @Nullable String status,
        @Nullable String updated) {

    public TicketSummary {
        Identifiers.requireTicketId(id);
        Identifiers.requireQueueId(queue);
    }
}
