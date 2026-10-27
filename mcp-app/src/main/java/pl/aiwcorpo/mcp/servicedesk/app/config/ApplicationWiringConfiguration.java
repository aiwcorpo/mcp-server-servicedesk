package pl.aiwcorpo.mcp.servicedesk.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.servicedesk.application.service.ServicedeskService;
import pl.aiwcorpo.mcp.servicedesk.core.port.in.ServicedeskUseCase;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    ServicedeskUseCase servicedeskUseCase(ServicedeskResourcePort resource) {
        return new ServicedeskService(resource);
    }
}
