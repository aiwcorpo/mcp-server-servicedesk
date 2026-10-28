package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Identifier grammar of the generic service desk adapter: {@code ServiceDeskQueueId} and
 * {@code ServiceDeskTicketId} of mcp-contracts {@code common.schema.json}, read from the contract on the classpath
 * and not copied, so a new contract version cannot leave the server with an older pattern. The tool schemas take the
 * same patterns from the contract through {@code @ArgumentLimits(contractPattern = ...)}. The pattern applies to
 * tool arguments, to the allowed-queue configuration and to ids the service desk returns: a value outside it never
 * becomes part of a URL or an access decision.
 */
public final class Identifiers {

    private static final String COMMON_SCHEMA = "/mcp-contracts/schemas/common.schema.json";
    private static final Pattern QUEUE_ID = contractPattern("ServiceDeskQueueId");
    private static final Pattern TICKET_ID = contractPattern("ServiceDeskTicketId");

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

    private static Pattern contractPattern(String definition) {
        try (InputStream in = Identifiers.class.getResourceAsStream(COMMON_SCHEMA)) {
            if (in == null) {
                throw new IllegalStateException(COMMON_SCHEMA + " is missing: mcp-contracts must be on the classpath");
            }
            String pattern = JsonMapper.builder().build().readTree(in).path("$defs").path(definition)
                    .path("pattern").asString(null);
            if (pattern == null) {
                throw new IllegalStateException(definition + " has no pattern in common.schema.json");
            }
            return Pattern.compile(pattern);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
