package pl.aiwcorpo.mcp.servicedesk.app.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.aiwcorpo.mcp.servicedesk.core.domain.CredentialMode;

/**
 * Which credential the platform gives this server and which queues it serves.
 *
 * @param credentialMode {@code service-account} (default, D10) or {@code user-secret}
 *                       ({@code MCP_SERVICEDESK_CREDENTIAL_MODE})
 * @param allowedQueues  queue ids the server serves ({@code MCP_SERVICEDESK_ALLOWED_QUEUES}, comma separated);
 *                       required for anything to work in {@code service-account} mode
 */
@ConfigurationProperties("servicedesk.access")
public record ServicedeskAccessProperties(CredentialMode credentialMode, List<String> allowedQueues) {

    public ServicedeskAccessProperties {
        credentialMode = credentialMode == null ? CredentialMode.SERVICE_ACCOUNT : credentialMode;
        allowedQueues = allowedQueues == null ? List.of()
                : allowedQueues.stream().map(String::trim).filter(queue -> !queue.isEmpty()).toList();
    }

    Set<String> allowedQueueSet() {
        return Set.copyOf(allowedQueues);
    }
}
