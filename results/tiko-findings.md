# Tiko AI-friendliness findings (from the Stage-1 benchmark) → tiko-di issues

What the benchmark (3 models × 16 Stage-1 trials, external-oracle graded, plus
per-build token telemetry) revealed about building on Tiko with an AI agent — and
how each finding maps to an **already-filed** `tomas-samek/tiko-di` issue.

The benchmark does not surface new bugs; nearly everything below is already tracked
(and the closed #340–#345 "AI-friendliness / DX hardening" cluster shows it's an
active line of work). Its contribution is **quantified corroboration**: which issues
cost the most tokens and cause the most outright failures, across models.

## Findings, ranked by benchmark impact

### 1. Kafka API is documented only in source javadoc — not surfaced  → #311
The `@KafkaSource`/`@KafkaSink` contract (must be `@Component(SINGLETON)`; source
needs a sibling `@EventTrigger`; sink takes one event, returns payload, must NOT be
`@EventHandler`; `topic`/`consumerGroup`/`serializer`/`commitMode` elements) is well
written in `KafkaSource.java`'s javadoc, but absent from `docs/`, `docs/cookbooks/`
(the Kafka recipe is an explicit TODO), and there is no `tiko-kafka/README.md`.
**Evidence:** every Tiko contestant reverse-engineered the contract via `javap` on the
jars; Tiko builds cost ~1.6–3× the tokens of the Spring builds (e.g. Opus tiko 113k vs
spring 71k vs spring3 51k), a large share of it API discovery.
**Fix:** fill the Kafka cookbook (#311) + a module README; lift the content from the
existing javadoc.

### 2. Poison record → infinite seek-back; no skip/DLT hook  → #313
`ThreadPerTopicRunner` re-seeks the same offset forever on a deserialize throw.
**Evidence:** *every* Tiko contestant independently discovered this (by reading runner
source) and invented a lenient/raw-serializer workaround that returns null + skips —
to satisfy the spec's "log and skip." Pure undocumented-gotcha cost, and the riskiest
correctness trap.
**Fix:** first-class skip/dead-letter hook (#313); document the pattern until then.

### 3. `topology.json` omits Kafka source/sink edges  → #312
**Evidence:** MCP `trace_event_flow` reported `Notification` "terminal / no handler";
contestants had to read generated `KafkaTransportBootstrap.java` to confirm the path.
This is *why the MCP gate added cost without a compliance lift* — it can't validate the
very wiring (Kafka transport) this app is built around.
**Fix:** emit Kafka edges into `topology.json` (#312).

### 4. Config binding rejects kebab-case / Spring-isms; no did-you-mean  → #310
**Evidence:** the single biggest *outright-failure* cause. ≥4 trials scored 0% at
startup from config-key mismatches — kebab `db.pool-size` vs field `poolSize`,
Spring-style top-level `kafka:`, missing required `app:` section, unknown `app.*`
prefixes. The model's defaults (kebab-case YAML, Spring conventions) hit Tiko's
exact-key validation hard at boot.
**Fix:** accept kebab-case and/or `ConfigValidationException` that names the expected
key + nearest match; ship a 1:1 config example (#310).

### 5. `EventBus`/`Container` not injectable; keep-alive idiom varies  → #314
**Evidence:** contestants used three different "keep the app alive" idioms
(`Thread.join()`, `CountDownLatch`, `Tiko.daemon()`); at least one was surprised
`EventBus`/`Container` aren't constructor-injectable (the CLAUDE.md example implied it).
**Fix:** one ergonomic injectable publish + a canonical keep-alive (#314).

### 6. `tiko-kafka` / `tiko-kafka-processor` missing from the BOM  → #298
**Evidence:** contestants had to pin explicit `${tiko.version}` on the Kafka deps
because they aren't in `tiko-bom` `dependencyManagement` — extra friction in every
Tiko trial.
**Fix:** add them to the BOM (#298).

## The broader point (raised reviewing the results)
The friction is concentrated at the **"plug in a library via `@Produces`" seam** —
the place Tiko's whole model says "you bring HTTP/DB/cache/Kafka." That seam needs to
be the *most* obvious, discoverable thing, because it's where every real app lives:
- **DataSource/DB:** `docs/cookbooks/persistence.md` exists, and DB was *not* a major
  failure point (agents hand-built JDBC fine) — so this seam is in better shape than Kafka.
- **Kafka:** the worst-documented seam (findings 1–3), and the one the benchmark hammered.
- A cross-cutting "integration seam" index (one page: here's the `@Produces` pattern,
  here are the cookbook recipes per library) would make the model reach for the right
  pattern instead of exploring. Partly served by the `tiko-build` skill; not a single
  discoverable doc.

## What the benchmark adds that the issues don't have
- **Failure attribution:** config strictness (#310) is the top *0%* cause; poison/Kafka
  the top *token* cost.
- **Cross-model confirmation:** the friction reproduces on Sonnet 4.6, Fable 5, and
  Opus 4.8 — it's framework-ergonomic, not a one-model quirk.
- **MCP value, measured:** the topology server gave **no median compliance lift** and,
  for Opus, modestly *higher* token cost — strengthening #312 (the Kafka blind spot is
  exactly why the gate couldn't pay off on this app shape).

## Recommended next actions
1. Add the benchmark evidence as comments on **#310, #311, #312, #313, #314** (priority
   signal + token/failure numbers).
2. Draft the Kafka cookbook (closes #311), lifting the contract from javadoc + the
   poison-message pattern + the config-key conventions.
3. Optional: run a `tiko-docs` benchmark arm (scaffold with the cookbook baked in) to
   *measure* the token reduction and failure-tail change — closing the loop empirically.
