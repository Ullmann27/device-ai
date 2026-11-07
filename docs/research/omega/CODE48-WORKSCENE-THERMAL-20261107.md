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
