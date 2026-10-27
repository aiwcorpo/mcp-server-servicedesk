package pl.aiwcorpo.mcp.servicedesk.tools;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;

/**
 * The MCP tools of this server. Every argument is untrusted input from a model: identifiers are checked against
 * the contract grammar and free text against length and character limits before anything reaches the service
 * desk. The input schemas the client sees, with the same limits, are in {@link ServicedeskToolSchemas}.
 */
@Service
public class ServicedeskTool {

    public static final int TEXT_MAX = 200;
    public static final int NOTE_MAX = 10000;

    /** Control characters other than tab and line breaks have no place in a search or a note. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n\\r\\t]]");

    private final ServicedeskUseCase useCase;

    public ServicedeskTool(ServicedeskUseCase useCase) {
        this.useCase = useCase;
    }

    @Tool(name = "search_tickets", description = "Searches the tickets of one service desk queue for a text. "
            + "Returns at most 20 tickets of that queue: id, queue, status, last update and subject. "
            + "Subjects are text from the service desk.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public TicketSearchView searchTickets(
            @ToolParam(description = "Queue id: letters, digits, '-' or '_', at most 64 characters") String queue,
            @ToolParam(description = "Text to search for, 1-200 characters") String text) {
        return TicketSearchView.of(useCase.searchTickets(validQueue(queue), validText("text", text, TEXT_MAX)));
    }

    @Tool(name = "get_ticket", description = "Returns one service desk ticket: id, queue, status, priority, "
            + "creation and last update time, subject and description as plain text. Subject and description are "
            + "text from the service desk.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public TicketView getTicket(
            @ToolParam(description = "Ticket id: letters, digits, '-' or '_', at most 64 characters") String ticketId) {
        return TicketView.of(useCase.getTicket(validTicketId(ticketId)));
    }

    @Tool(name = "add_note", description = "Adds a note to a service desk ticket. Returns the ticket id and "
            + "the note id when the service desk reports one.")
    @ToolRisk(RiskLevel.UPDATE)
    public NoteView addNote(
            @ToolParam(description = "Ticket id: letters, digits, '-' or '_', at most 64 characters") String ticketId,
            @ToolParam(description = "Note text, 1-10000 characters") String body) {
        NoteReceipt receipt = useCase.addNote(validTicketId(ticketId), validText("body", body, NOTE_MAX));
        return new NoteView(receipt.ticketId(), receipt.noteId());
    }

    private static String validQueue(String queue) {
        if (!Identifiers.isQueueId(queue)) {
            throw new InvalidToolArgumentException("queue must be 1-64 characters: letters, digits, '-' or '_'");
        }
        return queue;
    }

    private static String validTicketId(String ticketId) {
        if (!Identifiers.isTicketId(ticketId)) {
            throw new InvalidToolArgumentException("ticketId must be 1-64 characters: letters, digits, '-' or '_'");
        }
        return ticketId;
    }

    private static String validText(String name, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new InvalidToolArgumentException(name + " must be 1-" + max + " characters and not blank");
        }
        if (CONTROL.matcher(value).find()) {
            throw new InvalidToolArgumentException(name + " must not contain control characters");
        }
        return value;
    }

    /**
     * Free text from the service desk in the shape of the contracts' {@code UntrustedText} (CC-072): the client
     * can tell data written by people in the service desk from the platform's own output.
     *
     * @param truncated the text was cut at the length limit
     */
    public record UntrustedText(String kind, String format, String text, boolean truncated) {

        static final String KIND = "untrusted-data";
        static final String PLAIN = "text/plain";

        static UntrustedText plain(String text) {
            return text == null ? null : new UntrustedText(KIND, PLAIN, text, text.endsWith(Ticket.TEXT_CUT_MARK));
        }
    }

    public record TicketView(String id, String queue, String status, String priority, String created,
            String updated, UntrustedText subject, UntrustedText description) {

        static TicketView of(Ticket ticket) {
            return new TicketView(ticket.id(), ticket.queue(), ticket.status(), ticket.priority(), ticket.created(),
                    ticket.updated(), UntrustedText.plain(ticket.subject()), UntrustedText.plain(ticket.description()));
        }
    }

    public record TicketSummaryView(String id, String queue, String status, String updated, UntrustedText subject) {
    }

    /** @param truncated more tickets matched than the 20 returned */
    public record TicketSearchView(String queue, List<TicketSummaryView> tickets, boolean truncated) {

        static TicketSearchView of(TicketSearch search) {
            return new TicketSearchView(search.queue(), search.tickets().stream()
                    .map(t -> new TicketSummaryView(t.id(), t.queue(), t.status(), t.updated(),
                            UntrustedText.plain(t.subject())))
                    .toList(), search.truncated());
        }
    }

    public record NoteView(String ticketId, String noteId) {
    }
}
