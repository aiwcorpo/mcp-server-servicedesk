package pl.aiwcorpo.mcp.servicedesk.integration;

/**
 * The service desk answered 2xx with something that is not the expected JSON. Carries no part of the response;
 * the starter reports it as {@code internal_error}.
 */
public class ServicedeskResponseException extends RuntimeException {

    public ServicedeskResponseException() {
        super("the service desk returned an unreadable response", null, false, false);
    }
}
