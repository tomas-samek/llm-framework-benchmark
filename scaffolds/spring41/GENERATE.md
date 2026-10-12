# Spring Boot 4.1.1 golden scaffold

Generated once (2026-10-12), committed untouched. Every trial copies this tree. This is
the **current-release** counterpart to `scaffolds/spring/notify/` (Boot 4.0.6): same
major, the version Spring Initializr offers by default as of generation. It pairs with
the Tiko 0.6.0 cell (current release vs current release) and is benchmarked as the
`spring-411` cell.

## How it was produced

Derived from the `spring` golden scaffold with the parent version repinned, exactly as
`scaffolds/spring3/` was:

    cp -a scaffolds/spring/notify scaffolds/spring41/notify
    # in scaffolds/spring41/notify/pom.xml: <version>4.0.6</version> -> <version>4.1.1</version>
    cd scaffolds/spring41/notify && mvn -q -DskipTests package   # confirm it builds

So the only difference from the 4.0.6 scaffold is the Spring Boot version, and both
keep the `spring-boot-starter-json` battery (see `scaffolds/spring/GENERATE.md` for why).

> **Why not regenerate from Initializr:** on 2026-10-12 start.spring.io's default
> `bootVersion` was 4.1.1, but it no longer offers a `json` dependency
> (`dependencies=json` returns HTTP 400 "Unknown dependency 'json'"), so the original
> command can't be replayed. `spring-boot-starter-json` itself is still published for
> 4.1.1. A plain 4.1.1 Initializr project (`dependencies=` empty) was compared with the
> 4.0.6 scaffold: apart from line endings, it differs only in the parent version, the
> missing JSON starter, and the Maven wrapper's distribution URL (Maven 3.9.16 → 3.10.0).
> Contestants build with the pinned toolchain Maven, not `mvnw`, so the wrapper was
> left as in the 4.0.6 scaffold.

Boot 4.1.1 was released on Maven Central on 2026-08-20; 4.0.6 on 2026-04-23. The
latest 4.0.x at generation time was 4.0.8.
