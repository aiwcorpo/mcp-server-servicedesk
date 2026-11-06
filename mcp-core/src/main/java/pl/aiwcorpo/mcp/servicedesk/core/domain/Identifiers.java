package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.util.regex.Pattern;

/**
 * Identifier grammar of the generic service desk adapter: the {@code idPattern} of {@code ServiceDesk::Queue} and
 * {@code ServiceDesk::Ticket} in {@code mcp/policies/resource-types.json}, which the starter publishes in the tool
 * schemas and checks on every {@code @ResourceRef} value. The module that holds the declarations cannot be read from
 * here, so the pattern is repeated in {@link ServicedeskResourceModel#ID_PATTERN} and a test of the app module compares
 * the two. The pattern applies to tool arguments, to the allowed-queue configuration and to ids the service desk
 * returns: a value outside it never becomes part of a URL or an access decision.
 */
public final class Identifiers {

    private static final Pattern QUEUE_ID = Pattern.compile(ServicedeskResourceModel.ID_PATTERN);
    private static final Pattern TICKET_ID = Pattern.compile(ServicedeskResourceModel.ID_PATTERN);

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
