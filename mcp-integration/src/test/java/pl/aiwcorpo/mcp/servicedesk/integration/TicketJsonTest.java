package pl.aiwcorpo.mcp.servicedesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import tools.jackson.databind.json.JsonMapper;

/** The same mapping code against two different API shapes: only the pointers change. */
class TicketJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void readsTheReferenceLayoutAndDropsEverythingElse() {
        TicketJson json = new TicketJson("/id", "/queue", "/subject", "/status", "/priority", "/createdAt",
                "/updatedAt", "/description");

        Ticket ticket = json.ticket(MAPPER.readTree("""
                {"id":"IT-1","queue":"IT","subject":"Printer","status":"open","priority":"P3",
                 "createdAt":"2026-10-01T08:00:00Z","updatedAt":"2026-10-02T08:00:00Z",
                 "description":"<p>Paper jam</p><script>alert(1)</script>","requesterEmail":"a@corp.internal"}
                """)).orElseThrow();

        assertThat(ticket).isEqualTo(new Ticket("IT-1", "IT", "Printer", "open", "P3", "2026-10-01T08:00:00Z",
                "2026-10-02T08:00:00Z", "Paper jam"));
    }

    @Test
    void readsANestedLayoutWithNumericIds() {
        // Shape of a product that nests fields and numbers its tickets and queues.
        TicketJson json = new TicketJson("/issueId", "/serviceDesk/queueId", "/fields/summary",
                "/currentStatus/status", "", "/createdDate/iso8601", "", "/fields/description");

        Ticket ticket = json.ticket(MAPPER.readTree("""
                {"issueId":10042,"serviceDesk":{"queueId":7},"fields":{"summary":"VPN","description":"Cannot\\r\\nconnect"},
                 "currentStatus":{"status":"Waiting for support"},"createdDate":{"iso8601":"2026-10-01T08:00:00+0000"}}
                """)).orElseThrow();

        assertThat(ticket.id()).isEqualTo("10042");
        assertThat(ticket.queue()).isEqualTo("7");
        assertThat(ticket.status()).isEqualTo("Waiting for support");
        assertThat(ticket.description()).isEqualTo("Cannot\nconnect");
        assertThat(ticket.priority()).isNull();
    }

    @Test
    void aTicketWithoutAUsableQueueOrIdIsNotVisible() {
        TicketJson json = new TicketJson("/id", "/queue", "", "", "", "", "", "");

        assertThat(json.ticket(MAPPER.readTree("{\"id\":\"IT-1\"}"))).isEmpty();
        assertThat(json.ticket(MAPPER.readTree("{\"id\":\"IT-1\",\"queue\":null}"))).isEmpty();
        assertThat(json.ticket(MAPPER.readTree("{\"id\":\"IT-1\",\"queue\":{\"id\":\"IT\"}}"))).isEmpty();
        assertThat(json.ticket(MAPPER.readTree("{\"id\":\"IT-1\",\"queue\":\"IT FIN\"}"))).isEmpty();
        assertThat(json.ticket(MAPPER.readTree("{\"id\":\"../x\",\"queue\":\"IT\"}"))).isEmpty();
        assertThat(json.ticket(MAPPER.readTree("{\"id\":1.5,\"queue\":\"IT\"}"))).isEmpty();
        assertThat(json.ticket(MAPPER.readTree("{\"id\":true,\"queue\":\"IT\"}"))).isEmpty();
    }

    @Test
    void searchHitsWithoutUsableIdentifiersAreDroppedAndTheListIsCapped() {
        TicketJson json = new TicketJson("/id", "/queue", "/subject", "", "", "", "", "");

        var hits = json.summaries(MAPPER.readTree("""
                [{"id":"IT-1","queue":"IT","subject":"a"},{"id":"IT-2"},{"id":"IT-3","queue":"IT"},
                 {"id":"IT-4","queue":"IT"}]"""), 2);

        assertThat(hits).extracting("id").isEqualTo(List.of("IT-1", "IT-3"));
        assertThat(json.summaries(MAPPER.readTree("{\"items\":[]}"), 5)).isEmpty();
    }
}
