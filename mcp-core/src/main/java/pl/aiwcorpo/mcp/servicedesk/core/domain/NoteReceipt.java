package pl.aiwcorpo.mcp.servicedesk.core.domain;

/**
 * Confirmation of {@code add_note}.
 *
 * @param noteId id the service desk gave the note, or {@code null} when its response carries none
 */
public record NoteReceipt(String ticketId, String noteId) {
}
