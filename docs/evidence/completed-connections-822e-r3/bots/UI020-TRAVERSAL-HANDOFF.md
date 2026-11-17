# UI-020 bounded traversal repairs

Status: TEST_ONLY. Parent owns compilation and Android execution; NOT_RUN in this helper.

## Source and evidence

- Base: `8818685e7327cf7a72f8e3c1fcbc261563f43f34`; parent published counterpart `0cb7c3`.
- Fresh input: `verification-r3/android-reference-compact-v3/xml/TEST-at.heinz.deviceai.ContinuumReferenceCockpitUiTest.xml`, timestamp `2026-11-04T22:28:44`; 12 cases = 3 pass + 9 fail + 0 skip. XML SHA-256 `c7f2d6ecd161b1f33bcc73ed9fc18557eae9f4a25c0f89e35dd0ef187d1e615c`. This result predates this patch.
- Resulting Reference blob: `9b8f20100b0f764ed3698f3cfc8f048f91ef7aec`.
- Production source was read only from pinned base objects.

## Measured fixes

1. Dialog close: XML shows SCHLIESSEN moving from local top 448 to 588 during a pending real traversal. Its 66 px height is clipped to 61 px in the 649 px window after the status content grows. Tap now drains the initial pending traversal, performs the real ancestor scroll, drains one bounded 40 ms traversal, and repeats the same immediate native scroll request before hit testing. Visibility and sliver predicates remain unchanged; diagnostics include the additional post-rescroll state.
2. Recreation: the real recreate path already passes its single-pulse, configuration-change, destruction, replacement-instance and attached-Decor assertions. Dispatch `controller.windowFocusChanged(true)` after that attachment, then retain the strict real-focus assertion. No synthetic ViewRoot or manual saved-state replacement is introduced.
3. Home scene transition: source `CockpitAnimationPresenter.sceneDepth` uses `JarvisMotion.Durations.SCENE_Z`, backed by `MotionPresets.SCENE_MS = 800L`, and restores both scales and alpha in its real end action. The XML header transform was 1.1096487. After actual frame settling, an observed non-identity Home transform triggers at most one 800+32 ms frame advance, followed by exact scale/alpha restoration assertions. This occurs before/after real touches. No animations are disabled, cancelled or overwritten by this change.
4. Return Home: select uniquely from the current attached `railView` parent on wide windows, or the current phone navigation container. A present wide rail cannot fall back to another button. Retain 48 dp and actual Decor touch requirements; diagnose LayoutParams and rendered viewport dimensions. Root confirmed the independent product mismatch: `buildUi` uses width >=600 while `railWidthPx -> JarvisMenuRail.mode -> ContinuumHomeLayout.isWide` additionally requires height >=480. No fixture width is injected; Root merges the separate product repair.

## Preserved coverage and checks

- Entire source from the first test annotation onward is byte-identical to the base: all 12 test methods and their acceptance assertions remain.
- Both native ListView pressed-state waits remain. Strict 48 dp, full target visibility, real DOWN/UP, sibling and cross-parent overlap predicates remain.
- `git diff --check`: PASS. Source inventory: PASS.
- Gradle, Kotlin compilation, Android execution: NOT_RUN.
- Product files and the other test suites are untouched. Compact head/orb production failures remain with the separately assigned product fix.
