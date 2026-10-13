# Benchmark Protocol — Spring Boot vs Tiko DI (AI-friendliness)

**Date:** 2026-06-07
**Status:** Approved

## 1. Question

Given the **same framework-neutral specification**, which framework lets an AI
coding agent build the system *better*? "Better" is defined by three measured
signals (§8): spec-compliance, efficiency, and code quality.

This protocol governs every run. The per-stage specs describe **what** to build
and contain **no framework information**. This document is the only place
framework names, dependencies, and benchmark mechanics appear.

### Stages — a workflow-realism ladder

The stages are not just increasing task size; they increase how closely the run
mirrors a real agentic workflow. The **task spec** may be shared across stages;
what changes is the **mode**.

| Stage | Task spec | Mode | Measures |
|---|---|---|---|
| **1 — One-shot build** | `stage-1-spec.md` (Kafka → H2 → notifications) | build once, graded by the hidden oracle; the agent never runs the app | cold-build correctness — the version-recency / doc-discovery results to date |
| **2 — Loop until complies** | `stage-1-spec.md` (same task) | the agent runs the app against a live broker and **iterates until it believes it complies**, capped by a budget | whether iteration *converges* — and at what iteration/token cost — or whether a missing-knowledge failure (e.g. never discovering `spring-boot-starter-kafka`) is one that looping cannot escape |
| **3 — Evolve (brownfield)** | `stage-3-spec.md` *(planned — not written yet)* (add Lucene full-text search + an HTTP query endpoint to the Stage-1 app) | given a working Stage-1 codebase in a fresh context, extend it without breaking the existing acceptance scenarios | cold-codebase mutation — regression safety + new-feature correctness |

**Stage 2 — the loop's feedback signal** (this determines what Stage 2 measures;
see §"Stage 2 loop" below). **Decided: (b)**, implemented by
`conformance/stage-2/ci-gate.ps1` and used for the runs in `results/stage-2-loop.csv`.
Known limitation: the gate runs the same scenarios that grade the final result, so a
held-out scenario set (to detect overfitting to the gate) is still a TODO.
- **(a) sample-data only** — the agent gets a live broker + a sample publisher
  (*not* the graded fixtures) and self-verifies. Purest; but "complies against my
  own samples" ≠ "passes the hidden oracle".
- **(b) CI-gate pass/fail** *(recommended)* — additionally, a gate reports *which
  acceptance scenarios failed*, pass/fail only, **no expected values**. Mirrors a
  provided integration-test suite a developer runs; realistic, minimal leakage.
- **(c) full oracle diff** — expected-vs-actual shown. Rejected: leaks the answer
  key and measures hill-climbing against the grader, not capability.

### Stage 2 loop (mechanics)

Builds on the Gate-2 harness in `gate-protocol.md` (per-contestant live broker on a
unique port + embedded H2 + sample publisher). The agent iterates: run → observe →
fix, until it declares done (self-verified against option (a)/(b) above) **or** hits a
frozen budget cap (tokens / iterations / wall-clock). The **hidden oracle still grades
once at the end**. Captured per trial: iterations-to-declared-done, tokens-to-done,
final compliance, and — the headline metric — **did it converge within budget**.

## 2. Contestants and the equal start

Each framework begins from its own **canonical, idiomatic fresh-start path**,
as-shipped — because "how easily can an AI stand up and build on this framework"
is part of what we measure.

| | Tiko | Spring Boot |
|---|---|---|
| Fresh start | `mvn archetype:generate` from the Tiko archetype | `spring init` (Spring Initializr) |
| Pre-equipped with | DI core wired; `tiko-config`/`tiko-kafka`/`tiko-test` present but **commented out**; bundled `CLAUDE.md`, `.ai-skills/`, `.mcp.json` (MCP topology server) | core + `spring-boot-starter-json` (Jackson + `ObjectMapper` bean, mirroring the JSON serializer `tiko-kafka` bundles; added 2026-06-13 — see `scaffolds/spring/GENERATE.md`) |
| Agent adds | enables opt-in modules; adds Lucene; hand-builds DB + HTTP | adds `spring-kafka`, `spring-data-jpa`, `spring-boot-starter-web`, H2, Lucene |

The exact generation commands are recorded in each scaffold's `GENERATE.md`, and the
generated trees are committed **untouched** as golden copies. Every trial is a fresh
copy of the golden scaffold.

| Scaffold | Framework version | Result cells |
|---|---|---|
| `scaffolds/spring/` | Spring Boot 4.0.6 | `spring-fix` (`spring`, `spring-free`: earlier core-only revision\*); `spring-406` (5.5-model control plus Fable 5 run headless, graded on v1 and v2 fixtures) |
| `scaffolds/spring41/` | Spring Boot 4.1.1 (current) | `spring-411` (graded on v1 and v2 fixtures) |
| `scaffolds/spring3/` | Spring Boot 3.3.5 (version-recency control) | `spring3-fix` (`spring3`: earlier core-only revision\*) |
| `scaffolds/tiko/` | Tiko 0.2.2 (historical) | `tiko`, `tiko-mcp` |
| `scaffolds/tiko3/` | Tiko 0.3.0 (historical) | `tiko-030` |
| `scaffolds/tiko5/` | Tiko 0.5.0 | `tiko-050` |
| `scaffolds/tiko6/` | Tiko 0.6.0 (current) | `tiko-060` (graded on v1 and v2 fixtures) |

\* Before 2026-06-13 the Spring scaffolds were core-only (no `spring-boot-starter-json`);
see the "Spring scaffold confound" section of `results/RESULTS.md`. That revision is in
git history.

Tiko scaffolds must be generated from the archetype **as published on Maven
Central**. On a machine that also builds Tiko from source, `~/.m2` may hold a
locally-installed archetype/BOM under the release version number; generate (and run
trials) with an isolated `-Dmaven.repo.local=…` so nothing unreleased leaks in.

## 3. Pinned shared substrate (identical for both)

These are libraries/infrastructure, **not** the frameworks under test. Pinning
them isolates the only variable to Spring-vs-Tiko.

| Component | Version |
|---|---|
| JDK | 21 (Temurin) |
| Maven | 3.9.x |
| Kafka broker (docker) | `apache/kafka:3.8.0` (KRaft, single node, `localhost:9092`) |
| Embedded DB | H2 `2.2.224` *(target only — not enforced: the scaffolds don't ship H2 and the contestant prompt doesn't pin it, so the agent picks the version, and Spring Boot's dependency management supplies its own)* |
| Search engine | Apache Lucene `9.11.1` |

Framework versions are whatever each framework's canonical fresh-start path
yields at generation time (this is part of the as-shipped condition):
- **Spring Boot `4.1.1`** — the Spring Initializr default as of 2026-10-12; scaffold in
  `scaffolds/spring41/`, benchmarked as `spring-411`. It was derived from the 4.0.6
  scaffold by repinning the parent version, because Initializr no longer offers the
  `json` dependency the original command used (see `scaffolds/spring41/GENERATE.md`).
  `4.0.6`, the Initializr default when the benchmark started (2026-06), remains as the
  `spring-fix` cell, and as `spring-406`, the control that ran the 5.5 models on it
  (2026-10-13). (Initializr serves only supported lines; 3.3.x is no longer
  offered, so it cannot be pinned via `spring init`.)
- **Tiko `0.6.0`** via `tiko-archetype:0.6.0` (latest on Maven Central as of
  2026-10-11; scaffold in `scaffolds/tiko6/`, benchmarked 2026-10-11 as `tiko-060`).
  `0.5.0` (2026-08-15), `0.2.2` and `0.3.0` remain as historical/comparison cells —
  see `results/RESULTS.md`. (`0.4.0` was skipped.)

The Lucene/H2/Kafka substrate versions above are pinned independently of the
framework versions and are identical for both contestants.

## 4. Fairness rule (asymmetric-native)

Each framework uses its own **first-party** capabilities; neither may copy from
sample/example repositories.

- **Spring** may use first-party starters: `spring-kafka`, `spring-data-jpa`,
  `spring-boot-starter-web`.
- **Tiko** may use first-party modules: `tiko-config`, `tiko-kafka`,
  `tiko-test`. Tiko has **no** first-party DB or HTTP module, so the agent
  **must hand-build**:
  - DB access: raw JDBC over a pooled `DataSource`, against H2.
  - HTTP endpoint (Stage 3): the JDK `com.sun.net.httpserver`.
  Code **must not** be lifted from `tiko-examples`.
- Kafka transport is allowed on both via their native module.

Rationale: Spring's batteries-included surface is a real part of its value, so it
keeps it; Tiko is denied the example crutch so the agent must genuinely build
integration code with framework + docs only. This is the deliberate
"make it harder for Tiko" handicap agreed for this benchmark.

## 5. Isolation

Each trial sees **only** its golden scaffold + the stage spec (+ for Stage 3, the
Stage-1 result it builds on). A trial must not have access to:
- the other framework's repository,
- the two original projects (`test-no-tiko`, `tiko-warmup-test`),
- any prior trial,
- artifacts other projects put in a shared Maven cache. A listing of a shared `~/.m2`
  shows module names the trial's own build never downloaded. In 2026-10 this leaked the
  Boot 4 Kafka starter's name to six trials (`results/RESULTS.md` → "Maven cache leak").
  Give each trial its own `-Dmaven.repo.local`. Reading the project's own parent BOM is
  fine.

Fresh agent session per trial. No carry-over context.

**Harness.** Contestants normally run as Claude Code subagents (Agent tool, model set
per trial and verified from the transcript). A model the Agent tool can no longer select
runs as a headless top-level session instead: `claude -p --model <id>` from a directory
outside this repo, with `--setting-sources ""`, `--strict-mcp-config`, auto-memory off,
`--permission-mode auto`, and the Agent/Workflow tools disallowed (first used for Fable 5
in `spring-406`, 2026-10-13; full command in `results/RESULTS.md`). The two harnesses
gave the same result for Fable 5 on Boot 4.0.6 (9/10 headless, 8/10 as a subagent of a
headless Fable 5 session, both on Claude Code 2.1.296), and so did Claude Code 2.1.197
(9/10 as a subagent). The July subagent run on 2.1.197 nonetheless scored 1/5. It ran
inside an interactive session with user settings, plugins and CLAUDE.md, with the
trials inside the repo. Record the harness, the Claude Code version and the session
context with every cell.

**Non-Claude agents** run through their own CLI with the same prompt and the same
isolation intent. That means no user config, rules, plugins, apps or subagents, while
keeping shell, file editing and web search. Each trial gets a fresh scaffold outside this
repo, its own session, and its own Maven repository. The first was gpt-6-astra via Codex
CLI 0.162.1 (`spring-406`, 2026-10-13; flags in `results/RESULTS.md`). On Windows,
Codex needs its elevated sandbox, and the Codex desktop app must be closed while trials
run.

## 6. As-shipped guidance

Tiko runs keep the archetype's bundled agent guidance (`CLAUDE.md`, `.ai-skills/`)
and its `.mcp.json` file. Use of the MCP topology server is a **separate arm**:
`tiko-mcp` trials are told to use it (see `RUNNING.md`) and **must verify the MCP
server (jbang) is reachable** before starting, recording the check in the trial log;
if it is unavailable, the trial is void and re-run. Plain `tiko` cells (including
`tiko-030` and `tiko-050`) get no MCP instruction. Stage-1 showed no compliance lift
from the MCP arm, so newer Tiko versions were run without it. Spring
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
compliance** — only the external oracle does. Score = fraction of the 7 acceptance
expectations passed (6 scenarios; AC6 checks two purchases).

**Fixture versions.** Stage 1 has two fixture sets for the *same* contestant spec:
`scenarios.json` (**v1** — every result through 2026-08-15) and `scenarios.v2.json`
(**v2**, 2026-10-11), which fixes v1 scenarios that earlier scenarios' leftover state
could satisfy (AC4, AC5) and enforces "exactly one notification per purchase". Scores are
only comparable within a fixture version; v2 results are recorded separately
(`results/*.v2.json`, `results/metrics-v2.csv`). New cells should be graded on **both**
until v2 has enough history to stand alone. Details: `fixtures/stage-1/README.md`.

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
- **Harness drift:** the conditions around the agent affect results. The same model and
  prompt on Boot 4.0.6 went from 1/5 (July, a subagent inside an interactive session) to
  8–9/10 in October as a clean headless session or its subagent, on both Claude Code
  2.1.296 and July's 2.1.197. The Claude Code version and the subagent form are ruled out.
  Session context and serving-side changes are not. Cross-time comparisons confound
  model and harness.

## 10. Deliverables per cell

`results/metrics.csv` rows (compliance, efficiency) + `results/reviews/` entries
(quality) for all 5 trials, plus a short written comparison once both frameworks
have completed a stage.
