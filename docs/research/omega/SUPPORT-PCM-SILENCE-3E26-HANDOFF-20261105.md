# PCM silence integration — support handoff

Status: WIP / TESTS_NOT_RUN / NOT_RELEASED.
Date: 2026-11-05.

## Frozen intake

- Primary coordination: #559; avatar owner: #545; earlier support evidence: #547.
- Source: #544 c69199cda71c4d561c2f4721ff5d8c3a7eeefcac, tree db16767156bae99af8a41b3ef56e64b3670182ee.
- Verified local equivalent: cdd9ed6b49b3da928f2b3f6cd8a4f8d2ef131b31, same tree.
- Donor: #526 5a18f74f18d9b7d927f8c622052369496153829f.
- Work remains isolated on support/continuum-pcm-silence-3e26-20261105; no shared branch or checkout changes.

## Bounded repair

Reproduce measured silence producing word-range fallback in the current signal/director/reducer/morph chain. Selectively connect nullable utterance-bound PCM availability through TtsHologramSignal, the real AgentActivity voice frame, AvatarDirector and AvatarPoseReducer. Reuse donor regression coverage, retain all new conversation-camera/identity work and the corrected current morph mapper.

This is correction of the previously reported speech-animation bug, not new feature research. EXP-05/06/11/12 interfaces, memory/model/tool permissions and original package/signer/update channel remain unchanged. No new agent, listener, frame clock or voice provider.

## Execution plan

1. Run an actual Kotlin/JUnit behavioral baseline with the original production chain.
2. Apply the four selective source hunks and relevant existing regression hunks.
3. Run focused production-source Kotlin tests; use Android host UI tests only if the shared build lease is available.
4. Archive complete commands, raw logs, exact source tree and meaningful negative cases.
5. Hand the bounded patch to #545/#559. Final combined APK gates belong to #559.

## Current limits

No runtime success, compiled Activity, audible speech, device, signed APK or installation is claimed at this initial checkpoint. The primary team is separately repairing its APK test/build blockers. No main merge, paid execution or release action.

## Next entry and rollback

Continue from this branch's actual head, read the four production files and existing tests before edits. Revert this branch's individual commits only. Preserve other teams' changes and original raw evidence.
