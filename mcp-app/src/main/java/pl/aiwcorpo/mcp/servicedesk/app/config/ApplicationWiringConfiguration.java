package pl.aiwcorpo.mcp.servicedesk.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.servicedesk.application.service.ServicedeskService;
import pl.aiwcorpo.mcp.servicedesk.core.domain.CredentialMode;
import pl.aiwcorpo.mcp.servicedesk.core.domain.QueueAccessPolicy;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
@EnableConfigurationProperties(ServicedeskAccessProperties.class)
public class ApplicationWiringConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationWiringConfiguration.class);

    @Bean
    QueueAccessPolicy queueAccessPolicy(ServicedeskAccessProperties access) {
        QueueAccessPolicy policy = new QueueAccessPolicy(access.credentialMode(), access.allowedQueueSet());
        if (policy.mode() == CredentialMode.SERVICE_ACCOUNT && policy.allowedQueues().isEmpty()) {
            // Starting is still useful (health, tools/list), but every tool call is refused until queues are listed.
            LOG.warn("event=no_allowed_queues mode=service-account: every ticket and queue is refused; "
                    + "set servicedesk.access.allowed-queues (MCP_SERVICEDESK_ALLOWED_QUEUES)");
        }
        return policy;
    }

    @Bean
    ServicedeskUseCase servicedeskUseCase(ServicedeskResourcePort resource, QueueAccessPolicy policy) {
        return new ServicedeskService(resource, policy);
    }
}
