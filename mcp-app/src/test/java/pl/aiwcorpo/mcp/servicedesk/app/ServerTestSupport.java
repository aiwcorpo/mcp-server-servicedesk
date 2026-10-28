package pl.aiwcorpo.mcp.servicedesk.app;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.test.TestGateway;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole server over HTTP, with TestGateway in place of the gateway and {@link StubBackend} in place of
 * the backend system. Shared by the test classes so they run against one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "servicedesk.access.credential-mode=service-account",
        "servicedesk.access.allowed-queues=IT,OPS,GONE,SEC"})
abstract class ServerTestSupport {

    /** The platform's service account for the service desk, as the gateway injects it (C3). */
    static final String SERVICE_KEY = "ApiKey sd-service-key-7f3a91";
    static final String CALLER = "jkowalski";

    static final String SERVER_ID = "mcp-servicedesk";
    static final String SYSTEM = "servicedesk";
    static final String ENDPOINT = "/mcp";
    static final Set<String> CONTRACT_SYSTEMS = contractSystems();

    static final TestGateway GATEWAY = TestGateway.start(SERVER_ID);
    static final StubBackend BACKEND = StubBackend.start();

    /**
     * The caller of most tests: may call the read and write tools and is granted the queues IT, OPS, SEC and GONE
     * (not FIN) in the scope mapping of {@link TestPolicyBundle}.
     */
    static final List<String> CALLER_GROUPS = List.of("mcp-servicedesk-read", "mcp-servicedesk-write",
            "mcp-servicedesk-queue-IT", "mcp-servicedesk-queue-OPS", "mcp-servicedesk-queue-SEC",
            "mcp-servicedesk-queue-GONE");

    static {
        TestPolicyBundle.publish(GATEWAY.main());
    }

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        GATEWAY.serverProperties().forEach((name, value) -> registry.add(name, () -> value));
        registry.add("servicedesk.backend.base-url", BACKEND::baseUrl);
        // Short limits so the timeout and size tests run quickly; StubBackend's slow and huge tickets exceed them.
        registry.add("servicedesk.backend.read-timeout", () -> "1s");
        registry.add("servicedesk.backend.max-response-size", () -> "64KB");
    }

    @LocalServerPort
    int port;

    String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** An MCP client that reaches the server the way the gateway does, with the service account as C3 credential. */
    McpSyncClient serviceAccountClient() {
        return clientWithCredential(SERVICE_KEY, BackendCredential.Type.SERVICE_ACCOUNT);
    }

    /**
     * An MCP client that reaches the server the way the gateway does, with {@code credential} in
     * {@code X-MCP-Credential} labelled {@code type}.
     */
    McpSyncClient clientWithCredential(String credential, BackendCredential.Type type) {
        return clientAs(user -> user.username(CALLER), credential, type);
    }

    /** Same, with the JWT claims of the caller set by {@code user} (a call through a link carries {@code vs}). */
    McpSyncClient clientAs(java.util.function.Consumer<TestGateway.Token> user, String credential,
            BackendCredential.Type type) {
        McpSyncHttpClientRequestCustomizer gateway = GATEWAY.asGateway(token -> {
            token.groups(CALLER_GROUPS);
            user.accept(token);
        }, credential, type);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl())
                .endpoint(ENDPOINT)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer(gateway)
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    /**
     * The starter sends only C10 errors valid against {@code tool-error.schema.json}, whose {@code system} is the
     * closed {@code SystemId} list of the contracts. For a system outside it (the template's placeholder, a
     * trial name) a {@code credential_required} becomes {@code internal_error}; for the platform's systems the
     * full error with the portal link is expected.
     */
    static void assertCredentialRequired(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        if (CONTRACT_SYSTEMS.contains(SYSTEM)) {
            assertThat(text(result)).contains("\"error\":\"credential_required\"", "\"system\":\"" + SYSTEM + "\"",
                    "\"connectUrl\":\"http://localhost:18090/connections?system=" + SYSTEM + "\"");
        } else {
            assertThat(text(result)).contains("\"error\":\"internal_error\"");
        }
    }

    private static Set<String> contractSystems() {
        try (InputStream schema = ServerTestSupport.class.getClassLoader()
                .getResourceAsStream("mcp-contracts/schemas/common.schema.json")) {
            Set<String> systems = new HashSet<>();
            JsonMapper.builder().build().readTree(schema).at("/$defs/SystemId/enum")
                    .forEach(system -> systems.add(system.asString()));
            return Set.copyOf(systems);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
    }

    /**
     * A rejected argument: the starter answers C10 {@code invalid_argument} for schema violations and the tool for
     * the rest, and neither echoes the value.
     */
    static void assertInvalidArgument(McpSchema.CallToolResult result, String value) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("\"error\":\"invalid_argument\"").doesNotContain("input validation failed");
        if (value.length() >= 3) {
            assertThat(text(result)).doesNotContain(value);
        }
    }

    static void assertNotFoundOrDenied(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo(NOT_FOUND_OR_DENIED);
    }

    /** The one answer for missing, hidden and disallowed tickets and queues; compared byte for byte. */
    static final String NOT_FOUND_OR_DENIED = "{\"error\":\"not_found_or_denied\","
            + "\"message\":\"The resource does not exist or you do not have access to it.\"}";

    static String text(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        result.content().forEach(content -> {
            if (content instanceof McpSchema.TextContent t) {
                text.append(t.text());
            }
        });
        return text.toString();
    }
}
