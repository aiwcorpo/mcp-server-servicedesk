package pl.aiwcorpo.mcp.servicedesk.core.domain;

/** How this server's objects map to the resource types of the policy (C7) and which grammar names them. */
public final class ServicedeskResourceModel {

    public static final String TICKET_TYPE = "ServiceDesk::Ticket";
    public static final String QUEUE_TYPE = "ServiceDesk::Queue";

    /** The {@code idPattern} of both types in {@code mcp/policies/resource-types.json}; the starter checks it on every id. */
    public static final String ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";
    public static final int ID_MAX_LENGTH = 64;

    private ServicedeskResourceModel() {
    }
}
