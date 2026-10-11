# Running a trial (any agent)

This is the canonical, **neutral** procedure for running one benchmark trial with
any coding agent (Claude, GPT, Gemini, Cursor, …) and grading it objectively.
Keep it identical across agents — that is what makes the comparison fair.

> **Fairness rule:** give the agent **only** a fresh scaffold copy + the stage
> spec + the dependency rules below. Do **not** give it hints, the fixtures'
> expected outputs (`fixtures/`), the conformance oracle, `results/`, or any
> prior trial's code. Use the *same* prompt for every agent.

## Prerequisites

- JDK 21, Maven 3.9+, Docker.
- For the `tiko-mcp` arm only: [jbang](https://www.jbang.dev/) (the archetype's
  `.mcp.json` launches the topology server via jbang).
- Build the conformance oracle once:
  `mvn -f conformance/oracle/pom.xml -q package` → `conformance/oracle/target/conformance-oracle.jar`.
- Tiko trials: if this machine also builds Tiko from source, `~/.m2` may hold
  locally-installed (unreleased) artifacts under the release version number. Build
  trials with an isolated `-Dmaven.repo.local=…` so they resolve from Maven Central.

## 1. Create a fresh trial workspace

```
# <scaffold> ∈ { spring, spring3, tiko, tiko3, tiko5, tiko6 }
# <cell>     = the result-cell name, e.g. spring-fix, spring3-fix, tiko-050, tiko-mcp
cp -a scaffolds/<scaffold>/notify/.  runs/stage-1/<cell>/trial-NN/
cp docs/specs/stage-1-spec.md        runs/stage-1/<cell>/trial-NN/TASK-SPEC.md
```

Golden scaffolds are generated once via each framework's canonical command — see
`scaffolds/<scaffold>/GENERATE.md`. The scaffold → cell mapping is in
`docs/benchmark-protocol.md` §2. The `tiko-mcp` arm uses the `tiko` scaffold plus the
MCP instruction below.

## 2. The contestant prompt (give this verbatim — no extra hints)

> Build the system described in `TASK-SPEC.md`, inside this project directory.
> Work only in this directory. Do not look at any other project on the machine.
> The app must build (`mvn -DskipTests package`) and, when run, connect to a Kafka
> broker at `localhost:9092`, consume the four input topics, maintain the embedded
> H2 reference tables, and publish merged notifications. Report the exact command
> to run the app.
>
> **Dependency rules (asymmetric-native):**
> - **Spring:** use Spring's first-party starters (`spring-kafka`, `spring-data-jpa`, web if needed) + H2.
> - **Tiko:** use first-party modules (`tiko-config`, `tiko-kafka`, `tiko-test`); there is **no** first-party DB/HTTP module, so hand-build DB access (raw JDBC over a pooled `DataSource`) and any HTTP yourself — do not copy from `tiko-examples`.
>
> For the `tiko-mcp` arm additionally: the project ships a `.mcp.json` topology
> server — use it (or `target/classes/META-INF/tiko/*.json`) to validate your wiring
> and config before finishing.

**Stop rule:** agent declares done, or a frozen cap (default 90 min / 2M output
tokens / 400 turns — see `docs/benchmark-protocol.md` §7). Record tokens/turns/
wall-clock from the agent's transcript into `results/metrics.csv`.

## 3. Grade with the external oracle

Per trial, **fresh broker each time** (`apache/kafka:3.8.0` keeps topic data in an
anonymous volume, so `-v` is mandatory):

```
docker compose -f conformance/docker-compose.yml down -v
docker compose -f conformance/docker-compose.yml up -d
sleep 10
# pre-create the 5 topics so consumers can't miss messages on metadata refresh
for T in product-updates user-updates price-updates purchases notifications; do
  docker exec bench-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic "$T" --partitions 1 --replication-factor 1
done

# start the contestant app (per the agent's reported run command), wait ~35-40s
# then run the oracle:
java -jar conformance/oracle/target/conformance-oracle.jar \
     localhost:9092 fixtures/stage-1/scenarios.json results/stage-1-<cell>-NN.json

# record + tear down
# columns: timestamp,model,framework,stage,trial,compliance(%),wall_clock_seconds,output_tokens,agent_turns,tool_calls,cost_usd
echo "$(date -u +%FT%TZ),<model>,<cell>,stage-1,NN,<compliance-percent>,<seconds>,,,," >> results/metrics.csv
docker compose -f conformance/docker-compose.yml down -v
```

`compliance` = oracle expectations passed / 7 (6 scenarios; AC6 checks two
purchases), recorded in `metrics.csv` as a percent. Exit code is 0 only at 100%.

Automation (all three do the steps above: fresh broker with `down -v`, pre-created
topics, 40 s warm-up, oracle, process-tree kill, `metrics.csv` row):
- `conformance/stage-1/run-conformance.sh` / `.ps1` — one trial, any app start command.
- `conformance/stage-1/grade-batch.ps1` — many trials in sequence (jar or `mvn exec:java`);
  this is the script used for the published results. It has machine-specific JDK/Maven
  paths at the top — adjust them.

Set `BENCH_MODEL` to the contestant model ID so the `model` column is filled.

**Fixture versions:** all scripts default to v1 (`scenarios.json`). Grade new trials a
second time with v2 — `-Fixtures v2` (PowerShell) or `BENCH_FIXTURES=v2` (bash) — which
writes `results/stage-1-<cell>-NN.v2.json` and `results/metrics-v2.csv`. Grading is
non-destructive (a fresh broker and a fresh app start each time), so the same built trial
can be graded on both. See `fixtures/stage-1/README.md`.

## 4. Repeat

N = 5 trials per cell (per `docs/benchmark-protocol.md`); report median + spread.
Only compare efficiency / code quality at equal compliance.
