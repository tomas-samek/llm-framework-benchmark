package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Root of scenarios.json. The leading "_format" object is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenarios(List<Scenario> scenarios) {
}
