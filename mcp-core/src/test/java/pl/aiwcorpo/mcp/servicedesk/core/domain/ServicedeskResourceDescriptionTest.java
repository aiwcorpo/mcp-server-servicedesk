package pl.aiwcorpo.mcp.servicedesk.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;

class ServicedeskResourceDescriptionTest {

    @Test
    void aTicketAndItsQueueCarryTheScopeOfTheQueueTheServiceDeskReportsNow() {
        Ticket moved = new Ticket("IT-3", "FIN", null, null, null, null, null, null);

        ResolvedResource ticket = ServicedeskResourceDescription.of(new ClassifiedTicket(moved, new Queue("FIN", null)));

        assertThat(ticket.scopes()).containsExactly("ServiceDesk::Queue/FIN");
        assertThat(ticket.parent().orElseThrow().id()).isEqualTo("FIN");
        assertThat(ticket.parent().orElseThrow().scopes()).containsExactly("ServiceDesk::Queue/FIN");
    }

    @Test
    void aQueueWithoutAClassificationHasNoClassificationAttribute() {
        assertThat(ServicedeskResourceDescription.of(new Queue("IT", null)).attributes()).doesNotContainKey("classification");
        assertThat(ServicedeskResourceDescription.of(new Queue("IT", List.of("restricted"))).attributes())
                .containsEntry("classification", List.of("restricted"));
    }

    @Test
    void theProbeOfAQueueIsItsIdAndScopeAlone() {
        ResolvedResource probe = ServicedeskResourceDescription.probeOfQueue("OPS");

        assertThat(probe.scopes()).containsExactly("ServiceDesk::Queue/OPS");
        assertThat(probe.attributes()).containsOnlyKeys("id");
    }

    @Test
    void theProbeOfATicketOutsideScopesLiesInNoQueueAnyoneIsGranted() {
        ResolvedResource probe = ServicedeskResourceDescription.probeOfTicketOutsideScopes("IT-3");

        assertThat(probe.id()).isEqualTo("IT-3");
        assertThat(probe.scopes()).isEmpty();
        assertThat(probe.parent().orElseThrow().scopes()).isEmpty();
    }
}
