package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * GET {@code path} on the contestant's HTTP server until it answers with {@code status}
 * and a matching body, or {@code waitMs} (default 10 s) passes. The body check is either
 * {@code body} (deep JSON equality, as for notifications) or {@code errorField} (the body
 * is a JSON object whose field of that name is a non-blank string). {@code id} and
 * {@code key} label the result, like a scenario id and a notification key.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HttpCheck(String id, String key, String path, int status, JsonNode body,
                        String errorField, Long waitMs) {
}
