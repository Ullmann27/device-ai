# Device AI Continuum – Owner Fold7 portrait dashboard implementation

**Status: SOURCE_INTEGRATED / PIXEL_DEVICE_ACCEPTANCE_PENDING.**
**Repository:** https://github.com/Ullmann27/device-ai
**Branch:** `chatgpt/fold7-reference-exact-grid-20261027`
**Immutable starting point:** PR #183 `029ba12c2b92a8fc52025b0a557e9baaa4e3a153`.
**Target:** the REAL existing main Android app, package `at.heinz.deviceai.mentorpaired`; not a demo/preview or second APK. Never touch `main`, owner signing, release freeze, Sol research branches or productive brain weights.

## Owner's authoritative visual reference

Owner supplied a futuristic neon-blue/cyan/magenta CONTINUUM dashboard for the **unfolded Galaxy Z Fold7 held upright**, featuring large luminous infinity-orb, three prominent buttons per side, an upper-right live system tower and an absolutely regular 3-column by 2-row lower dashboard. A second version of the same reference was generated during this conversation. The latter is the frozen compositional target for the new portrait-only design:

- Reference preview dimensions **1144 x 1375 px**, SHA256 **`942388505349a66283d80dd833d8cb04fdefb35aa025f91c393f5c389c0e9edf`**.
- Original supplied reference **1385 x 1536 px**, SHA256 **`cd82a3cf3ee2cc80a3f90a18fddd4e13db4e78700550bcd34d38bad89924f641`**.
- These image files were supplied in the owner's ChatGPT session and are NOT falsely claimed as committed binary GitHub assets. The reference PNG must be provided to the QA comparison program if it is not mounted in the worker.

### Visual composition / measured coordinates

Portrait basis width **840 design dp**, scaled uniformly to the REAL app window width. Window height is measured separately, not inferred from the Fold product name. `isPortraitFold(widthDp,heightDp)` selects the new composition at **width >= 600 dp**, **height >= 480 dp**, **height/width >= 1.02**. Other phone and landscape geometry remains distinct.

- Top-left brand: x=2–341 dp. Head buttons remain functional.
- Hero behind controls: x=185–540 dp, y=60–(390+heroExtra) dp, with a **new real renderer geometry `FOLD_PORTRAIT`**. At 840 x 1010 dp its sphere radius is approximately 137 dp including the glass sphere factor; the larger, unclipped background stage is deliberately drawn behind cards.
- Left LARGE glass controls `VERBUNDEN`, `LERNEN`, `ENTWICKELN`: x=14–242 dp; y=77–163, 177–263, 277–369.
- Right LARGE glass controls `DENKEN`, `ANALYSIEREN`, `EVOLVIEREN`: x=491–654 dp; same vertical positions. Both sides now draw **56-dp circular icons**, fitting the title to available width without mirroring actual glyphs.
- Right `Systemstatus`: x=663–809 dp, y=61–(392+heroExtra). Includes a REAL measured app-CPU ring and current network badge; never fabricates a GPU or device-wide CPU measurement.
- Lower first row exactly `MODEL [12,276]`, `LEARNING [288,552]`, `STORAGE [564,828]`.
- Lower second row exactly `DEVELOPMENT [12,276]`, `SYSTEM_ACTIVITY [288,552]`, **`WORKERS [564,828]`**.
- Each of six cards is **264 dp wide**, with **12 dp horizontal gaps** and exactly equal height within the same portrait window. Top y=400+heroExtra; second row y=top+cardHeight+12. On tall windows lower margin grows to match the reference (rather than squeezing the two rows into the landscape template).
- At the preview reference size 1144x1375, calculated lower boxes are approximately `(16,582)-(376,923)`, `(392,582)-(752,923)`, `(768,582)-(1128,923)`, and corresponding second row `(16,940)-(376,1281)`, `(392,940)-(752,1281)`, `(768,940)-(1128,1281)`. This is **mathematical alignment to the reference**, not proof of exact Android paint pixels.

The original landscape rail is *omitted only while the Fold is portrait*. Same six action callbacks, exact repository code path, existing chat/learning/backlog/system actions. Clicking the sixth `WORKERS` card opens the existing development backlog. All card values are drawn from `ContinuumDashboardState`. This correctly differs from the image's illustrative numbers: do not inject 129 events, 435 sources, three fake workers, GPU 42% or unverified `Online` status to copy decorative text.

## Actual changed source

- `app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt`: new portrait slot layout, 3x2 grid and selection; legacy compact/landscape retained.
- `app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt`: large portrait button/icon treatment.
- `app/src/main/java/at/heinz/deviceai/ui/ContinuumPortraitElements.kt`: seven portrait elements (six cards + system tower) sharing live, correctly bounded telemetry.
- `app/src/main/java/at/heinz/deviceai/ui/ContinuumIcons.kt`: new native vector team icon.
- `app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeScreen.kt`: compose seven portrait-only elements.
- `app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt` and `ContinuumHomeView.kt`: actually enlarge the orb in the genuine device View, not just its empty slot.
- `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`: retain existing activity and package, remove rail only for portrait, preserve all existing actions and connect sixth card.
- `tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumPreviewMain.kt`: preview matches real Android orb geometry selection.
- `app/src/test/java/at/heinz/deviceai/ui/ContinuumHomeLayoutTest.kt` and `tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumHomeScreenTest.kt`: geometry, fallback and paint-path tests.
- `.github/continuum/fold7-portrait-reference.test.mjs`: source-bound design and nonregression checks.
- `scripts/continuum-fold7-portrait-compare.py`: objective comparison of a REAL owner device screenshot against the supplied target, with region-level low-frequency difference, overlays and aspect-ratio warning. It NEVER generates fake app output.

## Honest testing

- Source-bound **four focused JS structural/geometry checks PASS** against 9 exact remote GitHub files, executed through an isolated JS evaluator; this is NOT a native Node or Android/Kotlin compilation test. Measured reference box math separately: 1144x1375 target → identical lower card columns and rows with y≈582/940 px; SOURCE_MATH verified.
- The two Kotlin JUnit test sources and the one Node test source have been committed for a real checkout. **Native Kotlin/JUnit, full Node suite, Gradle app compile, Android emulator, Fold7 live rendering, PNG screenshot and pixel difference against an APK: NOT_RUN.** GitHub connector cannot independently launch the private repo Android runtime; earlier GitHub Actions were blocked by account/billing settings. Do not claim pixels look identical until physical screenshot.
- Existing telemetry, neural weights, owner permission gates, main branch and release manifest unchanged. No APK created, built, signed or installed.

### Exact next owner-device acceptance (without changing app package)

1. Build from THIS research branch (and later, after review, from a source-reviewed integrated owner app), using the existing approved Android SDK/Termux/Gradle recipe; check failure rather than assuming success. Do not bypass owner v25 source freeze. Do NOT publish a separate demo APK.
2. On the actual unfolded Fold7, upright, open the Continuum home screen. In the System Center/Diagnostics use **`Startbildschirm für Design-Abnahme sichern`**. The existing app writes a real PNG under `Pictures/Continuum` plus a JSON of window dp/px/density/font scale/insets under `Download/Continuum`, then offers the share sheet. Do not call a simulator image a physical screenshot.
3. On an authorized workstation with Pillow: `python scripts/continuum-fold7-portrait-compare.py ACTUAL_FOLD7_PNG.png REFERENCE_1144x1375.png /tmp/fold7-qa --metrics ACTUAL_FOLD7_METRICS.json`. Review `side-by-side.png`, `overlay.png`, `regions-side-by-side.png`, `report.json`. Do not accept large aspect distortions as matching pixels.
4. Fix confirmed visual deviations on this branch and repeat with the actual same app rendering. Test Fold cover (411x900), upright (750x790 and 656x690), and landscape (832x708) for hit targets, accessibility, scrolling, and non-clipped text. Use real telemetry rather than copying fictional reference figures.

**Release gate:** draft research only. Android owner-APK signing, installation, main merge and v25 source-freeze changes require distinct review/freigabe. Source remains open and editable.
