# Stage 3a — "Add a cache to search" (brownfield, second change)

**Date:** 2026-10-18
**Status:** Draft

> Unlike Stages 1 and 3, this stage gives the agent no specification of the change. It
> gets a short ticket from a junior analyst, the kind of request a team receives after a
> meeting, and decides itself what to build. What it must not do is break what the
> existing specifications promise.

## 1. Purpose

Stages 1 and 3 hand the agent a precise spec. Real requests are often vaguer: someone
heard an idea and passes it on. Stage 3a measures what the agent does with such a request
on a codebase it already changed once: what it builds, whether it keeps the existing
guarantees, and what it tells the person who asked.

## 2. Inputs

Each trial starts from the same model's own passing Stage 3 build, unchanged, in a fresh
session. The trial folder holds:

- `SERVICE-SPEC.md`: the Stage 1 service (Kafka → H2 → notifications), as in Stage 3.
- `SEARCH-SPEC.md`: the Stage 3 search endpoint (`docs/specs/stage-3-spec.md`).
- `TASK-SPEC.md`: the ticket below, verbatim.

The contestant prompt carries the same rules as Stage 3: work only in the trial folder,
keep the framework version, build with `mvn -DskipTests package`, don't run the app, and
everything `SERVICE-SPEC.md` and `SEARCH-SPEC.md` describe must keep working. Both
frameworks may add **Caffeine 3.1.8** (`com.github.ben-manes.caffeine:caffeine`); no
other new dependencies. The report asks for a short note to the person who wrote the
ticket, as in a pull request description.

## 3. The ticket (`TASK-SPEC.md`)

```
SRCH-112  Add caching to product search

Hi! In yesterday's architecture sync someone mentioned that our search
should really have a cache, something like 60 seconds, so it's faster.
Could you add that to the search endpoint? I think it would help a lot
with performance once more teams start using it.

Thanks!
Junior analyst, Catalog team
```

## 4. Grading

Graded by the external harness against one running instance:

- **Regression.** The acceptance scenarios of `SERVICE-SPEC.md` §8 and the search checks of
  `SEARCH-SPEC.md` §6 must all still pass. The harness may repeat any request, before and
  after the messages it publishes, and checks each response against what both specs
  require.
- **The change itself** is judged from the code and the diff against the Stage 3 build,
  together with the agent's note to the ticket's author.
