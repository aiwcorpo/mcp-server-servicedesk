package pl.aiwcorpo.mcp.servicedesk.core.domain;

import java.util.Set;

/**
 * Which queues this server serves. Fail-closed: whenever the call runs as the service account, either because
 * the server is configured for one or because the gateway labelled the credential so, only queues listed in
 * {@code allowedQueues} are served, and an empty list serves none.
 *
 * <p>This is the server's own boundary, asked before every backend call that names a queue and for every ticket a
 * backend call returns. The caller's policy (Cedar on the resolved queue and ticket, the grants and the link in
 * {@code QueryScope}) is applied by the starter and the tools on top of it, never instead of it.
 *
 * @param mode          credential mode from the configuration
 * @param allowedQueues queues the server may serve; with {@link CredentialMode#USER_SECRET} an empty set means
 *                      "whatever the user's own account sees"
 */
public record QueueAccessPolicy(CredentialMode mode, Set<String> allowedQueues) {

    public QueueAccessPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("credential mode is required");
        }
        allowedQueues = Set.copyOf(allowedQueues);
        for (String queue : allowedQueues) {
            if (!Identifiers.isQueueId(queue)) {
                throw new IllegalArgumentException("allowed queue '" + queue + "' is not a valid queue id");
            }
        }
    }

    /**
     * @param serviceAccountCredential the credential of this call is labelled {@code service-account}
     */
    public boolean restricted(boolean serviceAccountCredential) {
        return mode == CredentialMode.SERVICE_ACCOUNT || serviceAccountCredential || !allowedQueues.isEmpty();
    }

    /**
     * The one definition of "a service account call": the server is configured for the service account, or the gateway
     * labelled this call's credential so. Grants of the caller's groups and the attribution of notes follow it.
     */
    public boolean serviceAccountCall(boolean serviceAccountCredential) {
        return mode == CredentialMode.SERVICE_ACCOUNT || serviceAccountCredential;
    }

    /** Whether the server may serve anything at all for such a call; false skips the backend entirely. */
    public boolean servesAnyQueue(boolean serviceAccountCredential) {
        return !restricted(serviceAccountCredential) || !allowedQueues.isEmpty();
    }

    public boolean permits(String queue, boolean serviceAccountCredential) {
        if (!Identifiers.isQueueId(queue)) {
            return false;
        }
        return !restricted(serviceAccountCredential) || allowedQueues.contains(queue);
    }
}
