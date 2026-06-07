package io.bench.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Renders results to the console and a JSON file; computes compliance. */
public final class Report {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Report() {
    }

    public static double compliance(List<Result> results) {
        if (results.isEmpty()) {
            return 0.0;
        }
        long passed = results.stream().filter(Result::passed).count();
        return (double) passed / results.size();
    }

    public static void printConsole(List<Result> results) {
        for (Result r : results) {
            System.out.printf("[%s] %-5s %s%s%n",
                    r.passed() ? "PASS" : "FAIL", r.scenarioId(), r.key(),
                    r.passed() ? "" : " -- " + r.detail());
        }
        System.out.printf("Compliance: %.0f%% (%d/%d)%n",
                compliance(results) * 100,
                results.stream().filter(Result::passed).count(), results.size());
    }

    public static void writeJson(Path out, List<Result> results) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("compliance", compliance(results));
        root.put("passed", results.stream().filter(Result::passed).count());
        root.put("total", results.size());
        ArrayNode arr = root.putArray("results");
        for (Result r : results) {
            ObjectNode n = arr.addObject();
            n.put("scenario", r.scenarioId());
            n.put("key", r.key());
            n.put("passed", r.passed());
            n.put("detail", r.detail());
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
    }
}
