# Stage-1 acceptance fixtures

Hidden from contestants. Graded by `conformance/oracle` against one freshly started app
(empty store), all scenarios in one run, then the `notifications` topic is drained and
compared. Both versions grade the **same contestant spec** (`docs/specs/stage-1-spec.md`,
unchanged) with **7 expectations** over 6 scenarios.

| File | Version | Used for |
|---|---|---|
| `scenarios.json` | v1 (2026-06-08) | every published result through 2026-08-15; still the scripts' default |
| `scenarios.v2.json` | v2 (2026-10-11) | new cells, graded *in addition to* v1 → `results/*.v2.json`, `results/metrics-v2.csv` |

Scores are comparable only within a version.

## Why v2

All scenarios share one store, so state left by earlier scenarios can satisfy later
ones. In v1 that made two scenarios unable to fail for the defect they name:

- **AC4 (latest upsert wins)** reused `p1`, whose final name AC1 had already stored.
  A first-write-wins store (insert-if-absent, or a PK-violation that's logged and
  skipped) passed it.
- **AC5 (bad message skipped)** re-published `p1 = 7999`, already stored by AC1/AC4.
  An app whose `price-updates` consumer **stalls** on the bad message passed AC5 and
  failed one expectation *later*: AC6/buyB, with the stale price 7999.

v1 also didn't enforce spec §5 "exactly one notification": the drain keeps only the
latest value per key, so duplicates were invisible.

v2 changes (nothing else):

1. AC4 uses a fresh product `p4` ("Keyboard" → "Mechanical Keyboard", price 4999).
2. AC5 uses a fresh product/price `p5` ("Wrist Rest", 1299), a price the store has never seen.
3. AC5 runs **last**, so a consumer stalled by its bad message can only fail AC5.
4. `"exactlyOncePerKey": true`: an expected key seen more than once fails
   (`N notifications with this key, expected exactly one`). The oracle treats a missing
   flag as false, so v1 grades exactly as before.

Ids intentionally differ from the spec's AC table; the asserted behaviour is identical.
`ScenarioLoaderTest` checks that AC4/AC5 use ids no earlier scenario published.

## Validation (2026-10-11)

A minimal in-memory reference app (kept out of the repo: it's a working solution) was
graded on both versions in four modes: a correct one, plus three modes each
with one deliberate defect:

| Mode | Defect | v1 | v2 |
|---|---|---|---|
| correct | — | 7/7 | 7/7 |
| firstwins | upsert ignores updates to an existing id | 6/7 — AC6/buyB only (**AC4 passes**) | 5/7 — AC4, AC6/buyB |
| stall | stops applying `price-updates` after the bad message | 6/7 — **AC6/buyB** (`priceCents` 7999) | 6/7 — **AC5** (`priceCents` null) |
| dup | emits every notification twice | **7/7** | 0/7 |

The `stall` row on v1 is byte-for-byte the signature of all ten historical 86% runs
(Tiko 0.2.2 cells: AC6/buyB with `priceCents` 7999). Tiko's Kafka default
`poison-record-policy: SEEK` (redeliver a failing record with backoff, indefinitely) is
exactly this behaviour. So those runs may be poison-handling failures misattributed to
AC6 rather than refresh failures — the trial logs are gone, so v1 can't tell. v2 can.

## First real run (Tiko 0.6.0, 2026-10-11)

All 15 `tiko-060` trials were graded on both versions: 15/15 on v1, 14/15 on v2 (second
batches for all three models and a third Haiku batch on 2026-10-12 brought this to 35/35
and 33/35; the second v2 failure, Haiku h55-12, has the same cause as the first).
The difference is one Haiku 5.5 trial whose bad-message handler was meant to skip
undecodable records but checked for the wrong exception type, so it retried with
backoff for ~15 s before skipping. v1 scored it 7/7 — the stall was masked exactly as
above — while v2 scored it 6/7, failing AC5 (`priceCents` null for buy5). Details:
`results/RESULTS.md` → "Tiko 0.6.0".
