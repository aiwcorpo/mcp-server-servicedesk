package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A queue as the policy sees it. {@code classification} is what the service desk reports for the queue; {@code null}
 * means it reports none, and a queue without a classification is not treated as restricted (decision of the platform
 * owner, kept on purpose: the service desk's own categories are the only source).
 */
public record Queue(String id, @Nullable List<String> classification) {

    public Queue {
        Identifiers.requireQueueId(id);
        classification = classification == null ? null : List.copyOf(classification);
    }
}
