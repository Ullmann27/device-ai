# UI-020 rail request propagation

TEST_ONLY. No Gradle, Kotlin or Android run in this helper.

- Base `6c158b3e1e01954eabae8fde63d5bb4b8bf9deb4`; parent published counterpart `0e702e1d`.
- Input XML `verification-r3/android-ui-privacy-focus/xml/TEST-at.heinz.deviceai.ContinuumReferenceCockpitUiTest.xml`, timestamp `2026-11-04T22:43:50`: 12 Reference cases, 11 pass, 1 fail, 0 skip. SHA-256 `8eeeccd7afe3484cc8655c50de40d5932980b7e4a64ec39bbda4b47be772350b`. This is the pre-change result.
- Resulting Reference blob `2ac1382c0766ba4cfe32e0c0633ea2c8f09e147e`.

## Evidence and narrow change

The remaining ShortWide Home target and its rail have zero actual bounds, while the real rail LayoutParams are correctly 123 x MATCH_PARENT. Both the rail and its horizontal LinearLayout parent still have `isLayoutRequested=true`; uiWindowRoot and every outer Decor container have `false`.

Read-only javap of the installed Android 28 instrumented View confirms: `requestLayout` clears its own measure cache, marks FORCE_LAYOUT and propagates only when its parent is not already layout-requested. `measure` can reuse the cache for unchanged specs; `layout` clears its own FORCE_LAYOUT at completion. Requesting only Decor layout leaves the intervening containers' measure caches intact. This provides a concrete source explanation for the measured broken request chain after the fixture's manual measure/layout passes.

Replace the Decor-only request with `requestLayout()` on the actual current conversationRail parent, the pending horizontal LinearLayout. This propagates through the now-cleared outer parents and invalidates that measure chain. Keep the same two 16 ms frames. Capture the parent's complete pre-follow-up geometry in the minimum-target failure message; the existing post-follow-up target/ancestor geometry remains. No arbitrary wait increase, direct child measurement, size/visibility mutation or alternate Home target is introduced.

## Preserved checks

- Entire source from the first test annotation onward remains byte-identical: all 12 acceptance methods.
- Both pressed-state waits, the existing 32 ms count, strict 48 dp/visibility/overlap predicates and actual Decor touch dispatch remain.
- `git diff --check` and source inventory: PASS. Runtime confirmation belongs to Root.
- No production or other test files changed.
