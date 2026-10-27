package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import tools.jackson.databind.JsonNode;

/**
 * Reads tickets out of the service desk's JSON with the configured pointers. Only the mapped fields leave this
 * class; free text is converted to plain text and capped. A ticket whose id or queue is missing or not a valid
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
        return Optional.of(new Ticket(ticketId, queueId, text(node, subject, SUBJECT_MAX), text(node, status, SHORT_MAX),
                text(node, priority, SHORT_MAX), text(node, created, SHORT_MAX), text(node, updated, SHORT_MAX),
                text(node, description, DESCRIPTION_MAX)));
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
                result.add(new TicketSummary(ticketId, queueId, text(hit, subject, SUBJECT_MAX),
                        text(hit, status, SHORT_MAX), text(hit, updated, SHORT_MAX)));
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

    private static String text(JsonNode node, String pointer, int max) {
        if (pointer.isEmpty()) {
            return null;
        }
        return PlainText.of(scalar(node.at(pointer)), max);
    }
}
