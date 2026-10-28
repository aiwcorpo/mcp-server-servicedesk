package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shape of the service desk's REST API (D10: generic adapter, the product is not chosen yet). Paths, parameter
 * names and JSON pointers come from configuration; {@link ServicedeskApi} validates them at startup.
 *
 * @param credentialHeader header that carries the credential: {@code Authorization} gets {@code <scheme> <value>}
 *                         as the gateway sent it (C3), any other header gets the bare value (API key headers)
 * @param ticketPath       path of one ticket, with the {@code {ticketId}} placeholder
 * @param queue            one queue: its existence and classification
 * @param search           text search in one queue
 * @param note             adding a note to a ticket
 * @param fields           JSON pointers of the ticket fields, relative to a ticket object
 */
@ConfigurationProperties("servicedesk.api")
public record ServicedeskApiProperties(String credentialHeader, String ticketPath, Queue queue, Search search,
        Note note, Fields fields) {

    /**
     * @param path                  path of one queue, with the {@code {queue}} placeholder
     * @param idPointer             JSON pointer of the queue id in the response
     * @param classificationPointer JSON pointer of the queue classification (a string or an array of strings); empty
     *                              when the product has none. A queue that reports none is not classified.
     */
    public record Queue(String path, String idPointer, String classificationPointer) {
    }

    /**
     * @param path           search path; may contain {@code {queue}}, otherwise {@code queueParam} is required
     * @param queueParam     query parameter for the queue id when the path has no {@code {queue}}
     * @param textParam      query parameter for the search text (sent as a value, never as a query language)
     * @param limitParam     query parameter for the number of hits; empty when the API has none
     * @param resultsPointer JSON pointer of the hit array in the response; empty = the response is the array
     */
    public record Search(String path, String queueParam, String textParam, String limitParam, String resultsPointer) {
    }

    /**
     * @param path         note path, with the {@code {ticketId}} placeholder
     * @param method       POST, PUT or PATCH
     * @param bodyPointer  JSON pointer where the note text goes in the request body
     * @param staticFields fixed values added to every note request, e.g. internal visibility, so a note never
     *                     reaches the customer by the product's default
     * @param idPointer    JSON pointer of the note id in the response; empty when the API returns none
     */
    public record Note(String path, String method, String bodyPointer, List<StaticField> staticFields,
            String idPointer) {
    }

    /** @param value JSON literal: {@code true}, {@code "internal"}, {@code 3} */
    public record StaticField(String pointer, String value) {
    }

    public record Fields(String id, String queue, String subject, String status, String priority, String created,
            String updated, String description) {
    }
}
