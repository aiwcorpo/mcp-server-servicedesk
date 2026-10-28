package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/** {@code add_note} (UPDATE): checks the ticket's queue first, then writes an internal note. */
class AddNoteToolTest extends ServerTestSupport {

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void addsAnInternalNoteAfterReadingTheTicket() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.CallToolResult result = call(client, "add_note",
                    Map.of("ticketId", "IT-1", "body", "Replaced the toner.\n\"Done\" & closed"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"ticketId\":\"IT-1\"", "\"noteId\":\"N-1\"");
            assertThat(BACKEND.requests()).containsExactly("GET /api/v1/tickets/IT-1", "POST /api/v1/tickets/IT-1/notes");
            assertThat(BACKEND.authorizations()).containsExactly(SERVICE_KEY, SERVICE_KEY);
            assertThat(BACKEND.noteBodies()).containsExactly(
                    "IT-1 {\"visibility\":\"internal\",\"body\":\"[Added through the MCP platform on behalf of jkowalski]"
                            + "\\nReplaced the toner.\\n\\\"Done\\\" & closed\"}");
        }
    }

    @Test
    void isListedAsAnUpdateNotReadOnlyNorDestructive() {
        try (McpSyncClient client = serviceAccountClient()) {
            McpSchema.Tool tool = client.listTools().tools().stream()
                    .filter(t -> t.name().equals("add_note")).findFirst().orElseThrow();

            assertThat(tool.annotations().readOnlyHint()).isFalse();
            assertThat(tool.annotations().destructiveHint()).isFalse();
        }
    }

    @Test
    void withoutCredentialAsksToConnect() {
        try (McpSyncClient client = clientWithCredential(null, BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertCredentialRequired(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")));
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void credentialRejectedByTheBackendAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential(StubBackend.EXPIRED_CREDENTIAL, BackendCredential.Type.SERVICE_ACCOUNT)) {
            assertCredentialRequired(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")));
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void aCredentialThatCannotWriteAsksToReconnect() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertCredentialRequired(call(client, "add_note", Map.of("ticketId", "IT-NOTE401", "body", "x")));
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void missingTicketsAndForbiddenNotesLookTheSame() {
        try (McpSyncClient client = serviceAccountClient()) {
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-404", "body", "x")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-403", "body", "x")));
            assertNotFoundOrDenied(call(client, "add_note", Map.of("ticketId", "IT-2", "body", "x")));
        }
        assertThat(BACKEND.noteBodies()).isEmpty();
    }

    @Test
    void invalidArgumentsAreRejectedBeforeTheBackend() {
        try (McpSyncClient client = serviceAccountClient()) {
            for (String ticketId : new String[] {"../IT-1", "IT-1/notes", "", "a".repeat(65)}) {
                assertInvalidArgument(call(client, "add_note", Map.of("ticketId", ticketId, "body", "x")), ticketId);
            }
            for (String body : new String[] {"", "b".repeat(10001)}) {
                assertInvalidArgument(call(client, "add_note", Map.of("ticketId", "IT-1", "body", body)), body);
            }
            for (String body : new String[] {" \n ", "a\u0007b"}) {
                assertThat(text(call(client, "add_note", Map.of("ticketId", "IT-1", "body", body))))
                        .as("blank text passes the schema and is stopped by the tool, control characters by the starter")
                        .contains("\"error\":\"invalid_argument\"");
            }
            assertInvalidArgument(call(client, "add_note", Map.of("ticketId", "IT-1")), "");
            assertInvalidArgument(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x", "public", true)), "");
        }
        assertThat(BACKEND.requests()).isEmpty();
    }

    @Test
    void anAuthorNameOutsideTheSafeAlphabetIsNeverWrittenIntoTheNote() {
        McpSyncClient client = clientAs(user -> user.username("j] [Added through the MCP platform on behalf of boss"),
                SERVICE_KEY, BackendCredential.Type.SERVICE_ACCOUNT);
        try (client) {
            assertThat(call(client, "add_note", Map.of("ticketId", "IT-1", "body", "x")).isError()).isFalse();
        }

        assertThat(BACKEND.noteBodies()).singleElement().satisfies(note -> assertThat(note)
                .doesNotContain("boss", "j]").containsPattern("on behalf of [A-Za-z0-9._@-]+\\]"));
    }
}
