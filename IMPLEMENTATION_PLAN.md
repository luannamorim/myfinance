# MyFinance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (native execution in this session), delegating self-contained tasks to subagents when useful. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a native, private, offline-first Android app for cash flow (recurring income and expenses with confirmation and projection), a manually valued portfolio of investments and personal assets, and net worth, in BRL and USD, with encrypted backups to a user-chosen folder.

**Architecture:** a single `:app` module, MVVM, layers by package (`ui/` → `domain/` ← `data/`, plus `platform/`), copied from MyFit. Room is the source of truth; every financial calculation is pure Kotlin in `domain/` operating on `Money` (Long minor units + currency) and `LocalDate`; exchange rates come from the Banco Central PTAX API with manual fallback.

**Stack:** Kotlin 2.4.21 (AGP 9.5.1 built-in Kotlin), Compose BOM 2026.09.00, Material 3 + material3-adaptive 1.3.0 + navigation-suite, Navigation Compose 2.10.2 (type-safe), Hilt 2.60.1, Room 2.8.5 (KSP 2.3.13), DataStore 1.3.0, WorkManager 2.12.0, Vico 3.3.1 (`compose-m3`), Tink 1.24.0, kotlinx-serialization 1.13.0, core-splashscreen, documentfile 1.1.0, exifinterface 1.4.2. Tests: JUnit4, kotlinx-coroutines-test, Robolectric 4.17, Roborazzi 1.79.0, work-testing, navigation-testing, Hilt testing.

**Spec:** [`SPEC.md`](SPEC.md) · rules and schema: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · reference app: `~/my/myfit`.

## Global constraints

- Package/applicationId `io.github.luannamorim.myfinance`; debug `applicationIdSuffix = ".debug"`. minSdk 26, compileSdk/targetSdk 37, JDK 21 toolchain (Java/Kotlin target 17 as MyFit), Gradle 9.9.1.
- AGP 9: do **not** apply `org.jetbrains.kotlin.android`; apply `org.jetbrains.kotlin.plugin.compose` and `.serialization` 2.4.21; Kotlin pinned via root `buildscript` classpath (as MyFit).
- Money is `Long` minor units + `Currency`; never `Double`. Conversions/percentages in `BigDecimal`, `HALF_EVEN`, rounded once per total.
- No financial calculation in composables or ViewModels beyond calling `domain/` functions.
- UI in English (`res/values`) with Brazilian Portuguese (`res/values-pt-rBR`); every user-visible string in resources. Number format default `1.234,56`, date format default `DD/MM/YYYY`; symbols `R$`, `US$`.
- Network: only `https://api.bcb.gov.br`, cleartext disabled, no identifiers sent; app fully usable offline.
- Never log amounts, names, notes or categories. No analytics.
- Dark theme default; green is the only accent; colour rules in `docs/BRAND.md`.
- Backup: password required before the first backup; keeps 7; format version 1; magic `MYFINBAK`; extension `.myfinance`.
- Environment for every Gradle call: `export JAVA_HOME=~/.local/share/jdks/jdk-21.0.13+9 ANDROID_HOME=~/Android/Sdk`. Tests run with the `--add-exports/--add-opens` flags from MyFit's `app/build.gradle.kts`.
- Compose tests use `androidx.compose.ui.test.junit4.v2.*`.
- Standard verification for every task: `./gradlew testDebugUnitTest` green. End of each stage: `./gradlew lintDebug testDebugUnitTest recordRoborazziDebug`, review PNGs in `app/build/outputs/roborazzi/`, push.
- Conventional Commits in English, **without** a `Co-Authored-By` trailer; push to `origin main` at the end of each stage.
- From the first release on, every Room schema change requires a `Migration` and a `MigrationTest` case.

## Review focus

1. **Month-end and leap-year recurrence** (anchor 31 Jan monthly; 29 Feb yearly) → 28/29 Feb then 31 Mar; 28 Feb in common years. Test in 3.1.
2. **Editing a recurrence after some months were paid** → paid, cancelled, overdue and individually edited occurrences keep their values; no duplicate for the current month. Test in 3.2.
3. **Day boundary in São Paulo** (23:30 local = 02:30 UTC next day) → "today" and Overdue use the local date. Test in 3.3.
4. **Missing exchange rate** (USD record dated before any stored rate, offline) → total flagged incomplete, never converted with an invented rate. Test in 4.1.
5. **Contribution after the last valuation** → value = last valuation + net flows (carry-forward), gain unchanged by the contribution itself. Test in 4.2.

---

## File structure

Code root: `app/src/main/java/io/github/luannamorim/myfinance/` (below, `…/`). Tests mirror it in `app/src/test/java/io/github/luannamorim/myfinance/`.

```
…/MyFinanceApp.kt                 Application (@HiltAndroidApp, Configuration.Provider), startup tasks
…/MainActivity.kt                 single Activity, splash, edge-to-edge, theme + NavHost
…/di/                             Database, Data, Dispatchers, Time, Network modules
…/core/time/                      TimeSource, SystemTimeSource
…/core/format/                    MoneyFormatter, AmountInput, DateFormats, Formats (CompositionLocal), SearchText
…/domain/model/                   Currency, Money, enums, Settings, pure models
…/domain/cash/                    RecurrenceRule, Recurrence, OccurrenceStatus, CashCalculator, MonthSummary
…/domain/fx/                      ExchangeRates, Converter, PtaxParser
…/domain/portfolio/               PortfolioCalculator, Allocation, Period, Series
…/domain/networth/                NetWorthCalculator
…/domain/backup/                  BackupDataV1, BackupCrypto, BackupArchive, Retention
…/data/db/                        MyFinanceDatabase, Migrations, entity/, dao/, seed/CategorySeed.kt
…/data/repository/                Category, Cash, Portfolio, ExchangeRate, Export repositories + mappers, BackupTrigger
…/data/rates/                     BcbRateClient
…/data/prefs/                     SettingsRepository, BackupStateStore
…/data/images/                    ImageStore
…/data/backup/                    BackupManager, BackupKeyStore, KeyWrapper, BackupDestination, FolderBackupWriter, SnapshotMapper
…/platform/                       backup/BackupWork.kt, rates/RateRefreshWorker.kt
…/ui/theme/                       Color, Type, Shape, Spacing, Theme
…/ui/components/                  design system + FinanceMark, DonutChart, LineChart/ColumnChart wrappers
…/ui/common/                      Labels (enum → string res), CategoryIcons, ConfirmSheet
…/ui/navigation/                  Routes, TopLevelDestination, FinanceNavHost, AppViewModel
…/ui/feature/{overview,cashflow,transaction,history,portfolio,asset,settings,backup}/   Screen + ViewModel
app/src/main/res/                 font/ (Geist), drawable/ (launcher, monochrome, stat), values/ + values-pt-rBR/, xml/
app/schemas/                      exported Room schemas
docs/                             ARCHITECTURE, BRAND, SECURITY, TESTING, BACKUP, screenshots/, licenses/
.github/workflows/ci.yml          build + tests on push/PR
```

---

## Stage 1 — Specification and repository

### Task 1.1: Documents and public repository

**Files:** `SPEC.md`, `IMPLEMENTATION_PLAN.md`, `docs/{ARCHITECTURE,BRAND,SECURITY,TESTING,BACKUP}.md`, `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `LICENSE`, `.gitignore`, `.gitattributes`, `docs/licenses/GEIST_OFL.txt`

- [ ] Write the documents (English). README skeleton: name, one-liner, "in development", links.
- [ ] Fresh-eyes review by a subagent of SPEC + ARCHITECTURE + this plan for contradictions (rules, names, signatures); fix findings.
- [ ] Commit `docs: add spec, architecture, security, backup and testing docs` and `docs: add implementation plan`.
- [ ] `gh repo view luannamorim/myfinance` → not found; `gh repo create myfinance --public --source . --remote origin --push` inside `~/my/myfinance`. Verify `git remote -v` = `git@github.com:luannamorim/myfinance.git`, visibility `PUBLIC`.

---

## Stage 2 — Foundation and identity

### Task 2.1: Gradle skeleton, empty app and CI

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew*`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `…/MyFinanceApp.kt`, `…/MainActivity.kt`, `res/xml/{data_extraction_rules,backup_rules,network_security_config}.xml`, `res/values/{strings,themes,colors}.xml`, `res/values-pt-rBR/strings.xml`, `app/src/main/res/resources.properties`, `.github/workflows/ci.yml`, Test: `…/AppLaunchTest.kt`, `app/src/test/resources/robolectric.properties`

**Produces:** `MyFinanceApp : Application, Configuration.Provider` (`@HiltAndroidApp`, `HiltWorkerFactory`); `MainActivity : ComponentActivity` (`@AndroidEntryPoint`).

- [ ] Copy MyFit's catalog/build files, rename, drop `reorderable`, `lifecycle-service`, `coil`; add `androidx.core:core-splashscreen`. Signing reads `MYFINANCE_STORE_FILE/_STORE_PASSWORD/_KEY_ALIAS/_KEY_PASSWORD`. `androidResources { generateLocaleConfig = true }` with `resources.properties` `unqualifiedResLocale=en`.
- [ ] Proguard: `-assumenosideeffects class android.util.Log` with `public static *** v(...);` and the same for `d`, `i`, `w`, `e`, `wtf`, `println`.
- [ ] Manifest: declares only `INTERNET` and `POST_NOTIFICATIONS`; `allowBackup=false`, extraction rules excluding everything, `networkSecurityConfig` (cleartext off); WorkManager initializer removed. Expected merged list (checked at release): those two + `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (WorkManager) + `<appId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX).
- [ ] `robolectric.properties`: `application=android.app.Application`, `qualifiers=en`.
- [ ] `AppLaunchTest` (Hilt + Robolectric): `ActivityScenario.launch(MainActivity)` reaches RESUMED.
- [ ] CI: `actions/checkout@v4`, `actions/setup-java@v4` (temurin 21), `android-actions/setup-android@v3` + `sdkmanager "platforms;android-37" "build-tools;37.0.0"`, `gradle/actions/setup-gradle@v4`; run `./gradlew testDebugUnitTest lintDebug assembleDebug`; upload `app/build/reports` on failure.
- [ ] `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL. Commit `build: scaffold android project with compose, hilt, room and ci`.

### Task 2.2: Theme

**Files:** `…/ui/theme/{Color,Type,Shape,Spacing,Theme}.kt`, `res/font/geist_*.ttf`, Test: `…/ui/theme/ColorContrastTest.kt`, `ThemeScreenshotTest.kt`

**Produces:** `@Immutable data class FinanceColors(background, surface, surfaceRaised, outline, outlineStrong, textPrimary, textSecondary, textTertiary, accent, onAccent, accentMuted, danger, warning, isDark)` with `positive`/`negative` getters and `chart: List<Color>` (6 neutrals); `DarkColors`, `LightColors`, `LocalFinanceColors`; `FinanceType` (MyFit styles; `numericHero` = 40sp SemiBold tnum); `object FinanceTheme { colors; type }`; `@Composable fun FinanceTheme(mode: ThemeMode = ThemeMode.DARK, content)`; `FinanceShapes`, `Spacing` (MyFit values).

- [ ] Port MyFit's theme files and fonts; `ColorContrastTest` with MyFit thresholds plus `danger` vs background ≥ 3.0 and each `chart` colour vs surface ≥ 3.0.
- [ ] `ThemeScreenshotTest` (both themes). Run tests + `recordRoborazziDebug`; review. Commit `feat(ui): add theme with color, type and shape tokens`.

### Task 2.3: Money and formatting

**Files:** `…/domain/model/{Currency,Money}.kt`, `…/core/format/{MoneyFormatter,AmountInput,DateFormats,Formats,SearchText}.kt`, `…/domain/model/Settings.kt`, Test: `…/core/format/{MoneyFormatterTest,AmountInputTest,DateFormatsTest}.kt`, `…/domain/model/MoneyTest.kt`

**Produces:**
- `enum class Currency(val code: String, val symbol: String, val fractionDigits: Int) { BRL("BRL","R$",2), USD("USD","US$",2) }`
- `data class Money(val minor: Long, val currency: Currency)`: `plus/minus` (require same currency), `unaryMinus`, `isNegative`, `isZero`, `toBigDecimal()`, `companion zero(c)`, `of(BigDecimal, c)` (HALF_EVEN).
- `enum class NumberFormatStyle { COMMA_DECIMAL, DOT_DECIMAL }`, `enum class DateFormatStyle { DMY, MDY }`, `enum class ThemeMode { DARK, LIGHT, SYSTEM }`, `@Serializable data class AppSettings(themeMode = DARK, numberFormat = COMMA_DECIMAL, dateFormat = DMY, defaultCurrency = BRL, displayCurrency = BRL, autoFetchRates = true, autoBackup = true)`.
- `class MoneyFormatter(style: NumberFormatStyle)`: `format(m: Money, signed: Boolean = false): String`, `formatNumber(minor: Long, c: Currency): String`, `formatPercent(ratio: BigDecimal): String` (2 decimals), `formatRate(rate: BigDecimal): String` (4 decimals).
- `object AmountInput { fun minorOf(digits: String): Long?; fun appendDigit(digits: String, d: Char): String; fun backspace(digits: String): String }` (cash-register entry; max 13 digits).
- `class DateFormats(style: DateFormatStyle, locale: Locale)`: `date(d)` ("12/11/2026"), `dayMonth(d)` ("12/11"), `monthYear(ym)` ("November 2026", localized, capitalized), `monthShort(ym)` ("Nov"), `relativeDue(d, today)` handled in UI strings.
- `data class Formats(val money: MoneyFormatter, val dates: DateFormats)`; `val LocalFormats = staticCompositionLocalOf { Formats(MoneyFormatter(COMMA_DECIMAL), DateFormats(DMY, Locale.US)) }`.
- `fun String.normalizedForSearch(): String` (port).

- [ ] Tests (hand values): `format(Money(123456, BRL))` = `"R$ 1.234,56"` (COMMA) and `"R$ 1,234.56"` (DOT); `Money(-50, USD)` → `"−US$ 0,50"`; signed `+R$ 10,00`; `Money(0,BRL)` → `"R$ 0,00"`; `formatPercent(BigDecimal("0.024"))` → `"2,40%"`; `formatRate(5.6143)` → `"5,6143"`; `minorOf("")` = 0, `minorOf("31240")` = 31240, 14 digits rejected; `Money.of(BigDecimal("1.005"), BRL).minor` = 100 (HALF_EVEN) and `"1.015"` → 102; `plus` across currencies throws; `DateFormats(DMY).date(2026-11-12)` = `"12/11/2026"`, MDY = `"11/12/2026"`.
- [ ] Implement; tests pass. Commit `feat(core): add money type, currency formatting and amount input`.

### Task 2.4: Brand mark, icons, splash and components

**Files:** `…/ui/components/{FinanceMark,Buttons,Chrome,Controls,DateSelector,Inputs,Surfaces,TabScaffold,AmountDisplay,CurrencyInput,MonthSwitcher}.kt`, `res/drawable/{ic_launcher_foreground,ic_launcher_monochrome,ic_stat_myfinance,ic_splash}.xml`, `res/mipmap-anydpi/ic_launcher{,_round}.xml`, `res/values/ic_launcher_background.xml`, `res/values/themes.xml` (splash), `docs/BRAND.md`, Test: `…/ui/components/{ComponentCatalogScreenshotTest,MarkVariantsScreenshotTest}.kt`

**Produces:**
- `internal object MarkGeometry { stroke: List<List<Offset>>; dot: Offset; DOT_RADIUS = 2.3f; STROKE = 2.6f }`; `@Composable fun FinanceMark(modifier, size: Dp = 32.dp, lineColor, dotColor)`; `FinanceWordmark(modifier, fontSize = 22.sp, showMark = true)`.
- Ports with renamed prefixes: `FinanceCard`, `PrimaryButton`, `SecondaryButton`, `TextAction`, `FinanceTopBar`, `ConfirmDialog`, `NumberStepper`, `<T> FilterChipRow`, `FinanceChip`, `DateSelector(label, date, onDate, modifier, maxDate)` (= MyFit `DateField`), `FinanceTextField`, `SectionHeader`, `StatTile`, `EmptyState`, `TabHeader`, `TabScreen`, `ContentMaxWidth = 640.dp`.
- `@Composable fun AmountDisplay(money: Money, modifier, style = numeric, signed = false, tone: AmountTone = NEUTRAL)` with `enum class AmountTone { NEUTRAL, POSITIVE, NEGATIVE, AUTO }` (AUTO: green ≥ 0, red < 0), content description = spoken amount.
- `@Composable fun CurrencyInput(digits: String, currency: Currency, onDigits: (String) -> Unit, label: String, modifier, onCurrency: ((Currency) -> Unit)? = null, isError = false)` — numeric keyboard, cash-register entry via `AmountInput`, optional R$/US$ toggle.
- `@Composable fun MonthSwitcher(month: YearMonth, onMonth: (YearMonth) -> Unit, modifier)` — ‹ month › with 48 dp targets.

- [ ] `MarkVariantsScreenshotTest`: variants A (stem (6,20)→(6,9.5)→(14,6), middle (6,14.5)→(11.5,14.5)), B (both arms rising in parallel), C (straight F, dot after top arm) at 16/24/48/108 dp, circle + squircle, both themes, next to MyFit's mark geometry. Choose by legibility at 16 dp; keep the chosen numbers in `MarkGeometry`, update `BRAND.md` (Symbol section) with the final coordinates; delete the variants test.
- [ ] Launcher vectors: 108 viewport, group scale 2.25 centred on the ink bounds (as MyFit), background `#0B0B0C`, line `#F5F5F4`, dot `#47F55E`; monochrome all white; `ic_stat_myfinance` 24 dp.
- [ ] Splash: `Theme.MyFinance.Starting` (parent `Theme.SplashScreen`), `windowSplashScreenBackground #0B0B0C`, `windowSplashScreenAnimatedIcon @drawable/ic_splash`, `postSplashScreenTheme @style/Theme.MyFinance`; `installSplashScreen()` in `MainActivity`.
- [ ] `ComponentCatalogScreenshotTest`: every component in both themes + launcher icon preview (circle/squircle) + splash composition.
- [ ] Run tests + `recordRoborazziDebug`; review the PNGs (mark legible at 24 dp). Commit `feat(ui): add brand mark, launcher icon, splash and base components`.

### Task 2.5: Database v1, seeds and DI

**Files:** `…/data/db/{MyFinanceDatabase,Migrations}.kt`, `…/data/db/entity/{CategoryEntities,CashEntities,PortfolioEntities,RateEntity}.kt`, `…/data/db/dao/{CategoryDao,CashDao,PortfolioDao,RateDao,BackupDao}.kt`, `…/data/db/seed/CategorySeed.kt`, `…/domain/model/{Enums,Models}.kt`, `…/core/time/TimeSource.kt`, `…/di/{DatabaseModule,DispatchersModule,TimeModule}.kt`, `app/schemas/…/1.json`, Test: `…/data/db/{CategoryDaoTest,CashDaoTest,PortfolioDaoTest,RateDaoTest}.kt`, `…/data/db/seed/CategorySeedTest.kt`, `app/src/test/resources/seed-keys.txt`, `…/testutil/{TestDb,FakeTimeSource,Coroutines}.kt`

**Produces:**
- Enums: `EntryKind { INCOME, EXPENSE }`, `RecurrenceUnit { DAY, WEEK, MONTH, YEAR }`, `OccurrenceState { PENDING, CONFIRMED, CANCELLED }`, `AdjustmentKind { OPENING, ADJUSTMENT }`, `AssetGroup { INVESTMENT, PERSONAL }`, `FlowKind(val inflow: Boolean) { INITIAL(true), CONTRIBUTION(true), WITHDRAWAL(false), PARTIAL_DISPOSAL(false), FULL_DISPOSAL(false) }`, `RateSource { BCB, MANUAL }`.
- Domain models (`LocalDate`, `Money`): `Category`, `TransactionTemplate`, `Occurrence`, `CashAdjustment`, `AssetCategory`, `Asset`, `AssetFlow`, `Valuation`, `ExchangeRate(date, rate: BigDecimal, source)` — fields as the tables in ARCHITECTURE.md (flows and valuations carry `Money` in the asset currency; `Asset.closed` is derived later, not stored). `data class CashLedger(templates, occurrences, adjustments)` in `domain/cash`.
- `fun interface BackupTrigger { fun onDataChanged(); fun cancelPending() {} }` in `data/repository`, bound to a no-op in `DataModule` until 6.2 replaces it with `BackupScheduler`.
- Entities and tables exactly as ARCHITECTURE.md; `MyFinanceDatabase.VERSION = 1`, `NAME = "myfinance.db"`.
- `interface TimeSource { fun nowMs(): Long; fun zone(): ZoneId }`, `fun TimeSource.today(): LocalDate`; `SystemTimeSource`; test `FakeTimeSource(var nowMs, var zoneId = America/Sao_Paulo)`.
- `CategorySeed`: `items` (21 cash + 12 asset categories with `seedKey` like `expense.rent`, `asset.etfs`, icon key), `Callback : RoomDatabase.Callback` inserting on create; `defaultName(seedKey): String` (English).
- DAOs: CRUD + `Flow` observers + `getAll*()` for snapshot; `CashDao.insertOccurrencesIgnore(list): List<Long>`; `CashDao.deleteReplaceable(templateId, fromEpochDay)` (PENDING, not customized, dueDate ≥ from); `RateDao.insertIgnore`, `upsertManual`, `latestBcbDate()`; `CategoryDao.usageCount(id)`; `BackupDao.clear*/insert*`.

- [ ] Tests: seeds insert 21 categories and 12 asset categories with keys equal to `seed-keys.txt`; deleting a category used by an occurrence fails (RESTRICT); deleting an asset cascades flows and valuations; deleting a template sets `templateId` NULL; inserting two occurrences with the same (templateId, scheduledDate) keeps one; two standalone occurrences (NULL templateId) both stay; a BCB and a MANUAL rate coexist on the same date and `insertIgnore` of the same BCB date keeps the first.
- [ ] Implement; schema exported. Commit `feat(data): add room schema v1, category seeds and di`.

### Task 2.6: Settings and navigation shell

**Files:** `…/data/prefs/SettingsRepository.kt`, `…/di/DataModule.kt`, `…/ui/navigation/{Routes,TopLevelDestination,FinanceNavHost,AppViewModel}.kt`, `…/ui/feature/*/…Screen.kt` (tab roots), Test: `…/data/prefs/SettingsRepositoryTest.kt`, `…/ui/navigation/NavigationTest.kt`

**Produces:**
- `SettingsRepository(@Named("settings") store)`: `settings: Flow<AppSettings>`, `suspend fun update(transform: (AppSettings) -> AppSettings)`, `exportJson(): String`, `importJson(json)`.
- `@Serializable` routes: `OverviewRoute`, `CashFlowRoute`, `PortfolioRoute`, `HistoryRoute`, `SettingsRoute` (objects); `TransactionEditorRoute(templateId: Long = 0, occurrenceId: Long = 0, kind: EntryKind = EXPENSE)`, `OccurrenceDetailRoute(id: Long)`, `RecurringListRoute`, `CashFlowChartsRoute`, `CategoriesRoute(scope: CategoryScope)`, `AssetEditorRoute(assetId: Long = 0)`, `AssetDetailRoute(assetId: Long)`, `FlowEditorRoute(assetId: Long, kind: FlowKind)`, `ValuationEditorRoute(assetId: Long)`, `PortfolioChartsRoute`, `OpeningBalancesRoute`, `AdjustBalanceRoute`, `CurrencySettingsRoute`, `BackupRoute`, `PrivacyRoute`, `AboutRoute`; `enum class CategoryScope { INCOME, EXPENSE, ASSET }`.
- `enum class TopLevelDestination` in order Overview, Cash Flow, Portfolio, History, Settings (icons: SpaceDashboard, SwapVert, DonutLarge, ReceiptLong, Settings — outlined/rounded).
- `FinanceNavHost` (port of MyFit's: `NavigationSuiteScaffold`, hidden off top-level, 200 ms fades, `navigateToTopLevel`); `MainActivity` provides `LocalFormats` from settings and applies the theme.
- Tab roots render real empty states (replaced by their features in later stages).

- [ ] Tests: settings round trip and defaults (`COMMA_DECIMAL`, `DMY`, BRL/BRL, DARK, autoFetchRates true); NavigationTest: each tab shows its heading; back from Portfolio returns to Overview; rail on `w840dp`.
- [ ] Stage 2 verification + push. Commit `feat(ui): add settings repository and adaptive navigation shell`.

---

## Stage 3 — Cash flow

### Task 3.1: Recurrence engine (pure)

**Files:** `…/domain/cash/{RecurrenceRule,Recurrence}.kt`, Test: `…/domain/cash/RecurrenceTest.kt`

**Produces:** `data class RecurrenceRule(val unit: RecurrenceUnit, val interval: Int)` (`init { require(interval in 1..99) }`) with `companion` presets `DAILY, WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL` and `presetOrNull()`; `object Recurrence { const val HORIZON_MONTHS = 12; fun dateAt(anchor: LocalDate, rule: RecurrenceRule, n: Int): LocalDate; fun datesBetween(anchor: LocalDate, rule: RecurrenceRule, from: LocalDate, to: LocalDate, endDate: LocalDate?): List<LocalDate>; fun firstOnOrAfter(anchor: LocalDate, rule: RecurrenceRule, date: LocalDate): LocalDate; fun horizonEnd(today: LocalDate): LocalDate }`.

- [ ] Tests (hand tables): monthly from 2026-01-31 → 01-31, 02-28, 03-31, 04-30, 05-31; from 2028-01-31 → 2028-02-29; yearly from 2024-02-29 → 2025-02-28, 2026-02-28, 2027-02-28, 2028-02-29; weekly from 2026-11-12 (Thu) → 11-19, 11-26; biweekly → 11-26, 12-10; quarterly from 2026-11-30 → 2027-02-28, 2027-05-30; semiannual from 2026-08-31 → 2027-02-28, 2027-08-31; daily across month end; custom every 3 weeks; `datesBetween` respects `from`, `to` and `endDate` (inclusive) and returns nothing when `from > endDate`; `horizonEnd(2026-11-12)` = 2027-11-30; `interval = 0` throws; `firstOnOrAfter(anchor 2026-01-31 monthly, 2026-03-01)` = 2026-03-31.
- [ ] Implement (always from the anchor; `datesBetween` estimates the first index from the unit distance then walks forward). Commit `feat(cash): add recurrence engine`.

### Task 3.2: Occurrence generation and template edits

**Files:** `…/data/repository/{CashRepository,CashMappers}.kt`, `…/data/repository/BackupTrigger.kt`, Test: `…/data/repository/CashRepositoryTest.kt`

**Interfaces:**
- Consumes: `Recurrence`, DAOs (2.5), `TimeSource`.
- Produces: `data class TransactionDraft(name: String, categoryId: Long, kind: EntryKind, amount: Money, date: LocalDate, rule: RecurrenceRule?, endDate: LocalDate?, description: String?, alreadySettled: Boolean /* one-time only */)`; `CashRepository`:
  - `val ledger: Flow<CashLedger>`
  - `suspend fun create(draft): Long` (one-time → standalone occurrence, confirmed with actual = expected on `date` when `alreadySettled` and `date ≤ today`; recurring → template with `startDate = anchorDate = date`, generation from `max(date, first day of current month)`)
  - `suspend fun updateTemplate(id, draft, nextDueDate: LocalDate?)` (rules in ARCHITECTURE.md §Recurrence: non-structural vs structural edit, anchor kept unless `nextDueDate` changed, end-date trimming), `setTemplateActive(id, active)`, `deleteTemplate(id)`
  - `suspend fun ensureGenerated()` (mutex; each active template over `[generatedThrough + 1, horizonEnd(today)]`)
  - `suspend fun confirm(id, actual: Money, date: LocalDate, note: String?)`, `unconfirm(id)`, `cancel(id)`, `restore(id)`, `editOccurrence(id, name, amount: Money, dueDate)` (sets `customized`), `updateStandalone(id, draft)`, `deleteOccurrence(id)` (standalone only)
  - `suspend fun setOpening(amount: Money, date)` (refused when later than an existing adjustment), `addAdjustment(target: Money /*desired balance*/, date, note)` (delta = target − balance(date); date in [opening, today]), `deleteAdjustment(id)`

- [ ] Tests (in-memory Room + FakeTimeSource):
  - Today 2026-11-12, monthly R$ 2.500 with first due 2026-11-05 → 2026-11-05 … 2027-11-05 (13 rows); `ensureGenerated()` twice → still 13; first due 2026-09-05 → generation starts 2026-11-05 (no Sep/Oct backfill).
  - Template "Rent" anchored 2026-10-05 created with today = 2026-10-01; advance to 2026-11-12; confirm Nov-05 with R$ 2.500; Oct-05 left unpaid (overdue). Edit amount to R$ 2.600: Oct (overdue) and Nov (confirmed) unchanged, Dec-05 onward = 2.600.
  - Same template with Dec-05 moved to Dec-07 (customized): an amount-only edit keeps Dec-07 at its value; a structural edit (next due 2026-12-10) replaces it and generates Dec-10, Jan-10…; only one December row exists.
  - Anchor 2026-01-31 monthly, today 2026-02-10, amount-only edit → next rows 2026-02-28, 2026-03-31 (anchor kept).
  - End date moved to 2027-03-31 removes every PENDING row after it, customized included.
  - Deactivate removes pending rows due ≥ today (Nov-12 row included); reactivate on 2027-01-10 generates from 2027-01-10 on, none in between.
  - Delete template keeps confirmed, cancelled and overdue rows with `templateId == null`.
  - `confirm` rejects `date > today` and keeps `expectedAmount`; `unconfirm` clears actual fields; one-time `alreadySettled` is CONFIRMED with actual = expected; `deleteOccurrence` on a generated row throws.
  - Second `setOpening(BRL)` replaces the first; `addAdjustment(target R$ 9.000)` dated today with balance R$ 10.000 stores −1.000; adjustment before the opening date or in the future is rejected; moving the opening after an adjustment is refused.
  - Every write calls `BackupTrigger.onDataChanged()`.
- [ ] Implement. Commit `feat(cash): generate occurrences idempotently and apply template edits`.

### Task 3.3: Statuses and cash calculations (pure)

**Files:** `…/domain/cash/{OccurrenceStatus,CashCalculator,MonthSummary}.kt`, `…/domain/fx/{ExchangeRates,Converter}.kt` (minimal: identity + rate lookup), Test: `…/domain/cash/{OccurrenceStatusTest,CashCalculatorTest}.kt`, `…/domain/fx/ConverterTest.kt`

**Produces:**
- `enum class DisplayStatus { UPCOMING, PENDING, OVERDUE, PAID, RECEIVED, CANCELLED }`; `object OccurrenceStatus { const val PENDING_WINDOW_DAYS = 7; fun of(o: Occurrence, today: LocalDate): DisplayStatus }`.
- `class ExchangeRates(rates: List<ExchangeRate>)`: `rateOn(date): ExchangeRate?` (latest ≤ date, MANUAL wins on ties), `latest(): ExchangeRate?`.
- `class Converter(val rates: ExchangeRates)`: `convert(m: Money, to: Currency, on: LocalDate?): Money?` (null `on` = latest), `exact(m, to, on): BigDecimal?` (scale 10, HALF_EVEN); `fun sum(items: List<Pair<Money, LocalDate?>>, to: Currency): Total` with `data class Total(val money: Money, val incomplete: Boolean)` — `money` is the sum of the convertible items, rounded once.
- `object CashCalculator`: `balance(c: Currency, at: LocalDate, ledger: CashLedger): Money`; `available(ledger, today): Map<Currency, Money>`; `projected(ledger, today, through: LocalDate): Map<Currency, Money>`; `monthSummary(ledger, month: YearMonth, today, to: Currency, converter): MonthSummary`; `balanceHistory(ledger, months: List<YearMonth>, to, converter): List<Pair<YearMonth, Total>>`; `byCategory(ledger, month, kind, to, converter): List<Pair<Long, Total>>`.
- `data class MonthSummary(expectedIncome: Total, receivedIncome: Total, expectedExpenses: Total, paidExpenses: Total, overdueEarlier: Total, overdueEarlierCount: Int)`; `overdueEarlier` = PENDING expenses with `dueDate < min(month.start, today)`.

- [ ] Tests: statuses at 2026-11-12 for due 11-11 (OVERDUE), 11-12 and 11-19 (PENDING), 11-20 (UPCOMING); confirmed income RECEIVED, expense PAID; `FakeTimeSource` at 2026-11-13T02:30Z with zone São Paulo → today = 2026-11-12 (and = 11-13 with UTC). Balance fixture (hand-computed, BRL): opening 10.000,00 on 11-01; salary confirmed 9.000,00 on 11-05; rent expected 2.500,00 confirmed 2.550,00 on 11-10; groceries 312,40 confirmed on 11-12; electricity pending 250,00 due 11-20; internet overdue 120,00 due 10-25; old expense confirmed 10-30 (before opening, ignored) → `balance(today)` = 16.137,60; `projected(through 11-30)` = 15.767,60; Nov summary: expected income 9.000,00, received 9.000,00, expected expenses 3.112,40 (2.550,00 + 312,40 + 250,00), paid 2.862,40, overdue earlier 120,00 (1); cancelled rows count nowhere. Converter: US$ 100,00 on Saturday 2026-11-14 with BCB rates Fri 5,6143 → R$ 561,43; BRL→USD R$ 561,43 → US$ 100,00; manual 5,7000 on Fri wins; date before first rate → null and `Total.incomplete = true`.
- [ ] Implement. Commit `feat(cash): add statuses, balances, projections and month summaries`.

### Task 3.4: Category management

**Files:** `…/data/repository/CategoryRepository.kt`, `…/ui/common/{Labels,CategoryIcons}.kt`, `…/ui/feature/settings/{CategoriesScreen,CategoriesViewModel,CategoryEditorSheet}.kt`, Test: `…/data/repository/CategoryRepositoryTest.kt`, `…/ui/feature/settings/CategoriesScreenshotTest.kt`

**Produces:** `CategoryRepository`: `observe(kind: EntryKind, includeArchived: Boolean = false): Flow<List<Category>>`, `observeAll()`, `observeAssetCategories()`, `create(kind|group, name, icon)`, `rename(id, name)`, `setIcon(id, icon)`, `setArchived(id, archived)`, `delete(id): DeleteResult` (`enum DeleteResult { DELETED, IN_USE }`) and asset-category equivalents; `@Composable fun Category.displayName(): String` (seed string or user name); `object CategoryIcons { val all: Map<String, ImageVector> }` (~40 Material Rounded icons).

- [ ] Tests: delete unused custom → DELETED; delete used → IN_USE and row intact; archived hidden from `observe(kind, includeArchived = false)`; seeded name null → `displayName` = localized string (en and pt-BR).
- [ ] Screen: segmented Income/Expense/Assets, list with icon + name, add/edit sheet (name + icon grid), archive/delete with `ConfirmDialog`. Screenshots both themes. Commit `feat(cash): add category management`.

### Task 3.5: Transaction editor and recurring list

**Files:** `…/ui/feature/transaction/{TransactionEditorScreen,TransactionEditorViewModel,RepeatPicker}.kt`, `…/ui/feature/cashflow/{RecurringListScreen,RecurringListViewModel}.kt`, Test: `…/ui/feature/transaction/{TransactionEditorViewModelTest,TransactionFlowTest,TransactionScreenshotTest}.kt`

**Produces:** `TransactionEditorUiState(loading, kind, name, digits, currency, categoryId, date, repeat: RepeatChoice, customInterval, customUnit, endDate, description, alreadySettled, isRecurring, editingTemplate, nameError, amountError, categoryError, done)`; `sealed interface RepeatChoice { Never; Preset(rule); Custom }`; `fun save(): Boolean`.

- [ ] Tests: blank name / zero amount / no category → errors, nothing saved; one-time expense today with "Already paid" creates a CONFIRMED occurrence; the toggle is hidden for recurring entries; monthly income creates a template and 13 occurrences; editing a template shows "Next due date" = first replaceable occurrence (due ≥ today), rejects a past date, passes `nextDueDate` only when changed; editing a standalone occurrence calls `updateStandalone`; flow test "Create an expense" and "Create an income transaction" through the UI by text.
- [ ] Recurring list: active/inactive sections, next due date, amount, toggle active, open editor. Screenshots. Commit `feat(cash): add transaction editor and recurring list`.

### Task 3.6: Confirm, detail, cancel and undo

**Files:** `…/ui/common/ConfirmSheet.kt`, `…/ui/feature/transaction/{OccurrenceDetailScreen,OccurrenceDetailViewModel}.kt`, Test: `…/ui/feature/transaction/{OccurrenceDetailViewModelTest,ConfirmFlowTest,OccurrenceScreenshotTest}.kt`

**Produces:** `@Composable fun ConfirmSheet(occurrence: Occurrence, today: LocalDate, onConfirm: (Money, LocalDate, String?) -> Unit, onDismiss: () -> Unit)` — title "Confirm payment" (expense) / "Confirm receipt" (income), amount prefilled with expected, currency toggle, date (max today), note.

- [ ] Tests: confirm with a different amount stores actual separately (expected unchanged); currency switch stores `actualCurrency`; "Mark as unpaid" requires dialog and resets; cancel/restore; edit single occurrence sets `customized`; delete only for one-time; flow tests "Confirm payment" and "Confirm receipt".
- [ ] Commit `feat(cash): confirm payments and receipts, edit, cancel and undo occurrences`.

### Task 3.7: Opening balances and balance adjustment

**Files:** `…/ui/feature/settings/{OpeningBalancesScreen,AdjustBalanceScreen,BalanceViewModel}.kt`, Test: `…/ui/feature/settings/BalanceViewModelTest.kt`

- [ ] Opening balance per currency (amount with sign toggle, date). Adjust balance: shows current balance per currency, user types the real balance, preview of the difference, note; history list of adjustments with delete (dialog).
- [ ] Tests: opening replaced not duplicated; adjustment = target − balance(at its date); dates before the opening or in the future rejected; negative balances allowed. Commit `feat(cash): add opening balances and balance adjustments`.

### Task 3.8: Cash Flow dashboard and charts

**Files:** `…/ui/feature/cashflow/{CashFlowScreen,CashFlowViewModel,CashFlowChartsScreen,CashFlowChartsViewModel}.kt`, `…/ui/components/{LineChart,ColumnChart,CategoryBars}.kt`, Test: `…/ui/feature/cashflow/{CashFlowViewModelTest,CashFlowScreenshotTest}.kt`

**Produces:** `data class OccurrenceRow(id: Long, name: String, categoryId: Long, kind: EntryKind, amount: Money /* actual if confirmed, else expected */, date: LocalDate /* actual or due */, status: DisplayStatus)` in `ui/common`; `CashFlowUiState(loading, month, summary: MonthSummary, available: Total, projected: Total, overdue, pending, upcomingIncome: List<OccurrenceRow>, nextMonth: MonthSummary, nextMonthProjected: Total /* projected through end of next month */, display: Currency, rateDate: LocalDate?, empty)`; charts: income vs expenses (last 6 months, columns), balance evolution (12 month ends + current), expenses and income by category for the month (bars, top 6 + Other).

- [ ] Tests: VM state equals `CashCalculator` results for the 3.3 fixture; month switch changes the lists; empty state when no data; chart models built from domain outputs only.
- [ ] Screenshots (data + empty, both themes). Commit `feat(cash): add cash flow dashboard and charts`.

### Task 3.9: History

**Files:** `…/ui/feature/history/{HistoryScreen,HistoryViewModel,HistoryFilters}.kt`, Test: `…/ui/feature/history/{HistoryViewModelTest,HistoryScreenshotTest}.kt`

**Produces:** `data class HistoryFilter(query: String = "", kinds: Set<EntryKind>, statuses: Set<DisplayStatus>, categoryIds: Set<Long>, currencies: Set<Currency>, from: LocalDate?, to: LocalDate?)`; `fun filterOccurrences(list, filter, today, names: Map<Long,String>): List<Occurrence>` (pure, in `domain/cash`); grouping by month of `actualDate ?: dueDate`, newest first; adjustments shown as "Balance adjustment" rows.

- [ ] Tests: accent-insensitive search ("eletrica" finds "Elétrica"); each filter alone and combined; grouping and order; future occurrences beyond 60 days hidden unless a date filter includes them.
- [ ] Stage 3 verification + push. Commit `feat(history): add searchable, filterable transaction history`.

---

## Stage 4 — Exchange rates and portfolio

### Task 4.1: Exchange rates from the Banco Central

**Files:** `…/domain/fx/PtaxParser.kt`, `…/data/rates/BcbRateClient.kt`, `…/data/repository/ExchangeRateRepository.kt`, `…/platform/rates/RateRefreshWorker.kt`, `…/di/NetworkModule.kt`, `…/ui/feature/settings/{CurrencySettingsScreen,CurrencySettingsViewModel}.kt`, `…/ui/components/RateFooter.kt`, Test: `…/domain/fx/PtaxParserTest.kt`, `…/data/repository/ExchangeRateRepositoryTest.kt`, `…/platform/rates/RateRefreshWorkerTest.kt`

**Produces:**
- `object PtaxParser { fun parse(json: String): List<Pair<LocalDate, BigDecimal>> }` (accepts `dd/MM/yyyy` + decimal string; drops rates ∉ [0.1, 100]; throws `RateFormatException` on non-array JSON).
- `fun interface RateClient { suspend fun fetch(from: LocalDate, to: LocalDate): String }` returning the JSON array; `class BcbRateClient : RateClient` (HTTPS only, 10 s timeouts, 1 MiB cap, `User-Agent: MyFinance`; HTTP 404 whose body contains `Value(s) not found` → `"[]"`; other non-2xx → `IOException`).
- `RateStateStore` (DataStore `rate_state`): `lastFetchMs`, `lastError`.
- `ExchangeRateRepository`: `rates: Flow<ExchangeRates>`, `state: Flow<RateState(lastFetchMs, lastError)>`, `suspend fun refresh(force: Boolean = false): RefreshResult` (`sealed: Updated(count), UpToDate, Disabled, Throttled, Failed(reason: NETWORK|FORMAT)`), `setManual(date, rate)`, `deleteManual(date)`; ranges and the meaning of `force` as in ARCHITECTURE.md §Exchange rates (first fetch from `min(earliest record − 7 d, today − 30 d)`, then from last BCB date + 1 plus backfill; ≤ 10-year windows; every successful response updates `lastFetchMs`).
- `RateRefreshWorker` (`@HiltWorker`, daily, `NetworkType.CONNECTED`), started from `MyFinanceApp` (plus a refresh on start if last fetch > 6 h and auto-fetch on).
- `@Composable fun RateFooter(rate: ExchangeRate?, onUpdate: (() -> Unit)?, updating: Boolean)` → "US$ 1 = R$ 5,6143 · PTAX 12/11 · Update".

- [ ] Tests: parser with the real sample `[{"data":"12/11/2026","valor":"5.6143"}]`, empty array, malformed entry dropped, `"valor":"0"` dropped, HTML body → exception; client maps the real 404 body `{"erro":{"statusCode":404,"detail":"...SGSNegocioException: Value(s) not found"}}` to `[]`; repository with a fake client: first refresh with no records requests today−30..today; with a record dated 2025-03-02 requests 2025-02-23..today; second refresh only from last date+1 and a weekend 404 → UpToDate with `lastFetchMs` updated; adding a record older than the first stored rate triggers a backfill window; MANUAL and BCB coexist and `deleteManual` leaves the BCB rate; `autoFetchRates=false` → Disabled for automatic runs but `refresh(force = true)` fetches; refresh within 6 h → Throttled unless forced; client failure → Failed and existing rates intact; worker maps Failed(NETWORK) to `Result.retry()` and FORMAT to failure.
- [ ] Currency settings screen: default currency, display currency, auto-fetch switch, Update, last fetch time, rate list (latest 30) with manual add/edit/delete. Commit `feat(fx): fetch ptax rates from banco central with manual fallback`.

### Task 4.2: Portfolio calculations (pure)

**Files:** `…/domain/portfolio/{PortfolioCalculator,Allocation,Period,Series}.kt`, Test: `…/domain/portfolio/{PortfolioCalculatorTest,AllocationTest,SeriesTest}.kt`

**Produces:**
- `data class PortfolioData(assets: List<Asset>, flows: Map<Long, List<AssetFlow>>, valuations: Map<Long, List<Valuation>>, categories: List<AssetCategory>)`.
- `data class AssetSnapshot(asset: Asset, contributed: Money, withdrawn: Money, netInvested: Money, value: Money /* ≥ 0 */, gain: Money, simpleReturn: BigDecimal? /* null when contributed = 0 */, lastValuationDate: LocalDate?, carriedForward: Boolean, closed: Boolean)`.
- `object PortfolioCalculator`: `snapshot(asset, flows, valuations, at: LocalDate): AssetSnapshot?` (null before the first record); `valueAt(asset, flows, valuations, at): Money?`; `valuationRows(asset, flows, valuations): List<ValuationRow(date, value, netInvested, gain, simpleReturn)>`; `convertedSnapshot(snapshot, flows, at, to, converter): ConvertedSnapshot(contributed: Total, withdrawn: Total, value: Total, gain: Total, simpleReturn: BigDecimal?)`; `periodGain(asset, flows, valuations, start, end): Money?` (value(start) = 0 when no record yet; null when no record ≤ end); `totals(data, at, to, converter, group: AssetGroup?): PortfolioTotals(value: Total, invested: Total, gain: Total, simpleReturn: BigDecimal?, lastValuationDate: LocalDate?, realizedClosed: Total)` — open assets only, plus the realized gain of closed ones.
- `object Allocation { fun of(data, at, to, converter, group: AssetGroup?): List<Slice(categoryId, value: Money, share: BigDecimal)> }` (open assets; shares at scale 4 summing to exactly 1, remainder on the largest slice; sorted by value).
- `enum class Period(val months: Long?) { M1(1), M3(3), M6(6), Y1(12), ALL(null) }`; `object Series { fun sampleDates(period, firstRecord: LocalDate, today): List<LocalDate>; fun portfolio(data, period, today, to, converter): List<SeriesPoint(date, value: Money, invested: Money, gain: Money)> }` (1M weekly + today; others month ends + today; nothing before first record).

- [ ] Tests (hand-computed, BRL): car with INITIAL 0 and valuation R$ 40.000 → gain 40.000, simpleReturn null; withdrawal larger than the carried value → value 0, not negative. Tesouro: INITIAL 5.000,00 on 2026-01-10, valuation 5.120,00 on 2026-03-31 → gain 120,00, return 2,40%; CONTRIBUTION 1.000,00 on 2026-04-15, no new valuation → value 6.120,00 (carriedForward), gain 120,00; PARTIAL_DISPOSAL 2.000,00 on 2026-05-01 → value 4.120,00, netInvested 4.000,00, gain 120,00, return 120/6.000 = 2,00%; valuation 4.300,00 on 2026-06-30 → gain 300,00; FULL_DISPOSAL 4.350,00 on 2026-07-10 → value 0, closed, gain 350,00. Same-day valuation includes same-day flows. Snapshot before 2026-01-10 → null. `valuationRows` keep earlier rows unchanged after later records. USD asset: INITIAL US$ 1.000,00 on 2026-02-02 (rate 5,0000), valuation US$ 1.100,00 on 2026-11-12 (rate 5,6143) → gain US$ 100,00; in BRL contributed 5.000,00, value 6.175,73, gain 1.175,73. Period gain 3M = (V_end − V_start) − net flows. Allocation of 6.000 / 3.000 / 1.000 → 0,6 / 0,3 / 0,1. Series 1Y with first record 2026-01-10 starts at 2026-01-31, ends at today.
- [ ] Implement. Commit `feat(portfolio): add valuation, gain, allocation and series calculations`.

### Task 4.3: Assets: repository, editor and detail

**Files:** `…/data/repository/PortfolioRepository.kt`, `…/data/images/ImageStore.kt`, `…/ui/feature/asset/{AssetEditorScreen,AssetEditorViewModel,AssetDetailScreen,AssetDetailViewModel}.kt`, Test: `…/data/repository/PortfolioRepositoryTest.kt`, `…/data/images/ImageStoreTest.kt`, `…/ui/feature/asset/{AssetEditorViewModelTest,AssetFlowTest,AssetScreenshotTest}.kt`

**Produces:** `PortfolioRepository`: `data: Flow<PortfolioData>`, `createAsset(draft: AssetDraft, initial: Money /* may be 0 */, initialValue: Money?): Long` (writes the INITIAL flow on the acquisition date and, if `initialValue` is given and differs from `initial`, a valuation dated today), `updateAsset(id, draft)` (currency cannot change), `addFlow(assetId, kind, date, amount, quantityDelta, note, newValue: Money?)` (refused after a FULL_DISPOSAL; at most one FULL_DISPOSAL), `addValuation(assetId, date, value, note): ValuationResult (ADDED | REPLACED)`, `deleteFlow(id)`, `deleteValuation(id)`, `deleteAsset(id)`; `data class AssetDraft(name, categoryId, currency, description, institution, acquisitionDate, quantity: BigDecimal?, notes, imageUri: String?)`. `ImageStore` ported (dir `images/assets`).

- [ ] Tests: creating an asset writes exactly one INITIAL flow (amount 0 allowed); a FULL_DISPOSAL makes the asset closed and blocks later flows until deleted; changing currency in `updateAsset` throws; same-day valuation → REPLACED; deleting an asset removes flows/valuations/image; flow test "Create an asset" and "Update asset valuation"; quantity optional and accepts `0.00012345`.
- [ ] Detail: header numbers (asset currency + display currency when different, method note), invested-vs-value line chart, valuation history rows (`valuationRows`), flows list, actions Update value / Contribute / Withdraw / Close position. Commit `feat(portfolio): add asset editor and detail`.

### Task 4.4: Flows and valuations editors

**Files:** `…/ui/feature/asset/{FlowEditorScreen,ValuationEditorScreen,FlowEditorViewModel,ValuationEditorViewModel}.kt`, Test: `…/ui/feature/asset/{FlowEditorViewModelTest,ValuationEditorViewModelTest}.kt`

- [ ] Flow editor: kind (Contribution / Withdrawal / Partial disposal / Close position), date, amount, optional quantity change, note, optional "Update estimated value" amount. Valuation editor: date (≤ today), value, note, warning when a valuation exists that day.
- [ ] Tests: a withdrawal larger than the current value suggests "Close position" and, if kept, saves with a confirmation (the value then reads 0); value ≥ 0; future dates rejected; editors refuse dates after a FULL_DISPOSAL. Commit `feat(portfolio): record contributions, withdrawals and valuations`.

### Task 4.5: Portfolio dashboard with donut

**Files:** `…/ui/components/DonutChart.kt`, `…/ui/feature/portfolio/{PortfolioScreen,PortfolioViewModel}.kt`, Test: `…/ui/feature/portfolio/{PortfolioViewModelTest,PortfolioScreenshotTest}.kt`, `…/ui/components/DonutChartTest.kt`

**Produces:** `@Composable fun DonutChart(slices: List<DonutSlice(key: Long, share: Float, color: Color, label: String)>, selected: Long?, onSelect: (Long?) -> Unit, centerTitle: String, centerValue: String, modifier)` — Canvas arcs with 1.5° gaps starting at 12 o'clock clockwise, selected slice in accent and thicker, semantics list of "label share"; `internal fun hitSlice(slices: List<DonutSlice>, angleDegrees: Float): Long?` (0° = 12 o'clock, clockwise).

- [ ] Tests: `hitSlice(angle)` maps to the right key; VM: group chips filter totals and allocation; selecting a category filters the list; empty state.
- [ ] Screen: donut (total centre), legend with %, chips All/Investments/Personal assets, totals (value, invested, gain, return, last valuation), assets grouped by category, "+ Asset", rate footer. Commit `feat(portfolio): add portfolio dashboard with allocation donut`.

### Task 4.6: Portfolio charts by period

**Files:** `…/ui/feature/portfolio/{PortfolioChartsScreen,PortfolioChartsViewModel}.kt`, Test: `…/ui/feature/portfolio/PortfolioChartsViewModelTest.kt`

- [ ] Period chips 1M/3M/6M/1Y/All; charts: net worth evolution (needs 5.1's calculator → this task renders portfolio value; net worth line added in 5.1), invested vs value, cumulative gain, allocation; period gain figure (absolute). Tests: series built from `Series.portfolio` only; no points before first record.
- [ ] Stage 4 verification + push. Commit `feat(portfolio): add portfolio charts by period`.

---

## Stage 5 — Overview

### Task 5.1: Net worth and Overview

**Files:** `…/domain/networth/NetWorthCalculator.kt`, `…/ui/feature/overview/{OverviewScreen,OverviewViewModel}.kt`, Test: `…/domain/networth/NetWorthCalculatorTest.kt`, `…/domain/ScenarioTest.kt`, `…/ui/feature/overview/{OverviewViewModelTest,OverviewScreenshotTest}.kt`

**Produces:** `data class NetWorth(cash: Total, investments: Total, personal: Total, total: Total)`; `object NetWorthCalculator { fun at(date: LocalDate, cash: CashLedger, portfolio: PortfolioData, to: Currency, converter: Converter): NetWorth; fun series(dates: List<LocalDate>, cash: CashLedger, portfolio: PortfolioData, to: Currency, converter: Converter): List<Pair<LocalDate, NetWorth>> }`.

- [ ] `ScenarioTest`: one fixture combining BRL + USD cash, recurring bills, a BRL fixed income, a USD ETF and a car, with every expected figure computed by hand in comments (available, projected, month summary, portfolio totals, allocation, net worth in BRL and in USD).
- [ ] Overview: net worth hero + composition bar and legend, R$/US$ toggle (updates `displayCurrency`), Available cash / Projected tiles, This month, Upcoming (next 5 pending/overdue with Confirm), Recent (last 5 confirmed), rate footer, onboarding empty states (opening balance, first transaction, first asset). Net worth line added to Portfolio charts.
- [ ] Stage 5 verification + push. Commit `feat(overview): add net worth overview`.

---

## Stage 6 — Backup, export and security

### Task 6.1: Backup format (port)

**Files:** `…/domain/backup/{BackupDataV1,BackupCrypto,BackupArchive,Retention}.kt`, `…/data/backup/SnapshotMapper.kt`, Test: `…/domain/backup/{BackupCryptoTest,BackupArchiveTest,RetentionTest}.kt`, `…/data/backup/SnapshotMapperTest.kt`

- [ ] Port from MyFit with `MAGIC = "MYFINBAK"`, KCV info `myfinance-kcv`, `EXTENSION = ".myfinance"`, `NAME_REGEX = ^myfinance-backup-(\d{8}-\d{6})\.myfinance$`; DTOs for the 9 tables (money as `amount: Long` + `currency: String` where the table has one; dates as epoch-day `Long`; instants epoch ms; `rateMicros: Long`; quantities as strings; nullable category names kept); `counts()` keys per table; `referencedImages()`.
- [ ] Tests: MyFit's crypto/archive/retention cases adapted; snapshot round trip of a fully populated DB equals the original (all tables, NULL names, MANUAL rates). Commit `feat(backup): add encrypted backup format`.

### Task 6.2: Key store, manager, folder and scheduling (port)

**Files:** `…/data/backup/{BackupKeyStore,BackupManager,BackupDestination,FolderBackupWriter}.kt`, `…/data/prefs/BackupStateStore.kt`, `…/platform/backup/BackupWork.kt`, Test: `…/data/backup/{BackupKeyStoreTest,BackupManagerTest,FolderBackupWriterTest}.kt`, `…/platform/backup/BackupWorkerTest.kt`, `…/testutil/FakeKeyWrapper.kt`

- [ ] Port with alias `myfinance_backup_wrap`; restore without the "session active" check; after restore call `CashRepository.ensureGenerated()`; add `suspend fun BackupManager.verify(input: () -> InputStream, sizeBytes: Long): Boolean` (device key, full decrypt + `BackupArchive.read` into a temp dir, then delete it); `FolderBackupWriter` calls it on the new file before pruning to 7. Bind `BackupTrigger` to `BackupScheduler` (replacing the no-op). `BackupNotifier`: channel `backup_status`, request `POST_NOTIFICATIONS` (API 33+) when the user chooses a folder. Unconfigured (no folder or no password) automatic runs exit silently.
- [ ] Tests: MyFit's manager cases (round trip, wrong password untouched, corrupted untouched, weak KDF not adopted, interrupted restore cleanup) + "verification failure keeps older backups". Commit `feat(backup): add key store, manager, folder writer and scheduling`.

### Task 6.3: Backup screen and CSV export

**Files:** `…/ui/feature/backup/{BackupScreen,BackupViewModel}.kt`, `…/data/repository/ExportRepository.kt`, `…/domain/export/Csv.kt`, `…/ui/feature/settings/{SettingsScreen,SettingsViewModel,PrivacyScreen,AboutScreen}.kt`, Test: `…/ui/feature/backup/{BackupViewModelTest,BackupFlowTest}.kt`, `…/domain/export/CsvTest.kt`, `…/data/repository/ExportRepositoryTest.kt`, `…/ui/feature/settings/{SettingsScreenshotTest,SettingsFlowTest}.kt`

**Produces:** `object Csv { fun row(cells: List<String?>): String; fun safeText(s: String): String }` in `domain/export`; `ExportRepository.export(out: OutputStream)` (zip of 5 CSVs; `transactions.csv` has `currency` and `actual_currency`).

- [ ] Tests: quoting of `,` `"` newline; `=SUM(1)` → `'=SUM(1)`; amounts `2500.00` and ISO dates; zip has exactly the 5 files with headers; Backup flow test (set password → back up to a temp folder → restore → data equal); Settings flow test (change number format → amounts reformat).
- [ ] Settings screen complete (sections from SPEC §26), Privacy (what is stored, network use, encryption — mirrors SECURITY.md), About (version, Geist OFL, BCB data source, MIT). Finalize `SECURITY.md` and `BACKUP.md`.
- [ ] Stage 6 verification + push. Commit `feat(settings): add backup screen, csv export, privacy and about`.

---

## Stage 7 — Polish and release

### Task 7.1: Quality pass

- [ ] Accessibility: amounts have spoken descriptions, charts have text summaries, 1.5× font and tablet screenshots for every screen (`AccessibilityScreenshotTest`), 48 dp targets.
- [ ] Loading, empty and error states on every screen; light theme review; pt-BR strings complete (lint `MissingTranslation` as error).
- [ ] Icon and splash validation screenshots; `docs/screenshots/*.png` with synthetic data. Commit `feat(ui): polish accessibility, states and translations`.

### Task 7.2: Documentation and release v1.0.0

- [ ] README final (features, screenshots, install, privacy, backup, build, stack, docs, license), CHANGELOG `1.0.0`, CONTRIBUTING.
- [ ] Keystore: the user runs `keytool -genkeypair -v -keystore ~/my/.keystores/myfinance-release.jks -alias myfinance -keyalg RSA -keysize 4096 -validity 10000` and adds `MYFINANCE_*` to `~/.gradle/gradle.properties`.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleRelease`; `apksigner verify`; `aapt dump permissions` equals the list in Task 2.1; size < 15 MB.
- [ ] Hand the APK and `docs/TESTING.md` to the user: the manual checklist (including quick entry ≤ 4 taps + typing and cold start < 1.5 s) runs on their phone before the release is published.
- [ ] Tag `v1.0.0`, `gh release create v1.0.0 app/build/outputs/apk/release/myfinance-1.0.0.apk`. Push.
