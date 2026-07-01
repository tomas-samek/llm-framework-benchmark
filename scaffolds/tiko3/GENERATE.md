# Tiko 0.3.0 golden scaffold

Generated once, committed untouched. Every trial copies this tree. This is the
**0.3.0** counterpart to `scaffolds/tiko/notify/` (0.2.2) — same archetype command,
newer pinned version. tiko-bom 0.3.0 additionally manages `tiko-kafka` and
`tiko-kafka-processor` (they previously needed an explicit version pin — see
tiko-di#298), but the bundled `CLAUDE.md` / `.ai-skills/` are byte-identical to
0.2.2 (verified via diff): the doc gaps filed as
[tiko-di#399–#406](https://github.com/tomas-samek/tiko-di/issues/399) still apply.

## Command (run from `scaffolds/tiko3/`)

    mvn archetype:generate \
        -DarchetypeGroupId=io.github.tomas-samek \
        -DarchetypeArtifactId=tiko-archetype \
        -DarchetypeVersion=0.3.0 \
        -DgroupId=eu.bench.notify \
        -DartifactId=notify \
        -Dversion=1.0.0-SNAPSHOT \
        -DinteractiveMode=false

Verify `0.3.0` is still the latest published archetype on Maven Central
(https://central.sonatype.com/artifact/io.github.tomas-samek/tiko-archetype).
Do NOT hand-edit the generated tree — the archetype's bundled `CLAUDE.md`,
`.ai-skills/`, and `.mcp.json` are part of the as-shipped condition and must remain.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/tiko3/notify/` tree.
