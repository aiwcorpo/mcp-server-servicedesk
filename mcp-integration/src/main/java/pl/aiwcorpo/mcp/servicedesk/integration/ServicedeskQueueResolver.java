package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskResourceModel;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;

/**
 * Tells the starter what a queue id is before the tool runs. Empty for a queue that does not exist, that the service
 * desk hides, or that a service account call may not serve; a failing service desk throws and the client sees
 * {@code upstream_unavailable}.
 */
@Component
class ServicedeskQueueResolver implements ResourceResolver<Queue> {

    private final ServicedeskUseCase useCase;

    ServicedeskQueueResolver(ServicedeskUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public String resourceType() {
        return ServicedeskResourceModel.QUEUE_TYPE;
    }

    @Override
    public Optional<Queue> fetch(String queueId) {
        return useCase.findQueue(queueId);
    }

    @Override
    public ResolvedResource describe(Queue queue) {
        return ServicedeskResourceDescription.of(queue);
    }
}
