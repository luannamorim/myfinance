# MyFinance — Architecture

## Overview

Native Android app, single module (`:app`), MVVM, offline-first. Room is the source of truth. The only network traffic is the public exchange-rate request to the Banco Central do Brasil; nothing else leaves the device except the encrypted backups and CSV exports the user writes to a folder.

```
ui/        Compose: screens + ViewModels (Hilt), design system, navigation
  │  observes StateFlow, calls actions
domain/    pure Kotlin: money, recurrence, cash, portfolio, exchange rates, net worth, export, backup format
  ▲  used by
data/      Room, DataStore, ImageStore, repositories, BCB client, BackupManager, SAF destination
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
| `domain/networth` | `NetWorthCalculator` |
| `domain/export` | `Csv` |
| `domain/backup` | v1 DTOs, header and encryption, zip + manifest, retention |
| `data/db` | entities, DAOs, `MyFinanceDatabase`, `Migrations`, seeds |
| `data/repository` | categories, cash (templates, occurrences, adjustments), portfolio, exchange rates, export |
| `data/rates` | `BcbRateClient` (HTTPS GET) |
| `data/prefs` | `SettingsRepository`, `RateStateStore`, `BackupStateStore` |
| `data/backup` | `BackupManager`, `BackupKeyStore`, destinations (SAF/file), `FolderBackupWriter` |
| `platform` | `BackupScheduler` + workers (`BackupWork.kt`), `RateRefreshWorker`, backup failure notification |

## Money

- `Money(minor: Long, currency: Currency)`; `Currency` is an enum (`BRL`, `USD`) carrying ISO code, symbol (`R$`, `US$`) and fraction digits (2). Adding a currency means adding an enum entry, rates for it and strings; no schema change.
- Entities store `amount` as `Long` minor units. Every table with money has a `currency` column, except asset flows and valuations, which are always in their asset's currency (the asset currency cannot change after creation). `Double` is never used for money.
- Conversions and ratios use `BigDecimal` with **scale 10** and `RoundingMode.HALF_EVEN` for intermediate values. Sums are accumulated in `BigDecimal` and rounded to minor units (HALF_EVEN) once, at the end of each total. Percentages are rounded to two decimals only when displayed.
- Asset quantity is an optional `BigDecimal` stored as TEXT (`toPlainString()`), informational only.
- Formatting (`MoneyFormatter`) is the only place that turns money into text: symbol + no-break space + number in the chosen format (`R$ 1.234,56` or `R$ 1,234.56`), a true minus sign (`−`) for negatives, and an explicit `+` only where a signed delta is shown.

## Data

```
category ─┬─ transaction_template ──< transaction_occurrence
          └───────────────────────────< (standalone occurrences have no template)
cash_adjustment            (OPENING per currency, ADJUSTMENT)
asset_category ── asset ─┬─< asset_flow
                         └─< asset_valuation
exchange_rate              (USD→BRL per date and source)
```

Conventions (same as MyFit): `id Long` auto-generated; enums stored as TEXT names; calendar dates as `epochDay: Long`; instants as `*Ms: Long`; no TypeConverters.

| Table | Columns | Keys and rules |
|---|---|---|
| `category` | `name?`, `kind` (INCOME/EXPENSE), `icon`, `seedKey?`, `archived`, `position`, `createdAtMs` | unique `seedKey`. Kind fixed after creation. Delete only if unused, otherwise archive (hidden from pickers, kept in history). |
| `transaction_template` | `name`, `categoryId`, `kind`, `currency`, `expectedAmount`, `recurrenceUnit`, `recurrenceInterval`, `startDate`, `anchorDate`, `endDate?`, `description?`, `active`, `generatedThrough?`, `createdAtMs`, `updatedAtMs` | `categoryId` RESTRICT. Recurring only. |
| `transaction_occurrence` | `templateId?`, `scheduledDate?`, `dueDate`, `name`, `categoryId`, `kind`, `currency`, `expectedAmount`, `description?`, `state` (PENDING/CONFIRMED/CANCELLED), `actualAmount?`, `actualCurrency?`, `actualDate?`, `note?`, `confirmedAtMs?`, `customized`, `createdAtMs`, `updatedAtMs` | `templateId` SET NULL; `categoryId` RESTRICT; unique (`templateId`, `scheduledDate`); indices on `dueDate`, `actualDate`, `state`, `categoryId`. |
| `cash_adjustment` | `kind` (OPENING/ADJUSTMENT), `currency`, `date`, `amount` (signed), `note?`, `createdAtMs` | At most one OPENING per currency (enforced by the repository, tested). |
| `asset_category` | `name?`, `assetGroup` (INVESTMENT/PERSONAL), `icon`, `seedKey?`, `archived`, `position` | unique `seedKey`. Same delete/archive rule as `category`. |
| `asset` | `name`, `categoryId`, `currency`, `description?`, `institution?`, `acquisitionDate`, `quantity?` (TEXT), `notes?`, `imagePath?`, `createdAtMs`, `updatedAtMs` | `categoryId` RESTRICT. Currency locked after creation. Deleting requires confirmation; closing the position is the normal path. |
| `asset_flow` | `assetId`, `kind` (INITIAL/CONTRIBUTION/WITHDRAWAL/PARTIAL_DISPOSAL/FULL_DISPOSAL), `date`, `amount`, `quantityDelta?` (TEXT), `note?`, `createdAtMs` | `assetId` CASCADE; index (`assetId`, `date`). `amount > 0`, except INITIAL which may be 0 (inherited or granted assets). At most one INITIAL and one FULL_DISPOSAL per asset. |
| `asset_valuation` | `assetId`, `date`, `value` (≥ 0), `note?`, `createdAtMs` | `assetId` CASCADE; unique (`assetId`, `date`): a second valuation on the same day replaces it, after a warning. |
| `exchange_rate` | `base` (USD), `quote` (BRL), `date`, `rateMicros` (rate × 10⁶), `source` (BCB/MANUAL), `fetchedAtMs` | unique (`base`, `quote`, `date`, `source`): a fetched and a manual rate can coexist on the same date; the manual one wins. |

- **Standalone occurrence** = `templateId IS NULL`: one-time transactions, and rows kept after their template was deleted. Only standalone occurrences can be deleted; generated ones are cancelled instead.
- **Closed asset** = an asset with a FULL_DISPOSAL flow (derived, not stored). New flows and valuations after the disposal date are blocked until the disposal is deleted.
- Occurrences keep a snapshot of `name`, `kind`, `currency` and `description`, so editing or deleting a template never rewrites history. Category names are referenced, not copied: renaming a category is meant to apply to history.
- Settings (theme, number and date formats, default and display currency, automatic rate fetching, automatic backup) live in DataStore and travel in the backup's `settings.json`. Rate fetch state (`lastFetchMs`, last error) and backup state live in their own DataStores.
- Images (optional asset photo) live in `filesDir/images/assets/<uuid>.jpg`, EXIF stripped, downscaled to 1600 px (JPEG 85). The database stores only the relative path; paths that escape `images/` never resolve.
- Schema exported to `app/schemas`. Every change after v1 needs a migration with a `MigrationTest` case.
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
- **Available cash** = `balance(c, today)` per currency; consolidated by converting each currency once at the latest rate.
- **Projected balance through `t`** = available + Σ pending income (`dueDate ≤ t`, overdue included) − Σ pending expenses (same), per currency, then converted at the latest rate. Each occurrence counts once: confirmed by actual amount on its actual date (already inside the balance), pending by expected amount, cancelled never. "Projected end of month" uses `t = endOfMonth`.
- **Month summary M:** an occurrence belongs to the month of its `actualDate` when confirmed, or of its `dueDate` when pending. Expected income = Σ confirmed income in M (actual, converted at its actual date) + Σ pending income due in M (expected, converted at the latest rate); received income = Σ confirmed income in M. Same for expenses. "Overdue from earlier months" = PENDING with `dueDate < min(M.start, today)`.
- **Next month forecast** = projected balance through the end of next month; next month's expected income and expenses are shown alongside (not added again).
- **Balance history** = `balance(c, monthEnd)` for each past month, converted at each month-end's rate.

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
  - Automatic runs (app start when `lastFetchMs` is older than 6 h; `RateRefreshWorker` once a day with a network constraint) happen only when *Fetch exchange rates* is on. `force = true` (the "Update" button) is an explicit user action: it ignores the 6 h throttle and the switch.
  - Failures never block the UI: the last known rate stays in use and the error is shown.
- `BcbRateClient` uses `HttpsURLConnection` (10 s timeouts, `GET`, `Accept: application/json`, generic `User-Agent: MyFinance`), refuses non-HTTPS, and reads at most 1 MiB. `network_security_config.xml` disables cleartext.

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
- **Series** sample dates between the first record and today: weekly plus today for 1M; month ends plus today for the other periods. No point is drawn before the first record, and values are never interpolated between valuations (carry-forward only). "Invested vs value" covers assets open at each date; "performance" is the cumulative gain of every asset with records up to that date (closed assets keep their realized gain).

## Net worth

`NetWorthCalculator.at(date, cash, portfolio, to, converter)` = Σ cash balances + Σ investment values + Σ personal asset values at `date`, each converted to `to` at `date`'s rate. `series(dates, cash, portfolio, to, converter)` maps it over sample dates. The Overview shows the composition. Because portfolio flows never move cash, the user keeps cash aligned with the bank through confirmed transactions and balance adjustments; the composition makes any double counting visible.

## Backup

Same design as MyFit, adapted. Full format and recovery process in [`BACKUP.md`](BACKUP.md); security model in [`SECURITY.md`](SECURITY.md).

- `.myfinance` file: `"MYFINBAK"` header + Tink streaming AES-256-GCM-HKDF (1 MiB segments, header as associated data) over a zip with `manifest.json`, `data.json` (`BackupDataV1`), `settings.json` and `images/assets/*`.
- Key from PBKDF2-HMAC-SHA256 (600k iterations, NFKC) of the recovery password, kept wrapped by an Android Keystore AES key.
- Folder via SAF, 7 kept; the new file is fully decrypted and validated (`BackupManager.verify`) before older ones are deleted. Daily run, 10-minute debounce after changes, "Back up now". Without a folder or password, automatic runs are skipped silently (not an error).
- Restore validates everything before touching data and replaces all tables in one Room transaction; then regenerates the occurrence horizon.

## Navigation and UI

- Navigation Compose with type-safe routes in a `NavigationSuiteScaffold`: bottom bar on phones, rail on large screens, hidden on editors and detail screens. Five tabs: Overview, Cash Flow, Portfolio, History, Settings. 200 ms fades. Content capped at 640 dp.
- Screen pattern: `XUiState` data class with `loading`; `stateIn(WhileSubscribed(5_000))`; stateful `XScreen` wraps a stateless `XContent(state, actions)` used by screenshot tests; editors expose `save(): Boolean` and a `done` flag.
- Theme from settings (dark by default). Tokens, typography (Geist with tabular figures) and components are in [`BRAND.md`](BRAND.md).
- Strings in `res/values` (English) and `res/values-pt-rBR`. `localeConfig` is generated so Android 13+ offers per-app language.

## Tests

| Layer | Tool | Examples |
|---|---|---|
| Pure domain | JUnit | recurrence tables, statuses, cash balances and projections, portfolio gains and series, conversion, PTAX parsing, net worth, money formatting, CSV, crypto, zip, retention |
| Data | Robolectric + in-memory Room | DAOs and constraints, idempotent generation, template edits, repositories, snapshot round trip, backup create/restore/corrupt, rate refresh with a fake client |
| Platform | Robolectric + work-testing | backup worker, rate worker |
| UI | Compose test + Robolectric | create income/expense, confirm payment/receipt, create asset, update valuation, navigation, settings, backup and restore |
| Visual | Roborazzi | every screen in dark and light, 1.5× font and tablet |

Commands and the on-device checklist are in [`TESTING.md`](TESTING.md).

## Decisions and trade-offs

- **Portfolio separate from cash** (user decision): simpler model, no hidden transfers; the cost is that the user must keep cash aligned (balance adjustments), and the Overview makes the composition explicit.
- **One cash balance per currency** (user decision): no accounts or transfers; a USD account is the USD cash balance.
- **BCB PTAX over other rate APIs:** official, free, keyless, stable for years. AwesomeAPI (intraday, unofficial) and Frankfurter (ECB) were considered and rejected.
- **`HttpsURLConnection` over Retrofit/OkHttp:** a single GET does not justify the dependencies.
- **Materialized occurrences (12-month horizon) over virtual ones:** history, filters and confirmation work on real rows; the unique index makes duplicates impossible.
- **No backfill of past occurrences:** a recurring transaction created with a past first due date starts in the current month, so the projection is not polluted by bills the user never tracked.
- **Simple return only:** transparent and always computable from the records; TWR/MWR would need complete cash-flow data and are out of scope.
- **Sandbox-only local storage** (user decision): Android file-based encryption protects the database at rest; SQLCipher would add size and complexity for protection that only matters on compromised devices.
- **Single module:** personal app; boundaries live in packages and the dependency rule.
- **Navigation 2 and Room 2.8:** same as MyFit, stable and well documented.
- **No device during development:** platform behaviour is validated with Robolectric plus the manual checklist.
