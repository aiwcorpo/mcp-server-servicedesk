package pl.aiwcorpo.mcp.servicedesk.tools;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.text.TextHygiene;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;

/**
 * The MCP tools of this server. Every argument is untrusted input from a model: identifiers are checked against
 * the contract grammar and free text against length and character limits before anything reaches the service
 * desk. The limits the client sees in the input schema are the {@link ArgumentLimits} below; the starter
 * publishes them, enforces them on every call, and the methods check what they rely on once more.
 */
@Service
public class ServicedeskTool {

    public static final int TEXT_MAX = 200;
    public static final int NOTE_MAX = 10000;
    public static final int ID_MAX = 64;

    private final ServicedeskUseCase useCase;

    public ServicedeskTool(ServicedeskUseCase useCase) {
        this.useCase = useCase;
    }

    @Tool(name = "search_tickets", description = "Searches the tickets of one service desk queue for a text. "
            + "Returns at most 20 tickets of that queue: id, queue, status, last update and subject. "
            + "Subjects are text from the service desk.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public TicketSearchView searchTickets(
            @ToolParam(description = "Queue id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(contractPattern = "ServiceDeskQueueId", minLength = 1, maxLength = ID_MAX) String queue,
            @ToolParam(description = "Text to search for, 1-200 characters")
            @ArgumentLimits(minLength = 1, maxLength = TEXT_MAX) String text) {
        return TicketSearchView.of(useCase.searchTickets(validQueue(queue), validText("text", text, TEXT_MAX)));
    }

    @Tool(name = "get_ticket", description = "Returns one service desk ticket: id, queue, status, priority, "
            + "creation and last update time, subject and description as plain text. Subject and description are "
            + "text from the service desk.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public TicketView getTicket(
            @ToolParam(description = "Ticket id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(contractPattern = "ServiceDeskTicketId", minLength = 1, maxLength = ID_MAX) String ticketId) {
        return TicketView.of(useCase.getTicket(validTicketId(ticketId)));
    }

    @Tool(name = "add_note", description = "Adds a note to a service desk ticket. Returns the ticket id and "
            + "the note id when the service desk reports one.")
    @ToolRisk(RiskLevel.UPDATE)
    public NoteView addNote(
            @ToolParam(description = "Ticket id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(contractPattern = "ServiceDeskTicketId", minLength = 1, maxLength = ID_MAX) String ticketId,
            @ToolParam(description = "Note text, 1-10000 characters")
            @ArgumentLimits(minLength = 1, maxLength = NOTE_MAX) String body) {
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
        if (TextHygiene.firstProblem(value, false) != null) {
            throw new InvalidToolArgumentException(name + " must not contain control or invisible characters");
        }
        return value;
    }

    /** Subject and description are {@code null} when the service desk has none. */
    public record TicketView(String id, String queue, @Nullable String status, @Nullable String priority,
            @Nullable String created, @Nullable String updated, @Nullable UntrustedText subject,
            @Nullable UntrustedText description) {

        static TicketView of(Ticket ticket) {
            return new TicketView(ticket.id(), ticket.queue(), ticket.status(), ticket.priority(), ticket.created(),
                    ticket.updated(), ticket.subject(), ticket.description());
        }
    }

    public record TicketSummaryView(String id, String queue, @Nullable String status, @Nullable String updated,
            @Nullable UntrustedText subject) {
    }

    /** @param truncated more tickets matched than the 20 returned */
    public record TicketSearchView(String queue, List<TicketSummaryView> tickets, boolean truncated) {

        static TicketSearchView of(TicketSearch search) {
            return new TicketSearchView(search.queue(), search.tickets().stream()
                    .map(t -> new TicketSummaryView(t.id(), t.queue(), t.status(), t.updated(), t.subject()))
                    .toList(), search.truncated());
        }
    }

    public record NoteView(String ticketId, @Nullable String noteId) {
    }
}
