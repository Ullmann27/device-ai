# Avatar continuation with three helpers — 2026-11-04

## Owner request and live starting point

Heinz requested continuation and exactly three additional helpers. This round
continues G1-R2-AVATAR-VERIFY and the subsequent #510 speech/lifecycle handoff.
Status at this checkpoint: **IN_PROGRESS / new changes UNVERIFIED**.

Freshly read inputs:
- #495: 0e2b9ea59e71255e36580d695b16220d1d2974c6.
- #491: e101e393dd6c80803ac9d9ac91fccd9812c4a18d.
- #502 motion: 6cb9e760f0a16a6f34a8442abf9b79dcf2705799.
- #503 speech/morph follow-up: 2582be86c121d7f6d1179c7edc49e979bee8ba9c.
- #500 references/tools: 0e3685a3bc6cb3a4af61988450371f11e9cb0f3c.

Local starting commit ac2ca364b3e21655fa249d3db943c26751e92bd5 has the same
complete tree as published #495: 79a1eb7d4cb0018dacb21e5587132b8574dce88b.
The prior 136-case/NewApi result is retained in AVATAR-CONNECTION-HANDOFF.md.
It does not automatically apply to any new combination in this round.

## Three actually started helpers

| Helper | Bounded responsibility | Owned change area |
| --- | --- | --- |
| avatar_rig_contract | Reproduce current GLB pivot failure and distinguish exporter/model from obsolete contract; propose evidence-backed minimal repair | tools/epsilon-avatar-contract/ and its tests, after shared plan |
| avatar_render_motion | Independently compare existing #502/#503 motion/speech changes with the verified caller path; identify selective integration | avatar3d sources/tests after shared plan |
| avatar_independent_review | Independently inspect geometry/skin/contracts and propose negative controls; review corrections | Own isolated evidence/tests; no shared production edits |

The coordinator handles current PR collision checks, source integration,
Activity callback tests, serialized Gradle runs and publication to existing
Draft #495. Helpers use isolated worktrees. These are development helpers, not
claims of live activated Continuum app bots.

## Concrete gates

1. Reproduce the existing 5f49 asset/checker mismatch without changing the GLB.
   Do not widen anatomical bounds just to force PASS or transfer another asset's
   historical V2 receipt.
2. Reuse completed #502/#503 work where applicable; do not recreate its idle
   motion, PP closure or time-order fixes. Independently review overlap and
   preserve current speech-replay, Home/Chat and reduced-motion contracts.
3. Extend the existing real installed-listener fixture for interrupted speech
   when needed by #510, including stale events after interruption/replacement.
4. Execute selected tests on the exact integrated source; record actual failures,
   negative controls, source hashes and the limits of host rendering.

No device, native GPU or audible TTS result exists for this new round.
The source GLB remains unchanged until a separately evidenced asset decision;
reference-image fidelity remains an open owning-workstream gate.

## Compatibility and rollback

EXP-07 independent validation, EXP-11 one existing orchestration path and
EXP-12 exact source/checkpoint compatibility remain preserved. No second
Director, fanout, worker system, paid API, model training, main merge, APK release,
signing or installation. The app's offline setting and owner data are unchanged.

Rollback: omit this round's new tests/changes, retaining the prior verified
#495 connection checkpoint and the original asset. Do not reset another
developer's branch. Final results will replace this in-progress checkpoint.

