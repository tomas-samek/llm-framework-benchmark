package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;

/** Deep, numeric-tolerant equality of two JSON values. Nulls are significant. */
public final class JsonMatch {

    private JsonMatch() {
    }

    public static boolean matches(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isNull() || actual.isNull()) {
            return expected.isNull() && actual.isNull();
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.isObject() && actual.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                JsonNode a = actual.get(e.getKey());
                if (a == null || !matches(e.getValue(), a)) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!matches(expected.get(i), actual.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isBoolean() && actual.isBoolean()) {
            return expected.asBoolean() == actual.asBoolean();
        }
        if (expected.isTextual() && actual.isTextual()) {
            return expected.asText().equals(actual.asText());
        }
        return false;
    }
}
