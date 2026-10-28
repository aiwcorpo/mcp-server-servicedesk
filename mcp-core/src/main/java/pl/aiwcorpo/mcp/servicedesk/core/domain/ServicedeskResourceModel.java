package pl.aiwcorpo.mcp.servicedesk.core.domain;

/** How this server's objects map to the resource types of the policy (C7) and which contract patterns name them. */
public final class ServicedeskResourceModel {

    public static final String TICKET_TYPE = "ServiceDesk::Ticket";
    public static final String QUEUE_TYPE = "ServiceDesk::Queue";

    public static final String TICKET_ID_CONTRACT = "ServiceDeskTicketId";
    public static final String QUEUE_ID_CONTRACT = "ServiceDeskQueueId";
    public static final int ID_MAX_LENGTH = 64;

    private ServicedeskResourceModel() {
    }
}
