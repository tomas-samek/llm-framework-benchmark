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
}
