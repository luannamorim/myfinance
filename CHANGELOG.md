# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Assets can have a logo: pick an image in the asset's editor and it replaces the category icon in the Portfolio and on the asset's screen, whole on a white circle. Logos with a transparent background keep it. An asset without a logo keeps the category icon.

### Changed
- The asset photo is now its logo: the large photo at the top of the asset's screen is gone, and a photo added before shows as the logo.

### Fixed
- Deleting an asset that had transfers with cash (such as one created with "Take it from cash") left them in the cash history, so the money never came back. The delete dialog now offers *Undo in cash too*, on by default, with the amount that goes back to cash; turned off, the transfers stay in the history as before.

## [1.5.0] - 2026-11-23

### Added
- Portfolio: add a US stock, ETF or crypto by its ticker (e.g. NVDA, VOO, BTC-USD), with the quantity and the price per unit. Its value follows the latest quote from Yahoo Finance (with a daily history), converted at the PTAX like any US$ asset, and the detail shows the quote with its date. Buy and Sell record the shares (with or without cash) and Sell all closes the position.
- Quantities follow the splits: a purchase made before a split shows today's number of shares, and the form tells you so before you save.
- Settings → Currency and exchange rate → Stocks and crypto: *Fetch stock and crypto quotes* turns automatic fetching off, *Update now* fetches on demand, and the last fetch and any error are shown. Quotes are fetched when you open the app, at most every 15 minutes; there is no background job.
- Backups and the CSV export include the quotes and splits (`asset_prices.csv`, `asset_splits.csv`).

### Changed
- Backups that hold tracked assets or quotes use backup format 2 and are refused by older versions of the app; backups without them stay readable by those versions.
- The privacy text and the security notes name the second destination, Yahoo Finance, and what is sent to it: the tickers you hold, or the text you type in the ticker search, and a date range.

## [1.4.1] - 2026-11-18

### Added
- Settings → Opening balances: *Remove* deletes a saved opening balance (after a confirmation), for one saved by mistake. Refused while that currency has balance adjustments, which were made from it.

### Fixed
- History listed opening balances as "Balance adjustment"; a US$ or R$ opening saved with an empty field showed as "Balance adjustment · R$ 0,00" on the day it was saved. History now lists only real balance adjustments.
- Settings → Opening balances: *Save* is disabled while the amount is 0,00, so an untouched field no longer creates a zero opening balance dated today (which also hid that currency's earlier confirmed transactions).

## [1.4.0] - 2026-11-18

### Changed
- Overview: "This month" shows only what the month expects (income, expenses and P&L); what is done so far stays in Cash Flow. The P&L is green when positive and red when negative.
- Cash Flow: "Month result" is now "P&L" and shows what is left so far (received − paid), with "of … expected" underneath instead of the two captions.
- Cash Flow: "Transfers this month" is now "Transfers", with the amount on the title line and where it went ("to investment") underneath.
- History: opens on the current month with arrows to change it, like Cash Flow. The type, currency, status, category and date filters give way to two chips, Open (overdue, pending, upcoming) and Done (paid, received, transferred, and balance adjustments). Search stays and looks within the month.

### Removed
- The exchange-rate line (US$ 1 = R$ … · PTAX) at the bottom of Overview, Cash Flow, Portfolio and the asset detail; the rate and its date are in Settings → Currency and exchange rate.
- Overview: the "Moving money between cash and an investment?" hint.

## [1.3.0] - 2026-11-18

### Added
- Overview and Cash Flow: icons next to income (↑), expenses (↓), available cash, projected, transfers and month result (=).
- Overview: the month result in "This month" (so far of expected).
- Overview: a round flag in the header switches the currency amounts are shown in (R$ ⇄ US$), replacing the R$ / US$ chips.
- Settings → Currency: "Currencies in use" picks the currencies the flag switches between; at least one stays on.

### Changed
- Cash Flow: the next month's projection reads "Projected · 31/12" instead of "Projected at the end of December 2026".
- Overview, Cash Flow and History: transactions are shown in the display currency too, with the amount as entered underneath. Paid and received ones convert at the PTAX of their date, pending ones at the latest rate, as the totals do.
- Overview: "This month" marks its amounts with "≈" when one had no rate and was left out, like the other totals.
- Settings: About is now a card in Settings (version, author and font license), like MyFit; the separate About screen and the source code link are gone.

## [1.2.3] - 2026-11-16

### Changed
- Tab headers: less space between the header and the content (8 dp instead of 16 dp).
- Overview: net worth sits closer to the title, without the extra 8 dp above it.

## [1.2.2] - 2026-11-16

### Changed
- Overview: today's date moved above the title.

## [1.2.1] - 2026-11-16

### Changed
- Overview: today's date below the title (12 de novembro de 2026 / November 12, 2026).
- Tab headers: the title is centered on the header's buttons instead of sitting slightly below them.

## [1.2.0] - 2026-11-16

### Added
- Overview: a "+" in the header adds a transaction (Cash Flow) or an asset (Portfolio).
- History: a Month filter. It starts at the current month and the arrows move it; it combines with the other filters.

### Changed
- Cash Flow: the month result's caption no longer says that transfers don't count (they still don't).

## [1.1.2] - 2026-11-13

### Added
- Cash Flow shows the month's result below income and expenses: expected income − expected expenses (green or red), and what it is so far (received − paid). Transfers don't count.

### Changed
- Cash Flow lists income to receive first, before overdue and pending bills.

## [1.1.1] - 2026-11-13

### Fixed
- Deleting an asset could close the app: the portfolio is now read as one consistent snapshot.

## [1.1.0] - 2026-11-13

### Added
- Transfers between cash and the portfolio: to an investment (a contribution) or from it (a withdrawal), one-time or recurring, confirmed like a bill and recorded on both sides at once. They are neither income nor expense; a transfer between currencies records the amount on each side (prefilled at the PTAX). Contribute, withdraw and close on an asset, and a new asset's initial investment, can come from or go to cash.
- Cash Flow shows the month's transfers and a Transfers list; History has a Transfer filter; the CSV export carries the asset of each transfer and links flows to it.
- Portfolio shows the available cash in "All", for reference (a donut slice and a card that opens Cash Flow), never in its value, gain or return.
- Cash Flow charts: 3, 6 or 12 months.
- Predefined categories: Credit Card and Loans (expenses), Loans Receivable (personal assets).

### Changed
- New symbol: the allocation ring completed by the green dot replaces the rising "F" in the app icon, themed icon, notification icon, splash screen and in-app mark.
- The Overview's upcoming list shows only overdue commitments and those due this month.
- The balance chart starts at the first record, so it shows from the first month of use.
- Exchange rates are fetched when the app comes to the foreground (at most every 6 hours) or with *Update*; the daily background job is gone.
- Bottom bar label "Fluxo" in Portuguese, so it fits.
- Database schema 2, migrated automatically from 1.0.0; backups from 1.0.0 restore as is.

### Removed
- The Language row in Settings: the app follows the phone's language (Android's per-app language setting still works).

## [1.0.0] - 2026-11-13

First release.

### Added
- Cash flow: one-time and recurring income and expenses with daily, weekly, every-two-weeks, monthly, every-two-months, quarterly, semiannual, annual and custom recurrence; month-end and leap-year handling; no duplicate occurrences; edits that only replace pending occurrences from today on.
- Payment and receipt confirmation with actual amount, currency, date and note, kept apart from the expected amount; cancel, restore and undo.
- Opening balances and balance adjustments per currency.
- Cash Flow dashboard (expected vs. received/paid, available cash, projected end-of-month balance, overdue, pending, upcoming income, next month) and charts (income vs. expenses, balance evolution, by category).
- History with accent-insensitive search, filters by type, status, category, currency and date, grouped by month.
- Portfolio: investments and personal assets with categories, contributions, withdrawals, partial sales and closed positions, dated valuations with full history, simple return with its method stated, allocation donut, charts by period (1M, 3M, 6M, 1Y, All).
- Overview: net worth with its composition (cash, investments, personal assets), available and projected cash, this month, upcoming commitments with quick confirmation, recent transactions.
- BRL and USD everywhere, converted with the Banco Central do Brasil PTAX (automatic or on demand, manual fallback), always showing the rate and its date; incomplete totals are flagged.
- Encrypted backups (AES-256-GCM streaming, PBKDF2-SHA256 600k, Android Keystore) to a user-chosen folder, daily and after changes, keeping 7, fully verified before older ones are deleted; restore from the folder or a file.
- CSV export of transactions, cash adjustments, assets, flows, valuations and exchange rates.
- Categories management, number and date formats, dark/light/system theme, English and Brazilian Portuguese.
- Accessibility: amounts spoken with "minus" instead of the symbol, chart summaries for TalkBack, and layouts that reflow instead of clipping at 1.5× font size and on tablets.
- MyFinance identity: rising-"F" mark, wordmark, adaptive and themed launcher icons, splash screen.
