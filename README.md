# LLM Framework Benchmark

**How well can an AI coding agent build the *same specified system* on different frameworks?**
A framework-neutral spec, an external black-box oracle that grades the result, and
clean per-framework scaffolds — so the only variable is the framework (and the
agent). First comparison: **Spring Boot vs [Tiko DI](https://github.com/tomas-samek/tiko-di)**,
on an event-driven Kafka → H2 → merged-notification service.

The benchmark grades each run on **compliance** (does it actually pass the
acceptance scenarios, judged by an independent harness — not the agent's own
tests), with hooks for **efficiency** and **code quality**.

## Headline results — Stage 1

Model: **Claude Opus 4.8** (2026-06). N=5 per cell (spring3 is a single reference
run). Compliance = oracle scenarios passed / 7. Fixed fixtures, fresh broker per
trial. Full write-up: [`results/RESULTS.md`](results/RESULTS.md).

| Cell | Framework / version | Median compliance | Notes |
|---|---|---|---|
| spring | Spring Boot **4.0.6** | **0%** (1/5 correct) | brand-new major; model writes Boot-3 idioms that silently no-op |
| spring3 | Spring Boot **3.3.5** | **100%** (n=1, first try) | a version the model knows → nails it |
| tiko | Tiko **0.2.2** | **86%** | out-of-corpus framework; carried by bundled in-repo guidance |
| tiko-mcp | Tiko **0.2.2** + MCP | **86%** | identical to `tiko` here (see finding 3) |

### What it found

1. **Version recency dominates.** The *same model* scored 100% on Spring Boot 3.3.5
   and a median **0%** on 4.0.6. A single major-version bump — not a worse model,
   not a worse framework — caused it. The dangerous part is *silent* failure:
   Boot-4 apps compiled, ran, and emitted nothing. Even the current frontier model
   shows this; it's structural to training-on-historical-corpus, and the lag is
   *longer* than "time since cutoff" because the old version dominates the corpus.

2. **An unknown framework that ships its own docs can beat a known framework's
   unknown new version.** Tiko (essentially absent from training) reached 86%
   median because its scaffold ships machine-readable guidance the agent reads at
   build time; bare Boot-4 shipped neither training familiarity nor docs.

3. **The MCP topology server showed no compliance lift *here*** — `tiko` and
   `tiko-mcp` were identical (`0 0 86 86 100`). The pilot's apparent +57 pts was a
   fixture-timing artifact. Caveat: the validation gate targeted *wiring*, which the
   model already got right; it did not target *config*, which is where Tiko actually
   failed — so this is "MCP-for-wiring = no lift," not a general verdict.

4. **Tiko's main AI-friendliness issue is strict config validation** — 4/10 Tiko
   trials died at startup because the model used kebab-case / Spring-style config
   keys that Tiko's `@Configuration` records reject. The single most actionable fix.

> These are directional results: small N, one model, one point in time, and some
> early prompts carried hints. See `results/RESULTS.md` → "Caveats / validity".

## How it works

- **Framework-neutral spec** (`docs/specs/stage-1-spec.md`) — describes *what* to
  build, with zero framework words.
- **External conformance oracle** (`conformance/oracle/`, Java) — brings up Kafka,
  publishes the shared fixtures (`fixtures/stage-1/scenarios.json`), drains the
  output topic, and asserts the result. The agent's own tests don't count.
- **Asymmetric-native fairness** — each framework uses its own first-party stack;
  neither may copy from example repos. Tiko, having no first-party DB/HTTP module,
  must hand-build those. Full rules in `docs/benchmark-protocol.md`.
- **Pinned substrate** — apache/kafka 3.8.0, H2 2.2.224, Lucene 9.11.1, JDK 21,
  identical for everyone, so the only variable is the framework + the agent.

## Run it — including with other agents

See **[`RUNNING.md`](RUNNING.md)** for the canonical, neutral per-trial procedure:
copy a golden scaffold, hand the agent only the spec + dependency rules (no hints),
let it build, then grade with the oracle. The procedure is agent-agnostic — point
Claude, GPT, Gemini, Cursor, etc. at the same scaffold + spec and compare. Running
it across agents is the natural next step.

## Layout

```
docs/   benchmark-protocol.md (rules) + specs/ (framework-neutral WHAT)
fixtures/   shared acceptance vectors (input -> expected output)
conformance/   oracle (Java) + docker-compose Kafka + run scripts
scaffolds/   golden per-framework starting points (+ GENERATE.md)
results/   metrics.csv + per-trial oracle JSON + RESULTS.md
runs/   per-trial workspaces (git-ignored; see runs/README.md)
```

## Status

- [x] Protocol, Stage-1 spec, fixtures, conformance oracle (tests green)
- [x] Golden scaffolds (Spring Boot 4.0.6 + 3.3.5 reference; Tiko 0.2.2)
- [x] Stage-1 run: spring, tiko, tiko-mcp (5x) + spring3 reference — `results/RESULTS.md`
- [ ] Run with non-Claude agents
- [ ] Capture efficiency + code-quality rubric; n=10
- [ ] Stage-2 spec (full-text search)

## License

MIT — see [`LICENSE`](LICENSE).
