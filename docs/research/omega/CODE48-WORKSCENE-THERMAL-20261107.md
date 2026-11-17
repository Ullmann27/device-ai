# Code48 workscene and avoidable load repair

Status: WIP / UNVERIFIED. Base PR588 f726aad3a90f3ddf8a9fdd8217c9ae637ab1c7b5, verified live APK source
7732b8668f912419d7aac7a1609a97097d727cbf (Code47 / 0.3.46).

## Owner report and bounded work
The owner reports missing visible task animation and excessive device heat.
Trace the real input, task/tool, work-window, avatar and lifecycle path.
Identify reproducible redundant rendering, allocations or background work
before changing CPU/RAM policy. Preserve visible design, original orb/avatar,
local model quality and all execution/consent boundaries.

Work proceeds sequentially on this isolated branch. No helper agents,
parallel feature streams or replacement architecture. No main merge,
key export, package change, device installation or paid service.

## Investigation and proof
- Original Code47 full publisher QA and served APK verification passed.
- That result did not prove Fold7 animations or thermal behavior.
- Current changed-source tests: NOT_RUN.
- Device trace, installed build readback and physical thermal measurement: NOT_RUN.
- Candidate causes must be separated from reproduced host defects.
- Reuse actual SDK/Robolectric tests and source-bound publisher gates.
- Record rendering/callback/allocation counters where useful; do not claim
  temperature, FPS or battery savings from host tests.

EXP-02/07: regression repair and real counterexamples.
EXP-05/11/12: preserve local inference, existing task orchestration and identity.

## Next
Follow the task presentation from normal chat/voice input, including initial
layout, keyboard, app handoff and result return. Inspect frequent render and
dashboard paths and their visibility/lifecycle cancellation. Reproduce each
defect before its repair, preserve raw evidence, then validate the combined
source. Any new owner APK must use a code greater than 47 and the original
publisher/signature after complete QA. Physical Fold7 acceptance remains
a separate gate.

Rollback: revert only this branch's repair commits; retain previous release
evidence and all owner data. Resume from this branch's actual remote HEAD.

## Reproduced defects / checkpoint

Actual SDK Kotlin test compilation exit 0. Two new tests against the
unchanged live production baseline both fail for the intended assertions:
- typedPdfRequestReturnsFromChatIntoAnimatedDocumentWork: no visible work window.
- documentWorkWaitsForFreshShellGeometry: scene discarded before fresh geometry.

Actual Activity/input routing, native host Canvas and work-window classes;
only the parsed document/page is a synthetic boundary fixture. Raw logs,
exact command and source hashes are in docs/evidence/code48-workscene-thermal-20261107/.
Production has not yet changed. No Code48 APK has been built.

The owner requested an immediate way to view the existing Code47 animation
before the general repair. Verified source route: START -> Systemstatus
tile -> existing work-window presentation. Hologram long press -> Bewegung
pruefen reports motion blockers. This is a source/host route, not a newly
observed Fold7 result.

Follow-up: repair Chat-to-document presentation and first-layout handling;
then remove proved redundant scene allocations with bounded RAM reuse.
Inspection also found dashboard status/holdout work every five seconds,
including while the dashboard is hidden; measure before choosing a cache.
No thermal savings have been measured or claimed.

## Visual acceptance correction

The existing Systemstatus work window was explicitly rejected as a
demonstration of the requested cinematic task presentation. It is a generic
status card inside JarvisWorkWindow, with duplicated panel headings; its
opening transition is not acceptance of the reference-video objective.
Do not send the owner through settings or repeat this route as a solution.

The requested sequence must visibly coordinate the existing original
avatar/camera, an app or real document arriving as a spatial work surface,
actual processing/content changes, result presentation and return to the
cockpit. Existing partial motion primitives and green functional tests do
not prove that integrated visual result. Recover the original reference
videos before redesign; retain original palette, avatar and orb.

The two reproduced routing/layout failures remain valid but their repair
alone cannot satisfy this visual acceptance. New production changes: NONE.
Current APK remains Code47. No physical animation/thermal acceptance.

## Original Code47 evidence and continuation

This is the historical investigation checkpoint, not the final Code48 status.
The original served Code47 source is
`7732b8668f912419d7aac7a1609a97097d727cbf`, APK SHA256
`35f93b41ed835bf2ec16890b62d4e640d3b1ea28c2e8a098065bd08f64dc2481`,
101,925,655 bytes. The original readback was at 2026-11-07 03:27 UTC;
it is not a fresh download at this documentation update. Source-bound
publisher markers and binary metadata are preserved in
[Code47 evidence](../../evidence/code47-apk-20261107/README.md),
[publisher receipt](../../evidence/code47-apk-20261107/publisher-pass.json) and
[binary verification](../../evidence/code47-apk-20261107/served-apk/verification.json).
That selected publisher receipt does not expose aggregate successful test
counts or complete tool-version output; do not infer them from an earlier
failed run or from the later local environment.

The Code48 PDF routing regression is exercised through `AgentActivity` input,
`CockpitAnimationPresenter`, `JarvisWorkWindow` and the existing hologram host.
The regression methods are
`typedPdfRequestReturnsFromChatIntoAnimatedDocumentWork` and
`documentWorkWaitsForFreshShellGeometry`; raw compiler/JUnit commands and
hashes are in `docs/evidence/code48-workscene-thermal-20261107/` and the later
`docs/evidence/code48-document-scene-repair-20261107/`. Subsequent fixes,
commands, current gate states and entry points are in
[the combined cinema continuation](CODE48-FULLSTAGE-CINEMA-20261107.md).
These focused native-host results do not prove phone temperature or GPU FPS.
