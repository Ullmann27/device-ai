# Dark reference home layout — completed source and host QA

## Current status

**IMPLEMENTED / PUSHED / HOST_QA_PASS.** Review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597).
Verified application/test source: [`3b0e4dd2f40575561b87bd84327caea0a8e63eb9`](https://github.com/Ullmann27/device-ai/commit/3b0e4dd2f40575561b87bd84327caea0a8e63eb9). The final evidence commit changes documentation only.

- Base: PR #594 at `88dfd45139471463618673a1f827ee062e9bf6d5`.
- Own branch: `codex/continuum-dark-reference-layout-20261107`.
- Owner requested the darker reference layout with three helpers; later explicitly asked for stronger contrast/color intensity and diagnostics reached by scrolling below the home composition.
- Scope: EXP-05/EXP-11 presentation, static surfaces, responsive bounds and existing native controls.

## Delivered design and connections

Near-black glass interiors now separate clearly from saturated cyan/violet borders. Static corner lighting, narrow bright cores and dimensional vector-icon pedestals retain the project identity without another rendering clock, dependency or generated artwork. Decorative fine waves stay under the face and stop with the hero/input area.

The hero uses the original avatar/orb. A compact JARVIS status badge and restored conversation strip precede ten tall feature cards. Normal open-Fold profiles use five columns and two rows; narrow windows and large fonts reflow and scroll. Card titles/descriptions and rail names are complete in the tested font matrix. The duplicate in-app system clock is removed.

The primary status strip closes the home composition. Active agents, current process, memory activity, model/device and camera/module details follow below it. Native hit bounds derive from the same geometry as the visible microphone, camera, appearance and conversation controls. The existing route IDs, permission gates, disabled-control behavior, avatar lifetime and real state sources remain connected.

## Verification

**239/239 focused tests passed** at the exact source above; no errors or skips. Gradle `:app:testDebugUnitTest` succeeded in 1m29s with actual Android SDK compilation. Native font checks exercise 90 card cases and 9 rail profiles. The independent real-source geometry harness passed 150 window/font/compact cases.

Evidence, exact commands, complete test-name receipt, source hashes and image metadata: [`docs/evidence/dark-reference-layout-20261107/README.md`](../../evidence/dark-reference-layout-20261107/README.md).

The first integrated run at `b12acea2d4fb2dcea5c100b12fed4c6c09436864` ran 247 tests with two failures: full header caption did not fit, and an old test assumed the face still sat right of the previous large dialog. The caption now fits the real available width; the structural test follows the new centered-head/compact-badge contract while real posed-face/camera assertions are retained. All targeted assertions pass in the final run. The later saturated-material checkpoint `c318e2d0ada64e8f95aef463ab72d1f45366e5d0` also passed 239/239 before the final fine-wave placement adjustment.

Three helpers covered geometry/typography, surfaces/icons, and independent native routing/capture review. The primary integrator reviewed actual code-rendered images and reran the final source after the visual adjustment.

## Explicit remaining boundaries

Physical Fold7, native Filament/GPU and thermal acceptance: **NOT_RUN**. Captures use the actual Android Activity/native Canvas with the existing 2D head explicitly selected. The original 3D asset was not replaced. No APK assembly, original signing, installation, main merge or release was performed. No package/version contract, model, memory/worker authority, CCC1/ToolGate rule or paid service was changed. Existing PR #594 findings outside this layout scope remain separate.

## Next entry and rollback

Fetch this branch and review the verified source plus evidence before integration. A future owner APK must follow the existing combined-source, original-signature and physical-device gates; this layout PR is not an APK delivery. Revert only the commits introduced by this branch to return to the PR #594 base; leave other active branches untouched.
