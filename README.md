# LLM Framework Benchmark

**How well can an AI coding agent build the *same specified system* on different frameworks?**
A framework-neutral spec, an external black-box oracle that grades the result, and
clean per-framework scaffolds — so the only variable is the framework (and the
agent). First comparison: **Spring Boot vs [Tiko DI](https://github.com/tomas-samek/tiko-di)**,
on an event-driven Kafka → H2 → merged-notification service.

The benchmark grades each run on **compliance** (does it actually pass the
acceptance scenarios, judged by an independent harness — not the agent's own
tests), with hooks for **efficiency** and **code quality**.

📝 **Write-up:** [*Your AI coding agent has a blind spot — and a bigger model doesn't fix it*](https://www.linkedin.com/pulse/your-ai-coding-agent-has-blind-spot-bigger-model-doesnt-tom%25C3%25A1%25C5%25A1-samek-iwlyf)
— the story behind these numbers (LinkedIn). Feedback and your own results welcome.

> **Note on Tiko — intentionally unknown to LLMs.** Tiko DI is *purposefully*
> developed and used as an **out-of-training-corpus** framework. Its premise is
> that a framework can be AI-friendly **without** relying on the model's prior
> familiarity — instead shipping machine-readable, in-repo affordances:
> guidance (`CLAUDE.md`, `.ai-skills/`), compile-time wiring validation, and an
> MCP topology server. So in this benchmark Tiko is a deliberate test case for the
> question *"can an agent succeed on a framework it was never trained on, given
> good in-repo affordances?"* — which is also why it makes a fair foil for a
> brand-new, equally-unfamiliar Spring Boot **4.0.6**. (Stage-1 answer: largely
> yes — 86% median, vs Boot-4's 0%.)

## Headline results — Stage 1

The sharpest result is **version recency**, isolated cleanly: same spec, same fixed
scaffold, the **only** change is the Spring Boot version. Graded by the external oracle
(compliance = scenarios passed / 7), fresh broker per trial, contestants run as isolated
agents on the named model. Full write-up: [`results/RESULTS.md`](results/RESULTS.md)
(see "Three-model extension" for the authoritative numbers below).

| Spring Boot version | Sonnet 4.6 | Opus 4.8 | Sonnet 5 |
|---|---|---|---|
| **3.3.5** — in the training corpus | 100% (3/3) | 100% (3/3) | **100%** (4/5) |
| **4.0.6** — current major (~6 months old) | **0%** (0/5) | **0%** (0/5) | **0%** (0/5) |

**Three model generations, 15 independent trials on the current major, zero passes.**
Bigger and newer does not fix it — every trial fails on the identical root cause (a
missing `KafkaTemplate` bean from Boot 4's Kafka autoconfig reorganization), even in
Sonnet-5 trials that correctly adopted Jackson 3 and Spring Kafka's newest serializer
classes. Getting the JSON layer right didn't matter: **none of the 15 clean-run trials,
across all three models, ever reached for `spring-boot-starter-kafka`** — the actual fix.

On the other axis — an out-of-training-corpus framework that ships in-repo agent docs,
now on the latest release:

| Framework | Opus 4.8 | Sonnet 5 |
|---|---|---|
| Tiko **0.3.0** | **100%** median (4/5) | **100%** median (4/5) |

Both land at 100% median with one real failure apiece — the Opus failure independently
reproduces a documentation gap ([tiko-di#404](https://github.com/tomas-samek/tiko-di/issues/404))
filed from static analysis *before* this run and confirmed here by live reproduction.

**Token cost — a bigger/newer model is not a cheaper one.** Average output tokens per
build:

| Model | spring (Boot 4.0.6) | spring3 (Boot 3.3.5) | tiko (0.3.0) |
|---|---|---|---|
| Sonnet 4.6 | 45.0k | 36.5k | — |
| Opus 4.8 | 52.9k | 47.1k | 106.0k |
| Sonnet 5 | **88.2k** | **68.6k** | **186.9k** |

Sonnet 5 costs **~1.7–1.9×** Opus 4.8 and **~1.9×** Sonnet 4.6 on identical tasks,
consistently across every cell, for the same or statistically indistinguishable
compliance.

### What it found

1. **Version recency dominates — and a bigger model doesn't fix it.** Across three
   model generations and 15 independent trials, Spring Boot 4.0.6 scored **0%** every
   single time, while the same models scored ~100% on Boot 3.3.5. The failure is
   *silent and idiomatic*: every Boot-4 build compiled, but the models reflexively wrote
   the **Boot-3 Kafka idiom** (bare `spring-kafka` + trust auto-configuration) that
   Boot 4 reorganized away — so apps either failed to start (no `KafkaTemplate` bean) or
   booted and exited consuming nothing. Recency bites at *version choice* (given
   freedom, the models downgrade — Sonnet 5/5) **and** at *execution* (forced onto the
   new major, they use the old major's muscle memory) — and newer generations don't
   close the gap.

2. **A bigger/newer model is not a cheaper one.** Sonnet 5 spent 1.7–1.9× the tokens of
   Opus 4.8 on identical tasks (see table above) for the same compliance — on Tiko it
   explored deeper (bytecode-level jar inspection) but that thoroughness didn't
   translate into a better result, just a more expensive one.

3. **An out-of-training-corpus framework with in-repo docs holds up well** — Tiko 0.3.0
   reached 100% median for both Opus 4.8 and Sonnet 5, with the sole failures being
   already-filed, now-confirmed documentation gaps (kebab-case vs. camelCase config
   keys), not framework defects.

4. **The MCP topology server showed no compliance lift** in the original Tiko 0.2.2
   run — `tiko` and `tiko-mcp` were identical (`0 0 86 86 100`). The validation gate
   targeted *wiring*, which the model already got right; it did not target *config*,
   where Tiko's failures actually concentrated.

5. **Tiko's dominant failure mode is strict config validation** — kebab-case /
   Spring-style keys that Tiko's `@Configuration` records reject. Filed and tracked as
   [tiko-di#404](https://github.com/tomas-samek/tiko-di/issues/404), among
   [8 doc-friction issues](https://github.com/tomas-samek/tiko-di/issues/399) this
   benchmark surfaced and filed directly against Tiko.

> These are directional results: small N and a few points in time per cell. See
> `results/RESULTS.md` → "Three-model extension" for the full breakdown, the grading
> erratum (a harness bug that produced false negatives, now corrected), and
> "Caveats / validity".

## How it works

- **Framework-neutral spec** (`docs/specs/stage-1-spec.md`) — describes *what* to
  build, with zero framework words.
- **External conformance oracle** (`conformance/oracle/`, Java) — brings up Kafka,
  publishes the shared fixtures (`fixtures/stage-1/scenarios.json`), drains the
  output topic, and asserts the result. The agent's own tests don't count.
- **Asymmetric-native fairness** — each framework uses its own first-party stack;
  neither may copy from example repos. Tiko, having no first-party DB/HTTP module,
  must hand-build those. Full rules in `docs/benchmark-protocol.md`.
- **Pinned substrate** — apache/kafka 3.8.0, H2 2.2.224, Lucene 9.11.1, JDK 21,
  identical for everyone, so the only variable is the framework + the agent.

## Run it — including with other agents

See **[`RUNNING.md`](RUNNING.md)** for the canonical, neutral per-trial procedure:
copy a golden scaffold, hand the agent only the spec + dependency rules (no hints),
let it build, then grade with the oracle. The procedure is agent-agnostic — point
Claude, GPT, Gemini, Cursor, etc. at the same scaffold + spec and compare. Running
it across agents is the natural next step.

## Layout

```
docs/   benchmark-protocol.md (rules) + specs/ (framework-neutral WHAT)
fixtures/   shared acceptance vectors (input -> expected output)
conformance/   oracle (Java) + docker-compose Kafka + run scripts
scaffolds/   golden per-framework starting points (+ GENERATE.md)
results/   metrics.csv + per-trial oracle JSON + RESULTS.md
runs/   per-trial workspaces (git-ignored; see runs/README.md)
```

## Status

- [x] Protocol, Stage-1 spec, fixtures, conformance oracle (tests green)
- [x] Golden scaffolds (Spring Boot 4.0.6 + 3.3.5 reference; Tiko 0.2.2 → 0.3.0)
- [x] Stage-1 run: spring, tiko, tiko-mcp (5x) + spring3 reference — `results/RESULTS.md`
- [x] Clean Spring re-run, fixed scaffold, Sonnet 4.6 + Opus 4.8 (version-recency isolated)
- [x] Three-model extension: Sonnet 5 added, Tiko bumped to 0.3.0 (unanimous 0% on Boot
      4.0.6 across all three models; Sonnet 5 token-cost premium measured)
- [ ] Run with non-Claude agents
- [ ] Capture efficiency + code-quality rubric; n=10
- [ ] Stage-2 spec (full-text search)

## License

MIT — see [`LICENSE`](LICENSE).
