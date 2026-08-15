# Tiko 0.5.0 golden scaffold

Generated once, committed untouched. Every trial copies this tree. This is the
**0.5.0** counterpart to `scaffolds/tiko3/notify/` (0.3.0) — same archetype
command, newer pinned version. There's an intermediate 0.4.0 release on Maven
Central that this benchmark skipped straight past (no scaffold for it).

Two real changes worth knowing before reading Stage-1 results against this
scaffold, both confirmed by diffing against `scaffolds/tiko3/notify/`:

- **The `REQUEST` and `EVENT` scopes were merged into one unified `EVENT`
  scope** ("one unit of work — request, message, job, async dispatch"),
  down from four scopes (`SINGLETON`/`REQUEST`/`EVENT`/`PROTOTYPE`) to three.
  This is the archetype-level face of the "async handlers own their unit of
  work" change in the [v0.5.0 release notes](https://github.com/tomas-samek/tiko-di/releases/tag/v0.5.0)
  ([tiko-di#220](https://github.com/tomas-samek/tiko-di/issues/220)).
- **`.ai-skills/` was restructured**: `tiko-cookbook-extension/SKILL.md` was
  dropped; a new `tiko-build/reference/` directory was added with
  `api-signatures.md`, `config.md`, `events.md`, `kafka.md` — reference docs
  split out of the single `SKILL.md`. `CLAUDE.md` was also rewritten (not
  just reworded — the scope-model section reflects the 3-scope model above).

## Command (run from `scaffolds/tiko5/`)

    mvn archetype:generate \
        -DarchetypeGroupId=io.github.tomas-samek \
        -DarchetypeArtifactId=tiko-archetype \
        -DarchetypeVersion=0.5.0 \
        -DgroupId=eu.bench.notify \
        -DartifactId=notify \
        -Dversion=1.0.0-SNAPSHOT \
        -DinteractiveMode=false

Verify `0.5.0` is still the latest published archetype on Maven Central
(https://central.sonatype.com/artifact/io.github.tomas-samek/tiko-archetype).
Do NOT hand-edit the generated tree — the archetype's bundled `CLAUDE.md`,
`.ai-skills/`, and `.mcp.json` are part of the as-shipped condition and must remain.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/tiko5/notify/` tree.
