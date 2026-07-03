# Stage 1 — Event-driven merged purchase notification

**Date:** 2026-06-07
**Status:** Approved

> This specification describes **what** to build, not how. It names no
> programming framework, dependency-injection style, persistence library, or web
> framework. Implement it idiomatically for whatever stack you were given.

## 1. Purpose

Build a single runnable application that ingests four kinds of data from a Kafka
broker, keeps reference data in an embedded H2 database, and — whenever a
customer purchase arrives — emits one **merged notification** ("customer *X*
bought product *Y* for *Z*") back to Kafka. Whatever reference data is not yet
known is left null on the notification; a purchase always produces exactly one
notification.

## 2. Topics

All payloads are JSON. UTF-8.

| Topic | Direction | Payload |
|---|---|---|
| `product-updates` | in | `ProductUpdate` |
| `user-updates` | in | `UserUpdate` |
| `price-updates` | in | `PriceUpdate` |
| `purchases` | in | `Purchase` |
| `notifications` | out | `Notification` |

The Kafka bootstrap address is configurable (config file / environment).

## 3. Message contracts

```
ProductUpdate { productId: string, name: string, category: string }
UserUpdate    { userId: string, name: string, email: string }
PriceUpdate   { productId: string, priceCents: integer, currency: string }
Purchase      { purchaseId: string, userId: string, productId: string,
                quantity: integer, occurredAt: string (ISO-8601 instant) }

Notification  { purchaseId: string,
                userId: string,
                userName: string | null,
                productId: string,
                productName: string | null,
                priceCents: integer | null,
                currency: string | null,
                quantity: integer,
                totalCents: integer | null,
                occurredAt: string (ISO-8601 instant) }
```

Money is integer **cents** throughout. There is no floating-point money.

## 4. Reference store (H2)

Three tables, each keyed by its id:

- `products(product_id PK, name, category)`
- `users(user_id PK, name, email)`
- `prices(product_id PK, price_cents, currency)`

Each `*-updates` message **upserts** its row by primary key (insert-or-replace).
Re-delivery of the same message is therefore idempotent — the latest value for a
given id wins.

**Purchases are not persisted.** A purchase is a transient trigger only.

## 5. Behavior

On each `Purchase`:

1. Look up the product by `productId`, the user by `userId`, and the price by
   `productId`. Any of these may be absent.
2. Build a `Notification`:
   - `userName`     = user's name, or null if the user is unknown.
   - `productName`  = product's name, or null if the product is unknown.
   - `priceCents`   = price's cents, or null if the price is unknown.
   - `currency`     = price's currency, or null if the price is unknown.
   - `totalCents`   = `priceCents * quantity`, **only if** the price is known;
                      otherwise null.
   - `purchaseId`, `userId`, `productId`, `quantity`, `occurredAt` are copied
     straight from the purchase.
3. Publish exactly one `Notification` to `notifications`, keyed by `purchaseId`.

Never block waiting for reference data; never drop a purchase. Missing reference
data is the null path above, **not** an error.

## 6. Error handling

- A message that cannot be deserialized on any input topic is **logged and
  skipped**. One bad message must not stall consumption of that topic or crash
  the application.
- A transient failure handling a single message must not bring the application
  down.
- No retry queues, no dead-letter topics (out of scope).

## 7. Run surface

- **One runnable application.** On start it connects to Kafka, begins consuming
  the four input topics, initializes and maintains the H2 reference tables, and
  publishes to `notifications`. It shuts down cleanly.
- **Sample-data publisher.** A separate entry point publishes a small set of
  example messages to the four input topics, for a manual demo.
- **Local broker.** A `docker-compose` file brings up a single-node Kafka broker
  on `localhost:9092`.

Configuration (Kafka bootstrap, H2 location) is externalized to a config file
and/or environment variables.

## 8. Acceptance criteria

Canonical fixture data: product `p1` = name "Mechanical Keyboard", category
"electronics"; user `u1` = name "Alice"; price `p1` = 7999 USD. These are scored
by an external conformance harness (see `fixtures/stage-1/scenarios.json`).

| # | Given | When | Then |
|---|---|---|---|
| AC1 | `p1`, `u1`, price `p1` published and processed | purchase `{buy1, u1, p1, qty 2}` | one notification `{buy1, u1, "Alice", p1, "Mechanical Keyboard", 7999, "USD", 2, 15998, ts}` |
| AC2 | no reference data | purchase `{buy2, u-x, p-x, qty 3}` | one notification `{buy2, u-x, null, p-x, null, null, null, 3, null, ts}` |
| AC3 | only price `p2` = 500 EUR published | purchase `{buy3, u-x, p2, qty 4}` | notification has `priceCents 500, currency "EUR", totalCents 2000`; `userName` and `productName` null |
| AC4 | product `p1` published twice, name changing "Keyboard" → "Mechanical Keyboard" | purchase `{buy4, u1, p1, qty 1}` | notification `productName` = "Mechanical Keyboard" (latest upsert wins) |
| AC5 | a malformed (non-JSON) message on `price-updates`, then valid `p1`/`u1`/price `p1` | purchase `{buy5, u1, p1, qty 1}` | application did not crash; notification equals the AC1 shape for `buy5` (poison skipped) |
| AC6 | price `p1` = 7999 then purchase A `{buyA, u1, p1, qty 1}`; then price `p1` = 8999 then purchase B `{buyB, u1, p1, qty 1}` | — | A.`totalCents` = 7999; B.`totalCents` = 8999 (reference refresh) |

Timestamps (`occurredAt`) are echoed unchanged from the purchase.

## 9. Out of scope (YAGNI)

- Persisting purchases; any purchase history/audit.
- Updating an already-emitted notification when later reference data arrives.
- Retry, dead-letter topics, exactly-once semantics.
- Authentication, multi-tenancy, schema migrations beyond initial table
  creation.
- Full-text search (that is Stage 3).
