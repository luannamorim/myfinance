# MyFinance — Testing

## Automated

```bash
export JAVA_HOME=~/.local/share/jdks/jdk-21.0.13+9 ANDROID_HOME=~/Android/Sdk
./gradlew testDebugUnitTest      # unit + Robolectric (domain, database, platform, UI)
./gradlew recordRoborazziDebug   # screenshots in app/build/outputs/roborazzi/
./gradlew lintDebug
./gradlew assembleRelease        # R8 build (signed if MYFINANCE_* is configured)
```

GitHub Actions runs `testDebugUnitTest lintDebug assembleDebug` on every push and pull request.

| Layer | Tool | What it proves |
|---|---|---|
| Pure domain | JUnit | recurrence (monthly, weekly, biweekly, quarterly, annual, leap years, month ends, end dates, horizons), statuses and day boundaries, cash balances and projections without double counting, expected vs. actual, opening balance, portfolio contributions, withdrawals, disposals, valuations, gains, returns, allocation and series, conversion and rate lookup, PTAX parsing, net worth, money formatting and input, backup crypto, archive and retention |
| Data | Robolectric + in-memory Room | constraints (RESTRICT, CASCADE, unique), idempotent occurrence generation, template edits, confirmations, adjustments, repositories, snapshot round trip, backup create/restore/wrong password/corrupted archive, rate refresh with a fake client |
| Platform | Robolectric + work-testing | backup worker (dirty/skip/failure mapping, debounce), rate refresh worker |
| UI flows | Compose test + Robolectric | create income, create expense, confirm payment, confirm receipt, create asset, update valuation, navigate between tabs, change settings, backup and restore |
| Visual | Roborazzi | every screen in dark and light, 1.5× font and tablet; component and icon catalog |

Conventions:
- Domain tests use hand-computed expected values written in the test (no values copied from the implementation).
- Time is injected through `FakeTimeSource` with zone `America/Sao_Paulo`; day-boundary tests also run with UTC.
- UI tests run in English (`robolectric.properties`); one test per screen runs in `pt-rBR` to prove the translation exists.
- Screenshots use synthetic data only.

## Manual checklist on the phone

Development is done without a device. The items below validate what only the device can prove. Check each item; if something fails, note the step and the phone model.

### 0. Installation
- [ ] Install the APK (`adb install -r myfinance-<version>.apk`, see README).
- [ ] Splash shows the MyFinance symbol on black; the launcher icon matches, and the themed (monochrome) icon works on Android 13+.
- [ ] Opens in the dark theme with five tabs: Overview, Cash Flow, Portfolio, History, Settings.

### 1. Setup
- [ ] *Settings → Opening balances*: set R$ 10.000,00 for today; Overview shows available cash R$ 10.000,00.
- [ ] Set a USD opening balance of US$ 500,00; Overview converts it at the PTAX shown at the bottom.
- [ ] Change number format to `1,234.56` and date format to `MM/DD/YYYY`: every screen follows. Switch back.
- [ ] With the phone in Portuguese, the app is in Portuguese; *Settings → Language* opens the system per-app language screen (Android 13+).

### 2. Cash flow
- [ ] Add a monthly expense "Rent", R$ 2.500,00, first due on the 31st of a month: the following February occurrence falls on the 28th/29th.
- [ ] Add a one-time expense already paid today ("Groceries", R$ 312,40): cash goes down immediately.
- [ ] Confirm the rent payment with R$ 2.550,00: the occurrence shows Paid with the real amount; next month still expects R$ 2.500,00.
- [ ] Confirm a receipt for a monthly "Salary": Received; projected end-of-month balance updates.
- [ ] Edit the rent amount to R$ 2.600,00 with the next due date next month: past and paid occurrences keep their values.
- [ ] Cancel an occurrence and restore it; mark a paid one as unpaid.
- [ ] Add a USD subscription (US$ 20,00) and confirm it paid in BRL (R$ 115,00).
- [ ] Charts show income vs. expenses, balance evolution and expenses by category.

### 3. History
- [ ] Search "rent" finds it; filters by type, status, category, currency and date range work; months group correctly.

### 4. Exchange rates
- [ ] With internet, *Update* fetches today's PTAX (or the last business day before ~13:00) and shows its date.
- [ ] In airplane mode the app keeps working with the last rate; a manual rate can be entered and is used.
- [ ] Turning off *Fetch exchange rates* stops automatic requests.

### 5. Portfolio
- [ ] Create "Tesouro Selic" (Fixed Income, BRL) with R$ 5.000,00 invested; then update its value to R$ 5.120,00: gain R$ 120,00, return 2,40%.
- [ ] Create "VOO" (ETFs, USD) with US$ 1.000,00 and a later valuation of US$ 1.100,00: detail shows the gain in US$ and in R$ (including the currency effect).
- [ ] Add a contribution and a partial disposal; valuation history keeps earlier rows unchanged.
- [ ] Add a car (Vehicles, personal asset) with a photo; it appears under Personal assets.
- [ ] The donut shows the total in the centre; tapping a category filters the list. Period chips change the charts; no line starts before the first record.

### 6. Overview
- [ ] Net worth = cash + investments + personal assets, with the composition shown; the R$/US$ toggle converts everything and shows the rate date.
- [ ] Upcoming commitments can be confirmed from the Overview.

### 7. Backup
- [ ] *Settings → Backup*: set the recovery password and store it in a password manager.
- [ ] Choose a folder synced by a sync app. "Back up now" creates `myfinance-backup-YYYYMMDD-HHmmss.myfinance`.
- [ ] Edit something and wait ~10 min (or until the next day): a new file appears; at most 7 are kept.
- [ ] *Restore on a clean install*: uninstall, reinstall, restore from the folder or a file, confirm the date, type the password: transactions, portfolio, images, rates and settings come back.
- [ ] A wrong password shows "Wrong password" and nothing changes.
- [ ] Delete the chosen folder and tap "Back up now": the status shows the error and a notification arrives.

### 8. Export
- [ ] *Export CSV* warns that the file is not encrypted and writes a zip that opens in a spreadsheet with correct dates, amounts and currencies.

### 9. Update
- [ ] Installing a newer version over the old one keeps all data.

## Known limitations
- The day's PTAX is published around 13:00 BRT on business days; before that the previous business day's rate is used.
- The folder picker may not list Google Drive directly; use a local folder kept in sync by a sync app.
