<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://tomas-samek.github.io/banners/dark/llm-framework-benchmark.svg">
  <img alt="llm-framework-benchmark: Same spec, different stacks, graded by an external oracle" src="https://tomas-samek.github.io/banners/light/llm-framework-benchmark.svg" width="100%">
</picture>

# LLM Framework Benchmark

**How well can an AI coding agent build the *same specified system* on different frameworks?**
A framework-neutral spec, an external black-box oracle that grades the result, and
clean per-framework scaffolds — so the only variable is the framework (and the
agent). The first spec is an event-driven Kafka → H2 → merged-notification service,
built on Spring Boot (3.3.5, 4.0.6 and 4.1.1) and on
[Tiko DI](https://github.com/tomas-samek/tiko-di), a framework the models were never
trained on.

The benchmark grades each run on **compliance** (does it actually pass the
acceptance scenarios, judged by an independent harness — not the agent's own
tests), with hooks for **efficiency** and **code quality**.

📝 **Write-up:** [*Your AI coding agent has a blind spot — and a bigger model doesn't fix it*](https://www.linkedin.com/pulse/your-ai-coding-agent-has-blind-spot-bigger-model-doesnt-tom%25C3%25A1%25C5%25A1-samek-iwlyf)
— the story behind these numbers (LinkedIn). Feedback and your own results welcome.

🌐 **More projects and writing:** [tomas-samek.github.io](https://tomas-samek.github.io/)

> **Why Tiko is in the benchmark — it's intentionally unknown to LLMs.** Tiko DI is
> developed and used as an **out-of-training-corpus** framework. Its premise is that a
> framework can be AI-friendly **without** relying on the model's prior familiarity,
> by shipping machine-readable, in-repo affordances instead: guidance (`CLAUDE.md`,
> `.ai-skills/`), compile-time wiring validation, and an MCP topology server. Here it
> is the test case for the question *"can an agent succeed on a framework it was never
> trained on, given good in-repo affordances?"* (Stage-1 answer: yes — 86% median on
> Tiko 0.2.2, then 100% on 0.5.0 and 0.6.0 across three model generations.)

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
retrieved it **1 time in 5** in that run; no other model of that generation ever found it.

**Today the gap is closed (2026-10-12/13).** On the current Boot release, 4.1.1
(the Spring Initializr default; same major, same Kafka module split), the 5.5 models
went **30/30** on both fixture sets, and **every one of the 30 trials added
`spring-boot-starter-kafka`** and used Jackson 3:

| Spring Boot version | Opus 5.5 | Sonnet 5.5 | Haiku 5.5 |
|---|---|---|---|
| **4.1.1** — current release (~2 months old) | **100%** (10/10) | **100%** (10/10) | **100%** (10/10) |
| **4.0.6** — the scaffold the older models scored 0–20% on | **100%** (10/10) | **100%** median (9/10) | **100%** (10/10) |

The 4.0.6 control (2026-10-13) shows the patch release isn't the difference: on the same
4.0.6 scaffold, all 30 builds added `spring-boot-starter-kafka`. The one failure, a Sonnet
5.5 trial, never started because Spring Data didn't find its nested repository
interfaces, which is unrelated to Boot 4. Both fixture sets gave the same scores.

**But it isn't cleanly a model-generation effect.** Fable 5, the model that scored 1/5 on
this scaffold in July, was rerun with the identical prompt and scaffold: **8/10** as a
subagent, as in July, and **9/10** as a top-level session. The model ID and the subagent
setup are the same as in July; what changed is the Claude Code version (2.1.197 → 2.1.296)
and the session around it. So the July results measured the models *under the July
harness*, and part of the 1/20 → 29/30 jump may be the harness rather than the models.
Details: [`results/RESULTS.md`](results/RESULTS.md) → "Fable 5 on the 4.0.6 control".

On the other axis — an out-of-training-corpus framework that ships in-repo agent docs,
on the current release with the current model generation:

| Framework | Opus 5.5 | Sonnet 5.5 | Haiku 5.5 |
|---|---|---|---|
| Tiko **0.6.0** — v1 fixtures | **100%** (10/10) | **100%** (10/10) | **100%** (15/15) |
| Tiko **0.6.0** — v2 fixtures | **100%** (10/10) | **100%** (10/10) | **100%** median (13/15) |

**35/35 on the original fixtures, 33/35 on the stricter v2 set** (see
[`fixtures/stage-1/README.md`](fixtures/stage-1/README.md); N=10 for Opus and Sonnet,
N=15 for Haiku). Both v2 misses are Haiku trials with the same bug: the bad-message
handler looked for an exception Tiko never attaches, so it retried for ~15 s instead of
skipping at once — a stall v1 couldn't see. Tiko 0.6.0's docs now steer agents toward
blocking on bad messages (`SEEK`), which contradicts the spec's log-and-skip; all 35
trials noticed and overrode it. Even the smallest current model builds this correctly
on a framework it was never trained on, and cheaper models lose almost nothing: median
input-side cost per build is $0.95 for Opus 5.5, $0.33 for Sonnet 5.5 and $0.05 for
Haiku 5.5.

**Both current releases on the current models:** every model now builds the service on
Spring Boot 4.1.1 and on Tiko 0.6.0. On the stricter v2 fixtures Spring went 30/30 and
Tiko 33/35, and a Spring build costs about half as much, because it takes 1.7–1.9× fewer
API calls:

| Median input-side cost per build | Opus 5.5 | Sonnet 5.5 | Haiku 5.5 |
|---|---|---|---|
| Spring Boot 4.1.1 | $0.44 · 12 calls | $0.18 · 7 calls | $0.013 · 10 calls |
| Tiko 0.6.0 | $0.95 · 23 calls | $0.33 · 12 calls | $0.047 · 17 calls |

(Average API calls per build. Input-side cost is exact; output tokens are only partly
recorded in these transcripts, see `results/RESULTS.md`.)

The previous release, Tiko 0.5.0, was also a clean sweep (15/15) on the previous model
generation — Opus 5, Sonnet 5, Fable 5. (Its Opus column was first published as
"Opus 4.8"; the trial transcripts show the `opus` alias had already moved to **Claude
Opus 5** — corrected 2026-10-11.) It improved on Tiko 0.3.0, where Opus 4.8 and Sonnet 5
each had one failure (both independently reproducing a documentation gap,
[tiko-di#404](https://github.com/tomas-samek/tiko-di/issues/404), filed from static
analysis *before* those runs). See `results/RESULTS.md` → "Tiko 0.6.0" and "Tiko 0.5.0".

### Tiko cost per build, release by release

Same spec, same oracle, Stage 1, one row per model. Each cell gives the average cost
per build, average API calls per build, and median v1 compliance. API calls don't
depend on price, and call count is what drives cost (see finding 6). Costs are
reconstructed from transcripts (`conformance/token-accounting.py`; read the erratum in
`results/pricing.md` before comparing with older published figures).

| Model | Tiko 0.2.2 | Tiko 0.3.0 | Tiko 0.5.0 | Tiko 0.6.0 |
|---|---|---|---|---|
| **Fable 5** | $5.73 · 37 calls · 100% | $4.68 · 29 · 100% | **$2.58 · 14 · 100%** | — |
| **Sonnet 5** | — | $4.14 · 77 · 100% | **$2.34 · 50 · 100%** | — |
| Opus 4.8 | $2.90 · 41 · 86% | $3.11 · 50 · 100% | — | — |
| Sonnet 4.6 | $2.42 · 93 · 86% | — | — | — |
| Opus 5 | — | — | $3.54 · 47 · 100% | — |
| Opus 5.5 | — | — | — | $1.22¹ · 23 · 100% |
| Sonnet 5.5 | — | — | — | $0.36¹ · 12 · 100% |
| Haiku 5.5 | — | — | — | $0.06¹ · 17 · 100% |

¹ Lower bound: the input side is exact, but only ~15% of 0.6.0's API calls carry a
final output-token record. N=10 per 0.6.0 model (Haiku N=15), N=5 elsewhere.

**Fable 5 is the only model measured on three Tiko releases.** With the model held
fixed, cost per build fell **55%** ($5.73 → $2.58) and API calls fell **61%**
(37 → 14) from 0.2.2 to 0.5.0. On the same model, Spring takes 14 calls on Boot
4.0.6 and 11 on Boot 3.3.5, so Tiko 0.5.0 roughly matches Spring on call count. In
dollars Tiko is still 1.4–1.8× Spring ($1.90 / $1.47), down from 3.0–3.9× on 0.2.2.
What remains is cost per call (~1.3× Spring's), not number of calls. **Sonnet 5**
shows the same direction from 0.3.0 to 0.5.0: cost −43%, calls −35%.

Not every step improved: **Opus 4.8** cost 7% more on 0.3.0 than on 0.2.2, with more
calls (41 → 50). The drop that Fable 5 and Sonnet 5 show falls between 0.3.0 and
0.5.0, the window of Tiko 0.4.0's agent-documentation overhaul. That is a timing
match, not a measured cause. Compliance went from 86% median (Opus 4.8, Sonnet 4.6)
on 0.2.2 to 100% on every model since.

**What this can't show:** Tiko 0.6.0 was run only on the 5.5 generation, at new
prices (Opus 5.5 bills cache reads at 0.05× input). Its builds are the cheapest of
any Tiko cell and its call counts the lowest, but no model was run on both 0.5.0 and
0.6.0, so that step can't be split between Tiko, the newer models and the pricing.
The Tiko 0.3.0 and Spring token and cost tables, by model, are in `results/RESULTS.md`
→ "Erratum (2026-10-11)".

### What it found

1. **Version recency dominated until the training data caught up — neither size nor
   tokens fixed it, a newer model generation did.** Across four
   model generations and 20 independent trials, Spring Boot 4.0.6 produced **one**
   working app, while the same models scored ~100% on Boot 3.3.5. The failure is
   *silent and idiomatic*: every Boot-4 build compiled, but the models reflexively wrote
   the **Boot-3 Kafka idiom** (bare `spring-kafka` + trust auto-configuration) that
   Boot 4 reorganized away — so apps either failed to start (no `KafkaTemplate` bean) or
   booted and exited consuming nothing. Recency bites at *version choice* (given
   freedom, the models downgrade — Sonnet 5/5) **and** at *execution* (forced onto the
   new major, they use the old major's muscle memory). The newest model (Fable 5) is the
   only one that ever produced the fix (`spring-boot-starter-kafka`) — **once in five
   tries**. The next generation closed the gap: on Boot 4.1.1 (same Kafka module split),
   Opus, Sonnet and Haiku 5.5 went **30/30**, every trial using that starter. The
   failures were measured 7–9 months after Boot 4.0.0 shipped (2025-11-20); the 5.5
   run, about 11 months after, so the blind spot outlasted at least one model
   generation and closed by the next.

2. **Token spend and correctness are uncorrelated here.** Sonnet 5 spent 1.3×–1.8× the
   real output tokens of Opus 4.8 on identical tasks for the same compliance (deeper
   bytecode-level exploration that didn't change outcomes), while Fable 5 got the best
   compliance results of any model at the highest dollar cost per build. What separated
   models on compliance was *what they knew*, not *how hard they worked*.

3. **An out-of-training-corpus framework with in-repo docs holds up well — and keeps
   improving.** Tiko 0.3.0 reached 100% median for both Opus 4.8 and Sonnet 5, with the
   sole failures being already-filed documentation gaps (kebab-case vs. camelCase config
   keys), not framework defects. On the most recent benchmarked release, **Tiko 0.5.0, all three models
   go 15/15 (100%)** — that doc gap doesn't recur anywhere in the newer run. The 0.5.0
   docs are also a cost win for the models measured on both versions (Sonnet 5 −43%,
   Fable 5 −45% per build). The Opus trials on 0.5.0 self-authored an integration test
   5/5 times and cost more — but they ran on Opus 5, not the Opus 4.8 measured on
   0.3.0, so that change can't be pinned on the docs — see `results/RESULTS.md` →
   "Erratum (2026-10-11)".

4. **The MCP topology server showed no compliance lift** in the original Tiko 0.2.2
   run — `tiko` and `tiko-mcp` were identical (`0 0 86 86 100`). The validation gate
   targeted *wiring*, which the model already got right; it did not target *config*,
   where Tiko's failures actually concentrated.

5. **Tiko's dominant failure mode is strict config validation** — kebab-case /
   Spring-style keys that Tiko's `@Configuration` records reject. Filed and tracked as
   [tiko-di#404](https://github.com/tomas-samek/tiko-di/issues/404), among
   [8 doc-friction issues](https://github.com/tomas-samek/tiko-di/issues/399) this
   benchmark surfaced and filed directly against Tiko.

6. **Tiko's setup-cost gap is a call-count problem, and it has been shrinking.**
   Through 0.3.0, Tiko cost more than Spring on every model, and only ~13% of that
   gap was output tokens (Tiko has no first-party DB/HTTP module, so the agent
   hand-writes more code). What dominated was **~2× more turns (API calls) per build**, which
   compounds into ~4× more re-read conversation context (~86% of the gap with cache
   writes). The agent needed more *rounds* to discover Tiko's API surface and satisfy
   its strict compile-time DI validation, not more *lines*. That is the axis later
   releases moved: on Fable 5, Tiko 0.5.0 needs about as many calls as Spring Boot 4.0.6
   (14 vs 14) and costs 1.4× instead of 3.0× (see "Tiko cost per build, release by
   release" above). Full component breakdown: `results/RESULTS.md` → "Cross-cell cost
   analysis".

> These are directional results: small N and a few points in time per cell. See
> `results/RESULTS.md` → "Three-model extension" for the full breakdown, the grading
> erratum (a harness bug that produced false negatives, now corrected), and
> "Caveats / validity".

## Headline results — Stage 2

Stage 1 is one-shot: build, grade, done — the agent never runs its own app. Stage 2
keeps the *identical* task but lets the agent iterate against a CI-gate (pass/fail per
scenario, no expected values — mirrors a black-box integration-test suite) until it
believes it complies. Only the Boot 4.0.6 cell is informative (everything else already
complies one-shot). Four batches of N=3 on three models. Full write-up, methodology, and
the [token-accounting errata](results/pricing.md) worth reading:
[`results/RESULTS.md`](results/RESULTS.md#stage-2--loop-until-complies-2026-07-0203-cost-corrected-2026-07-03).

| Model | Converged | Iterations to pass | Cost to compliance ($, real — input + cache write/read + output) |
|---|---|---|---|
| Fable 5 | **3/3** | 2, 2, **1** | avg **$1.84** |
| Opus 4.8 | **3/3** | 2, 2, 2 | avg **$1.23** — cheapest |
| Sonnet 5 (batch B) | **3/3** | 2, **1**, 2 | avg **$1.25** |
| Sonnet 5 (batch A) | **3/3** | 3, 2, 3 | avg **$2.89** — most expensive, by ~2.3× |

(Corrected 2026-10-11: costs re-derived from final usage records, and batch A —
originally published as "Sonnet 4.6" — ran on **Claude Sonnet 5** per its transcripts,
so Stage 2 covers three models, with Sonnet 5 sampled twice.)

**The version-recency wall is fully iteration-proof: 12/12 trials converge to 100%
compliance**, across every model tried, in 1–3 gate runs — the same cell that scored
0/5 one-shot for Opus 4.8 and Sonnet 5. The one-shot 0% headline above is a
*feedback* problem, not a knowledge wall: an agent that never runs its own app ships a
silently-broken Boot-4 service every time; an agent that runs-and-checks recovers every
time, cheaply. Cost tracks **iteration count** — the single most expensive trial
(Sonnet 5 batch A, 3 iterations, $5.22) costs ~2.4× any trial outside its batch,
because every extra gate iteration means re-reading a larger accumulated conversation,
and that cache-read volume dominates real cost. The two Sonnet 5 batches — same model,
same task — differ 2.3× in average cost purely through iteration count.

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
- [x] Stage 2 — loop-until-complies harness on the Boot 4.0.6 cell, four batches of N=3
      on three models (Opus 4.8, Fable 5, and Sonnet 5 twice — one batch was first
      published as Sonnet 4.6): **12/12 converged to 100% compliance** in 1–3 gate
      iterations — the one-shot 0% is a feedback problem, not a knowledge wall. Real
      (transcript-reconstructed, corrected 2026-10-11) cost: Opus 4.8 cheapest (avg
      $1.23), Sonnet 5 batch A most expensive by ~2.3× (avg $2.89, driven by
      3-iteration trials). See `results/RESULTS.md` → "Stage 2 — loop until complies"
      and the token-accounting errata in `results/pricing.md`.
- [x] Current releases on the current models (2026-10-11/12): Tiko 0.6.0 and Spring
      Boot 4.1.1 on Opus, Sonnet and Haiku 5.5, graded on v1 and v2 fixtures. Spring
      30/30 on both; Tiko 35/35 on v1, 33/35 on v2; Spring about half the cost per build.
- [x] Spring Boot 4.0.6 control on the 5.5 models (2026-10-13): 29/30 on v1 and v2, every
      build added `spring-boot-starter-kafka`, so the 4.0.6 → 4.1.1 change isn't the
      difference. Cost and calls match 4.1.1.
- [x] Fable 5 on the 4.0.6 control (2026-10-13): 9/10 headless and 8/10 as a subagent,
      against July's 1/5 with the same prompt, so the subagent form isn't the cause.
      Cost $2.14 and $2.24 per build (exact).
- [ ] Fable 5 on Claude Code 2.1.197, to test whether the Claude Code version alone
      explains July's 1/5
- [ ] Run with non-Claude agents
- [ ] Capture efficiency + code-quality rubric; n=10
- [ ] Stage 2 — extend loop-until-complies to other cells (spring3, tiko) and add a
      held-out acceptance set to detect gate overfitting
- [ ] Stage 3 spec — brownfield feature-add (Lucene full-text search + HTTP query)

## License

MIT — see [`LICENSE`](LICENSE).
