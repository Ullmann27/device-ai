# Dark reference home — single navigation and useful diagrams

## Current status

**WIP / SOURCE_CHECKPOINT / HOST_QA_PENDING.** Review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597).
This revision follows the owner's request for subtler modern icons, retained glow, and useful diagrams instead of duplicate wide-screen navigation. The earlier completed source [`3b0e4dd2f40575561b87bd84327caea0a8e63eb9`](https://github.com/Ullmann27/device-ai/commit/3b0e4dd2f40575561b87bd84327caea0a8e63eb9) and its 239-test receipt remain historical evidence, not validation of this revision.

- Base: PR #594 at `88dfd45139471463618673a1f827ee062e9bf6d5`.
- Own branch: `codex/continuum-dark-reference-layout-20261107`.
- Owner requested the darker reference layout with three helpers; later explicitly asked for stronger contrast/color intensity and diagnostics reached by scrolling below the home composition.
- Scope: EXP-05/EXP-11 presentation, static surfaces, responsive bounds and existing native controls.

## Delivered design and connections

Near-black glass interiors separate clearly from saturated cyan/violet borders. Shared vector icons now use thinner optical contours, restrained violet details and a soft local halo. Heavy icon pedestals and repeated outline passes are removed; component edge lighting remains. Cached paths introduce no animation clock, dependency or generated artwork. Decorative fine waves stay under the face and stop with the hero/input area.

The hero uses the original avatar/orb. A compact JARVIS status badge and conversation strip precede the information area. Wide layouts keep the eleven left-rail destinations and omit the duplicated ten feature cards, including their native hit targets. Narrow phone layouts without a rail retain ten feature cards. Large fonts and narrow content reflow and scroll. The duplicate in-app system clock remains removed.

On wide layouts, memory activity and model/device charts follow the conversation input; active agents and the current process follow those charts. The status strip closes that area; camera/module details remain below it. The compact phone ordering is preserved. The memory chart shows observed events since Activity startup; the device card shows existing app-CPU samples, RAM usage and battery temperature. Missing measurements stay unknown. Native hit bounds derive from the same geometry as the visible controls. Existing routes, permission gates, disabled controls and avatar lifetime remain connected.

## Verification

Current geometry-only check: **150/150 production-source Kotlin cases passed**, covering bounds, overlap, one wide navigation surface, ten compact routes, diagnostic ordering, chart heights and 48-dp input targets. Current Android compilation, native font/touch/scroll checks and captures: **PENDING** at this checkpoint.

Historical source `3b0e4dd2f40575561b87bd84327caea0a8e63eb9`: **239/239 focused tests passed** with no errors or skips. Gradle `:app:testDebugUnitTest` succeeded in 1m29s with actual Android SDK compilation. Native font checks exercised 90 card cases and 9 rail profiles. Its separate geometry harness passed 150 cases.

Evidence, exact commands, complete test-name receipt, source hashes and image metadata: [`docs/evidence/dark-reference-layout-20261107/README.md`](../../evidence/dark-reference-layout-20261107/README.md).

The first integrated run at `b12acea2d4fb2dcea5c100b12fed4c6c09436864` ran 247 tests with two failures: full header caption did not fit, and an old test assumed the face still sat right of the previous large dialog. The caption now fits the real available width; the structural test follows the new centered-head/compact-badge contract while real posed-face/camera assertions are retained. All targeted assertions pass in the final run. The later saturated-material checkpoint `c318e2d0ada64e8f95aef463ab72d1f45366e5d0` also passed 239/239 before the final fine-wave placement adjustment.

Three helpers cover geometry/typography, surfaces/icons, and independent native routing review. Next: run the focused local Android suite at the published source SHA, inspect actual Activity captures, and publish a new receipt without overwriting the earlier evidence.

## Explicit remaining boundaries

Physical Fold7, native Filament/GPU and thermal acceptance: **NOT_RUN**. Captures use the actual Android Activity/native Canvas with the existing 2D head explicitly selected. The original 3D asset was not replaced. No APK assembly, original signing, installation, main merge or release was performed. No package/version contract, model, memory/worker authority, CCC1/ToolGate rule or paid service was changed. Existing PR #594 findings outside this layout scope remain separate.

## Next entry and rollback

Fetch this branch and review the verified source plus evidence before integration. A future owner APK must follow the existing combined-source, original-signature and physical-device gates; this layout PR is not an APK delivery. Revert only the commits introduced by this branch to return to the PR #594 base; leave other active branches untouched.
