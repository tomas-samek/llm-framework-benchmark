# Gate protocol — compiles / runs / conforms, with attempts and tokens

A refinement of how a trial is *measured*. Instead of collapsing a build into one
`compliance` number, split it into three ordered gates and report, per gate, three
things: **pass-rate**, **attempts-to-pass**, and **tokens-to-pass**.

This is a measurement layer, not a scoring change. **No framework is presumed to win
any gate** — the protocol is designed so either could, and the interesting result is
*where* effort and failure concentrate, not a predetermined verdict.

## The three gates

| Gate | Passes when | Verified by |
|---|---|---|
| **1 — Compiles** | `mvn -DskipTests package` → `BUILD SUCCESS` | the build tool |
| **2 — Runs** | the built app boots against a live broker + DB and processes a sample message end-to-end (emits ≥1 notification) without crashing — the **first running version** | the agent, against a live broker |
| **3 — Conforms** | the hidden external oracle's acceptance scenarios pass | `conformance/oracle` (unchanged) |

Gate 1 can be a *false green* (compiles ≠ works); Gate 3 can be *runs-but-wrong*.
Separating them makes both visible.

## Metrics (per model × cell, median over N)

- **pass_rate** — fraction of trials reaching the gate.
- **attempts_to_pass** — number of that gate's verification-command invocations until
  the first pass (first-try success = 1):
  - Gate 1: `mvn` `package`/`compile`/`install`/`verify` invocations.
  - Gate 2: app launches (`java -jar` / `mvn exec:java`).
  - Gate 3: the oracle is one-shot — no attempts; report compliance only.
- **tokens_to_pass** — output tokens spent up to the first pass (transcript checkpoint).

### On "attempt" (and its honest caveat)

`attempts` is parsed objectively from the transcript (`conformance/extract-attempts.js`),
not self-reported. It is **cadence-dependent**: an agent that compiles after every edit
logs more attempts than one that batches edits then compiles once. So it is noisy as an
absolute — always report the **median across N** and pair it with tokens. It is meaningful
as a **relative, cross-gate** signal (where does the framework reject you — compile or
run?), not as a precise count.

Observed already (Stage-1, `results/attempts.csv`): the compile gate is cheap for
everyone (median 1–3 attempts); Tiko's real cost is API *discovery* (tokens spent reading
jars/`javap`), which is not a compile *attempt*. Conflating attempts with tokens would
misstate this — both columns are needed.

## Harness change required for Gate 2

Today trials are graded **build-only** (the contestant never runs the app; the oracle runs
it later). To measure Gate-2 attempts and tokens, the contestant must reach a running app:

- **Per-contestant live broker** on a unique port (so builds still parallelize) + embedded H2.
- A **sample publisher** producing representative messages — **not** the graded fixtures
  (no oracle leakage). The agent self-verifies "it runs and emits notifications," iterating
  until it does, exactly like a developer running it locally.
- The **hidden oracle still grades Gate 3** on the fixed scenarios afterward.
- Capture `compile_attempts` / `run_attempts` / tokens by transcript parsing; conformance
  from the oracle.

## Fairness constraints (unchanged in spirit)

- Identical dispatch prompt, scaffold, spec, and sample-data generator across every
  framework and model.
- **Sample data ≠ fixtures.** The oracle stays hidden. No hints, no example repos, no MCP
  unless MCP is the arm under test.
- Report medians + spread over N; compare efficiency only at equal conformance.

## Reporting shape

| Cell | G1 compiles (pass · att · tok) | G2 runs (pass · att · tok) | G3 conforms (%) |
|---|---|---|---|
| spring (Boot 4.0.6) | … | … | … |
| spring3 (Boot 3.3.5) | … | … | … |
| tiko (0.3.0) | … | … | … |

## Status

- **Gate 1 + Gate 3** exist for prior runs (build-green + oracle compliance).
- **compile_attempts** backfilled across this session's cells → `results/attempts.csv`.
- **Gate 2** (run attempts + tokens) needs the live-broker harness above.
  Suggested first pilot: **Spring Boot 4.0.6 vs Tiko 0.3.0, Opus, N=3** — the cell where
  the compiles-vs-runs gap is most informative — before scaling.

## Caveats

N is small and directional; `attempts` is cadence-dependent (see above); single domain
(Kafka → H2 → notifications); single point in time per model/version. The gates measure
the agent-plus-framework system, not the framework alone.
