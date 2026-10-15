# Code-quality judge prompt

Give this prompt verbatim to each judge, in a fresh session whose working directory is the
blinded bundle built by `conformance/quality/make_bundle.py` (see `RUBRIC.md` →
Procedure). The judge reads the files itself with read-only tools; it must not be able to
build, run or edit anything. Replace `<RUBRIC>` with the full text of `RUBRIC.md`.

```
You are reviewing the code of one implementation of a specified service. Score it against
the rubric that follows. You see only the code; you don't know who wrote it or whether it
passed any tests, and you must not guess.

The project is in the current directory. SPEC.md is the specification. The code is
pom.xml and src/, plus any README and compose file. For a Tiko project,
framework-guidance/ holds the framework's own documentation that shipped with the
project; use it to judge idiomatic use. Read every source file before scoring. Do not
build, run or modify anything.

Rules:
- Score each of the six dimensions 1-5 using the rubric's anchors. Use 2 or 4 when the
  code sits between two anchors.
- Back every score with evidence: a file path and line number, or a named class or method.
  A score without evidence is invalid.
- Judge idiomaticity against the framework's own guidance, as the rubric describes. Do not
  prefer one framework over another.
- For defect risk, work through every listed check and say what you found for each.
- Do not run the code. Do not reward length, comments or documentation for their own sake.

Reply with JSON only, in this shape:
{"idiomaticity": {"score": n, "evidence": "..."},
 "structure": {"score": n, "evidence": "..."},
 "defect_risk": {"score": n, "evidence": "...", "checks": {"upsert": "...", "null_path": "...", "one_notification": "...", "no_blocking": "...", "concurrency": "...", "resources": "..."}},
 "error_handling": {"score": n, "evidence": "..."},
 "run_surface": {"score": n, "evidence": "..."},
 "test_quality": {"score": n, "evidence": "..."}}

<RUBRIC>
```
