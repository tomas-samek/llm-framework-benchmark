# Spring Boot golden scaffold

Generated once, committed untouched. Every trial copies this tree.
Near-core — the trial agent adds spring-kafka / data-jpa / web / h2 / lucene
itself, mirroring Tiko's opt-in model. The one battery included is
`spring-boot-starter-json` (Jackson + an auto-configured `ObjectMapper` bean),
to mirror the fact that Tiko's `tiko-kafka` module ships its JSON serializer
bundled with the transport. See the "starter-json" note below.

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
        -d dependencies=json \
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

`dependencies=json` yields `spring-boot-starter` + `spring-boot-starter-json` +
`spring-boot-starter-test`.

> **Note — starter-json (added 2026-06-13):** the original scaffold was generated
> with `dependencies=` (empty), i.e. core-only. That shipped **no Jackson and no
> `ObjectMapper` bean**, so any contestant injecting an `ObjectMapper` failed at
> startup — diagnosed as 7/10 fail-to-start in the `spring-free` arm and several
> failures in the forced runs. That was a **scaffold gap**, not a framework signal,
> and it was *asymmetric*: Tiko's `tiko-kafka` module bundles a JSON serializer, so
> a Tiko contestant got JSON for free with the transport while a Spring contestant
> did not. `spring-boot-starter-json` restores symmetry. On Boot 4 it brings
> **Jackson 3** (`tools.jackson`), whose auto-configured bean is *not* the
> `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) type — so a remaining
> `ObjectMapper` mismatch is now a genuine Jackson 2→3 *version-recency* signal,
> which is exactly what this benchmark is trying to isolate.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/spring/notify/` tree.
