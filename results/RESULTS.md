# Stage-1 results

> **Start here:** the authoritative version-recency + Tiko-0.3.0 result (four model
> generations — Sonnet 4.6, Opus 4.8, Sonnet 5, Fable 5) is in
> ["Multi-model extension" below](#-multi-model-extension--sonnet-5--fable-5-added-tiko-030-added-2026-07-0102).
> Everything above it in this file is superseded first-pass / confounded data, kept for
> the audit trail.

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

**Token cost — a bigger/newer model is not a cheaper one (but the newest is the most
efficient).** Average output tokens per build:

| Model | spring-fix (Boot 4.0.6) | spring3-fix (Boot 3.3.5) | tiko-030 (Tiko 0.3.0) |
|---|---|---|---|
| Sonnet 4.6 | 45.0k | 36.5k | — |
| Opus 4.8 | 52.9k | 47.1k | 106.0k |
| Sonnet 5 | **88.2k** | **68.6k** | **186.9k** |
| Fable 5 | 54.9k | **49.4k** | 107.8k |

Sonnet 5 costs **~1.7–1.9× Opus 4.8** and **~1.9× Sonnet 4.6** on the identical tasks,
consistently across all three cells (not one outlier trial) — while landing at the same
or statistically indistinguishable compliance. On `tiko-030` specifically, all 5 Sonnet-5
trials independently rediscovered the same undocumented facts already filed as
[#401](https://github.com/tomas-samek/tiko-di/issues/401)–[#404](https://github.com/tomas-samek/tiko-di/issues/404)
(the `@KafkaSource`/`@EventTrigger` contract, the `poison-record-policy` default,
config-key casing) via deeper bytecode-level exploration (`javap -p -c` on generated
validators) than earlier models used — more thorough, and markedly more expensive.
**Fable 5 breaks the "newer = pricier" trend**: it matches Opus's token cost on Tiko
(~108k vs ~106k) and beats it on both Spring cells, while scoring highest overall
(perfect Tiko sweep + the only Boot-4 pass). So the relationship between model
generation and cost is not monotonic — but the core point stands: **you cannot buy your
way out of the version-recency blind spot with tokens** (Sonnet 5 spent 1.7× Opus on
Boot 4 and still went 0/5), and **capability + current knowledge beat brute-force
exploration** (Fable got the best results at near-lowest cost).

---

## Stage 2 — loop until complies (2026-07-02/03)

Stage 1 is **one-shot**: build, grade, done — the agent never runs its own app. Stage 2
keeps the *identical* Stage-1 task but lets the agent **iterate against a CI-gate** that
reports which acceptance scenarios pass/fail — **pass/fail only, expected values
suppressed** (`conformance/stage-2/ci-gate.ps1`; it rebuilds → runs the app against a
live broker → runs the hidden suite → reports per-scenario PASS/FAIL + a leak-free
category). Cap: 8 gate runs; the hidden oracle confirms at the end. Only the **Boot 4.0.6**
cell is informative (the others already comply one-shot). N=3 per model, four models.
Data: `results/stage-2-loop.csv`; pricing basis: `results/pricing.md`.

**Result — the version-recency wall is NOT iteration-proof, on any model.**

| Model | Converged | Iterations to pass | Tokens to compliance | Cost to compliance ($, output-only — see caveat below) |
|---|---|---|---|---|
| Fable 5 | **3/3** | 2, 2, **1** | 52k, 51k, 45k (**avg 49.5k** — lowest tokens) | $2.60, $2.55, $2.27 (**avg $2.47** — highest cost) |
| Opus 4.8 | **3/3** | 2, 2, 2 | 54k, 57k, 53k (avg 54.5k) | $1.34, $1.43, $1.32 (avg $1.36) |
| Sonnet 5 | **3/3** | 2, **1**, 2 | 75k, 63k, 82k (avg 73.4k) | $1.12, $0.95, $1.24 (**avg $1.10** — lowest cost) |
| Sonnet 4.6 | **3/3** | 3, 2, 3 | 119k, 80k, 152k (avg 116.8k — highest tokens) | $1.78, $1.19, $2.28 (avg $1.75) |

Every Boot-4 loop trial reached **100%**, across all four models — the same cell that
went **0/5 one-shot for Sonnet 4.6, Opus 4.8, and Sonnet 5**, and 1/5 for Fable, in
Stage-1 "Multi-model extension". Convergence took **1–3 gate runs**. One Fable trial and
one Sonnet 5 trial each complied on their *first* gate run — i.e. they got Boot 4 right
one-shot within the loop's opening build, which no Opus or Sonnet-4.6 trial did.

**Tokens and dollars rank differently — the "cheapest" model depends which unit you
use.** Fable 5 uses the **fewest tokens** (avg 49.5k) but, at $50/1M output, is the
**most expensive in dollars** (avg $2.47). Sonnet 5 is the mirror image: it uses **more
tokens than Opus** (73.4k vs 54.5k — a Sonnet-5-vs-Opus token gap consistent with the
Stage-1 finding below) but at $15/1M vs Opus's $25/1M it ends up **cheapest overall in
dollars** (avg $1.10 vs Opus's $1.36). Per-token price differences between models are
large enough to invert a token-based ranking — see `results/pricing.md` for the
dated price table this is computed from.

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

**Cost framing.** Opus reaches a compliant Boot-4 solution in 2 iterations at ~54.5k
tokens — **essentially the same token cost as one *failed* one-shot Boot-4 build (~53k,
see the Stage-1 token table)**. The one-shot 0% was never a *cost* problem; it was a
*feedback* problem. Sonnet 4.6 gets there in 2–3 iterations at ~117k tokens (~2.6× its
one-shot spend, ~2.1× Opus's loop cost) — pricier in tokens, and still pricier in
dollars (avg $1.75 vs Opus's $1.36), but still 100% convergence. Sonnet 5 spends more
tokens than Opus (~73.4k) but, being priced at $15/1M output vs Opus's $25/1M, is
actually the **cheapest of the four in dollars** (avg $1.10) — cost and token-count
diverge once per-model pricing enters the picture.

**A real gap in the cost accounting: input tokens aren't captured, and Stage 2 is
where that bites hardest.** Every `tokens_to_compliance` / `cost_usd` figure above
counts **output tokens only** — the harness has no visibility into input tokens. That's
a fair proxy for Stage 1 (the agent never runs its own app there), but in Stage 2 the
agent reads `app.log` after every gate run to diagnose the failure, and that log is
large: 130 KB–363 KB across these trials, i.e. roughly **33k–91k tokens per full
read** — on the same order as, or larger than, the entire output-token count for some
trials. A 2–3 iteration trial plausibly re-reads a log that size 2–3 times. This means
every dollar figure above likely **understates** true cost, and does so more for
higher-iteration trials (Sonnet 4.6's 3-iteration runs) than for 1–2-iteration ones —
so the true cost spread across models is probably *wider* than shown, not narrower.
Treat the Stage-2 cost column as directional. Full reasoning in `results/pricing.md`.

**Bottom line — the danger of version-recency concentrates in fire-and-forget workflows.**
An agent that builds, declares done, and never runs it ships a silently-broken Boot-4
service 100% of the time (Stage 1). An agent that runs-and-checks recovers 100% of the
time, cheaply (Stage 2). The honest headline is not *"models can't build on Boot 4"* — it
is *"models can't build on Boot 4 **without running it**."*

**Caveats.** N=3 per model, one cell, one point in time. The gate rebuilds+runs each
iteration (~2 min); loop trials are broker-exclusive (run sequentially). Convergence is
measured against the same scenario set the final oracle uses — a **held-out** acceptance
set (to detect overfitting to the gate) is a Stage-2 hardening TODO. Cost figures are an
output-token-only proxy (see above) and should not be read as authoritative dollar
amounts, particularly for Stage 2.

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
