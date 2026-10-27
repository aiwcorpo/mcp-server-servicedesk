package pl.aiwcorpo.mcp.servicedesk.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.servicedesk.core.domain.CredentialMode;
import pl.aiwcorpo.mcp.servicedesk.core.domain.QueueAccessPolicy;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketNotFoundException;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

class ServicedeskServiceTest {

    private final FakeServiceDesk desk = new FakeServiceDesk();
    private final ServicedeskService service =
            new ServicedeskService(desk, new QueueAccessPolicy(CredentialMode.SERVICE_ACCOUNT, Set.of("IT")));

    @Test
    void readsATicketOfAnAllowedQueue() {
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));

        assertThat(service.getTicket("IT-1").queue()).isEqualTo("IT");
    }

    @Test
    void ticketsOfOtherQueuesMovedTicketsAndAliasesAreNotFound() {
        desk.tickets.put("FIN-1", ticket("FIN-1", "FIN"));
        desk.tickets.put("ALIAS", ticket("IT-9", "IT"));

        assertThatThrownBy(() -> service.getTicket("FIN-1")).isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> service.getTicket("ALIAS")).isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> service.getTicket("NONE")).isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void aNoteIsWrittenOnlyAfterTheQueueCheck() {
        desk.tickets.put("FIN-1", ticket("FIN-1", "FIN"));
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));

        assertThatThrownBy(() -> service.addNote("FIN-1", "hello")).isInstanceOf(TicketNotFoundException.class);
        assertThat(service.addNote("IT-1", "hello").noteId()).isEqualTo("N-1");
        assertThat(desk.notes).containsExactly("IT-1:hello");
    }

    @Test
    void anEmptyNoteIdBecomesNull() {
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));
        desk.noteId = "";

        assertThat(service.addNote("IT-1", "hello").noteId()).isNull();
    }

    @Test
    void searchInADisallowedQueueNeverReachesTheServiceDesk() {
        assertThatThrownBy(() -> service.searchTickets("FIN", "printer")).isInstanceOf(TicketNotFoundException.class);
        assertThat(desk.searches).isEmpty();
    }

    @Test
    void searchDropsHitsFromOtherQueuesAndCutsAtTheLimit() {
        desk.hits = new ArrayList<>(IntStream.range(0, 25).mapToObj(i -> summary("IT-" + i, "IT")).toList());
        desk.hits.add(0, summary("FIN-1", "FIN"));

        var search = service.searchTickets("IT", "printer");

        assertThat(search.tickets()).hasSize(20).allMatch(t -> t.queue().equals("IT"));
        assertThat(search.truncated()).isTrue();
    }

    @Test
    void withoutAllowedQueuesNothingReachesTheServiceDesk() {
        ServicedeskService closed =
                new ServicedeskService(desk, new QueueAccessPolicy(CredentialMode.SERVICE_ACCOUNT, Set.of()));
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));

        assertThatThrownBy(() -> closed.getTicket("IT-1")).isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> closed.addNote("IT-1", "x")).isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> closed.searchTickets("IT", "x")).isInstanceOf(TicketNotFoundException.class);
        assertThat(desk.reads).isZero();
        assertThat(desk.notes).isEmpty();
    }

    private static Ticket ticket(String id, String queue) {
        return new Ticket(id, queue, "subject", "open", null, null, null, null);
    }

    private static TicketSummary summary(String id, String queue) {
        return new TicketSummary(id, queue, "subject", "open", null);
    }

    private static final class FakeServiceDesk implements ServicedeskResourcePort {

        final Map<String, Ticket> tickets = new HashMap<>();
        final List<String> notes = new ArrayList<>();
        final List<String> searches = new ArrayList<>();
        List<TicketSummary> hits = List.of();
        String noteId = "N-1";
        int reads;

        @Override
        public Optional<List<TicketSummary>> searchTickets(String queue, String text, int limit) {
            searches.add(queue);
            return Optional.of(hits);
        }

        @Override
        public Optional<Ticket> findTicket(String ticketId) {
            reads++;
            return Optional.ofNullable(tickets.get(ticketId));
        }

        @Override
        public Optional<String> addNote(String ticketId, String body) {
            notes.add(ticketId + ":" + body);
            return Optional.of(noteId);
        }

        @Override
        public boolean callerIsServiceAccount() {
            return true;
        }
    }
}
