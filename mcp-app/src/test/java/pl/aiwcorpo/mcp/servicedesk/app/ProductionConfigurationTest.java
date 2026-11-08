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
    void theServerAsksForNoClientCertificateAndLeavesTlsToTheStarter() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        // The gateway proves itself with the internal JWT, not with a certificate; the certificate of the server comes from
        // MCP_TLS_*, or the starter generates one (CC-249), so the file sets neither the client auth nor a bundle.
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.containsProperty("server.ssl.client-auth")).isFalse();
            assertThat(document.containsProperty("server.ssl.bundle")).isFalse();
            assertThat(document.containsProperty("spring.ssl.bundle.jks.mcp-server.keystore.location")).isFalse();
        });
    }

    @Test
    void thePolicyDataIsPushedByTheGatewayAndNoPolicyBundleSettingRemains() throws IOException {
        Map<String, Object> base = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst().getSource();

        assertThat(base.containsKey("aiwcorpo.mcp.platform.policy-data.url")).as("pushed by the gateway").isFalse();
        assertThat(base.keySet()).noneMatch(key -> key.startsWith("aiwcorpo.mcp.platform.policy."));
    }

    @Test
    void theServiceAccountIsTheOnlyCredentialTypeAndNoQueueIsOpenedByConfiguration() throws IOException {
        String types = "aiwcorpo.mcp.platform.manifest.accepted-credential-types";
        PropertySource<?> base = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();
        assertThat(base.getProperty(types + "[0]")).isEqualTo("service-account");
        assertThat(base.getProperty(types + "[1]")).isNull();

        for (String resource : List.of("application.yml", "application-dev.yml")) {
            PropertySource<?> document = new YamlPropertySourceLoader()
                    .load(resource, new ClassPathResource(resource)).getFirst();
            assertThat(document.getProperty("servicedesk.access.allowed-queues")).isNull();
            assertThat(document.getProperty("servicedesk.access.credential-mode")).isNull();
        }
        assertThat(new YamlPropertySourceLoader().load("application-dev.yml", new ClassPathResource("application-dev.yml"))
                .getFirst().getProperty(types + "[0]")).as("dev does not widen the base set").isNull();
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
