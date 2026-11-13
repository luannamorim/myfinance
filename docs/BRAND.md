# MyFinance — Visual identity

## Concept

MyFinance is MyFit's sibling. MyFit's symbol is an "M" drawn as a single progress line ending in a green dot. MyFinance uses the same grammar for the **portfolio's allocation ring**: a donut almost closed, completed by the **green data point**, the latest value. It reads as a whole being built up (net worth, allocation, progress) without dollar signs, coins, banks, wallets or stock-market clichés.

The interface follows the same principle as MyFit: graphite neutrals, firm typography and **a single accent colour**, used only where it carries meaning.

## Symbol and wordmark

- 24-unit grid, same metrics as MyFit: 2.6 stroke with round caps and joins; dot radius 2.3, centred on the extension of the last segment with a 1-unit gap between stroke and dot.
- One stroke, as in MyFit: an arc of radius 7.4 around (12, 12) that leaves a gap at the top right (centred at −45°). The green dot sits in the gap, on the circle, with a 1-unit gap to each end of the stroke (half-gap angle 2·asin(4.6 / 14.8) ≈ 36.2°).
- Geometry (ink centred on (12, 12)):
  - Arc: `M19.313,10.870 A7.4,7.4 0 1,1 13.130,4.687` (start −8.784°, sweep 287.568° clockwise from 3 o'clock)
  - Dot: centre `(17.233, 6.767)`, radius 2.3
- Single source of the geometry: `ui/components/FinanceMark.kt` (`MarkGeometry`). The launcher foreground, monochrome, splash and notification vectors use the same numbers.
- Wordmark: `myfinance` in lowercase, Geist SemiBold, −0.04 em letter spacing, to the right of the symbol (symbol height = 1.25 × font size).
- App icon: `#0B0B0C` background, `#F5F5F4` line, `#47F55E` dot; the 24-unit mark is scaled 2.25× and centred in the 108 dp adaptive icon (ink ≈ 39 × 39 dp, inside the 66 dp safe zone). Monochrome version for themed icons (Android 13+). Splash: the same foreground on `#0B0B0C` (`core-splashscreen`).
- Minimum symbol size: 16 dp.

Version 1.0.0 shipped an "F" whose arms rose in parallel. It was replaced after release: six marks in the same grammar (ring, rising bars, compound-growth curve, lowercase f, dollar S, stairs) were compared at 16–64 dp, as launcher icons (circle, squircle, themed) and as notification icons, next to the MyFit mark. The ring was chosen: a closed, simple shape that stays firm at 16 dp, ties the brand to the Portfolio donut, and keeps MyFit's line-and-dot vocabulary.

## Colours

Identical to MyFit.

| Token | Dark | Light | Usage |
|---|---|---|---|
| `background` | `#0B0B0C` | `#FAFAF9` | screen background |
| `surface` | `#141416` | `#FFFFFF` | cards |
| `surfaceRaised` | `#1C1C1F` | `#F2F2F0` | controls, dialogs, navigation indicator |
| `outline` | `#2A2A2E` | `#E2E2DF` | thin borders (1 dp) |
| `outlineStrong` | `#3A3A40` | `#CBCBC7` | secondary button borders |
| `textPrimary` | `#F5F5F4` | `#0B0B0C` | primary text, amounts |
| `textSecondary` | `#A1A1A8` | `#5A5A62` | labels, metadata |
| `textTertiary` | `#6B6B73` | `#8E8E95` | disabled, inactive icons |
| `accent` | `#47F55E` | `#15803D` | primary action, positive values, received/paid |
| `onAccent` | `#0B0B0C` | `#FFFFFF` | text on green |
| `danger` | `#F2555A` | `#CD2B31` | overdue, negative values, destructive actions, errors |
| `warning` | `#F5A524` | `#B45309` | attention (pending soon, stale exchange rate) |

Semantic aliases: `positive = accent`, `negative = danger`.

Contrast is enforced by `ColorContrastTest`: primary text ≥ 7:1, secondary ≥ 4.5:1, text on green ≥ 4.5:1, green and red on background ≥ 3:1.

**Money colour rule.** Financial information must stay easy to read, so colour is used sparingly:
- Transaction rows: amount in `textPrimary` with a sign (`+`/`−`); the status chip carries colour (Overdue red, Pending amber, Paid/Received green).
- Totals and deltas: income and positive gains in green; expenses in summaries, negative gains and negative balances in red.
- Neutral for everything else: categories, dates, labels, chart axes.

**Chart palette.** Charts use the accent for the primary series or the selected category and a ramp of neutrals for the rest (`chart1`…`chart6`, from `textPrimary` towards `textTertiary`), so a single green never competes with a rainbow. Expenses vs. income use `danger` vs. `accent` only in the income-vs-expenses chart.

## Typography

Geist (SIL OFL 1.1, bundled in `res/font`, license in `docs/licenses/GEIST_OFL.txt`), in Regular, Medium, SemiBold and Bold weights.

| Style | Size / weight | Usage |
|---|---|---|
| `display` | 32 SemiBold, −0.03 em | tab titles |
| `headline` | 24 SemiBold | full-screen titles |
| `title` | 17 SemiBold | card and list titles |
| `body` / `bodyStrong` | 15 Regular / Medium | body text, buttons |
| `label` | 13 Medium | labels, chips |
| `caption` | 12 Regular | metadata |
| `overline` | 11 SemiBold, uppercase, +0.1 em | section headers |
| `numericHero` | 40 SemiBold, tabular figures | net worth, portfolio total |
| `numericLarge` | 30 SemiBold, tabular figures | highlighted values |
| `numeric` | 15 Medium, tabular figures | amounts in rows |

Amounts always use tabular figures (`tnum`) so columns of values align.

## Shape and spacing

- 4 dp grid; 20 dp horizontal screen margin; content capped at 640 dp on large screens.
- Corners: 10 dp (small), 16 dp (cards), 24 dp (dialogs); pill-shaped buttons and chips.
- Flat cards: 1 dp border, no shadow.
- Touch targets: 48 dp minimum.

## Motion

150–250 ms, standard easing, no bounce. Animate only what communicates state: content entering and leaving, selection, chart segment selection.

## Components

`ui/components`: `FinanceMark`, `FinanceWordmark`, `FinanceCard`, `PrimaryButton`, `SecondaryButton`, `TextAction`, `StatTile`, `SectionHeader`, `EmptyState`, `FilterChipRow` / `FinanceChip`, `AmountDisplay`, `CurrencyInput`, `DateSelector`, `NumberStepper`, `MonthSwitcher`, `DonutChart`, `ConfirmDialog`, `FinanceTopBar`, `TabHeader` / `TabScreen`, `FinanceTextField`.
Visual catalog: `ComponentCatalogScreenshotTest` (PNGs in `app/build/outputs/roborazzi/`).

## Do / avoid

- Do: plenty of negative space, one primary action per screen, large readable figures, the rate date next to converted values.
- Avoid: gradients, shadows, illustrations, dollar signs, coins, bank buildings, rainbow charts, more than one accent colour, coloured icons.
