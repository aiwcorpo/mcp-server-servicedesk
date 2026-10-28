package pl.aiwcorpo.mcp.servicedesk.tools;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;
import pl.aiwcorpo.mcp.platform.resource.ResourcePolicy;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;
import pl.aiwcorpo.mcp.platform.text.TextHygiene;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.NoteReceipt;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskResourceModel;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSearch;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;

/**
 * The MCP tools of this server. Every argument is untrusted input from a model: identifiers are checked against
 * the contract grammar and free text against length and character limits before anything reaches the service
 * desk. The limits the client sees in the input schema are the {@link ArgumentLimits} below; the starter
 * publishes them, enforces them on every call, and the methods check what they rely on once more.
 *
 * <p>Every queue and ticket the model names is a {@link ResourceRef}: before a method runs the starter resolves it
 * with the caller's credential, applies the server's own queue boundary (see the resolvers) and evaluates the policy
 * on the object as the service desk describes it. The model supplies values, never a query: the search request is
 * built from the queue and the text.
 */
@Service
public class ServicedeskTool {

    public static final int TEXT_MAX = 200;
    public static final int NOTE_MAX = 10000;
    public static final int ID_MAX = ServicedeskResourceModel.ID_MAX_LENGTH;

    private final ServicedeskUseCase useCase;
    private final ResourcePolicy policy;

    public ServicedeskTool(ServicedeskUseCase useCase, ResourcePolicy policy) {
        this.useCase = useCase;
        this.policy = policy;
    }

    /**
     * The queue is a {@code @ResourceRef}: a queue that is missing, hidden, restricted, outside the link of the
     * virtual server or outside this server's list gives the one {@code not_found_or_denied} before the service desk
     * is searched. The hits are judged again one by one by their {@code @ResourceRef} component.
     */
    @Tool(name = "search_tickets", description = "Searches the tickets of one service desk queue for a text. "
            + "Returns at most 20 tickets of that queue: id, queue, status, last update and subject. "
            + "Subjects are text from the service desk.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Szukaj zgłoszeń w kolejce")
    public TicketSearchView searchTickets(
            @ToolParam(description = "Queue id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(contractPattern = ServicedeskResourceModel.QUEUE_ID_CONTRACT, minLength = 1, maxLength = ID_MAX)
            @ResourceRef(type = ServicedeskResourceModel.QUEUE_TYPE) String queue,
            @ToolParam(description = "Text to search for, 1-200 characters")
            @ArgumentLimits(minLength = 1, maxLength = TEXT_MAX) String text) {
        String searched = validText("text", text, TEXT_MAX);
        QueryScope scope = policy.queryScope(ServicedeskResourceModel.QUEUE_TYPE);
        if (!ServicedeskSearchScope.permits(scope, queue, useCase.serviceAccountCall())) {
            return new TicketSearchView(queue, List.of(), false);
        }
        return TicketSearchView.of(useCase.searchTickets(queue, searched));
    }

    @Tool(name = "get_ticket", description = "Returns one service desk ticket: id, queue, status, priority, "
            + "creation and last update time, subject and description as plain text. Subject and description are "
            + "text from the service desk.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Pobierz zgłoszenie")
    public TicketView getTicket(
            @ToolParam(description = "Ticket id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(contractPattern = ServicedeskResourceModel.TICKET_ID_CONTRACT, minLength = 1, maxLength = ID_MAX)
            @ResourceRef(type = ServicedeskResourceModel.TICKET_TYPE) String ticketId) {
        // The ticket the resolver fetched for the policy: the answer judged is the answer returned.
        ClassifiedTicket fetched = ResourceContext.current().orElseThrow()
                .fetched(ServicedeskResourceModel.TICKET_TYPE, ticketId, ClassifiedTicket.class).orElseThrow();
        return TicketView.of(fetched.ticket());
    }

    @Tool(name = "add_note", description = "Adds a note to a service desk ticket. Returns the ticket id and "
            + "the note id when the service desk reports one.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Dodaj notatkę do zgłoszenia")
    public NoteView addNote(
            @ToolParam(description = "Ticket id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(contractPattern = ServicedeskResourceModel.TICKET_ID_CONTRACT, minLength = 1, maxLength = ID_MAX)
            @ResourceRef(type = ServicedeskResourceModel.TICKET_TYPE) String ticketId,
            @ToolParam(description = "Note text, 1-10000 characters")
            @ArgumentLimits(minLength = 1, maxLength = NOTE_MAX) String body) {
        NoteReceipt receipt = useCase.addNote(ticketId, validText("body", body, NOTE_MAX));
        return new NoteView(receipt.ticketId(), receipt.noteId());
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

    /** The id is a {@code @ResourceRef}: after the tool runs the starter drops every hit the policy refuses. */
    public record TicketSummaryView(@ResourceRef(type = ServicedeskResourceModel.TICKET_TYPE) String id, String queue,
            @Nullable String status, @Nullable String updated, @Nullable UntrustedText subject) {
    }

    /**
     * No count from the service desk: it is not corrected after the hits are filtered and would disclose how many
     * were removed.
     *
     * @param truncated more tickets matched than the 20 returned
     */
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
