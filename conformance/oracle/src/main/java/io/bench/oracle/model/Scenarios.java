package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Root of scenarios.json. The leading "_format" object is ignored.
 * {@code exactlyOncePerKey} (optional, default false): when true, an expected key
 * that appears more than once on the output topic fails, even if its latest value matches.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenarios(Boolean exactlyOncePerKey, List<Scenario> scenarios) {

    public boolean requiresExactlyOnce() {
        return Boolean.TRUE.equals(exactlyOncePerKey);
    }
}
