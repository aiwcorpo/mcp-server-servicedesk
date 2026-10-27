package pl.aiwcorpo.mcp.servicedesk.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.aiwcorpo.mcp.platform.auth.CallerContext;
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
 * failed call becomes an exception with the status alone, an unusable answer (redirect, unparsable or oversized
 * body) a {@link BackendResponseException}, so no response body reaches a message or a log line.
 */
@Component
@EnableConfigurationProperties({ServicedeskBackendProperties.class, ServicedeskApiProperties.class})
public class ServicedeskResourceAdapter implements ServicedeskResourcePort {

    /** System id of this server: {@code server-id} is {@code mcp-<system>}, and the starter refuses any other. */
    static final String SYSTEM = "servicedesk";

    private static final String DEV_PROFILE = "dev";
    private static final Pattern AUTHOR = Pattern.compile("[A-Za-z0-9._@-]{1,64}");
    private static final Logger LOG = LoggerFactory.getLogger(ServicedeskResourceAdapter.class);

    private final RestClient client;
    private final ServicedeskApi api;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ServicedeskResourceAdapter(ServicedeskBackendProperties backend, ServicedeskApiProperties apiProperties,
            ObjectProvider<SslBundles> sslBundles, Environment environment) {
        this.api = new ServicedeskApi(backend.baseUrl(), apiProperties, mapper);
        requireHttps(backend.baseUrl(), environment.matchesProfiles(DEV_PROFILE) || backend.allowPlainHttp());
        this.client = RestClient.builder()
                .requestFactory(requestFactory(backend, sslBundles))
                .requestInterceptor(new BoundedBodyInterceptor(backend.maxResponseSize().toBytes()))
                .defaultStatusHandler(status -> status.isError() || status.is3xxRedirection(), (request, response) -> {
                    // Status only: the body and headers of a failed call may echo the credential or carry backend
                    // detail, and the exception message ends up in the starter's tool_failed log line.
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is3xxRedirection()) {
                        // Redirects are not followed (the credential must not go elsewhere) and an empty body
                        // must not read as "no ticket": an SSO login page would turn into not_found_or_denied.
                        throw new BackendResponseException("redirect " + status.value());
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
                    .body(api.noteBody(credential.type() == BackendCredential.Type.SERVICE_ACCOUNT
                            ? attributed(body) : body))
                    .retrieve()
                    .body(String.class);
            return Optional.of(api.noteId(parseLeniently(response)));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw new CredentialRequiredException(SYSTEM);
        } catch (RestClientResponseException | ResourceAccessException statusOrIo) {
            throw statusOrIo;
        } catch (RestClientException unreadable) {
            throw new BackendResponseException("body not readable");
        }
    }

    @Override
    public boolean callerIsServiceAccount() {
        return CredentialContext.type().map(type -> type == BackendCredential.Type.SERVICE_ACCOUNT).orElse(false);
    }

    /**
     * The platform's service account is the note's author in the service desk, so the person on whose behalf it
     * writes goes into the text. The name comes from the gateway's verified token, never from tool arguments, and
     * is reduced to a safe character set.
     */
    static String attributed(String body) {
        String author = CallerContext.current()
                .flatMap(caller -> Stream.of(caller.username(), caller.subject())
                        .filter(name -> name != null && AUTHOR.matcher(name).matches()).findFirst())
                .orElse("unknown");
        return "[Added through the MCP platform on behalf of " + author + "]\n" + body;
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
                throw new BackendResponseException("empty body");
            }
            return Optional.of(parse(response));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw new CredentialRequiredException(SYSTEM);
        } catch (RestClientResponseException | ResourceAccessException statusOrIo) {
            // Status-only exceptions (see the status handler) and I/O failures: the starter maps 502-504 and
            // unreachable backends to upstream_unavailable.
            throw statusOrIo;
        } catch (RestClientException unreadable) {
            // A body that cannot be read (for example over the size limit): the converter's message quotes it.
            throw new BackendResponseException("body not readable");
        }
    }

    private JsonNode parse(String response) {
        try {
            return mapper.readTree(response);
        } catch (RuntimeException notJson) {
            // Without the cause: the parser's message quotes the response.
            throw new BackendResponseException("body not JSON");
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

    private static void requireHttps(String baseUrl, boolean plainHttpAllowed) {
        if (baseUrl != null && baseUrl.regionMatches(true, 0, "http://", 0, 7)) {
            if (!plainHttpAllowed) {
                // The credential travels in a header; over http it would cross the network in clear text.
                throw new IllegalStateException("servicedesk.backend.base-url must use https outside the 'dev' profile "
                        + "(servicedesk.backend.allow-plain-http exists for the mocks of mcp-platform-dev only)");
            }
            LOG.warn("event=backend_plain_http detail=\"credentials go to the backend unencrypted\"");
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
