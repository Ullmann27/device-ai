# Owner Android publisher failure and targeted test correction — 2026-11-06

## Exact publisher evidence

- Original Render publisher: `srv-dbf1tr2vcj2c73ehpfog`.
- Rejected deployment: `dep-dbn06r8u01pc73fpcgt0`, source `48251610d9c9519678e9fc5ef652ad8286675b7f` (Code45).
- Actual full Android unit results at `2026-11-06T17:23:58Z`: **3556 tests, 5 failed, 20 skipped**. `:app:testDebugUnitTest` failed after 37m58s.
- Five failing methods all belong to `JarvisWorkWindowDepthTest`: `aNarrowWindowKeepsItsSheetsInsideTheirOwnWidth`, `nonFiniteOrEmptyGeometryDrawsNothing`, `thePageRecedesFromItsPlaceInTheDocumentToTheSourceSlotOfTheDraft`, `theDocumentPlaneSettlesAsTheFirstRearLayerOfTheDraft`, `rearLayersStepBackAndUpBehindTheFrontWhileItIsOpen`.
- Render gate output: `QA_RED android-gradle`, `QA_RED incomplete-or-stale-android-junit`, `QA_RED mandatory-owner-Fold7-suites`; later `QA_SUMMARY failed_gates=3` and `CONTINUUM_OWNER_RENDER_RELEASE_BLOCKED`. Original signing and delivery were **correctly blocked**.

## Cause and repair

All five failing test methods construct platform `android.graphics.RectF`, while the unchanged test class was under `app/src/test/java` with plain JUnit and no Android framework implementation. The one scalar-only method is not among the five failures. This is a strongly supported test-harness cause, not proof of an animation production defect. Render's summarized console output did not include the full exception message.

Move the same complete `JarvisWorkWindowDepthTest` suite to `app/src/uiTest/java`, attach `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [28])`. The full original publisher runs `-PcontinuumUiWidgetTests=true`, which includes that directory and its Robolectric 4.14.1 dependencies. **No assertion dropped, no production geometry changed, no release gate relaxed.**

## Verification status

- Original five failures: **confirmed** on Code45 full Render QA.
- Code46 original source `756bc68d0fb7f8522da8900fd889fd2eae2e8caf`: built by an earlier in-flight publisher job; uncorrected class is still present in that exact pinned source.
- Corrected source: see `codex/continuum-owner-apk-20261106` commits following `9e7852ae2852d35148bf2dd93f945252b1716a02`.
- Corrected **full publisher QA, sign, HTTPS download, APK cert and Fold7 device acceptance: NOT_YET_VERIFIED**. Neither passing local selective Android tests nor the test-harness repair is release approval.

The publisher branch must fast-forward to the corrected exact GitHub SHA; a new original publisher deployment must run all existing gates before signing. Do not reuse a green status from an older SHA, merge to main, or distribute unsigned artifacts.
