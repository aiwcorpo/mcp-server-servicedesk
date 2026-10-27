package pl.aiwcorpo.mcp.servicedesk.core.domain;

/**
 * Which credential the platform hands this server for the service desk (D10), from the server configuration.
 */
public enum CredentialMode {

    /**
     * A personal token of the user ({@code user-secret}): the service desk itself decides what the user sees,
     * and this server's queue list, when set, only narrows that.
     */
    USER_SECRET,

    /**
     * One shared account of the platform ({@code service-account}): the service desk does not know the user, so
     * this server is the main access boundary and serves only queues listed in its configuration.
     */
    SERVICE_ACCOUNT
}
