# Technical Reference

Complete technical specification of the Mini Expense Manager: stack, database schema, and every
API endpoint with real request and response payloads.

All version numbers, schema definitions, and payloads in this document were captured from the
running application and live database — none are written from memory.

**Related:** [README](README.md) · [Testing guide](TESTING_GUIDE.md) · [Technical plan](TECHNICAL_PLAN.md)

---

## Contents

1. [Architecture](#1-architecture)
2. [Technology stack](#2-technology-stack)
3. [Database schema](#3-database-schema)
4. [Core algorithms](#4-core-algorithms)
5. [API reference](#5-api-reference)
6. [Error contract](#6-error-contract)
7. [Configuration](#7-configuration)
8. [Source layout](#8-source-layout)

---

## 1. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Browser — React SPA (Vite dev server :5173 / static build) │
│                                                             │
│   Pages          Expenses  │  Import CSV  │  Dashboard      │
│   Data layer     TanStack Query  →  hooks/queries.ts        │
│   Transport      api/client.ts  →  fetch + Zod validation   │
└───────────────────────────┬─────────────────────────────────┘
                            │  JSON over HTTP  (/api/**)
                            │  dev: Vite proxies → :8080 (same-origin)
┌───────────────────────────▼─────────────────────────────────┐
│  Spring Boot :8080                                          │
│                                                             │
│   web/controller   Expense · CsvImport · Dashboard · Rules  │
│         ↓          DTO mapping only, no business logic      │
│   service          Categorization · Anomaly · CsvImport ·   │
│                    Expense · Dashboard                      │
│         ↓                                                   │
│   repository       Spring Data JPA + native aggregations    │
└───────────────────────────┬─────────────────────────────────┘
                            │  JDBC (HikariCP)
┌───────────────────────────▼─────────────────────────────────┐
│  PostgreSQL :5432 — schema owned by Flyway                  │
│  category · vendor_category_rule · expense · csv_import_batch│
└─────────────────────────────────────────────────────────────┘
```

**Layering rules enforced throughout:**

- Controllers map DTOs and nothing else — no business logic, no repository access.
- Entities never cross the controller boundary; every response is an explicit `record` DTO.
- Repositories are the only code that touches the database.
- `spring.jpa.open-in-view: false` — DTO mapping happens inside the service transaction, so no
  lazy-loading can leak into view rendering.

---

## 2. Technology stack

### 2.1 Backend

| Component | Choice | Version |
|---|---|---|
| Language | Java | 21 (verified on 21 and 25) |
| Framework | Spring Boot | 3.3.5 |
| Core | Spring Framework | 6.1.14 |
| Web | Spring MVC on embedded Tomcat | Tomcat 10.1.31 |
| Persistence | Spring Data JPA / Hibernate ORM | Hibernate 6.5.3.Final |
| Connection pool | HikariCP | 5.1.0 |
| Migrations | Flyway (+ PostgreSQL module) | 10.10.0 |
| JDBC driver | PostgreSQL | 42.7.4 |
| CSV parsing | Apache Commons CSV | 1.11.0 |
| JSON | Jackson Databind | 2.17.2 |
| Validation | Jakarta Bean Validation (Hibernate Validator) | Boot-managed |
| Monitoring | Spring Boot Actuator | Boot-managed |
| Build | Maven | 3.9+ |
| Testing | JUnit 5 · AssertJ · Mockito · MockMvc | Boot-managed |

**Why these:**

- **Flyway over `ddl-auto`** — the seed categories and ~70 vendor rules must ship as versioned,
  repeatable migrations. Hibernate runs in `validate` mode; it checks the schema, never writes it.
- **Commons CSV over a hand-rolled split** — quoted fields containing commas, embedded newlines,
  and CRLF endings are all cases a naive parser gets wrong on real spreadsheet exports.
- **No Lombok** — plain getters keep the build free of annotation-processor setup and IDE plugins.

### 2.2 Frontend

| Component | Choice | Version |
|---|---|---|
| Language | TypeScript (`strict: true`) | 6.0.2 |
| UI library | React | 19.2.8 |
| Build tool | Vite | 8.2.0 |
| Routing | React Router | 7.18.2 |
| Server state | TanStack Query | 5.101.4 |
| Forms | React Hook Form + `@hookform/resolvers` | 7.85.0 / 5.7.1 |
| Runtime validation | Zod | 4.4.3 |
| Charts | Recharts | 3.10.1 |
| Styling | Tailwind CSS + PostCSS + Autoprefixer | 3.4.19 |
| Testing | Vitest · Testing Library · jsdom | 4.1.10 |
| Linting | oxlint | 1.75.0 |

**Why these:**

- **TanStack Query, no client store** — every screen is server-state driven. Redux or Zustand
  would add a second copy of the truth with nothing to put in it.
- **Zod at the transport boundary** — TypeScript types are *erased* at runtime, so a `fetch`
  result typed as `Expense` is an unchecked assertion. Parsing through a schema turns a backend
  contract change into a named error at the boundary rather than `undefined` inside a chart.
  All TS types are inferred **from** the schemas, so the two cannot drift.
- **Recharts** — declarative React components rather than imperative D3 selections.

### 2.3 Cross-cutting decisions

| Decision | Rationale |
|---|---|
| Money is `BigDecimal` / `NUMERIC(14,2)` / decimal **string** on the wire | IEEE-754 cannot represent every 2-decimal value. The client formats but never computes, so no float ever touches a monetary value at any layer. |
| Vite dev proxy for `/api` | The browser sees one origin, so CORS never applies in development and headers behave as they would behind a production reverse proxy. |
| Sort keys are whitelisted | An unrestricted sort parameter is a way to probe the entity model and force unindexed scans. |
| `PageResponse<T>` envelope | Spring's `PageImpl` JSON shape is not part of its API contract and has changed between versions; the frontend parses this with a fixed schema. |

---

## 3. Database schema

Four tables, created by [`V1__schema.sql`](backend/src/main/resources/db/migration/V1__schema.sql)
and seeded by V2 and V3.

```
category ──1:N──> expense <──N:1── csv_import_batch
    │
    └──1:N──> vendor_category_rule

vendor_category_rule matches expense.vendor_normalized by string,
not by foreign key — a rule describes a pattern, not a row.
```

### 3.1 `category`

Spending categories. Exactly one row is the fallback used when no vendor rule matches.

| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | `bigint` | no | `nextval(...)` | PK |
| `name` | `text` | no | | **unique** |
| `color_hex` | `text` | yes | | keeps chart colours stable across screens |
| `is_default` | `boolean` | no | `false` | the `Uncategorized` fallback |
| `created_at` | `timestamptz` | no | `now()` | |

**Indexes / constraints**

```sql
PRIMARY KEY (id)
UNIQUE (name)
CREATE UNIQUE INDEX uq_category_single_default ON category (is_default) WHERE is_default = true;
```

That partial unique index is the important one: it makes "there is exactly one fallback
category" a database guarantee rather than a convention the code has to remember.

**Seed data (V2)** — 8 rows: Food, Groceries, Travel, Shopping, Utilities, Entertainment,
Health, and Uncategorized (`is_default = true`).

### 3.2 `vendor_category_rule`

The vendor-to-category mapping. Rules are data, not code.

| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | `bigint` | no | `nextval(...)` | PK |
| `pattern` | `text` | no | | stored **already normalized** |
| `match_type` | `text` | no | | `EXACT` \| `CONTAINS` |
| `category_id` | `bigint` | no | | FK → `category(id)` |
| `priority` | `integer` | no | `100` | **lower wins** among CONTAINS matches |
| `active` | `boolean` | no | `true` | delete deactivates rather than removes |
| `created_at` | `timestamptz` | no | `now()` | |

**Indexes / constraints**

```sql
PRIMARY KEY (id)
UNIQUE (pattern, match_type)
CHECK (match_type IN ('EXACT', 'CONTAINS'))
FOREIGN KEY (category_id) REFERENCES category(id)
CREATE INDEX idx_rule_pattern_active ON vendor_category_rule (pattern) WHERE active = true;
```

**Patterns are stored pre-normalized.** A rule typed as `"Uber Eats"` is saved as `uber eats`,
because it is compared against normalizer output — storing the raw form would mean it never
matches anything.

**Seed data (V3)** — ~68 rules across all categories.

### 3.3 `expense`

The main table.

| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | `bigint` | no | `nextval(...)` | PK |
| `expense_date` | `date` | no | | date only; no time component |
| `amount` | `numeric(14,2)` | no | | **never** float/double |
| `vendor_name` | `text` | no | | exactly as entered, for display |
| `vendor_normalized` | `text` | no | | matching + grouping key |
| `description` | `text` | yes | | |
| `category_id` | `bigint` | no | | FK → `category(id)` |
| `categorization_source` | `text` | no | | `RULE` \| `DEFAULT` \| `MANUAL_OVERRIDE` |
| `is_anomaly` | `boolean` | no | `false` | materialized by the anomaly sweep |
| `anomaly_reason` | `text` | yes | | `AMOUNT_GT_3X_CATEGORY_AVG` when flagged |
| `anomaly_evaluated_at` | `timestamptz` | yes | | last sweep timestamp |
| `import_batch_id` | `bigint` | yes | | FK → `csv_import_batch(id)`; null for manual entries |
| `created_at` | `timestamptz` | no | `now()` | |
| `updated_at` | `timestamptz` | no | `now()` | |

**Indexes / constraints**

```sql
PRIMARY KEY (id)
CHECK (amount > 0)
CHECK (categorization_source IN ('RULE', 'DEFAULT', 'MANUAL_OVERRIDE'))
FOREIGN KEY (category_id)     REFERENCES category(id)
FOREIGN KEY (import_batch_id) REFERENCES csv_import_batch(id)

CREATE INDEX idx_expense_date          ON expense (expense_date DESC);
CREATE INDEX idx_expense_category_date ON expense (category_id, expense_date);
CREATE INDEX idx_expense_vendor_norm   ON expense (vendor_normalized);
CREATE INDEX idx_expense_batch         ON expense (import_batch_id);
CREATE INDEX idx_expense_anomaly       ON expense (is_anomaly) WHERE is_anomaly = true;
```

**Why two vendor columns.** `vendor_name` preserves what the user typed, so the UI never shows a
mangled label. `vendor_normalized` is what rules match against and what the top-vendors chart
groups by, so `SWIGGY`, `Swiggy ` and `swiggy Pvt Ltd` collapse into one bar.

**Why `is_anomaly` is stored, not computed.** Both the list and the dashboard need it. Computing
it per request would mean a correlated aggregate per row; storing it makes the anomaly filter a
plain indexed lookup. The partial index covers only flagged rows, which are a small minority.

**`CHECK (amount > 0)`** — the last line of defence. Bean Validation, the CSV parser, and the
browser form all reject non-positive amounts; this guarantees it even for direct SQL writes.

### 3.4 `csv_import_batch`

One row per upload — an audit trail of what was imported and what failed.

| Column | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | `bigint` | no | `nextval(...)` | PK |
| `filename` | `text` | no | | leaf name only; browser path is not trusted |
| `total_rows` | `integer` | no | `0` | data rows read |
| `imported_rows` | `integer` | no | `0` | |
| `failed_rows` | `integer` | no | `0` | |
| `status` | `text` | no | | `COMPLETED` \| `COMPLETED_WITH_ERRORS` \| `FAILED` |
| `error_report` | `jsonb` | yes | | array of `{row, field, message}` |
| `created_at` | `timestamptz` | no | `now()` | |

```sql
PRIMARY KEY (id)
CHECK (status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'))
```

`error_report` is `jsonb` rather than `text` so failures stay queryable — e.g. "which column
fails most often across all imports?"

### 3.5 Migrations

| File | Contents |
|---|---|
| `V1__schema.sql` | 4 tables, all constraints and indexes |
| `V2__seed_categories.sql` | 8 categories |
| `V3__seed_vendor_rules.sql` | ~68 vendor rules |

Applied automatically on boot. Verify with:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

---

## 4. Core algorithms

### 4.1 Vendor name normalization

[`VendorNameNormalizer.java`](backend/src/main/java/com/iconcile/expense/util/VendorNameNormalizer.java)

Both rule patterns and incoming vendor names pass through the same pipeline, so substring rules
stay short and readable while still matching noisy statement lines.

| Step | Effect |
|---|---|
| 1. Unicode NFD + strip diacritics | `Café` → `Cafe` |
| 2. Lowercase, non-alphanumerics → space | `Swiggy*Order` → `swiggy order` |
| 3. Drop pure-digit runs of 4+ | strips transaction ids, keeps `1mg`, keeps `Store 365` |
| 4. Strip **leading** payment-rail noise | `upi`, `pos`, `neft`, `imps`, `paytm`, `razorpay`, … |
| 5. Strip **trailing** corporate suffixes | `pvt`, `ltd`, `llp`, `inc`, `technologies`, … |
| 6. Fall back if empty | a symbols-only vendor still gets a usable grouping key |

```
"UPI/SWIGGY*ORDER 8823 BLR"    → "swiggy order blr"
"POS 4412 UBER INDIA PVT LTD"  → "uber india"
"Amazon Retail Pvt Ltd"        → "amazon retail"
```

Country words are **deliberately not** stripped: removing `india` would turn `Air India` into
`air` and break the airline rule — a bigger loss than the noise it removes.

### 4.2 Categorization

[`CategorizationService.java`](backend/src/main/java/com/iconcile/expense/service/CategorizationService.java)

1. **EXACT** — normalized name equals a pattern.
2. **CONTAINS** — pattern is a substring, ordered by `priority ASC`, then `LENGTH(pattern) DESC`,
   then pattern alphabetically. First match wins.
3. **Fallback** — the `is_default` category, source `DEFAULT`.

An explicit `categoryId` on the request bypasses all of it and is stored as `MANUAL_OVERRIDE`.

**Why the ordering matters:**

| Vendor | Result | Beat |
|---|---|---|
| `uber eats` | Food (priority 10) | `uber` → Travel (priority 100) |
| `swiggy instamart` | Groceries (priority 5) | `swiggy` → Food (100) |
| `amazon prime video` | Entertainment (10) | `amazon` → Shopping (100) |
| `coca cola` | Uncategorized | `ola` is **EXACT**, so it cannot match inside "cola" |

**Caching.** All active rules are held in memory as an immutable snapshot. CSV import calls the
matcher once per row, so a database round trip per row would dominate import time. The snapshot
is dropped by `refresh()` on any rule write and lazily rebuilt.

### 4.3 Anomaly detection

[`AnomalyService.java`](backend/src/main/java/com/iconcile/expense/service/AnomalyService.java)

> Flagged when `amount > 3 × average(other expenses in the same category)`.

Three properties, each a deliberate choice (see [README](README.md#design-decisions)):

- **Leave-one-out average** — the expense is excluded from its own baseline.
- **Minimum sample size of 3** other expenses before a category flags anything.
- **Stored and re-swept on every write** — the whole affected category is re-evaluated.

Executed as **one set-based statement per affected category** — never a row loop:

```sql
WITH stats AS (
    SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) AS cnt
    FROM expense WHERE category_id = :categoryId AND expense_date >= :sinceDate
), calc AS (
    SELECT e2.id AS eid,
           COALESCE(
               (s.cnt - 1) >= :minSampleSize
               AND e2.amount > :multiplier * ((s.total - e2.amount) / NULLIF(s.cnt - 1, 0)),
               false
           ) AS new_flag
    FROM expense e2 CROSS JOIN stats s
    WHERE e2.category_id = :categoryId AND e2.expense_date >= :sinceDate
)
UPDATE expense e
SET is_anomaly = calc.new_flag,
    anomaly_reason = CASE WHEN calc.new_flag THEN 'AMOUNT_GT_3X_CATEGORY_AVG' ELSE NULL END,
    anomaly_evaluated_at = now()
FROM calc
WHERE e.id = calc.eid
  AND (e.is_anomaly IS DISTINCT FROM calc.new_flag OR e.anomaly_evaluated_at IS NULL);
```

- `(s.total - e2.amount) / (s.cnt - 1)` is the leave-one-out mean, derived from the category
  sum and count rather than a per-row subquery.
- `NULLIF(s.cnt - 1, 0)` guards a single-expense category — the division yields NULL, and
  `FALSE AND NULL` is FALSE, so nothing is flagged.
- `IS DISTINCT FROM` limits writes to rows whose flag actually changed.

**Sweep triggers**

| Event | Categories re-swept |
|---|---|
| Create | the new expense's category |
| Update | old **and** new category |
| Delete | the deleted expense's category |
| CSV import | every distinct affected category, **once**, after all rows land |

### 4.4 CSV import

[`CsvImportService.java`](backend/src/main/java/com/iconcile/expense/service/CsvImportService.java)

1. Reject non-`.csv` and empty uploads (`422`).
2. Open a reader, stripping a UTF-8 BOM if present.
3. Stream-parse; resolve headers via alias table; missing required column → `422`.
4. Per row: parse → validate → categorize. A `RowFieldException` records `{line, field, message}`
   and moves to the next row.
5. Insert valid rows in batches of 500.
6. Sweep anomalies once per affected category.
7. Persist the batch record with counts and the error report.

**Transaction boundary:** the entire import is one transaction. Bad rows are *skipped and
reported*, not thrown — so the common "a few malformed rows" case still succeeds. Only an
unreadable file fails outright.

**Header aliases**

| Field | Required | Accepted spellings (case/punctuation insensitive) |
|---|---|---|
| date | yes | `date`, `expense_date`, `transaction_date`, `txn_date`, `value_date` |
| amount | yes | `amount`, `value`, `price`, `debit`, `spend` |
| vendor | yes | `vendor`, `vendor_name`, `merchant`, `merchant_name`, `payee` |
| description | no | `description`, `notes`, `note`, `remarks`, `memo`, `narration`, `particulars` |
| category | no | `category`, `category_name` |

**Value parsing**

- **Dates** — `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`, resolved `STRICT` so
  `31/02` is rejected rather than rolled forward. The format is **locked** to whichever one the
  first parsed row uses; later rows must match. Without the lock, `01/02/2026` and `13/02/2026`
  in one file would be read under two different conventions.
- **Amounts** — strips currency symbols, thousands separators, and `Rs`/`INR`/`USD` tokens.
  Rejects `(123.45)` accounting-negative notation, non-positive values, more than 2 decimal
  places, and anything over 10,000,000.
- **Duplicates** — rows matching `(date, amount, vendor_normalized)` against existing data or
  earlier rows in the same file are **warned about and still imported**.

---

## 5. API reference

Base path `/api`. Content type `application/json` except the multipart upload.
**All monetary values are decimal strings.**

### 5.1 Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/expenses` | List, filtered and paged |
| `GET` | `/api/expenses/{id}` | Fetch one |
| `POST` | `/api/expenses` | Create |
| `PUT` | `/api/expenses/{id}` | Update |
| `DELETE` | `/api/expenses/{id}` | Delete |
| `POST` | `/api/expenses/import` | CSV upload (multipart) |
| `GET` | `/api/expenses/import/format` | Machine-readable format spec |
| `GET` | `/api/dashboard/summary` | Headline tiles |
| `GET` | `/api/dashboard/monthly-by-category` | Stacked-bar series |
| `GET` | `/api/dashboard/top-vendors` | Top vendors by spend |
| `GET` | `/api/dashboard/anomalies` | Anomalies with baselines |
| `GET` | `/api/categories` | All categories |
| `GET` | `/api/vendor-rules` | All rules |
| `POST` | `/api/vendor-rules` | Create rule |
| `PUT` | `/api/vendor-rules/{id}` | Update rule |
| `DELETE` | `/api/vendor-rules/{id}` | Deactivate rule |
| `GET` | `/actuator/health` | Liveness |

### 5.2 Shared object shapes

**`Category`**

```json
{ "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false }
```

**`Expense`**

```json
{
  "id": 1,
  "date": "2026-05-02",
  "amount": "420.00",
  "vendorName": "Swiggy",
  "description": "Friday dinner",
  "category": { "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false },
  "categorizationSource": "RULE",
  "isAnomaly": false,
  "anomalyReason": null,
  "importBatchId": 1,
  "createdAt": "2026-08-13T22:23:15.086276Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `date` | `string` | `yyyy-MM-dd` |
| `amount` | `string` | decimal, always 2 places |
| `categorizationSource` | `enum` | `RULE` \| `DEFAULT` \| `MANUAL_OVERRIDE` |
| `isAnomaly` | `boolean` | maintained by the sweep |
| `anomalyReason` | `string?` | `AMOUNT_GT_3X_CATEGORY_AVG` when flagged, else `null` |
| `importBatchId` | `number?` | `null` for manually created expenses |

**`Page<T>`** — the envelope on every paged response:

```json
{ "content": [], "page": 0, "size": 25, "totalElements": 118,
  "totalPages": 5, "hasNext": true }
```

---

### 5.3 `GET /api/expenses`

**Query parameters** — all optional.

| Name | Type | Default | Notes |
|---|---|---|---|
| `from` | date | | inclusive lower bound, `yyyy-MM-dd` |
| `to` | date | | inclusive upper bound |
| `categoryId` | number | | exact match |
| `vendor` | string | | case-insensitive substring, matches raw or normalized name |
| `anomalyOnly` | boolean | `false` | flagged rows only |
| `page` | number | `0` | zero-based |
| `size` | number | `25` | clamped to 200 |
| `sort` | string | `expenseDate,desc` | `<property>,<asc\|desc>` |

`sort` is restricted to `expenseDate`, `amount`, `vendorName`, `createdAt`, `anomaly`; anything
else returns `400`. `id desc` is always appended as a tiebreaker so paging stays stable when two
rows share a sort value.

**Request**

```http
GET /api/expenses?from=2026-07-01&to=2026-07-31&categoryId=1&anomalyOnly=false&page=0&size=2
```

**`200 OK`**

```json
{
  "content": [
    {
      "id": 122,
      "date": "2026-08-15",
      "amount": "222.00",
      "vendorName": "Starbucks",
      "description": null,
      "category": { "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false },
      "categorizationSource": "RULE",
      "isAnomaly": false,
      "anomalyReason": null,
      "importBatchId": null,
      "createdAt": "2026-08-14T19:24:01.159371Z"
    },
    {
      "id": 58,
      "date": "2026-08-15",
      "amount": "300.00",
      "vendorName": "Swiggy",
      "description": "Team lunch",
      "category": { "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false },
      "categorizationSource": "RULE",
      "isAnomaly": false,
      "anomalyReason": null,
      "importBatchId": null,
      "createdAt": "2026-08-14T19:04:07.060196Z"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 118,
  "totalPages": 59,
  "hasNext": true
}
```

---

### 5.4 `GET /api/expenses/{id}`

**`200 OK`** — a single `Expense` object (shape above).
**`404 Not Found`** — `NOT_FOUND`.

---

### 5.5 `POST /api/expenses`

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `date` | string | yes | `yyyy-MM-dd`, not future, on/after 2000-01-01 |
| `amount` | string\|number | yes | `> 0`, `≤ 10000000.00`, max 2 decimals |
| `vendorName` | string | yes | 1–120 chars after trim |
| `description` | string | no | ≤ 500 chars |
| `categoryId` | number | no | must exist; **omit or `null` to use the rules** |

```json
{
  "date": "2026-08-01",
  "amount": "450.00",
  "vendorName": "Swiggy",
  "description": "Team lunch",
  "categoryId": null
}
```

**`201 Created`** · header `Location: /api/expenses/{id}`

```json
{
  "id": 1,
  "date": "2026-08-01",
  "amount": "450.00",
  "vendorName": "Swiggy",
  "description": "Team lunch",
  "category": { "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false },
  "categorizationSource": "RULE",
  "isAnomaly": false,
  "anomalyReason": null,
  "importBatchId": null,
  "createdAt": "2026-08-01T10:12:03.397102Z"
}
```

**Side effects:** the vendor name is normalized and stored; the category is resolved; the
expense's category is re-swept for anomalies — all inside one transaction. The returned object
already reflects the sweep, so `isAnomaly` is never stale.

**`400 Bad Request`**

```json
{
  "timestamp": "2026-08-13T03:27:32.638554+05:30",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/expenses",
  "fieldErrors": [
    { "field": "vendorName", "message": "Vendor name is required" },
    { "field": "amount", "message": "Amount must be greater than 0" }
  ]
}
```

---

### 5.6 `PUT /api/expenses/{id}`

Same body and validation as `POST`. Full replacement, not a patch — every field is applied.

**`200 OK`** — the updated `Expense`.

**Side effects:** both the previous and the new category are re-swept, because moving an expense
or changing its amount can change which rows are outliers on **both** sides.

**`404 Not Found`** when the id or a supplied `categoryId` does not exist.

---

### 5.7 `DELETE /api/expenses/{id}`

**`204 No Content`** — empty body. The category is re-swept, so removing a large expense can
re-flag rows that it had been masking.

**`404 Not Found`** if the id does not exist.

---

### 5.8 `POST /api/expenses/import`

`multipart/form-data`, single part named `file`. Max 5 MB / 10,000 data rows.

```http
POST /api/expenses/import
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="file"; filename="expenses.csv"
Content-Type: text/csv

date,amount,vendor,description
2026-08-01,450.00,Swiggy,Team lunch
--boundary--
```

**`200 OK` — clean file**

```json
{
  "batchId": 1,
  "filename": "expenses.csv",
  "totalRows": 56,
  "importedRows": 56,
  "failedRows": 0,
  "status": "COMPLETED",
  "errors": [],
  "warnings": []
}
```

**`200 OK` — file with bad rows.** Note the status is still `200`: the request succeeded, and
the *result* describes partial failure.

```json
{
  "batchId": 2,
  "filename": "expenses-with-errors.csv",
  "totalRows": 10,
  "importedRows": 4,
  "failedRows": 6,
  "status": "COMPLETED_WITH_ERRORS",
  "errors": [
    { "row": 5,  "field": "date",   "message": "Date '2026-06-18' is not a valid date in the format dd/MM/yyyy locked by the first row of this file" },
    { "row": 6,  "field": "amount", "message": "Not a valid number: 'abc'" },
    { "row": 7,  "field": "amount", "message": "Amount must be greater than 0, got '-99.00'" },
    { "row": 8,  "field": "date",   "message": "Date '31/02/2026' is not a valid date in the format dd/MM/yyyy locked by the first row of this file" },
    { "row": 9,  "field": "vendor", "message": "Vendor name is required" },
    { "row": 10, "field": "amount", "message": "Amount '99.999' has more than 2 decimal places" }
  ],
  "warnings": [
    { "row": 4, "message": "Matches an expense already on file (Swiggy, 2026-06-15)" }
  ]
}
```

| Field | Notes |
|---|---|
| `row` | **line number in the uploaded file** — the header is line 1 |
| `status` | `COMPLETED` when `failedRows` is 0, else `COMPLETED_WITH_ERRORS` |
| `warnings` | possible duplicates — reported, **not** dropped |

**`422 Unprocessable Entity`** — the file itself is unusable:

```json
{
  "timestamp": "2026-08-13T09:31:02.113Z",
  "status": 422,
  "error": "CSV_UNPROCESSABLE",
  "message": "Missing required column(s): amount (accepted: amount, debit, price, spend, value). Found header: date, description",
  "path": "/api/expenses/import"
}
```

**`413 Payload Too Large`** — over 5 MB.

---

### 5.9 `GET /api/expenses/import/format`

Serves the parser's own configuration so the UI's format hint cannot drift from what the
importer actually accepts.

**`200 OK`**

```json
{
  "templateHeader": "date,amount,vendor,description",
  "acceptedDateFormats": ["yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"],
  "requiredColumns": ["date", "amount", "vendor"],
  "optionalColumns": ["description", "category"],
  "columnAliases": {
    "DATE": ["date", "expensedate", "transactiondate", "txndate", "valuedate"],
    "AMOUNT": ["amount", "debit", "price", "spend", "value"],
    "VENDOR": ["merchant", "merchantname", "payee", "vendor", "vendorname"],
    "DESCRIPTION": ["description", "memo", "narration", "note", "notes", "particulars", "remarks"],
    "CATEGORY": ["category", "categoryname"]
  },
  "notes": [
    "The date format is locked to whichever supported format the first row uses.",
    "Rows that fail validation are reported and skipped; the rest of the file still imports.",
    "Amounts must be positive and have at most 2 decimal places.",
    "Supplying a category overrides the automatic vendor rules for that row."
  ]
}
```

---

### 5.10 `GET /api/dashboard/summary`

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `month` | `yyyy-MM` | all time | omit for every expense on record |

**`200 OK`**

```json
{
  "month": "2026-08",
  "totalAmount": "38578.00",
  "expenseCount": 26,
  "anomalyCount": 2,
  "topCategoryName": "Food",
  "topCategoryAmount": "21972.00"
}
```

`month` is `null` in the response when the window was all time. An empty month returns
`"0.00"`, zero counts, and `null` category fields — never a missing key.

---

### 5.11 `GET /api/dashboard/monthly-by-category`

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `from` | `yyyy-MM` | 5 months before `to` | inclusive |
| `to` | `yyyy-MM` | current month | inclusive |

Range is capped at 60 months.

**`200 OK`**

```json
{
  "months": ["2026-06", "2026-07", "2026-08"],
  "series": [
    {
      "categoryId": 7,
      "categoryName": "Health",
      "colorHex": "#F15BB5",
      "totals": ["1780.00", "31600.00", "1440.00"]
    },
    {
      "categoryId": 3,
      "categoryName": "Travel",
      "colorHex": "#264653",
      "totals": ["26590.00", "4910.00", "1350.00"]
    }
  ]
}
```

**Contract guarantees** the chart depends on:

- `months` is generated from the requested range, not from the data — no gaps.
- `totals[i]` always corresponds to `months[i]`, and every series has exactly
  `months.length` entries, zero-filled server-side.
- Series are ordered by total spend descending.

---

### 5.12 `GET /api/dashboard/top-vendors`

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `month` | `yyyy-MM` | all time | |
| `limit` | number | `5` | 1–50 |

**`200 OK`**

```json
{
  "vendors": [
    { "vendorName": "Apollo Hospital", "totalAmount": "31600.00", "expenseCount": 2, "topCategory": "Health" },
    { "vendorName": "MakeMyTrip",      "totalAmount": "29200.00", "expenseCount": 4, "topCategory": "Travel" }
  ]
}
```

Grouped on `vendor_normalized`, so casing and punctuation variants collapse into one entry.
`vendorName` and `topCategory` are the **most frequent** raw spelling and category for that
group, computed with PostgreSQL's `mode() WITHIN GROUP`.

---

### 5.13 `GET /api/dashboard/anomalies`

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `page` | number | `0` | |
| `size` | number | `20` | max 200 |

Always sorted by amount descending. **All time**, not per-month.

**`200 OK`**

```json
{
  "content": [
    {
      "expense": {
        "id": 100,
        "date": "2026-07-26",
        "amount": "15800.00",
        "vendorName": "Apollo Hospital",
        "description": "Dental procedure",
        "category": { "id": 7, "name": "Health", "colorHex": "#F15BB5", "isDefault": false },
        "categorizationSource": "RULE",
        "isAnomaly": true,
        "anomalyReason": "AMOUNT_GT_3X_CATEGORY_AVG",
        "importBatchId": 2,
        "createdAt": "2026-08-14T19:13:34.908529Z"
      },
      "categoryAverage": "2900.00",
      "threshold": "8700.00",
      "timesAverage": "5.45"
    }
  ],
  "page": 0,
  "size": 1,
  "totalElements": 6,
  "totalPages": 6,
  "hasNext": true
}
```

| Field | Meaning |
|---|---|
| `categoryAverage` | mean of the **other** expenses in that category (leave-one-out) |
| `threshold` | `categoryAverage × 3` — the amount this expense had to exceed |
| `timesAverage` | `amount ÷ categoryAverage`, for the "5.5×" badge |

Baselines come from **one** grouped query for all categories, so a page costs two queries
regardless of size.

---

### 5.14 `GET /api/categories`

**`200 OK`** — array, ordered by name.

```json
[
  { "id": 6, "name": "Entertainment", "colorHex": "#9B5DE5", "isDefault": false },
  { "id": 1, "name": "Food",          "colorHex": "#E76F51", "isDefault": false },
  { "id": 2, "name": "Groceries",     "colorHex": "#2A9D8F", "isDefault": false },
  { "id": 7, "name": "Health",        "colorHex": "#F15BB5", "isDefault": false },
  { "id": 4, "name": "Shopping",      "colorHex": "#E9C46A", "isDefault": false },
  { "id": 3, "name": "Travel",        "colorHex": "#264653", "isDefault": false },
  { "id": 8, "name": "Uncategorized", "colorHex": "#8D99AE", "isDefault": true  },
  { "id": 5, "name": "Utilities",     "colorHex": "#8AB17D", "isDefault": false }
]
```

---

### 5.15 Vendor rules

#### `GET /api/vendor-rules`

**`200 OK`** — array, active first, then by priority.

```json
[
  {
    "id": 23,
    "pattern": "swiggy instamart",
    "matchType": "CONTAINS",
    "category": { "id": 2, "name": "Groceries", "colorHex": "#2A9D8F", "isDefault": false },
    "priority": 5,
    "active": true
  }
]
```

#### `POST /api/vendor-rules`

| Field | Type | Required | Validation |
|---|---|---|---|
| `pattern` | string | yes | ≤ 120 chars; **normalized before storage** |
| `matchType` | string | yes | `EXACT` \| `CONTAINS` |
| `categoryId` | number | yes | must exist |
| `priority` | number | no | 1–1000, default `100` (lower wins) |
| `active` | boolean | no | default `true` |

```json
{ "pattern": "Chai Point", "matchType": "CONTAINS", "categoryId": 1, "priority": 100 }
```

**`201 Created`** — note `pattern` comes back normalized:

```json
{
  "id": 69,
  "pattern": "chai point",
  "matchType": "CONTAINS",
  "category": { "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false },
  "priority": 100,
  "active": true
}
```

The rule cache is refreshed immediately, so the very next categorization uses it.

**`400 Bad Request`** on a duplicate `(pattern, matchType)`:

```json
{
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "A CONTAINS rule for 'chai point' already exists (id 69)",
  "path": "/api/vendor-rules"
}
```

#### `PUT /api/vendor-rules/{id}`

Same body as `POST`. **`200 OK`** with the updated rule.

#### `DELETE /api/vendor-rules/{id}`

**`204 No Content`**. **Deactivates** (`active = false`) rather than deleting, so an accidental
removal is one toggle away from undone and historical rules remain inspectable.

> Rule changes never re-categorize existing expenses. An expense keeps the category it was filed
> under, and manual overrides are never silently undone.

---

## 6. Error contract

Every failure returns the same body, produced by
[`GlobalExceptionHandler`](backend/src/main/java/com/iconcile/expense/web/error/GlobalExceptionHandler.java).

```json
{
  "timestamp": "2026-08-13T09:22:11.482+05:30",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/expenses",
  "fieldErrors": [
    { "field": "amount", "message": "Amount must be greater than 0" }
  ]
}
```

`fieldErrors` is present only for field-level validation failures; it is omitted otherwise.

| Status | `error` code | Raised by |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation on a request body |
| 400 | `BAD_REQUEST` | bad argument — unknown sort key, malformed month, duplicate rule |
| 400 | `MALFORMED_REQUEST` | unparseable JSON, wrong parameter type, missing multipart part |
| 404 | `NOT_FOUND` | no such expense, category, or rule |
| 413 | `FILE_TOO_LARGE` | upload exceeds 5 MB |
| 422 | `CSV_UNPROCESSABLE` | file unreadable as CSV or missing a required column |
| 500 | `INTERNAL_ERROR` | anything unhandled |

`INTERNAL_ERROR` returns a fixed message; the stack trace goes to the server log only.
`server.error.include-message: never` prevents Spring's default handler from leaking details
through any path this advice does not cover.

---

## 7. Configuration

All settings are environment variables with working defaults — nothing needs editing to run.

### 7.1 Backend

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/expenses` | database |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | |
| `SERVER_PORT` | `8080` | |
| `ANOMALY_MULTIPLIER` | `3.0` | flagging threshold |
| `ANOMALY_MIN_SAMPLE_SIZE` | `3` | other expenses needed before flagging |
| `ANOMALY_LOOKBACK_DAYS` | *(unset)* | averaging window; unset = all time |
| `CSV_MAX_ROWS` | `10000` | import row cap |
| `CSV_BATCH_SIZE` | `500` | rows per flush |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | CORS allowlist |

Fixed in `application.yml`: `spring.jpa.hibernate.ddl-auto: validate` (Flyway owns the schema),
`open-in-view: false`, multipart limits 5 MB / 6 MB.

### 7.2 Test configuration

| Variable | Default |
|---|---|
| `TEST_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/expenses_test` |
| `TEST_DATASOURCE_USERNAME` | `postgres` |
| `TEST_DATASOURCE_PASSWORD` | `postgres` |

Separate from the development datasource, so tests never touch working data.

### 7.3 Frontend

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | request base path |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | dev/preview proxy target |

---

## 8. Source layout

```
iConcile/
├── README.md                     setup, commands, design decisions
├── TECHNICAL_PLAN.md             the design this was built from
├── TESTING_GUIDE.md              non-technical walkthrough
├── TECH_REFERENCE.md             this document
├── sample-data/                  clean CSV + one with deliberate errors
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/iconcile/expense/
│       │   ├── ExpenseManagerApplication.java
│       │   ├── config/           AnomalyProperties, CsvImportProperties, WebConfig
│       │   ├── domain/           Expense, Category, VendorCategoryRule, CsvImportBatch,
│       │   │                     MatchType, CategorizationSource
│       │   ├── repository/       4 repositories, ExpenseSpecifications,
│       │   │                     projection/DashboardProjections
│       │   ├── service/          CategorizationService, AnomalyService, ExpenseService,
│       │   │                     CsvImportService, DashboardService,
│       │   │                     csv/ (CsvField, CsvValueParser, RowFieldException)
│       │   ├── util/             VendorNameNormalizer
│       │   └── web/
│       │       ├── controller/   Expense, CsvImport, Dashboard, VendorRule
│       │       ├── dto/          request/response records
│       │       └── error/        ApiError, GlobalExceptionHandler, exceptions
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/     V1 schema · V2 categories · V3 vendor rules
│       └── test/java/…           136 tests
│
└── frontend/
    ├── vite.config.ts            aliases, dev + preview proxy, vitest config
    ├── tailwind.config.js
    └── src/
        ├── main.tsx              providers: QueryClient, BrowserRouter
        ├── App.tsx               nav + routes
        ├── api/                  client.ts (fetch + Zod), endpoints.ts
        ├── types/schemas.ts      Zod schemas — all TS types inferred from here
        ├── hooks/queries.ts      TanStack Query hooks + invalidation policy
        ├── lib/format.ts         money/date formatting
        ├── components/ui.tsx     Card, Button, StatTile, AnomalyBadge, states
        ├── features/
        │   ├── expenses/         ExpensesPage, ExpenseForm, ExpenseTable
        │   ├── import/           ImportPage
        │   └── dashboard/        DashboardPage, charts.tsx
        └── test/                 setup + render helpers
```

### Test coverage

| Suite | Count | Scope |
|---|---|---|
| Backend unit | 82 | normalizer, categorization, CSV value parsing, header aliases, baseline maths |
| Backend integration | 54 | real PostgreSQL — anomaly SQL, CSV import, dashboard aggregation, full HTTP layer via MockMvc |
| Frontend | 26 | API client, formatting, form validation, anomaly rendering, route smoke tests |
| **Total** | **162** | |

Integration tests run against real PostgreSQL rather than an in-memory substitute, because the
logic most worth testing is PostgreSQL-specific: `date_trunc`, `FILTER (WHERE …)`,
`mode() WITHIN GROUP`, and the `UPDATE … FROM` behind anomaly detection.
