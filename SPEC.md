# MyFinance — Specification

> A private, offline-first Android app to manage cash flow, an investment and personal-asset portfolio, and net worth. MyFit's sibling: same design language, same engineering standards.

## Problem and why it matters

Keeping track of personal finances means answering three questions at once: *how much cash do I have and what is still coming in or going out this month*, *what are my investments and assets worth and how did they perform*, and *what is my overall net worth*. Today the answers are spread across a bank app, a broker app, a spreadsheet and memory. Bank apps only see one account; broker apps only see their own products; neither knows about a car, an apartment or startup equity. Spreadsheets work but are slow to update on a phone and easy to break.

Finance apps that try to consolidate everything require an account, ask for bank credentials, push subscriptions and send highly sensitive data to third-party servers.

MyFinance is a single-user app where the user enters everything by hand, sees cash, portfolio and net worth kept clearly apart, and owns the data completely: it lives on the phone and in encrypted backups the user controls.

## Goals

- Plan and confirm income and expenses, including recurring bills, with reliable recurrence and a trustworthy end-of-month projection.
- Track investments and personal assets with full valuation history, keeping contributions and withdrawals apart from performance.
- Show a consolidated net worth that never double counts cash and portfolio, in BRL or USD.
- Support BRL and USD everywhere, converting with the official Banco Central PTAX rate.
- Protect the data against device loss with automatic encrypted backups, with no backend.
- Look and feel like MyFit: the same minimal dark identity, purpose-built for finance.

## Non-goals

- No bank, card or broker integration, no Open Finance, no market price feeds.
- No account, login, server or sync service.
- No budgeting envelopes, goals, gamification or financial advice.
- No ads, analytics or telemetry.
- No liabilities (loans, credit card balances) in v1: net worth is cash + investments + personal assets.

## Success criteria

| Criterion | Measurement | Target |
|---|---|---|
| Recurrence correctness | Generated dates for every preset, month-end and leap-year case match hand-computed tables | 100% in tests |
| No duplicate occurrences | Running generation repeatedly, editing rules and restoring backups never creates two occurrences for the same template and nominal date | 100% in tests |
| Calculation correctness | Balances, projections, gains, returns, allocation and net worth match independent hand-computed fixtures | 100% in tests |
| Quick entry | Taps to log a one-off expense already paid today | ≤ 4 taps + typing |
| App launch | Cold start to interactive Overview on a mid-range phone | < 1.5 s |
| Backup integrity | Restore recreates an identical database and images (counts and hashes) | 100% in round-trip tests |
| Corruption detection | Tampered file or wrong password rejected before local data is touched | 100% in tests |
| APK size | Minified release APK | < 15 MB |
| User acceptance | Manual checklist in `docs/TESTING.md` on the user's phone | All items pass |

## User and use cases

**User:** one person (the app's owner) in Brazil who earns and spends mostly in BRL, has some income, expenses and investments in USD, and wants a private, consolidated view.

**Main use cases:**

1. "On the 1st I see this month's expected salary, rent, utilities and subscriptions, my current cash and how much I'll have left at the end of the month."
2. "I paid the electricity bill: it was R$ 287,40 instead of the expected R$ 250. I confirm the payment with the real amount and date; next month still expects R$ 250."
3. "A freelance client paid US$ 1.000. I confirm the receipt; my USD cash goes up and the Overview converts it to reais at today's PTAX."
4. "Every month I update the value of my CDB, my US ETF and my car. I see each asset's history, how much I put in, the gain and the allocation."
5. "I check my net worth over the last year, in reais or in dollars."
6. "I switched phones: I install the app, point it to the backup folder synced with Drive, type the password and everything is back."

## Functional requirements

### Overview

1. MUST show estimated net worth and how it is composed: available cash, investment portfolio, personal assets.
2. MUST show available cash and the projected end-of-month cash balance, clearly labelled and never mixed with portfolio values.
3. MUST show this month's income and expenses (expected vs. confirmed), upcoming commitments with a confirm action, and recent transactions.
4. MUST let the user switch the display currency (BRL/USD) and show the exchange rate used and its date.

### Cash flow

5. MUST provide predefined categories — income: Salary, Freelance, Side Income, Dividends, Business Income, Other Income; expenses: Housing, Rent, Electricity, Water, Internet, Phone, Groceries, Transportation, Healthcare, Gym, Subscriptions, Entertainment, Education, Shopping, Other Expenses — each with an icon, and allow creating, renaming, changing the icon, archiving and (when unused) deleting categories.
6. MUST allow creating one-time and recurring transactions with: name, category, type (income/expense), currency (BRL/USD), expected amount, recurrence, start date, next due date, optional end date, description and active/inactive status.
7. MUST support recurrence: one-time, daily, weekly, every two weeks, monthly, every two months, quarterly, semiannually, annually and custom (every N days/weeks/months/years).
8. MUST compute recurrence dates from the anchor (never chained), clamp to the last day of shorter months (31 Jan → 28/29 Feb → 31 Mar) and handle 29 Feb in non-leap years (→ 28 Feb).
9. MUST keep recurrence definitions separate from generated occurrences and MUST NOT create two occurrences for the same template and nominal date.
10. Editing a recurrence MUST only replace pending, not individually edited occurrences due from today on; confirmed, cancelled, overdue and individually edited occurrences MUST stay untouched.
11. Each occurrence MUST be confirmable on its own ("Confirm payment" / "Confirm receipt") with actual amount, actual currency, actual date (not in the future) and optional note. The expected amount MUST be kept separately and future occurrences MUST NOT change.
12. MUST derive statuses: Upcoming, Pending (due within 7 days), Overdue, Paid, Received, Cancelled; and allow cancelling, restoring and un-confirming an occurrence (with confirmation).
13. MUST let the user set an opening balance per currency (amount and date) and record balance adjustments that are neither income nor expense.
14. The Cash Flow tab MUST show, per month: expected and received income, expected and paid expenses, current balance, projected end-of-month balance, overdue and pending bills, upcoming income and next month's forecast.
15. MUST show charts: income vs. expenses per month, balance evolution, expenses by category, income by category.

### History

16. MUST list all transactions grouped by month with search (accent-insensitive), filters by date range, category, type, status and currency, and a detail screen to edit, cancel or un-confirm.

### Portfolio

17. MUST provide asset categories — investments: Fixed Income, Stocks, ETFs, REITs, Investment Funds, Cryptocurrencies, Other Investments; personal assets: Business Ownership, Startup Equity, Real Estate, Vehicles, Other Assets — and allow custom categories.
18. Each asset MUST support: name, category, currency, description, institution or company, acquisition date, initial invested amount, current estimated value, optional quantity, notes and an optional image. Quantity and market price MUST NOT be required.
19. MUST record contributions (initial and additional), withdrawals, partial disposals and full disposals as separate cash-flow records, never as performance.
20. MUST record valuations as a history (date, value, note), never overwriting earlier dates (a second valuation on the same date replaces that day's, after a warning), and show for each valuation the invested amount, gain/loss and return at that date.
21. The Portfolio tab MUST show total value, total invested, gain/loss, simple return, last valuation date and a donut chart with the total in the centre, allocation percentages, a category legend and category selection; and allow drilling down into each asset.
22. MUST show charts for net worth evolution, allocation, performance (cumulative gain) and invested vs. value, for 1M, 3M, 6M, 1Y and All, built only from recorded data.
23. Return figures MUST state their method (simple, capital-recovery basis) and MUST NOT be presented as time- or money-weighted returns.

### Currency and exchange rates

24. MUST support BRL and USD for every monetary record (an asset's flows and valuations use the asset's currency) and convert using the USD→BRL PTAX (sell) rate from the Banco Central do Brasil public API, fetched automatically on start and daily (unless turned off) and on demand ("Update"), with manual entry as a fallback; MUST show the date of the rate used and MUST NOT invent a rate. Totals that include an unconvertible amount MUST be marked as incomplete.
25. Current values MUST use the latest rate; historical values MUST use the rate of their own date (last published rate on or before it).

### Settings

26. MUST allow: opening balances, balance adjustment, default currency for new entries, display currency, automatic rate fetching (on/off), manual rate entry, automatic backup (on/off), number format (1.234,56 / 1,234.56), date format (DD/MM/YYYY / MM/DD/YYYY), theme (Dark default, Light, System), language (opens the system per-app language screen on Android 13+), category management, backup, CSV export, privacy information and app information.

### Backup and export

27. MUST create a single compressed and encrypted backup with all data (transactions, categories, portfolio, valuations, exchange rates, images, settings), with format version and metadata.
28. MUST encrypt every backup with a key derived from a user-defined recovery password; the derived key is kept wrapped by the Android Keystore.
29. MUST write backups to a user-chosen folder (Storage Access Framework), keeping the 7 most recent, and verify a new file fully before deleting older ones.
30. MUST back up daily and after changes (debounced), allow manual backups and show the last backup time, status and last error.
31. MUST restore from the folder or a standalone file, showing the backup date and asking for confirmation, validating password, authenticity, hashes, counts and format version before replacing data, atomically.
32. MUST export transactions, assets, flows, valuations and exchange rates to CSV (zip), warning that the export is not encrypted.

## Non-functional requirements

- **Offline-first:** every feature works without internet. The only network use is fetching public exchange rates from `api.bcb.gov.br` over HTTPS; automatic fetching can be turned off ("Update" still fetches on demand) and requests carry only a date range.
- **Privacy:** no analytics, no account, no financial data leaves the device except in the user's own encrypted backups and explicit CSV exports. Amounts, names and notes are never logged.
- **Correctness:** money in integer minor units (`Long`) with an explicit currency; conversions and percentages in `BigDecimal` with `HALF_EVEN`, rounded once per total. All financial calculations live in the pure `domain` layer and are unit tested.
- **History:** historical records are never silently modified; the database has tested migrations from v1 on; restore is all-or-nothing.
- **Security:** Android auto backup disabled; backups AES-256-GCM (streaming) with PBKDF2-SHA256 (600k) password-derived key; imported files validated (zip-slip, limits, hashes). Local database relies on app sandbox + file-based encryption (documented in `docs/SECURITY.md`).
- **Experience:** dark theme first and complete light theme; phones and large screens; 150–250 ms motion; 48 dp minimum targets; large font scales; tabular figures for amounts.
- **Compatibility:** Android 8.0 (API 26)+, target API 37.
- **Language and formats:** English by default with a Brazilian Portuguese translation (device language). Number format default `1.234,56`, date format default `DD/MM/YYYY`, both configurable; currency symbols `R$` and `US$`; dates evaluated in the device time zone (America/Sao_Paulo for the target user).

## Visual identity

- MyFinance brand: a geometric "F" whose top arm rises like a progress line and ends in a green data point; `myfinance` wordmark in lowercase Geist SemiBold. Adaptive and monochrome launcher icons, splash screen and notification icon. Documented in `docs/BRAND.md`.
- Same palette, typography, shapes, spacing and motion as MyFit: graphite and black, white type, green as the only accent; red for overdue, negative and destructive; amber for warnings.

## Architecture overview

Native Android (Kotlin, Jetpack Compose, Material 3), MVVM, single `:app` module:

```
ui/        screens + ViewModels, design system, navigation
domain/    pure Kotlin: recurrence, cash, portfolio, exchange rates, net worth, backup format
data/      Room (source of truth), DataStore, image store, BCB client, backup (SAF)
platform/  WorkManager workers, notifications
```

Details in `docs/ARCHITECTURE.md`.

## Constraints and assumptions

- Distributed as a signed APK installed manually (no Play Store). Assumption: the user can install apps outside the store (ADB or the unverified-developer flow).
- No backend, authentication or paid services. The BCB API is free and keyless; if it changes or is unavailable, manual rates keep the app working.
- Cloud sync of the backup folder is the user's responsibility (folder-sync app).
- Developed without a device: JVM tests (unit, Robolectric, screenshots) plus a manual on-device checklist.
- Losing both the device and the recovery password makes backups unrecoverable, by design.
- Portfolio contributions never move cash: when money moves from the bank to an investment, the user records it in the portfolio and adjusts cash (or records it as they see fit). The Overview shows the composition so double counting is visible.

## Out of scope (v1)

- Liabilities, loans and credit card statements.
- Multiple cash accounts (one balance per currency only).
- Currencies other than BRL and USD (the model supports adding them).
- Bill reminders and notifications other than backup failure.
- Time-weighted / money-weighted returns.
- App lock, encrypted local database.
- Widgets, Wear OS, sync between devices.

## Open questions

1. **PTAX timing:** the day's PTAX is published around 13:00 BRT on business days; before that the previous business day's rate is used and shown with its date. Revisit if an intraday rate is needed.
2. **Pending window:** "Pending" means due within 7 days. Revisit after a few weeks of use.
3. **Carry-forward after contributions:** an asset's value after a contribution without a new valuation is the last valuation plus net flows. Revisit if it confuses more than it helps.
