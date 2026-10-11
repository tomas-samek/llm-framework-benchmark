"""Compute real per-trial token/cost accounting for any Agent-tool-dispatched
benchmark trial (Stage 1 one-shot builds, Stage 2 loop trials, etc).

WHY THIS EXISTS
----------------
The Agent-tool dispatch used to run each trial reports a single
`subagent_tokens` figure. Empirically (see results/pricing.md), that figure is
NOT a sum of output tokens generated during the trial -- it is approximately
the *final turn's total context size* (fresh input + cache-write + cache-read
+ that turn's own output), i.e. how big the conversation had grown by the time
the subagent finished. Verified within +/-0.1%-1.8% by reconstructing it from
raw usage data across the first 12 Stage-2 loop trials, and confirmed again
across all 51 Stage-1 trials.

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

Gotchas this script handles:
  1. A single API response can be split across multiple JSONL lines (one per
     content block: text, tool_use, tool_use, ...), each carrying an
     copy of that response's usage object. Naively summing every line
     double/triple-counts. Dedupe by `message.id` first -- and keep the RIGHT
     line (see 5): the copies are not identical.
  2. A subagent can itself dispatch a nested sub-agent (e.g. a background
     research agent). That nested agent's tokens are NOT in the parent's
     transcript but DO count toward the trial's real cost. This script finds
     "agentId: <id>" patterns inside tool_result content and recurses into
     `agent-<id>.jsonl` for each one found -- across every session directory
     given, since a nested agent can land in a different session file than
     its parent.
  3. A trial that got interrupted (e.g. hit a session token limit mid-build,
     `stop_reason: "stop_sequence"` with a "You've hit your session limit"
     message) and was wiped and re-dispatched leaves BOTH transcripts on
     disk, and both will match a naive path-substring search. When
     find_transcript() returns more than one candidate for the same trial,
     prefer the one whose last assistant turn has `stop_reason: "end_turn"`
     (completed normally) over one that stopped some other way (interrupted).
     This is not automatic in `find_transcript()` -- inspect the candidates
     and pick explicitly; see results/pricing.md for a worked example.
  4. An unrelated transcript can spuriously match a trial's path -- e.g. a
     shared batch-grading dispatch that enumerates several trial directories,
     or a completely unrelated task that happens to reference the path in
     passing (e.g. "target project (already built...): <path>"). `find_
     transcript()` guards against this two ways: it only searches each
     transcript's FIRST user message (the actual dispatch prompt), not the
     whole transcript, and it requires the word "only" to appear shortly
     before the matched path -- every genuine build dispatch establishes its
     working directory with "work ONLY here" / "working ONLY inside this
     directory" language immediately before the path, which a passing mention
     does not have. (An earlier version of this check required literally
     "build" in the message's opening ~80 characters instead; that both
     missed genuine dispatches with a long preamble before the word "build"
     appeared, e.g. Stage-2's loop trials, AND -- more seriously -- silently
     accepted a session-limit-truncated duplicate as the sole match for 5
     Fable-5/tiko-mcp trials because the genuine completed dispatch happened
     to fail that check. If you're looking at output from a copy of this tool
     from before 2026-07-04, re-verify it.) Older, more loosely-named trial
     directories (bare `trial-01` rather than a longer cell-specific name)
     are more exposed to false matches in general -- double check results
     for those specifically if you extend this further.
  5. The lines of one response do NOT all carry its final usage. Early lines
     carry the stream-start snapshot: input/cache counts are final, but
     `output_tokens` is a placeholder (typically 3-16) and `stop_reason` is
     null. The final usage is only on a line with a non-null `stop_reason`.
     Keeping the first line (as this script did until 2026-10-11) undercounts
     output 2-33x. When no line has a stop_reason, only the placeholder exists;
     `calls_without_final_usage` counts those calls, so output for them -- and
     the trial's output total -- is a lower bound. Claude Code 2.1.296 records
     final usage for only ~15% of calls, so check that count before quoting
     output tokens. See results/pricing.md, "Erratum 2".

CAVEAT -- TRANSCRIPT RETENTION
-------------------------------
This only works while the subagent transcript still exists on disk. There is
no documented retention guarantee (though in practice, transcripts for this
project's two sessions survived from 2026-06-07 through at least 2026-07-03
without pruning). Run this accounting soon after each trial completes if you
want a guarantee; don't assume old trials in a different project can always
be reconstructed this way.

USAGE
-----
As a library:
    from token_accounting import find_transcript, sum_usage_recursive, cost
    hits = find_transcript(["<session-id-1>", "<session-id-2>"], "spring-fix", "trial-o-01")
    # hits is a list of (session, filename); disambiguate manually if len > 1
    session, fname = hits[0]
    usage = sum_usage_recursive([s for s, _ in hits], session, fname)
    print(cost("claude-opus-4-8", usage))

As a script: fill in the TRIALS dict below, then `python token-accounting.py`.
"""
import glob
import json
import os
import re
import sys

# Fill in per-run: {trial_name: (model_id, session_id, "agent-<id>.jsonl")}
# Find the (session_id, filename) with find_transcript() below, or manually:
#   grep -Fl "<cell>/<trial-dir-name>" ~/.claude/projects/<project>/*/subagents/*.jsonl
TRIALS = {
    # "s5-01": ("claude-sonnet-5", "155baf58-...", "agent-a2c3589fde9093ed4.jsonl"),
}

# $/1M tokens, regular (non-intro) output/input rates -- keep in sync with results/pricing.md
# Optional per model: "read" = cache-read multiplier on the input rate (default 0.1);
# "long" = rates for API calls whose prompt exceeds LONG_PROMPT_TOKENS (input +
# cache write + cache read of that call). Cache writes are always 1.25x (5m) / 2x (1h).
PRICES = {
    "claude-opus-4-8":   {"in": 5.00,  "out": 25.00},
    "claude-sonnet-4-6": {"in": 3.00,  "out": 15.00},
    "claude-fable-5":    {"in": 10.00, "out": 50.00},
    "claude-sonnet-5":   {"in": 3.00,  "out": 15.00},
    "claude-opus-5":     {"in": 5.00,  "out": 25.00},
    "claude-opus-5-5":   {"in": 4.00,  "out": 20.00, "read": 0.05},
    "claude-sonnet-5-5": {"in": 2.00,  "out": 10.00},
    # Cache multipliers for Haiku 5.5 aren't published separately; standard ones assumed.
    "claude-haiku-5-5":  {"in": 0.10,  "out": 0.50, "long": {"in": 0.50, "out": 2.50}},
}
LONG_PROMPT_TOKENS = 100_000
_TOKEN_KEYS = ("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens",
               "output_tokens", "ephemeral_1h")

AGENT_ID_RE = re.compile(r"agentId:\s*([a-f0-9]+)")


def _subagents_dir(projects_root, session):
    return os.path.join(projects_root, session, "subagents")


def _first_user_text(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            if obj.get("type") == "user":
                c = (obj.get("message") or {}).get("content")
                if isinstance(c, str):
                    return c
                if isinstance(c, list) and c and isinstance(c[0], dict):
                    return c[0].get("text", "")
                return ""
    return ""


def find_transcript(projects_root, sessions, cell, trial_name):
    """Search every subagent transcript across the given sessions for a path
    reference to `<cell>/<trial_name>` (tolerating '/', '\\', and the
    double-backslash JSON escaping Windows paths get on disk). Returns a list
    of (session, filename) -- more than one hit means disambiguate manually
    (see module docstring, gotcha #3).

    Two precision requirements, both required after gotcha #4 (see module
    docstring): the path must appear in the transcript's FIRST user message
    (the actual dispatch prompt establishing the working directory) -- not
    anywhere in the transcript, which can false-match a later grading/
    orchestration agent that enumerates many trial dirs, or a completely
    unrelated task that happens to reference the path in passing -- AND the
    word "only" must appear shortly before that path (within `only_window`
    characters). Every genuine build dispatch seen in this project establishes
    its working directory with "work ONLY here" / "working ONLY inside this
    directory" language immediately before the path; a task that merely
    *mentions* the path as context (e.g. "target project (already built...):
    <path>") does not. This is checked against EVERY occurrence of EVERY path
    spelling in the message, not just the first one found -- a naive
    first-match check can find an unrelated later mention (e.g. inside a shell
    command) before the real "working ONLY here" declaration and wrongly
    reject the file. An earlier, less precise version of this check --
    requiring literally "build" in the message's opening ~80 characters --
    both missed genuine dispatches whose preamble ran long before the word
    "build" appeared, and (separately) missed some genuine dispatches for
    unrelated reasons, silently leaving a session-limit-truncated duplicate as
    the only accepted match for 5 trials. Re-verify anything computed with a
    tool older than 2026-07-04 against this one."""
    candidates = [f"{cell}\\\\{trial_name}", f"{cell}/{trial_name}", f"{cell}\\{trial_name}"]
    only_window = 300
    hits = []
    for session in sessions:
        subdir = _subagents_dir(projects_root, session)
        if not os.path.isdir(subdir):
            continue
        for path in glob.glob(os.path.join(subdir, "*.jsonl")):
            try:
                text = _first_user_text(path)
            except (OSError, json.JSONDecodeError):
                continue
            matched = False
            for c in candidates:
                start = 0
                while True:
                    idx = text.find(c, start)
                    if idx == -1:
                        break
                    preceding = text[max(0, idx - only_window):idx].lower()
                    if "only" in preceding:
                        matched = True
                        break
                    start = idx + 1
                if matched:
                    break
            if matched:
                hits.append((session, os.path.basename(path)))
    return hits


def last_assistant_stop_reason(projects_root, session, fname):
    """Helper for disambiguating gotcha #3: returns the stop_reason of the
    final assistant turn, so you can prefer 'end_turn' (completed) over an
    interrupted run."""
    path = os.path.join(_subagents_dir(projects_root, session), fname)
    last = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            if obj.get("type") == "assistant":
                last = (obj.get("message") or {}).get("stop_reason")
    return last


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


def sum_usage_recursive(projects_root, sessions, session, agent_jsonl_filename, seen=None):
    """`sessions` is the full list of session ids to search when resolving a
    nested agent dispatch, which may land in a different session than its
    parent. `session` is where THIS transcript file lives."""
    seen = seen if seen is not None else set()
    path = os.path.join(_subagents_dir(projects_root, session), agent_jsonl_filename)
    tot = dict(input_tokens=0, cache_creation_input_tokens=0, cache_read_input_tokens=0,
               output_tokens=0, ephemeral_1h=0, api_calls=0, nested=0,
               calls_without_final_usage=0)
    # The same token counts, restricted to calls whose prompt exceeded LONG_PROMPT_TOKENS.
    tot.update({"long_" + k: 0 for k in _TOKEN_KEYS})
    if path in seen or not os.path.exists(path):
        return tot
    seen.add(path)

    lines = _load_lines(path)

    # Older Claude Code versions repeated the response's final usage on every line of a
    # message. Newer ones (seen in 2.1.296) write the stream-start snapshot -- placeholder
    # output_tokens, stop_reason null -- on each line and the final usage only sometimes,
    # on a last line that carries the stop_reason. Prefer that line; count the misses.
    by_msg_id, final, order = {}, set(), []
    for obj in lines:
        if obj.get("type") != "assistant":
            continue
        m = obj.get("message") or {}
        mid = m.get("id")
        u = m.get("usage")
        if not u or mid is None:
            continue
        if mid not in by_msg_id:
            order.append(mid)
        if mid not in final:
            by_msg_id[mid] = u
            if m.get("stop_reason") is not None:
                final.add(mid)

    for mid in order:
        u = by_msg_id[mid]
        call = dict(input_tokens=u.get("input_tokens", 0),
                    cache_creation_input_tokens=u.get("cache_creation_input_tokens", 0),
                    cache_read_input_tokens=u.get("cache_read_input_tokens", 0),
                    output_tokens=u.get("output_tokens", 0),
                    ephemeral_1h=(u.get("cache_creation") or {}).get("ephemeral_1h_input_tokens", 0))
        prompt = (call["input_tokens"] + call["cache_creation_input_tokens"]
                  + call["cache_read_input_tokens"])
        for k in _TOKEN_KEYS:
            tot[k] += call[k]
            if prompt > LONG_PROMPT_TOKENS:
                tot["long_" + k] += call[k]
        tot["api_calls"] += 1
        if mid not in final:
            tot["calls_without_final_usage"] += 1

    for nested_id in _find_nested_agent_ids(lines):
        nested_fname = f"agent-{nested_id}.jsonl"
        for candidate_session in sessions:
            if os.path.exists(os.path.join(_subagents_dir(projects_root, candidate_session), nested_fname)):
                sub = sum_usage_recursive(projects_root, sessions, candidate_session, nested_fname, seen)
                for k in tot:
                    tot[k] += sub[k]
                if sub["api_calls"] > 0:
                    tot["nested"] += 1
                break

    return tot


def _priced(u, rate_in, rate_out, read_mult):
    write_5m = u["cache_creation_input_tokens"] - u["ephemeral_1h"]
    write_1h = u["ephemeral_1h"]
    return dict(input=u["input_tokens"] * rate_in / 1e6,
                write=(write_5m * 1.25 + write_1h * 2.0) * rate_in / 1e6,
                read=u["cache_read_input_tokens"] * rate_in * read_mult / 1e6,
                output=u["output_tokens"] * rate_out / 1e6)


def cost(model, u):
    p = PRICES[model]
    read_mult = p.get("read", 0.1)
    if "long" in p and any(u.get("long_" + k) for k in _TOKEN_KEYS):
        long_u = {k: u["long_" + k] for k in _TOKEN_KEYS}
        short_u = {k: u[k] - long_u[k] for k in _TOKEN_KEYS}
        a = _priced(short_u, p["in"], p["out"], read_mult)
        b = _priced(long_u, p["long"]["in"], p["long"]["out"], read_mult)
        c = {k: a[k] + b[k] for k in a}
    else:
        c = _priced(u, p["in"], p["out"], read_mult)
    c["total"] = sum(c.values())
    return c


def main():
    projects_root = sys.argv[1] if len(sys.argv) > 1 else None
    if not projects_root:
        home = os.path.expanduser("~")
        default_root = os.path.join(home, ".claude", "projects", "<project>")
        print(f"No projects root given; pass it explicitly, e.g.:\n"
              f"  python token-accounting.py {default_root}\n"
              f"(the directory containing one subfolder per session-id)")
        return

    if not TRIALS:
        print("Fill in the TRIALS dict at the top of this file first.")
        return

    sessions = sorted({session for _model, session, _fname in TRIALS.values()})

    print(f"{'trial':8} {'model':18} {'calls':>5} {'nest':>4} {'in':>8} {'write':>9} {'read':>10} {'out':>7} "
          f"| {'$in':>6} {'$wr':>6} {'$rd':>6} {'$out':>6} {'$TOT':>7} | no-final-usage calls")
    for trial, (model, session, fname) in TRIALS.items():
        u = sum_usage_recursive(projects_root, sessions, session, fname)
        c = cost(model, u)
        print(f"{trial:8} {model:18} {u['api_calls']:5} {u['nested']:4} {u['input_tokens']:8} "
              f"{u['cache_creation_input_tokens']:9} {u['cache_read_input_tokens']:10} {u['output_tokens']:7} | "
              f"{c['input']:6.3f} {c['write']:6.3f} {c['read']:6.3f} {c['output']:6.3f} {c['total']:7.3f} | "
              f"{u['calls_without_final_usage']}/{u['api_calls']}")


if __name__ == "__main__":
    main()
