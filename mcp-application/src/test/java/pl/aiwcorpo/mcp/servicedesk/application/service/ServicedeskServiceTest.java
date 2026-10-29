package pl.aiwcorpo.mcp.servicedesk.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketNotFoundException;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

class ServicedeskServiceTest {

    private final FakeServiceDesk desk = new FakeServiceDesk();
    private final ServicedeskService service = new ServicedeskService(desk);

    @Test
    void aTicketIsFoundWithItsQueueAndClassification() {
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));
        desk.queues.put("IT", new Queue("IT", List.of("Internal")));

        ClassifiedTicket found = service.findTickets(List.of("IT-1")).get("IT-1");

        assertThat(found.ticket().queue()).isEqualTo("IT");
        assertThat(found.queue().classification()).containsExactly("Internal");
    }

    @Test
    void aliasesMissingTicketsAndTicketsOfHiddenQueuesAreNotFound() {
        desk.tickets.put("ALIAS", ticket("IT-9", "IT"));
        desk.tickets.put("IT-2", ticket("IT-2", "IT"));

        assertThat(service.findTickets(List.of("ALIAS", "NONE")))
                .as("ALIAS answers with another id, NONE does not exist").isEmpty();
        assertThat(service.findTickets(List.of("IT-2")))
                .as("the queue itself is hidden from the credential: the ticket is not judged without it").isEmpty();
    }

    @Test
    void theQueueOfAWholeResultIsLookedUpOnce() {
        desk.queues.put("IT", new Queue("IT", null));
        for (int i = 0; i < 5; i++) {
            desk.tickets.put("IT-" + i, ticket("IT-" + i, "IT"));
        }

        assertThat(service.findTickets(List.of("IT-0", "IT-1", "IT-2", "IT-3", "IT-4"))).hasSize(5);
        assertThat(desk.queueReads).isEqualTo(1);
    }

    @Test
    void aQueueIsFoundOnlyWhenTheServiceDeskShowsIt() {
        desk.queues.put("IT", new Queue("IT", null));

        assertThat(service.findQueue("IT")).isPresent();
        assertThat(service.findQueue("GONE")).as("the service desk does not show it").isEmpty();
        assertThat(service.findQueue("bad queue")).as("not an identifier: never sent").isEmpty();
        assertThat(desk.queueReads).as("the malformed id did not reach the service desk").isEqualTo(2);
    }

    @Test
    void aNoteIsWrittenAfterReadingTheTicketOnceMoreAndNotWhenItsQueueIsNoLongerAccepted() {
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));
        desk.queues.put("IT", new Queue("IT", null));

        assertThatThrownBy(() -> service.addNote("IT-1", "hello", queue -> false))
                .isInstanceOf(TicketNotFoundException.class);
        assertThat(desk.notes).isEmpty();
        assertThat(service.addNote("IT-1", "hello", queue -> queue.equals("IT")).noteId()).isEqualTo("N-1");
        assertThat(desk.notes).containsExactly("IT-1:hello");
    }

    @Test
    void searchDropsHitsWhoseCurrentQueueIsRefused() {
        desk.queues.put("IT", new Queue("IT", null));
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));
        desk.tickets.put("IT-2", ticket("IT-2", "IT"));
        desk.hits = List.of(summary("IT-1", "IT"), summary("IT-2", "IT"));

        assertThat(service.searchTickets("IT", "x", queue -> true).tickets()).hasSize(2);
        assertThat(service.searchTickets("IT", "x", queue -> false).tickets()).isEmpty();
    }

    @Test
    void anEmptyNoteIdBecomesNull() {
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));
        desk.queues.put("IT", new Queue("IT", null));
        desk.noteId = "";

        assertThat(service.addNote("IT-1", "hello", queue -> true).noteId()).isNull();
    }

    @Test
    void searchInAMalformedQueueNeverReachesTheServiceDesk() {
        assertThatThrownBy(() -> service.searchTickets("bad queue", "printer", queue -> true))
                .isInstanceOf(TicketNotFoundException.class);
        assertThat(desk.searches).isEmpty();
    }

    @Test
    void searchDropsHitsFromOtherQueuesAndCutsAtTheLimit() {
        desk.queues.put("IT", new Queue("IT", null));
        IntStream.range(0, 25).forEach(i -> desk.tickets.put("IT-" + i, ticket("IT-" + i, "IT")));
        desk.hits = new ArrayList<>(IntStream.range(0, 25).mapToObj(i -> summary("IT-" + i, "IT")).toList());
        desk.hits.add(0, summary("FIN-1", "FIN"));

        var search = service.searchTickets("IT", "printer", queue -> true);

        assertThat(search.tickets()).hasSize(20).allMatch(t -> t.queue().equals("IT"));
        assertThat(search.truncated()).isTrue();
    }

    @Test
    void searchDropsHitsOfAnotherQueueToo() {
        desk.queues.put("IT", new Queue("IT", null));
        desk.tickets.put("IT-1", ticket("IT-1", "IT"));
        desk.hits = new ArrayList<>(List.of(summary("IT-1", "IT"), summary("OPS-5", "OPS")));

        assertThat(service.searchTickets("IT", "printer", queue -> true).tickets()).extracting(TicketSummary::id)
                .as("the queue asked for is the boundary")
                .containsExactly("IT-1");
    }

    private static Ticket ticket(String id, String queue) {
        return new Ticket(id, queue, UntrustedText.plain("subject", 100), "open", null, null, null, null);
    }

    private static TicketSummary summary(String id, String queue) {
        return new TicketSummary(id, queue, UntrustedText.plain("subject", 100), "open", null);
    }

    private static final class FakeServiceDesk implements ServicedeskResourcePort {

        final Map<String, Ticket> tickets = new HashMap<>();
        final Map<String, Queue> queues = new HashMap<>();
        final List<String> notes = new ArrayList<>();
        final List<String> searches = new ArrayList<>();
        List<TicketSummary> hits = List.of();
        String noteId = "N-1";
        int reads;
        int queueReads;

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
        public Optional<Queue> findQueue(String queueId) {
            queueReads++;
            return Optional.ofNullable(queues.get(queueId));
        }

        @Override
        public Optional<String> addNote(String ticketId, String body) {
            notes.add(ticketId + ":" + body);
            return Optional.of(noteId);
        }
    }
}
