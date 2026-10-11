package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires a broker on localhost:9092 (conformance/docker-compose.yml up).
 * Run with: mvn -f conformance/oracle/pom.xml test -Pintegration
 */
@Tag("integration")
class OracleRunnerIT {

    private static final String BOOTSTRAP = "localhost:9092";
    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void drainMatchesAPublishedNotification() throws Exception {
        // A one-scenario fixture: publish a notification directly and expect it back.
        String key = "smk-" + java.util.UUID.randomUUID();
        String json = "{\"scenarios\":[{\"id\":\"SMOKE\",\"title\":\"smoke\","
                + "\"steps\":[{\"publish\":{\"topic\":\"notifications\",\"key\":\"" + key + "\","
                + "\"value\":{\"purchaseId\":\"" + key + "\",\"quantity\":1}}}],"
                + "\"expect\":[{\"topic\":\"notifications\",\"key\":\"" + key + "\","
                + "\"value\":{\"purchaseId\":\"" + key + "\",\"quantity\":1}}]}]}";
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "smoke-scenarios.json");
        java.nio.file.Files.writeString(tmp, json);

        Scenarios scenarios = ScenarioLoader.load(tmp);
        List<Result> results;
        try (KafkaIo kafka = new KafkaIo(BOOTSTRAP)) {
            results = new OracleRunner(kafka).run(scenarios);
        }
        assertThat(results).allMatch(Result::passed);
        assertThat(Report.compliance(results)).isEqualTo(1.0);
    }

    @Test
    void duplicateNotificationFailsOnlyWhenExactlyOnceIsRequired() throws Exception {
        // Each run uses its own key: the drain reads the topic from the beginning.
        assertThat(runInline("{\"scenarios\":[" + duplicatePublishScenario() + "]}"))
                .allMatch(Result::passed);

        List<Result> strict = runInline(
                "{\"exactlyOncePerKey\":true,\"scenarios\":[" + duplicatePublishScenario() + "]}");
        assertThat(strict).singleElement().satisfies(r -> {
            assertThat(r.passed()).isFalse();
            assertThat(r.detail()).isEqualTo("2 notifications with this key, expected exactly one");
        });
    }

    /** A scenario that publishes the same notification twice under a fresh key. */
    private static String duplicatePublishScenario() {
        String key = "dup-" + java.util.UUID.randomUUID();
        String publish = "{\"publish\":{\"topic\":\"notifications\",\"key\":\"" + key + "\","
                + "\"value\":{\"purchaseId\":\"" + key + "\",\"quantity\":1}}}";
        return "{\"id\":\"DUP\",\"title\":\"dup\",\"steps\":[" + publish + "," + publish + "],"
                + "\"expect\":[{\"topic\":\"notifications\",\"key\":\"" + key + "\","
                + "\"value\":{\"purchaseId\":\"" + key + "\",\"quantity\":1}}]}";
    }

    private static List<Result> runInline(String json) throws Exception {
        Path tmp = java.nio.file.Files.createTempFile("scenarios-", ".json");
        java.nio.file.Files.writeString(tmp, json);
        try (KafkaIo kafka = new KafkaIo(BOOTSTRAP)) {
            return new OracleRunner(kafka).run(ScenarioLoader.load(tmp));
        }
    }
}
