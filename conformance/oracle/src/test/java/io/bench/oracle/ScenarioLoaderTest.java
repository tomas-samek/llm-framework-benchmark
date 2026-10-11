package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.bench.oracle.model.Scenario;
import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    // From the oracle module dir up to repo root, then into fixtures.
    private static final Path FIXTURES =
            Path.of("..", "..", "fixtures", "stage-1", "scenarios.json");
    private static final Path FIXTURES_V2 =
            Path.of("..", "..", "fixtures", "stage-1", "scenarios.v2.json");

    @Test
    void loadsAllSixScenariosInOrder() throws Exception {
        Scenarios scenarios = ScenarioLoader.load(FIXTURES);

        assertThat(scenarios.scenarios()).extracting(Scenario::id)
                .containsExactly("AC1", "AC2", "AC3", "AC4", "AC5", "AC6");
    }

    @Test
    void ac1HasThreeReferencePublishesASettleAndAPurchase() throws Exception {
        Scenario ac1 = ScenarioLoader.load(FIXTURES).scenarios().get(0);

        assertThat(ac1.steps()).hasSize(5);
        assertThat(ac1.steps().get(0).publish().topic()).isEqualTo("product-updates");
        assertThat(ac1.steps().get(3).settleMs()).isEqualTo(6000L);
        assertThat(ac1.steps().get(4).publish().topic()).isEqualTo("purchases");
        assertThat(ac1.expect()).hasSize(1);
        assertThat(ac1.expect().get(0).key()).isEqualTo("buy1");
        assertThat(ac1.expect().get(0).value().get("totalCents").asInt()).isEqualTo(15998);
    }

    @Test
    void ac5HasARawPoisonPublish() throws Exception {
        Scenario ac5 = ScenarioLoader.load(FIXTURES).scenarios().get(4);

        assertThat(ac5.steps().get(0).publishRaw()).isNotNull();
        assertThat(ac5.steps().get(0).publishRaw().value()).isEqualTo("this is not valid json");
    }

    @Test
    void v1DoesNotRequireExactlyOnce() throws Exception {
        assertThat(ScenarioLoader.load(FIXTURES).requiresExactlyOnce()).isFalse();
    }

    @Test
    void v2RunsThePoisonScenarioLastAndRequiresExactlyOnce() throws Exception {
        Scenarios v2 = ScenarioLoader.load(FIXTURES_V2);

        assertThat(v2.scenarios()).extracting(Scenario::id)
                .containsExactly("AC1", "AC2", "AC3", "AC4", "AC6", "AC5");
        assertThat(v2.requiresExactlyOnce()).isTrue();
        assertThat(v2.scenarios().get(5).steps().get(0).publishRaw()).isNotNull();
        assertThat(v2.scenarios()).flatMap(Scenario::expect).hasSize(7);
    }

    @Test
    void v2Ac4AndAc5UseIdsNoEarlierScenarioPublished() throws Exception {
        // The whole point of v2: state left by earlier scenarios must not be able to
        // satisfy AC4's upsert check or AC5's post-poison price on its own.
        List<Scenario> scenarios = ScenarioLoader.load(FIXTURES_V2).scenarios();
        assertFresh(scenarios, "AC4", "product-updates");
        assertFresh(scenarios, "AC5", "price-updates");
    }

    private static void assertFresh(List<Scenario> scenarios, String id, String topic) {
        Set<String> seenBefore = new HashSet<>();
        for (Scenario s : scenarios) {
            Set<String> keys = s.steps().stream()
                    .filter(step -> step.publish() != null && step.publish().topic().equals(topic))
                    .map(step -> step.publish().key())
                    .collect(Collectors.toSet());
            if (s.id().equals(id)) {
                assertThat(keys).isNotEmpty().doesNotContainAnyElementsOf(seenBefore);
                return;
            }
            seenBefore.addAll(keys);
        }
        throw new AssertionError("scenario " + id + " not found");
    }
}
