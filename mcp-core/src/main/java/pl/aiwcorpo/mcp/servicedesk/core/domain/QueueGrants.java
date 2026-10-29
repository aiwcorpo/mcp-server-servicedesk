package pl.aiwcorpo.mcp.servicedesk.core.domain;

import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * Which queues the platform's service account may touch for one caller (CC-036, CC-163). The service account sees every
 * queue of the service desk, so a queue is served only when the groups of the caller grant it
 * ({@link QueryScope#grantedIds()}) and the link of the virtual server, when there is one, allows it. A caller granted
 * no queue is served none ({@link QueryScope#nothingToQuery()}), never "every queue". The configuration of the server
 * opens no queue.
 *
 * <p>The starter's {@code StructuredQuery} builds JQL and CQL; this service desk is searched by a queue in the path, so
 * the scope is applied to the one thing the model chooses, the queue.
 */
public final class QueueGrants {

    private QueueGrants() {
    }

    public static boolean permits(QueryScope scope, String queue) {
        if (scope.linkIds().map(link -> !link.contains(queue)).orElse(false)) {
            return false;
        }
        return !scope.nothingToQuery() && scope.grantedIds().contains(queue);
    }
}
