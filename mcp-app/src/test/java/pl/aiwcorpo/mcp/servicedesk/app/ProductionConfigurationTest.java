package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.SpringVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import pl.aiwcorpo.mcp.servicedesk.integration.ServicedeskApiProperties;
import pl.aiwcorpo.mcp.servicedesk.integration.ServicedeskBackendProperties;
import pl.aiwcorpo.mcp.servicedesk.integration.ServicedeskResourceAdapter;
import tools.jackson.core.Version;
import tools.jackson.core.json.PackageVersion;

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

    @Test
    void theEmbeddedTomcatAndJacksonIncludeTheirSecurityFixes() {
        // The Boot parent stays on the starter's version, so the pom overrides these two; a parent bump must not lower them.
        int[] tomcat = Arrays.stream(ServerInfo.getServerNumber().split("\\.")).mapToInt(Integer::parseInt).toArray();
        assertThat(Arrays.compare(tomcat, new int[] {11, 0, 27})).as("Tomcat %s", ServerInfo.getServerNumber()).isNotNegative();
        assertThat(PackageVersion.VERSION).isGreaterThanOrEqualTo(new Version(3, 1, 7, null, null, null));
    }

    @Test
    void theOtherRuntimeLibrariesIncludeTheirSecurityFixes() throws IOException {
        assertAtLeast("spring-core", SpringVersion.getVersion(), "7.0.10");
        assertAtLeast("jackson-core 2", mavenVersion("com.fasterxml.jackson.core", "jackson-core"), "2.21.7");
        assertAtLeast("netty-common", mavenVersion("io.netty", "netty-common"), "4.2.19");
        assertAtLeast("log4j-api", mavenVersion("org.apache.logging.log4j", "log4j-api"), "2.25.5");
        assertAtLeast("guava", mavenVersion("com.google.guava", "guava"), "33.7.2");
    }

    private static String mavenVersion(String groupId, String artifactId) throws IOException {
        String resource = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        try (InputStream in = ProductionConfigurationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        }
    }

    private static void assertAtLeast(String library, String actual, String minimum) {
        assertThat(Arrays.compare(versionNumbers(actual), versionNumbers(minimum))).as("%s %s", library, actual)
                .isNotNegative();
    }

    private static int[] versionNumbers(String version) {
        return Arrays.stream(version.split("[^0-9]+")).filter(part -> !part.isEmpty()).mapToInt(Integer::parseInt).toArray();
    }

    private static ServicedeskResourceAdapter adapter(String baseUrl, MockEnvironment environment) {
        return new ServicedeskResourceAdapter(new ServicedeskBackendProperties(baseUrl, null, null, null, null, false),
                REFERENCE_API, new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), environment);
    }
}
