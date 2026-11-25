# MyFinance — Architecture

## Overview

Native Android app, single module (`:app`), MVVM, offline-first. Room is the source of truth. The only network traffic is the public exchange-rate request to the Banco Central do Brasil and, for assets that follow a quote, the price requests to Yahoo Finance; nothing else leaves the device except the encrypted backups and CSV exports the user writes to a folder.

```
ui/        Compose: screens + ViewModels (Hilt), design system, navigation
  │  observes StateFlow, calls actions
domain/    pure Kotlin: money, recurrence, cash, portfolio, exchange rates, market quotes, net worth, export, backup format
  ▲  used by
data/      Room, DataStore, ImageStore, repositories, BCB and Yahoo clients, BackupManager, SAF destination
platform/  WorkManager workers (backup, rates), notifications
```

Dependency rule: `domain` knows nothing about Android (it uses only the JDK, kotlinx.serialization and Tink). `ui` never talks to Room directly. Financial calculations live only in `domain`; ViewModels assemble inputs and format outputs, composables only render.

| Package | Contents |
|---|---|
| `core/format` | `MoneyFormatter`, `AmountInput`, `DateFormats`, `Formats`, `SearchText` |
| `core/time` | `TimeSource` (`nowMs()`, `zone()`), `today()` |
| `domain/model` | `Currency`, `Money`, enums, settings, pure models |
| `domain/cash` | `RecurrenceRule`, `Recurrence`, `CashLedger`, `OccurrenceStatus`, `CashCalculator`, `MonthSummary`, history filter |
| `domain/fx` | `ExchangeRates`, `Converter`, `Total`, `PtaxParser` |
| `domain/portfolio` | `PortfolioData`, `PortfolioCalculator`, `AssetSnapshot`, `Allocation`, `Period`, `Series` |
| `domain/market` | `MarketData` (ticker rules), `YahooChartParser`, `YahooSearchParser`, `Split`, `PricePoint` |
| `domain/networth` | `NetWorthCalculator` |
| `domain/export` | `Csv` |
| `domain/backup` | v1 DTOs, header and encryption, zip + manifest, retention |
| `data/db` | entities, DAOs, `MyFinanceDatabase`, `Migrations`, seeds |
| `data/repository` | categories, cash (templates, occurrences, adjustments), portfolio, exchange rates, market prices (`PriceRepository`), export |
| `data/rates` | `BcbRateClient` (HTTPS GET) |
| `data/market` | `YahooQuoteClient` (HTTPS GET) |
| `data/prefs` | `SettingsRepository`, `RateStateStore`, `PriceStateStore`, `BackupStateStore` |
| `data/backup` | `BackupManager`, `BackupKeyStore`, destinations (SAF/file), `FolderBackupWriter` |
| `platform` | `BackupScheduler` + workers (`BackupWork.kt`), `RateRefreshWorker`, backup failure notification |

## Money

- `Money(minor: Long, currency: Currency)`; `Currency` is an enum (`BRL`, `USD`) carrying ISO code, symbol (`R$`, `US$`) and fraction digits (2). Adding a currency means adding an enum entry, rates for it and strings; no schema change.
- Entities store `amount` as `Long` minor units. Every table with money has a `currency` column, except asset flows and valuations, which are always in their asset's currency (the asset currency cannot change after creation). `Double` is never used for money.
- Conversions and ratios use `BigDecimal` with **scale 10** and `RoundingMode.HALF_EVEN` for intermediate values. Sums are accumulated in `BigDecimal` and rounded to minor units (HALF_EVEN) once, at the end of each total. Percentages are rounded to two decimals only when displayed.
- Asset quantity is an optional `BigDecimal` stored as TEXT (`toPlainString()`), informational only; for a tracked asset (with a ticker) it is NULL and the share count comes from the flows (see "Market prices"). Raw quote prices are also `BigDecimal` TEXT, never `Double`.
- Formatting (`MoneyFormatter`) is the only place that turns money into text: symbol + no-break space + number in the chosen format (`R$ 1.234,56` or `R$ 1,234.56`), a true minus sign (`−`) for negatives, and an explicit `+` only where a signed delta is shown.

## Data

```
category ─┬─ transaction_template ──< transaction_occurrence
          └───────────────────────────< (standalone occurrences have no template)
cash_adjustment            (OPENING per currency, ADJUSTMENT)
asset_category ── asset ─┬─< asset_flow ── (occurrenceId) ── transaction_occurrence   (transfers)
                         ├─< asset_valuation
                         └─< transaction_template / transaction_occurrence (assetId, transfers)
exchange_rate              (USD→BRL per date and source)
asset_price, asset_split   (daily closes and splits per Yahoo symbol; joined to asset.ticker by value, no foreign key)
```

Conventions (same as MyFit): `id Long` auto-generated; enums stored as TEXT names; calendar dates as `epochDay: Long`; instants as `*Ms: Long`; no TypeConverters.

| Table | Columns | Keys and rules |
|---|---|---|
| `category` | `name?`, `kind` (INCOME/EXPENSE only), `icon`, `seedKey?`, `archived`, `position`, `createdAtMs` | unique `seedKey`. Kind fixed after creation. Delete only if unused, otherwise archive (hidden from pickers, kept in history). |
| `transaction_template` | `name`, `categoryId?`, `kind` (INCOME/EXPENSE/TRANSFER_OUT/TRANSFER_IN), `currency`, `expectedAmount`, `recurrenceUnit`, `recurrenceInterval`, `startDate`, `anchorDate`, `endDate?`, `description?`, `active`, `generatedThrough?`, `createdAtMs`, `updatedAtMs`, `assetId?` | `categoryId` RESTRICT; `assetId` SET NULL. Recurring only. Income/expenses have a category, transfers an asset. |
| `transaction_occurrence` | `templateId?`, `scheduledDate?`, `dueDate`, `name`, `categoryId?`, `kind`, `currency`, `expectedAmount`, `description?`, `state` (PENDING/CONFIRMED/CANCELLED), `actualAmount?`, `actualCurrency?`, `actualDate?`, `note?`, `confirmedAtMs?`, `customized`, `createdAtMs`, `updatedAtMs`, `assetId?` | `templateId` SET NULL; `categoryId` RESTRICT; `assetId` SET NULL; unique (`templateId`, `scheduledDate`); indices on `dueDate`, `actualDate`, `state`, `categoryId`, `assetId`. |
| `cash_adjustment` | `kind` (OPENING/ADJUSTMENT), `currency`, `date`, `amount` (signed), `note?`, `createdAtMs` | At most one OPENING per currency (enforced by the repository, tested). |
| `asset_category` | `name?`, `assetGroup` (INVESTMENT/PERSONAL), `icon`, `seedKey?`, `archived`, `position` | unique `seedKey`. Same delete/archive rule as `category`. |
| `asset` | `name`, `categoryId`, `currency`, `description?`, `institution?`, `acquisitionDate`, `quantity?` (TEXT), `notes?`, `imagePath?`, `ticker?`, `createdAtMs`, `updatedAtMs` | `categoryId` RESTRICT. Currency locked after creation. `ticker` (NULL for ordinary assets) is also fixed after creation; see "Market prices". Deleting requires confirmation; closing the position is the normal path. |
| `asset_flow` | `assetId`, `kind` (INITIAL/CONTRIBUTION/WITHDRAWAL/PARTIAL_DISPOSAL/FULL_DISPOSAL), `date`, `amount`, `quantityDelta?` (TEXT), `note?`, `createdAtMs`, `occurrenceId?` | `assetId` CASCADE; `occurrenceId` SET NULL, unique (the transfer it belongs to); index (`assetId`, `date`). `amount > 0`, except INITIAL which may be 0 (inherited or granted assets). At most one INITIAL and one FULL_DISPOSAL per asset. |
| `asset_valuation` | `assetId`, `date`, `value` (≥ 0), `note?`, `createdAtMs` | `assetId` CASCADE; unique (`assetId`, `date`): a second valuation on the same day replaces it, after a warning. |
| `exchange_rate` | `base` (USD), `quote` (BRL), `date`, `rateMicros` (rate × 10⁶), `source` (BCB/MANUAL), `fetchedAtMs` | unique (`base`, `quote`, `date`, `source`): a fetched and a manual rate can coexist on the same date; the manual one wins. |
| `asset_price` | `symbol`, `date`, `price` (TEXT, plain decimal, raw), `source` (YAHOO), `fetchedAtMs` | unique (`symbol`, `date`). Shared by every asset with that ticker; no foreign key, so it survives deleting the asset. |
| `asset_split` | `symbol`, `date`, `numerator` (TEXT), `denominator` (TEXT) | unique (`symbol`, `date`); each share becomes numerator ÷ denominator shares. |

- **Standalone occurrence** = `templateId IS NULL`: one-time transactions, and rows kept after their template was deleted. Only standalone occurrences can be deleted; generated ones are cancelled instead.
- **Closed asset** = an asset with a FULL_DISPOSAL flow (derived, not stored). New flows and valuations after the disposal date are blocked until the disposal is deleted.
- Occurrences keep a snapshot of `name`, `kind`, `currency` and `description`, so editing or deleting a template never rewrites history. Category names are referenced, not copied: renaming a category is meant to apply to history.
- Settings (theme, number and date formats, default and display currency, currencies in use, automatic rate fetching, automatic backup) live in DataStore and travel in the backup's `settings.json`. Rate fetch state (`lastFetchMs`, last error), price fetch state (`PriceStateStore`: last attempt, last success, last error) and backup state live in their own DataStores. The "Fetch stock and crypto quotes" switch is a setting (`autoFetchPrices`).
- Images (optional asset logo, shown in place of the category icon on a white circle) live in `filesDir/images/assets/<uuid>.png` (when the image has transparency) or `.jpg` (JPEG 85), EXIF stripped, downscaled to 512 px. Before the logo, the same field held a photo downscaled to 1600 px; those still show, decoded at the icon's size. The database stores only the relative path; paths that escape `images/` never resolve.
- Schema exported to `app/schemas`. Every change after v1 needs a migration with a `MigrationTest` case. **v2 (1.1.0, transfers):** nullable `categoryId` and `assetId` on templates and occurrences, `occurrenceId` on flows; `MIGRATION_1_2` rebuilds the three tables (SQLite can't change nullability) with foreign keys off, as Room's auto-migrations do, and adds the predefined categories introduced in 1.1.0 (Credit Card and Loans for expenses, Loans Receivable for personal assets). `MigrationTest` builds a v1 database from `1.json`, migrates it and lets Room validate v2. **v3 (US stock and crypto quotes):** `asset.ticker` (NULL for every existing asset) and the empty `asset_price` and `asset_split` tables with their unique indices; only additions, so `MIGRATION_2_3` rebuilds nothing, and `MigrationTest` checks it against `3.json`.
- Seeds (`CategorySeed`) insert the predefined categories on database creation, with stable `seedKey`s frozen in `app/src/test/resources/seed-keys.txt`. A predefined category that was never renamed has `name = NULL` and is shown with the localized string for its `seedKey` (English or Portuguese); renaming stores the user's text. Backups keep `NULL`; CSV export resolves it to the English seed name.

## Recurrence

`RecurrenceRule(unit: DAY | WEEK | MONTH | YEAR, interval: Int /* 1..99 */)`. Presets: daily (DAY,1), weekly (WEEK,1), every two weeks (WEEK,2), monthly (MONTH,1), every two months (MONTH,2), quarterly (MONTH,3), semiannually (MONTH,6), annually (YEAR,1). One-time transactions have no rule and no template.

- **Dates:** `Recurrence.dateAt(anchor, rule, n)` = `anchor.plusDays/Weeks/Months/Years(n × interval)`, always from the anchor, never chained. `java.time` clamps month ends: anchor 31 Jan → 28/29 Feb → 31 Mar → 30 Apr. Anchor 29 Feb yearly → 28 Feb in common years, 29 Feb in leap years. The anchor is never replaced by a clamped date.
- **Window:** `Recurrence.datesBetween(anchor, rule, from, to, endDate)` returns the nominal dates in `[from, to]`, never after `endDate`.
- **Horizon:** occurrences are materialized through the last day of the month 12 months ahead of today. `CashRepository.ensureGenerated()` (today from `TimeSource`) runs on app start, after any template change and after a restore. Each active template generates `datesBetween(anchor, rule, generationStart, horizonEnd, endDate)` where `generationStart = generatedThrough + 1` (or the template's creation start); then `generatedThrough = horizonEnd`.
- **Creation:** the first due date is the anchor and the start date. If it is in the past, generation starts at the first day of the current month: earlier occurrences are not backfilled (they would be history the user never recorded and would show as overdue). An occurrence due earlier this month and not yet paid shows as Overdue.
- **Duplicates:** generation runs inside a transaction under a mutex and inserts with `OnConflictStrategy.IGNORE` against the unique (`templateId`, `scheduledDate`) index. Running it twice changes nothing.
- **`scheduledDate` vs. `dueDate`:** `scheduledDate` is the nominal date produced by the rule (immutable, part of the unique key); `dueDate` starts equal to it and can be moved for a single occurrence (which sets `customized = true`).
- **Replaceable occurrences** = `state = PENDING` and `dueDate ≥ today`. Confirmed, cancelled and overdue (`dueDate < today`) occurrences are never touched by template changes.
- **Editing a template**, in one transaction:
  - *Amount, name, category or description only:* replaceable occurrences that are not `customized` are deleted and regenerated (`generatedThrough = today − 1`); customized ones keep their own values.
  - *Rule or next due date (structural):* all replaceable occurrences, customized or not, are deleted (the editor says how many individually edited ones will be replaced). The editor's "Next due date" defaults to the first replaceable occurrence's due date (or `Recurrence.firstOnOrAfter(anchor, rule, today)`); it must be ≥ today. Only if the user changes it does `anchorDate` become that date; otherwise the anchor is kept, so month-end anchors survive edits. Generation restarts at today (`generatedThrough = today − 1`).
  - *End date:* every PENDING occurrence (customized or not) with `scheduledDate > endDate` is deleted.
- **Deactivating** deletes replaceable, non-customized occurrences and stops generation. **Reactivating** sets `generatedThrough = today − 1` (no re-anchoring, no backfill of the inactive period) and regenerates.
- **Deleting** a template deletes its replaceable, non-customized occurrences; the remaining ones keep their snapshots with `templateId = NULL` and become standalone.
- **Time zone:** "today" is the local date of `TimeSource.nowMs()` in `TimeSource.zone()`. All dates are `LocalDate`; there are no times of day in the cash model.

## Occurrence status

Stored `state`: PENDING, CONFIRMED, CANCELLED. Displayed status (`OccurrenceStatus.of(occurrence, today)`):

| Condition | Status |
|---|---|
| CANCELLED | Cancelled |
| CONFIRMED, income | Received |
| CONFIRMED, expense | Paid |
| PENDING, `dueDate < today` | Overdue |
| PENDING, `dueDate ≤ today + 7` | Pending |
| PENDING, later | Upcoming |

Confirming stores `actualAmount`, `actualCurrency` (defaults to the occurrence currency; a USD bill can be paid in BRL), `actualDate ≤ today`, optional `note` and `confirmedAtMs`. The expected amount is never changed. "Mark as unpaid" clears the actual fields after a confirmation dialog. A recurring transaction cannot be created "already paid"; its occurrences are confirmed one by one.

## Cash

`CashLedger(templates, occurrences, adjustments)` (in `domain/cash`) is the input of every cash function; all functions are pure and take `today` explicitly. Per currency `c`:

- `balance(c, d)` = opening(c).amount + Σ ADJUSTMENT(c, `opening.date ≤ date ≤ d`) + Σ confirmed income(`actualCurrency = c`, `opening.date ≤ actualDate ≤ d`) − Σ confirmed expenses(same). Without an opening balance the opening is 0 with no lower date bound. The OPENING row itself is not part of the adjustments sum. Confirmations dated before the opening date are history only: the opening balance already includes them.
- **Adjustment:** `addAdjustment(target, date)` stores `target − balance(c, date)`; the date must be on or after the opening date and not in the future. Moving the opening date later than an existing adjustment is refused (delete the adjustment first).
- **Opening balance form:** Save needs an amount other than zero (the amount field can't hold an explicit 0, and without an opening cash already starts from zero). `deleteOpening(c)` removes one, refused while `c` has adjustments, which were computed from it. History lists ADJUSTMENT rows only: the opening is the starting point, shown in *Settings → Opening balances*.
- **Available cash** = `balance(c, today)` per currency; consolidated by converting each currency once at the latest rate.
- **Projected balance through `t`** = available + Σ pending income (`dueDate ≤ t`, overdue included) − Σ pending expenses (same), per currency, then converted at the latest rate. Each occurrence counts once: confirmed by actual amount on its actual date (already inside the balance), pending by expected amount, cancelled never. "Projected end of month" uses `t = endOfMonth`.
- **Month summary M:** an occurrence belongs to the month of its `actualDate` when confirmed, or of its `dueDate` when pending. Expected income = Σ confirmed income in M (actual, converted at its actual date) + Σ pending income due in M (expected, converted at the latest rate); received income = Σ confirmed income in M. Same for expenses. "Overdue from earlier months" = PENDING with `dueDate < min(M.start, today)`.
- **P&L** (the month's result) = income − expenses of M. Cash Flow shows it so far (received income − paid expenses; green when positive, red when negative) with the expected figure (expected income − expected expenses) underneath; the Overview's "This month" shows only the expected one. Unlike the projected balance it doesn't include cash already held, and transfers don't count.
- **Next month forecast** = projected balance through the end of next month; next month's expected income and expenses are shown alongside (not added again).
- **Balance history** = `balance(c, date)` converted at each date's rate, over the chart's span (3, 6 or 12 months): the first record's day when it falls in the span, then each month's end (today for the current month), so the line shows from the first month of use. Income vs. expenses covers the same span.
- **Transfers** count in the balance and projections by `cashInflow` (out of cash or into it) but never in income or expenses; the month summary reports them apart (`transfersOut`, `transfersIn`). See "Transfers".

## Exchange rates

- Source: Banco Central do Brasil SGS series 1, "Dólar americano (venda) — PTAX": `https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados?formato=json&dataInicial=dd/MM/yyyy&dataFinal=dd/MM/yyyy` (windows of at most 10 years). Response: `[{"data":"12/11/2026","valor":"5.6143"}]`. **A range without business-day rates (weekend, holiday, today before ~13:00) returns HTTP 404 with `{"erro":{..."Value(s) not found"}}`**, which the client treats as an empty result, not an error.
- `PtaxParser` accepts only well-formed entries with a positive rate between 0.1 and 100 and drops anything else; an empty array is valid.
- `ExchangeRates.rateOn(date)` = the latest rate with `date ≤ d`, preferring MANUAL over BCB on the same date (weekends and holidays use the last business day). If none exists the conversion is unavailable; values are never converted with an invented rate.
- `Converter.convert(money, to, date)`: USD→BRL multiplies by the rate, BRL→USD divides (scale 10, HALF_EVEN, then to cents). Current values use the latest rate; historical values use the rate of their own date; pending items in projections use the latest rate and are labelled "converted at today's rate".
- `Total(money, incomplete)`: when an item cannot be converted, `money` is the sum of the convertible items and `incomplete = true`; the UI shows the figure with a "≈" marker and a prompt to add an exchange rate.
- `ExchangeRateRepository.refresh(force)`:
  - Range: on the first fetch, from `min(earliest record date − 7 days, today − 30 days)` to today, where "earliest record" is the earliest date in any table. Later, from the last stored BCB date + 1 to today, plus a backfill window `[earliest record − 7 days, first stored BCB date − 1]` when a record is older than the first stored rate.
  - Inserts BCB rows with IGNORE; manual rows are separate and never touched.
  - Every successful response (including an empty 404) updates `lastFetchMs` in `RateStateStore`.
  - The automatic run (each time the app comes to the foreground, if `lastFetchMs` is older than 6 h) happens only when *Fetch exchange rates* is on. There is no background job: rates are fetched when the app opens or with *Update now* in Settings. `force = true` (that button) is an explicit user action: it ignores the 6 h throttle and the switch.
  - Failures never block the UI: the last known rate stays in use and the error is shown.
- `BcbRateClient` uses `HttpsURLConnection` (10 s timeouts, `GET`, `Accept: application/json`, generic `User-Agent: MyFinance`), refuses non-HTTPS, and reads at most 1 MiB. `network_security_config.xml` disables cleartext.

## Market prices

An asset with a `ticker` follows a US stock, ETF or crypto quote (a *tracked* asset). It is USD only, and ticker and currency never change after creation.

- **Source:** Yahoo Finance, unofficial and keyless. Chart: `https://query1.finance.yahoo.com/v8/finance/chart/{SYM}?period1=&period2=&interval=1d&events=split`. Search (ticker picker in the asset editor): `https://query2.finance.yahoo.com/v1/finance/search?q=&quotesCount=10&newsCount=0&listsCount=0`, keeping EQUITY, ETF and CRYPTOCURRENCY (crypto only with the `-USD` suffix). `YahooQuoteClient` uses `HttpsURLConnection` (10 s timeouts, GET, no redirects, `Accept: application/json`, `User-Agent: MyFinance`) and reads at most 4 MiB (chart) or 256 KiB (search).
- **Tickers** (after trim and uppercase, `MarketData`): stock or ETF `^[A-Z0-9]{1,6}(-[A-Z]{1,2})?$`, crypto `^[A-Z0-9]{1,12}-USD$`. Only `currency == "USD"` answers are accepted.
- **Raw prices and splits.** Yahoo returns split-adjusted closes. `YahooChartParser` multiplies each close back by every split after its date, so `asset_price` holds the price as it was traded that day, rounded to 7 significant digits (`HALF_EVEN`) and stored as a plain decimal string. Closes are read from their JSON text, never through `Double` (a tiny crypto price can arrive as `3.789999936998356e-06`). A candle's date is the date in `meta.exchangeTimezoneName` (UTC for crypto, `America/New_York` for stocks, which is also the default), so a late-evening UTC bar keeps its New York date.
- **Share counts.** Flows carry the shares of the day they were made. `Holdings.quantityAt(flows, splits, d)` rescales them by the splits on or before `d` (scale 10, `HALF_EVEN`), so entering an old purchase with today's split-adjusted count gives the right value on every date. The asset editor shows the split note and today's count before saving, and Sell is refused when it would take the position below zero.
- **Value at `d`** (`PortfolioCalculator`): closed → 0; quantity known and a stored price on or before `d` → `Money.of(quantity × price)`; otherwise net invested. Gain and return use the same capital-recovery basis as other assets, and the value is converted with the PTAX of `d` like any US$ asset. Tracked assets take no valuations and no withdrawals.
- **Refresh** (`PriceRepository.refresh`): runs on foreground (`MainActivity`) when the switch is on, at most every 15 minutes counted from the last attempt; *Update now* and creating a tracked asset (on the application scope, so leaving the editor does not cancel it) fetch regardless of both. No request is made when no asset has a ticker. Positions are fetched per symbol, in sorted order, serialized by a mutex. A network error stops the run; an unknown symbol or an unexpected answer skips that symbol; the first error is kept in `PriceStateStore` and shown in Settings, while the last price and its date stay in use.
- **Windows.** Every request ends at "now", with no chunking. The first fetch of a symbol starts 7 days before the earliest flow of the assets that hold it (so a date always has a close on or before it). Later fetches start 7 days before the last stored close, unless a flow predates the first stored price (an older purchase added later), which fetches the full window again. If the answer holds a split that is not stored yet, the run fetches the full window once more, because earlier raw prices change. A symbol whose assets are all closed, with a close stored on or after the last disposal, is skipped.
- **Storage:** closes and splits are written in one transaction (`INSERT OR REPLACE` for prices, ignore for splits); a change schedules a backup. Both tables travel in backups (format 2) and in the CSV export.
- **Buy and Sell** on a tracked asset record flows with a share count; with cash they create the usual confirmed transfer, whose asset side is kept in sync. Editing such a done transfer from Cash Flow is refused (`TrackedAssetTransferException`): undo or delete it instead.
- **Known limitations.** Spin-offs appear as fractional splits, so the value stays continuous, as if the spun-off shares were folded into the holding. Dividends are not tracked, so the gain understates total return. Yahoo may change or block its answers (cookies, 429): the effect is a stale price with its date and an error in Settings; the stored history is in the backup.

## Transfers

A transfer moves money between cash and an asset (1.1.0, user request). It is the only link between Cash and the Portfolio; everything else stays separate.

- `EntryKind` has four values: INCOME, EXPENSE, TRANSFER_OUT (cash → asset, a contribution) and TRANSFER_IN (asset → cash, a withdrawal). `cashInflow` gives the sign for cash; `isTransfer` keeps transfers out of income, expenses and category charts (they have no category). Because the kind is stored, a transfer stays a transfer after its asset is deleted (`assetId` becomes NULL).
- Transfers are ordinary occurrences: one-time or recurring, pending until confirmed, projected in the cash forecast, listed in History (in their month) and in Cash Flow (their own section and month totals). A blank name takes the asset's.
- **Confirmation records the asset side** (`TransferFlows.sync`, inside the same transaction): TRANSFER_OUT creates a CONTRIBUTION, TRANSFER_IN a WITHDRAWAL, linked by `asset_flow.occurrenceId`, dated the actual date, in the asset's currency — the same amount when the currencies match, otherwise the amount the user typed (prefilled at the date's PTAX; the broker's figure is what counts). Undoing the confirmation or deleting the transfer removes the flow; a confirmed transfer can't change its asset or direction (undo it first).
- **From the asset side**, contribute / withdraw / close and a new asset's initial investment have a "From cash" / "To cash" switch: it records a confirmed one-time transfer linked to the flow (a disposal keeps its own kind). Deleting such a flow deletes a one-time transfer, or puts a recurring one back to pending. A transfer that created the initial investment can't be undone (the asset needs it): deleting it unlinks the flow, which stays as the initial investment.
- **Asset lifecycle:** closing a position deactivates its recurring transfers (future pending ones removed); new transfers to a closed position are refused. Deleting an asset deletes its recurring and pending transfers; the done ones are undone too when the user leaves "Undo in cash too" on (the default: deleting usually removes something entered by mistake, and the money is back in cash), otherwise they stay in the cash history without the asset. The switch shows what cash gets back (`cashMovedBy`) and only appears when there are done transfers.
- Rules enforced: a transfer needs an asset and no category; it can't be dated before the asset's initial investment; refusals reach the UI as a dialog (`ActionError`), never a crash.

## Portfolio

Flows and valuations are in the asset's currency. Per asset, at date `d`:

- `contributed(d)` = Σ INITIAL + CONTRIBUTION with `date ≤ d`; `withdrawn(d)` = Σ WITHDRAWAL + PARTIAL_DISPOSAL + FULL_DISPOSAL with `date ≤ d`; `netInvested(d)` = contributed − withdrawn.
- `value(d)`: if a valuation exists on or before `d`, the latest such valuation plus net flows dated after it and on or before `d` (carry-forward; a valuation on day D already includes the flows of day D). Without any valuation, `netInvested(d)`. Never below 0. After a FULL_DISPOSAL on or before `d`, 0. Before the first flow or valuation, no value (the asset does not exist yet).
- `gain(d)` = value − netInvested (= value + withdrawn − contributed: capital-recovery basis, includes amounts already withdrawn). `simpleReturn(d)` = gain ÷ contributed (null when contributed = 0).
- Valuation history rows show, at each valuation date, the value, netInvested, gain and simple return derived from the records up to that date. Nothing is stored twice and nothing is overwritten.
- **Display currency:** for an asset in another currency, contributed and withdrawn are converted at each flow's own date, value at the rate of `d`. The gain in display currency therefore includes the currency effect; the asset detail shows the gain in the asset currency and in the display currency side by side.
- **Dashboard totals** are over open assets: value, invested (= Σ netInvested), gain (= value − invested), simple return (= gain of the assets that received contributions ÷ their Σ contributed; a granted or inherited asset with nothing contributed adds to the gain but not to the return), last valuation date (= the most recent valuation among them). Closed assets are listed apart with their final (realized) gain, shown as "Realized from closed positions".
- **Period gain** (1M/3M/6M/1Y/All) = (value(end) − value(start)) − net flows in (start, end], where value(start) = 0 if the asset had no record yet; null only when there is no record on or before `end`. Absolute only: no percentage is shown for periods (it would require a time-weighted method).
- **Allocation** = value by category ÷ total value of open assets, for All, Investments or Personal assets; shares are computed at scale 4 and the rounding remainder goes to the largest slice so they sum to 1.
- **Cash for reference** (1.1.0): in "All", once Cash Flow has an opening balance or a done entry, the screen shows available cash (from `CashCalculator.available`, converted at the latest rate) as a card that opens Cash Flow and, when positive, as a donut slice; asset slices are scaled by (1 − cash share) and the centre shows the total with cash. Value, invested, gain and return never include it.
- **Series** sample dates between the first record and today: weekly plus today for 1M; month ends plus today for the other periods. No point is drawn before the first record, and values are never interpolated between valuations (carry-forward only). "Invested vs value" covers assets open at each date; "performance" is the cumulative gain of every asset with records up to that date (closed assets keep their realized gain).

## Net worth

`NetWorthCalculator.at(date, cash, portfolio, to, converter)` = Σ cash balances + Σ investment values + Σ personal asset values at `date`, each converted to `to` at `date`'s rate. `series(dates, cash, portfolio, to, converter)` maps it over sample dates. The Overview shows the composition. Because portfolio flows never move cash, the user keeps cash aligned with the bank through confirmed transactions and balance adjustments; the composition makes any double counting visible.

## Backup

Same design as MyFit, adapted. Full format and recovery process in [`BACKUP.md`](BACKUP.md); security model in [`SECURITY.md`](SECURITY.md).

- `.myfinance` file: `"MYFINBAK"` header + Tink streaming AES-256-GCM-HKDF (1 MiB segments, header as associated data) over a zip with `manifest.json`, `data.json` (`BackupDataV1`), `settings.json` and `images/assets/*`.
- Key from PBKDF2-HMAC-SHA256 (600k iterations, NFKC) of the recovery password, kept wrapped by an Android Keystore AES key.
- Folder via SAF, 7 kept; the new file is fully decrypted and validated (`BackupManager.verify`) before older ones are deleted. Daily run, 10-minute debounce after changes, "Back up now". Without a folder or password, automatic runs are skipped silently (not an error).
- Format 2 (stock quotes) adds `assets.ticker`, `assetPrices` and `assetSplits`; the version is 2 only when a backup holds a ticker, a price or a split, and their manifest counts are written only when present (see [`BACKUP.md`](BACKUP.md)).
- Restore validates everything before touching data and replaces all tables in one Room transaction; then regenerates the occurrence horizon.

## Navigation and UI

- Navigation Compose with type-safe routes in a `NavigationSuiteScaffold`: bottom bar on phones, rail on large screens, hidden on editors and detail screens. Five tabs: Overview, Cash Flow, Portfolio, History, Settings. 200 ms fades. Content capped at 640 dp.
- Screen pattern: `XUiState` data class with `loading`; `stateIn(WhileSubscribed(5_000))`; stateful `XScreen` wraps a stateless `XContent(state, actions)` used by screenshot tests; editors expose `save(): Boolean` and a `done` flag.
- Theme from settings (dark by default). Tokens, typography (Geist with tabular figures) and components are in [`BRAND.md`](BRAND.md).
- Strings in `res/values` (English) and `res/values-pt-rBR`. `localeConfig` is generated so Android 13+ offers per-app language.

## Tests

| Layer | Tool | Examples |
|---|---|---|
| Pure domain | JUnit | recurrence tables, statuses, cash balances and projections, portfolio gains and series, conversion, PTAX parsing, Yahoo chart and search parsing (captured responses), tickers, share counts across splits, tracked-asset values, net worth, money formatting, CSV, crypto, zip, retention |
| Data | Robolectric + in-memory Room | DAOs and constraints, idempotent generation, template edits, repositories, snapshot round trip, backup create/restore/corrupt, rate refresh with a fake client, price refresh with a fake Yahoo client (windows, throttle, splits, closed positions), v2 → v3 migration |
| Platform | Robolectric + work-testing | backup worker, rate worker |
| UI | Compose test + Robolectric | create income/expense, confirm payment/receipt, create asset, create a tracked asset by ticker, buy and sell shares, update valuation, navigation, settings, backup and restore |
| Visual | Roborazzi | every screen in dark and light, 1.5× font and tablet |

Commands and the on-device checklist are in [`TESTING.md`](TESTING.md).

## Decisions and trade-offs

- **Portfolio separate from cash** (user decision): simpler model, no hidden transfers; the cost is that the user must keep cash aligned, and the Overview makes the composition explicit. Since 1.1.0 an explicit **transfer** links the two when the user wants it (user request); balance adjustments remain for everything else. The Portfolio shows cash in "All" for reference only, never in its figures.
- **One cash balance per currency** (user decision): no accounts; a USD account is the USD cash balance. Transfers only go between cash and assets.
- **Transfers as occurrences** over a separate table: they get recurrence, confirmation, projections, history and backup for free; the price is two new kinds that every income/expense rule must handle (`cashInflow`, `isTransfer`).
- **BCB PTAX over other rate APIs:** official, free, keyless, stable for years. AwesomeAPI (intraday, unofficial) and Frankfurter (ECB) were considered and rejected.
- **Yahoo Finance over keyed APIs (user decision):** a free, keyless source with daily closes and splits for US stocks, ETFs and crypto, and no account or key to store. The cost is that it is unofficial and may change or block: prices go stale with their date and an error in Settings, never an invented value. Alpha Vantage, Twelve Data and similar need a key and tight free limits.
- **Raw prices plus a splits table over adjusted closes:** adjusted closes change after every new split, which would rewrite history; raw prices with the split ratios keep stored data stable and make a split a visible event.
- **`HttpsURLConnection` over Retrofit/OkHttp:** a single GET does not justify the dependencies.
- **Materialized occurrences (12-month horizon) over virtual ones:** history, filters and confirmation work on real rows; the unique index makes duplicates impossible.
- **No backfill of past occurrences:** a recurring transaction created with a past first due date starts in the current month, so the projection is not polluted by bills the user never tracked.
- **Simple return only:** transparent and always computable from the records; TWR/MWR would need complete cash-flow data and are out of scope.
- **Sandbox-only local storage** (user decision): Android file-based encryption protects the database at rest; SQLCipher would add size and complexity for protection that only matters on compromised devices.
- **Single module:** personal app; boundaries live in packages and the dependency rule.
- **Navigation 2 and Room 2.8:** same as MyFit, stable and well documented.
- **No device during development:** platform behaviour is validated with Robolectric plus the manual checklist.
