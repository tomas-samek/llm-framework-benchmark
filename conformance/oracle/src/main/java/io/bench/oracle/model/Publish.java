package io.bench.oracle.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Publish a JSON value to a topic under a key. */
public record Publish(String topic, String key, JsonNode value) {
}
