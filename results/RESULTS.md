# Stage-1 results

## Multi-model comparison (added 2026-06-13)

Same harness, spec, scaffolds, and external oracle; N=5 per cell (spring3 is a
single Boot-3.3.5 reference). Contestants run as isolated subagents on the named
model. **Median compliance** (oracle scenarios passed / 7), with the raw 5-trial
vector:

| Cell | Sonnet 4.6 | Fable 5 | Opus 4.8 |
|---|---|---|---|
| spring (Spring Boot **4.0.6**) | **0%** `0 0 0 0 100` | **100%** `100×5` | **100%** `0 0 100 100 100` |
| spring3 (Spring Boot 3.3.5) | 100% | 100% | 100% |
| tiko (0.2.2) | 86% `0 86 86 100 0` | 100% `100×5` | 86% `86 86 86 86 100` |
| tiko-mcp (0.2.2) | 86% `0 0 86 86 100` | 100% `0 100 100 100 100` | 86% `0 0 86 86 100` |

**Reading it:**
- **Version-recency is model-generation-dependent.** Spring Boot 4.0.6 — a brand-new
  major — was a near-total wall for **Sonnet 4.6** (median 0%, only 1/5 working) but
  **Fable 5 and Opus 4.8 both clear it** (median 100%). The blind spot is real but
  closes as newer models absorb the new major. (spring3 = 3.3.5, a version all three
  know, is 100% across the board — the control.)
- **Fable 5 is the strongest here**, 100% median in every cell. Opus 4.8 matches it on
  Spring but trails on Tiko (median 86 vs 100); Sonnet trails on Spring-4.
- **Tiko (out-of-corpus framework) holds up well across models** (86–100% median),
  carried by its in-repo guidance — a brand-new framework is *not* dramatically harder
  than a brand-new framework *version*.
- **MCP added no median lift** in any model (tiko == tiko-mcp medians), consistent with
  the earlier finding that the validation gate targeted wiring, which models already got
  right; the residual tiko-mcp 0%s are single-trial failures (see caveats).

**Caveats:** N=5 (directional, not significant); the tiko-mcp 0% outliers
(Fable f5-04; Opus o8-04/o8-05) were **re-graded and reproduced 0%** — they are
real wiring/runtime faults in those specific apps (built green, but emit nothing
or wrong at runtime), not harness artifacts or model traits. An earlier Opus grading pass was discarded as invalid: stray contestant
JVMs from build smoke-runs had contaminated the shared topics (the tell was a
bogus `spring3 = 29%`); the grader now kills stray JVMs per trial
(`BENCH_KILL_STRAY_JAVA=1`) and the clean re-grade restored `spring3 = 100%`.

### Efficiency — build cost (output tokens per contestant build)

Extracted from the run transcripts (`conformance/extract-telemetry.js` →
`results/efficiency.csv`; also backfilled into `metrics.csv`). Averages exclude
failed/overloaded dispatches (0-token rows). **Tokens are the reliable metric;
`duration_ms` is NOT** (some dispatches spanned a session pause, inflating wall-time).

Average output tokens per build (k):

| Model | spring | spring3 | tiko | tiko-mcp |
|---|---|---|---|---|
| Sonnet 4.6 | 36k | 34k | 110k | 102k |
| Fable 5 | 67k | 46k | 118k | 81k |
| Opus 4.8 | 71k | 51k | 113k | 124k |

**Reading it:**
- **Out-of-corpus tax is large.** Building on Tiko costs **~1.6–3× the tokens of
  Spring** for every model (Sonnet 110k vs 36k ≈ 3×; Opus 113k vs 71k ≈ 1.6×;
  Fable 118k vs 67k ≈ 1.8×) — the agent must reverse-engineer the `@KafkaSource`/
  `@KafkaSink` API from jars, dodge the poison-message seek-back trap, and hand-build
  JDBC, versus Spring's familiar starters. Tiko reaches *similar compliance* but at a
  real effort premium.
- **Cheap ≠ good — compare only at equal compliance.** Sonnet's low Spring cost (36k)
  is *cheap failure*: it gave up fast on Boot 4.0.6 (median 0%). Fable/Opus spent ~2×
  (67–71k) and actually succeeded (100%). Low tokens with low compliance is the worst
  cell, not the best.
- **Fable is also the most *efficient* where it's hardest.** On tiko-mcp it used the
  fewest tool-calls per build (~33 avg, vs Opus ~68, Sonnet ~101) while scoring
  highest — Sonnet flailed most (most iterations, lowest compliance).

The section below is the original Sonnet 4.6 deep-dive (now one column above).

---

# Stage-1 results — Sonnet 4.6 deep-dive (5× per category)

**Date:** 2026-06-08
**Model (CORRECTED 2026-06-10):** contestant agents ran on **Claude Sonnet 4.6**
(`model: sonnet` subagent override), orchestrated by Claude Opus 4.8. An earlier
revision of this file mis-attributed the contestants to Opus 4.8; `results/metrics.csv`
now carries an explicit `model` column per trial.
**Setup:** framework-neutral Stage-1 spec; external conformance oracle (7 expectations,
AC1–AC6); pinned substrate (apache/kafka:3.8.0, H2, JDK 21); Spring Boot 4.0.6,
Tiko 0.2.2. **Fixed fixtures** (generous settles) and **fresh broker per trial**
(`docker compose down -v`). N=5 per category, fresh contestant session each,
isolated (spec + scaffold only). Single model, single point in time.

This supersedes the pilot in `PILOT-NOTES.md` (see "Correction" below).

## Scores (compliance = oracle scenarios passed / 7)

| Trial | spring | tiko (no mcp) | tiko-mcp |
|---|---|---|---|
| 01 | 0% | 86% | 86% |
| 02 | 0% | 0% | 86% |
| 03 | 0% | 100% | 0% |
| 04 | 0% | 86% | 100% |
| 05 | 100% | 0% | 0% |
| **median** | **0%** | **86%** | **86%** |
| mean | 20% | 54% | 54% |
| fully correct | 1/5 | 1/5 | 1/5 |

Distributions (sorted): spring `0 0 0 0 100`; tiko `0 0 86 86 100`; tiko-mcp `0 0 86 86 100`.

## Findings

### 1. Tiko beat Spring-on-Boot-4 (median 86% vs 0%)
For *this* model at *current* versions, the AI built the working service far more
reliably on Tiko than on a freshly-generated Spring Boot 4.0.6 project. The user's
"leveled ground" hypothesis held — and then some: Spring 4 is novel enough that the
model's Boot-3 habits actively misfire.

### 2. Spring's failures are Boot-4 version-knowledge friction
4/5 Spring trials scored 0% for distinct Boot-4 reasons: JVM exits with no
`spring.main.keep-alive` (01); missing `ObjectMapper` bean (02, 03); missing
`KafkaTemplate` bean (04). Only trial-05 wired Boot-4 correctly → 100%. The model
writes Boot-3-shaped code (e.g. assumes auto-configured beans) that no longer holds
in Boot 4.

### 3. The MCP topology server showed NO measurable compliance lift
tiko and tiko-mcp produced **identical distributions** (`0 0 86 86 100`). The
pilot's apparent **+57 pts** from the MCP was a **timing artifact**: the old 3s
settle starved the no-mcp app's reference upserts, faking "null names". With the
fixed fixtures the no-mcp app scores 86% on its own.
- **Caveat:** the MCP *validation gate* in these runs targeted event/DB wiring, which
  the model already got right ~unaided. It did **not** direct `get_config_schema`
  validation — and config is exactly where Tiko actually failed (see #4). So this
  measures "MCP for wiring validation = no lift here," not "MCP is worthless." A
  config-focused gate might show value. The MCP demonstrably *did* let contestants
  see/confirm wiring (e.g. trial-05 caught two errors pre-ship); it just wasn't the
  binding constraint.

### 4. Tiko's dominant failure mode: strict config validation
4 of the 10 Tiko failures were startup `ConfigValidationException`s from
config-key mismatches: kebab-case `db.pool-size` vs record field `poolSize`;
Spring-style top-level `kafka:` key; unknown `app.kafka`/`app.h2`/`app.pool`
prefixes. The model defaults to kebab-case / Spring-isms that Tiko's `@Configuration`
records reject hard at boot. **This is the most actionable AI-friendliness finding
for Tiko:** either accept kebab-case, or make the error point at the expected key
(and have the bundled guidance steer config naming / push `get_config_schema`).

### 5. AC6 / buyB price-refresh is a fair discriminator
Several otherwise-correct apps cap at 86% by emitting the stale price for buyB
(refresh `7999→8999` not applied before the purchase) — but trial-03 (tiko) and
trial-04 (tiko-mcp) and spring-05 handled it → 100%. So it reflects a real
implementation difference (cross-topic refresh handling), not a harness artifact,
once settles are generous.

## Correction vs the pilot
The pilot reported spring 0% / tiko 29% / tiko-mcp 86% and a large MCP lift. The
tiko 29% and the lift were **timing artifacts** of the 3s settle + non-`-v` broker.
The controlled 5× run is authoritative: **MCP lift ≈ 0 here; Tiko ≫ Spring(4.0.6).**

## Reference: Spring on a version the model knows (Boot 3.3.5)

To separate "framework design" from "training-data recency," one extra Spring trial
was built with the parent pinned to **Spring Boot 3.3.5** (well inside the model's
training), same spec/rules.

| Category | compliance |
|---|---|
| spring (Boot 4.0.6) | median 0% (1/5 correct) |
| **spring3 (Boot 3.3.5)** | **100% (7/7), first try, n=1** |
| tiko / tiko-mcp (0.2.2) | median 86% |

**This reframes everything.** Spring's 0% was almost entirely **Boot-4 novelty**, not a
Spring design flaw — given a known version the model nailed the spec immediately. So:
- "Tiko beats Spring" is **false** as a framework-design claim. Known-Spring = 100%.
- The fair, useful readings are:
  1. **Tiko is genuinely AI-friendly for a framework outside the training corpus:**
     ~86% median with essentially no model priors, carried by its bundled
     guidance — and its failures are concentrated in one fixable area (config strictness).
  2. **Brand-new framework *versions* are a real, separate hazard:** a major Spring
     bump alone took the same model from 100% to a median 0%. "As-shipped
     AI-friendliness" is as much about version recency + bundled docs as about API design.

## Caveats / validity
- N=5 is directional, not statistically strong; both Tiko variants have 2/5 hard
  failures + a 86–100 cluster.
- Single model, single point in time (versions as pinned).
- MCP delivered via a jbang CLI one-shot, and the MCP gate emphasized wiring not config.
- Contestants ran as isolated subagents, not full IDE sessions.
- Efficiency columns (tokens/turns/tool-calls) were not captured per trial; only
  compliance + (broker) wall-clock. Code-quality rubric scoring not yet applied.
