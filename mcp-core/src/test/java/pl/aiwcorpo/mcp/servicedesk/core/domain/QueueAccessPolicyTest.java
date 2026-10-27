package pl.aiwcorpo.mcp.servicedesk.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QueueAccessPolicyTest {

    @Test
    void serviceAccountModeServesOnlyListedQueues() {
        QueueAccessPolicy policy = new QueueAccessPolicy(CredentialMode.SERVICE_ACCOUNT, Set.of("IT"));

        assertThat(policy.permits("IT", true)).isTrue();
        assertThat(policy.permits("IT", false)).isTrue();
        assertThat(policy.permits("FIN", true)).isFalse();
        assertThat(policy.permits("FIN", false)).as("mode alone restricts, whatever the label").isFalse();
        assertThat(policy.permits("it", true)).as("exact, case-sensitive match").isFalse();
    }

    @Test
    void serviceAccountModeWithoutQueuesServesNothing() {
        QueueAccessPolicy policy = new QueueAccessPolicy(CredentialMode.SERVICE_ACCOUNT, Set.of());

        assertThat(policy.servesAnyQueue(true)).isFalse();
        assertThat(policy.servesAnyQueue(false)).isFalse();
        assertThat(policy.permits("IT", true)).isFalse();
    }

    @Test
    void userSecretModeWithoutQueuesLeavesTheDecisionToTheServiceDesk() {
        QueueAccessPolicy policy = new QueueAccessPolicy(CredentialMode.USER_SECRET, Set.of());

        assertThat(policy.permits("FIN", false)).isTrue();
        assertThat(policy.servesAnyQueue(false)).isTrue();
    }

    @Test
    void aServiceAccountCredentialIsRestrictedEvenInUserSecretMode() {
        QueueAccessPolicy policy = new QueueAccessPolicy(CredentialMode.USER_SECRET, Set.of());

        assertThat(policy.servesAnyQueue(true)).isFalse();
        assertThat(policy.permits("FIN", true)).isFalse();
    }

    @Test
    void userSecretModeWithQueuesNarrows() {
        QueueAccessPolicy policy = new QueueAccessPolicy(CredentialMode.USER_SECRET, Set.of("IT"));

        assertThat(policy.permits("IT", false)).isTrue();
        assertThat(policy.permits("FIN", false)).isFalse();
    }

    @Test
    void invalidQueueIdsAreNeverPermittedNorConfigurable() {
        QueueAccessPolicy open = new QueueAccessPolicy(CredentialMode.USER_SECRET, Set.of());

        assertThat(open.permits(null, false)).isFalse();
        assertThat(open.permits("../IT", false)).isFalse();
        assertThat(open.permits("", false)).isFalse();
        assertThatThrownBy(() -> new QueueAccessPolicy(CredentialMode.SERVICE_ACCOUNT, Set.of("IT,FIN")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueAccessPolicy(null, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
