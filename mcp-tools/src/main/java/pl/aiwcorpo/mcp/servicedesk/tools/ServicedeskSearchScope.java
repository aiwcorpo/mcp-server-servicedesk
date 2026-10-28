package pl.aiwcorpo.mcp.servicedesk.tools;

import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * What the policy lets a search touch (CC-036). The starter's {@code StructuredQuery} builds JQL and CQL; this service
 * desk is searched by a queue in the path and a text parameter, which the adapter builds from validated values, so
 * the scope is applied here to the one thing the model chooses, the queue.
 *
 * <p>The link of the virtual server narrows the queue. For the service account, whose rights in the service desk
 * are the platform's and not the user's, the queue must also be one the groups of the caller grant, the equivalent of
 * {@code includeGrants} in a JQL scope; a caller granted no queue searches nothing ({@code grantsNothing}), never
 * "every queue".
 */
final class ServicedeskSearchScope {

    private ServicedeskSearchScope() {
    }

    static boolean permits(QueryScope scope, String queue, boolean serviceAccount) {
        if (scope.linkIds().map(link -> !link.contains(queue)).orElse(false)) {
            return false;
        }
        return !serviceAccount || (!scope.grantsNothing() && scope.grantedIds().contains(queue));
    }
}
