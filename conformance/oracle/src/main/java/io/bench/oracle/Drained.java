package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** A drained output topic: key -> latest value, and key -> number of records seen. */
public record Drained(Map<String, JsonNode> latest, Map<String, Integer> counts) {
}
