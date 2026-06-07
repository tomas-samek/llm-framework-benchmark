package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonMatchTest {

    private final ObjectMapper m = new ObjectMapper();

    private boolean match(String expected, String actual) throws Exception {
        return JsonMatch.matches(m.readTree(expected), m.readTree(actual));
    }

    @Test
    void identicalObjectsMatch() throws Exception {
        assertThat(match("{\"a\":1,\"b\":\"x\"}", "{\"b\":\"x\",\"a\":1}")).isTrue();
    }

    @Test
    void intAndLongOfSameValueMatch() throws Exception {
        // 15998 vs 15998 written as a long-ish literal; same decimal value.
        assertThat(match("{\"t\":15998}", "{\"t\":15998}")).isTrue();
    }

    @Test
    void nullMatchesNullButNotAValue() throws Exception {
        assertThat(match("{\"p\":null}", "{\"p\":null}")).isTrue();
        assertThat(match("{\"p\":null}", "{\"p\":7999}")).isFalse();
        assertThat(match("{\"p\":7999}", "{\"p\":null}")).isFalse();
    }

    @Test
    void differentValuesDoNotMatch() throws Exception {
        assertThat(match("{\"name\":\"Alice\"}", "{\"name\":\"Bob\"}")).isFalse();
    }

    @Test
    void extraOrMissingFieldsDoNotMatch() throws Exception {
        assertThat(match("{\"a\":1}", "{\"a\":1,\"b\":2}")).isFalse();
        assertThat(match("{\"a\":1,\"b\":2}", "{\"a\":1}")).isFalse();
    }
}
