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

## MCP-assisted Tiko re-run (new category `tiko-mcp`)

`jbang` was installed and the `tiko-mcp:0.2.2` topology server confirmed working
(13 read-only tools: `topology_overview`, `trace_event_flow`, `list_events`,
`explain_wiring`, `list_wiring_errors`, …; no MCP resources). The `tiko-mcp`
contestant got the same spec/isolation/deps as `tiko`, plus the topology server
(invoked via a jbang one-shot stdio recipe) and a requirement to validate its
wiring before finishing.

| Category | trial-01 | detail file |
|---|---|---|
| spring | 0% (0/7) | `results/stage-1-spring-01.json` |
| tiko (no mcp) | 29% (2/7) | `results/stage-1-tiko-01.json` |
| **tiko (mcp)** | **86% (6/7)** | `results/stage-1-tiko-mcp-01.json` |

**Finding:** the topology server is worth ~+57 points here. The no-mcp run shipped a
reference-enrichment flow that silently didn't wire (null `userName`/`productName`);
the mcp run used `trace_event_flow`/`topology_overview`, saw the gap, added the
`*Upserted` reference handlers, and verified the flow before declaring done.

**Caveat — AC6/buyB:** both Tiko runs emitted the stale price for buyB (refresh
`7999→8999` not applied in time). Cross-topic ordering (price-updates vs purchases)
is unguaranteed and the fixture allows only a 3s settle, so this is likely harness
timing sensitivity rather than a pure app defect. Candidate fix: lengthen the settle
before the refresh purchase in AC6, or have the oracle confirm the 2nd price update
was consumed before publishing buyB.

**Validity:** n=1 per category — directional, not statistically robust. The MCP was
delivered via a CLI one-shot, not an always-on session MCP client (a faithful
approximation; results identical since it's the same server over the same metadata).
