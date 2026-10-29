package pl.aiwcorpo.mcp.servicedesk.tools;

import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * What the policy lets a search touch (CC-036). The starter's {@code StructuredQuery} builds JQL and CQL; this service
 * desk is searched by a queue in the path and a text parameter, which the adapter builds from validated values, so
 * the scope is applied here to the one thing the model chooses, the queue.
 *
 * <p>The platform's service account sees every queue of the service desk, so a queue is searched only when the groups
 * of the caller grant it ({@link QueryScope#grantedIds()}) and the link of the virtual server, when there is one,
 * allows it. A caller granted no queue searches nothing ({@link QueryScope#nothingToQuery()}), never "every queue".
 */
final class ServicedeskSearchScope {

    private ServicedeskSearchScope() {
    }

    static boolean permits(QueryScope scope, String queue) {
        if (scope.linkIds().map(link -> !link.contains(queue)).orElse(false)) {
            return false;
        }
        return !scope.nothingToQuery() && scope.grantedIds().contains(queue);
    }
}
