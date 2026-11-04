# Avatar connection — owner continuation, 2026-11-04

## Request and exact inputs

Heinz authorized connecting the completed three-helper package. The fresh read
found that #491 already incorporated all three production fixes and all 26 tests.
Do not reapply them or replace the working direct live-view binding with fanout.

- Current #491 input: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- Existing #495 helper input: `6cf6e2128463e6a24dfac4652bb65e325ff6f18a`.
- Original tested helper production: `7859d5d212d61829fabc7dfdbd3ca7587852388d`.
- Common ancestor: `d352bab6855db2a44def0bbf60fd93202b4e3066`.

This continuation reconciles #491 and #495 on the helper review branch. The
merged app sources and tests are exactly those of the #491 input; earlier
helper documentation/evidence is retained. No other developer branch is reset
or edited. The previous 118-test result remains historical and source-bound.

## Independently confirmed source connection

The coordinator and two reviewers compared the six file blobs. They match
exactly on #491 and the previously tested helper source:

| File | Git blob |
| --- | --- |
| avatar/AvatarContract.kt | `d9a0f6b1d72d2b9107d620526e311e41007a573d` |
| avatar3d/AvatarMorphMapper.kt | `546832d4c4b7248fb865154879744eeb0be9ab05` |
| cockpit/CockpitContracts.kt | `602ab96b32e0886f0bad4d511d8cdbc9ef07822c` |
| avatar/AvatarSpeechOrderingRegressionTest.kt | `b577331fafabfd4d7ba5fd17786f283dfe311ca0` |
| avatar3d/AvatarMorphTransitionTest.kt | `c1ae70968dc617a5f1cae349d2239c043223bd10` |
| cockpit/JarvisAvatarDispatchRegressionTest.kt | `d4a7bb9550f1e7b34178ac65efd17890629576f2` |

The first three files are under app/src/main/java/at/heinz/deviceai; the last
three are under app/src/test/java/at/heinz/deviceai.

The actual path is the installed Android TTS listener and voiceFrame → existing
AvatarDirector → directly attached ContinuumHologramView → AvatarPoseReducer.
The optional native renderer reads that pose and calls AvatarMorphMapper before
Filament setMorphWeights. stopVoiceAnimation, onPause and onDestroy retain their
existing cleanup paths. Home/Chat navigation preserves the same view and director.

The slot adapter docks that same surface only in Home. Additional fanout would
deliver twice in Home; fanout as the sole replacement could lose Chat delivery
while the slot adapter is detached. Preserve the existing direct binding.

## Current execution status

- Source/blob comparison and conflict-free merge: PASS.
- Fresh component and actual TTS/voiceFrame integration execution: NOT_RUN at
  this checkpoint. The old ephemeral SDK/cache is no longer present; an isolated
  local toolchain is being reconstructed with existing JDK/Gradle binaries.
- Existing source tests already cover Director → visible pose and repeated
  Home/Chat navigation. The narrow remaining execution test should call the
  actually installed TTS listener/voiceFrame, including stale callbacks and stop.
- Native Filament pixels, physical Fold7, audible speech and reference visual
  acceptance: NOT_RUN / no device attached.

## Scope, compatibility and rollback

EXP-07 independent verification, EXP-11 existing orchestration and EXP-12 exact
checkpoint/rollback remain compatible. No second state machine, event authority,
avatar, dependency, model execution or Android permission is introduced. Existing
model/memory/bot work, original asset, signer and update channel are preserved.

Keep the existing Draft #495 open for this verification continuation, targeted
at #491. No main merge, release, installation or GitHub Actions execution.
Rollback consists of omitting the new tests/evidence; #491 already contains the
original fixes independently. Preserve all prior branches and evidence.

## Next bounded step

Run the 26 existing helper regressions and the real callback-to-view tests on
the exact published continuation tree. Record commands, exit codes and raw
results. If local dependency access blocks execution, retain the actual failure
and explicitly distinguish code connection from runtime/device acceptance.
