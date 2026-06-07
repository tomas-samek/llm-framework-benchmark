# Spring Boot vs Tiko DI — AI-friendliness benchmark

Which framework lets an AI agent build the *same specified system* better?
"Better" = passes the shared acceptance tests, at lower token/turn/time cost,
at higher code quality.

This repo holds **only the benchmark harness** — the neutral specs, the shared
acceptance fixtures, the external conformance oracle, the clean scaffolds, and
the per-trial run workspaces. It contains no system-under-test code itself;
that is produced *inside* `runs/` by each agent trial.

## What's pinned (shared substrate, identical for both)

Apache Kafka (broker), H2 (embedded DB), Apache Lucene (search engine), JDK 21,
Maven. Exact versions are pinned in `docs/benchmark-protocol.md`. These are
libraries/infrastructure, **not** the frameworks under test — pinning them
isolates the only variable to Spring-vs-Tiko.

## Fairness rule (asymmetric-native)

- **Spring** may use first-party starters: `spring-kafka`, `spring-data-jpa`,
  `spring-boot-starter-web`.
- **Tiko** may use first-party modules: `tiko-config`, `tiko-kafka`,
  `tiko-test`; and **must hand-build** DB access (raw JDBC over a pooled
  `DataSource`) and the HTTP endpoint (JDK `com.sun.net.httpserver`) — never
  lifted from `tiko-examples`.

## Starting line (each framework's canonical fresh start, as-shipped)

- **Tiko:** `mvn archetype:generate` from the Tiko archetype, untouched
  (keeps its bundled `CLAUDE.md`, `.ai-skills`, `.mcp.json` / MCP topology
  server). Stored as a golden copy in `scaffolds/tiko/`.
- **Spring:** `spring init` with **core only**; the agent adds dependencies as
  the spec requires. Stored as a golden copy in `scaffolds/spring/`.

## Directory layout

```
docs/
  benchmark-protocol.md     # the rules: scaffolds, fairness, substrate, metrics, scoring, stop rule
  specs/
    stage-1-spec.md         # neutral WHAT: ingest -> merge -> emit notification
    stage-2-spec.md         # neutral WHAT: add full-text search index + query endpoint
fixtures/
  stage-1/                  # shared acceptance test vectors (input -> expected output, JSON)
  stage-2/
conformance/
  stage-1/                  # external black-box oracle (docker-compose Kafka + checker)
  stage-2/
scaffolds/
  spring/  tiko/            # golden clean starting points (copied per trial, never edited)
runs/
  stage-1/spring/<trial-NN>/   # fresh copy of golden scaffold + the stage spec; agent works here
  stage-1/tiko/<trial-NN>/
  stage-2/spring/<trial-NN>/
  stage-2/tiko/<trial-NN>/
results/
  metrics.csv              # per trial: compliance %, tokens, turns, tool calls, wall-clock
  reviews/                 # per trial: blinded rubric-based code-quality review
```

## Method (summary; full detail in `docs/benchmark-protocol.md`)

- **Multiple trials per cell:** N>=3 (target 5) independent trials per framework
  per stage, fresh session each. Report median + spread.
- **Compliance graded externally** by the `conformance/` oracle, not by the
  agent's own tests.
- **Pin & stop rule:** same model/version/temperature/tooling; a run ends when
  the agent declares done or hits the token/turn/wall-clock cap.
- **Isolation:** each trial sees only its scaffold + the stage spec — never the
  other framework, the originals, or prior trials.
- **Tiko affordance check:** verify the MCP server/jbang is reachable per Tiko
  run, so Tiko is measured at its real as-shipped capability.

## Status

- [x] Directory skeleton
- [ ] `docs/benchmark-protocol.md`
- [ ] `docs/specs/stage-1-spec.md`
- [ ] Stage-1 fixtures + conformance oracle
- [ ] Golden scaffolds generated
- [ ] `docs/specs/stage-2-spec.md` (after Stage 1)
