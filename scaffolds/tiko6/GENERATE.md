# Tiko 0.6.0 golden scaffold

Generated once (2026-10-11), committed untouched. Every trial copies this tree. This
is the **0.6.0** counterpart to `scaffolds/tiko5/notify/` (0.5.0) — same archetype
command, newer pinned version. Benchmarked 2026-10-11 as the `tiko-060` cell.

> **Generated from Maven Central, not `~/.m2`.** The generating machine had a
> locally-installed (pre-release) `tiko-archetype:0.6.0` in `~/.m2` whose template
> pins `<tiko.version>0.6.0-SNAPSHOT</tiko.version>`; Maven prefers the local copy,
> so a plain `archetype:generate` silently produces the wrong scaffold. The jar on
> Central (sha1 `5ee24355a2d4979881d4072f2821d78d7330613e`) correctly pins `0.6.0`.
> Hence the isolated `-Dmaven.repo.local` below — use the same isolation when
> building trials, or they compile against the local, unreleased Tiko artifacts.

Changes vs 0.5.0 worth knowing before reading results against this scaffold (diffed
against `scaffolds/tiko5/notify/`, ignoring line-ending noise):

- **Poison-record guidance is now prominent.** `CLAUDE.md` and
  `.ai-skills/tiko-build/reference/kafka.md` now explain that
  `tiko.kafka.poison-record-policy` defaults to **`SEEK`** (redeliver with backoff, so
  a permanently bad record **blocks its partition**), and advise *against* overriding
  it to `SKIP`. The default itself is unchanged from 0.5.0 (`SEEK`, `SKIP` opt-in).
  This bears directly on Stage-1 spec §6 ("a message that cannot be deserialized is
  logged and skipped; one bad message must not stall consumption"): a contestant that
  follows the guidance literally will stall `price-updates` on AC5's poison message.
  New config keys: `seek-backoff`, `seek-backoff-max`; new `KafkaIngestErrorDecider`
  (SEEK / SKIP / FAIL / DEAD_LETTER).
- **HTTP routes pattern changed** (Javalin 7): route groups are `@Component`s with a
  `register(RoutesConfig)` method, taken by the `@Produces Javalin` factory; `Main`
  only starts the server. Not exercised by Stage 1 (no HTTP).
- **Test wiring:** `@TestComponent`s apply only in containers that opt in
  (`@TikoTest`, or `TikoOptions.builder().testWiring(true)`).
- `pom.xml` and `.mcp.json` differ only by the version bump (`0.5.0` → `0.6.0`).

## Command (run from `scaffolds/tiko6/`)

    mvn archetype:generate \
        -Dmaven.repo.local=<empty temp dir> \
        -DarchetypeGroupId=io.github.tomas-samek \
        -DarchetypeArtifactId=tiko-archetype \
        -DarchetypeVersion=0.6.0 \
        -DgroupId=eu.bench.notify \
        -DartifactId=notify \
        -Dversion=1.0.0-SNAPSHOT \
        -DinteractiveMode=false

Verify `0.6.0` is still the latest published archetype on Maven Central
(https://central.sonatype.com/artifact/io.github.tomas-samek/tiko-archetype).
Do NOT hand-edit the generated tree — the archetype's bundled `CLAUDE.md`,
`.ai-skills/`, and `.mcp.json` are part of the as-shipped condition and must remain.

## After generation

    cd notify && mvn -q -Dmaven.repo.local=<same dir> -DskipTests package   # confirm the scaffold builds

Verified 2026-10-11: builds against a clean repository resolving only from Central.
Then commit the whole `scaffolds/tiko6/notify/` tree.
