# G1-R5-COMBINED-AVATAR-ACCEPT-20261104

## Verdict: PARTIAL / BLOCKED — not full application acceptance

The isolated candidate preserves completed #513 integration, imports one independently reproduced correction from #495 and adds acceptance regressions. Full Android/JVM/widget/Lint and physical-device acceptance remain open. No main merge, release, signing, installation, model training, S01 or hosted Actions were performed. No other branch was modified. No subagents were launched.

## Fresh source selection and ownership

- #491: e101e393dd6c80803ac9d9ac91fccd9812c4a18d, shared Activity/UI owner.
- #512: f9267a3743a87a77bf6421011c014b2ca2d6b0d6.
- #513: 28b228698aef8c52e46c72b71fdd5e1915d6e76c; replaces the stale supplied dda8a3cf anchor.
- #495: 9ef9bd16848fea1fcc6f9c9832ad65c5fc05fb7f; replaces the supplied 3eb39a70 repair anchor.
- #493: ee2fa0ee5567e5d34bcdfe229425a71df9818183; bot/profile/task-intake owner, unsent-draft finding still open.
- #496: 3968d785df607f8d461162c50bb3d710a925503e; independent overall review remains assigned here.
- #518: 4167961234452c8845416ec99b241bd501b7f038, WIP geometry excluded.

GitHub compare confirms #513 is 4 commits ahead of #512 and 0 behind; the merge base is exactly the #512 head. Ruling: start this isolated branch from the completed #513, rather than reconstruct the historical partial integration. #513's 352 selected Android-host tests and configured NewApi-lint are author evidence, not this executor's tests and not acceptance of the new delta.

Read #513's comments, #493's review/assignment comments and #495's handoff/claims. The latest #491 issue-comment query after 18:00 UTC was empty. Published assignments do not establish live worker activity. No ownership claim is asserted over #493's repair or #496's independent review. This lane owns its tests/evidence and the single-file import below, on its own branch only.

Relevant project boundaries: AGENTS.md and experiment contract; source-integrated presentation change only, no new training, worker, tool, consent or recovery pathway. No new model or physical-device capability is implied.

## Exact production difference: #512 -> selected #513

All paths below are under app/src/main/java/at/heinz/deviceai/. GitHub's pinned comparison is https://github.com/Ullmann27/device-ai/compare/f9267a3743a87a77bf6421011c014b2ca2d6b0d6...28b228698aef8c52e46c72b71fdd5e1915d6e76c .

| Path | Status | Added/deleted | Treatment |
| --- | --- | --- | --- |
| AgentActivity.kt | modified | +4/-3 | Inherit exact #513 caller; no replacement |
| avatar/AvatarContract.kt | modified | +20/-2 | Inherit reducer/admission changes |
| avatar/AvatarDirector.kt | modified | +58/-9 | Inherit speech clock/order/reattachment changes |
| avatar3d/AvatarIdleMotion.kt | new | +79/-0 | Inherit, then import only #495's signed-zero correction |
| avatar3d/AvatarMorphMapper.kt | modified | +19/-17 | Inherit speech/idle mapping unchanged |

No other production path differs. In particular this comparison does not replace cockpit, memory, worker, model or packaged assets. Corresponding test differences are listed in the matrix file. Documentation/tool receipts are not production implementation.

## Actual new correction imported from #495

The inherited sampler calculates a negative-zero remainder for -0.0f and negative whole cycles. DoubleArray.binarySearch orders -0.0 before its +0.0 first entry; the derived slot becomes -1 and causes ArrayIndexOutOfBoundsException. The five new cases were compiled with the actual unmodified sampler: 3 PASS, 2 FAIL, exit 1. Positive boundaries and near-zero controls passed.

Imported AvatarIdleMotion.kt exactly from #495 at 9ef9bd16, blob 31801578f47f6a677c58283a408bc2840297de6d. It canonicalizes a zero remainder before lookup. The same five cases then pass, exit 0. The existing 26 motion/reducer checks were freshly recompiled and pass after the import too. This is a numeric-boundary regression, not an observed physical Fold7 crash. No alternative motion system was created.

## Remaining blocking draft loss

AgentActivity blob c1482ed3be0cb91734e10947613ec62d1a3c38cb still executes input.setText(request.text) before asynchronous preparation, then input.text.clear() on accepted paths. This matches existing #493 review r4466676983. A pre-existing unsent composer draft can be overwritten. No new repair is duplicated over #493 ownership.

Added ContinuumBotDraftPreservationUiTest with two real-Activity Robolectric cases: existing draft through accepted/safely-refused coding handoff, and stale-profile refusal. Both are NOT_RUN here, not claimed as observed Android RED tests. They permit a safe refusal or lossless successful handoff rather than imposing a new task pipeline. Remaining private-reply/local-tool accepted cases must be checked by #493/#496. The acceptance gate remains BLOCKED.

## Gate matrix

| Gate | Evidence/status |
| --- | --- |
| #512 preserved in #513 | PASS: pinned ancestry and five-path production comparison |
| audioFrame reaches Director | SOURCE_CONFIRMED: actual onRangeStart forwards audioFrame=frame |
| repeated starts / rejected ranges do not re-anchor PCM | SOURCE_CONFIRMED: speechStarted/wordRange acceptance precedes anchor; inherited Android test receipts, not rerun here |
| existing motion/reducer host checks | Own baseline 26/26 and own post-import 26/26; repeated coverage, not 52 unique tests |
| signed-zero/whole-cycle regression | Own 5 cases: 3/5 before, 5/5 after; exact production sampler, no Android stubs |
| STOP/observed-gaze/mouth cases in existing host runner | PASS within the runner's limited scope; actual Android lifecycle not rerun |
| JUnit wrapper for same five numeric cases | NOT_RUN as JUnit; bodies executed by no-dependency host runner |
| composer preservation | BLOCKED by current source; two new UI tests NOT_RUN |
| all relevant Android/unit/widget tests and NewApi lint on this candidate | NOT_RUN / environment unavailable |
| subscriptions, actual TTS/renderer/GPU/Fold7, visual reference | NOT_RUN; no inherited acceptance claimed for the new source |
| #518 geometry | NOT_IMPORTED; original asset path unchanged |

The executor has Kotlin 1.9.0 and Java 21.0.12.1 but no Android SDK, Gradle distribution or dependency cache. Direct git clone failed DNS resolution; authorized connector reads and writes succeeded. Source subsets used for host compilation were checked against Git blob hashes. The 26-case runner uses a test-only TARGETS declaration instead of the Android/org.json GLB parser. It does not validate binary GLB or GPU behavior. The five cycle cases use the actual sampler with no such declaration fixture.

Raw local execution outputs, hashes and commands are retained in docs/evidence/g1-r5-avatar-accept-20261104/. This is not a full Gradle execution.

## Next exact steps and rollback

1. #493: run the new draft-preservation UI cases against this candidate in an Android-capable environment, reproduce the existing finding and deliver the minimal existing-owner repair; include private reply and local tool paths. Do not resend already accepted owner tasks.
2. #496: independently inspect the imported sampler delta, then run regular unit suite, configured widget suite and NewApi lint on the resulting single pinned source. The published #513 counts are not a waiver.
3. Preserve actual memory, TTS/PCM, STOP, consent and subscription hooks. Do not import #518 geometry before its asset/rig/render review.
4. If any integration gate fails, retain this as draft and do not adopt it. Roll back only this lane's correction/test commit or omit this branch; #512/#513 and owner branches remain untouched.

Portable host reproduction from a complete checkout:

```sh
bash tools/fable-avatar-motion-check/run.sh
work=$(mktemp -d)
kotlinc app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt app/src/test/java/at/heinz/deviceai/avatar3d/AvatarCycleBoundaryCases.kt -jvm-target 17 -include-runtime -d "$work/cycle.jar"
java -jar "$work/cycle.jar"
```

Android-capable follow-up, NOT executed here:

```sh
./gradlew --offline --no-daemon --max-workers=1 -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests at.heinz.deviceai.ContinuumBotDraftPreservationUiTest --tests at.heinz.deviceai.avatar3d.AvatarCycleBoundaryTest
./gradlew --offline --no-daemon --max-workers=1 :app:testDebugUnitTest :app:lintDebug
./gradlew --offline --no-daemon --max-workers=1 -PcontinuumUiWidgetTests=true :app:testDebugUnitTest
```

Do not mark complete until these required gates have actual evidence. No sign/install step is included.
