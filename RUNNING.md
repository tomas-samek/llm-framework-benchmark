# Running a trial (any agent)

This is the canonical, **neutral** procedure for running one benchmark trial with
any coding agent (Claude, GPT, Gemini, Cursor, …) and grading it objectively.
Keep it identical across agents — that is what makes the comparison fair.

> **Fairness rule:** give the agent **only** a fresh scaffold copy + the stage
> spec + the prompt below. Do **not** give it hints, the fixtures' expected outputs
> (`fixtures/`), the conformance oracle, `results/`, or any prior trial's code. Use the
> *same* prompt for every agent.

## Prerequisites

- JDK 21, Maven 3.9+, Docker.
- Build the conformance oracle once:
  `mvn -f conformance/oracle/pom.xml -q package` → `conformance/oracle/target/conformance-oracle.jar`.
- For the Tiko MCP arm only: the `tiko-mcp` server. The archetype's `.mcp.json` launches
  it through [jbang](https://www.jbang.dev/), but jbang's Windows launcher fails when
  several instances start at once. For parallel trials, resolve the jar once
  (`jbang info tools io.github.tomas-samek:tiko-mcp:0.6.0`) and start it directly:
  `java -cp tiko-mcp-0.6.0.jar io.tiko.mcp.TikoMcpServer <trial dir>`.

## 1. Create a fresh trial workspace

```
# <scaffold> ∈ { spring, spring41, spring3, tiko, tiko3, tiko5, tiko6 }
# <cell>     = the result-cell name, e.g. spring-406, spring-411, tiko-060, tiko-060-mcp
cp -a scaffolds/<scaffold>/notify/.  <work>/<cell>/trial-<prefix>-NN/
cp docs/specs/stage-1-spec.md        <work>/<cell>/trial-<prefix>-NN/TASK-SPEC.md
mkdir                                <work>/<cell>/m2-<prefix>-NN     # its own Maven repository
```

- **Work outside this repository** (`<work>`), so the agent can't see the repo's git
  state, results or other trials. Copy the finished trial into
  `runs/stage-1/<cell>/trial-<prefix>-NN/` for grading. `<prefix>` names the model, e.g.
  `o55`, `sol`; the grader drops it, so the CSV trial number is `NN`.
- **Give each trial its own Maven repository**, e.g. `MAVEN_OPTS=-Dmaven.repo.local=<work>/<cell>/m2-<prefix>-NN`
  in the agent's environment. A shared `~/.m2` leaks module names other projects
  downloaded (see `results/RESULTS.md` → "Maven cache leak").
- Golden scaffolds are generated once via each framework's canonical command (see
  `scaffolds/<scaffold>/GENERATE.md`). The scaffold → cell mapping is in
  `docs/benchmark-protocol.md` §2.

## 2. The contestant prompt (give it verbatim, no extra hints)

These are the exact prompts of the 2026-10 cells (`spring-406`, `spring-411`, `tiko-060`,
`tiko-060-mcp`). Only the three placeholders change per machine and trial:
`<TRIAL_DIR>` (the trial directory's absolute path), `<MAVEN>` (the `mvn` executable) and
`<JDK21>` (the JDK 21 home). Keep everything else byte for byte, including the report
format.

### Spring Boot cells

```
You are a contestant in a coding benchmark. Build the system described in TASK-SPEC.md, working ONLY inside this directory:

<TRIAL_DIR>

Rules (follow exactly):
- Work ONLY in the directory above. Do NOT read, list, search, or copy from any other project, scaffold, fixtures, results, oracle, or memory file anywhere on this machine. Your only inputs are TASK-SPEC.md and the Spring Boot scaffold already in that directory.
- This is a Spring Boot project. Use Spring's own first-party starters (e.g. spring-kafka, spring-data-jpa, web if you need it) plus H2.
- DO NOT change the Spring Boot version. Keep the parent <version> in pom.xml exactly as the scaffold ships it. Build against that version.
- The app must build with `mvn -DskipTests package` and produce a runnable jar under target/. When later run, it must connect to Kafka at localhost:9092, consume the four input topics, maintain the embedded H2 reference tables, and publish merged notifications exactly as the spec describes.
- Toolchain: use Maven at <MAVEN> and JDK 21 at <JDK21> (set JAVA_HOME=<JDK21> for Maven).
- Do NOT start/run the app — there is no broker running and a stray app process would corrupt later grading. Only build it green.

When finished, report exactly: (1) the precise command to run the app, (2) the final Spring Boot parent version in pom.xml, (3) whether `mvn -DskipTests package` succeeded (BUILD SUCCESS), and (4) one sentence on how you handled JSON serialization of notifications.
```

### Tiko cells

```
You are a contestant in a coding benchmark. Build the system described in TASK-SPEC.md, working ONLY inside this directory:

<TRIAL_DIR>

Rules (follow exactly):
- Work ONLY in the directory above. Do NOT read, list, search, or copy from any other project, scaffold, fixtures, results, oracle, or memory file anywhere on this machine. Your only inputs are TASK-SPEC.md and the project scaffold already in that directory (including its CLAUDE.md and .ai-skills/ guidance).
- This project uses Tiko DI (a compile-time dependency-injection framework). Use Tiko's own first-party modules (e.g. tiko-config, tiko-kafka, tiko-test). There is NO first-party DB or HTTP module, so hand-build database access yourself (raw JDBC over a pooled DataSource) and any HTTP yourself. Do NOT copy code from tiko-examples.
- The Tiko version is 0.6.0 and is published on Maven Central. Do NOT change or downgrade tiko.version. If a Tiko module is not in your local Maven cache, let Maven download it from Maven Central (you have network access) — do not assume it is unpublished.
- Do NOT use any MCP server. Rely only on the in-repo docs and the dependency jars.
- The app must build with `mvn -DskipTests package`. When later run, it must connect to Kafka at localhost:9092, consume the four input topics, maintain the embedded H2 reference tables, and publish merged notifications exactly as the spec describes. The pom's exec-maven-plugin is configured to run the Main class via `mvn exec:java`.
- Toolchain: use Maven at <MAVEN> and JDK 21 at <JDK21> (set JAVA_HOME=<JDK21> for Maven).
- Do NOT start/run the app — there is no broker running and a stray app process would corrupt later grading. Only build it green.

When finished, report exactly: (1) the precise command to run the app, (2) the Tiko version used, (3) whether `mvn -DskipTests package` succeeded (BUILD SUCCESS), (4) one or two sentences on how you discovered and used the Kafka source/sink API.
```

For another Tiko release, change only the version in the third rule.

### Tiko MCP arm

The Tiko prompt with one line replaced. Instead of
`- Do NOT use any MCP server. Rely only on the in-repo docs and the dependency jars.` use:

```
- The project ships a `.mcp.json` topology server: use it (or `target/classes/META-INF/tiko/*.json`) to validate your wiring and config before finishing.
```

Attach the `tiko-mcp` server pointed at the trial directory, with its read-only tools
pre-approved, and check that every session connected before grading.

### Agent setup

Run each trial in a fresh session with no user configuration, memory, plugins, rules or
extra MCP servers. Keep the agent's ordinary tools (shell, file editing, web search) and
disallow sub-agents. Record the agent, its version and the exact launch command with the
cell. The flags used for Claude Code and for Codex are in `results/RESULTS.md` (Fable 5
on the 4.0.6 control; gpt-6-astra on the 4.0.6 control).

**Stop rule:** the agent declares done, or a frozen cap (default 90 min / 2M output
tokens / 400 turns, see `docs/benchmark-protocol.md` §7). Record tokens, turns, tool
calls and wall-clock from the agent's transcript into `results/metrics.csv`.

> **Earlier prompt.** Cells before 2026-10 (the June–August `spring*`, `tiko`, `tiko-mcp`,
> `tiko3`/`tiko-030` and `tiko-050` cells) used a shorter prompt. It did not forbid
> running the app, did not pin the framework version and did not name the toolchain:
>
> > Build the system described in `TASK-SPEC.md`, inside this project directory.
> > Work only in this directory. Do not look at any other project on the machine.
> > The app must build (`mvn -DskipTests package`) and, when run, connect to a Kafka
> > broker at `localhost:9092`, consume the four input topics, maintain the embedded
> > H2 reference tables, and publish merged notifications. Report the exact command
> > to run the app.
> >
> > **Dependency rules (asymmetric-native):**
> > - **Spring:** use Spring's first-party starters (`spring-kafka`, `spring-data-jpa`, web if needed) + H2.
> > - **Tiko:** use first-party modules (`tiko-config`, `tiko-kafka`, `tiko-test`); there is **no** first-party DB/HTTP module, so hand-build DB access (raw JDBC over a pooled `DataSource`) and any HTTP yourself — do not copy from `tiko-examples`.
> >
> > For the `tiko-mcp` arm additionally: the project ships a `.mcp.json` topology
> > server — use it (or `target/classes/META-INF/tiko/*.json`) to validate your wiring
> > and config before finishing.
>
> Results from the two prompts aren't directly comparable: an agent allowed to run its
> app can catch startup failures before grading.

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

Grade only after every build of the batch has finished: build load can slow the graded
apps' startup, and a grading run writes results the contestants must not see. Before
each pass, restore every trial from a post-build snapshot (file-based H2 stores must not
carry over) and make sure no trial JVM is still running.

## 4. Repeat

N = 10 trials per model per cell for the 2026-10 cells (earlier cells used N = 5);
report the pass count, median and spread. Only compare efficiency and code quality at
equal compliance; the code-quality rubric is `results/reviews/RUBRIC.md`.
