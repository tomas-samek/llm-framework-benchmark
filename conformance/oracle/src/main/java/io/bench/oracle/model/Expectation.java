package io.bench.oracle.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Expect topic[key] to deep-equal value after all steps have run. */
public record Expectation(String topic, String key, JsonNode value) {
}
