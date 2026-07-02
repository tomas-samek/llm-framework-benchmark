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

| Spring Boot version | Sonnet 4.6 | Opus 4.8 | Sonnet 5 | Fable 5 |
|---|---|---|---|---|
| **3.3.5** — in the training corpus | 100% (3/3) | 100% (3/3) | **100%** (4/5) | **100%** (5/5) |
| **4.0.6** — current major (~6 months old) | **0%** (0/5) | **0%** (0/5) | **0%** (0/5) | **20%** (1/5) |

**Four model generations, 20 independent trials on the current major — exactly one
pass, and it's the one trial that found the fix.** Nineteen trials failed on the
identical root cause (a missing `KafkaTemplate` bean from Boot 4's Kafka autoconfig
reorganization), even when they correctly adopted Jackson 3 and Spring Kafka's newest
serializer classes. The single pass is a Fable 5 trial that added
**`spring-boot-starter-kafka`** — the actual Boot-4 fix — explicitly reasoning that
Boot 4 moved Kafka autoconfig into its own module. Fable *carries* that knowledge but
retrieved it **1 time in 5**; no other model ever found it. The blind spot weakens with
model recency; it does not disappear.

On the other axis — an out-of-training-corpus framework that ships in-repo agent docs,
now on the latest release:

| Framework | Opus 4.8 | Sonnet 5 | Fable 5 |
|---|---|---|---|
| Tiko **0.3.0** | **100%** median (4/5) | **100%** median (4/5) | **100%** (5/5) |

All at 100% median (Fable sweeps 5/5). The two real failures independently reproduce a
documentation gap ([tiko-di#404](https://github.com/tomas-samek/tiko-di/issues/404))
filed from static analysis *before* these runs — and five Fable trials live-reproduced
[tiko-di#400](https://github.com/tomas-samek/tiko-di/issues/400) at compile time and
self-corrected.

**Token cost — newer is not automatically cheaper, and expensive is not better.**
Average output tokens per build:

| Model | spring (Boot 4.0.6) | spring3 (Boot 3.3.5) | tiko (0.3.0) |
|---|---|---|---|
| Sonnet 4.6 | 45.0k | 36.5k | — |
| Opus 4.8 | 52.9k | 47.1k | 106.0k |
| Sonnet 5 | **88.2k** | **68.6k** | **186.9k** |
| Fable 5 | 54.9k | 49.4k | 107.8k |

Sonnet 5 costs **~1.7–1.9×** Opus 4.8 on identical tasks for the same compliance —
while Fable 5 gets the **best results** (only Boot-4 pass, perfect Tiko sweep) at
**near-lowest cost**. Spending more tokens doesn't buy correctness; current knowledge
does.

### What it found

1. **Version recency dominates — and neither size nor tokens fixes it.** Across four
   model generations and 20 independent trials, Spring Boot 4.0.6 produced **one**
   working app, while the same models scored ~100% on Boot 3.3.5. The failure is
   *silent and idiomatic*: every Boot-4 build compiled, but the models reflexively wrote
   the **Boot-3 Kafka idiom** (bare `spring-kafka` + trust auto-configuration) that
   Boot 4 reorganized away — so apps either failed to start (no `KafkaTemplate` bean) or
   booted and exited consuming nothing. Recency bites at *version choice* (given
   freedom, the models downgrade — Sonnet 5/5) **and** at *execution* (forced onto the
   new major, they use the old major's muscle memory). The newest model (Fable 5) is the
   only one that ever produced the fix (`spring-boot-starter-kafka`) — **once in five
   tries**. The blind spot weakens with model recency; it does not close.

2. **Token spend and correctness are uncorrelated here.** Sonnet 5 spent 1.7–1.9× the
   tokens of Opus 4.8 on identical tasks for the same compliance (deeper bytecode-level
   exploration that didn't change outcomes), while Fable 5 got the best results of any
   model at near-lowest cost. What separated models was *what they knew*, not *how hard
   they worked*.

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
- [x] Multi-model extension: Sonnet 5 + Fable 5 added, Tiko bumped to 0.3.0 (1/20 pass
      on Boot 4.0.6 across four models — the one trial that found the new starter;
      per-model token costs measured)
- [ ] Run with non-Claude agents
- [ ] Capture efficiency + code-quality rubric; n=10
- [ ] Stage-2 spec (full-text search)

## License

MIT — see [`LICENSE`](LICENSE).
