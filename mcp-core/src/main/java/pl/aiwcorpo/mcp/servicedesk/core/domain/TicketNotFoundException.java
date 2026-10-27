package pl.aiwcorpo.mcp.servicedesk.core.domain;

/**
 * The ticket or queue does not exist, the service desk hides it, or this server does not allow its queue. All
 * cases are one exception with one message: the client gets the same {@code not_found_or_denied} for each, so
 * it cannot probe which tickets exist in queues it may not see.
 */
public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException() {
        super("ticket or queue not found or not visible", null, false, false);
    }
}
