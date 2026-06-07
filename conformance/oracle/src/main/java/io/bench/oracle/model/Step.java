package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One step in a scenario. Exactly one of the three is non-null:
 * a JSON publish, a raw publish, or a settle pause (milliseconds).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Step(Publish publish, PublishRaw publishRaw, Long settleMs) {
}
