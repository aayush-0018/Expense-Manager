# Mini Expense Manager

Track daily expenses with automatic rule-based categorization, bulk CSV import, statistical
anomaly detection, and a dashboard.

**Stack:** React 19 + TypeScript (Vite) · Java 21 + Spring Boot 3.3 · PostgreSQL

- [Deployment](DEPLOYMENT.md) — putting it on Render (database, API, static site)
- [Technical reference](TECH_REFERENCE.md) — stack, database tables, every API with request/response
- [Testing guide](TESTING_GUIDE.md) — click-by-click walkthrough of every feature, no code
- [Technical plan](TECHNICAL_PLAN.md) — the design this was built from
- [Design decisions](#design-decisions) — the judgement calls the requirement left open

---

## Quick start

**Prerequisites:** Java 21 or later, Maven 3.9+, Node 20+, a running PostgreSQL.

### 1. Set up PostgreSQL

You need a running PostgreSQL and two databases. Install one if you have neither:

```bash
brew install postgresql@16 && brew services start postgresql@16   # Homebrew
# or install Postgres.app from https://postgresapp.com and press Start
```

Create the role and databases. The `CREATE ROLE` line is only needed if a `postgres` login role
does not already exist — Homebrew installs create a role named after your macOS user instead:

```bash
psql -d postgres -c "CREATE ROLE postgres LOGIN SUPERUSER PASSWORD 'postgres';"
psql -d postgres -c "CREATE DATABASE expenses      OWNER postgres;"
psql -d postgres -c "CREATE DATABASE expenses_test OWNER postgres;"   # for the test suite
```

Verify before starting the app:

```bash
psql -h localhost -p 5432 -U postgres -d expenses -c "SELECT current_database();"
```

**No manual DDL is needed.** Flyway creates every table and seeds the categories and vendor
rules the first time the backend boots.

<details>
<summary>Using Postgres.app? Read this first.</summary>

Postgres.app gates each new client behind a macOS permission dialog. The first `psql` command
and the first backend start will each block until you approve it — a hung connection with no
error usually means that dialog is waiting behind another window. Turn it off permanently in
**Postgres.app → Settings → App Permissions**.
</details>

<details>
<summary>Different credentials, port, or host?</summary>

Pass them as environment variables rather than editing files:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/expenses \
SPRING_DATASOURCE_USERNAME=myuser \
SPRING_DATASOURCE_PASSWORD=mypass \
mvn spring-boot:run
```

The test suite reads `TEST_DATASOURCE_URL`, `TEST_DATASOURCE_USERNAME` and
`TEST_DATASOURCE_PASSWORD` independently, so tests never touch your development data.
</details>

<details>
<summary>Resetting the data</summary>

```bash
# Wipe expenses, keep categories and vendor rules
psql -U postgres -d expenses -c "TRUNCATE expense, csv_import_batch RESTART IDENTITY CASCADE;"

# Start completely over — Flyway re-migrates and re-seeds on the next boot
psql -U postgres -d postgres -c "DROP DATABASE expenses;"
psql -U postgres -d postgres -c "CREATE DATABASE expenses OWNER postgres;"
```

Useful inspection queries:

```bash
psql -U postgres -d expenses -c "SELECT count(*) FROM expense;"
psql -U postgres -d expenses -c "SELECT vendor_name, amount FROM expense WHERE is_anomaly;"
psql -U postgres -d expenses -c "SELECT version, description, success FROM flyway_schema_history;"
```
</details>

### 2. Backend → http://localhost:8080

```bash
cd backend
mvn spring-boot:run
```

### 3. Frontend → http://localhost:5173

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to `:8080`, so the browser sees a single origin and CORS
never comes into play.

### 4. Load the sample data

Open **Import CSV** and upload [`sample-data/expenses.csv`](sample-data/expenses.csv) — 56 rows
across four months, containing three deliberate anomalies. Then upload
[`sample-data/expenses-with-errors.csv`](sample-data/expenses-with-errors.csv) to see the
row-level error reporting: 4 rows import, 6 are reported and skipped.

---

## Commands

Run each from its own directory (`backend/` or `frontend/`).

### Backend

| Command | What it does |
|---|---|
| `mvn spring-boot:run` | Run in dev mode on :8080 (applies migrations on boot) |
| `mvn clean package` | Build the executable jar → `target/expense-manager-1.0.0.jar` |
| `java -jar target/expense-manager-1.0.0.jar` | Run the built jar |
| `mvn test` | Full suite — 136 tests (needs `expenses_test` database) |
| `mvn test -DskipITs` | Unit tests only — 82 tests, no database needed |
| `mvn clean` | Remove build output |

`mvn package` runs the tests as part of the build; add `-DskipTests` to skip them, or
`-DskipITs` to build with unit tests only.

### Frontend

| Command | What it does |
|---|---|
| `npm install` | Install dependencies (first time only) |
| `npm run dev` | Dev server on :5173 with hot reload, proxying `/api` → :8080 |
| `npm run build` | Typecheck + production bundle → `dist/` |
| `npm run preview` | Serve the built bundle on :4173 (also proxies `/api`) |
| `npm test` | Run the 26 tests once |
| `npm run test:watch` | Tests in watch mode |
| `npm run typecheck` | Typecheck without building |

### Both at once

Two terminals, backend first:

```bash
# terminal 1
cd backend && mvn spring-boot:run

# terminal 2
cd frontend && npm run dev
```

Then open http://localhost:5173.

### Notes

- **JDK:** builds and runs on Java 21 or later; verified on 21 and 25. The jar targets 21.
- **PostgreSQL must be running before the backend starts** — Flyway applies migrations during
  startup and the app will not boot without it.
- **Integration tests need the `expenses_test` database.** Use `mvn test -DskipITs` if you have
  not created it.

---

## Tests

```bash
cd backend  && mvn test              # 136 tests (82 unit + 54 integration)
cd backend  && mvn test -DskipITs    # unit tests only, no database needed
cd frontend && npm test              # 26 tests
cd frontend && npm run typecheck
```

Integration tests run against a real PostgreSQL (`expenses_test`), not an in-memory
substitute — the logic most worth testing lives in PostgreSQL-specific SQL (`date_trunc`,
`FILTER (WHERE …)`, `mode() WITHIN GROUP`, and the `UPDATE … FROM` behind anomaly detection),
none of which H2 reproduces faithfully. Point them elsewhere with `TEST_DATASOURCE_URL`.

---

## Features

### Add an expense manually
Date, amount, vendor, description. The category is assigned automatically from the vendor
name; picking one explicitly overrides the rules for that expense and is never silently undone
by later rule edits.

### CSV upload
Header row required; column order and spelling are flexible (`vendor` / `merchant` /
`payee`, `amount` / `value` / `debit`, and so on). A bad row is reported with its **file line
number** and skipped — the rest of the file still imports.

```csv
date,amount,vendor,description
2026-08-01,450.00,Swiggy,Team lunch
2026-08-02,1200.00,Uber,Airport drop
```

- **Dates:** `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`. The format is **locked** to
  whichever one the first row uses, and enforced for the rest of the file.
- **Amounts:** currency symbols and thousands separators are stripped (`Rs. 1,234.50` → `1234.50`).
  Negative, zero, and more-than-2-decimal values are refused.
- **Optional `category` column:** overrides the vendor rules for that row.
- **Handles** UTF-8 BOM, CRLF endings, quoted commas, and blank lines.
- **Limits:** 5 MB, 10,000 rows.

### Rule-based categorization
Vendor names are normalized before matching, so real statement lines work as-is:

```
"UPI/SWIGGY*ORDER 8823 BLR"   →  swiggy order blr  →  Food
"POS 4412 UBER INDIA PVT LTD" →  uber india        →  Travel
"Amazon Retail Pvt Ltd"       →  amazon retail     →  Shopping
```

Resolution order: exact match → substring match (most specific first) → `Uncategorized`.
"Most specific" means explicit priority first, then longest pattern — which is how
`uber eats` lands in Food rather than being swallowed by the `uber` → Travel rule, and how
`swiggy instamart` reaches Groceries instead of Food.

~68 seed rules ship in [`V3__seed_vendor_rules.sql`](backend/src/main/resources/db/migration/V3__seed_vendor_rules.sql).
Rules are data, editable through `/api/vendor-rules`, and cached in memory because CSV import
calls the matcher once per row.

### Anomaly detection
An expense is flagged when it exceeds **3× the average for its category**. Flagged rows are
distinct on three independent channels — a tinted row, a left rule, and a text badge carrying
the multiple — so the signal survives greyscale, colour blindness, and screen readers. The
dashboard explains each one: *"₹15,800 is 21.1× the Health average of ₹750"*.

### Dashboard
Monthly totals per category (stacked bars, gap-free axis), top 5 vendors by spend, live anomaly
count linking through to the filtered list, and headline tiles — all driven by one month picker.

---

## Design decisions

The requirement fixes the 3× rule but leaves several things open. Each was resolved
deliberately, and each is configuration rather than a constant.

### 1. The average excludes the expense being tested

A leave-one-out baseline. Including the expense in its own average lets a large outlier inflate
the very number it is measured against — the bigger the outlier, the harder it becomes to
detect, which is backwards.

> Four expenses of ₹100 and one of ₹900. Including it: the average is ₹200 and 900 < 3 × 200,
> so it passes unnoticed. Excluding it: the average is ₹100 and it is flagged at 9×.

### 2. A category needs at least 3 other expenses before anything is flagged

With a single prior expense, anything over 3× it trips the rule — that is noise, not signal.
Configurable via `anomaly.min-sample-size`.

### 3. Flags are stored, and the whole category is re-swept on every write

Adding, editing, or deleting an expense moves the category average, which can flip *other* rows
in or out of anomaly state. Without the sweep, the dashboard's anomaly count drifts away from
the data it claims to summarize. This is verifiable in the running app: add three ₹12,000 Health
expenses and the ₹15,800 outlier stops being an anomaly; delete them and it comes back.

The sweep is one set-based `UPDATE` per affected category — not a row loop — so a CSV import
touching six categories costs six statements regardless of row count.

### 4. A CSV import is one transaction, but bad rows do not abort it

Rows are validated individually and failures are collected into a report. Only a file that
cannot be read at all (missing a required column, not a CSV) fails outright. A partial import
the user cannot identify or undo would be worse than either outcome.

### 5. Duplicates are reported, never dropped

Two identical coffees on the same day are a perfectly normal pair of expenses. The importer
flags rows matching something already on file, and rows repeating within the upload, but
imports them anyway.

### 6. Money is `BigDecimal` / `NUMERIC(14,2)` end to end

Amounts cross the wire as decimal **strings** and stay strings in the client, which formats but
never computes. No float touches a monetary value at any layer.

### Configuration

| Variable | Default | Effect |
|---|---|---|
| `ANOMALY_MULTIPLIER` | `3.0` | Flagging threshold |
| `ANOMALY_MIN_SAMPLE_SIZE` | `3` | Other expenses needed before flagging |
| `ANOMALY_LOOKBACK_DAYS` | *(unset)* | Averaging window; unset means all time |
| `CSV_MAX_ROWS` | `10000` | Import row cap |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/expenses` | Database |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | localhost defaults | Used when no full JDBC URL is given |
| `PORT` / `SERVER_PORT` | `8080` | HTTP port |
| `VITE_API_BASE_URL` | `/api` | Frontend API base |

---

## API

Base path `/api`. All amounts are decimal strings. Full request/response payloads for every
endpoint are in the [technical reference](TECH_REFERENCE.md#5-api-reference).

### Expenses

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/expenses` | `from`, `to`, `categoryId`, `vendor`, `anomalyOnly`, `page`, `size`, `sort` |
| `GET` | `/api/expenses/{id}` | |
| `POST` | `/api/expenses` | `201` + `Location` |
| `PUT` | `/api/expenses/{id}` | |
| `DELETE` | `/api/expenses/{id}` | `204` |
| `POST` | `/api/expenses/import` | `multipart/form-data`, field `file` |
| `GET` | `/api/expenses/import/format` | Machine-readable format spec |

`sort` is restricted to a whitelist (`expenseDate`, `amount`, `vendorName`, `createdAt`,
`anomaly`); anything else is a `400`.

```jsonc
// POST /api/expenses
{ "date": "2026-08-01", "amount": "450.00", "vendorName": "Swiggy",
  "description": "Team lunch", "categoryId": null }   // null → let the rules decide

// 201 Created
{ "id": 1, "date": "2026-08-01", "amount": "450.00", "vendorName": "Swiggy",
  "description": "Team lunch",
  "category": { "id": 1, "name": "Food", "colorHex": "#E76F51", "isDefault": false },
  "categorizationSource": "RULE", "isAnomaly": false, "anomalyReason": null,
  "importBatchId": null, "createdAt": "2026-08-01T10:12:03Z" }
```

### Dashboard

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/dashboard/summary` | `month=yyyy-MM`; omit for all time |
| `GET` | `/api/dashboard/monthly-by-category` | `from`, `to` as `yyyy-MM`; zero-filled |
| `GET` | `/api/dashboard/top-vendors` | `month`, `limit` (default 5) |
| `GET` | `/api/dashboard/anomalies` | `page`, `size`; each item carries its baseline |

### Categories and rules

| Method | Path |
|---|---|
| `GET` | `/api/categories` |
| `GET` `POST` | `/api/vendor-rules` |
| `PUT` `DELETE` | `/api/vendor-rules/{id}` (delete deactivates) |

### Errors

Every failure returns the same shape:

```json
{ "timestamp": "2026-08-13T09:22:11Z", "status": 400, "error": "VALIDATION_FAILED",
  "message": "Request validation failed", "path": "/api/expenses",
  "fieldErrors": [{ "field": "amount", "message": "Amount must be greater than 0" }] }
```

| Status | Code | Cause |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Field validation; `fieldErrors` names each one |
| 400 | `BAD_REQUEST` | Bad parameter (unknown sort key, malformed month) |
| 404 | `NOT_FOUND` | No such resource |
| 413 | `FILE_TOO_LARGE` | Upload over 5 MB |
| 422 | `CSV_UNPROCESSABLE` | File unreadable as CSV / missing a required column |
| 500 | `INTERNAL_ERROR` | Logged in full server-side, never leaked to the client |

---

## Project layout

```
iConcile/
├── README.md                  this file
├── DEPLOYMENT.md              Render deployment plan
├── TECH_REFERENCE.md          stack, tables, full API reference
├── TESTING_GUIDE.md           non-technical walkthrough
├── TECHNICAL_PLAN.md          the design
├── render.yaml                Render blueprint
├── sample-data/               a clean CSV and one with deliberate errors
├── backend/
│   ├── Dockerfile             multi-stage build for deployment
│   └── src/main/
│       ├── java/com/iconcile/expense/
│       │   ├── config/        AnomalyProperties, CsvImportProperties, WebConfig
│       │   ├── domain/        Expense, Category, VendorCategoryRule, CsvImportBatch
│       │   ├── repository/    JPA repositories, specifications, native aggregations
│       │   ├── service/       categorization, anomaly, CSV import, dashboard
│       │   ├── util/          VendorNameNormalizer
│       │   └── web/           controllers, DTOs, error handling
│       └── resources/db/migration/   V1 schema · V2 categories · V3 vendor rules
└── frontend/
    └── src/
        ├── api/               typed client + endpoints
        ├── types/             Zod schemas (TS types are inferred from these)
        ├── hooks/             TanStack Query hooks
        ├── lib/               formatting helpers
        ├── components/        UI primitives
        └── features/          expenses · import · dashboard
```

### Notes on the frontend

Every API response is parsed through a Zod schema at the boundary, and the TypeScript types are
inferred from those schemas — a backend contract change surfaces as a named parse error rather
than as `undefined` deep inside a chart. Filters live in the URL, so a filtered view is
linkable and survives a refresh. Any write invalidates both the expense list and the dashboard,
because an import can flip anomaly flags on rows the user never touched.

---

## Known limitations

- **Single user.** No authentication or per-user scoping; every expense is in one shared ledger.
- **Editing a rule does not re-categorize history.** Existing expenses keep the category they
  were filed under. A `reapply` endpoint is the natural next step.
- **Import inserts row by row.** Identity-generated ids prevent JDBC batching. Fine at the
  10,000-row cap; a sequence with a pooled allocator would be the fix if that cap were raised.
- **Anomaly detection is a fixed multiplier**, not a distribution-aware test. A z-score or MAD
  variant would behave better on skewed categories — the multiplier is configuration, so it can
  be tuned without a code change.
- **Single currency (INR)** in formatting and validation.
# Expense-Manager
