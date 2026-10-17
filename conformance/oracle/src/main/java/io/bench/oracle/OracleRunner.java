package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import io.bench.oracle.model.Expectation;
import io.bench.oracle.model.Scenario;
import io.bench.oracle.model.Scenarios;
import io.bench.oracle.model.Step;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs all scenarios in order against a single running contestant app:
 * executes every step (publish/raw/settle/HTTP check), then drains the output
 * topic once and evaluates every expectation. HTTP checks are evaluated when
 * their step is reached, since they assert the state at that point.
 */
public final class OracleRunner {

    private static final String OUTPUT_TOPIC = "notifications";
    private static final long DRAIN_QUIET_MILLIS = 5000;
    private static final String DEFAULT_HTTP_BASE = "http://localhost:8080";

    private final KafkaIo kafka;
    private final HttpChecker http;

    public OracleRunner(KafkaIo kafka) {
        this(kafka, new HttpChecker(httpBaseFromEnv()));
    }

    public OracleRunner(KafkaIo kafka, HttpChecker http) {
        this.kafka = kafka;
        this.http = http;
    }

    /** ORACLE_HTTP_BASE overrides the contestant's HTTP base URL (default http://localhost:8080). */
    private static String httpBaseFromEnv() {
        String env = System.getenv("ORACLE_HTTP_BASE");
        return env == null || env.isBlank() ? DEFAULT_HTTP_BASE : env;
    }

    private static List<Expectation> expectations(Scenario s) {
        return s.expect() == null ? List.of() : s.expect();
    }

    public List<Result> run(Scenarios scenarios) throws InterruptedException {
        boolean anyExpectation = false;
        for (Scenario s : scenarios.scenarios()) {
            for (Expectation e : expectations(s)) {
                anyExpectation = true;
                if (!OUTPUT_TOPIC.equals(e.topic())) {
                    throw new IllegalArgumentException(
                        "Scenario " + s.id() + ": unsupported expectation topic '" + e.topic() + "'");
                }
            }
        }
        List<Result> results = new ArrayList<>();
        for (Scenario s : scenarios.scenarios()) {
            for (Step step : s.steps()) {
                applyStep(step, results);
            }
        }
        if (!anyExpectation) {
            return results;
        }
        Drained emitted = kafka.drain(OUTPUT_TOPIC, DRAIN_QUIET_MILLIS);

        for (Scenario s : scenarios.scenarios()) {
            for (Expectation e : expectations(s)) {
                JsonNode actual = emitted.latest().get(e.key());
                int count = emitted.counts().getOrDefault(e.key(), 0);
                if (actual == null) {
                    results.add(new Result(s.id(), e.key(), false, "no notification with this key"));
                } else if (scenarios.requiresExactlyOnce() && count > 1) {
                    results.add(new Result(s.id(), e.key(), false,
                            count + " notifications with this key, expected exactly one"));
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

    private void applyStep(Step step, List<Result> results) throws InterruptedException {
        if (step.settleMs() != null) {
            Thread.sleep(step.settleMs());
        } else if (step.publish() != null) {
            kafka.publishJson(step.publish().topic(), step.publish().key(), step.publish().value());
        } else if (step.publishRaw() != null) {
            kafka.publishRaw(step.publishRaw().topic(), step.publishRaw().key(), step.publishRaw().value());
        } else if (step.httpCheck() != null) {
            results.add(http.check(step.httpCheck()));
        } else {
            throw new IllegalArgumentException("Step has no recognized action: " + step);
        }
    }
}
