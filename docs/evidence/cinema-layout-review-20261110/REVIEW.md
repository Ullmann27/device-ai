# Cinema layout review — 10 November 2026

Read-only review of **22 actual PNGs**, using the image viewer, plus the corresponding source. No new render, Gradle test, model call or production edit. Coordinates below are screenshot pixels at mdpi, measured approximately from the displayed frames. Existing capture JSON and exact PNG/source hashes are in `manifest.json`.

Reviewed source: local `a7b4d1a090ab7117f29f6fcc3440b60b863395a5`; published counterpart `5457351a7585eada2393f80aa81db4bff5cc8897`. Earlier sequence/STOP PNGs have their own source commits in the manifest. This review does not recertify them against later production changes.

## Finding: crowded compact module icons

**P2, geometry/readability; no demonstrated hit-target failure.** In `cinema-native-sequence/native-cover-idle.png` (412×915), the module block occupies approximately x276–400, y68–140. Its five module rows have a 12.8dp pitch, while each icon receives an 18dp nominal square. Consecutive nominal icon boxes therefore overlap vertically by 5.2dp. Actual vector strokes occupy smaller, shape-dependent areas; 5.2dp is **not** a claim about measured ink collision. The displayed icons look tightly packed, and the narrow label column reads shortened forms such as `Gedächt...` and `Lok...`.

Source: `JarvisShellLayout.kt:277–282` allocates `72f * font` for the compact top block. `JarvisCockpitGridContent.kt:245–257` divides `(height - 8)` by five and draws icons at fixed size `18f`. `ContinuumIcons.kt:19` maps its 24-unit grid to that nominal size. At fontScale=1 the nominal first two boxes are approximately x284–302, y69.4–87.4 and y82.2–100.2. The open-fold block has much more vertical room and does not show this packing problem.

Suggested acceptance criterion for a later scoped fix: compact module-row pitch accommodates the actual icon stroke bounds plus separation, or the compact view deliberately presents fewer module rows with an accessible detail path. Keep the existing `panel:COCKPIT_SECONDARY` system route. Do not replace the dock or create a second panel system.

## Confirmed baseline gap already under repair

`cinema-button-before/button-{inner,cover}-held-160ms.png` has no clearly distinct system-button material depression relative to idle. Both accompanying JSON receipts report `pressed_key: null`. The existing test log contains two failed assertions at `CinemaButtonMaterialUiTest.kt:144` (`Dock press reaches drawn material`). This is the known baseline for the integrator's pressure patch, not a new independent regression or a claim about the forthcoming fix.

The test performs real decor DOWN/CANCEL/UP on `bottom:system`; it asserts origin-center agreement within 1.5px at lines 140–142 **before** its failing pressure assertion. The observed baseline failure occurs later, so these assertions completed. This establishes only the tested System action and scripted native environment, not all actions or device behavior.

## Excluded false positives

| Observation | Classification and reason |
| --- | --- |
| Header cut off in button idle/held/opened: inner about 42px; cover about 56px | Fixture scroll state. `CinemaButtonMaterialUiTest` calls `JarvisHomeTestRoutes.scrollIntoView`; lines 113–123 explicitly center the target within each ScrollView. Earlier unscrolled idle screenshots show the complete header. Not a product clipping defect. |
| Background enlarges and crops during button opening-672ms, then returns to normal by opened | Transition observation only. The magnification differs from mere scrolling, but no inaccessible settled control or persistent clipping was established. Do not redesign from one transitional frame. |
| Work650ms overlay is translucent and labels behind it are faint | Construction phase. At work1180ms the foreground content, close control and STOP are readable. Not counted as a settled text-overlap defect. |
| Bottom Systemstatus cards extend below the visible body | Existing scrollable content boundary. This review did not scroll that body, so no lost-content defect is established. |
| Compact camera and work-card titles are ellipsized | Explicit bounded fit behavior. Truncation is visible, but no collision or wrong route was demonstrated. |

## Phase comparison and controls

| Frame group | Observed layout |
| --- | --- |
| Unscrolled idle, 412×915 and 884×760 | Header, hero, mission strip, three work cards, dock and footer fit the captured viewport. Compact dock uses two rows; open-fold dock one row. |
| Work280ms | Work cards/dock recede; mission strip remains visible. No completed foreground body yet. This is expected transient state, not missing result content. |
| Work650ms | Foreground construction spans approximately x15–399/y424–899 on cover and x42–661/y286–744 on inner. Underlying footer/mission text remains visible through the transient glass. |
| Work1180ms | Cover window approximately x8–403/y420–907; inner x34–674/y280–752. Cover STOP x16–395/y632–697 and close x309–376/y448 are visible; inner STOP x42–665/y490–555 and close x579–648/y307 are visible. No sampled settled overlap with face or orb. |
| Return650ms → returned | Geometry is transitional at 650ms; final idle dock/work cards/footer return on both sizes. The final screenshots show no residual foreground window. |
| Actual STOP352ms → returned, inner | Returning geometry settles to the idle layout; processing marker is red in the final screenshot. PNGs cannot establish cancellation timing independently of the existing STOP test. |
| Button System opened | Cover x8–403/y364–851; inner x34–674/y238–711. Title, close control and readable top Systemstatus rows are visible. The cropped header is the preserved fixture scroll state. |

Dock geometry is shared: `JarvisTiles.bottomNavItemBoxes` is used for paint and `ContinuumHomeView.measureShellActions` native controls; the existing minimum cell width is 48dp. At 412px, 388dp usable width / 6 columns gives about 64.7dp per cell; at 884px, 860dp / 11 gives about 78.2dp. This is a source-bound geometry check, not a new device touch test. `JarvisWorkWindowHost.plan` takes the touched native navigation view's visible bounds as origin and asks `JarvisWorkWindowGeometry.destination` for space outside face/dialog/orb protected bounds.

## Decision and remaining boundary

No new P0/P1 settled clipping or overlap defect was established in these 22 PNGs. Record the compact module-icon packing as a small bounded follow-up. Keep the existing pressure baseline failure open until the exact after-capture and regression receipts pass; this review does not approve the forthcoming patch.

Native Canvas here means Robolectric API28 with the existing 2D avatar fallback. It is **not** a Fold7 screenshot, hardware AGSL/Filament proof, accessibility-scale audit, live inference, voice or performance measurement. Larger font scales, body scrolling and other touched actions were not exercised. Main, protected release/signing, production sources and memory PR #719 remain unchanged by this review.
