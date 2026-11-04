# G1-R5-COMBINED-AVATAR-ACCEPT-20261104

Status: WIP / NOT ACCEPTED. Owner-requested integration acceptance, not a release.

## Source selection and ruling

The supplied #513 anchor dda8a3cf5b07cf32d878f9b3309490fdd892091d is historical. Fresh GitHub inspection found #513 at 28b228698aef8c52e46c72b71fdd5e1915d6e76c. It already includes #512 f9267a3743a87a77bf6421011c014b2ca2d6b0d6: compare status ahead, 4 commits ahead, 0 behind, merge base equals the #512 head. Do not reconstruct or repeat this completed integration.

This new isolated branch starts at that exact #513 commit. Existing #491/#493/#495/#496/#512/#513/#518 branches are not modified. No #518 geometry is imported. The only five changed production files between #512 and #513 are AgentActivity.kt, avatar/AvatarContract.kt, avatar/AvatarDirector.kt, avatar3d/AvatarIdleMotion.kt (new) and avatar3d/AvatarMorphMapper.kt.

## Current inputs

- #491: e101e393dd6c80803ac9d9ac91fccd9812c4a18d
- #512: f9267a3743a87a77bf6421011c014b2ca2d6b0d6
- #513: 28b228698aef8c52e46c72b71fdd5e1915d6e76c
- #495: 9ef9bd16848fea1fcc6f9c9832ad65c5fc05fb7f observed; supplied repair anchor 3eb39a7023d14afd9cadcc318a61a64d41f02b65 retained for comparison.
- #493: ee2fa0ee5567e5d34bcdfe229425a71df9818183
- #496: 3968d785df607f8d461162c50bb3d710a925503e
- #518: 4167961234452c8845416ec99b241bd501b7f038; deliberately excluded.

## Claims and boundaries

This acceptance lane owns only its new tests/evidence/handoff. Existing Bot profile/task intake ownership remains #493; independent overall QA remains #496. The existing draft-loss repair is not duplicated. #513 comments and #493 review/assignment comments were read; a PR or assignment is not proof of a running worker.

The current submitBotTask implementation still writes request.text into the shared composer before asynchronous preparation and clears it on accepted paths. #493 review r4466676983 remains relevant. Draft preservation is a blocking acceptance condition, not waived by #513's selected 352-test report.

## Execution ledger

1. Fresh heads, ancestry and production delta: read and compared.
2. Isolated branch from completed #513: created.
3. Own source-bound host checks and draft-preservation counterexample: pending.
4. Full Android/JVM/widget/Lint rerun: NOT_RUN. This executor has Java/Kotlin but no Android SDK, Gradle distribution or dependency cache. Direct git clone failed with DNS resolution failure; authorized connector reads work. No remote build service or paid action started.
5. Final difference matrix, evidence and independent-review handoff: pending.

Author-reported #513 tests are inherited evidence, not executions by this coordinator. No APK/build/signature/device acceptance is claimed. No main merge, force push, Actions, model training, S01 or installation. Rollback: omit this branch; prior production sources remain unchanged.
