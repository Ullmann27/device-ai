# Dark reference home layout

## Source and authorization

- Owner requested the supplied darker reference layout on 2026-11-07, with three assistants.
- Base: PR #594, `88dfd45139471463618673a1f827ee062e9bf6d5`.
- Branch: `codex/continuum-dark-reference-layout-20261107`.
- Scope: native home composition, static surfaces and functional hit targets.
- Status at initial checkpoint: **WIP / UNVERIFIED**.

## Design

Keep the original cyan/violet identity, live avatar and original orb. Use near-black card interiors, restrained edge illumination and larger, vertically arranged feature cards. Restore the home input bar. The hero owns the primary area; secondary diagnostics remain reachable without dominating the first screen. Preserve actual runtime status rather than copying the reference's illustrative green indicators.

Portrait, open Fold and tablet retain all ten feature destinations. Native action bounds follow the drawn controls; microphone, camera, appearance, notifications, settings, model details and navigation stay reachable. No extra animation timer, render loop, remote model, asset dependency or permission is introduced.

## Implementation plan

- [ ] Layout assistant: `JarvisCockpitGrid.kt`, `JarvisShellLayout.kt`, optional `JarvisWindowProfile.kt`; update composition assertions while preserving bounds and minimum hit sizes.
- [ ] Surface assistant: `JarvisReferenceHud.kt`, `JarvisTiles.kt`, palette in `JarvisDesign.kt`; dark glass, bounded rims and static dimensional icon pedestals.
- [ ] Integrator: `JarvisCockpitGridContent.kt`, `JarvisShellRenderer.kt`, `ContinuumHomeView.kt`; full card labels, compact companion badge, input controls and native action routing.
- [ ] Reviewer: existing native UI routing, resize and accessibility tests; inspect the combined diff and actual rendered frames.
- [ ] Run focused geometry/drawing tests and real Android host UI tests. Use `JarvisDeterministicCaptureUiTest` for code-rendered images, not generated mockups.
- [ ] Commit and push source-bound results, exact commands and limitations.

## Compatibility and review focus

Presentation-only change around EXP-05/EXP-11; no model, memory, worker, authority or release protocol changes. Retain large-font scrolling, original render identity, meaningful status, cancelled/stale-task behavior and minimum 48dp controls. Existing PR #594 review findings outside the layout scope remain separate.

No main merge, signing, publication, device installation or claim of physical Fold7/GPU/thermal acceptance. Original signing/package/version contract is untouched. No GitHub Actions trigger requested for this layout work; checkpoint commits use `[skip ci]`.

## Verification

Initial checkpoint: NOT_RUN. The base's results are not evidence for the changed layout.

## Resume and rollback

Read this file, fetch the exact branch HEAD and inspect its PR before continuing. Revert only this branch's layout commits to return to PR #594. Other active branches must remain untouched.
