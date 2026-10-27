package pl.aiwcorpo.mcp.servicedesk.tools;

import java.util.Map;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers;

/**
 * Input schemas of the tools as the client sees them. Spring AI derives schemas from the method signature but
 * cannot express length limits or patterns, so they are written here; the tool methods enforce the same limits
 * again, because a client may ignore the schema. Patterns are the contract's ({@code ServiceDeskQueueId},
 * {@code ServiceDeskTicketId}, mcp-contracts 2.0.2).
 */
public final class ServicedeskToolSchemas {

    private static final String QUEUE = """
            "queue": {"type": "string", "description": "Queue id: letters, digits, '-' or '_', at most 64 characters",
              "minLength": 1, "maxLength": %d, "pattern": "%s"}""".formatted(Identifiers.MAX_ID_LENGTH,
            Identifiers.QUEUE_ID_PATTERN);

    private static final String TICKET_ID = """
            "ticketId": {"type": "string", "description": "Ticket id: letters, digits, '-' or '_', at most 64 characters",
              "minLength": 1, "maxLength": %d, "pattern": "%s"}""".formatted(Identifiers.MAX_ID_LENGTH,
            Identifiers.TICKET_ID_PATTERN);

    public static final Map<String, String> INPUT_SCHEMAS = Map.of(
            "search_tickets", """
                    {"type": "object", "properties": {%s,
                      "text": {"type": "string", "description": "Text to search for, 1-200 characters",
                        "minLength": 1, "maxLength": %d}},
                     "required": ["queue", "text"], "additionalProperties": false}"""
                    .formatted(QUEUE, ServicedeskTool.TEXT_MAX),
            "get_ticket", """
                    {"type": "object", "properties": {%s},
                     "required": ["ticketId"], "additionalProperties": false}""".formatted(TICKET_ID),
            "add_note", """
                    {"type": "object", "properties": {%s,
                      "body": {"type": "string", "description": "Note text, 1-10000 characters",
                        "minLength": 1, "maxLength": %d}},
                     "required": ["ticketId", "body"], "additionalProperties": false}"""
                    .formatted(TICKET_ID, ServicedeskTool.NOTE_MAX));

    private ServicedeskToolSchemas() {
    }
}
