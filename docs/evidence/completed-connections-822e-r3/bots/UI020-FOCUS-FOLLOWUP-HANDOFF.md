# UI-020 follow-up traversal and focus

TEST_ONLY. Gradle/Kotlin/Android checks: NOT_RUN by this helper.

- Base `75533fa97532ace8333d1023c689ee2a86e87668`; parent published counterpart `4d9ba7e`.
- Input XML `verification-r3/android-reference-connected/xml/TEST-at.heinz.deviceai.ContinuumReferenceCockpitUiTest.xml`, timestamp `2026-11-04T22:35:52`: 12 total, 8 pass, 4 fail, 0 skip. SHA-256 `3ed8fb83e42da014975563fb1c8c5e88b00e2e42bf431f6b46dd832ada421179`. Input result only.
- Resulting Reference blob `6ac1b9c3aaa259a5696953bf6f322ff3cb2da33c`.

## Narrow changes

- After real `controller.windowFocusChanged(true)`, deliver exactly two 16 ms frames before the unchanged real-focus predicate. Read-only javap of installed Robolectric 4.14.1 `ActivityController.windowFocusChanged` shows `ShadowViewRootImpl.callWindowFocusChanged` followed only by `idleIfPaused`. Installed Android 28 `ViewRootImpl.windowFocusChanged` sets pending focus flags and queues synchronous message 6; a paused future traversal can still gate its dispatch. No focus flag or ViewRoot field is written by the fixture. Failure text now includes actual Decor focus/geometry.
- After Return Home's two manual measure/layout passes, request a real Decor layout and deliver exactly two 16 ms frames before choosing the active current rail. Fresh XML shows the corrected rail LayoutParams width 123 but zero actual dimensions, pending layout in the rail and horizontal parent, and cleared Decor/root layout flags. Re-propagating the pending layout through the actual root avoids measuring before that follow-up traversal. No measured size, LayoutParams or fallback target is injected.

## Checks and boundaries

- Complete test section from the first `@Test` remains byte-identical: all 12 cases and existing acceptance assertions.
- Both native ListView waits, 48 dp, full target visibility, real DOWN/UP, current rail selection and overlap predicates retained.
- `git diff --check` and source inventory: PASS. Runtime result remains for Root to measure.
- No product or other test file changed. The separate font-2 knowledge-dialog geometry failure remains with its assigned product investigation.
