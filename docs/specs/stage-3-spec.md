# Stage 3 — Product search on an existing service (brownfield)

**Date:** 2026-10-17
**Status:** Approved (2026-10-17)

> This specification describes **what** to add, not how. Like Stage 1, it names no
> framework, dependency-injection style or web framework. The one fixed library is the
> search engine, Apache Lucene, pinned like the rest of the substrate.

## 1. Purpose

You are given a **working** implementation of the existing service, specified in
`SERVICE-SPEC.md`: it consumes product, user, price and purchase messages from Kafka,
keeps reference data in H2, and publishes one merged notification per purchase. Extend
it with **full-text product search over HTTP**. Everything `SERVICE-SPEC.md` specifies
must keep working unchanged.

## 2. Search index

- Every product in the `products` table is in a full-text index built with **Apache
  Lucene 9.11.1**. The indexed text of a product is its `name` and its `category`.
- **Upserts are reflected.** When a `ProductUpdate` is processed, the index follows the
  stored row: the latest name and category are searchable, and words that only the old
  values had no longer match. A processed update must be searchable within **5 seconds**.
- **Prices are not indexed.** A search result's price is the current row in the `prices`
  table at query time, so a price update needs no reindexing.
- **Startup.** When the application starts, the index contains every product already in
  the `products` table. Whether the index lives in memory or on disk is up to you.
- A malformed message must not break search, just as it must not stall consumption
  (`SERVICE-SPEC.md` §6).

## 3. Matching

- Text is split into **words** at every character that is not a letter or a digit.
  Matching is **case-insensitive**.
- A product matches a query if **every** word of the query appears as a whole word in
  the product's name or in its category (each word may come from either field).
- There is no stemming, no prefix matching and no fuzzy matching: `keyboards` does not
  match "Keyboard", and `ergo` does not match "Ergonomic".
- An optional `category` parameter restricts results to products whose category equals
  it exactly (case-sensitive, whole value).

## 4. HTTP API

One endpoint, served by the same application as the Kafka pipeline:

```
GET /search?q=<text>[&category=<category>][&limit=<n>]
```

- **Port** configurable (config file and/or environment); default **8080**.
- **200** with `Content-Type: application/json` (UTF-8):

  ```
  SearchResponse { query: string,             // q exactly as received (decoded)
                   total: integer,            // number of matches before the limit
                   results: [ SearchHit ] }   // at most `limit` hits

  SearchHit      { productId: string,
                   name: string,
                   category: string,
                   priceCents: integer | null,   // null if the price is unknown
                   currency: string | null }     // null if the price is unknown
  ```

- **Order:** results are sorted by `productId` ascending (plain string comparison). No
  relevance ranking: the order must not depend on scores.
- **`limit`:** default 10; an integer from 1 to 100.
- **No match** is not an error: 200 with `total: 0` and `results: []`.
- **400** with a JSON body `{ "error": string }` when `q` is missing or blank, or when
  `limit` is not an integer from 1 to 100.
- Any other path: **404**.

## 5. Run surface

Unchanged from `SERVICE-SPEC.md` §7, with one addition: the HTTP server starts with the
application and stops on clean shutdown. The run command stays the same. The
sample-data publisher may stay as it is.

## 6. Acceptance criteria

Graded by the external harness in two parts:

**Regression.** The existing acceptance scenarios (`SERVICE-SPEC.md` §8) must all still
pass.

**Search.** Canonical search data, published after the existing scenarios and using
words no existing product uses:

| Product | name | category | price |
|---|---|---|---|
| `sp1` | "Ergonomic Split Keyboard" | `peripherals` | 12900 EUR |
| `sp2` | "Vertical Ergonomic Mouse" | `peripherals` | 4900 EUR |
| `sp3` | "Ergonomic Office Chair" | `furniture` | — |
| `sp4` | "Trackball Cleaning Kit" | `accessories` | — |

| # | Given | Request | Then |
|---|---|---|---|
| S1 | the four products and two prices processed | `q=ergonomic` | `total` 3; results `sp1`, `sp2`, `sp3` in that order; `sp3` has null `priceCents` and `currency` |
| S2 | same | `q=ERGONOMIC mouse` | only `sp2` (case-insensitive; every word must match) |
| S3 | same | `q=mouse peripherals` | only `sp2` (words may match name or category) |
| S4 | same | `q=ergonomic&category=furniture` | only `sp3` |
| S5 | same | `q=ergonomic&limit=2` | `total` 3; results `sp1`, `sp2` |
| S6 | same | `q=keyboards`, then `q=ergo` | both 200 with `total` 0 (no stemming, no prefix) |
| S7 | `sp4` renamed to "Monitor Cleaning Kit" | `q=trackball`, then `q=monitor` | `total` 0; then only `sp4` with the new name |
| S8 | price `sp1` updated to 13900 EUR | `q=split` | `sp1` with `priceCents` 13900 |
| S9 | — | no `q`; then `q=ergonomic&limit=0` | both 400 with an `error` field |

The harness waits up to 10 seconds for each search expectation to hold. Each request in
the table is one graded check: 12 in all.

## 7. Out of scope (YAGNI)

- Relevance ranking, highlighting, facets, pagination offsets, autocomplete.
- Searching users, prices or notifications.
- Authentication, TLS, rate limiting.
- Changing any existing message contract or topic.
