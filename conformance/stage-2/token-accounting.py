"""Compute real per-trial token/cost accounting for Stage-2 loop trials.

WHY THIS EXISTS
----------------
The Agent-tool dispatch used to run each loop trial reports a single
`subagent_tokens` figure. Empirically (see results/pricing.md), that figure is
NOT a sum of output tokens generated during the trial -- it is approximately
the *final turn's total context size* (fresh input + cache-write + cache-read
+ that turn's own output), i.e. how big the conversation had grown by the time
the subagent finished. Verified within +/-0.1%-1.8% by reconstructing it from
raw usage data across the first 12 Stage-2 loop trials.

Using that number as if it were "output tokens" and multiplying by the output
price wildly overstates cost, because the number is dominated by cache-read
tokens (priced at ~0.1x the input rate), not output tokens (priced far
higher). This script computes the real thing from Claude Code's own persisted
subagent transcripts.

HOW IT WORKS
------------
Claude Code persists one JSONL transcript per subagent dispatch at:
    ~/.claude/projects/<project>/<session-id>/subagents/agent-<id>.jsonl
Each line is a transcript event; "assistant" events carry a `message.usage`
object with real input_tokens / cache_creation_input_tokens /
cache_read_input_tokens / output_tokens for that API call.

Two gotchas this script handles:
  1. A single API response can be split across multiple JSONL lines (one per
     content block: text, tool_use, tool_use, ...), each carrying an
     IDENTICAL copy of that response's usage object. Naively summing every
     line double/triple-counts. Dedupe by `message.id` first.
  2. A subagent can itself dispatch a nested sub-agent (e.g. a background
     research agent). That nested agent's tokens are NOT in the parent's
     transcript but DO count toward the trial's real cost. This script finds
     "agentId: <id>" patterns inside tool_result content and recurses into
     `agent-<id>.jsonl` for each one found.

CAVEAT -- TRANSCRIPT RETENTION
-------------------------------
This only works while the subagent transcript still exists on disk. There is
no documented retention guarantee. Run this accounting SOON after each trial
completes (same session, same day), not months later -- don't assume you can
reconstruct cost for old trials the way this file's history did.

USAGE
-----
Edit the TRIALS dict below to map trial name -> (model, transcript filename),
then: python token-accounting.py
"""
import json
import os
import re
import sys

# Fill in per-run: {trial_name: (model_id, "agent-<id>.jsonl")}
# Find the transcript filename with:
#   grep -Fl "<trial-dir-name>" ~/.claude/projects/<project>/<session>/subagents/*.jsonl
TRIALS = {
    # "s5-01": ("claude-sonnet-5", "agent-a2c3589fde9093ed4.jsonl"),
}

# $/1M tokens, regular (non-intro) output/input rates -- keep in sync with results/pricing.md
PRICES = {
    "claude-opus-4-8":   {"in": 5.00,  "out": 25.00},
    "claude-sonnet-4-6": {"in": 3.00,  "out": 15.00},
    "claude-fable-5":    {"in": 10.00, "out": 50.00},
    "claude-sonnet-5":   {"in": 3.00,  "out": 15.00},
}

AGENT_ID_RE = re.compile(r"agentId:\s*([a-f0-9]+)")


def _load_lines(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def _find_nested_agent_ids(lines):
    ids = []
    for obj in lines:
        if obj.get("type") != "user":
            continue
        content = (obj.get("message") or {}).get("content")
        if not isinstance(content, list):
            continue
        for block in content:
            if isinstance(block, dict) and block.get("type") == "tool_result":
                c = block.get("content")
                text = json.dumps(c) if not isinstance(c, str) else c
                ids.extend(AGENT_ID_RE.findall(text))
    return ids


def sum_usage_recursive(subagents_dir, agent_jsonl_filename, seen=None):
    seen = seen if seen is not None else set()
    path = os.path.join(subagents_dir, agent_jsonl_filename)
    tot = dict(input_tokens=0, cache_creation_input_tokens=0, cache_read_input_tokens=0,
               output_tokens=0, ephemeral_1h=0, api_calls=0, nested=0)
    if path in seen or not os.path.exists(path):
        return tot
    seen.add(path)

    lines = _load_lines(path)

    by_msg_id = {}
    order = []
    for obj in lines:
        if obj.get("type") != "assistant":
            continue
        m = obj.get("message") or {}
        mid = m.get("id")
        u = m.get("usage")
        if not u or mid is None:
            continue
        if mid not in by_msg_id:
            by_msg_id[mid] = u
            order.append(mid)

    for mid in order:
        u = by_msg_id[mid]
        tot["input_tokens"] += u.get("input_tokens", 0)
        tot["cache_creation_input_tokens"] += u.get("cache_creation_input_tokens", 0)
        tot["cache_read_input_tokens"] += u.get("cache_read_input_tokens", 0)
        tot["output_tokens"] += u.get("output_tokens", 0)
        tot["ephemeral_1h"] += (u.get("cache_creation") or {}).get("ephemeral_1h_input_tokens", 0)
        tot["api_calls"] += 1

    for nested_id in _find_nested_agent_ids(lines):
        sub = sum_usage_recursive(subagents_dir, f"agent-{nested_id}.jsonl", seen)
        for k in tot:
            tot[k] += sub[k]
        if sub["api_calls"] > 0:
            tot["nested"] += 1

    return tot


def cost(model, u):
    p = PRICES[model]
    write_5m = u["cache_creation_input_tokens"] - u["ephemeral_1h"]
    write_1h = u["ephemeral_1h"]
    c_in = u["input_tokens"] * p["in"] / 1e6
    c_write = (write_5m * 1.25 + write_1h * 2.0) * p["in"] / 1e6
    c_read = u["cache_read_input_tokens"] * p["in"] * 0.1 / 1e6
    c_out = u["output_tokens"] * p["out"] / 1e6
    return dict(input=c_in, write=c_write, read=c_read, output=c_out,
                total=c_in + c_write + c_read + c_out)


def main():
    subagents_dir = sys.argv[1] if len(sys.argv) > 1 else None
    if not subagents_dir:
        home = os.path.expanduser("~")
        subagents_dir = os.path.join(home, ".claude", "projects")
        print(f"No subagents dir given; pass it explicitly, e.g.:\n"
              f"  python token-accounting.py <path-to>/subagents\n"
              f"(under {subagents_dir}/<project>/<session-id>/subagents)")
        return

    if not TRIALS:
        print("Fill in the TRIALS dict at the top of this file first.")
        return

    print(f"{'trial':8} {'model':18} {'calls':>5} {'nest':>4} {'in':>8} {'write':>9} {'read':>10} {'out':>7} "
          f"| {'$in':>6} {'$wr':>6} {'$rd':>6} {'$out':>6} {'$TOT':>7}")
    for trial, (model, fname) in TRIALS.items():
        u = sum_usage_recursive(subagents_dir, fname)
        c = cost(model, u)
        print(f"{trial:8} {model:18} {u['api_calls']:5} {u['nested']:4} {u['input_tokens']:8} "
              f"{u['cache_creation_input_tokens']:9} {u['cache_read_input_tokens']:10} {u['output_tokens']:7} | "
              f"{c['input']:6.3f} {c['write']:6.3f} {c['read']:6.3f} {c['output']:6.3f} {c['total']:7.3f}")


if __name__ == "__main__":
    main()
