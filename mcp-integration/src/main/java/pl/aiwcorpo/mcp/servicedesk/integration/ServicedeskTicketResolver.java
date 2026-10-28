package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskResourceModel;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;

/**
 * Tells the starter what a ticket id is before the tool runs, and judges every hit of a search afterwards. The ticket
 * is described together with the queue the service desk reports for it now, so a ticket moved into a restricted queue
 * is refused whatever its id looks like. The tool reads the fetched ticket from the call context instead of asking
 * the service desk again.
 */
@Component
class ServicedeskTicketResolver implements ResourceResolver<ClassifiedTicket> {

    private final ServicedeskUseCase useCase;

    ServicedeskTicketResolver(ServicedeskUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public String resourceType() {
        return ServicedeskResourceModel.TICKET_TYPE;
    }

    @Override
    public Optional<ClassifiedTicket> fetch(String ticketId) {
        return Optional.ofNullable(useCase.findTickets(List.of(ticketId)).get(ticketId));
    }

    @Override
    public Map<String, ClassifiedTicket> fetchAll(Collection<String> ticketIds) {
        return useCase.findTickets(ticketIds);
    }

    @Override
    public ResolvedResource describe(ClassifiedTicket ticket) {
        return ServicedeskResourceDescription.of(ticket);
    }
}
