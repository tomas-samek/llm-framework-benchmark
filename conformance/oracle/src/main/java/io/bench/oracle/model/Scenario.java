package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(String id, String title, List<Step> steps, List<Expectation> expect) {
}
