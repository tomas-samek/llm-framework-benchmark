package io.bench.oracle;

import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI: java -jar conformance-oracle.jar <bootstrap> <scenarios.json> [out.json]
 * Exits 0 only when compliance is 100%; 1 otherwise (so run scripts can gate).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: <bootstrap> <scenarios.json> [out.json]");
            System.exit(2);
            return;
        }
        String bootstrap = args[0];
        Path scenariosFile = Path.of(args[1]);
        Path out = args.length >= 3 ? Path.of(args[2]) : null;

        Scenarios scenarios = ScenarioLoader.load(scenariosFile);
        List<Result> results;
        try (KafkaIo kafka = new KafkaIo(bootstrap)) {
            results = new OracleRunner(kafka).run(scenarios);
        }
        Report.printConsole(results);
        if (out != null) {
            Report.writeJson(out, results);
        }
        System.exit(Report.compliance(results) == 1.0 ? 0 : 1);
    }
}
