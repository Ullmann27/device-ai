# UI-020 fixture v3 integration

Status: TEST_ONLY. Android compilation and execution: NOT_RUN in this helper. Parent owns the next actual gate.

## Pinned inputs

- Integration base: `95b0f8f843b8296c22ec715d7a1836a9ee7c5db0` (parent published counterpart `a3ef4d2`).
- Frozen C1 v3: local `5fec06d587dcceded3d066bf3eeded888255fd2f`; parent-verified published counterpart `08370482c609537b5b8d0d8370d2b4a933d4e35d`. Reference source blob: `5059be0cd5f3d4e850eaac6d08faca357e1429fd`. Read using `git show`; no foreign working-tree files imported.
- Failing parent run: `verification-r3/android-reference-compact/xml/TEST-at.heinz.deviceai.ContinuumReferenceCockpitUiTest.xml`, timestamp `2026-11-04T22:21:40`; 12 cases, 3 pass, 9 fail, 0 skip. XML SHA-256: `b2a6db99a74594733f93b3aeb931974c151f1ca8cab8288c379659e44e305ae8`. These are input failures, not a result for this change.
- Resulting Reference test blob: `ca95b1d41b5affd40d752d886821cb3520678cdd`.

## Changes

1. Import C1's null-safe native-control label and the explicit two-pass Return Home traversal. The label-only NPE and the width-zero-before-layout stack frames directly motivate these fixes.
2. Replace the earlier manual saved-Bundle recreation helper with C1's actual `controller.recreate()` path. One async 16 ms pulse passes traversal barriers; assertions retain configuration-change teardown, old-Activity destruction, replacement identity, attached decor and actual window focus. Both existing restore scenarios keep their state and no-task assertions. This supersedes the manual-recreation implementation described in the earlier merge handoff.
3. Preserve global clipped visibility filtering. Compare visible siblings strictly as transformed `RectF` bounds in their shared parent's coordinate system; compare different-parent controls using their global clipped `Rect`s. This avoids independent integer floor/ceil hulls introducing false intersections at a shared transformed edge. No pixel allowance was added. Existing sibling checks and the rail-viewport global check remain.
4. Retain the full visible-target/sliver requirement unchanged. Add raw local/global rectangles, matrices, scroll offsets, child heights, layout/attachment state and before-scroll/after-scroll/after-traversal diagnostics. The separate dialog close failure is unresolved until the parent obtains actual dimensions; it is not attributed to the Home transform without evidence.

## Preserved acceptance

All 12 named methods, all fonts/profiles, exact compact-to-2D restoration, same live avatar, no task submission, 48 dp bounds, actual Decor DOWN/UP dispatch, cross-parent global clipping and both native ListView pressed-state waits remain. No product, gate, dependency or other test file changed. The three avatar lifecycle methods are outside this patch.

## Checks

- `git diff --check`: PASS.
- Source inventory: 12 unchanged test names; two real recreation call sites; both native ListView waits; strict 48 dp, full target visibility and real touch predicates present.
- Gradle, Kotlin compilation, Android execution: NOT_RUN, as requested.
