package pl.aiwcorpo.mcp.servicedesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import tools.jackson.databind.json.JsonMapper;

class ServicedeskApiTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final ServicedeskApiProperties.Fields FIELDS = new ServicedeskApiProperties.Fields(
            "/id", "/queue", "/subject", "/status", "", "", "/updatedAt", "/description");

    static ServicedeskApiProperties reference() {
        return new ServicedeskApiProperties("Authorization", "/api/v1/tickets/{ticketId}",
                new ServicedeskApiProperties.Search("/api/v1/queues/{queue}/tickets", null, "q", "limit", "/items"),
                new ServicedeskApiProperties.Note("/api/v1/tickets/{ticketId}/notes", "POST", "/body",
                        List.of(new ServicedeskApiProperties.StaticField("/visibility", "\"internal\"")), "/id"),
                FIELDS);
    }

    private final ServicedeskApi api = new ServicedeskApi("https://sd.internal/base", reference(), MAPPER);

    @Test
    void searchTextIsAnEncodedValueThatCannotAddParametersOrChangeThePath() {
        URI uri = api.searchUri("IT", "a&limit=1000 #x/../?y=+1%", 21);

        assertThat(uri.getRawPath()).isEqualTo("/base/api/v1/queues/IT/tickets");
        var params = UriComponentsBuilder.fromUri(uri).build(true).getQueryParams();
        assertThat(params).containsOnlyKeys("q", "limit");
        assertThat(params.getFirst("limit")).isEqualTo("21");
        assertThat(URLDecoder.decode(params.getFirst("q"), StandardCharsets.UTF_8))
                .isEqualTo("a&limit=1000 #x/../?y=+1%");
        assertThat(uri.getRawFragment()).isNull();
    }

    @Test
    void queueCanGoAsAParameterWhenThePathHasNoPlaceholder() {
        ServicedeskApiProperties properties = new ServicedeskApiProperties(null, "/rest/ticket/{ticketId}",
                new ServicedeskApiProperties.Search("/rest/search", "queue", "text", "", ""),
                new ServicedeskApiProperties.Note("/rest/ticket/{ticketId}", "PATCH", "/work_notes", null, ""),
                FIELDS);
        ServicedeskApi patchApi = new ServicedeskApi("http://sd.internal", properties, MAPPER);

        URI uri = patchApi.searchUri("HR", "vpn", 21);

        assertThat(uri.getPath()).isEqualTo("/rest/search");
        assertThat(uri.getQuery()).isEqualTo("queue=HR&text=vpn");
        assertThat(patchApi.noteMethod()).isEqualTo(HttpMethod.PATCH);
        assertThat(patchApi.noteBody("x")).isEqualTo("{\"work_notes\":\"x\"}");
        assertThat(patchApi.noteId(MAPPER.readTree("{\"id\":\"N-1\"}"))).isEmpty();
    }

    @Test
    void noteBodyCarriesTheTextAsAJsonValueAndTheStaticFields() {
        ServicedeskApiProperties nested = new ServicedeskApiProperties(null, "/t/{ticketId}",
                new ServicedeskApiProperties.Search("/q/{queue}", null, "q", null, null),
                new ServicedeskApiProperties.Note("/t/{ticketId}/comments", null, "/ticket/comment/body",
                        List.of(new ServicedeskApiProperties.StaticField("/ticket/comment/public", "false")), "/a/id"),
                FIELDS);
        ServicedeskApi nestedApi = new ServicedeskApi("http://sd.internal", nested, MAPPER);

        assertThat(api.noteBody("say \"hi\"\n}")).isEqualTo("{\"visibility\":\"internal\",\"body\":\"say \\\"hi\\\"\\n}\"}");
        assertThat(nestedApi.noteBody("x"))
                .isEqualTo("{\"ticket\":{\"comment\":{\"public\":false,\"body\":\"x\"}}}");
        assertThat(nestedApi.noteId(MAPPER.readTree("{\"a\":{\"id\":42}}"))).isEqualTo("42");
        assertThat(nestedApi.noteId(MAPPER.readTree("{\"a\":{\"id\":\"<b>\"}}"))).isEmpty();
        assertThat(nestedApi.noteMethod()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void credentialGoesToTheConfiguredHeader() {
        BackendCredential key = new BackendCredential(BackendCredential.Scheme.API_KEY, "k-123",
                BackendCredential.Type.SERVICE_ACCOUNT);
        HttpHeaders authorization = new HttpHeaders();
        api.applyCredential(key, authorization);
        assertThat(authorization.getFirst("Authorization")).isEqualTo("ApiKey k-123");

        ServicedeskApiProperties props = reference();
        ServicedeskApi apiKeyHeader = new ServicedeskApi("http://sd.internal", new ServicedeskApiProperties(
                "X-API-Key", props.ticketPath(), props.search(), props.note(), props.fields()), MAPPER);
        HttpHeaders custom = new HttpHeaders();
        apiKeyHeader.applyCredential(key, custom);
        assertThat(custom.getFirst("X-API-Key")).isEqualTo("k-123");
        assertThat(custom.containsHeader("Authorization")).isFalse();
    }

    @Test
    void badConfigurationStopsTheStart() {
        ServicedeskApiProperties ok = reference();
        assertInvalid("http://sd.internal", with(ok, "/api/tickets/{id}"), "ticket-path");
        assertInvalid("http://sd.internal", with(ok, "/api/{ticketId}/{ticketId}"), "ticket-path");
        assertInvalid("http://sd.internal", with(ok, "/api/../{ticketId}"), "ticket-path");
        assertInvalid("http://sd.internal", with(ok, "/api/tickets/{ticketId}?x=1"), "ticket-path");
        assertInvalid("http://sd.internal", with(ok, "api/tickets/{ticketId}"), "ticket-path");
        assertInvalid("", ok, "base-url");
        assertInvalid("ftp://sd.internal", ok, "base-url");
        assertInvalid("http://sd.internal?token=1", ok, "base-url");
        assertInvalid("http://sd.internal", new ServicedeskApiProperties(null, ok.ticketPath(),
                new ServicedeskApiProperties.Search("/search", "", "q", null, null), ok.note(), ok.fields()),
                "queue-param");
        assertInvalid("http://sd.internal", new ServicedeskApiProperties(null, ok.ticketPath(), ok.search(),
                new ServicedeskApiProperties.Note("/n/{ticketId}", "DELETE", "/body", null, null), ok.fields()),
                "method");
        assertInvalid("http://sd.internal", new ServicedeskApiProperties(null, ok.ticketPath(), ok.search(),
                new ServicedeskApiProperties.Note("/n/{ticketId}", "POST", "/body",
                        List.of(new ServicedeskApiProperties.StaticField("/x", "not json")), null), ok.fields()),
                "static-fields");
        assertInvalid("http://sd.internal", new ServicedeskApiProperties(null, ok.ticketPath(), ok.search(),
                new ServicedeskApiProperties.Note("/n/{ticketId}", "POST", "/body",
                        List.of(new ServicedeskApiProperties.StaticField("/body", "1")), null), ok.fields()),
                "static-fields");
        assertInvalid("http://sd.internal", new ServicedeskApiProperties(null, ok.ticketPath(), ok.search(),
                ok.note(), new ServicedeskApiProperties.Fields("/id", "", null, null, null, null, null, null)),
                "fields.queue");
        assertInvalid("http://sd.internal", new ServicedeskApiProperties("Bad Header", ok.ticketPath(),
                ok.search(), ok.note(), ok.fields()), "credential-header");
    }

    private static ServicedeskApiProperties with(ServicedeskApiProperties p, String ticketPath) {
        return new ServicedeskApiProperties(p.credentialHeader(), ticketPath, p.search(), p.note(), p.fields());
    }

    private static void assertInvalid(String baseUrl, ServicedeskApiProperties properties, String mentioned) {
        assertThatThrownBy(() -> new ServicedeskApi(baseUrl, properties, MAPPER))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(mentioned);
    }
}
