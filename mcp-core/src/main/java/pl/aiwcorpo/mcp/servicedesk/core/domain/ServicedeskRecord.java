package pl.aiwcorpo.mcp.servicedesk.core.domain;

/**
 * A record of the backend system, reduced to the fields the tools hand out. Anything else the backend
 * returns stays in the adapter: a field the model never sees cannot leak.
 */
public record ServicedeskRecord(String id, String project, String title, String status) {

    public ServicedeskRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
