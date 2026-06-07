# Code-quality review rubric

Score each dimension 1-5 (5 best). One review per trial, stored as
`results/reviews/stage-<s>-<framework>-<trial>.md`. If using LLM judges, run >=3
and record the median per dimension. Framework is identifiable from imports;
score against the rubric, not against framework preference.

| Dimension | 1 | 3 | 5 |
|---|---|---|---|
| Idiomaticity | fights the framework | mostly idiomatic | clean, idiomatic use |
| Structure / cohesion | tangled, god-classes | reasonable split | focused, single-responsibility units |
| Defect risk | clear bugs / races | minor issues | none spotted |
| Error handling | crashes on bad input | partial | poison-safe, never stalls (per spec §6) |
| Test quality | none / trivial | covers happy path | covers merge + null + poison paths |

Only compare trials at equal external compliance (see protocol §8).
Record: dimension scores, total, and a 3-5 sentence justification.
