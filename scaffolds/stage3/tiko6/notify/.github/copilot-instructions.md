# Copilot instructions

This project uses Tiko DI — a compile-time dependency injection framework
for Java 21+. **Read [`CLAUDE.md`](../CLAUDE.md) for the full conventions
before suggesting code.** The summary below is a refresher; CLAUDE.md is
authoritative.


```java
@Component(scope = Scope.SINGLETON)
public class FooService {
    @Inject
    public FooService(BarRepository repo) { /* ... */ }
}
```

- Constructor injection only — never field injection.
- Components must declare a scope.
- `@TestComponent` for test fixtures (from `tiko-test`).


- `mvn compile` — runs annotation processing
- `mvn test` — runs tests
