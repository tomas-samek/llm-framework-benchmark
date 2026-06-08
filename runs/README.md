# runs/ — per-trial contestant workspaces (not committed)

Each benchmark trial runs in its own directory here, created by copying a golden
scaffold from `../scaffolds/` (see [`../RUNNING.md`](../RUNNING.md)):

```
runs/stage-1/<framework>/trial-NN/
```

These workspaces (the code an agent under test produces) are **intentionally
git-ignored** for two reasons:

1. **No answer key.** Publishing one agent's solution would contaminate later
   runs by other agents — a fair trial must see only the spec + a fresh scaffold.
2. **Size.** A populated trial includes Maven `target/` output (hundreds of MB).

The *outcomes* of runs are captured in [`../results/`](../results/)
(`metrics.csv` + per-trial oracle JSON + `RESULTS.md`).
