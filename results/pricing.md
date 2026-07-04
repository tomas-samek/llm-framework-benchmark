# Model pricing reference and token-accounting methodology

**As of 2026-07-03.** Prices move; re-verify before citing in anything durable.
Anthropic API, per-million-tokens, standard (non-batch) rate.

| Model | Input $/1M | Output $/1M |
|---|---|---|
| Claude Fable 5 (`claude-fable-5`) | $10.00 | $50.00 |
| Claude Opus 4.8 (`claude-opus-4-8`) | $5.00 | $25.00 |
| Claude Sonnet 5 (`claude-sonnet-5`) | $3.00 (intro $2.00 through 2026-08-31) | $15.00 (intro $10.00 through 2026-08-31) |
| Claude Sonnet 4.6 (`claude-sonnet-4-6`) | $3.00 | $15.00 |

Cache multipliers (applied to the *input* rate above): cache write ×1.25 (5-minute
TTL) or ×2 (1-hour TTL); cache read ×0.1. `cost_usd` in `results/stage-2-loop.csv`
and `results/metrics.csv` is `input×price_in + cache_write×price_in×1.25 +
cache_read×price_in×0.1 + output×price_out`, all divided by 1e6 — i.e. the
**full**, correctly-weighted cost, not an output-only proxy.

## Erratum: `subagent_tokens` is not output tokens (corrected 2026-07-03)

An earlier version of this benchmark used the Agent-tool dispatch's reported
`subagent_tokens` figure as `tokens_to_compliance`, and computed `cost_usd` as
`subagent_tokens × output_price`, labeling it an "output-token-only proxy."
That was wrong in a more serious way than "only counts output": **`subagent_tokens`
is not a token-generation count at all.**

### What it actually is

Reconstructed by checking Claude Code's own persisted subagent transcripts
(`~/.claude/projects/<project>/<session>/subagents/agent-<id>.jsonl`, which
contain the real per-call `usage` objects — `input_tokens`,
`cache_creation_input_tokens`, `cache_read_input_tokens`, `output_tokens`).
`subagent_tokens` matches, to within 0.1%–1.8% across all 12 Stage-2 loop
trials, the **final API call's total context size**: that call's
`input_tokens + cache_creation_input_tokens + cache_read_input_tokens +
output_tokens`. In other words, it is a snapshot of how large the conversation
had grown by the time the subagent finished — not a sum of tokens generated
across the session, and mostly composed of **cheap cache-read tokens** (billed
at ~0.1× the input rate), not output tokens (billed at 15–50× the input rate).

Multiplying that number by the *output* price therefore overstated cost by
roughly **15×–30×**, and in the wrong direction to boot — the growing-context
tokens it's dominated by are the *cheapest* tokens in the whole accounting,
not the most expensive.

### The fix

`results/stage-2-loop.csv` now reports real, reconstructed values:
- `output_tokens` — sum of every unique API response's `output_tokens` for
  the trial (see the dedup note below), including any nested sub-agent the
  trial spawned (e.g. a background research agent) — its tokens are real
  spend that belongs to the trial.
- `input_tokens`, `cache_write_tokens`, `cache_read_tokens` — same treatment.
- `cost_usd` — computed from all four components above with the correct
  per-component price (see formula above), not `output_tokens_apparent ×
  output_price`.

Reconstruction gotchas, all handled by `conformance/token-accounting.py`:
1. **A single API response is logged as multiple transcript lines** (one per
   content block — text, tool_use, tool_use, …), each carrying an *identical*
   copy of that response's usage object. Summing every line triple-counts.
   Fix: dedupe by `message.id` before summing.
2. **Nested sub-agents.** A trial's subagent can itself dispatch a background
   research agent; that nested agent's tokens are real cost but live in a
   *separate* transcript file, found by matching `agentId: <id>` inside
   `tool_result` content and recursing — potentially into a *different*
   session directory than its parent, if the parent and the nested dispatch
   didn't happen to fall in the same session.
3. **Interrupted-and-re-dispatched trials leave two transcripts.** Discovered
   while re-verifying Stage 1: 6 of the 51 Stage-1 trials (all Fable 5) each
   matched *two* candidate transcripts. One was an earlier attempt that hit a
   session token limit mid-build (final `stop_reason: "stop_sequence"`, last
   message literally "You've hit your session limit — resets 10:40pm..."),
   the other was the completed re-dispatch after the workspace was wiped and
   re-run (final `stop_reason: "end_turn"`, a real completion report). Using
   the truncated transcript understates both tokens and cost for that trial.
   Fix: when `find_transcript()` returns more than one hit, check
   `last_assistant_stop_reason()` for each and prefer `"end_turn"`.
4. **A different, unrelated task can spuriously match a trial's path.** Found
   while extending the reconstruction to `results/metrics.csv`'s full
   109-trial corpus: a shared batch-grading dispatch ("grade five Tiko
   contestant apps... trials 01..05") and an unrelated jbang/MCP-setup task
   both happened to contain a target trial's path string somewhere in their
   transcript, causing a false match when searching the *entire* transcript
   text. Fix, in `token-accounting.py`'s matching helper: restrict the search
   to each transcript's **first user message** (the actual dispatch prompt,
   not later tool output/exploration that can reference other paths in
   passing), and require the word **"only"** to appear shortly before the
   matched path (checked against every occurrence of every path spelling in
   the message, not just the first found). Every genuine build dispatch
   establishes its working directory with "work ONLY here" / "working ONLY
   inside this directory" language immediately before the path; a passing
   mention (e.g. "target project (already built...): `<path>`") does not.
   Older, more loosely-worded trial directories (bare `trial-01` instead of a
   longer, cell-specific name) are more exposed to this than the newer
   headline trials, which is why it surfaced late.

   **An earlier, less precise version of this fix required literally "build"
   in the message's opening ~80 characters instead of the "only"-proximity
   check** — cheaper to state, but wrong two ways. It produced false
   *negatives* for genuine dispatches whose preamble ran past 80 characters
   before the word "build" appeared (all 12 Stage-2 loop trials, which open
   with "You are a contestant... running in ITERATIVE mode. Build the
   system..."). More seriously, it silently left a session-limit-truncated
   duplicate as the *only* accepted match for 5 Fable-5/`tiko-mcp` trials,
   because the genuine completed dispatch happened to fail that specific
   check — understating both tokens and cost for those 5 rows in a way that
   produced no error and no "ambiguous" flag, since only one candidate
   passed. Caught by re-running the Stage-2 trials through the corrected
   tool and finding they no longer matched at all, which prompted a full
   121-trial validation against every previously-published number; exactly
   these 5 rows differed. Both `results/metrics.csv` and this file's Stage-2
   / Stage-1 sections have been corrected. The qualitative "Tiko always
   pricier than Spring on average" finding is unaffected — the corrected
   Fable/`tiko-mcp` average ($3.86, was $2.53) makes that finding *more*
   robust, not less; only the specific "cheapest Tiko-family cell" cited for
   Fable changed, from `tiko-mcp` to `tiko-030`.

Spot-checked against a single large `Write` tool call (~3.3KB of file content,
~824 tokens) whose logged `output_tokens` was 1,269 — the right order of
magnitude, confirming the transcript's per-call usage is trustworthy once
deduped correctly.

### Caveat: this requires the transcript to still exist

This reconstruction only works while Claude Code's subagent transcript is
still on disk — there's no documented retention guarantee. In practice, this
project's two sessions' transcripts survived from 2026-06-07 through at least
2026-07-03 (nearly a month, spanning both the original Stage-1 runs and the
Stage-2 loop trials) without being pruned, which is why both stages were
fully reconstructable — but that's this environment's observed behavior, not
a guarantee. **Run the accounting soon after each trial if you want certainty.**

## Stage-1 headline numbers — also corrected (2026-07-03)

The Stage-1 "Token cost" table in `README.md` (spring-fix/spring3-fix/tiko-030
cells, 4 models, 51 trials total) was also built from `subagent_tokens` and
was re-verified the same way as Stage 2, using all 51 trials' persisted
transcripts (100% recovered — both of this project's sessions had every
transcript still on disk). See `README.md` → "Headline results — Stage 1" →
"Token cost" for the corrected tables, and `results/RESULTS.md` →
"Multi-model extension" → "Token cost" for the full narrative. Headline
finding: **Fable 5 uses fewer real output tokens than Opus 4.8 in every
single cell**, but is still the most expensive per build in dollars in two
of three cells, because its $50/1M output and $10/1M input rates outweigh
the lower token count — tokens and dollars rank differently here too, for
the same reason as in Stage 2.

## Full metrics.csv reconstruction (2026-07-04)

Extended the reconstruction to `results/metrics.csv`'s **entire** 109-trial
Stage-1 corpus — not just the 51-trial headline table, but also the earlier
`spring`/`spring-free`/`spring3`/`tiko`/`tiko-mcp` cells that predate the
"-fix"/"-030" scaffold corrections. All 109 recovered; `output_tokens` and a
new `cost_usd` column now hold real, reconstructed values throughout the
file. This pass is what surfaced reconstruction gotcha #4 above (spurious
matches on older, more loosely-named trial directories) — re-verify anything
derived from the *older* cells specifically if you're extending this further,
since they're more exposed to that failure mode than the newer headline
trials are.

Headline finding from the full corpus: **Tiko's cheapest per-cell average
cost exceeds Spring's priciest per-cell average cost, for every model
tested** — see `results/RESULTS.md` → "Cross-cell cost analysis" for the
full breakdown and why this is a by-design consequence of the benchmark's
fairness rule (Tiko hand-builds DB/HTTP; Spring gets first-party starters),
not a Tiko defect.
