# Mini Expense Manager — Technical Plan

**Version:** 1.0
**Date:** 2026-08-13
**Source requirement:** [req.md](req.md)

---

## 1. Overview

A full-stack application to track daily expenses with automatic rule-based categorization, bulk CSV ingestion, statistical anomaly flagging, and an analytics dashboard.

### 1.1 Scope

| In scope | Out of scope (v1) |
|---|---|
| Manual expense entry | Multi-user auth / accounts |
| CSV bulk upload | Receipt OCR / bank sync |
| Vendor → category mapping (rule-based) | ML-based categorization |
| Anomaly flagging (3× category average) | Budgets, alerts, notifications |
| Dashboard: monthly totals, top vendors, anomalies | Multi-currency, FX conversion |
| Expense list with filters + edit/delete | Recurring expenses, mobile app |

### 1.2 Tech stack

| Layer | Choice | Version |
|---|---|---|
| Frontend | React + TypeScript + Vite | React 18, TS 5.x |
| UI state / data | TanStack Query + React Hook Form + Zod | latest |
| Styling | Tailwind CSS | 3.x |
| Charts | Recharts | 2.x |
| Backend | Java Spring Boot | Java 21, Boot 3.3.x |
| Persistence | Spring Data JPA (Hibernate) | — |
| Migrations | Flyway | 10.x |
| CSV parsing | Apache Commons CSV | 1.11 |
| Database | PostgreSQL | 16 |
| Build | Maven (backend), npm (frontend) | — |
| Testing | JUnit 5 + Testcontainers + MockMvc; Vitest + RTL | — |
| Local orchestration | Docker Compose | — |

**Rationale for key picks:** Flyway over `ddl-auto` because the seed vendor-mapping data needs to ship as versioned migrations. Testcontainers over H2 because the dashboard queries use PostgreSQL-specific aggregation (`date_trunc`) that H2 does not emulate faithfully. TanStack Query because every screen is server-state-driven — a client store (Redux/Zustand) would be pure overhead here.

---

## 2. Architecture

```
┌──────────────────────────────────────────────┐
│  React SPA (Vite dev server / static build)  │
│  ┌────────────┬────────────┬──────────────┐  │
│  │ Add Expense│ CSV Upload │  Dashboard   │  │
│  └────────────┴────────────┴──────────────┘  │
│         api/ client (fetch + Zod parse)      │
└──────────────────────┬───────────────────────┘
                       │ JSON / REST over HTTP
┌──────────────────────▼───────────────────────┐
│           Spring Boot application            │
│  Controller  →  Service  →  Repository       │
│  ┌────────────────────────────────────────┐  │
│  │ ExpenseService                         │  │
│  │ CategorizationService (rule engine)    │  │
│  │ AnomalyService (3× rule)               │  │
│  │ CsvImportService (parse + validate)    │  │
│  │ DashboardService (aggregations)        │  │
│  └────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────┘
                       │ JDBC
┌──────────────────────▼───────────────────────┐
│  PostgreSQL: expense, category,              │
│  vendor_category_rule, csv_import_batch      │
└──────────────────────────────────────────────┘
```

Standard layered architecture. Controllers handle HTTP + DTO mapping only; all business rules live in services; repositories are the sole DB touchpoint. Entities never cross the controller boundary — DTOs are explicit records.

### 2.1 Package layout (backend)

```
com.iconcile.expense
├── ExpenseManagerApplication.java
├── config/          WebConfig (CORS), JacksonConfig, OpenApiConfig
├── domain/          Expense, Category, VendorCategoryRule, CsvImportBatch  (@Entity)
├── repository/      ExpenseRepository, CategoryRepository,
│                    VendorCategoryRuleRepository, CsvImportBatchRepository
├── service/         ExpenseService, CategorizationService, AnomalyService,
│                    CsvImportService, DashboardService
├── web/
│   ├── controller/  ExpenseController, CsvImportController,
│                    DashboardController, VendorRuleController
│   ├── dto/         request/response records
│   └── error/       GlobalExceptionHandler, ApiError
└── util/            AmountUtils, VendorNameNormalizer
```

### 2.2 Frontend layout

```
src/
├── main.tsx, App.tsx, router.tsx
├── api/             client.ts, expenses.ts, dashboard.ts, imports.ts
├── types/           schemas.ts  (Zod schemas + inferred TS types)
├── hooks/           useExpenses, useCreateExpense, useDashboard, useCsvUpload
├── features/
│   ├── expenses/    ExpenseForm, ExpenseTable, ExpenseFilters, AnomalyBadge
│   ├── import/      CsvUploader, ImportResultPanel
│   └── dashboard/   MonthlyCategoryChart, TopVendorsChart, AnomalyList, StatTiles
└── components/      ui primitives (Button, Input, Card, Table, Toast, EmptyState)
```

---

## 3. Data model

### 3.1 ER summary

```
category (1) ──< (N) expense >── (N) [name match] ── vendor_category_rule
csv_import_batch (1) ──< (N) expense
```

### 3.2 Tables

**`category`**
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| name | TEXT NOT NULL UNIQUE | e.g. `Food`, `Travel` |
| color_hex | TEXT | for chart consistency |
| is_default | BOOLEAN NOT NULL DEFAULT false | exactly one row true → `Uncategorized` |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

**`vendor_category_rule`**
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| pattern | TEXT NOT NULL | normalized match token, e.g. `swiggy` |
| match_type | TEXT NOT NULL | `EXACT` \| `CONTAINS` |
| category_id | BIGINT NOT NULL FK → category | |
| priority | INT NOT NULL DEFAULT 100 | lower wins on tie |
| active | BOOLEAN NOT NULL DEFAULT true | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Unique constraint: `(pattern, match_type)`.

**`expense`**
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| expense_date | DATE NOT NULL | |
| amount | NUMERIC(14,2) NOT NULL CHECK (amount > 0) | never `double` |
| vendor_name | TEXT NOT NULL | as entered, for display |
| vendor_normalized | TEXT NOT NULL | lowercased/trimmed, for matching + grouping |
| description | TEXT | nullable |
| category_id | BIGINT NOT NULL FK → category | |
| categorization_source | TEXT NOT NULL | `RULE` \| `DEFAULT` \| `MANUAL_OVERRIDE` |
| is_anomaly | BOOLEAN NOT NULL DEFAULT false | materialized flag |
| anomaly_reason | TEXT | e.g. `AMOUNT_GT_3X_CATEGORY_AVG` |
| anomaly_evaluated_at | TIMESTAMPTZ | |
| import_batch_id | BIGINT FK → csv_import_batch | null for manual entries |
| created_at / updated_at | TIMESTAMPTZ NOT NULL | |

**`csv_import_batch`**
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| filename | TEXT NOT NULL | |
| total_rows / imported_rows / failed_rows | INT NOT NULL | |
| status | TEXT NOT NULL | `COMPLETED` \| `COMPLETED_WITH_ERRORS` \| `FAILED` |
| error_report | JSONB | array of `{row, field, message}` |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

### 3.3 Indexes

```sql
CREATE INDEX idx_expense_date          ON expense (expense_date DESC);
CREATE INDEX idx_expense_category_date ON expense (category_id, expense_date);
CREATE INDEX idx_expense_vendor_norm   ON expense (vendor_normalized);
CREATE INDEX idx_expense_anomaly       ON expense (is_anomaly) WHERE is_anomaly = true;
CREATE INDEX idx_rule_pattern_active   ON vendor_category_rule (pattern) WHERE active = true;
```

### 3.4 Flyway migrations

| File | Contents |
|---|---|
| `V1__schema.sql` | all four tables, constraints, indexes |
| `V2__seed_categories.sql` | Food, Travel, Shopping, Utilities, Entertainment, Health, Groceries, Uncategorized |
| `V3__seed_vendor_rules.sql` | ~40 seed rules (see §4.2) |

---

## 4. Rule-based categorization

### 4.1 Algorithm

`CategorizationService.categorize(String vendorName) → CategorizationResult(categoryId, source)`

1. **Normalize** the vendor name: trim, collapse internal whitespace, lowercase, strip punctuation and common corporate suffixes (`pvt`, `ltd`, `inc`, `llc`, `technologies`), strip payment-gateway noise prefixes (`upi/`, `pos `, `neft-`, trailing transaction ids).
   `"SWIGGY*ORDER 8823 BLR"` → `swiggy order 8823 blr`
2. **EXACT pass** — look up an active rule whose `pattern` equals the normalized string. First match wins.
3. **CONTAINS pass** — find active `CONTAINS` rules whose pattern is a substring of the normalized string. Order by `priority ASC`, then by `LENGTH(pattern) DESC` so the most specific pattern wins (`uber eats` beats `uber`).
4. **Fallback** — the `is_default` category (`Uncategorized`), source `DEFAULT`.

**Caching:** all active rules are loaded into memory once and held in a `CategorizationRuleCache` (`@PostConstruct` load + explicit `evict()` on rule mutation). Rule volume is small (hundreds), and CSV import calls this per row — a DB round-trip per row would dominate import time. Matching is then pure in-memory string work.

### 4.2 Seed rules (illustrative subset)

| Pattern | Match | Category |
|---|---|---|
| swiggy, zomato, dominos, starbucks, mcdonalds, kfc | CONTAINS | Food |
| uber eats, swiggy instamart | CONTAINS (priority 10) | Food |
| uber, ola, rapido, irctc, indigo, makemytrip | CONTAINS | Travel |
| amazon, flipkart, myntra, ajio, nykaa | CONTAINS | Shopping |
| bigbasket, blinkit, zepto, dmart, reliance fresh | CONTAINS | Groceries |
| airtel, jio, tata power, bescom, act fibernet | CONTAINS | Utilities |
| netflix, spotify, bookmyshow, hotstar, prime video | CONTAINS | Entertainment |
| apollo, pharmeasy, 1mg, practo, cult fit | CONTAINS | Health |

The `uber eats` / `uber` pair is the canonical priority test case and must be covered by a unit test.

### 4.3 Manual override

The create/update expense request accepts an optional `categoryId`. When present it wins over the rule engine, and `categorization_source` is stored as `MANUAL_OVERRIDE`. Overrides are never retroactively changed by rule edits.

### 4.4 Rule management

`VendorRuleController` exposes CRUD over `vendor_category_rule` so mappings are data, not code. Creating or updating a rule does **not** re-categorize historical expenses in v1; a `POST /api/vendor-rules/reapply` endpoint is listed as a stretch item (§11).

---

## 5. Anomaly detection

### 5.1 Rule

> An expense is an anomaly if `amount > 3 × (average amount of all other expenses in the same category)`.

### 5.2 Design decisions (and why)

These are the ambiguities in the one-line requirement; each is resolved explicitly.

| Question | Decision | Reason |
|---|---|---|
| Does the average include the expense itself? | **Excluded** (leave-one-out) | A large expense inflates its own baseline and can suppress its own flag. Excluding it makes the test "is this unusual versus everything else?" |
| Average over what window? | **All time, whole category** | Requirement says "the average amount for its category" with no window. A configurable `anomaly.lookback-days` property is added (default `null` = all time) so this can change without a code edit. |
| Minimum sample size? | **≥ 3 other expenses in the category**, else not an anomaly | With one prior expense, any amount over 3× it trips the rule — noise, not signal. Threshold exposed as `anomaly.min-sample-size`. |
| Stored or computed on read? | **Stored** on `expense.is_anomaly`, recomputed on writes | Dashboard and list views both need it; recomputing per request means a correlated aggregate per row. |
| Does a new expense change others' flags? | **Yes** — a new expense shifts the category average, so the whole category is re-evaluated | Otherwise flags drift out of sync with the data and the dashboard count becomes wrong. |

### 5.3 Implementation

`AnomalyService.reevaluateCategory(Long categoryId)`:

```sql
WITH stats AS (
  SELECT SUM(amount) AS total, COUNT(*) AS cnt
  FROM expense WHERE category_id = :categoryId
)
UPDATE expense e
SET is_anomaly = new_flag,
    anomaly_reason = CASE WHEN new_flag THEN 'AMOUNT_GT_3X_CATEGORY_AVG' END,
    anomaly_evaluated_at = now()
FROM (
  SELECT e2.id,
         (s.cnt - 1) >= :minSample
     AND e2.amount > :multiplier * ((s.total - e2.amount) / NULLIF(s.cnt - 1, 0))
         AS new_flag
  FROM expense e2 CROSS JOIN stats s
  WHERE e2.category_id = :categoryId
) calc
WHERE e.id = calc.id AND e.is_anomaly IS DISTINCT FROM calc.new_flag;
```

One set-based statement per affected category — no N+1, no row loop in Java. The leave-one-out average is derived from the category sum/count rather than a per-row subquery.

**Triggers for re-evaluation** (all inside the same transaction as the write):
- Create expense → re-evaluate its category.
- Update expense → re-evaluate old and new category if the category or amount changed.
- Delete expense → re-evaluate its category.
- CSV import → collect the distinct set of affected categories, re-evaluate each **once** after all rows are inserted (not per row).

**Configuration** (`application.yml`):
```yaml
anomaly:
  multiplier: 3.0
  min-sample-size: 3
  lookback-days: null
```

---

## 6. CSV upload

### 6.1 Expected format

Header row required, case-insensitive, order-independent:

```csv
date,amount,vendor,description
2026-08-01,450.00,Swiggy,Team lunch
2026-08-02,1200,Uber,Airport drop
```

- **Accepted header aliases:** `date`/`expense_date`/`transaction_date`; `amount`/`value`; `vendor`/`vendor_name`/`merchant`; `description`/`notes`/`remarks`.
- **Accepted date formats:** `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`. Format is detected from the first successfully parsed row and then enforced for the remainder of the file, so `01/02/2026` cannot silently mean two different dates within one upload.
- **Amount:** strips currency symbols, thousands separators, and surrounding whitespace; `(123.45)` is rejected rather than read as negative.
- **Optional `category` column:** if present and it names an existing category, it is applied as `MANUAL_OVERRIDE`; unknown names are a row error.

### 6.2 Processing

`POST /api/expenses/import` — `multipart/form-data`, field `file`.

1. **Guardrails:** max size 5 MB (`spring.servlet.multipart.max-file-size`), extension must be `.csv`, content sniffed as text. Max 10,000 data rows.
2. **Stream-parse** with Apache Commons CSV (`withFirstRecordAsHeader`, `withIgnoreSurroundingSpaces`) — never load the whole file into a `List<String>`.
3. **Per-row validation** → on failure, record `{row, field, message}` and continue. A bad row never aborts the import.
4. **Categorize + insert** valid rows in batches of 500 (`hibernate.jdbc.batch_size=500`).
5. After insert, re-evaluate anomalies for the affected category set.
6. Persist a `csv_import_batch` row with counts and the error report.

**Transaction boundary:** the whole import is one transaction. Either all valid rows land together or none do — a partial import that a user cannot identify or undo is worse than a clean failure. Because rows are validated individually and skipped rather than thrown, the common "some rows are malformed" case still succeeds.

**Duplicate handling:** rows identical on `(expense_date, amount, vendor_normalized, description)` to an existing expense are imported anyway but returned in the response under `warnings` as possible duplicates. Silent de-duplication would discard genuine same-day repeat purchases.

### 6.3 Response

```json
{
  "batchId": 12,
  "filename": "august.csv",
  "totalRows": 120,
  "importedRows": 117,
  "failedRows": 3,
  "status": "COMPLETED_WITH_ERRORS",
  "errors": [
    { "row": 14, "field": "amount", "message": "Not a valid positive number: 'abc'" },
    { "row": 52, "field": "date",   "message": "Unparseable date: '31/31/2026'" },
    { "row": 88, "field": "vendor", "message": "Vendor name is required" }
  ],
  "warnings": [
    { "row": 20, "message": "Possible duplicate of expense #431" }
  ]
}
```

---

## 7. REST API

Base path `/api`. All responses JSON. Money serialized as a decimal string to avoid float drift in JS.

### 7.1 Expenses

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/expenses` | Create one expense |
| `GET` | `/api/expenses` | List, paged + filtered |
| `GET` | `/api/expenses/{id}` | Fetch one |
| `PUT` | `/api/expenses/{id}` | Update |
| `DELETE` | `/api/expenses/{id}` | Delete |
| `POST` | `/api/expenses/import` | CSV upload |

**`GET /api/expenses` query params:** `from`, `to` (ISO dates), `categoryId`, `vendor` (substring), `anomalyOnly` (bool), `page` (0-based), `size` (default 25, max 200), `sort` (default `expenseDate,desc`).

**`POST /api/expenses` request:**
```json
{
  "date": "2026-08-01",
  "amount": "450.00",
  "vendorName": "Swiggy",
  "description": "Team lunch",
  "categoryId": null
}
```

**Expense response:**
```json
{
  "id": 431,
  "date": "2026-08-01",
  "amount": "450.00",
  "vendorName": "Swiggy",
  "description": "Team lunch",
  "category": { "id": 1, "name": "Food", "colorHex": "#E76F51" },
  "categorizationSource": "RULE",
  "isAnomaly": false,
  "anomalyReason": null,
  "createdAt": "2026-08-01T10:12:03Z"
}
```

### 7.2 Dashboard

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/dashboard/summary?month=2026-08` | Stat tiles: total spend, expense count, anomaly count, top category |
| `GET` | `/api/dashboard/monthly-by-category?from=2026-01&to=2026-08` | Monthly totals per category |
| `GET` | `/api/dashboard/top-vendors?limit=5&month=2026-08` | Top vendors by total spend |
| `GET` | `/api/dashboard/anomalies?page=0&size=20` | Anomaly list with context |

All month params are `yyyy-MM`; omitting `month` means all time.

**`monthly-by-category` response:**
```json
{
  "months": ["2026-06", "2026-07", "2026-08"],
  "series": [
    { "categoryId": 1, "categoryName": "Food",   "colorHex": "#E76F51",
      "totals": ["12400.00", "9800.50", "15200.00"] },
    { "categoryId": 2, "categoryName": "Travel", "colorHex": "#2A9D8F",
      "totals": ["4300.00", "0.00", "8750.25"] }
  ]
}
```
Months with no spend are zero-filled server-side so the chart does not have to reconcile ragged series.

**`top-vendors` response:**
```json
{
  "vendors": [
    { "vendorName": "Swiggy", "totalAmount": "8420.00", "expenseCount": 21,
      "topCategory": "Food" }
  ]
}
```
Grouped on `vendor_normalized`; the display name is the most frequently used raw spelling for that normalized key, so `SWIGGY` and `Swiggy` collapse into one bar.

**`anomalies` response** — each item carries the comparison context the UI needs to explain the flag:
```json
{
  "content": [
    { "expense": { "...": "expense object" },
      "categoryAverage": "512.40",
      "threshold": "1537.20",
      "timesAverage": "4.2" }
  ],
  "page": 0, "size": 20, "totalElements": 7
}
```

### 7.3 Vendor rules & categories

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/categories` | List categories |
| `GET` | `/api/vendor-rules` | List rules |
| `POST` | `/api/vendor-rules` | Create rule |
| `PUT` | `/api/vendor-rules/{id}` | Update rule |
| `DELETE` | `/api/vendor-rules/{id}` | Deactivate rule |

### 7.4 Error contract

`GlobalExceptionHandler` returns RFC 7807-style bodies for every failure path:

```json
{
  "timestamp": "2026-08-13T09:22:11Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/expenses",
  "fieldErrors": [ { "field": "amount", "message": "must be greater than 0" } ]
}
```

| Exception | Status | Code |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| `CsvParseException` | 422 | `CSV_UNPROCESSABLE` |
| `MaxUploadSizeExceededException` | 413 | `FILE_TOO_LARGE` |
| `Exception` | 500 | `INTERNAL_ERROR` (details logged, not returned) |

### 7.5 Validation rules

| Field | Constraint |
|---|---|
| `date` | required, not in the future, not before 2000-01-01 |
| `amount` | required, `> 0`, `<= 10,000,000`, max 2 decimal places |
| `vendorName` | required, 1–120 chars after trim |
| `description` | optional, ≤ 500 chars |
| `categoryId` | optional, must exist if present |

---

## 8. Frontend

### 8.1 Screens

**1. Expenses (`/`)** — Add-expense form (inline, collapsible) above a filterable, paginated table. Columns: date, vendor, category chip, description, amount, anomaly badge, row actions. Filters: date range, category, vendor search, "anomalies only" toggle.

**2. Import (`/import`)** — Drag-and-drop CSV zone with a downloadable template and a visible format spec. After upload: a result panel with imported/failed counts, an expandable per-row error table, and duplicate warnings. Errors are copyable so the user can fix the source file.

**3. Dashboard (`/dashboard`)** —
- Four stat tiles: total spend (selected month), expense count, anomaly count, top category.
- Stacked bar chart: monthly totals per category, last 6 months, one bar per month segmented by category.
- Horizontal bar chart: top 5 vendors by total spend.
- Anomaly panel: list of flagged expenses with `4.2× the Food average` phrasing and a link to the expense row.
- Month selector driving all four sections.

### 8.2 Anomaly presentation

Anomalies must be *distinct*, not merely labelled:
- Table rows get a left amber border and a tinted background.
- The amount cell shows a `⚠ 4.2×` badge with a tooltip: *"₹2,150 is 4.2× the Food category average of ₹512"*.
- A dashboard tile shows the live anomaly count and deep-links to the filtered list.
- Colour is never the only channel — the badge carries text and an icon, and rows expose `aria-label="anomalous expense"`.

### 8.3 Data layer

- One typed API client (`api/client.ts`) that centralizes base URL, JSON handling, and error normalization into an `ApiError`.
- Every response is parsed through a **Zod schema**; TS types are inferred from those schemas, so a backend contract change surfaces as a runtime parse error at the boundary instead of an `undefined` deep inside a chart.
- Amounts arrive as strings and are held as strings; formatting for display uses `Intl.NumberFormat`. No arithmetic in the client.
- TanStack Query keys: `['expenses', filters]`, `['dashboard', 'summary', month]`, etc. Creating, updating, importing, or deleting invalidates both `['expenses']` and `['dashboard']` — an import changes anomaly flags across the board, so partial invalidation would show stale numbers.

### 8.4 UX states

Every data surface implements four states explicitly: loading (skeletons, not spinners), empty (with a primary action — "Add your first expense" / "Upload a CSV"), error (message + retry), and populated. Form submission disables the button and shows inline field errors mapped from `fieldErrors`.

---

## 9. Testing

### 9.1 Backend

| Layer | Tool | Coverage targets |
|---|---|---|
| Unit | JUnit 5 + AssertJ | `VendorNameNormalizer` (noise prefixes, suffixes, casing); `CategorizationService` (exact > contains, `uber eats` vs `uber`, unknown → Uncategorized); CSV row parsers (all date formats, malformed amounts) |
| Service + repo | Testcontainers PostgreSQL | `AnomalyService`: below threshold, exactly 3×, above 3×, sample size < 3, flag flips when a new expense raises the average, flag clears on delete |
| Web | MockMvc | validation 400s, 404 shape, CSV multipart happy path + mixed-error path, pagination |
| Aggregation | Testcontainers | monthly zero-fill correctness, vendor grouping across casing variants, top-5 tie-breaking |

**Critical edge cases to test explicitly:**
- Empty category (no expenses) → no division by zero.
- Single-expense category → never anomalous.
- All expenses identical → none anomalous (ratio 1.0).
- CSV with only a header row → `totalRows: 0`, status `COMPLETED`.
- CSV with BOM, CRLF line endings, and quoted commas inside descriptions.
- Amount `0.00` and negative amounts → rejected.

### 9.2 Frontend

- Vitest + React Testing Library for `ExpenseForm` validation, `ExpenseTable` anomaly rendering, `CsvUploader` error-panel rendering.
- MSW to mock the API so tests do not need a backend.
- One Playwright smoke test (stretch): add expense → see it in the table → see it counted on the dashboard.

---

## 10. Local setup & delivery

### 10.1 Repository layout

```
iConcile/
├── README.md                 setup, API docs, design decisions
├── TECHNICAL_PLAN.md         this file
├── docker-compose.yml        postgres + (optional) backend + frontend
├── sample-data/expenses.csv  ~50 rows incl. 3 deliberate anomalies + 3 bad rows
├── backend/                  Maven project
└── frontend/                 Vite project
```

### 10.2 Running

```bash
docker compose up -d db          # PostgreSQL 16 on :5432
cd backend  && ./mvnw spring-boot:run    # :8080, Flyway migrates + seeds on boot
cd frontend && npm install && npm run dev # :5173, proxies /api → :8080
```

CORS is configured for `http://localhost:5173` in dev via `WebConfig`; the Vite dev proxy is the primary path so the browser sees a same-origin app.

### 10.3 Configuration

| Property | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/expenses` | DB |
| `ANOMALY_MULTIPLIER` | `3.0` | anomaly threshold |
| `ANOMALY_MIN_SAMPLE_SIZE` | `3` | minimum peers to flag |
| `CSV_MAX_ROWS` | `10000` | import guardrail |
| `VITE_API_BASE_URL` | `/api` | frontend base URL |

### 10.4 README contents

Setup steps, the API table from §7, the CSV format spec from §6.1, and — most importantly — a **Design Decisions** section restating §5.2 so the reviewer sees the ambiguities were noticed and resolved deliberately rather than by accident.

---

## 11. Build order

| # | Milestone | Deliverable |
|---|---|---|
| 1 | Scaffold | Boot app + Vite app + Docker Compose, `/actuator/health` green |
| 2 | Schema | Flyway V1–V3, entities, repositories, seed data verified in psql |
| 3 | Categorization | Normalizer + rule engine + rule cache, fully unit tested |
| 4 | Expense CRUD | Controller, DTOs, validation, error handler, MockMvc tests |
| 5 | Anomaly engine | `AnomalyService` + re-evaluation hooks, Testcontainers tests |
| 6 | CSV import | Streaming parser, row errors, batch record, transaction semantics |
| 7 | Dashboard API | Three aggregation endpoints with zero-fill and vendor grouping |
| 8 | Frontend shell | Router, API client, Zod schemas, UI primitives |
| 9 | Expenses UI | Form + table + filters + anomaly styling |
| 10 | Import UI | Uploader + result panel |
| 11 | Dashboard UI | Stat tiles + two charts + anomaly panel |
| 12 | Polish | Empty/loading/error states, sample CSV, README, final pass |

Milestones 1–7 are independently demoable via HTTP before any UI exists; 8–11 consume a contract that is already frozen and tested.

### Stretch items (only after 1–12 are complete)
- `POST /api/vendor-rules/reapply` to re-categorize historical expenses after a rule change.
- Statistically stronger anomaly detection (z-score or MAD) alongside the 3× rule, selectable by config.
- Export filtered expenses to CSV.
- Playwright end-to-end smoke test.

---

## 12. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Anomaly recompute on every write becomes slow with large categories | Slow POSTs | Single set-based UPDATE per category (§5.3); indexed on `category_id`; move to async recompute only if measured p95 exceeds 200 ms |
| Large CSV holds one long transaction | Lock contention | 5 MB / 10k-row cap; batched inserts; single-user app in v1 so contention is theoretical |
| Vendor names too noisy for substring rules | Poor auto-categorization | Aggressive normalization + `Uncategorized` fallback + manual override + editable rules — nothing is a dead end for the user |
| Float rounding on money | Wrong totals | `NUMERIC(14,2)` in PG, `BigDecimal` in Java, decimal **strings** over the wire, no client-side arithmetic |
| Ambiguous dates in CSV (`01/02/2026`) | Silently wrong data | Format locked from the first parsed row and enforced for the file; ambiguity surfaced as a row error rather than a guess |
