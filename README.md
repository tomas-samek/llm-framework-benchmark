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

**Token cost (re-verified 2026-07-03 — see erratum).** An earlier version of this
table used the Agent-tool dispatch's `subagent_tokens` figure, which turned out to
measure something closer to "final conversation context size" than "output tokens
generated" (see `results/pricing.md`). All 51 trials behind this table were
re-verified by reconstructing real per-call usage from Claude Code's persisted
subagent transcripts (`conformance/token-accounting.py`) — real average output
tokens per build:

| Model | spring (Boot 4.0.6) | spring3 (Boot 3.3.5) | tiko (0.3.0) |
|---|---|---|---|
| Sonnet 4.6 | 3.4k | 3.0k | — |
| Opus 4.8 | 2.6k | 2.8k | 4.2k |
| Sonnet 5 | **4.5k** | **7.7k** | **6.7k** |
| Fable 5 | 2.5k | 0.7k | 2.2k |

Sonnet 5 still uses meaningfully more tokens than Opus 4.8 on identical tasks for the
same compliance (1.6×–2.7× depending on cell) — but tokens aren't dollars. Real
average cost per build (input + cache-write + cache-read + output, each priced
correctly for its model):

| Model | spring (Boot 4.0.6) | spring3 (Boot 3.3.5) | tiko (0.3.0) |
|---|---|---|---|
| Sonnet 4.6 | **$0.52** | **$0.35** | — |
| Opus 4.8 | $0.85 | $0.63 | $2.65 |
| Sonnet 5 | $1.17 | $0.76 | $3.62 |
| Fable 5 | $1.21 | $0.88 | **$3.44** |

**Fable 5 uses fewer real output tokens than Opus 4.8 in every single cell** (0.7k–2.5k
vs 2.6k–4.2k) — the opposite of what the flawed numbers implied — but is still the
**most expensive per build in dollars** in two of three cells, because its $50/1M
output and $10/1M input rates outweigh the lower token count. Sonnet 4.6 is the
cheapest model in every cell it has data for. Spending more tokens doesn't buy
correctness, and fewer tokens doesn't mean cheaper; current knowledge determines
correctness, and per-model pricing determines cost, largely independently of each
other.

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

2. **Token spend and correctness are uncorrelated here.** Sonnet 5 spent 1.6×–2.7× the
   real tokens of Opus 4.8 on identical tasks for the same compliance (deeper
   bytecode-level exploration that didn't change outcomes), while Fable 5 wrote *less*
   code than Opus in every cell yet got the best compliance results of any model —
   though not at the lowest dollar cost, since its per-token pricing is highest. What
   separated models on compliance was *what they knew*, not *how hard they worked*.

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

6. **Tiko can win on compliance but not on Stage-1 setup cost — and it's iteration
   count, not code volume, that costs.** Across the full 109-trial corpus
   (`results/metrics.csv`, every cell including the pre-"-fix" originals), Tiko's
   cheapest cell-average build cost exceeds Spring's priciest cell-average build cost,
   for every model tested. The obvious hypothesis — Tiko costs more because the agent
   hand-writes more code (no first-party DB/HTTP module) — turns out to be a minor
   contributor: actual code written (output tokens) is only ~2% of the cost gap. What
   dominates is **~2× more turns on every model** (most extreme for Sonnet 4.6, a 3.2×
   increase), which compounds into ~4× more re-read conversation context — the agent
   needs more *rounds* to discover Tiko's undocumented API surface and satisfy its
   strict compile-time DI validation, not more *lines*. See `results/RESULTS.md` →
   "Cross-cell cost analysis" for the full component breakdown.

> These are directional results: small N and a few points in time per cell. See
> `results/RESULTS.md` → "Three-model extension" for the full breakdown, the grading
> erratum (a harness bug that produced false negatives, now corrected), and
> "Caveats / validity".

## Headline results — Stage 2

Stage 1 is one-shot: build, grade, done — the agent never runs its own app. Stage 2
keeps the *identical* task but lets the agent iterate against a CI-gate (pass/fail per
scenario, no expected values — mirrors a black-box integration-test suite) until it
believes it complies. Only the Boot 4.0.6 cell is informative (everything else already
complies one-shot). N=3 per model, four models. Full write-up, methodology, and a
[token-accounting erratum](results/pricing.md) worth reading:
[`results/RESULTS.md`](results/RESULTS.md#stage-2--loop-until-complies-2026-07-0203-cost-corrected-2026-07-03).

| Model | Converged | Iterations to pass | Cost to compliance ($, real — input + cache write/read + output) |
|---|---|---|---|
| Fable 5 | **3/3** | 2, 2, **1** | avg **$1.26** |
| Opus 4.8 | **3/3** | 2, 2, 2 | avg **$0.99** — cheapest |
| Sonnet 5 | **3/3** | 2, **1**, 2 | avg **$1.06** |
| Sonnet 4.6 | **3/3** | 3, 2, 3 | avg **$2.52** — most expensive, by ~2.5× |

**The version-recency wall is fully iteration-proof: 12/12 trials converge to 100%
compliance**, across every model tried, in 1–3 gate runs — the same cell that scored
0/5 one-shot for three of the four models. The one-shot 0% headline above is a
*feedback* problem, not a knowledge wall: an agent that never runs its own app ships a
silently-broken Boot-4 service every time; an agent that runs-and-checks recovers every
time, cheaply. Cost tracks **iteration count**, not code volume — the single most
expensive trial (Sonnet 4.6, 3 iterations, $4.71) costs ~4× any other trial, because
every extra gate iteration means re-reading a larger accumulated conversation, and that
cache-read volume dominates real cost far more than the code the model actually writes.

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
- [x] Stage 2 — loop-until-complies harness on the Boot 4.0.6 cell, N=3 across all four
      models (Sonnet 4.6, Opus 4.8, Fable 5, Sonnet 5): **12/12 converged to 100%
      compliance** in 1–3 gate iterations — the one-shot 0% is a feedback problem, not a
      knowledge wall. Real (transcript-reconstructed) cost: Opus 4.8 cheapest (avg
      $0.99), Sonnet 4.6 most expensive by ~2.5× (avg $2.52, driven by a 3-iteration
      outlier trial). See `results/RESULTS.md` → "Stage 2 — loop until complies" and
      the token-accounting erratum in `results/pricing.md`.
- [ ] Run with non-Claude agents
- [ ] Capture efficiency + code-quality rubric; n=10
- [ ] Stage 2 — extend loop-until-complies to other cells (spring3, tiko) and add a
      held-out acceptance set to detect gate overfitting
- [ ] Stage 3 spec — brownfield feature-add (Lucene full-text search + HTTP query)

## License

MIT — see [`LICENSE`](LICENSE).
