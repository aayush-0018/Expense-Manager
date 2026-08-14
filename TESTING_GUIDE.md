# Testing Guide — Walking Through the Product

A click-by-click walkthrough of everything the app does and how to check that each part
actually works. No code, no terminal — just the screen in front of you.

Every number in this guide was confirmed against a real run, so if you follow the steps in
order you should see exactly the values shown.

---

## What the product is

An expense tracker with four jobs:

| # | What it does | Where you see it |
|---|---|---|
| 1 | **Record expenses** — one at a time, by hand | Expenses tab |
| 2 | **Import expenses in bulk** — from a spreadsheet export | Import CSV tab |
| 3 | **Sort them into categories automatically** — from the shop's name | happens invisibly on both |
| 4 | **Point out unusual spending** — anything far above your normal | Expenses tab + Dashboard |
| 5 | **Summarize where the money went** | Dashboard tab |

Three tabs across the top: **Expenses**, **Import CSV**, **Dashboard**.

---

## Before you start

1. Both parts of the app need to be running (see the [README](README.md#commands)).
2. Open **http://localhost:5173**.
3. You should land on the **Expenses** tab.

**Starting fresh?** If the list is empty, that is fine — Test 2 loads the sample data. If you
want to start over at any point, the README has a [one-line reset](README.md#set-up-postgresql).

> Throughout this guide, "the sample file" means `sample-data/expenses.csv` in the project
> folder — 56 real-looking expenses across four months (May–August 2026).

---

## Test 1 — Add an expense by hand

**What we are checking:** you can record an expense, and the app files it into the right
category on its own without you choosing one.

### Steps

1. Go to the **Expenses** tab.
2. In the form at the top, fill in:
   - **Date** — pick any past date
   - **Amount** — `450`
   - **Vendor** — `Swiggy`
   - **Description** — `Team lunch`
   - **Category** — leave it on **Automatic**
3. Press **Add expense**.

### What you should see

- The expense appears at the top of the table below.
- The **Category** column shows **Food** — even though you never picked it. This is the whole
  point: it recognized "Swiggy" as a food delivery service.
- The form clears itself, ready for the next entry.

### Also worth trying

| Type this as the Vendor | Category you should get | Why it is interesting |
|---|---|---|
| `Uber` | Travel | the obvious one |
| `Uber Eats` | **Food** | it is *not* fooled into Travel by the word "Uber" |
| `Swiggy Instamart` | **Groceries** | not Food — the more specific name wins |
| `Amazon Prime Video` | **Entertainment** | not Shopping, for the same reason |
| `Coca Cola` | Uncategorized | it does **not** guess Travel from "Ola" hiding inside "Cola" |
| `Random Corner Shop` | Uncategorized | it admits when it does not know, rather than guessing |

Under any vendor that ended up uncategorized, the table shows a small note: *"no vendor rule
matched"*. That is the app being honest about it, not a bug.

---

## Test 2 — Import a spreadsheet

**What we are checking:** you can load a whole month of expenses at once instead of typing them.

### Steps

1. Go to the **Import CSV** tab.
2. Press **Choose file** (or drag the file onto the dashed box).
3. Pick `sample-data/expenses.csv` from the project folder.

### What you should see

- A result panel: **56 rows read · 56 imported · 0 failed**, badged **Completed** in green.
- Buttons to jump to the expenses list or the dashboard.
- Going back to **Expenses**, the table is now full, and the counter reads **56 total**.

Every one of those 56 rows got a category automatically, the same way Test 1 did.

### The panel on the right

It tells you what the file needs to look like — required columns, accepted date formats. Press
**Download template** to get a correctly formatted starter file. This is not a static picture:
it is read live from the app, so it cannot fall out of date with what the importer actually
accepts.

---

## Test 3 — Import a *messy* spreadsheet

**What we are checking:** one bad row does not throw away the whole file, and you are told
exactly what to fix.

This is the test that matters most for real-world use — exports from banks are rarely clean.

### Steps

1. Stay on **Import CSV**.
2. Upload `sample-data/expenses-with-errors.csv`. This file is broken on purpose.

### What you should see

- The badge reads **Completed with errors** in amber.
- **10 rows read · 4 imported · 6 failed.**
- An expandable list naming every failure, with the **line number in your file**:

| Line | Field | Problem |
|---|---|---|
| 5 | date | mixes a second date format into the file |
| 6 | amount | `abc` is not a number |
| 7 | amount | `-99.00` is not positive |
| 8 | date | 31 February does not exist |
| 9 | vendor | the vendor name is blank |
| 10 | amount | `99.999` has too many decimal places |

### Why this is the right behaviour

- **The 4 good rows were saved.** A single typo on line 6 did not cost you the other nine rows.
- **Line numbers match your file exactly** — open the CSV, jump to line 6, fix it. There is no
  mental arithmetic about header rows.
- **Press "Copy errors"** to take the whole list into your spreadsheet.
- **Nothing was silently corrected.** `99.999` was refused rather than quietly rounded to
  `100.00` — money should never be changed behind your back.

### One more thing to notice

If you upload the same file **twice**, the second time shows an amber **possible duplicates**
note listing the repeated rows — *but it still imports them*. Two identical coffees on the same
day are a normal thing to record, so the app tells you and lets you decide.

---

## Test 4 — Spotting unusual spending

**What we are checking:** the app flags expenses that are far above normal for their category,
and shows them clearly.

An expense is flagged when it is **more than 3× the average for its category**.

### Steps

1. Go to the **Expenses** tab (with the sample data loaded from Test 2).
2. Press the **⚠ Anomalies only** button above the table.

### What you should see

Exactly **3** expenses:

| Date | Vendor | Amount | Category |
|---|---|---|---|
| 26 Jul 2026 | Apollo Hospital | ₹15,800.00 | Health |
| 29 Jun 2026 | MakeMyTrip | ₹12,500.00 | Travel |
| 03 Aug 2026 | Zomato | ₹9,800.00 | Food |

Each one is a genuinely unusual purchase — a hospital bill among small pharmacy runs, a flight
among daily cab rides, a party catering order among single lunches.

### How they are marked

Look at how a flagged row differs from a normal one. There are **three** separate signals:

1. the row has an amber tint,
2. a thick amber bar runs down its left edge,
3. an **⚠ anomaly** badge sits next to the amount.

That is deliberate. If you printed the page in black and white, or if you cannot easily
distinguish amber, the bar and the word "anomaly" still tell you. Colour is never the only clue.

Press the button again to turn the filter off.

---

## Test 5 — Watching the flags update themselves

**What we are checking:** flags are not stamped on once and forgotten — they track your data as
it changes. This is the most interesting behaviour in the app.

The logic: an expense is unusual *compared to your other spending in that category*. Add more
expenses and "normal" moves, so what counts as unusual has to move too.

### Steps

1. Go to the **Dashboard** tab and note the **Anomalies** tile: it reads **3**.
2. Go back to **Expenses**. Add three expenses, one at a time:
   - Vendor `Apollo Clinic`, amount `12000`, any past date — repeat **three times**.
   - (Apollo Clinic files itself under Health, same as Apollo Hospital.)
3. Return to the **Dashboard**.

### What you should see

- The **Anomalies** tile now reads **2**.
- The ₹15,800 hospital bill is **no longer flagged**.

**Why that is correct:** ₹15,800 stood out when the only other Health expenses were around
₹750. Now that ₹12,000 medical bills are routine for you, it is no longer remarkable — so the
app stops calling it out. Nothing about that expense changed; what changed is the context
around it.

### Now undo it

4. Delete the three Apollo Clinic expenses (**Delete** button on each row, confirm).
5. Go back to the **Dashboard**.

The **Anomalies** tile reads **3** again, and Apollo Hospital is flagged once more.

This is worth doing because a lesser implementation would leave the count stuck at 2 forever —
the dashboard would slowly stop matching the data it claims to describe.

---

## Test 6 — The dashboard

**What we are checking:** the summary screens answer "where did my money go?"

Go to the **Dashboard** tab. Make sure the month picker at the top right reads **August 2026**.

### The four tiles

| Tile | Shows | With the sample data (Aug 2026) |
|---|---|---|
| Total spend | everything that month | ₹19,028.00 |
| Expenses | how many entries | 12 |
| Anomalies | unusual items, all time | 3 |
| Top category | biggest spending area | Food · ₹10,725.00 |

**Click the Anomalies tile.** It should jump you to the Expenses tab with the anomaly filter
already switched on — the number is a way in, not just a statistic.

### Monthly totals per category

A stacked bar chart, one bar per month for the last six months. Each coloured block is a
category.

- Hover any block for the exact figure.
- Note that **months with no spending still appear** on the axis rather than being skipped —
  the months stay evenly spaced, so a quiet month reads as a real gap instead of vanishing.
- June and July are the tall bars (the big travel and hospital expenses).

### Top 5 vendors by spend

Horizontal bars, biggest first. With the sample data:

| Vendor | Total | Expenses |
|---|---|---|
| Apollo Hospital | ₹15,800.00 | 1 |
| MakeMyTrip | ₹14,600.00 | 2 |
| Zomato | ₹11,045.50 | 4 |
| DMart | ₹5,430.00 | 2 |
| BigBasket | ₹5,340.00 | 2 |

Hover a bar to see the number of transactions and the category.

**Worth testing:** add two expenses spelled differently — `SWIGGY` and `swiggy` — and check the
chart. They combine into **one** Swiggy bar rather than splitting into two. Different
capitalizations of the same shop are the same shop.

### Anomalies panel

Each flagged expense is listed with an explanation in plain language, for example:

> **Apollo Hospital** — Health average is ₹750.00 — flagged above ₹2,250.00 · **21.1×**

So you can see *why* it was flagged, not just that it was.

### Changing the month

Use **← Previous** / **Next →** or the month box. Every tile and chart follows. Step back to
June 2026 and the Top Vendors chart reshuffles — MakeMyTrip leads that month.

The **Next** button is disabled once you reach the current month, since there is no future
spending to show.

---

## Test 7 — Finding things in a long list

**What we are checking:** the expenses list stays usable at 56+ rows.

Go to the **Expenses** tab and try each filter:

| Filter | Try | Expected |
|---|---|---|
| **From / To** | From `2026-07-01`, To `2026-07-31` | only July expenses, counter drops to 15 |
| **Category** | Pick `Food` | only food, every chip reads Food |
| **Vendor** | Type `swiggy`, press Enter | only Swiggy rows — capitalization does not matter |
| **⚠ Anomalies only** | Toggle on | the 3 flagged expenses |
| **Clear** | Press it | everything comes back, 56 total |

Filters **combine** — category `Food` *and* July gives you July's food spending only.

### Two things to check

1. **Look at the address bar** while filtering. It updates as you go. Copy that link, open it in
   a new tab, and the same filtered view loads. Refreshing the page keeps your filters rather
   than dumping you back to the unfiltered list.
2. **Set a filter that matches nothing** (e.g. category `Utilities` in a month with none). You
   get a short message and a **Clear filters** button — not a blank white area leaving you
   wondering whether the app broke.

### Paging

The list shows 25 at a time. Use **Previous** / **Next** at the bottom; the label reads
"Page 1 of 3". **Previous** is disabled on the first page, **Next** on the last.

---

## Test 8 — Editing and deleting

**What we are checking:** you can correct a mistake.

### Editing

1. Press **Edit** on any row.
2. The form at the top switches to edit mode, filled in, headed *"Edit expense · <vendor>"*.
3. Change the amount, press **Save changes**.
4. The row updates in place. Press **Cancel** to back out instead.

### Overriding a category

The app's guess is a starting point, not a verdict.

1. Add an expense with vendor `Starbucks` — it files as **Food**.
2. Press **Edit** on it, change **Category** from *Automatic* to **Entertainment**, save.
3. The row now reads Entertainment, with a small note underneath: *"category set manually"*.

That note matters — it marks the expense as yours to control. Your choice will not be quietly
overwritten later by the automatic rules.

### Deleting

Press **Delete**. A confirmation appears naming the vendor and amount, so you cannot lose a row
with a stray click. Confirm, and it disappears.

---

## Test 9 — When you make a mistake

**What we are checking:** the app tells you what is wrong, next to the thing that is wrong.

On the **Expenses** tab, try to submit each of these and watch where the message appears:

| What you do | What you should see |
|---|---|
| Leave everything blank, press Add | red messages under **Amount** and **Vendor** |
| Amount `-50` | *"Enter a positive amount…"* under the Amount box |
| Amount `abc` | same |
| Amount `10.005` | *"…at most 2 decimal places"* |
| Date in the future | *"Date cannot be in the future"* under the Date box |
| Vendor of only spaces | *"Vendor name is required"* |

In every case the message sits **under the specific field**, not in a general banner at the top
— you never have to work out which box it means. Nothing is saved until it is valid.

---

## Test 10 — When something is broken

**What we are checking:** the app fails clearly instead of showing a blank screen.

### Steps

1. Stop the backend (close its terminal, or press `Ctrl-C` there).
2. Back in the browser, reload the page.

### What you should see

A red panel reading **"Could not load this"** with *"Could not reach the server. Is the backend
running?"* and a **Try again** button.

Start the backend again, press **Try again**, and the data comes back without needing a reload.

That message is the useful part: it names the actual problem instead of showing a spinner
forever or an empty page.

---

## Quick checklist

Ten minutes, in order:

- [ ] **1.** Add a `Swiggy` expense → lands in **Food** on its own
- [ ] **2.** `Uber Eats` → **Food**, not Travel
- [ ] **3.** Import `expenses.csv` → **56 of 56** imported
- [ ] **4.** Import `expenses-with-errors.csv` → **4 in, 6 reported** with line numbers
- [ ] **5.** Anomalies filter → exactly **3**, each amber with a ⚠ badge
- [ ] **6.** Add three ₹12,000 `Apollo Clinic` expenses → anomalies drop to **2**
- [ ] **7.** Delete those three → anomalies back to **3**
- [ ] **8.** Dashboard, Aug 2026 → **₹19,028.00**, 12 expenses, top category **Food**
- [ ] **9.** Click the Anomalies tile → jumps to the filtered list
- [ ] **10.** Filter by vendor `swiggy` → matches regardless of capitalization
- [ ] **11.** Copy the filtered URL into a new tab → same view loads
- [ ] **12.** Edit a row, override its category → *"category set manually"* appears
- [ ] **13.** Submit an empty form → errors under each field
- [ ] **14.** Stop the backend → clear red message with a working **Try again**

---

## Things that are deliberately *not* there

So they do not get reported as faults:

| Not present | Why |
|---|---|
| Login / user accounts | not part of the brief — one shared ledger |
| Changing a categorization rule from the UI | rules are editable through the API; no screen was built for them |
| Editing a rule updating past expenses | past expenses keep the category they were filed under, on purpose |
| Currencies other than rupees | single-currency by design |
| Undo after deleting | deletion asks for confirmation instead |

---

## If something looks wrong

| What you see | Most likely cause |
|---|---|
| Red *"Could not reach the server"* | the backend is not running |
| Backend will not start | PostgreSQL is not running, or (Postgres.app) an approval dialog is waiting behind a window |
| Import says *"Only .csv files are accepted"* | the file is `.xlsx` — export as CSV first |
| Import rejects every date after the first row | the file mixes date formats; the format is locked to whatever the first row uses |
| Anomaly count is not what you expect | it is all-time, not per-month, unlike the other tiles |
| A category shows nothing this month | that is real — there was no spending; the chart keeps the month visible rather than hiding it |
