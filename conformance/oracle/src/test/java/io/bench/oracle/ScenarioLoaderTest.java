package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.bench.oracle.model.Scenario;
import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    // From the oracle module dir up to repo root, then into fixtures.
    private static final Path FIXTURES =
            Path.of("..", "..", "fixtures", "stage-1", "scenarios.json");

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
        assertThat(ac1.steps().get(3).settleMs()).isEqualTo(3000L);
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
}
