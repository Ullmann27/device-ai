# Dark reference home — owner correction

## Current source and review

Review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597). Branch: `codex/continuum-dark-reference-layout-20261107`, stacked on PR #594 at `88dfd45139471463618673a1f827ee062e9bf6d5`.

Application source: [`60ec75e3fc468b6cf96237a338f3f6388e6bcaf0`](https://github.com/Ullmann27/device-ai/commit/60ec75e3fc468b6cf96237a338f3f6388e6bcaf0). Final source/test commit: [`64609f65c319a97dacacd7608d541fbc196c0265`](https://github.com/Ullmann27/device-ai/commit/64609f65c319a97dacacd7608d541fbc196c0265). **IMPLEMENTED / PUSHED / HOST_VERIFIED_WITH_TARGETED_RETRY.** The coordinated run passed 266 of 267 cases; its only failure was an old two-line subtitle expectation. The final commit changes that one test expectation to the reference's three lines, with no production-source change. The affected class then passed all seven cases in 15 seconds. There are 267 distinct cases with passing evidence across both runs, not one all-green coordinated run.

The owner explicitly clarified the intended composition: copy the supplied dark reference closely; remove the fixed LEFT Home rail; retain the TEN large feature cards; place diagnostics after the complete homepage. Small animated live tabs can replace the rail. Measurements must remain real and current. This supersedes the prior rail-only interpretation.

## Implemented design

- Full-width hero with the original head and orb, a small JARVIS badge, microphone/send input, ten tall cards and a three-part status strip. Five columns on ordinary wide profiles; readable reflow for phones and large text. No fixed Home rail or duplicate Home-rail nodes.
- Dark glass, saturated cyan/magenta light, localized corner reflections, modern vector icons and small light ellipses. Reference card wording and line grouping are restored. Icon/text positions and typography follow the taller card proportions. Native font widths are remeasured before fitting; 200% text remains supported.
- The main frame ends below the primary status. Memory/model diagrams, agents/process data and secondary camera/module information follow through normal scrolling.
- Three narrow native live tabs expose existing system, memory and worker snapshots. Missing/invalid readings stay unknown; time alone cannot change measurements. Finite drawer transitions and measurement highlights share the existing shell clock. Animator scale, own reduced motion, foreground, visibility, power and thermal policy remain connected.
- Open drawers retain selection and live values across native measurement/reflow. Background, detach and work-window deck clearance close them. Native click targets remain at least 48 dp.
- Work windows retain the real dashboard scroll position. Their origin follows the visible part of the actual tapped card, and their destination uses the clipped viewport in overlay coordinates. Original avatar/dialog/orb protection and existing permission routes remain.

The original 12,038,416-byte GLB is unchanged, SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`. A host Blender render verifies the packaged realistic portrait asset. This is not an Android/Filament screenshot. Reference-mode head ornaments are reduced, lower waves stay near the chest and additional orb-floor rings are less prominent; existing clocks and state signals are retained.

## Verification and evidence

Three helpers covered production geometry, reference materials/typography and native routing/motion review. The production Kotlin geometry harness passed 150 cases; its three source hashes still match the candidate. The latest focused diagnostic run passed all 11 tests, including nine LiveTabs cases and the native 90-card font matrix. Normal reference/Fold/scrolled captures were visually reviewed with no new clipping or icon/text collisions.

The two-run receipt, every test name and original failure, source hashes, six actual Activity capture records and reproduction commands are in [`reference-match/README.md`](../../evidence/dark-reference-layout-20261107/reference-match/README.md). All nine LiveTabs tests, both work-window viewport tests, navigation, motion and native font checks passed in the coordinated run. Native screenshots explicitly select the existing 2D fallback head; their metadata says so. The original 3D asset has separate host-render evidence.

Earlier correction runs exposed a stale idle-reply assertion, native posted-click fixture timing, the old scroll-reset origin behavior, a transient-width drawer dismissal and native font-hinting overflow. These were addressed without bypassing actual touch dispatch or weakening geometry/accessibility assertions. Intermediate results are superseded by the final source-bound receipt.

Historical receipts remain separate: [252-test superseded rail layout](../../evidence/dark-reference-layout-20261107/rail-dashboard/README.md) and [earlier 239-test composition](../../evidence/dark-reference-layout-20261107/README.md). Neither certifies this correction.

## Integration boundary

Scope: EXP-05/EXP-11 presentation and native controls. No model/memory/worker authority, CCC1/ToolGate rule, package/version, original signing contract or paid service changed. Physical Fold7, actual Filament/GPU output and real thermal acceptance remain NOT_RUN. No APK assembly/signing/installation, main merge, release or GitHub Actions was performed. Pixel identity on the owner's device is not established by host screenshots.

Fetch this branch and compare the exact source and receipt before integration. A future owner APK follows the existing combined-source, original-signature and physical-device gates. To roll back, revert only this branch's layout commits; preserve the PR #594 base and older evidence.
