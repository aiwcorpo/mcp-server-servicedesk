package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.util.regex.Pattern;

/**
 * Identifier grammar of the generic service desk adapter, as in mcp-contracts 2.0.2 {@code common.schema.json}
 * ({@code ServiceDeskQueueId}, {@code ServiceDeskTicketId}). The same pattern applies to tool arguments, to the
 * allowed-queue configuration and to ids the service desk returns: a value outside it never becomes part of a
 * URL or an access decision.
 */
public final class Identifiers {

    public static final String QUEUE_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";
    public static final String TICKET_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";
    public static final int MAX_ID_LENGTH = 64;

    private static final Pattern QUEUE_ID = Pattern.compile(QUEUE_ID_PATTERN);
    private static final Pattern TICKET_ID = Pattern.compile(TICKET_ID_PATTERN);

    private Identifiers() {
    }

    public static boolean isQueueId(String value) {
        return value != null && QUEUE_ID.matcher(value).matches();
    }

    public static boolean isTicketId(String value) {
        return value != null && TICKET_ID.matcher(value).matches();
    }

    static void requireQueueId(String value) {
        if (!isQueueId(value)) {
            throw new IllegalArgumentException("not a service desk queue id");
        }
    }

    static void requireTicketId(String value) {
        if (!isTicketId(value)) {
            throw new IllegalArgumentException("not a service desk ticket id");
        }
    }
}
