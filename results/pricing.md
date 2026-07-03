# Model pricing reference (for cost_usd columns)

**As of 2026-07-03.** Prices move; re-verify before citing in anything durable.
Anthropic API, per-million-tokens, standard (non-batch) rate.

| Model | Input $/1M | Output $/1M |
|---|---|---|
| Claude Fable 5 (`claude-fable-5`) | $10.00 | $50.00 |
| Claude Opus 4.8 (`claude-opus-4-8`) | $5.00 | $25.00 |
| Claude Sonnet 5 (`claude-sonnet-5`) | $3.00 (intro $2.00 through 2026-08-31) | $15.00 (intro $10.00 through 2026-08-31) |
| Claude Sonnet 4.6 (`claude-sonnet-4-6`) | $3.00 | $15.00 |

All `cost_usd` figures in this repo use the **regular** (non-intro) output rate and are
computed as `tokens_to_compliance × output_price / 1e6`.

## Why this is an output-only proxy — and why that matters more for Stage 2

The benchmark harness (Agent tool subagent results) only surfaces **output** tokens
(`subagent_tokens`); input tokens are not captured. For Stage 1 (one-shot builds) this is
a reasonable proxy — the agent never runs its own app, so there's no large log to read
back in as input.

**Stage 2 (loop-until-complies) is different.** Each iteration, the agent reads
`app.log` to diagnose the ci-gate's pass/fail signal — and that log is large:
across the 9 completed Boot-4 loop trials, `app.log` ranges **130 KB–363 KB**
(roughly **33k–91k tokens if read in full** — comparable to, or larger than, the
*entire* output-token count we report for some trials). A trial that runs the gate
2–3 times plausibly re-reads a log of that size 2–3 times, meaning the true input-token
cost of a Stage-2 trial could be of the **same order of magnitude as its output cost**,
un-captured by every `cost_usd` figure here.

This effect also isn't uniform across cells: higher-iteration trials (e.g. Sonnet 4.6's
3-iteration runs) re-read the log more times than 1–2-iteration trials, so the true cost
gap between models is very likely **larger** than the output-only numbers show, not
smaller. **Treat every Stage-2 `cost_usd` / "$" figure as directional, not authoritative.**
Stage-1 `cost_usd` figures don't carry this caveat.
