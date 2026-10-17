"""Aggregate code-quality judgments (RUBRIC.md, Procedure 3-4) and report judge agreement.

Inputs: a judging directory with
  key.json                   {"case-01": "<cell>/trial-<prefix>-<NN>", ...}  (kept away from judges)
  out/<case>-<judge>.json     Claude CLI output (--output-format json)
  out/<case>-<judge>.last.txt Codex last message (-o)
Each judgment is the rubric's JSON (six dimensions, score + evidence).

Usage:
  python conformance/quality/aggregate_reviews.py <judging-dir> <judge> [<judge> ...]          # report only
  python conformance/quality/aggregate_reviews.py <judging-dir> <judge> ... --write            # also write
      results/reviews/stage-1-<cell>-<prefix>-<NN>.json and rows in results/code-quality.csv
"""
import csv
import glob
import itertools
import json
import os
import re
import statistics
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DIMS = ["idiomaticity", "structure", "defect_risk", "error_handling", "run_surface", "test_quality"]
CORE = DIMS[:5]
MODEL_OF_PREFIX = {"o55": "claude-opus-5-5", "s55": "claude-sonnet-5-5", "h55": "claude-haiku-5-5",
                   "f5": "claude-fable-5", "f5s": "claude-fable-5", "f5o": "claude-fable-5",
                   "f51": "claude-fable-5-1",
                   "ast": "gpt-6-astra", "sol": "gpt-6.1-sol", "lun": "gpt-6-luna"}
CSV_FIELDS = ["cell", "model", "trial", "compliance_v2", "idiomaticity", "structure", "defect_risk",
              "error_handling", "run_surface", "core_total", "test_quality", "judges"]


def parse_json(text):
    """The judgment JSON. If a judge's JSON is malformed (e.g. one brace too many after the
    nested "checks" object), fall back to reading each dimension's score directly and keep
    the raw text as the record."""
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        return None
    try:
        j = json.loads(m.group(0))
        if all(isinstance(j.get(d), dict) and "score" in j[d] for d in DIMS):
            return j
    except ValueError:
        pass
    # Malformed or misnested (a stray brace can nest one dimension inside another).
    scores = {d: re.search(r'"%s"\s*:\s*\{\s*"score"\s*:\s*(\d)' % d, text) for d in DIMS}
    if all(scores.values()):
        return {d: {"score": int(s.group(1)), "evidence": "(malformed JSON; see raw)"} for d, s in scores.items()} \
            | {"raw": text}
    return None


def load(jdir, case, judge):
    """Return (scores dict, full judgment, cost info) or None."""
    base = os.path.join(jdir, "out", f"{case}-{judge}")
    if os.path.exists(base + ".json"):
        try:
            r = json.load(open(base + ".json", encoding="utf-8"))
        except ValueError:  # still running, or the session failed
            return None
        if r.get("is_error"):
            return None
        j = parse_json(r.get("result", ""))
        cost = {"usd": round(r.get("total_cost_usd", 0), 4), "turns": r.get("num_turns")}
    elif os.path.exists(base + ".last.txt"):
        j = parse_json(open(base + ".last.txt", encoding="utf-8").read())
        usage = None
        if os.path.exists(base + ".jsonl"):
            for line in open(base + ".jsonl", encoding="utf-8"):
                e = json.loads(line)
                if e.get("type") == "turn.completed":
                    usage = e["usage"]
        cost = {"tokens": usage}
    else:
        return None
    if not j or any(not isinstance(j.get(d), dict) or "score" not in j[d] for d in DIMS):
        return None
    return {d: int(j[d]["score"]) for d in DIMS}, j, cost


def compliance_v2(cell, prefix, nn):
    p = os.path.join(REPO, "results", f"stage-1-{cell}-{prefix}-{nn}.v2.json")
    return json.load(open(p))["passed"] if os.path.exists(p) else ""


def main(argv):
    write = "--write" in argv
    argv = [a for a in argv if a != "--write"]
    jdir, judges = argv[0], argv[1:]
    key = json.load(open(os.path.join(jdir, "key.json"), encoding="utf-8"))
    rows, spreads, pairs = [], {d: [] for d in DIMS}, {}
    for case, trial in sorted(key.items()):
        got = {j: load(jdir, case, j) for j in judges}
        missing = [j for j, v in got.items() if v is None]
        ok = {j: v for j, v in got.items() if v is not None}
        cell, tname = trial.split("/")
        prefix, nn = re.match(r"trial-([a-z0-9]+)-(\d\d)", tname).groups()
        med = {d: statistics.median(v[0][d] for v in ok.values()) for d in DIMS} if ok else {}
        flags = [d for d in DIMS if ok and max(v[0][d] for v in ok.values()) - min(v[0][d] for v in ok.values()) >= 3]
        for d in DIMS:
            if len(ok) >= 2:
                spreads[d].append(max(v[0][d] for v in ok.values()) - min(v[0][d] for v in ok.values()))
        for a, b in itertools.combinations(sorted(ok), 2):
            for d in DIMS:
                pairs.setdefault((a, b), []).append(abs(ok[a][0][d] - ok[b][0][d]))
        core = sum(med[d] for d in CORE) if med else None
        print(f"{case} {trial:28} " + " ".join(f"{j}={''.join(str(ok[j][0][d]) for d in DIMS) if j in ok else 'MISSING'}" for j in judges)
              + (f"  median core={core:g} tests={med['test_quality']:g}" if med else "")
              + (f"  RE-REVIEW {flags}" if flags else ""))
        rows.append(dict(case=case, cell=cell, prefix=prefix, nn=nn, ok=ok, med=med, core=core,
                         flags=flags, missing=missing))
    print("\nscore order per judge: " + " ".join(DIMS))
    print("\nAgreement per dimension (spread = max - min across judges):")
    for d in DIMS:
        s = spreads[d]
        if s:
            print(f"  {d:15} mean spread {statistics.mean(s):.2f}  within 1: {sum(x <= 1 for x in s)}/{len(s)}"
                  f"  spread>=3: {sum(x >= 3 for x in s)}")
    print("\nPairwise mean |difference| over all dimensions:")
    for (a, b), diffs in pairs.items():
        print(f"  {a} vs {b}: {statistics.mean(diffs):.2f}  exact {sum(x == 0 for x in diffs)}/{len(diffs)}"
              f"  within 1 {sum(x <= 1 for x in diffs)}/{len(diffs)}")
    costs = {}
    for r in rows:
        for j, v in r["ok"].items():
            costs.setdefault(j, []).append(v[2])
    print("\nCost per judgment:")
    for j, cs in costs.items():
        if "usd" in cs[0]:
            print(f"  {j}: median ${statistics.median(c['usd'] for c in cs):.3f}, total ${sum(c['usd'] for c in cs):.2f}")
        elif cs[0].get("tokens"):
            t = [c["tokens"] for c in cs if c.get("tokens")]
            print(f"  {j}: median input {statistics.median(x['input_tokens'] for x in t)/1e3:.0f}K, "
                  f"output {statistics.median(x['output_tokens'] for x in t)/1e3:.1f}K tokens")
    if write:
        out_rows = []
        for r in rows:
            if r["missing"] or r["flags"]:
                print(f"not written: {r['case']} (missing {r['missing']}, re-review {r['flags']})")
                continue
            rec = {"cell": r["cell"], "model": MODEL_OF_PREFIX.get(r["prefix"], r["prefix"]),
                   "trial": f"{r['prefix']}-{r['nn']}", "judges": {j: v[1] for j, v in r["ok"].items()},
                   "median": r["med"], "core_total": r["core"]}
            p = os.path.join(REPO, "results", "reviews", f"stage-1-{r['cell']}-{r['prefix']}-{r['nn']}.json")
            json.dump(rec, open(p, "w", encoding="utf-8"), indent=1)
            out_rows.append({"cell": r["cell"], "model": rec["model"], "trial": rec["trial"],
                             "compliance_v2": compliance_v2(r["cell"], r["prefix"], r["nn"]),
                             **{d: r["med"][d] for d in DIMS}, "core_total": r["core"],
                             "judges": "+".join(sorted(r["ok"]))})
        path = os.path.join(REPO, "results", "code-quality.csv")
        existing = list(csv.DictReader(open(path, encoding="utf-8"))) if os.path.exists(path) else []
        keyset = {(x["cell"], x["model"], x["trial"]) for x in out_rows}
        merged = [x for x in existing if (x["cell"], x["model"], x["trial"]) not in keyset] + out_rows
        with open(path, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=CSV_FIELDS, lineterminator="\r\n")
            w.writeheader()
            w.writerows(merged)
        print(f"wrote {len(out_rows)} reviews; {path} has {len(merged)} rows")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1:])
