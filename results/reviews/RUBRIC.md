# Code-quality review rubric (Stage 1)

This rubric scores the code a contestant wrote for the Stage 1 service (Kafka → H2 →
merged notification, `docs/specs/stage-1-spec.md`). It sits beside the external oracle,
which only checks observable behavior. Protocol: `docs/benchmark-protocol.md` §8.3.

**Compare quality only at equal compliance.** A trial that fails the oracle can still be
reviewed, but its quality score is reported next to its compliance, never pooled with
passing trials.

## Dimensions and scale

Six dimensions, each scored 1–5 (5 best). Anchors are given for 1, 3 and 5; score 2 or 4
when a trial sits between two anchors. Every score needs evidence: a file and line, or a
named construct.

The **core total** is the sum of dimensions 1–5 (5–25). **Test quality** (6) is reported
separately and kept out of the total, because the contestant prompt asks for a green build
with `-DskipTests` and never asks for tests.

### 1. Idiomaticity

Does the code use its framework the way that framework's own documentation intends?

| Score | Anchor |
|---|---|
| 1 | Fights the framework: bypasses its wiring or configuration, hand-rolls what a first-party module provides, or copies patterns from another framework. |
| 3 | Mostly idiomatic, with a few workarounds that a framework user would question. |
| 5 | Clean, idiomatic use throughout; nothing a maintainer of that framework would rewrite. |

Judge each framework against its own guidance, not against the other:
- **Spring Boot 4:** first-party starters and auto-configuration
  (`spring-boot-starter-kafka`, Jackson 3 via `tools.jackson`), `@KafkaListener`,
  configuration in `application.properties`/`yaml`, constructor injection. Hand-built
  `@EnableKafka` factory beans are legitimate but less idiomatic than the starter.
- **Tiko:** the scaffold's own guidance (`CLAUDE.md`, `.ai-skills/`), its
  `@KafkaSource`/`@KafkaSink`/`@Produces` model, `tiko-config` for configuration, and raw
  JDBC over a pooled `DataSource`. Tiko has no first-party DB module, so hand-built JDBC
  is expected, not a deduction.

### 2. Structure and cohesion

| Score | Anchor |
|---|---|
| 1 | Tangled: one god class or main method does consuming, storage, merging and publishing; duplicated logic. |
| 3 | A reasonable split with some mixing, e.g. SQL inside the Kafka listener, or merge logic duplicated per topic. |
| 5 | Focused units with one job each: message types, reference store, merge logic, Kafka adapters, configuration. The merge logic is a pure function that is easy to test. |

### 3. Defect risk

Problems the oracle may not catch. Check in particular:
- **Upsert semantics (§4):** insert-or-replace by primary key (`MERGE`, `ON CONFLICT`, or
  a correct find-then-save). A plain `INSERT` that fails on redelivery is a defect.
- **Null path (§5):** unknown user, product or price gives null fields and
  `totalCents = null`, never an exception, a zero, or a dropped purchase. `totalCents`
  is computed only when the price is known, without integer overflow on large values.
- **Exactly one notification per purchase, keyed by `purchaseId` (§5.3).** No
  duplicates from retries or from sending inside a loop.
- **No blocking on reference data (§5):** no sleeps, polling or waiting for a reference
  row before emitting.
- **Concurrency:** shared state (connections, caches, mappers) is thread-safe when
  listeners run on several threads; no lost updates across topics.
- **Resources:** connections and statements are closed (try-with-resources or a pool);
  the producer is flushed and closed on shutdown.

| Score | Anchor |
|---|---|
| 1 | A clear bug or race that would break the spec under some input or timing. |
| 3 | Minor issues only, e.g. an unclosed resource on an error path, or a non-atomic upsert that is safe at this load. |
| 5 | Nothing found after checking every item above. |

### 4. Error handling (§6)

| Score | Anchor |
|---|---|
| 1 | A malformed message crashes the app or stalls its topic; or every error is swallowed silently. |
| 3 | Malformed messages are skipped, but with gaps: a retry loop before skipping (a stall the v2 fixtures can catch), a broad catch that also hides real failures, or no log line. |
| 5 | A malformed message on any topic is logged and skipped at once, and a failure handling one message never stops consumption. Errors are logged with enough context (topic, partition, offset or key) to diagnose. Deserialization failures are told apart from processing failures. |

### 5. Run surface and operability (§7)

The spec requires these; the oracle checks none of them.
- One runnable application that starts and **shuts down cleanly** (closes consumers,
  producer and store).
- A **sample-data publisher** as a separate entry point.
- A **`docker-compose`** file for a single-node broker on `localhost:9092`.
- **Externalized configuration:** Kafka bootstrap and H2 location come from a config file
  and/or environment variables, not from constants in the code.

| Score | Anchor |
|---|---|
| 1 | Two or more of these are missing, or configuration is hard-coded throughout. |
| 3 | All present, with one weak spot, e.g. bootstrap externalized but the H2 path hard-coded, or no clean shutdown. |
| 5 | All present and solid: a clean shutdown path, fully externalized configuration with sensible defaults, and a publisher that sends the spec's example data. |

### 6. Test quality (reported separately)

| Score | Anchor |
|---|---|
| 1 | No tests, or only the scaffold's context test. |
| 3 | Tests cover the happy path: a merge with all reference data present. |
| 5 | Tests cover the merge, the null path (each reference missing), the poison path, and the latest upsert winning, without a real broker (fakes or an embedded broker) and fast enough to run on every build. |

## Measured signals

`conformance/quality/code_metrics.py` records facts about each trial before review. They
inform the judges and are reported beside the scores, but they are not scores: source and
test lines, classes, test methods, broad `catch (Exception …)` blocks, `Thread.sleep` in
main code, Kafka bootstrap literals in Java source, `docker-compose` and sample-publisher
presence.

## Procedure

1. **Blind the trial.** Give the judge only the trial's source tree: `pom.xml`, `src/`,
   configuration files, `docker-compose` and the README if present. Leave out the
   transcript, the model name, the trial ID, the oracle result and other trials. The
   framework stays identifiable from imports; score against the rubric, not a preference.
2. **Three independent judges** with the prompt in `results/reviews/JUDGE.md`. Use at
   least two vendors' models, to limit a judge favoring its own family's code. Judges
   don't see each other's scores.
3. **Take the median per dimension**, and the core total from the medians. If two judges
   differ by 3 or more on a dimension, re-review that dimension with the evidence each
   judge cited.
4. **Record** one file per trial:
   `results/reviews/stage-1-<cell>-<prefix>-<NN>.json`, holding each judge's model, scores
   and evidence, the medians, the core total and the measured signals. Add a row per
   trial to `results/code-quality.csv`:
   `cell,model,trial,compliance_v2,idiomaticity,structure,defect_risk,error_handling,run_surface,core_total,test_quality,judges`.
