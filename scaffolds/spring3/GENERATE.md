# Spring Boot 3.3.5 golden scaffold (version-recency control)

Generated once, committed untouched. Every trial copies this tree. This is the
**control variant** for the version-recency experiment: it is identical to
`scaffolds/spring/notify/` (same `spring-boot-starter-json` battery, see that
`GENERATE.md` for the rationale) **except** the Spring Boot parent is pinned to
**3.3.5** — a version well inside the models' training corpus — instead of 4.0.6.

## How it was produced

Derived from the `spring` golden scaffold with the parent version repinned:

    cp -a scaffolds/spring/notify scaffolds/spring3/notify
    # in scaffolds/spring3/notify/pom.xml: <version>4.0.6</version> -> <version>3.3.5</version>
    cd scaffolds/spring3/notify && mvn -q -DskipTests package   # confirm it builds

On Boot 3.3.5, `spring-boot-starter-json` resolves **Jackson 2**
(`com.fasterxml.jackson.core:jackson-databind:2.17.x`), so the auto-configured
`ObjectMapper` is exactly the `com.fasterxml.jackson.databind.ObjectMapper` type the
models expect — whereas the 4.0.6 scaffold resolves **Jackson 3** (`tools.jackson`).
That difference, plus the Boot-4 Kafka autoconfiguration reorganization, is what the
`spring-fix` vs `spring3-fix` cells isolate. See `results/RESULTS.md` → "Clean re-run".
