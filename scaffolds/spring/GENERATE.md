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
        -d bootVersion=3.3.5 \
        -d groupId=eu.bench.notify \
        -d artifactId=notify \
        -d name=notify \
        -d packageName=eu.bench.notify \
        -d dependencies= \
        -o notify.zip
    unzip notify.zip -d notify && rm notify.zip

(Equivalent Spring CLI: `spring init --build=maven --java-version=21 \
 --boot-version=3.3.5 --group-id=eu.bench.notify --artifact-id=notify \
 --name=notify notify`.)

`dependencies=` (empty) yields core only: `spring-boot-starter` +
`spring-boot-starter-test`.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/spring/notify/` tree.
