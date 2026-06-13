# LLM Framework Benchmark

**How well can an AI coding agent build the *same specified system* on different frameworks?**
A framework-neutral spec, an external black-box oracle that grades the result, and
clean per-framework scaffolds — so the only variable is the framework (and the
agent). First comparison: **Spring Boot vs [Tiko DI](https://github.com/tomas-samek/tiko-di)**,
on an event-driven Kafka → H2 → merged-notification service.

The benchmark grades each run on **compliance** (does it actually pass the
acceptance scenarios, judged by an independent harness — not the agent's own
tests), with hooks for **efficiency** and **code quality**.

> **Note on Tiko — intentionally unknown to LLMs.** Tiko DI is *purposefully*
> developed and used as an **out-of-training-corpus** framework. Its premise is
> that a framework can be AI-friendly **without** relying on the model's prior
> familiarity — instead shipping machine-readable, in-repo affordances:
> guidance (`CLAUDE.md`, `.ai-skills/`), compile-time wiring validation, and an
> MCP topology server. So in this benchmark Tiko is a deliberate test case for the
> question *"can an agent succeed on a framework it was never trained on, given
> good in-repo affordances?"* — which is also why it makes a fair foil for a
> brand-new, equally-unfamiliar Spring Boot **4.0.6**. (Stage-1 answer: largely
> yes — 86% median, vs Boot-4's 0%.)

## Headline results — Stage 1

The sharpest result is **version recency**, isolated cleanly: same spec, same fixed
scaffold, the **only** change is the Spring Boot version. Graded by the external oracle
(compliance = scenarios passed / 7), fresh broker per trial, contestants run as isolated
agents on the named model. Full write-up: [`results/RESULTS.md`](results/RESULTS.md).

| Spring Boot version | Claude Sonnet 4.6 | Claude Opus 4.8 |
|---|---|---|
| **3.3.5** — in the training corpus | **100%** (3/3) | **100%** (3/3) |
| **4.0.6** — current major (~6 months old) | **0%** (0/5) | **0%** (0/5) |

A cheap model *and* the frontier reasoning model both fail the new major completely —
while both ace the version they know. (This fixed-scaffold re-run supersedes an earlier
core-only run that under-reported the effect; see `results/RESULTS.md` → "Clean re-run"
for the reconciliation and the high-variance caveat on the Boot-4 number.)

On the other axis — an out-of-training-corpus framework that ships in-repo agent docs
(Sonnet 4.6 contestants, N=5):

| Framework | Median compliance | Notes |
|---|---|---|
| Tiko **0.2.2** | **86%** | out-of-corpus; carried by bundled in-repo guidance |
| Tiko **0.2.2** + MCP | **86%** | identical to plain Tiko here (see finding 3) |

### What it found

1. **Version recency dominates — and a bigger model doesn't fix it.** The same spec
   scored **100% on Spring Boot 3.3.5 and 0% on 4.0.6 — for *both* Sonnet 4.6 and
   Opus 4.8.** Not a worse model (the frontier reasoning model failed too), not a worse
   framework — one major-version bump. The failure is *silent and idiomatic*: every
   Boot-4 build compiled, but the models reflexively wrote the **Boot-3 Kafka idiom**
   (bare `spring-kafka` + trust auto-configuration) that Boot 4 reorganized away — so
   apps either failed to start (no `KafkaTemplate` bean) or booted and exited consuming
   nothing. None of 10 clean-run trials reached for the new `spring-boot-starter-kafka`
   that fixes it. Recency bites at *version choice* (given freedom, the models downgrade —
   Sonnet 5/5) **and** at *execution* (forced onto the new major, they use the old
   major's muscle memory).

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

> These are directional results: small N and a few points in time. The Boot-4
> compliance *number* is high-variance (success hinges on whether the model hand-wires
> Kafka vs. trusts autoconfig) — the robust finding is the qualitative gap, reproduced
> across 26 Spring trials, two models, and two scaffolds. See `results/RESULTS.md` →
> "Clean re-run" and "Caveats / validity".

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
- [x] Clean Spring re-run, fixed scaffold, Sonnet 4.6 + Opus 4.8 (version-recency isolated)
- [ ] Run with non-Claude agents
- [ ] Capture efficiency + code-quality rubric; n=10
- [ ] Stage-2 spec (full-text search)

## License

MIT — see [`LICENSE`](LICENSE).
