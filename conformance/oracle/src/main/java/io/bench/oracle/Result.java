package io.bench.oracle;

/** Outcome of one expectation within a scenario. */
public record Result(String scenarioId, String key, boolean passed, String detail) {
}
