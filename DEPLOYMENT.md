# Deploying to Render

Plan for putting all three pieces on Render: **PostgreSQL** (managed database), **backend**
(Docker web service), **frontend** (static site).

Everything the deployment needs is already in the repo:

| File | Purpose |
|---|---|
| [`render.yaml`](render.yaml) | Blueprint — provisions all three services in one go |
| [`backend/Dockerfile`](backend/Dockerfile) | Multi-stage build; Java is not a native Render runtime |
| [`backend/.dockerignore`](backend/.dockerignore) | Keeps `target/` out of the build context |
| `application.yml` | Already reads Render's `PORT` and split `DB_*` credentials |

---

## What gets created

```
┌──────────────────────────────────────────────────────────────┐
│  expense-manager-web        (Static Site · free · no sleep)  │
│  React build served from CDN                                 │
│                                                              │
│   /api/*  ──rewrite──►  expense-manager-api                   │
│   /*      ──rewrite──►  /index.html   (React Router)         │
└────────────────────────────┬─────────────────────────────────┘
                             │  same-origin from the browser,
                             │  so no CORS is involved
┌────────────────────────────▼─────────────────────────────────┐
│  expense-manager-api        (Web Service · Docker · free)    │
│  Spring Boot; Flyway migrates on boot                        │
└────────────────────────────┬─────────────────────────────────┘
                             │  private network, same region
┌────────────────────────────▼─────────────────────────────────┐
│  expense-manager-db         (PostgreSQL 16 · free)           │
│  ipAllowList: [] — not reachable from the public internet    │
└──────────────────────────────────────────────────────────────┘
```

---

## Read this before you start

Four things about Render's free tier that will affect a reviewer's experience.

| Constraint | Effect | What to do |
|---|---|---|
| **Free PostgreSQL is deleted after 30 days** | the whole database disappears, not just suspended | fine for an assignment; upgrade ($7/mo) for anything lasting |
| **Free web services sleep after 15 min idle** | first request after idling takes **50–90 s** while the JVM cold-starts | warm it before sharing the link, or upgrade to Starter |
| **512 MB RAM** | Spring Boot + Hibernate fits, but not with default JVM settings | already handled — `MaxRAMPercentage=70` + SerialGC in `render.yaml` |
| **Free Postgres allows few connections** | default pool of 10 can exhaust them | already handled — `DB_POOL_SIZE=5` |

The static site does **not** sleep, so the page always loads instantly — but it will sit on a
loading state while the API wakes. Worth telling anyone you send the link to.

---

## Step 1 — Put the code on GitHub

Render deploys from a Git repository. **This project is not a Git repo yet**, so this is the
real first step.

```bash
cd /Users/aayushgoswami/Documents/iConcile
git init
git add .
git commit -m "Mini Expense Manager"
git branch -M main
git remote add origin https://github.com/<you>/iconcile.git
git push -u origin main
```

`.gitignore` already excludes `backend/target/`, `frontend/node_modules/` and `frontend/dist/`.

> **Check before pushing:** the repo contains no secrets — the only credentials are the local
> `postgres/postgres` defaults in `application.yml`, which are overridden by environment
> variables in production. Nothing else needs scrubbing.

---

## Step 2 — Create the Blueprint

1. [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint**
2. Connect your GitHub account and pick the repository
3. Render reads `render.yaml` and shows three resources to create
4. Give the blueprint a name → **Apply**

Render now provisions the database, then builds and deploys both services.

**Pick your region before applying** if Singapore is not closest to you. Change `region:` in
`render.yaml` — the database and API **must be in the same region**, or they cannot use the
private network.

---

## Step 3 — Wait for the first build

| Service | Expected | What is happening |
|---|---|---|
| Database | ~1 min | provisioning |
| API | **5–8 min** | Docker build: Maven downloads every dependency on the first run |
| Frontend | ~2 min | `npm ci && npm run build` |

The API's first build is the slow one. Later deploys are much faster — the Dockerfile copies
`pom.xml` and resolves dependencies in a separate layer, so unchanged dependencies are cached.

**Watch the API logs** for the boot sequence that confirms the database wiring worked:

```
HikariPool-1 - Added connection ...
Flyway ... Migrating schema "public" to version "1 - schema"
Flyway ... Migrating schema "public" to version "2 - seed categories"
Flyway ... Migrating schema "public" to version "3 - seed vendor rules"
Tomcat started on port 10000
Started ExpenseManagerApplication
```

Flyway creates every table and seeds the 8 categories and ~68 vendor rules automatically. **You
never run any SQL by hand.**

---

## Step 4 — Verify the API on its own

Before wiring the frontend, confirm the backend works standalone. Its URL is on its dashboard
page, of the form `https://expense-manager-api.onrender.com`.

```bash
curl https://expense-manager-api.onrender.com/actuator/health
# {"status":"UP"}

curl https://expense-manager-api.onrender.com/api/categories
# the 8 seeded categories

curl https://expense-manager-api.onrender.com/api/dashboard/summary
# {"month":null,"totalAmount":"0.00","expenseCount":0,...}   ← empty is correct
```

If health is `UP` and categories come back, the database, migrations and seed data are all
working.

---

## Step 5 — Point the frontend at the API ⚠️ the one manual step

`render.yaml` rewrites `/api/*` to the API, but the destination has a **placeholder hostname**.
Render assigns the real URL only after the service exists, and it cannot interpolate a service
URL into a route destination — so this one line has to be filled in by hand.

1. Copy the API's URL from Step 4.
2. Edit `render.yaml`:

```yaml
      - type: rewrite
        source: /api/*
        destination: https://YOUR-ACTUAL-API-URL.onrender.com/api/*
```

3. Commit and push. The static site redeploys automatically.

```bash
git add render.yaml && git commit -m "Point web rewrite at deployed API" && git push
```

**Why a rewrite rather than calling the API host directly:** the browser only ever talks to the
frontend's own origin, so CORS never enters the picture, cookies and headers behave normally,
and the frontend's existing `VITE_API_BASE_URL=/api` default works unchanged in production
exactly as it does behind the Vite dev proxy.

<details>
<summary>Option B — call the API host directly instead (needs CORS)</summary>

If you would rather not use a rewrite:

1. On **expense-manager-web** → Environment, add
   `VITE_API_BASE_URL = https://expense-manager-api.onrender.com/api`
   (this is baked in at **build** time — you must redeploy for it to take effect)
2. On **expense-manager-api** → Environment, set
   `APP_CORS_ALLOWED_ORIGINS = https://expense-manager-web.onrender.com`
3. Remove the `/api/*` rewrite from `render.yaml`

The backend already reads that variable, so no code change is needed. This is strictly more
moving parts, which is why the rewrite is the default.
</details>

---

## Step 6 — Load the sample data

The deployed database starts empty — categories and rules are seeded, expenses are not.

Open the site, go to **Import CSV**, and upload `sample-data/expenses.csv`. You should get
**56 of 56 imported** and 3 anomalies on the dashboard.

Or from the terminal:

```bash
curl -X POST https://YOUR-WEB-URL.onrender.com/api/expenses/import \
     -F "file=@sample-data/expenses.csv"
```

---

## Step 7 — Smoke test the deployment

Run the short version of the [testing guide](TESTING_GUIDE.md) against the live site:

- [ ] Page loads, three tabs render
- [ ] Add an expense with vendor `Swiggy` → categorized as **Food** automatically
- [ ] Import `sample-data/expenses.csv` → 56 of 56
- [ ] Import `sample-data/expenses-with-errors.csv` → 4 in, 6 reported with line numbers
- [ ] **Anomalies only** filter → 3 flagged rows, amber with ⚠ badges
- [ ] Dashboard → tiles, both charts, anomaly panel populated
- [ ] Filter by vendor, copy the URL, open in a new tab → same view (proves routing rewrite works)
- [ ] **Reload on `/dashboard` directly** → loads rather than 404s (proves the SPA rewrite works)

That second-to-last item is the one most likely to fail on a misconfigured static host, so do
not skip it.

---

## Configuration reference

Set automatically by `render.yaml` — nothing to type unless you want to change behaviour.

| Variable | Source | Value |
|---|---|---|
| `PORT` | Render | injected; the app binds it |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | `fromDatabase` | from the managed database |
| `DB_POOL_SIZE` | `render.yaml` | `5` |
| `JAVA_OPTS` | `render.yaml` | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` |
| `APP_CORS_ALLOWED_ORIGINS` | `sync: false` | unset — only needed for Option B |

Tunable without a code change, via the API service's **Environment** tab:

| Variable | Default | Effect |
|---|---|---|
| `ANOMALY_MULTIPLIER` | `3.0` | flagging threshold |
| `ANOMALY_MIN_SAMPLE_SIZE` | `3` | peers required before flagging |
| `ANOMALY_LOOKBACK_DAYS` | unset | averaging window; unset = all time |
| `CSV_MAX_ROWS` | `10000` | import row cap |

Changing one restarts the service; the flags are recalculated on the next write.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| API build fails on `mvn dependency:go-offline` | transient registry failure | redeploy — it is not deterministic |
| API deploys, health check never passes | app not on Render's `PORT` | already handled; confirm the log says `Tomcat started on port 10000` |
| `Connection refused` to the database in API logs | DB and API in different regions | make `region:` match for both, redeploy |
| `FATAL: too many connections` | pool too large for free tier | lower `DB_POOL_SIZE` |
| Frontend loads, every API call 404s | Step 5 not done — rewrite still points at the placeholder | fix the destination and push |
| Frontend loads, API calls fail with a CORS error | using Option B without `APP_CORS_ALLOWED_ORIGINS` | set it to the exact web URL, no trailing slash |
| Reloading `/dashboard` 404s | SPA catch-all rewrite missing or ordered before `/api/*` | keep `/api/*` **first** in `routes` |
| First request takes ~60 s | free service cold start | expected; upgrade to Starter to remove |
| `Flyway ... validate failed` | database has an older schema | free DB is disposable — delete and recreate it |
| Everything worked, now the DB is gone | free database expired at 30 days | recreate, redeploy, re-import the sample CSV |

**Where to look:** each service's **Logs** tab shows live output. The API prints the full Spring
Boot startup, so any database or migration failure appears there with a stack trace.

---

## What this plan does not do

Deliberately out of scope, so nothing here looks like an oversight:

| Not included | Why |
|---|---|
| Custom domain + TLS | Render gives `*.onrender.com` with HTTPS already; a domain is a dashboard setting |
| CI running tests before deploy | integration tests need PostgreSQL; a GitHub Actions workflow with a `postgres` service container would be the next step |
| Database backups | free tier has none — an assignment deployment holds no data worth keeping |
| Staging environment | one environment is enough at this size |
| Zero-downtime deploys | free tier restarts in place; Starter and above do rolling deploys |
| Authentication | the app is single-user by design; **the deployed instance is public** — anyone with the URL can read and write expenses |

That last row is the one to be deliberate about: do not put real financial data in a free-tier
deployment of this app.
