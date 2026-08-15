# tiko-di skill

This project uses **tiko-di**, a compile-time dependency injection framework for Java 21+.


Read [`.ai-skills/tiko-build/SKILL.md`](../../.ai-skills/tiko-build/SKILL.md) first — the decision tree, `@Produces` cookbook, and anti-pattern redirects that stop you from searching for "tiko's equivalent of Spring's X".


Ask the user for the missing facts rather than fabricating an integration
(**ask, don't fabricate**). To contribute the recipe upstream, follow
[the cookbook-extension skill on GitHub](https://github.com/tomas-samek/tiko-di/blob/main/.ai-skills/tiko-cookbook-extension/SKILL.md).


- README: https://github.com/tomas-samek/tiko-di/blob/main/README.md
- Worked examples: https://github.com/tomas-samek/tiko-di/tree/main/tiko-examples
- Issue tracker: https://github.com/tomas-samek/tiko-di/issues


- Constructor injection only — no field injection, no setter injection.
- All dependency wiring is validated and generated at compile time. Build failures point at exact problems.
- `@Component` declares a bean. Default scope is `PROTOTYPE` — pass `Scope.SINGLETON` for stateless services.
- Cross-scope injection (e.g. SINGLETON depending on EVENT) requires the shorter-scoped bean to implement an interface — the framework generates a proxy.
- Events: `@EventHandler` to receive; to send, inject `EventBus` into a component and call `publish(...)` (or `container.getEventBus().publish(...)` outside a component). `@EventTrigger` chains handlers declaratively.
- See README "Core Concepts" and "Usage Examples" sections for full details.


Read the generated code under `target/generated-sources/annotations/io/tiko/generated/`. The framework's behaviour is fully visible in source — there is no reflection or classpath scanning in your wiring.
