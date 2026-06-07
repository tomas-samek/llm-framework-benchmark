package io.bench.oracle.model;

/** Publish a raw (possibly non-JSON) string value to a topic under a key. */
public record PublishRaw(String topic, String key, String value) {
}
