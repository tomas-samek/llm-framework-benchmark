package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One step in a scenario. Exactly one of the four is non-null:
 * a JSON publish, a raw publish, a settle pause (milliseconds), or an HTTP check.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Step(Publish publish, PublishRaw publishRaw, Long settleMs, HttpCheck httpCheck) {
}
