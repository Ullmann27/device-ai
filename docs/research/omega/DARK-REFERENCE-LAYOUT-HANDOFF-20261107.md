# Dark reference home — single navigation and useful diagrams

## Current status

**IMPLEMENTED / PUSHED / HOST_QA_PASS.** Review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597).
Verified application/test source: [`101c0810334a347001a7b50da3faff378987ff00`](https://github.com/Ullmann27/device-ai/commit/101c0810334a347001a7b50da3faff378987ff00). This revision follows the owner's request for subtler modern icons, retained glow, and useful diagrams instead of duplicate wide-screen navigation. The earlier source `3b0e4dd2f40575561b87bd84327caea0a8e63eb9` and its 239-test receipt remain historical evidence. The new final evidence commit changes documentation only.

- Base: PR #594 at `88dfd45139471463618673a1f827ee062e9bf6d5`.
- Own branch: `codex/continuum-dark-reference-layout-20261107`.
- Owner requested the darker reference layout with three helpers; later explicitly asked for stronger contrast/color intensity and diagnostics reached by scrolling below the home composition.
- Scope: EXP-05/EXP-11 presentation, static surfaces, responsive bounds and existing native controls.

## Delivered design and connections

Near-black glass interiors separate clearly from saturated cyan/violet borders. Shared vector icons now use thinner optical contours, restrained violet details and a soft local halo. Heavy icon pedestals and repeated outline passes are removed; component edge lighting remains. Cached paths introduce no animation clock, dependency or generated artwork. Decorative fine waves stay under the face and stop with the hero/input area.

The hero uses the original avatar/orb. A compact JARVIS status badge and conversation strip precede the information area. Wide layouts keep the eleven left-rail destinations and omit the duplicated ten feature cards, including their native hit targets. Narrow phone layouts without a rail retain ten feature cards. Large fonts and narrow content reflow and scroll. The duplicate in-app system clock remains removed.

On wide layouts, memory activity and model/device charts follow the conversation input; active agents and the current process follow those charts. The status strip closes that area; camera/module details remain below it. The compact phone ordering is preserved. The memory chart shows observed events since Activity startup; the device card shows existing app-CPU samples, RAM usage and battery temperature. Missing measurements stay unknown. Native hit bounds derive from the same geometry as the visible controls. Work windows now resolve their opening origin from the actual rail or compact card. Existing routes, permission gates, disabled controls and avatar lifetime remain connected.

## Verification

Current result: **252/252 focused tests passed**, zero failures/errors/skips; real local Android compilation and Gradle completed in 11m 3s. Tests cover native routing and accessibility, normal/150%/200% text, resize, actual scrolling, work-window origins, diagnostic-deck clearance/restoration, model/measurement truthfulness and original avatar identity. Captures bind to the exact published source above.

The independent geometry harness passed **150/150 production-source Kotlin cases**, covering bounds, overlap, one wide navigation surface, ten compact routes, diagnostic ordering, chart heights and 48-dp input targets. Source hashes match the verified application source. Native card font checks include 30 actual compact-card cases plus six wide no-card cases; rail checks include nine profiles.

Current exact commands, all test names, source hashes and capture metadata: [`rail-dashboard/README.md`](../../evidence/dark-reference-layout-20261107/rail-dashboard/README.md). Owner Fold scrolling is 599px / 299.5dp and exposes all four actual diagnostic panels. Root and icon helper visually reviewed the actual Activity captures; no new icon clipping/collision was found. The compact phone hero heading retains its narrow-header ellipsis.

Historical source `3b0e4dd2f40575561b87bd84327caea0a8e63eb9`: **239/239 focused tests passed** with no errors or skips. Gradle `:app:testDebugUnitTest` succeeded in 1m29s with actual Android SDK compilation. Native font checks exercised 90 card cases and 9 rail profiles. Its separate geometry harness passed 150 cases.

Evidence, exact commands, complete test-name receipt, source hashes and image metadata: [`docs/evidence/dark-reference-layout-20261107/README.md`](../../evidence/dark-reference-layout-20261107/README.md).

The first integrated run at `b12acea2d4fb2dcea5c100b12fed4c6c09436864` ran 247 tests with two failures: full header caption did not fit, and an old test assumed the face still sat right of the previous large dialog. The caption now fits the real available width; the structural test follows the new centered-head/compact-badge contract while real posed-face/camera assertions are retained. All targeted assertions pass in the final run. The later saturated-material checkpoint `c318e2d0ada64e8f95aef463ab72d1f45366e5d0` also passed 239/239 before the final fine-wave placement adjustment.

Three helpers covered geometry/typography, surfaces/icons, and independent native routing review. All three streams are integrated, with source-bound Android verification and new evidence preserved separately from the earlier receipt.

## Explicit remaining boundaries

Physical Fold7, native Filament/GPU and thermal acceptance: **NOT_RUN**. Captures use the actual Android Activity/native Canvas with the existing 2D head explicitly selected. The original 3D asset was not replaced. No APK assembly, original signing, installation, main merge or release was performed. No package/version contract, model, memory/worker authority, CCC1/ToolGate rule or paid service was changed. Existing PR #594 findings outside this layout scope remain separate.

## Next entry and rollback

Fetch this branch and review the verified source plus evidence before integration. A future owner APK must follow the existing combined-source, original-signature and physical-device gates; this layout PR is not an APK delivery. Revert only the commits introduced by this branch to return to the PR #594 base; leave other active branches untouched.
