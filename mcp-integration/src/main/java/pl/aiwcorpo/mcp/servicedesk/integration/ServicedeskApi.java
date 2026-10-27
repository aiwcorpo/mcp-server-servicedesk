package pl.aiwcorpo.mcp.servicedesk.integration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The validated API shape: builds request URIs and bodies. Only configuration is spliced into a path; values
 * from tool arguments go in as URI variables or JSON values, which the builder encodes strictly, so a search
 * text cannot add parameters or change the path.
 */
public final class ServicedeskApi {

    private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9/_.{}-]*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");
    private static final Pattern PARAM = Pattern.compile("[A-Za-z0-9_.\\[\\]-]{1,64}");
    private static final Pattern HEADER = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern POINTER = Pattern.compile("(/[A-Za-z0-9_.-]+)+");
    private static final Set<HttpMethod> NOTE_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

    private final URI baseUrl;
    private final String credentialHeader;
    private final String ticketPath;
    private final String searchPath;
    private final String searchQueueParam;
    private final String searchTextParam;
    private final String searchLimitParam;
    private final String resultsPointer;
    private final String notePath;
    private final HttpMethod noteMethod;
    private final String noteBodyPointer;
    private final Map<String, JsonNode> noteStaticFields;
    private final String noteIdPointer;
    private final TicketJson ticketJson;
    private final JsonMapper mapper;

    public ServicedeskApi(String baseUrl, ServicedeskApiProperties properties, JsonMapper mapper) {
        this.mapper = mapper;
        this.baseUrl = requireBaseUrl(baseUrl);
        if (properties == null || properties.search() == null || properties.note() == null
                || properties.fields() == null) {
            throw invalid("servicedesk.api.search, .note and .fields must be configured");
        }
        this.credentialHeader = blank(properties.credentialHeader()) ? HttpHeaders.AUTHORIZATION
                : require(properties.credentialHeader(), HEADER, "servicedesk.api.credential-header");
        this.ticketPath = path(properties.ticketPath(), "servicedesk.api.ticket-path", Set.of("ticketId"));

        ServicedeskApiProperties.Search search = properties.search();
        Set<String> searchPlaceholders = placeholders(search.path());
        this.searchPath = path(search.path(), "servicedesk.api.search.path",
                searchPlaceholders.contains("queue") ? Set.of("queue") : Set.of());
        this.searchQueueParam = searchPlaceholders.contains("queue") ? null
                : require(search.queueParam(), PARAM, "servicedesk.api.search.queue-param (the path has no {queue})");
        this.searchTextParam = require(search.textParam(), PARAM, "servicedesk.api.search.text-param");
        this.searchLimitParam = blank(search.limitParam()) ? null
                : require(search.limitParam(), PARAM, "servicedesk.api.search.limit-param");
        this.resultsPointer = optionalPointer(search.resultsPointer(), "servicedesk.api.search.results-pointer");

        ServicedeskApiProperties.Note note = properties.note();
        this.notePath = path(note.path(), "servicedesk.api.note.path", Set.of("ticketId"));
        this.noteMethod = noteMethod(note.method());
        this.noteBodyPointer = require(note.bodyPointer(), POINTER, "servicedesk.api.note.body-pointer");
        this.noteStaticFields = staticFields(note.staticFields());
        this.noteIdPointer = optionalPointer(note.idPointer(), "servicedesk.api.note.id-pointer");

        ServicedeskApiProperties.Fields fields = properties.fields();
        this.ticketJson = new TicketJson(
                require(fields.id(), POINTER, "servicedesk.api.fields.id"),
                require(fields.queue(), POINTER, "servicedesk.api.fields.queue"),
                optionalPointer(fields.subject(), "servicedesk.api.fields.subject"),
                optionalPointer(fields.status(), "servicedesk.api.fields.status"),
                optionalPointer(fields.priority(), "servicedesk.api.fields.priority"),
                optionalPointer(fields.created(), "servicedesk.api.fields.created"),
                optionalPointer(fields.updated(), "servicedesk.api.fields.updated"),
                optionalPointer(fields.description(), "servicedesk.api.fields.description"));
    }

    URI ticketUri(String ticketId) {
        return builder(ticketPath).encode().buildAndExpand(Map.of("ticketId", ticketId)).toUri();
    }

    URI noteUri(String ticketId) {
        return builder(notePath).encode().buildAndExpand(Map.of("ticketId", ticketId)).toUri();
    }

    URI searchUri(String queue, String text, int limit) {
        UriComponentsBuilder builder = builder(searchPath);
        Map<String, Object> variables = new HashMap<>();
        variables.put("queue", queue);
        variables.put("text", text);
        variables.put("limit", limit);
        if (searchQueueParam != null) {
            builder.queryParam(searchQueueParam, "{queue}");
        }
        builder.queryParam(searchTextParam, "{text}");
        if (searchLimitParam != null) {
            builder.queryParam(searchLimitParam, "{limit}");
        }
        return builder.encode().buildAndExpand(variables).toUri();
    }

    HttpMethod noteMethod() {
        return noteMethod;
    }

    String noteBody(String text) {
        ObjectNode root = mapper.createObjectNode();
        noteStaticFields.forEach((pointer, value) -> put(root, pointer, value));
        put(root, noteBodyPointer, mapper.getNodeFactory().stringNode(text));
        return mapper.writeValueAsString(root);
    }

    /** Note id from the response, or empty when the API returns none or something that is not an id. */
    String noteId(JsonNode response) {
        if (noteIdPointer.isEmpty() || response == null) {
            return "";
        }
        String id = TicketJson.scalar(response.at(noteIdPointer));
        return id != null && pl.aiwcorpo.mcp.servicedesk.core.domain.Identifiers.isTicketId(id) ? id : "";
    }

    JsonNode results(JsonNode response) {
        return resultsPointer.isEmpty() ? response : response.at(resultsPointer);
    }

    TicketJson ticketJson() {
        return ticketJson;
    }

    void applyCredential(BackendCredential credential, HttpHeaders headers) {
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(credentialHeader)) {
            credential.applyTo(headers);
        } else {
            headers.set(credentialHeader, credential.value());
        }
    }

    private UriComponentsBuilder builder(String path) {
        return UriComponentsBuilder.fromUri(baseUrl).path(path);
    }

    private static void put(ObjectNode root, String pointer, JsonNode value) {
        String[] segments = pointer.substring(1).split("/");
        ObjectNode parent = root;
        for (int i = 0; i < segments.length - 1; i++) {
            JsonNode child = parent.get(segments[i]);
            parent = child instanceof ObjectNode object ? object : parent.putObject(segments[i]);
        }
        parent.set(segments[segments.length - 1], value);
    }

    private Map<String, JsonNode> staticFields(List<ServicedeskApiProperties.StaticField> fields) {
        Map<String, JsonNode> result = new java.util.LinkedHashMap<>();
        for (ServicedeskApiProperties.StaticField field : fields == null
                ? List.<ServicedeskApiProperties.StaticField>of() : fields) {
            String pointer = require(field.pointer(), POINTER, "servicedesk.api.note.static-fields[].pointer");
            if (overlaps(pointer, noteBodyPointer)) {
                throw invalid("servicedesk.api.note.static-fields must not overwrite the note body pointer");
            }
            for (String other : result.keySet()) {
                if (overlaps(pointer, other)) {
                    throw invalid("servicedesk.api.note.static-fields pointers must not overlap: " + pointer);
                }
            }
            if (blank(field.value())) {
                throw invalid("servicedesk.api.note.static-fields[].value must be a JSON literal");
            }
            JsonNode value;
            try {
                value = mapper.readTree(field.value());
            } catch (RuntimeException notJson) {
                throw invalid("servicedesk.api.note.static-fields value for " + pointer + " is not valid JSON");
            }
            result.put(pointer, value);
        }
        return Map.copyOf(result);
    }

    /** Equal pointers, or one inside the other: the later {@code put} would silently replace the earlier value. */
    private static boolean overlaps(String a, String b) {
        return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/");
    }

    private static HttpMethod noteMethod(String method) {
        HttpMethod parsed = blank(method) ? HttpMethod.POST : HttpMethod.valueOf(method.trim().toUpperCase());
        if (!NOTE_METHODS.contains(parsed)) {
            throw invalid("servicedesk.api.note.method must be POST, PUT or PATCH");
        }
        return parsed;
    }

    private static String path(String value, String name, Set<String> requiredPlaceholders) {
        if (blank(value) || !PATH.matcher(value).matches() || value.contains("..") || value.contains("//")) {
            throw invalid(name + " must be an absolute path of letters, digits, '/', '_', '.', '-' and placeholders");
        }
        Set<String> found = placeholders(value);
        if (!found.equals(requiredPlaceholders) || countPlaceholders(value) != requiredPlaceholders.size()) {
            throw invalid(name + " must contain exactly the placeholders " + new TreeSet<>(requiredPlaceholders));
        }
        return value;
    }

    private static Set<String> placeholders(String value) {
        Set<String> names = new TreeSet<>();
        if (value != null) {
            Matcher matcher = PLACEHOLDER.matcher(value);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static int countPlaceholders(String value) {
        List<String> all = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            all.add(matcher.group(1));
        }
        return all.size();
    }

    private static String optionalPointer(String value, String name) {
        return blank(value) ? "" : require(value, POINTER, name);
    }

    private static String require(String value, Pattern pattern, String name) {
        if (blank(value) || !pattern.matcher(value).matches()) {
            throw invalid(name + " is missing or has a wrong format");
        }
        return value;
    }

    private static URI requireBaseUrl(String baseUrl) {
        if (blank(baseUrl)) {
            throw invalid("servicedesk.backend.base-url (MCP_SERVICEDESK_URL) must point at the service desk API");
        }
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalid("servicedesk.backend.base-url must be an absolute http(s) URL without query or fragment");
        }
        return uri;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }
}
