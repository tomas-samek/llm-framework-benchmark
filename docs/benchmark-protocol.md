# Benchmark Protocol — Spring Boot vs Tiko DI (AI-friendliness)

**Date:** 2026-06-07
**Status:** Approved

## 1. Question

Given the **same framework-neutral specification**, which framework lets an AI
coding agent build the system *better*? "Better" is defined by three measured
signals (§8): spec-compliance, efficiency, and code quality.

This protocol governs every run. The per-stage specs
(`docs/specs/stage-1-spec.md`, `docs/specs/stage-2-spec.md`) describe **what** to
build and contain **no framework information**. This document is the only place
framework names, dependencies, and benchmark mechanics appear.

## 2. Contestants and the equal start

Each framework begins from its own **canonical, idiomatic fresh-start path**,
as-shipped — because "how easily can an AI stand up and build on this framework"
is part of what we measure.

| | Tiko | Spring Boot |
|---|---|---|
| Fresh start | `mvn archetype:generate` from the Tiko archetype | `spring init` (Spring Initializr) |
| Pre-equipped with | DI core wired; `tiko-config`/`tiko-kafka`/`tiko-test` present but **commented out**; bundled `CLAUDE.md`, `.ai-skills/`, `.mcp.json` (MCP topology server) | **core only** |
| Agent adds | enables opt-in modules; adds Lucene; hand-builds DB + HTTP | adds `spring-kafka`, `spring-data-jpa`, `spring-boot-starter-web`, H2, Lucene |

The exact generation commands are recorded in `scaffolds/spring/GENERATE.md` and
`scaffolds/tiko/GENERATE.md`, and the generated trees are committed **untouched**
as golden copies. Every trial is a fresh copy of the golden scaffold.

## 3. Pinned shared substrate (identical for both)

These are libraries/infrastructure, **not** the frameworks under test. Pinning
them isolates the only variable to Spring-vs-Tiko.

| Component | Version |
|---|---|
| JDK | 21 (Temurin) |
| Maven | 3.9.x |
| Kafka broker (docker) | `apache/kafka:3.8.0` (KRaft, single node, `localhost:9092`) |
| Embedded DB | H2 `2.2.224` |
| Search engine | Apache Lucene `9.11.1` |

Framework versions (each its own latest stable at time of writing): Spring Boot
`3.3.x`; Tiko `0.2.1`.

## 4. Fairness rule (asymmetric-native)

Each framework uses its own **first-party** capabilities; neither may copy from
sample/example repositories.

- **Spring** may use first-party starters: `spring-kafka`, `spring-data-jpa`,
  `spring-boot-starter-web`.
- **Tiko** may use first-party modules: `tiko-config`, `tiko-kafka`,
  `tiko-test`. Tiko has **no** first-party DB or HTTP module, so the agent
  **must hand-build**:
  - DB access: raw JDBC over a pooled `DataSource`, against H2.
  - HTTP endpoint (Stage 2): the JDK `com.sun.net.httpserver`.
  Code **must not** be lifted from `tiko-examples`.
- Kafka transport is allowed on both via their native module.

Rationale: Spring's batteries-included surface is a real part of its value, so it
keeps it; Tiko is denied the example crutch so the agent must genuinely build
integration code with framework + docs only. This is the deliberate
"make it harder for Tiko" handicap agreed for this benchmark.

## 5. Isolation

Each trial sees **only** its golden scaffold + the stage spec (+ for Stage 2, the
Stage-1 result it builds on). A trial must not have access to:
- the other framework's repository,
- the two original projects (`test-no-tiko`, `tiko-warmup-test`),
- any prior trial.

Fresh agent session per trial. No carry-over context.

## 6. As-shipped guidance

Tiko runs keep the archetype's bundled agent guidance and MCP topology server.
**Before each Tiko trial, verify the MCP server (jbang) is reachable**; record
the check in the trial log. If it is unavailable, the trial is void and re-run —
Tiko must be measured at its real as-shipped capability, not below it. Spring
runs rely on the model's built-in Spring knowledge (no special docs); that
asymmetry is intentional and is the honest real-world comparison.

## 7. Run procedure and stop rule

For each cell (framework x stage), run **N = 5** independent trials.

1. Copy golden scaffold → `runs/stage-<s>/<framework>/trial-NN/`.
2. Drop the stage spec into the trial as the agent's task input.
3. Start a fresh agent session; pin model, version, temperature, tool/permission
   posture (recorded in `results/metrics.csv`). Autonomous: no human
   intervention.
4. The run ends when **either** the agent declares the task complete **or** it
   reaches a safety cap. Recommended defaults (identical across all cells; tune
   once before the first run, then freeze): **90 minutes** wall-clock,
   **2,000,000** output tokens, **400** agent turns. The primary stop is
   agent-declared-done; the caps only catch runaways.
5. Run the external conformance oracle (§8.1) against the result; record metrics.
6. Archive the full transcript and final git state in the trial dir.

## 8. Metrics and scoring

Reported per trial, then aggregated as **median + min/max** across the 5 trials
of each cell. Efficiency and quality are only compared **at equal compliance** —
compliance gates the comparison.

### 8.1 Spec-compliance (backbone) — external oracle
Graded by `conformance/stage-<s>/`, a framework-independent harness that brings
up Kafka, publishes the fixture inputs (`fixtures/stage-<s>/scenarios.json`),
and asserts the observable outputs. **The agent's own tests do not count toward
compliance** — only the external oracle does. Score = fraction of acceptance
scenarios passed.

### 8.2 Efficiency
Output tokens, agent turns, tool calls, wall-clock — captured from the session
transcript. Recorded both **to first 100%-compliance** and **to agent-declared
done** (captures over-polishing).

### 8.3 Code quality (post-hoc, blinded rubric)
Independent review against a fixed rubric (idiomaticity, structure/cohesion,
defect count, test quality, error handling). If an LLM judge is used, run >=3
independent judges and take the median. Framework is identifiable from imports;
rely on the rubric to keep scoring consistent. Reviews stored in
`results/reviews/`.

## 9. Threats to validity (acknowledged)

- **Training-corpus asymmetry:** the model knows Spring far better than Tiko.
  This is intrinsic and is exactly what "as-shipped AI-friendliness" measures;
  it is not corrected for, only stated.
- **Judge identifiability:** code quality cannot be fully blinded (imports
  reveal the framework). Mitigated by a strict rubric and multiple judges.
- **Fixture timing:** Stage 1 has no query API, so the oracle uses bounded waits
  between publishing reference data and the triggering purchase. Waits are
  generous and identical across cells.
- **Single model / single point in time:** results describe this model+versions;
  re-run on model upgrades.

## 10. Deliverables per cell

`results/metrics.csv` rows (compliance, efficiency) + `results/reviews/` entries
(quality) for all 5 trials, plus a short written comparison once both frameworks
have completed a stage.
