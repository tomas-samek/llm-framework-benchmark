# Stage-1 pilot notes (1 Spring + 1 Tiko)

**Date:** 2026-06-08
**Versions:** Spring Boot 4.0.6 (Initializr default); Tiko 0.2.2 (`tiko-archetype:0.2.2`).
**Substrate:** apache/kafka:3.8.0, H2, JDK 21. Graded by `conformance/oracle` against
`fixtures/stage-1/scenarios.json` (AC1–AC6 → 7 expectations).

## Purpose
Validate the end-to-end pipeline (golden scaffold → isolated contestant build →
external oracle grade) before committing to the full 5+5 run. **Pipeline validated.**

## Results

| Trial | Compliance | Detail file |
|---|---|---|
| spring trial-01 | **0% (0/7)** | `results/stage-1-spring-01.json` |
| tiko trial-01 | **29% (2/7)** | `results/stage-1-tiko-01.json` |

### Spring trial-01 — 0%
On Spring Boot 4.0.6, `@KafkaListener` auto-configuration moved into
`spring-boot-starter-kafka`. The contestant declared plain `spring-kafka` (the
Boot-3 idiom), so `@EnableKafka`/the listener post-processor never loaded; the
consumer group never registered and **no notifications were emitted**. A
version-knowledge gap: the model produced Boot-3-style wiring against a Boot-4
scaffold. Likely to recur across Spring trials at 4.0.6.

### Tiko trial-01 — 29%
The full pipeline ran (consume → merge → emit), but reference enrichment was not
wired: `userName`/`productName` came back null and a price update didn't take
effect (AC6 buyB used the stale price). AC2/AC3 pass because their expected names
are null anyway. The contestant flagged the smell itself: the `product/user/price`
`@KafkaSource` handlers returned events with **no downstream handler**, so the
upserts to H2 effectively didn't happen for the lookups the purchase handler reads.

## Caveats affecting validity (read before scaling)
1. **Spring measures Boot-4 unfamiliarity**, not purely Spring-vs-Tiko ergonomics,
   at version 4.0.6 (chosen deliberately as the as-shipped Initializr default).
2. **Tiko is measured below its as-shipped capability:** per protocol §6 a Tiko
   trial assumes the bundled MCP topology server is reachable, but contestant
   subagents in this environment cannot connect to it — they used only the static
   `.ai-skills`/`CLAUDE.md` + `javap`. Protocol §6 would *void* such a trial. To
   measure Tiko fairly, run Tiko trials in an environment where the project MCP
   server connects.

## Status
- Full 5+5 run **paused** by decision after the pilot.
- Harness, oracle, scaffolds, run scripts: complete and committed.
- To resume: run trials NN=02..05 per framework (copy golden scaffold → contestant
  build → `conformance/stage-1` grade), then aggregate median + spread per protocol §8.
