package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import io.bench.oracle.model.Expectation;
import io.bench.oracle.model.Scenario;
import io.bench.oracle.model.Scenarios;
import io.bench.oracle.model.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs all scenarios in order against a single running contestant app:
 * executes every step (publish/raw/settle), then drains the output topic once
 * and evaluates every expectation.
 */
public final class OracleRunner {

    private static final String OUTPUT_TOPIC = "notifications";
    private static final long DRAIN_QUIET_MILLIS = 5000;

    private final KafkaIo kafka;

    public OracleRunner(KafkaIo kafka) {
        this.kafka = kafka;
    }

    public List<Result> run(Scenarios scenarios) throws InterruptedException {
        for (Scenario s : scenarios.scenarios()) {
            for (Expectation e : s.expect()) {
                if (!OUTPUT_TOPIC.equals(e.topic())) {
                    throw new IllegalArgumentException(
                        "Scenario " + s.id() + ": unsupported expectation topic '" + e.topic() + "'");
                }
            }
        }
        for (Scenario s : scenarios.scenarios()) {
            for (Step step : s.steps()) {
                applyStep(step);
            }
        }
        Map<String, JsonNode> emitted = kafka.drain(OUTPUT_TOPIC, DRAIN_QUIET_MILLIS);

        List<Result> results = new ArrayList<>();
        for (Scenario s : scenarios.scenarios()) {
            for (Expectation e : s.expect()) {
                JsonNode actual = emitted.get(e.key());
                if (actual == null) {
                    results.add(new Result(s.id(), e.key(), false, "no notification with this key"));
                } else if (JsonMatch.matches(e.value(), actual)) {
                    results.add(new Result(s.id(), e.key(), true, "ok"));
                } else {
                    results.add(new Result(s.id(), e.key(), false,
                            "expected " + e.value() + " but got " + actual));
                }
            }
        }
        return results;
    }

    private void applyStep(Step step) throws InterruptedException {
        if (step.settleMs() != null) {
            Thread.sleep(step.settleMs());
        } else if (step.publish() != null) {
            kafka.publishJson(step.publish().topic(), step.publish().key(), step.publish().value());
        } else if (step.publishRaw() != null) {
            kafka.publishRaw(step.publishRaw().topic(), step.publishRaw().key(), step.publishRaw().value());
        } else {
            throw new IllegalArgumentException("Step has no recognized action: " + step);
        }
    }
}
