package pl.aiwcorpo.mcp.servicedesk.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.manifest.ManifestRules;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code target/mcp-manifest.json} for the registration step of the CI. The {@code manifest-artifact} execution of
 * surefire in mcp-app/pom.xml runs only this class in the package phase, also when the build is run with
 * {@code -DskipTests}, so the artifact exists after every ordinary build of the commit under review.
 */
class ManifestArtifactTest extends ServerTestSupport {

    @Test
    void theBuildLeavesAManifestThatPassesTheRulesOfTheGateway() throws Exception {
        String manifest = Files.readString(MANIFEST_FILE);

        assertThat(ManifestRules.violations(manifest)).isEmpty();
        assertThat(JsonMapper.builder().build().readTree(manifest).get("serverId").asString()).isEqualTo("mcp-servicedesk");
    }
}
