# Tiko DI project

This project uses [Tiko DI](https://github.com/tomas-samek/tiko-di) — a
compile-time dependency injection framework for Java 21+.


Read [`.ai-skills/tiko-build/SKILL.md`](./.ai-skills/tiko-build/SKILL.md)
first — decision tree, `@Produces` cookbook, anti-pattern redirects. The
skill is the procedure for building with the framework; the files below
are the reference.


Read
[`.ai-skills/tiko-cookbook-extension/SKILL.md`](./.ai-skills/tiko-cookbook-extension/SKILL.md) —
the procedural skill for adding a new recipe. Load-bearing rule:
**ask, don't fabricate.**


The canonical conventions live in [`CLAUDE.md`](./CLAUDE.md) at the project
root. It covers:

- Component scopes (SINGLETON / REQUEST / EVENT / PROTOTYPE)
- Annotation cheat-sheet (`@Component`, `@Inject`, `@Produces`,
  `@Configuration`, `@EventHandler`, `@EventTrigger`)
- Constructor-injection rule (no field injection)
- Build commands and common pitfalls


- Constructor injection only. `@Inject` on the constructor, never on fields.
- Components declare scope: `@Component(scope = Scope.SINGLETON)`.
- Annotation processing runs in `mvn compile`.


- `mvn compile` — runs annotation processing
- `mvn test` — runs tests
- `mvn exec:java -Dexec.mainClass=eu.bench.notify.Main` — runs the example
