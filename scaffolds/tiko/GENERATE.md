# Tiko golden scaffold

Generated once, committed untouched. Every trial copies this tree.

## Command (run from `scaffolds/tiko/`)

    mvn archetype:generate \
        -DarchetypeGroupId=io.github.tomas-samek \
        -DarchetypeArtifactId=tiko-archetype \
        -DarchetypeVersion=0.2.2 \
        -DgroupId=eu.bench.notify \
        -DartifactId=notify \
        -Dversion=1.0.0-SNAPSHOT \
        -DinteractiveMode=false

Verify `0.2.2` is the latest published archetype on Maven Central
(https://central.sonatype.com/artifact/io.github.tomas-samek/tiko-archetype).
If a newer version is the pinned Tiko version, use it and update
`docs/benchmark-protocol.md` §3 to match. Do NOT hand-edit the generated tree —
the archetype's bundled `CLAUDE.md`, `.ai-skills/`, and `.mcp.json` are part of
the as-shipped condition and must remain.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/tiko/notify/` tree.
