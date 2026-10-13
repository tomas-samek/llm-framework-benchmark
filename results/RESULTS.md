# Stage-1 results

> **Start here:** the authoritative version-recency result (four model generations —
> Sonnet 4.6, Opus 4.8, Sonnet 5, Fable 5) is in
> ["Multi-model extension" below](#-multi-model-extension--sonnet-5--fable-5-added-tiko-030-added-2026-07-0102),
> and the current-version Tiko result is in
> ["Tiko 0.6.0"](#-tiko-060--three-new-models-first-run-graded-on-both-fixture-sets-2026-10-11)
> (0.5.0 and 0.3.0 are kept just above it as prior-version comparison points). Its
> Spring counterpart on the same current models is
> ["Spring Boot 4.1.1 on the 5.5 models"](#-spring-boot-411-on-the-55-models-2026-10-12). Read the
> [2026-10-11 erratum](#erratum-2026-10-11-output-tokens-undercounted-two-model-mislabels)
> before any token or cost figure.
> Everything above the Multi-model extension section is superseded first-pass /
> confounded data, kept for the audit trail.

## Erratum (2026-10-11): output tokens undercounted; two model mislabels

Read this before any token or cost figure below. Full method: `results/pricing.md` →
"Erratum 2"; every row's old and new values: `results/token-audit.csv`. **Compliance
results are unaffected.**

1. **Output tokens were undercounted 2–33× per cell, costs 1.1–1.7×.** The transcript
   reconstruction kept each API response's first transcript line, whose `output_tokens`
   is a stream-start placeholder; the final usage is on the response's last line. Input
   and cache counts were right, so every turn-count and cache-volume statement stands.
   Corrected figures are still **lower bounds** (62–97% of calls per cell have a final
   usage record). `results/metrics.csv` and `results/stage-2-loop.csv` now carry the
   corrected values; tables marked *(superseded — see erratum)* below are kept as
   published.
2. **`tiko-050` "Opus 4.8" was Claude Opus 5**, and **Stage-2 "Sonnet 4.6" (s-01..03)
   was Claude Sonnet 5** — the trial transcripts record the model; the `opus`/`sonnet`
   aliases had moved on. The CSV model columns are corrected.

**Corrected Stage-1 headline** (spring-fix = Boot 4.0.6, spring3-fix = Boot 3.3.5,
tiko-030 = Tiko 0.3.0; average per build):

| Model | Output tokens: spring-fix | spring3-fix | tiko-030 | Cost: spring-fix | spring3-fix | tiko-030 |
|---|---|---|---|---|---|---|
| Sonnet 4.6 | 10.1k | 7.6k | — | $0.62 | $0.42 | — |
| Opus 4.8 | 13.3k | 13.3k | 22.5k | $1.11 | $0.89 | $3.11 |
| Sonnet 5 | 23.3k | 17.6k | 40.9k | $1.45 | $0.91 | $4.14 |
| Fable 5 | 16.3k | 12.3k | 27.0k | $1.90 | $1.47 | $4.68 |

**Claims that change:**
- *"Fable 5 uses fewer real output tokens than Opus 4.8 in every cell"* — false; Fable
  writes more in spring-fix and tiko-030, and is the **most expensive model in all three
  cells** (not two of three). Sonnet 5 uses 1.3–1.8× Opus 4.8's output (not 1.6–2.7×).
- *Boot 4 vs Boot 3:* models write **~19% more** output on Boot 4.0.6, not slightly less
  (Fable 1.33×, Sonnet 5 1.32×, Sonnet 4.6 1.34×, Opus 4.8 1.00× — not "Fable 3.7×,
  Sonnet 5 −41%"). Output is ~19% of the $0.28 cost gap (not ~0%); turns (1.46×) and
  cache-read (~66%) still dominate.
- *Tiko 0.3.0 → 0.5.0:* Sonnet 5 tokens −7% (not +120%), cost −43%; Fable 5 tokens −27%
  (not −73%), cost −45%. The Opus row compares **Opus 4.8 on 0.3.0 with Opus 5 on 0.5.0**
  (22.5k → 39.8k tokens, $3.11 → $3.54), so its "self-testing habit taught by the new
  docs" explanation is confounded by a model change and can't be attributed to the docs.
- *Cross-cell (Tiko vs Spring):* the headline holds for the corpus up to Tiko 0.3.0 —
  for every model, the cheapest Tiko-family cell average still exceeds the priciest
  Spring-family one (0.5.0 later breaks it for Fable 5; see "Tiko cost across releases") — but output is
  ~13% of the $2.51 gap (not ~2%), output grows 1.8× pooled (Opus 4.8 +46%, Sonnet 5
  +100% — not +6% / +9%); cache read ~60% and cache write ~26% remain the bulk.
- *Stage 2:* three models, not four (Sonnet 5 sampled twice). Opus 4.8 is still cheapest
  (avg $1.23); Sonnet 5 batch A (3, 2, 3 iterations) is most expensive (avg $2.89; its
  s-03 at $5.22 is the costliest trial), batch B (2, 1, 2) averages $1.25; Fable 5
  averages $1.84.

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
- **Version-recency on Spring Boot 4.0.6.** In *this* (confounded, core-only) table
  Sonnet 4.6 was a near-total wall (median 0%) while Fable 5 / Opus 4.8 looked like they
  cleared it (median 100%). **⚠️ SUPERSEDED:** the clean fixed-scaffold re-run (see
  "Clean re-run" below) shows the Opus 100% here was driven by the few trials that
  *hand-wired* Kafka; with the scaffold confound removed, **both Opus 4.8 and Sonnet 4.6
  score 0/5 on Boot 4.0.6** and 100% on 3.3.5. So the blind spot does **not** simply
  "close with newer models" — recency bites Opus too. Treat this row as the confounded
  first pass; the fixed-scaffold section is authoritative for the Spring version question.
  (spring3 = 3.3.5 = 100% across the board remains the control.)
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

### ⚠️ Spring scaffold confound (discovered 2026-06-13 — read before trusting Spring compliance)

The Spring golden scaffold is **core-only** (generated with no starters, to mirror
Tiko's minimal start). The base `spring-boot-starter` ships **no auto-wired
`ObjectMapper` bean**. Apps that inject `ObjectMapper` assuming it's present (the norm
in a `-web` app) **fail at startup** — on Boot 3.x and 4.x alike. Diagnosed from the
`spring-free` arm: **7 of 10 apps failed to start** (mostly
`No qualifying bean of type ...ObjectMapper`); of the 3 that *did* start, only 2 passed.
The same failure appears in the original forced-Spring runs (Sonnet's forced-4.0.6
"missing ObjectMapper bean" trials).

**Consequence:** the Spring compliance numbers (all cells) **conflate two variables** —
"can the model handle the Boot version" *and* "did the model wire Jackson into a
deliberately-minimal scaffold." **RESOLVED 2026-06-13** by the fixed-scaffold re-run
below (`spring-fix` / `spring3-fix`, both with `spring-boot-starter-json`): with the
Jackson gap removed, the version-recency effect is **confirmed and sharpened** — see
"Clean re-run" below.

**Unaffected:** the version-*choice* result (the "gravity well": Sonnet 5/5 downgraded
off 4.0.6, Opus 4/5 kept it — `results/spring-free-versions.csv`) is about which version
the model *picks*, independent of the Jackson wiring, and stands.

**Why `spring-boot-starter-json` and not `spring-boot-starter-web` (a deliberate choice).**
There is no umbrella "microservice" starter in Spring Boot — starters are composable
building blocks. The habitual reach is `-web`, but that is specifically the HTTP/servlet
stack (Spring MVC + embedded Tomcat); this service is a **headless Kafka consumer with no
HTTP endpoints**, so `-web` would bolt an idle Tomcat onto an app that never serves a
request. `spring-boot-starter-json` is the minimal, dedicated JSON building block (Jackson
+ auto-configured `ObjectMapper`) that `-web` itself depends on. It does **not** alter the
version-recency probe — both starters resolve the *same* Jackson per Boot version (Jackson
3 on 4.0.6, Jackson 2 on 3.3.5) — and keep-alive is handled regardless by `spring-kafka`'s
non-daemon listener thread, so no web server is needed to keep the process up. `-json` is
therefore the honest minimal stack for a Kafka service and gives the cleanest isolation.

### ✅ Clean re-run — version-recency isolated (fixed scaffold, 2026-06-13)

With the Jackson confound removed (both scaffolds ship `spring-boot-starter-json` →
an auto-configured `ObjectMapper`), the **only** difference between these two cells is
the Spring Boot version. Same spec, same fixed scaffold, contestants instructed to keep
the pinned Boot version. Compliance (oracle scenarios passed / 7):

| Cell | Boot / Jackson | Sonnet 4.6 | Opus 4.8 |
|---|---|---|---|
| `spring3-fix` (control, known version) | 3.3.5 / Jackson 2 | **100 / 100 / 100** | **100 / 100 / 100** |
| `spring-fix` (current major)           | 4.0.6 / Jackson 3 | **0 / 0 / 0 / 0 / 0** | **0 / 0 / 0 / 0 / 0** |

**Every one of the 16 trials built green** (BUILD SUCCESS) and **every Boot-4 build
correctly handled the Jackson 2→3 move** (`tools.jackson` / Spring Kafka's
`JacksonJsonSerializer`) — so this is *not* the old scaffold gap. The failure simply
**relocated from build-time to runtime**, with one root cause.

**Root cause — Spring Boot 4.0's Kafka auto-configuration reorganization.** Boot 4 split
Kafka support into `spring-boot-starter-kafka` with relocated autoconfig. Both models,
trained predominantly on Boot 3, default to the **Boot-3 idiom**: depend on bare
`spring-kafka` and trust autoconfig for `KafkaTemplate` and `@KafkaListener` containers.
In Boot 4 that idiom silently no longer wires. Two failure modes, same cause (verified
from `app.log` + source of all 10 Boot-4 trials):

- **6/10 fail-to-start** — relied on autoconfig for the producer →
  `No qualifying bean of type KafkaTemplate<String,String>` → `APPLICATION FAILED TO START`.
- **4/10 boot-and-exit (silent)** — hand-wired the *producer* (so the context starts)
  but left the *consumer* side to autoconfig → listener containers never start → the JVM
  has no non-daemon thread, exits ~20 ms after "Started", consumes/emits nothing
  (oracle: "no notification with this key" ×7).
- **0/10 used the new `spring-boot-starter-kafka`** — the actual Boot-4 fix.

The `spring3-fix` control proves it is the version, not the harness or the spec: the
*same* Boot-3 autoconfig idiom wires fully on 3.3.5 (`Subscribed to topic(s)…`,
`groupId=notify`) → 100% in the same grader batches.

**Reconciliation with the (confounded) original run.** The original "Opus clears Boot 4"
successes were not autoconfig wins — they were the trials that **fully hand-wired** Kafka
or used the new starter: `o8-03` used `spring-boot-starter-kafka`; `o8-04`/`o8-05`
hand-wired producer + consumer factories + `KafkaTemplate`. The original 0% trials
(`o8-01`/`o8-02`) and *all 10* clean-run trials leaned on the broken autoconfig idiom.
So "success on Boot 4" = "the model distrusted its autoconfig priors and hand-wired (or
found the new starter)," which both models do **inconsistently**.

**Honest caveat on the number.** Boot-4 compliance is **high-variance**, because it hinges
on that hand-wire-vs-autoconfig choice. Pooling both runs (core-only original + fixed
re-run): Boot 4.0.6 ≈ **Opus 3/10, Sonnet 1/10**; Boot 3.3.5 = **100%** throughout. N=5
per cell cannot pin a precise Boot-4 percentage — but the **qualitative** result is robust
and reproduced across 26 Spring trials and two scaffolds: *the known version works every
time; the 6-month-old major frequently breaks both frontier models, via stale framework
idioms rather than syntax they can't write.*

**Bottom line.** This sharpens the gravity-well story. It is not only that models *pick*
the version they know (`spring-free-versions.csv`: Sonnet downgrades 5/5, Opus keeps 4.0.6
4/5) — it is that **even when forced onto the current major, both models execute it with
the previous major's muscle memory.** Recency bites at choice *and* at execution.

### 🔺 Multi-model extension — Sonnet 5 + Fable 5 added, Tiko 0.3.0 added (2026-07-01/02)

**Four model generations on the identical fixed scaffolds.** Same spec, N=5, forced
version:

| Cell | Boot / Jackson | Sonnet 4.6 | Opus 4.8 | Sonnet 5 | Fable 5 |
|---|---|---|---|---|---|
| `spring3-fix` (control) | 3.3.5 / Jackson 2 | 100/100/100 (n=3) | 100/100/100 (n=3) | **100 100 100 0 100** | **100 ×5** |
| `spring-fix` (current major) | 4.0.6 / Jackson 3 | 0/0/0/0/0 | 0/0/0/0/0 | **0 0 0 0 0** | **0 0 0 0 100** |

**20 independent `spring-fix` trials across four model generations: exactly one pass —
and it is the one trial that found the fix.** Sonnet 5 reproduced the unanimous failure
(0/5): root cause re-verified as **still the missing `KafkaTemplate` bean**, unchanged
from Sonnet 4.6 / Opus 4.8, *even in trials that correctly adopted Jackson 3 and Spring
Kafka's new `JacksonJsonSerializer`*. Sonnet 5 tried three distinct JSON strategies
(adopt Jackson 3 directly; adopt the new `JacksonJsonSerializer`/`JacksonJsonDeserializer`;
resist Jackson 3 by pinning classic Jackson 2 alongside it) — all three hit the same
Kafka-autoconfig wall. Getting the JSON layer right does not help.

**Fable 5 is the first model to show genuine Boot-4 knowledge — inconsistently.** Its
five Boot-4 trials split three ways:
- `f5-05` (**the sole pass, 100%**): added **`spring-boot-starter-kafka`**, explicitly
  reasoning that "in Boot 4 the Kafka auto-configuration lives in the `spring-boot-kafka`
  module, not `spring-boot-autoconfigure`." The only trial out of 20 to reach for the
  actual fix — and the only one to pass.
- `f5-01` (0%): diagnosed the `KafkaTemplate` generic-mismatch and worked around it with
  `KafkaTemplate<Object, Object>` — necessary but not sufficient (the consumer side still
  never wired).
- `f5-02/03/04` (0%): the standard Boot-3 idiom (bare `spring-kafka` + autoconfig), same
  failure as every other model.

So the blind spot is **not** binary "closes with newer models" — the newest-generation
model *carries* the Boot-4 knowledge but retrieves it **1 time in 5**. The gravity well
weakens with recency; it does not disappear.

**Tiko 0.3.0 (Opus 4.8 + Sonnet 5 + Fable 5, N=5 each), clean re-run:**

| Trial | Opus 4.8 | Sonnet 5 | Fable 5 |
|---|---|---|---|
| 01–05 | 100 100 100 **0** 100 | 100 **0** 100 100 100 | **100 ×5** |
| median | **100%** | **100%** | **100%** |

All three land at 100% median; **Fable 5 is the only model to sweep the cell 5/5.** The
Opus failure (`o-04`) is a `ConfigValidationException` from kebab-case keys (`app.h2-url`)
not matching the camelCase binding (`app.h2Url`) — **independently reproducing issue
[#404](https://github.com/tomas-samek/tiko-di/issues/404)**, filed from static analysis
before this run and now confirmed by live failure. Separately, **five of five Fable
trials hit the `@PostConstruct`-must-be-`public` compile error live**
([#400](https://github.com/tomas-samek/tiko-di/issues/400)) and self-corrected — five
more independent reproductions of that doc bug. Fable's discovery path also differed:
it read the published `-sources.jar`s instead of `javap`-ing bytecode, a cheaper route
to the same undocumented contract.

**Grading erratum (2026-07-01):** an earlier attempt to grade `tiko-030` produced
`0/0/0` for three trials whose apps had, in fact, started and were consuming normally.
Cause: the grading invocation was piped through an intermediate `grep` filter that
interfered with the oracle JVM's own stdout/exit, so the oracle's result JSON was never
written and compliance silently defaulted to 0. This was a **harness/methodology bug**,
not a Tiko fault — re-running the identical trials with a clean (unfiltered) output
redirect produced the 100/100/100/0/100 result above, confirmed against a byte-identical
manual reproduction of trial `o-01` (7/7, 100%). Lesson for this project: never pipe a
grading invocation through a filtering command; redirect to a file and grep the file
afterward.

**Token cost (corrected 2026-07-03 — see erratum).** An earlier version of this
section used the Agent-tool dispatch's `subagent_tokens` figure as "average output
tokens." Per the erratum in `results/pricing.md` (originally discovered while
re-verifying Stage 2), `subagent_tokens` is not a token-generation count — it's
approximately the *final API call's total context size*, dominated by cheap
cache-read tokens, not tokens the model wrote. All 51 Stage-1 trials behind this table
were re-verified by reconstructing real per-call usage from Claude Code's persisted
subagent transcripts (`conformance/token-accounting.py`); one wrinkle surfaced along
the way: **6 of the Fable-5 trials each had two candidate transcripts** — an earlier
attempt that hit a session token limit mid-build (`stop_reason: stop_sequence`, "You've
hit your session limit") and a later, completed re-dispatch (`stop_reason: end_turn`).
The corrected numbers below use the completed transcript in every case.

Real average output tokens per build:

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| Model | spring-fix (Boot 4.0.6) | spring3-fix (Boot 3.3.5) | tiko-030 (Tiko 0.3.0) |
|---|---|---|---|
| Sonnet 4.6 | 3.4k | 3.0k | — |
| Opus 4.8 | 2.6k | 2.8k | 4.2k |
| Sonnet 5 | **4.5k** | **7.7k** | **6.7k** |
| Fable 5 | 2.5k | **0.7k** | 2.2k |

Real average cost per build (input + cache-write + cache-read + output, each priced
correctly — see `results/pricing.md`):

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| Model | spring-fix | spring3-fix | tiko-030 |
|---|---|---|---|
| Sonnet 4.6 | **$0.52** | **$0.35** | — |
| Opus 4.8 | $0.85 | $0.63 | $2.65 |
| Sonnet 5 | $1.17 | $0.76 | $3.62 |
| Fable 5 | $1.21 | $0.88 | **$3.44** |

Sonnet 5 does use meaningfully more tokens than Opus 4.8 on identical tasks (1.6×–2.7×
depending on cell) while landing at the same or statistically indistinguishable
compliance — that qualitative finding survives the correction, even though the exact
multiplier and absolute magnitudes do not. The dollar story is now cleaner than before:
**Sonnet 4.6 is the cheapest model in every cell it has data for**, and **Fable 5 —
despite using *fewer* real output tokens than Opus in every single cell (0.7k–2.5k vs
2.6k–4.2k) — is still the most expensive per build in two of three cells**, because its
$50/1M output and $10/1M input rates outweigh its lower token count. The relationship
between model generation and dollar cost is not monotonic, and tokens and dollars rank
differently — but the core qualitative points survive the correction: **you cannot buy
your way out of the version-recency blind spot with tokens** (Sonnet 5 spent more than
Opus on Boot 4 and still went 0/5), and **capability + current knowledge beat
brute-force exploration** (Fable got the best compliance results while writing less
code than Opus, even if not at the lowest dollar cost).

**Why Boot 4.0.6 costs more: the same iteration-count mechanism as the Tiko-vs-Spring
gap (see "Cross-cell cost analysis" below), not more code.** Breaking real usage down by
component, pooled across all four models, `spring-fix` (Boot 4.0.6) vs `spring3-fix`
(Boot 3.3.5):

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| | spring3-fix (Boot 3.3.5) | spring-fix (Boot 4.0.6) | Ratio | Share of the $0.24 cost gap |
|---|---|---|---|---|
| API calls (turns) | 20.6 | 29.9 | **1.45×** | — |
| Output tokens (code written) | 3,710 | 3,276 | 0.88× (fewer) | ~0% |
| Cache-write tokens | 33,874 | 43,744 | 1.29× | ~18% |
| Cache-read tokens | 837,528 | 1,344,357 | **1.61×** | **~79%** |
| **Total cost** | **$0.697** | **$0.935** | — | 100% |

Models write about the same amount of code on Boot 4.0.6 as on Boot 3.3.5 — pooled,
slightly *less* — but take ~45% more turns to get there, consistent across every model
(Fable 10.8→13.8, Opus 18.0→24.8, Sonnet 4.6 26.7→35.2, Sonnet 5 28.2→46.0). That turn
increase, not extra code, is almost the entire cost gap. Output tokens themselves move
inconsistently by model: Fable writes **3.7× more** code on Boot 4 (682→2,530) — it's
the only model that sometimes finds and applies the real fix
(`spring-boot-starter-kafka`), which is genuinely more code — while Sonnet 5 writes
**41% less** (7,705→4,530). So the extra turns are spent on *deliberation* (second-
guessing the Kafka/Jackson dependency choice, checking Jackson-2-vs-3 compatibility,
trying alternate serializer classes) rather than uniformly on more code — some models
spend those extra turns and still commit to a smaller, wrong answer; the one model that
finds the real fix writes more because the fix itself requires it.

---

### 🆕 Tiko 0.5.0 — clean sweep, cost story mixed (2026-08-15)

Same protocol as the 0.3.0 run above (Opus + Sonnet 5 + Fable 5, N=5 each — the Opus trials ran on **Claude Opus 5**, not Opus 4.8; see the erratum at the top), against
`scaffolds/tiko5/notify/` — the 0.5.0 archetype scaffold (`tiko-archetype:0.5.0`, current
latest on Maven Central; see `scaffolds/tiko5/GENERATE.md` for what changed archetype-side:
the `REQUEST`/`EVENT` scope merge and the `.ai-skills/` restructuring into
`tiko-build/reference/{api-signatures,config,events,kafka}.md`).

| Trial | Opus 5 (published as "Opus 4.8") | Sonnet 5 | Fable 5 |
|---|---|---|---|
| 01–05 | 100 100 100 100 100 | 100 100 100 100 100 | 100 100 100 100 100 |
| median | **100%** (5/5) | **100%** (5/5) | **100%** (5/5) |

**15/15 — a clean sweep**, up from 13/15 (87%) on 0.3.0. Both 0.3.0 failures were
independent live reproductions of the same doc-gap issue
([tiko-di#404](https://github.com/tomas-samek/tiko-di/issues/404), kebab-case config keys
rejected by Tiko's `@Configuration` records); neither recurs anywhere in this run. That's
consistent with — though not proof of — the doc gap actually having been closed between
0.3.0 and 0.5.0.

**Real average output tokens and cost per build** (same reconstruction method as above,
`conformance/token-accounting.py` against the real subagent transcripts):

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| Model | tiko-030 tokens | tiko-050 tokens | Δ | tiko-030 cost | tiko-050 cost | Δ |
|---|---|---|---|---|---|---|
| Opus 4.8 | 4.2k | 16.3k | **+291%** | $2.65 | $2.96 | +12% |
| Sonnet 5 | 6.7k | 14.7k | +120% | $3.62 | $1.90 | **−48%** |
| Fable 5 | 2.2k | 0.6k | **−73%** | $3.44 | $1.62 | **−53%** |

Two of three models got *both* more compliant and cheaper. Opus is the outlier: pricier
despite the compliance win. The token and dollar columns don't move together, and the
reason is a real, verifiable behavioral change, not noise:

**Opus started writing and running its own integration test, every time.** Checking each
trial workspace for a self-authored test file under `src/test/`: on 0.3.0, **0 of 15**
trials across all three models wrote one. On 0.5.0: **Opus 5/5**, Sonnet 5 **1/5**, Fable
5 **0/5**. This traces to a concrete doc change — `.ai-skills/tiko-build/reference/kafka.md`
(new in 0.5.0) opens with *"Read this when: consuming/producing Kafka, or **writing the
Kafka integration test**"* and points at the `FakeBroker*IT.java` pattern in
`tiko-examples`. The restructured docs make self-testing an obvious, cheap next step —
Opus reliably takes it, Sonnet 5 sometimes does, Fable essentially never does.

That explains the split: Fable's cost fell the most (−53%) and it's the model that never
self-tests either version, so its number is close to a clean read of the leaner
`.ai-skills/` split needing less doc content pulled into context — fewer/smaller reference
files to read before a correct build, same effect on the cheap cache-read side of the
bill that dominated the Boot-4-vs-Boot-3 gap above. Sonnet 5's cost fell too (−48%)
*despite* writing more real output tokens (+120%), which only makes sense if the
input/cache-read side shrank enough to outweigh the extra output — same leaner-docs
effect, partially offset by one self-test. Opus is the one case where the offset flips:
writing and *compiling/running* its own test five times out of five costs more in
output tokens (+291%) than the leaner docs save on the input side, so it nets **12%
pricier** even though the underlying documentation genuinely improved.

**Bottom line:** the 0.5.0 doc restructuring is a real compliance and (mostly) cost win —
except it also taught at least one model a new habit (self-testing) that costs more than
the docs save, for that model. Better docs don't uniformly cut cost; they can also invite
more work.

---

### 🆕 Tiko 0.6.0 — three new models, first run graded on both fixture sets (2026-10-11)

Same protocol and dispatch prompt as 0.5.0 (version and path changed only), against
`scaffolds/tiko6/notify/` (`tiko-archetype:0.6.0`, generated from Maven Central — see
`scaffolds/tiko6/GENERATE.md`), on the current model generation: **Claude Opus 5.5,
Sonnet 5.5, Haiku 5.5** (model IDs verified from each trial's transcript), N=5 each.
Second batches for all three models (o55-06–10, s55-06–10, h55-06–10) were run on
2026-10-12 with the identical prompt and scaffold, bringing each model to N=10, plus a
third Haiku batch (h55-11–15) the same day, bringing Haiku to N=15. Builds ran in parallel with no broker; grading was sequential with a fresh broker per
trial, each trial restored from its post-build snapshot before every grading (several
apps keep H2 in a file under `./data/`, which must not carry over between gradings).
Every trial was graded on **v1** (`scenarios.json`, comparable with all earlier cells)
and **v2** (`scenarios.v2.json`, see `fixtures/stage-1/README.md`).

| Model | v1 (01–05) | v1 median | v2 (01–05) | v2 median |
|---|---|---|---|---|
| Opus 5.5 | 100 100 100 100 100 | **100%** (5/5) | 100 100 100 100 100 | **100%** (5/5) |
| Opus 5.5, batch 2 (06–10, 2026-10-12) | 100 100 100 100 100 | **100%** (5/5) | 100 100 100 100 100 | **100%** (5/5) |
| Sonnet 5.5 | 100 100 100 100 100 | **100%** (5/5) | 100 100 100 100 100 | **100%** (5/5) |
| Sonnet 5.5, batch 2 (06–10, 2026-10-12) | 100 100 100 100 100 | **100%** (5/5) | 100 100 100 100 100 | **100%** (5/5) |
| Haiku 5.5 | 100 100 100 100 100 | **100%** (5/5) | **86** 100 100 100 100 | **100%** (4/5) |
| Haiku 5.5, batch 2 (06–10, 2026-10-12) | 100 100 100 100 100 | **100%** (5/5) | 100 100 100 100 100 | **100%** (5/5) |
| Haiku 5.5, batch 3 (11–15, 2026-10-12) | 100 100 100 100 100 | **100%** (5/5) | 100 **86** 100 100 100 | **100%** (4/5) |

**v1: 35/35. v2: 33/35 — and both differences are exactly what v2 was built to see.**
(Before the later batches: 15/15 and 14/15.) Both v2 failures are Haiku trials with the
same bug, h55-01 and h55-12 (described below for h55-01; h55-12's decider is the same
check, and its v2 log shows the bad record given up on after 5 attempts).
Haiku h55-01 registered a `KafkaIngestErrorDecider` meant to SKIP undecodable records
by looking for an `IOException` in the cause chain; Tiko 0.6.0 raises an
`IllegalStateException` there, so the check missed and the decider SEEKed 5 times with
the default doubling backoff (~15 s) before skipping. On v2 that stall held back AC5's
fresh price (`priceCents` null for buy5, 6/7). On v1 the same app scored 7/7: AC5's price
was already stored, and the stall was over by the time AC6 republished prices — the
exact masking v2 removes.

**Poison handling — every trial overrode the default.** 0.6.0's docs (newly prominent)
explain that `poison-record-policy` defaults to `SEEK` — a bad record blocks its
partition — and advise against `SKIP`; the spec requires log-and-skip. All 35 trials
reconciled the two: 27 registered a `KafkaIngestErrorDecider` (all 10 Opus), 6 (5
Sonnet, 1 Haiku) set `poison-record-policy: SKIP` and said so explicitly, and 2 (Haiku
h55-04, h55-14) bypassed the policy with a raw-bytes serializer plus parse-and-skip in
their handlers. None hit the SEEK-forever stall the v1-era 86% Tiko trials are suspected of (see the erratum at
the top). The deciders differ in how they recognise a record that can't be decoded —
Tiko gives them no type to check, so they match message text, wrap the serializer, or
don't distinguish at all (filed as
[tiko-di#549](https://github.com/tomas-samek/tiko-di/issues/549)). Two second-batch Sonnet
deciders (s55-08, s55-10) SEEK twice and then SKIP every failure, a ~1.5 s stall with the
default backoff; v2's settle pauses absorb a stall that short, so both scored 7/7.

Sonnet 5.5 split evenly between the two routes: 5 of 10 set `SKIP`, 5 of 10 wrote a
decider (batch 1: 2 and 3; batch 2: 3 and 2). Haiku 5.5's second batch went 4 deciders and
1 `SKIP`. Two of those deciders tried the same thing h55-01 got wrong — skip undecodable
records, retry the rest — and this time recognised the failure: h55-07 by finding a
`JsonKafkaSerializer.deserialize` frame in the stack trace, h55-08 by finding "json" in
the exception message. Their v2 app logs show AC5's bad record skipped on the first
attempt. Both checks rely on Tiko internals (a stack frame, message text), which is the
gap #549 asks to close; h55-07's would SEEK forever on any failure it didn't recognise.

Haiku 5.5's third batch went 4 deciders and 1 bypass (h55-14). h55-11 returns
`DEAD_LETTER` on the first failure, which commits past the record at once; h55-13 SEEKs
twice and then SKIPs everything. Two tried skip-undecodable-retry-the-rest and both
missed the signal: h55-12 looked for an `IOException` (h55-01's bug, now 2 of 15 Haiku
trials) and failed AC5 on v2 after 5 attempts; h55-15 looked for "jackson" in a class
name, which isn't in the chain either, but gave up after 3 attempts (~1.5 s, two
retries in its log) and passed.

Opus 5.5's second batch, like its first, wrote a decider in every trial, each recognising
undecodable records some way: three match Tiko's message text or Jackson class names
(o55-07, o55-08, o55-10), one wraps the serializer in its own and checks for its own
exception type (o55-09), and one inverts the test — SEEK only on database errors, SKIP
everything else (o55-06). Four v2 app logs show AC5's bad record skipped on the first
attempt; o55-10 also passed AC5, but its decider's log lines don't appear in the captured
output, so which branch fired isn't recorded.

**Self-testing:** Opus 5.5 wrote and ran its own integration test against
`FakeKafkaBroker` in 10/10 trials (as Opus 5 did on 0.5.0); Sonnet 5.5 in 0/10 and
Haiku 5.5 in 0/15.

**Cost.** This run's transcripts (Claude Code 2.1.296) carry a final usage record for
only ~15% of API calls, so output tokens are a loose lower bound (blank in
`metrics.csv` for the four trials with no final record at all); the input side —
input + cache write + cache read — is exact. Per build, median:

| Model | Input-side cost (exact) | Output tokens (lower bound) | Calls with final usage |
|---|---|---|---|
| Opus 5.5 (N=10) | $0.95 | ≥ 13.7k | 42/227 |
| Sonnet 5.5 (N=10) | $0.33 | ≥ 3.1k | 7/117 |
| Haiku 5.5 (N=15) | $0.05 | ≥ 10.9k | 51/248 |

Each model's batches cost about the same on the exact input side (median: Opus $0.98
and $0.93, Sonnet $0.31 and $0.34, Haiku $0.04, $0.05 and $0.05). Sonnet's output bound dropped from ≥ 5.4k
(batch 1 alone) only because two more trials have no final usage record; it says nothing
about how much Sonnet actually wrote.

Opus 5.5's cache reads are priced at 0.05× input; Haiku 5.5 bills calls with prompts
over 100K tokens at its higher rate (11 of 15 Haiku trials crossed it: 3, 4 and 4 by
batch) — both handled in `conformance/token-accounting.py`.

**Run notes.** (1) Haiku h55-03's first v1 grading was invalid (0%): a contestant shell
stuck on `python -` reading an empty here-doc kept a handle on the trial directory, the
restore's delete failed partway, and the snapshot was copied into a nested directory, so
the app had no `pom.xml`. The shell tree was killed (the shell would otherwise have gone
on to overwrite the trial's `pom.xml`), and h55-03 was re-graded on v1 from its snapshot:
100%; its v2 grading, after a clean restore, was also 100%. (2) Haiku h55-01 checked H2
`MERGE` syntax in a scratch directory under the system temp folder (deleted afterwards) —
outside its trial directory, against the "work only here" rule; it read only Maven-cache
jars, no other project, fixture or result, so the trial is kept. (3) Haiku h55-09 left a
shell stuck on `cat >` with a queued script that would have rewritten its `pom.xml` after
the build; the shell tree was killed before grading and the `pom.xml` hash checked
unchanged. (4) Five more trials wrote scratch files outside their trial directory: o55-01,
o55-05 and h55-07 wrote their Maven classpath (`cp.txt`) into the orchestrating session's
scratchpad, o55-05 also unpacked jar metadata there, and h55-09/h55-10 used the parent
temp folder. All 25 transcripts were searched: no trial read any other trial, fixture,
result, grading script or the reference app, so all are kept. The scratchpad path reaches
contestants through the subagent environment, though, and it held grading material;
future runs should keep that material out of it. (5) Before the second Opus batch the
reference app, trial snapshots and grading logs were moved out of the scratchpad. In that
batch o55-07, o55-08 and o55-09 ran `javap` from `/tmp` (o55-08 and o55-09 also wrote
their classpath there), and o55-10 did the same in the scratchpad (classpath, two config files extracted from the
tiko-kafka jar, a one-file probe of `JsonKafkaSerializer`); none listed or read anything
else, so all are kept. (6) In the third Haiku batch, h55-11 and h55-12 wrote their
classpath into the scratchpad (by then holding no grading material) and h55-12 and
h55-14 ran `javap` from `/tmp`; none read anything else. No contestant processes were
left running.

### Tiko cost across releases (2026-10-12)

No new trials: this regroups the existing Stage-1 Tiko cells by release, using
`results/metrics.csv` (cost, compliance) and `results/token-audit.csv` (API calls per
build; for 0.6.0 the totals in the table above, N=10, Haiku N=15). Average per build;
compliance is the v1 median.

| Model | 0.2.2 (`tiko`) | 0.3.0 (`tiko-030`) | 0.5.0 (`tiko-050`) | 0.6.0 (`tiko-060`) |
|---|---|---|---|---|
| Fable 5 | $5.73 · 36.8 calls · 100% | $4.68 · 29.2 · 100% | $2.58 · 14.4 · 100% | — |
| Sonnet 5 | — | $4.14 · 76.6 · 100% | $2.34 · 49.6 · 100% | — |
| Opus 4.8 | $2.90 · 41.4 · 86% | $3.11 · 50.2 · 100% | — | — |
| Sonnet 4.6 | $2.42 · 93.0 · 86% | — | — | — |
| Opus 5 | — | — | $3.54 · 47.0 · 100% | — |
| Opus 5.5 | — | — | — | $1.22¹ · 22.7 · 100% |
| Sonnet 5.5 | — | — | — | $0.36¹ · 11.7 · 100% |
| Haiku 5.5 | — | — | — | $0.06¹ · 16.5 · 100% |

¹ Lower bound (output tokens only partly recorded; input side exact).

Spring reference on the same models (`spring-fix` = Boot 4.0.6, `spring3-fix` = Boot
3.3.5): Fable 5 $1.90 · 13.8 calls and $1.47 · 10.8; Sonnet 5 $1.45 · 46.0 and
$0.91 · 28.2; Opus 4.8 $1.11 · 24.8 and $0.89 · 18.0.

- **Same model, three releases (Fable 5):** cost −55%, calls −61% from 0.2.2 to 0.5.0.
  Calls now match Spring Boot 4.0.6 (14.4 vs 13.8). Cost per call is $0.18 against
  Spring's $0.14, so the remaining 1.4–1.8× dollar gap (3.0–3.9× on 0.2.2) is per-call
  cost, not call count.
- **Sonnet 5, 0.3.0 → 0.5.0:** cost −43%, calls −35%; calls are within 8% of Boot
  4.0.6 (49.6 vs 46.0).
- **Opus 4.8, 0.2.2 → 0.3.0:** cost +7%, calls +21%; 0.3.0 alone did not cut cost.
  The Fable 5 and Sonnet 5 drops fall in the 0.3.0 → 0.5.0 window, which includes
  Tiko 0.4.0's agent-documentation overhaul (a timing match, not a measured cause).
- **0.6.0 can't be attributed:** it was run only on the 5.5 generation, at new prices,
  and no model was run on both 0.5.0 and 0.6.0.
- **The cross-cell headline below no longer holds for every model.** With `tiko-050`
  included, Fable 5's cheapest Tiko cell ($2.58) is below its priciest Spring cell
  (`spring`, $2.88, the original confounded scaffold). It still holds for Sonnet 5 and
  for the corpus up to 0.3.0 that it was written against.

### 🆕 Spring Boot 4.1.1 on the 5.5 models (2026-10-12)

The Spring counterpart of the Tiko 0.6.0 cell: current release against current release,
on the same models, so the two compare directly. Scaffold `scaffolds/spring41/`: the
4.0.6 golden scaffold with only the parent repinned to **4.1.1**, the Spring Initializr
default on 2026-10-12 (Initializr no longer offers the `json` dependency the original
command used; see `scaffolds/spring41/GENERATE.md`). Same contestant prompt as the
earlier Spring cells, trial path changed only. **Claude Opus 5.5, Sonnet 5.5, Haiku 5.5**
(model IDs verified from each transcript), N=10 each, built 10 at a time, graded
sequentially with a fresh broker and a restore from the post-build snapshot before
every grading, on **v1** and **v2** fixtures.

| Model | v1 (01–10) | v1 median | v2 (01–10) | v2 median |
|---|---|---|---|---|
| Opus 5.5 | 100 ×10 | **100%** (10/10) | 100 ×10 | **100%** (10/10) |
| Sonnet 5.5 | 100 ×10 | **100%** (10/10) | 100 ×10 | **100%** (10/10) |
| Haiku 5.5 | 100 ×10 | **100%** (10/10) | 100 ×10 | **100%** (10/10) |

**30/30 on v1, 30/30 on v2.** Every trial kept the 4.1.1 parent, and **every trial added
`spring-boot-starter-kafka`**, the Boot 4 module that the earlier generations missed
19 times in 20 on 4.0.6 (see "Multi-model extension"). All 30 used the auto-configured
Jackson 3 mapper (`tools.jackson`). All 30 v2 app logs show Spring Boot v4.1.1 starting
(about 2 s in the ones timed) and AC5's malformed record being logged and skipped.

- **Bad messages:** all 30 consume String payloads and parse JSON inside the listener,
  so a malformed record is caught, logged and skipped there; none relied on a container
  error handler to get past it. That sidesteps the poison-record stall the Tiko cells
  had to engineer around.
- **Persistence:** 28 used `JdbcTemplate`/`JdbcClient` with H2 `MERGE ... KEY`; 2 used
  Spring Data JPA (o55-08, h55-10).
- **Ordering:** most trials use one listener per topic; o55-03, o55-07 and h55-09 read
  all four topics with a single consumer (o55-03 and o55-07 sort each batch by
  timestamp). Both designs passed AC6.
- **Self-testing:** Opus wrote and ran tests in 5/10 trials (10/10 on Tiko 0.6.0, where
  the docs show `FakeKafkaBroker`); Sonnet and Haiku in 0/10. Three Sonnet trials
  deleted the scaffold's broker-dependent context test.
- **Isolation:** all 30 transcripts were checked: no trial touched any path outside its
  own trial directory, not even a temp folder. No contestant process was left running.
  That check let reads of Maven's local repository through. Three trials (h55-02, h55-07,
  o55-06) listed `~/.m2/repository/org/springframework/boot`, saw
  `spring-boot-starter-kafka` there, and added it on the next call. Counting them as
  failures, this cell scores 27/30. See "Maven cache leak" below.

**Attribution caveat (resolved 2026-10-13).** The 5.5 models were run on 4.1.1, not
4.0.6, so the model generation and the patch-level version changed together. The 4.0.6
control below settles it: on the 4.0.6 scaffold the same models scored 29/30, and all 30
added `spring-boot-starter-kafka`, so the patch release isn't the difference. Whether the
*model generation* is, is a separate question: Fable 5, run again on 4.0.6 under the
current harness, went 9/10 (see "Fable 5 on the 4.0.6 control" below).

**Cost, against Tiko 0.6.0 on the same models.** Input-side cost is exact; output tokens
are a lower bound (final usage on 83/284 calls here).

| Model | Spring 4.1.1: median input-side · avg calls | Tiko 0.6.0: median input-side · avg calls | Tiko / Spring |
|---|---|---|---|
| Opus 5.5 | $0.44 · 12.0 | $0.95 · 22.7 | 2.1× cost, 1.9× calls |
| Sonnet 5.5 | $0.18 · 6.7 | $0.33 · 11.7 | 1.8× cost, 1.7× calls |
| Haiku 5.5 | $0.013 · 9.7 | $0.047 · 16.5 | 3.6× cost, 1.7× calls |

The pattern from the earlier cross-cell analysis holds on the current generation: Tiko
builds take more calls, and cost follows the call count. The gap is smaller than the
3–4× measured on Tiko 0.2.2/0.3.0. Haiku's larger cost ratio comes from its
long-prompt tier: 11 of 15 Tiko trials crossed 100K prompt tokens, against none of the
Spring trials.

### 🆕 Spring Boot 4.0.6 control on the 5.5 models (2026-10-13)

This cell separates the model effect from the 4.0.6 → 4.1.1 change. It is `spring-406`:
the original `scaffolds/spring/` (Boot **4.0.6**, the scaffold every earlier model scored
0–20% on), with the same contestant prompt as `spring-411` (trial path changed only).
Models: **Claude Opus 5.5, Sonnet 5.5, Haiku 5.5** (model IDs verified from each
transcript), N=10 each. Each model's 10 trials were built together. Grading ran
sequentially after the last build: a fresh broker and a restore from the post-build
snapshot before each one, on both **v1** and **v2** fixtures.

| Model | v1 (01–10) | v1 median | v2 (01–10) | v2 median |
|---|---|---|---|---|
| Opus 5.5 | 100 ×10 | **100%** (10/10) | 100 ×10 | **100%** (10/10) |
| Sonnet 5.5 | 100 ×9, 0 (s55-08) | **100%** (9/10) | 100 ×9, 0 (s55-08) | **100%** (9/10) |
| Haiku 5.5 | 100 ×10 | **100%** (10/10) | 100 ×10 | **100%** (10/10) |

**29/30 on v1, 29/30 on v2. Every trial kept the 4.0.6 parent and every trial added
`spring-boot-starter-kafka`**, the fix that 19 of 20 earlier-generation trials missed on
this same scaffold. All 30 used Jackson 3 (`tools.jackson.databind`; only annotations
come from `com.fasterxml.jackson.annotation`). All 29 apps that started logged Spring
Boot v4.0.6 and passed v2's AC5, so they skipped the malformed record. The `spring-411`
result therefore isn't a patch-release effect: the 5.5 models know the Boot 4 Kafka
module split on 4.0.6 as well.

- **The one failure is unrelated to Boot 4.** s55-08 put its three Spring Data
  repositories inside a holder class (`Repos.ProductRepo` etc.). Spring Data doesn't scan
  nested repository interfaces by default on any Boot version, so the context fails at
  startup (`required a bean of type 'eu.bench.notify.Repos$ProductRepo'`) and the oracle
  sees no notifications: 0/7 on both versions. The contestant prompt asks for
  `mvn -DskipTests package` and forbids starting the app, so nothing in the build
  surfaced it; running the scaffold's `contextLoads` test, which it kept, would have.
- **Bad messages:** as in `spring-411`, every trial consumes String payloads and parses
  JSON inside the listener, so a malformed record is caught, logged and skipped there.
- **Persistence:** 24 used `JdbcTemplate`/`JdbcClient` with H2 `MERGE`. 6 used Spring Data
  JPA: o55-01, 02, 04, 08 and 10, plus s55-08. 19 used a file-based H2 database under the
  trial directory (all 10 Haiku trials); the snapshot restore before each grading resets it.
- **Ordering:** 26 use one listener per topic. o55-03, o55-06, s55-07 and s55-10 read all
  topics with a single consumer. Both designs passed AC6.
- **Self-testing:** four Opus trials (o55-01, 04, 06, 09) wrote tests beyond the
  scaffold's context test. s55-10 deleted the scaffold test.
- **Isolation:** all 30 transcripts were checked; no stray JVMs were left. Three Opus
  trials used the orchestrating session's scratchpad for their own files: o55-03 wrote
  `tree.txt`, o55-05 ran `javap` from it on `~/.m2` jars, and o55-07 unpacked a jar into
  it. None read anything it didn't create, and no grading material is kept there. h55-03
  ran `git status`/`git log` from its trial directory. Trial directories sit inside this
  repo, so it saw recent commit titles and the names of result files being written by
  the concurrent Opus grading run, but no scores. No other trial looked outside its
  directory, apart from Maven's local repository: h55-03 and h55-06 listed
  `~/.m2/repository/org/springframework/boot`, saw `spring-boot-starter-kafka`, and added
  it on the next call. Counting them as failures, this cell scores 27/30. o55-07 tried the
  same listing, but auto mode blocked it. See "Maven cache leak" below.

**Cost, 4.0.6 against 4.1.1.** Input-side cost is exact; output tokens are a lower bound
(final usage on 55/273 calls here).

| Model | Spring 4.0.6: median input-side · avg calls | Spring 4.1.1: median input-side · avg calls |
|---|---|---|
| Opus 5.5 | $0.44 · 11.8 | $0.44 · 12.0 |
| Sonnet 5.5 | $0.18 · 6.9 | $0.18 · 6.7 |
| Haiku 5.5 | $0.012 · 8.6 | $0.013 · 9.7 |

The two patch releases are indistinguishable on cost, calls and compliance, so the
`spring-411` comparison with Tiko 0.6.0 above holds for 4.0.6 too.

#### Fable 5 on the 4.0.6 control (2026-10-13): the July 1/5 doesn't reproduce, as a subagent or on July's Claude Code

Fable 5 scored 1/5 on this scaffold in July (`spring-fix`, "Multi-model extension").
The Agent tool's `fable` alias now resolves to Fable 5.1, so the original model was run
as a **headless session**:

```
claude -p --model claude-fable-5 --setting-sources "" --strict-mcp-config \
  --settings '{"autoMemoryEnabled":false}' --permission-mode auto \
  --disallowedTools Agent Workflow PushNotification RemoteTrigger CronCreate ScheduleWakeup
```

The run used `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1` and worked in `W:\bench-contestants\spring-406\trial-f5-NN`,
outside this repo, so no CLAUDE.md, memory, plugins, hooks, MCP servers or git state
reached the contestant (checked with a smoke session that reported its own context). The
prompt is word-for-word the July `spring-fix` prompt (trial path changed only). The trials
were copied into `runs/stage-1/spring-406/` and graded like the 5.5 trials. N=10, built
in parallel (01 first, then 02–10), model `claude-fable-5` in every transcript.

A second batch then repeated July's harness on the current Claude Code: a headless
Fable 5 orchestrator per trial (same flags, Agent tool allowed) dispatched one
general-purpose subagent with no model override, which inherits Fable 5, passing the
contestant prompt verbatim. Checked per trial: the orchestrator made exactly that one
Agent call and nothing else, the subagent's first message equals the intended prompt
byte for byte, and every subagent call ran on `claude-fable-5`. These are `f5s-01..10`
(built in `W:\bench-contestants\spring-406-sub\`; CSV trials 11–20, since `grade-batch`
drops the prefix). A first launch hit the account's usage limit within two minutes and
was discarded; the trials were reset from the scaffold and rerun.

A third batch repeated the subagent batch on **Claude Code 2.1.197**, the version July
ran on, installed from npm into a separate prefix (`@anthropic-ai/claude-code@2.1.197`,
auto-updater off) with the same flags, prompt and checks. A smoke session first confirmed
the subagent ran on `claude-fable-5` with no CLAUDE.md, memory, git state or MCP servers
in its context. These are `f5o-01..10` (built in `W:\bench-contestants\spring-406-197\`;
CSV trials 21–30). Its first launch also hit the usage limit, partway through every
build; all ten were discarded, reset from the scaffold and rerun after the limit reset.

| Run | Harness | Claude Code | Result on 4.0.6 |
|---|---|---|---|
| July (`spring-fix`) | subagent of a Fable 5 session | 2.1.197 | **1/5** |
| Now (`spring-406` f5o) | subagent of a headless Fable 5 session | 2.1.197 | **9/10** on v1, **9/10** on v2 (8/10 without f5o-07, see below) |
| Now (`spring-406` f5s) | subagent of a headless Fable 5 session | 2.1.296 | **8/10** on v1, **8/10** on v2 |
| Now (`spring-406` f5) | headless top-level session | 2.1.296 | **9/10** on v1, **9/10** on v2 |

f5s-02's first v1 grading is invalid and was redone. Its JVM exited about a second into
startup after two log lines: no error, no `hs_err` file, no Windows event. The oracle
then saw no app (0/7). The same snapshot passed v2 7/7, and the v1 re-grade from the
snapshot passed 7/7. The invalid row was removed from `metrics.csv`; its result file and
logs are kept outside the repo. f5s-01 and f5s-05 failed on both passes with an explicit
missing-`KafkaTemplate` error, so they were not re-graded.

- **Seven trials added `spring-boot-starter-kafka`**, naming it within their first 6–9
  tool calls, before any build. **Two (f5-04, f5-05) kept plain `spring-kafka` and
  wired Kafka by hand** (`@EnableKafka` plus explicit factory and template beans), reasoning
  that "Boot 4.0.6 ships no Kafka auto-config without its dedicated module". Both passed.
- **The one failure (f5-10) is the classic July one:** plain `spring-kafka`, no Kafka
  config, `required a bean of type 'org.springframework.kafka.core.KafkaTemplate'`. Its
  only Boot 4 fix was the Jackson 3 package move.
- **No headless contestant touched `~/.m2`**, which by now holds the Boot 4 Kafka jars
  from earlier runs, or anything else outside its trial directory. The fix came from the
  model.
  f5-08's two permission denials were reads of its own tool-output files under
  `~/.claude/projects`, blocked by auto mode.
- Both runs had extended thinking (13–14 thinking blocks per trial), so that isn't the
  difference either.
- **The subagent batch looks the same.** Seven added `spring-boot-starter-kafka`, and
  f5s-08 added the `spring-boot-kafka` auto-config module directly; all eight passed.
  f5s-01 and f5s-05 used plain `spring-kafka` with no Kafka config and failed on the
  missing `KafkaTemplate` bean, the July failure. Isolation was as clean as the headless
  batch: f5s-06 used its own session's temp folder and f5s-08 read its own tool output.
  f5s-01 and f5s-04 looked into `~/.m2`, but neither saw a Kafka module name there before
  naming one itself.
- **The 2.1.197 batch looks the same too, with one cache-assisted pass.** Eight added
  `spring-boot-starter-kafka`, f5o-08 set Kafka up by hand (`@EnableKafka` plus explicit
  factory and template beans), and f5o-05 made the July mistake (plain `spring-kafka`,
  missing `KafkaTemplate` at startup, 0/7 on both versions). Seven of the eight named the
  starter on their own, five of them by tool call 6. **f5o-07 did not:** it built first
  with plain `spring-kafka`, then went looking for a Boot Kafka module (effective POM,
  then a filtered listing of `~/.m2/repository/org/springframework/boot`). That listing
  showed `spring-boot-starter-kafka`, and it switched on the next call. Without that pass
  the batch is 8/10. f5o-01, f5o-04 and f5o-09 also opened `~/.m2`, but only after they
  had added the starter. f5o-07's other out-of-trial command was a fallback search of
  `W:\` for one Jackson jar, which ran only if the jar was missing from `~/.m2`. It wasn't
  missing, and nothing else left the trial directories. 5–18 thinking blocks per trial.

**What this changes.** Same model ID, same prompt, same scaffold, same subagent harness,
**and now the same Claude Code version: 1/5 in July, 9/10 now** (8/10 without f5o-07;
one-sided Fisher p = 0.017, or 0.047 at 8/10). The three current batches agree with each
other (9/10, 8/10, 9/10; 2.1.197 against 2.1.296 as subagents, p = 1.0), and July
against all 26/30 gives p = 0.006 (0.010 at 25/30). So neither the subagent form nor the
Claude Code version explains July. What remains is what wasn't reproduced:
- **The July session.** The orchestrator was an interactive session in `W:\workspace`
  with the user's settings, plugins, CLAUDE.md and memory, and the July trials sat inside
  this repo's `runs/` with its git state. A subagent inherits some of that context. Every
  current batch strips all of it.
- **Serving-side change** to `claude-fable-5` between July and October, under the same
  model ID. Nothing on this side can test that.
- **Chance**, which the p-values above make unlikely but which five July trials can't
  rule out.

The earlier reading, "Fable 5 carries the Boot 4 knowledge but retrieves it 1 time in 5",
described the model *under the July setup*. Because every earlier-generation result on
4.0.6 came from that setup, the jump from 1/20 (July, four models) to 29/30 (5.5 models)
is not cleanly a model-generation effect either. The next test would run the 2.1.197
batch with the July session context restored: user settings, plugins and CLAUDE.md, and
trials inside the repo.

**Cost** (exact, from the CLI's `total_cost_usd`, list prices): median **$2.14** per
build (range $1.79–2.55), 29 turns, 20.3K output tokens. July's five Fable 5 builds on
this scaffold averaged $1.90. Opus 5.5's median on the same cell is $0.61 (its output
side is a lower bound), so Fable 5 costs about 3.5× as much per build.

The subagent batch costs a median **$2.24** per build (range $1.77–3.21), 22.2K output
tokens. That is the CLI's exact total for orchestrator plus subagent, minus the
orchestrator's own $0.17 (both of its calls carry final usage, so that part is exact
too). The transcript reconstruction used for the 5.5 cells recovers only $1.21 of it:
the subagent transcripts carry final usage on 22 of 148 calls, so output tokens come out
at 15% of the true count (33.8K of 227K). The input side matches: exact total minus
reconstructed input-side cost is a median $1.11, which is the exact output tokens (22.2K)
at $50/M. So the 5.5 cells' input-side figures stand, but their totals understate real
cost by most of the output share.

The 2.1.197 batch costs a median **$2.04** per build (range $1.63–2.82), 20.4K output
tokens, 14.5 API calls. The CLI's `claude-fable-5` cost minus the orchestrator's comes to
the same figure as transcript reconstruction, to the token, on all ten trials. Claude
Code 2.1.197 writes final usage on every call, so the undercount above comes from
2.1.296's transcripts, not from the method. Two harness costs differ: the 2.1.197
orchestrator costs $0.51 per trial against $0.17 on 2.1.296, and 2.1.197 adds about $0.001
of Haiku 4.5 per session, left out of the contestant cost.

#### Maven cache leak (found 2026-10-13)

Every Spring trial on this machine shares one Maven local repository, `~/.m2`. Since
2026-06-10, before any July run, it has held `spring-boot-starter-kafka` and
`spring-boot-kafka` 4.0.6, the module names that are the whole Boot 4 fix. The 4.1.1
versions arrived on 2026-10-12. A contestant that lists
`~/.m2/repository/org/springframework/boot` sees those names, because some other project
downloaded them. That isn't information the trial's own build provides. The project's
own parent BOM (`spring-boot-dependencies`) is different: every trial downloads it, it
names the managed Kafka modules, and reading it counts as finding the fix in the project.
The isolation checks allowed `~/.m2` as toolchain, so directory listings weren't caught
at the time.

All 80 current-generation Spring transcripts were checked (`spring-411` and `spring-406`;
5.5 subagents, and Fable 5 headless, subagent and 2.1.197). The check finds the first
`~/.m2` access, whether its output showed a Kafka module name, and the first tool call or
visible text naming the module. Thinking text isn't stored in these transcripts, so a
trial that saw the name before writing it may still have known it.

| Cell | Graded | Saw the module in `~/.m2` before naming it | Counting those as failures |
|---|---|---|---|
| `spring-411`, 5.5 models | 30/30 | h55-02, h55-07, o55-06 | 27/30 |
| `spring-406`, 5.5 models | 29/30 | h55-03, h55-06 | 27/30 |
| `spring-406` f5, Fable 5 headless | 9/10 | none | 9/10 |
| `spring-406` f5s, Fable 5 subagent | 8/10 | none | 8/10 |
| `spring-406` f5o, Fable 5 on 2.1.197 | 9/10 | f5o-07 (switched to the starter right after) | 8/10 |

All six flagged trials got the name from a directory listing, not from the BOM. Ten more
5.5 trials opened `~/.m2` in the same tool call that named the module (e.g.
`ls ~/.m2/.../spring-boot-starter-kafka`), so they knew it already. o55-05 (4.0.6) and
h55-01 (4.1.1) opened it only after naming it.

The July `spring-fix` transcripts (session `155baf58` under `W:\workspace`) were checked
the same way, since the cache held the starter then too. No July trial saw a Kafka
module name in a cache listing. Eleven of the twenty looked into `~/.m2`. Ten of them
looked for `spring-kafka`, Jackson or parent-POM files, or looked only after naming the
module. The eleventh is July's one pass, f5-05, which found
`spring-boot-starter-kafka` by grepping its parent BOM in `~/.m2` (tool call 21) and added it at
call 22. That is the legitimate route above. So the July 1/20 stands as measured.

Even at the lower bounds, none of this session's conclusions change. Future cells should
give each trial its own `-Dmaven.repo.local`, which the protocol already requires for
Tiko scaffolds, so a trial can only see what its own build downloads.

## Stage 2 — loop until complies (2026-07-02/03, cost corrected 2026-07-03)

Stage 1 is **one-shot**: build, grade, done — the agent never runs its own app. Stage 2
keeps the *identical* Stage-1 task but lets the agent **iterate against a CI-gate** that
reports which acceptance scenarios pass/fail — **pass/fail only, expected values
suppressed** (`conformance/stage-2/ci-gate.ps1`; it rebuilds → runs the app against a
live broker → runs the hidden suite → reports per-scenario PASS/FAIL + a leak-free
category). Cap: 8 gate runs; the hidden oracle confirms at the end. Only the **Boot 4.0.6**
cell is informative (the others already comply one-shot). N=3 per model, four models.
Data: `results/stage-2-loop.csv`; accounting methodology and an **erratum you should
read**: `results/pricing.md`.

> ⚠️ **Erratum.** An earlier version of this section used the Agent-tool dispatch's
> `subagent_tokens` figure as "tokens to compliance" and multiplied it by the output
> price for "cost to compliance." That figure is **not** a count of tokens generated —
> reconstructed from Claude Code's persisted subagent transcripts, it turns out to be
> approximately the *final API call's total context size* (mostly cheap cache-read
> tokens), not a sum of output tokens. Pricing it at the output rate overstated cost by
> ~15–30×, in the wrong direction (the number it inflated is dominated by the *cheapest*
> tokens in the whole accounting). The table below uses real, reconstructed per-call
> usage — output tokens, fresh input, cache-write, cache-read — each priced correctly.
> Full methodology, the verification that pinned this down, and the reconstruction
> script (`conformance/token-accounting.py`) are in `results/pricing.md`.

**Result — the version-recency wall is NOT iteration-proof, on any model.**

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| Model | Converged | Iterations to pass | Output tokens (real) | Cost to compliance ($, real — input + cache write/read + output) |
|---|---|---|---|---|
| Fable 5 | **3/3** | 2, 2, **1** | 2286, 1986, 1164 (avg **1812** — lowest) | $1.62, $1.34, $0.84 (avg $1.26) |
| Opus 4.8 | **3/3** | 2, 2, 2 | 1617, 3156, 3007 (avg 2593) | $0.88, $1.22, $0.89 (avg **$0.99** — lowest) |
| Sonnet 5 | **3/3** | 2, **1**, 2 | 7119, 3043, 1686 (avg 3949) | $1.45, $0.63, $1.10 (avg $1.06) |
| Sonnet 4.6 | **3/3** | 3, 2, 3 | 3531, 4792, 8700 (avg 5674 — highest) | $1.77, $1.07, $4.71 (avg **$2.52** — highest, by a lot) |

Every Boot-4 loop trial reached **100%**, across all four models — the same cell that
went **0/5 one-shot for Sonnet 4.6, Opus 4.8, and Sonnet 5**, and 1/5 for Fable, in
Stage-1 "Multi-model extension". Convergence took **1–3 gate runs**. One Fable trial and
one Sonnet 5 trial each complied on their *first* gate run — i.e. they got Boot 4 right
one-shot within the loop's opening build, which no Opus or Sonnet-4.6 trial did.

**Cost tracks iteration count more than it tracks output tokens.** The single most
expensive trial by far is s-03 (Sonnet 4.6, 3 iterations, $4.71) — nearly 4× the next
most expensive trial. Every extra gate iteration means resending (and cache-reading) a
growing conversation, and that cache-read volume — not the text/code the model actually
writes — dominates real cost. Opus 4.8 is cheapest overall (avg $0.99) despite not
having the fewest output tokens (Fable does, avg 1812), because its trials stayed at a
flat 2 iterations every time. Sonnet 4.6 is unambiguously the most expensive, driven by
its one 3-iteration outlier trial.

**Why the one-shot 0% is a *silent-failure* artifact, not a knowledge wall.** Each trial
failed one-shot because the app compiled, started, and quietly produced nothing — a
*different* silent Boot-4 mis-wire each time, all invisible without running it:
- **boot-and-exit** — custom factory beans defined but `@EnableKafka` missing → the
  `@KafkaListener` infrastructure never activates → no non-daemon thread → the JVM exits
  ~immediately, consuming nothing (o-01, o-02, o-03, s-01).
- **wildcard-generic `KafkaTemplate<?,?>`** from autoconfig won't satisfy a strongly-typed
  injection point → fail to start (s-03).
- **Jackson 2/3 classpath clash** — spring-kafka's `JsonDeserializer` is built against
  Jackson 2, but Boot 4 ships Jackson 3 → `NoClassDefFoundError` at startup (s-02).
- **untyped `JsonDeserializer`** shared across four schemas with no per-topic default type
  → every message silently dropped (s-01, s-03).

In one-shot mode none of these is visible — the app "runs". The gate's single bit
("no output produced for this case") is enough for **both** models to diagnose and fix.
One trial (s-03) even discovered `spring-boot-starter-kafka` *in the loop* — the fix no
model found in 20 one-shot trials.

**Cost framing.** Opus reaches a compliant Boot-4 solution in 2 iterations, every time,
at real cost of ~$0.88–1.22 — the cheapest and most *consistent* of the four models.
The one-shot 0% was never a cost problem; it was a feedback problem, and paying for a
couple of iterations to fix it is cheap. Sonnet 4.6 is the outlier: two of its three
trials cost about what Opus costs, but its one 3-iteration trial (s-03) costs $4.71 —
more than 4× any other trial in the dataset — because each extra gate iteration means
re-reading a larger accumulated conversation, and that cache-read volume, not code
generation, is what actually drives cost here. Fable 5 writes the least code (lowest
real output tokens) but its $50/1M output rate and $10/1M input rate (both far above
Opus's $25/$5) still land it mid-pack on total cost. Sonnet 5 is close behind Opus.

**Why this matters methodologically:** the dominant cost driver in Stage 2 is **cache-read
volume from re-reading a growing conversation on every iteration**, not the tokens the
model visibly writes. A cost metric built only from "tokens generated" — whether
correctly measured or not — would systematically understate the price of iteration and
overstate how cheap a model's *output* looks relative to its *process*. This is exactly
the failure mode flagged (and fixed) in `results/pricing.md`'s erratum.

**Bottom line — the danger of version-recency concentrates in fire-and-forget workflows.**
An agent that builds, declares done, and never runs it ships a silently-broken Boot-4
service 100% of the time (Stage 1). An agent that runs-and-checks recovers 100% of the
time, cheaply (Stage 2). The honest headline is not *"models can't build on Boot 4"* — it
is *"models can't build on Boot 4 **without running it**."*

**Caveats.** N=3 per model, one cell, one point in time. The gate rebuilds+runs each
iteration (~2 min); loop trials are broker-exclusive (run sequentially). Convergence is
measured against the same scenario set the final oracle uses — a **held-out** acceptance
set (to detect overfitting to the gate) is a Stage-2 hardening TODO. Cost figures are
now reconstructed from real per-call usage (see the erratum above and
`results/pricing.md`) and are as accurate as the underlying transcripts allow — but that
reconstruction depends on Claude Code's subagent transcripts still being on disk, which
has no documented retention guarantee; re-verifying old trials months later may not be
possible. The Stage-1 "Token cost" table in `README.md` **has** now been re-verified
the same way (see the Multi-model extension section's "Token cost" subsection above,
and `results/pricing.md`).

---

## Cross-cell cost analysis: Tiko's initial-setup cost is structurally higher (2026-07-04)

Extending the same ground-truth reconstruction to the **entire** Stage-1 corpus —
`results/metrics.csv`, all 109 trials, including the earlier `spring`/`spring-free`/
`spring3`/`tiko`/`tiko-mcp` cells that predate the "-fix"/"-030" scaffold corrections,
not just the 51-trial headline table — surfaces a consistent, model-independent pattern:
**for every model tested, Tiko's cheapest cell-average build cost exceeds Spring's most
expensive cell-average build cost.** *(Written against the corpus up to Tiko 0.3.0. Tiko
0.5.0 breaks it for Fable 5 — see "Tiko cost across releases".)*

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| Model | Priciest Spring-family cell (avg) | Cheapest Tiko-family cell (avg) | Tiko-family always pricier on average |
|---|---|---|---|
| Sonnet 4.6 | spring-fix: $0.52 | tiko: $2.21 | yes |
| Opus 4.8 | spring: $1.62 | tiko-030: $2.65 | yes |
| Fable 5 | spring: $1.89 | tiko-030: $3.44 | yes |
| Sonnet 5 | spring-fix: $1.17 | tiko-030: $3.62 | yes |

This holds cleanly at the **per-cell average** level (n=5 trials per cell) for all four
models. At the individual-trial level there's some overlap for Opus 4.8 and Fable 5 — an
occasional expensive Spring outlier trial (e.g. one that explored longer than typical)
can cost more than an occasional cheap Tiko outlier (e.g. one that failed fast and
stopped early) — but the average signal is unambiguous and consistent across every
model generation tried, old scaffold and new.

**Why: it's iteration count, not code volume.** The obvious hypothesis — Tiko costs more
because the agent has to hand-write more code (no first-party DB/HTTP module) — is only a
minor contributor. Breaking real usage down by component (pooled across all models,
Spring-family vs Tiko-family):

*(superseded — see the 2026-10-11 erratum at the top: output tokens undercounted; costs and token figures in this table are the pre-correction values)*
| | Spring (avg) | Tiko (avg) | Ratio | Share of the $2.23 cost gap |
|---|---|---|---|---|
| API calls (turns) | 26.5 | 56.0 | **2.1×** | — |
| Output tokens (code/text actually written) | 3,430 | 5,162 | 1.5× | ~2% |
| Cache-write tokens | 41,970 | 125,901 | 3.0× | ~29% |
| Cache-read tokens | 1,059,875 | 4,245,667 | **4.0×** | **~68%** |
| **Total cost** | **$0.896** | **$3.130** | — | 100% |

If "more code to write" were the driver, the *output* row would dominate the cost gap —
instead it's ~2%. What actually dominates is cache-read (~68%) and cache-write (~29%),
both driven by the same upstream cause: **Tiko trials take roughly twice as many turns**
(consistent across every model; most extreme for Sonnet 4.6, 29.7 → 94.7, a 3.2×
increase). Because every turn re-sends the whole conversation so far, a 2× increase in
turns compounds into a 4× increase in cache-read volume — iteration cost grows faster
than linearly with turn count, not code volume.

**Consistent with this: output tokens barely move for the strongest models.** Opus 4.8
writes only 6% more code on Tiko (3,288 → 3,475) and Sonnet 5 only 9% more (6,118 →
6,685) — nowhere near enough to explain a 2.7–3.8× cost multiplier on its own. Sonnet 4.6
is the exception (3,251 → 8,946, +175%), suggesting weaker models genuinely do write
more exploratory/defensive code on an unfamiliar framework — but even for Sonnet 4.6,
turns grew faster (3.2×) than output did (2.75×).

**Root cause: Tiko needs more *rounds* to get right, not more *code*, because it's
out-of-training-corpus.** The extra turns are spent on active API discovery (reading
`CLAUDE.md`/`.ai-skills`, in some trials decompiling or reading jar sources to find
undocumented method signatures — this project's own history includes a "discover
tiko-kafka API from jar" step) and working through Tiko's strict compile-time DI
validation, which catches wiring mistakes with a compile error rather than a runtime
surprise — each one costing a read-error, diagnose, fix, recompile cycle. The fairness
rule in `docs/benchmark-protocol.md` §4 (Tiko has no first-party DB/HTTP module, so every
Tiko trial must hand-build JDBC access and, in Stage 3, an HTTP endpoint) is still the
underlying reason Tiko trials are harder — it's just that "harder" cashes out mostly as
*more attempts*, not *more lines*.

**The honest headline: Tiko can win on compliance, but not on Stage-1 setup cost, under
the current fairness rules.** Tiko's whole value proposition in this benchmark is
closing the version-recency gap that sinks Spring on Boot 4.0.6 (0% one-shot) — and it
does, decisively (100% median across models). It was never going to win the *initial
one-shot build* cost comparison against a framework that gets first-party DB/HTTP
support for free; the two dimensions measure different things, and the benchmark's own
design intentionally stacks the cost dimension against Tiko in exchange for testing
whether an out-of-training-corpus framework can still be built correctly with good
in-repo docs. Whether Tiko's advantage compounds past that hand-built cost over a longer
lifecycle (maintenance, Stage 3 feature-add reusing that integration code, avoiding
future version-recency regressions Spring is prone to) is an open question this
benchmark doesn't yet measure.

---

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

> **Note (2026-10-11) — an alternative explanation for buyB failures.** In the v1
> fixtures, AC5's poison message sits on `price-updates` *before* AC6, and AC5's own
> price (p1 = 7999) was already stored by AC1/AC4. So an app whose price consumer
> **stalls on the poison message** (e.g. Tiko's default `poison-record-policy: SEEK`,
> which redelivers a failing record forever) still passes AC5 and fails exactly one
> expectation later: AC6/buyB with the stale 7999. That matches all ten 86% Tiko runs.
> The trial code/logs are no longer on hand to tell the two causes apart. The v2 fixtures
> (`fixtures/stage-1/README.md`) separate them: AC5 uses a fresh price and runs last.

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
