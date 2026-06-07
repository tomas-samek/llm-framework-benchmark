package io.bench.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bench.oracle.model.Scenarios;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the shared scenario fixtures into the typed model. */
public final class ScenarioLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScenarioLoader() {
    }

    public static Scenarios load(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, Scenarios.class);
        }
    }
}
