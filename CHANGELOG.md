# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

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
