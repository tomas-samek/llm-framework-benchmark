# Stage 3 starting codebases

Stage 3 (`docs/specs/stage-3-spec.md`) gives every contestant the same working Stage 1
build to extend, one per framework:

| Folder | Source trial | Stage 1 result | Code quality (core / tests) |
|---|---|---|---|
| `spring41/notify` | `spring-411/trial-ast-02` (gpt-6-astra) | 7/7 on v1 and v2 | 22 / 4 |
| `tiko6/notify` | `tiko-060/trial-ast-09` (gpt-6-astra) | 7/7 on v1 and v2 | 23 / 4 |

These are the highest-scoring passing builds in the code-quality sample that also have
solid tests, with one author for both frameworks. Copied as built, minus build output,
logs and the trial's own Maven repository. The Stage 1 spec ships as `SERVICE-SPEC.md`,
and each trial adds the Stage 3 spec as `TASK-SPEC.md`.

Caveat: gpt-6-astra wrote this code, so in Stage 3 Astra extends code from its own model.
