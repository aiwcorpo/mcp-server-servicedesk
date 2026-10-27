package pl.aiwcorpo.mcp.servicedesk.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.security.CredentialContext;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.TicketSummary;
import pl.aiwcorpo.mcp.servicedesk.core.port.out.ServicedeskResourcePort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the service desk's REST API with the credential the gateway resolved for this request (C3): the user's
 * token or the platform's service account, never a secret of this server.
 *
 * <p>Error mapping matters for security: 404 and 403 both become "not visible" (one client-facing code,
 * {@code not_found_or_denied}), and 401 means the credential no longer works ({@code credential_required}). A
 * failed call becomes an exception with the status alone, so no response body reaches a message or a log line.
 */
@Component
@EnableConfigurationProperties({ServicedeskBackendProperties.class, ServicedeskApiProperties.class})
public class ServicedeskResourceAdapter implements ServicedeskResourcePort {

    /** System id of this server: {@code server-id} is {@code mcp-<system>}, and the starter refuses any other. */
    static final String SYSTEM = "servicedesk";

    private final RestClient client;
    private final ServicedeskApi api;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ServicedeskResourceAdapter(ServicedeskBackendProperties backend, ServicedeskApiProperties apiProperties,
            ObjectProvider<SslBundles> sslBundles) {
        this.api = new ServicedeskApi(backend.baseUrl(), apiProperties, mapper);
        this.client = RestClient.builder()
                .requestFactory(requestFactory(backend, sslBundles))
                .defaultStatusHandler(status -> status.isError() || status.is3xxRedirection(), (request, response) -> {
                    // Status only: the body and headers of a failed call may echo the credential or carry backend
                    // detail, and the exception message ends up in the starter's tool_failed log line.
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is3xxRedirection()) {
                        throw new ServicedeskResponseException();
                    }
                    throw status.is4xxClientError()
                            ? HttpClientErrorException.create(status, "", HttpHeaders.EMPTY, new byte[0], null)
                            : HttpServerErrorException.create(status, "", HttpHeaders.EMPTY, new byte[0], null);
                })
                .build();
    }

    @Override
    public Optional<List<TicketSummary>> searchTickets(String queue, String text, int limit) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return get(api.searchUri(queue, text, limit + 1), credential)
                .map(response -> api.ticketJson().summaries(api.results(response), limit + 1));
    }

    @Override
    public Optional<Ticket> findTicket(String ticketId) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        return get(api.ticketUri(ticketId), credential).flatMap(api.ticketJson()::ticket);
    }

    @Override
    public Optional<String> addNote(String ticketId, String body) {
        BackendCredential credential = CredentialContext.require(SYSTEM);
        try {
            String response = client.method(api.noteMethod())
                    .uri(api.noteUri(ticketId))
                    .headers(headers -> api.applyCredential(credential, headers))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(api.noteBody(body))
                    .retrieve()
                    .body(String.class);
            return Optional.of(api.noteId(parseLeniently(response)));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw new CredentialRequiredException(SYSTEM);
        }
    }

    @Override
    public boolean callerIsServiceAccount() {
        return CredentialContext.type().map(type -> type == BackendCredential.Type.SERVICE_ACCOUNT).orElse(false);
    }

    private Optional<JsonNode> get(URI uri, BackendCredential credential) {
        try {
            String response = client.get()
                    .uri(uri)
                    .headers(headers -> api.applyCredential(credential, headers))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw new ServicedeskResponseException();
            }
            return Optional.of(parse(response));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw new CredentialRequiredException(SYSTEM);
        }
    }

    private JsonNode parse(String response) {
        try {
            return mapper.readTree(response);
        } catch (RuntimeException notJson) {
            // Without the cause: the parser's message quotes the response.
            throw new ServicedeskResponseException();
        }
    }

    /** The note was written already; an unreadable confirmation only loses the note id. */
    private JsonNode parseLeniently(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(response);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    private static JdkClientHttpRequestFactory requestFactory(ServicedeskBackendProperties properties,
            ObjectProvider<SslBundles> sslBundles) {
        // Redirects are not followed: the credential header must not travel to wherever a backend points.
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.sslBundle() != null && !properties.sslBundle().isBlank()) {
            http.sslContext(sslBundles.getObject().getBundle(properties.sslBundle()).createSslContext());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
