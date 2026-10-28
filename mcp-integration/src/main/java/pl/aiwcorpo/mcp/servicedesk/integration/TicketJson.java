package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import tools.jackson.databind.JsonNode;

/**
 * Reads tickets out of the service desk's JSON with the configured pointers. Only the mapped fields leave this
 * class; free text is converted to plain text, normalized and cut to its limit. A ticket whose id or queue is missing or not a valid
 * identifier is treated as not visible: without a trustworthy queue no access decision can be made.
 */
final class TicketJson {

    static final int SUBJECT_MAX = 500;
    static final int DESCRIPTION_MAX = 8000;
    static final int SHORT_MAX = 64;

    private final String id;
    private final String queue;
    private final String subject;
    private final String status;
    private final String priority;
    private final String created;
    private final String updated;
    private final String description;

    TicketJson(String id, String queue, String subject, String status, String priority, String created,
            String updated, String description) {
        this.id = id;
        this.queue = queue;
        this.subject = subject;
        this.status = status;
        this.priority = priority;
        this.created = created;
        this.updated = updated;
        this.description = description;
    }

    Optional<Ticket> ticket(JsonNode node) {
        String ticketId = scalar(node.at(id));
        String queueId = scalar(node.at(queue));
        if (!Identifiers.isTicketId(ticketId) || !Identifiers.isQueueId(queueId)) {
            return Optional.empty();
        }
        return Optional.of(new Ticket(ticketId, queueId, untrusted(node, subject, SUBJECT_MAX),
                field(node, status, SHORT_MAX), field(node, priority, SHORT_MAX), field(node, created, SHORT_MAX),
                field(node, updated, SHORT_MAX), untrusted(node, description, DESCRIPTION_MAX)));
    }

    List<TicketSummary> summaries(JsonNode hits, int max) {
        List<TicketSummary> result = new ArrayList<>();
        if (hits == null || !hits.isArray()) {
            return result;
        }
        for (JsonNode hit : hits) {
            if (result.size() >= max) {
                break;
            }
            String ticketId = scalar(hit.at(id));
            String queueId = scalar(hit.at(queue));
            if (Identifiers.isTicketId(ticketId) && Identifiers.isQueueId(queueId)) {
                result.add(new TicketSummary(ticketId, queueId, untrusted(hit, subject, SUBJECT_MAX),
                        field(hit, status, SHORT_MAX), field(hit, updated, SHORT_MAX)));
            }
        }
        return result;
    }

    /** A string or number as text; anything else (object, array, null, missing) is no value. */
    static String scalar(JsonNode node) {
        if (node == null || !(node.isString() || node.isIntegralNumber())) {
            return null;
        }
        return node.asString();
    }

    private static UntrustedText untrusted(JsonNode node, String pointer, int max) {
        return pointer.isEmpty() ? null : PlainText.untrusted(scalar(node.at(pointer)), max);
    }

    private static String field(JsonNode node, String pointer, int max) {
        return pointer.isEmpty() ? null : PlainText.field(scalar(node.at(pointer)), max);
    }
}
