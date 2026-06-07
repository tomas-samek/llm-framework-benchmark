# Spring Boot golden scaffold

Generated once, committed untouched. Every trial copies this tree.
Core only — the trial agent adds spring-kafka / data-jpa / web / h2 / lucene
itself, mirroring Tiko's opt-in model.

## Command (run from `scaffolds/spring/`)

Using curl against Spring Initializr (no Spring CLI required):

    curl -s https://start.spring.io/starter.zip \
        -d type=maven-project \
        -d language=java \
        -d javaVersion=21 \
        -d bootVersion=4.0.6 \
        -d groupId=eu.bench.notify \
        -d artifactId=notify \
        -d name=notify \
        -d packageName=eu.bench.notify \
        -d dependencies= \
        -o notify.zip
    unzip notify.zip -d notify && rm notify.zip

(Equivalent Spring CLI: `spring init --build=maven --java-version=21 \
 --boot-version=4.0.6 --group-id=eu.bench.notify --artifact-id=notify \
 --name=notify notify`.)

> **Note — bootVersion fallback (generated 2026-06-07):** bootVersion 3.3.5 was
> rejected by start.spring.io (HTTP 400); Spring Boot 3.3.x is no longer offered.
> The current default per `GET /metadata/client` was 4.0.6.RELEASE; the Maven
> Central artifact is published without the `.RELEASE` suffix, so `4.0.6` was used.
> Pinned bootVersion: 4.0.6 (current Initializr default as of generation).

`dependencies=` (empty) yields core only: `spring-boot-starter` +
`spring-boot-starter-test`.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/spring/notify/` tree.
