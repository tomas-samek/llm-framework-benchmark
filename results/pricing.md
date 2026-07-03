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
is `input×price_in + cache_write×price_in×1.25 + cache_read×price_in×0.1 +
output×price_out`, all divided by 1e6 — i.e. the **full**, correctly-weighted
cost, not an output-only proxy.

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

Two reconstruction gotchas, both handled by `conformance/stage-2/token-accounting.py`:
1. **A single API response is logged as multiple transcript lines** (one per
   content block — text, tool_use, tool_use, …), each carrying an *identical*
   copy of that response's usage object. Summing every line triple-counts.
   Fix: dedupe by `message.id` before summing.
2. **Nested sub-agents.** A trial's subagent can itself dispatch a background
   research agent; that nested agent's tokens are real cost but live in a
   *separate* transcript file, found by matching `agentId: <id>` inside
   `tool_result` content and recursing.

Spot-checked against a single large `Write` tool call (~3.3KB of file content,
~824 tokens) whose logged `output_tokens` was 1,269 — the right order of
magnitude, confirming the transcript's per-call usage is trustworthy once
deduped correctly.

### Caveat: this requires the transcript to still exist

This reconstruction only works while Claude Code's subagent transcript is
still on disk — there's no documented retention guarantee. **Run the
accounting soon after each trial, in the same session, not months later.**
Don't assume old trials can be reconstructed the way this file's history was
(we got lucky that this session's transcripts hadn't been pruned yet).

### Known unresolved scope: Stage-1 headline numbers

The Stage-1 "Token cost" table in `README.md` (spring/spring3/tiko cells,
4 models) was also built from `subagent_tokens` and is very likely subject to
the same distortion — those numbers should be treated as **directional, not
authoritative**, pending the same reconstruction. Re-deriving them is out of
scope for this fix (those trials ran across multiple earlier Claude Code
sessions over several days; some transcripts may already be pruned) but is a
real TODO, not a closed question.
