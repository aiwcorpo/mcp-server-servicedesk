package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import pl.aiwcorpo.mcp.servicedesk.integration.ServicedeskApiProperties;
import pl.aiwcorpo.mcp.servicedesk.integration.ServicedeskBackendProperties;
import pl.aiwcorpo.mcp.servicedesk.integration.ServicedeskResourceAdapter;

/** What the production configuration must keep, checked without starting a server. */
class ProductionConfigurationTest {

    private static final ServicedeskApiProperties REFERENCE_API = new ServicedeskApiProperties(null,
            "/api/v1/tickets/{ticketId}",
            new ServicedeskApiProperties.Queue("/api/v1/queues/{queue}", "/id", "/classification"),
            new ServicedeskApiProperties.Search("/api/v1/queues/{queue}/tickets", null, "q", "limit", "/items"),
            new ServicedeskApiProperties.Note("/api/v1/tickets/{ticketId}/notes", "POST", "/body", null, "/id"),
            new ServicedeskApiProperties.Fields("/id", "/queue", "/subject", "/status", "/priority", "/createdAt",
                    "/updatedAt", "/description"));

    @Test
    void outsideDevTheServerDemandsAClientCertificate() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        PropertySource<?> production = documents.stream()
                .filter(document -> "!dev".equals(document.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();
        assertThat(String.valueOf(production.getProperty("server.ssl.client-auth"))).isEqualTo("need");
        assertThat(String.valueOf(production.getProperty("server.ssl.bundle"))).isEqualTo("mcp-server");
        assertThat(documents).allSatisfy(document -> {
            Object clientAuth = document.getProperty("server.ssl.client-auth");
            assertThat(clientAuth == null || "need".equals(String.valueOf(clientAuth))).isTrue();
        });
    }

    @Test
    void theProductionConfigurationNeedsTheSignedBundleAndKeepsItsStateButAllowsNoTestKeys() throws IOException {
        PropertySource<?> base = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();

        assertThat(base.getProperty("aiwcorpo.mcp.platform.policy.main.url")).isNotNull();
        assertThat(base.getProperty("aiwcorpo.mcp.platform.policy.main.public-key-file")).isNotNull();
        assertThat(base.getProperty("aiwcorpo.mcp.platform.policy.state-dir")).isNotNull();
        assertThat(base.getProperty("aiwcorpo.mcp.platform.policy.allow-test-keys")).isNull();
        assertThat(String.valueOf(base.getProperty("aiwcorpo.mcp.platform.manifest.accepted-credential-types")))
                .contains("service-account");
    }

    @Test
    void outsideDevTheBackendMustBeHttps() {
        assertThatThrownBy(() -> adapter("http://servicedesk.internal:8080", new MockEnvironment()))
                .hasMessageContaining("https");
        assertThatCode(() -> adapter("https://backend.internal", new MockEnvironment())).doesNotThrowAnyException();

        assertThatCode(() -> new ServicedeskResourceAdapter(
                new ServicedeskBackendProperties("http://servicedesk.internal:8080", null, null, null, null, true),
                REFERENCE_API, new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), new MockEnvironment()))
                .as("explicit opt-in for the dev environment's mocks").doesNotThrowAnyException();

        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThatCode(() -> adapter("http://localhost:18081", dev)).doesNotThrowAnyException();
    }

    @Test
    void devIsReachableOnLoopbackOnly() throws IOException {
        Map<String, Object> dev = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst().getSource();
        assertThat(String.valueOf(dev.get("server.address"))).isEqualTo("127.0.0.1");
    }

    private static ServicedeskResourceAdapter adapter(String baseUrl, MockEnvironment environment) {
        return new ServicedeskResourceAdapter(new ServicedeskBackendProperties(baseUrl, null, null, null, null, false),
                REFERENCE_API, new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), environment);
    }
}
