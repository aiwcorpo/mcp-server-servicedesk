package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.platform.test.PolicyDecisions;
import pl.aiwcorpo.mcp.platform.test.PolicyDecisions.Decision;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ClassifiedTicket;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Queue;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskResourceDescription;
import pl.aiwcorpo.mcp.servicedesk.core.domain.Ticket;

/**
 * The decisions of the policies in {@code mcp/policies/} on the resources of this server, without Spring and without
 * the gateway, for the service account this server uses: who may read, change and delete what, given the scope mapping
 * an administrator would set. The resources are built by the description the resolvers use. The rules for restricted
 * queues and for the service account outside the queues of the user come from the platform, generated from the
 * declared types.
 */
class ServicedeskPolicyDecisionsTest {

    private static final String READ = "mcp-servicedesk-read";
    private static final String WRITE = "mcp-servicedesk-write";
    private static final String DESTRUCTIVE = "mcp-servicedesk-destructive";
    private static final String IT = "mcp-servicedesk-queue-IT";

    private final PolicyDecisions policies = PolicyDecisions.fromResources()
            .scopeMapping(Map.of(IT, List.of("ServiceDesk::Queue/IT"),
                    "mcp-servicedesk-queue-FIN", List.of("ServiceDesk::Queue/FIN")))
            .credentialType("service-account");

    private static ResolvedResource queue(String id, List<String> classification) {
        return ServicedeskResourceDescription.of(new Queue(id, classification));
    }

    private static ResolvedResource ticket(String id, String queueId, List<String> classification) {
        Ticket ticket = new Ticket(id, queueId, null, null, null, null, null, null);
        return ServicedeskResourceDescription.of(new ClassifiedTicket(ticket, new Queue(queueId, classification)));
    }

    @Test
    void aReaderInAGrantedQueueReadsTicketsAndTheQueue() {
        Decision decision = policies.decide(PolicyDecisions.caller(READ, IT), "read", ticket("IT-7", "IT", null));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("servicedesk-read");
        policies.assertAllowed(PolicyDecisions.caller(READ, IT), "read", queue("IT", null));
    }

    @Test
    void aCallerWithoutTheReadGroupReadsNothing() {
        policies.assertDenied(PolicyDecisions.caller(IT), "read", ticket("IT-7", "IT", null));
        policies.assertDenied(PolicyDecisions.caller(WRITE, IT), "read", queue("IT", null));
    }

    @Test
    void theServiceAccountReadsOnlyInQueuesTheUserIsGranted() {
        policies.assertDenied(PolicyDecisions.caller(READ, IT), "read", ticket("HR-3", "HRQ", null));
        policies.assertDenied(PolicyDecisions.caller(READ, IT), "read", queue("HRQ", null));
        policies.assertDenied(PolicyDecisions.caller(READ), "read", ticket("IT-7", "IT", null));
    }

    @Test
    void aUserCredentialIsLeftToTheRightsOfTheUserInTheServiceDesk() {
        for (String type : List.of("user-oauth", "user-secret", "token-exchange")) {
            PolicyDecisions user = PolicyDecisions.fromResources().credentialType(type);

            user.assertAllowed(PolicyDecisions.caller(READ), "read", ticket("HR-3", "HRQ", null));
        }
    }

    @Test
    void aTicketUpdateInAGrantedQueueIsAllowed() {
        Decision decision = policies.decide(PolicyDecisions.caller(READ, WRITE, IT), "update", ticket("IT-7", "IT", null));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("servicedesk-update-ticket-in-scope");
    }

    @Test
    void creatingATicketIsAnUpdateOfTheQueue() {
        Decision decision = policies.decide(PolicyDecisions.caller(READ, WRITE, IT), "update", queue("IT", null));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("servicedesk-update-queue-in-scope");
    }

    @Test
    void anUpdateOutsideTheGrantedQueuesIsDeniedByThePlatformRule() {
        Decision decision = policies.decide(PolicyDecisions.caller(READ, WRITE, IT), "update", ticket("HR-3", "HRQ", null));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.determiningPolicies()).isNotEmpty().allMatch(id -> id.startsWith("platform-"));
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, IT), "update", queue("HRQ", null));
    }

    @Test
    void theScopeWithoutTheWriteGroupChangesNothing() {
        policies.assertDenied(PolicyDecisions.caller(READ, IT), "update", ticket("IT-7", "IT", null));
    }

    @Test
    void aTicketMovedToAnotherQueueIsJudgedByTheQueueItIsInNow() {
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, IT), "update", ticket("IT-9", "FIN", null));
        policies.assertAllowed(PolicyDecisions.caller(READ, WRITE, IT), "update", ticket("FIN-2", "IT", null));
    }

    @Test
    void aTicketIsDeletedInTheGrantedQueueWithTheDestructiveGroup() {
        Decision decision = policies.decide(PolicyDecisions.caller(READ, DESTRUCTIVE, IT), "delete", ticket("IT-7", "IT", null));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.determiningPolicies()).containsExactly("servicedesk-delete-ticket-in-scope");
    }

    @Test
    void aDeleteNeedsTheDestructiveGroupAndTheQueue() {
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, IT), "delete", ticket("IT-7", "IT", null));
        policies.assertDenied(PolicyDecisions.caller(READ, DESTRUCTIVE, IT), "delete", ticket("HR-3", "HRQ", null));
        policies.assertDenied(PolicyDecisions.caller(READ, DESTRUCTIVE), "delete", ticket("IT-7", "IT", null));
    }

    @Test
    void aWholeQueueIsNeverDeleted() {
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE, DESTRUCTIVE, IT), "delete", queue("IT", null));
    }

    @Test
    void aRestrictedQueueAndItsTicketsAreDeniedByThePlatformRuleWhatEverTheGroups() {
        for (String type : List.of("service-account", "user-oauth")) {
            PolicyDecisions decisions = PolicyDecisions.fromResources().credentialType(type)
                    .scopeMapping(Map.of(IT, List.of("ServiceDesk::Queue/IT")));
            for (String action : List.of("read", "update", "delete")) {
                for (ResolvedResource resource : List.of(queue("IT", List.of("restricted")),
                        ticket("IT-2", "IT", List.of("restricted")))) {
                    Decision decision = decisions.decide(PolicyDecisions.caller(READ, WRITE, DESTRUCTIVE, IT), action, resource);

                    assertThat(decision.allowed()).as(type + " " + action + " " + resource.type()).isFalse();
                    assertThat(decision.determiningPolicies()).isNotEmpty().allMatch(id -> id.startsWith("platform-"));
                }
            }
        }
    }

    @Test
    void theClassificationIsNormalizedBeforeThePlatformRuleLooksAtIt() {
        policies.assertDenied(PolicyDecisions.caller(READ, IT), "read", queue("IT", List.of("Restricted")));
        policies.assertDenied(PolicyDecisions.caller(READ, IT), "read", ticket("IT-2", "IT", List.of(" RESTRICTED ")));
    }

    @Test
    void aQueueWithAnotherClassificationOrNoneIsNotRestricted() {
        policies.assertAllowed(PolicyDecisions.caller(READ, IT), "read", queue("IT", List.of("Internal")));
        policies.assertAllowed(PolicyDecisions.caller(READ, IT), "read", queue("IT", List.of()));
        policies.assertAllowed(PolicyDecisions.caller(READ, IT), "read", ticket("IT-2", "IT", null));
    }

    @Test
    void aCallerWithNoQueueScopeIsDeniedOnTheProbeOfATicketOutsideScopes() {
        ResolvedResource probe = ServicedeskResourceDescription.probeOfTicketOutsideScopes("IT-7");

        policies.assertDenied(PolicyDecisions.caller(READ), "read", probe);
        policies.assertDenied(PolicyDecisions.caller(READ, WRITE), "update", probe);
    }

    @Test
    void theProbeOfAQueueIsJudgedOnTheGrantAlone() {
        ResolvedResource probe = ServicedeskResourceDescription.probeOfQueue("IT");

        policies.assertAllowed(PolicyDecisions.caller(READ, IT), "read", probe);
        policies.assertDenied(PolicyDecisions.caller(READ), "read", probe);
    }

    @Test
    void anIdOutsideTheDeclaredPatternIsRefused() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "-bad");
        ResolvedResource queue = ResolvedResource.of("ServiceDesk::Queue", "-bad", attributes)
                .withScopes(List.of("ServiceDesk::Queue/IT"));

        assertThatThrownBy(() -> policies.decide(PolicyDecisions.caller(READ), "read", queue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOverrideOfTheAdministratorClosesDeletesEverywhere() {
        PolicyDecisions frozen = policies.override("override-no-delete",
                "forbid (principal, action == ServiceDesk::Action::\"delete\", resource);");

        frozen.assertDenied(PolicyDecisions.caller(READ, DESTRUCTIVE, IT), "delete", ticket("IT-7", "IT", null));
        frozen.assertAllowed(PolicyDecisions.caller(READ, WRITE, IT), "update", ticket("IT-7", "IT", null));
    }
}
