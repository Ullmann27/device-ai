# PCM silence integration — support handoff

Status: HOST_JVM_VERIFIED / ANDROID_UI_NOT_RUN / NOT_RELEASED.
Date: 2026-11-05. Draft #562; APK coordination #559; avatar owner #545.

## Exact intake and bounded change

Baseline #544 c69199cda71c4d561c2f4721ff5d8c3a7eeefcac, tree db16767156bae99af8a41b3ef56e64b3670182ee. Local cdd9ed6b49b3da928f2b3f6cd8a4f8d2ef131b31 was verified to have this identical tree. Donor: #526 5a18f74f18d9b7d927f8c622052369496153829f.

Four selective production hunks connect:
TtsHologramSignal.measuredLevel(id, elapsedRealtime) → actual AgentActivity voiceFrame → AvatarDirector measuredPcmAvailable → AvatarPoseReducer source/level tracking → existing AvatarMorphMapper.

Known silence and expired measured audio now close jaw, viseme, weight and energy. Missing PCM retains the existing word-range fallback. Signal reads remain utterance-bound and synchronized; old level() callers stay compatible. Speech reset clears the new transient fields.

The older donor also contains incompatible WARM expression changes. Those were explicitly excluded. Current NEUTRAL contract, morph mapper, new camera consent/owner-face work, memory, original head/orb and all release settings remain preserved. EXP-05/06/11/12 interfaces remain compatible; no model promotion, additional listener, frame clock or external voice provider.

## Actual RED → GREEN evidence

Two unchanged test bodies from the donor (measured silence during a vowel; expired measured audio with a queued vowel) were compiled against the unmodified current production chain first. Both failed: expected closed jaw 0, observed jawPitch 9.749999. After the selective fix, the same two bodies passed.

The focused original Kotlin/JUnit suite then passed **85 tests / 0 failures**, across fourteen test classes. It covers the six donor silence cases, an end-to-end production-signal/director/reducer/morph test, old utterance rejection, interruption, temporal ordering, reattachment, PCM encoding, gaze and mouth coupling. These are real JVM executions, not source regex checks or simulated results. The two counterexamples overlap with the six donor cases; do not add them as distinct cases to 85.

- RED: compile exit 0; JUnit exit 1; 2 tests / 2 failures; 12.246 s.
- Counterexample GREEN: compile exit 0; JUnit exit 0; 2 tests / 0 failures; 10.584 s.
- Focused GREEN: compile exit 0; JUnit exit 0; 85 tests / 0 failures; 14.708 s.
- Input files unchanged through every execution; per-file hashes and complete logs archived.
- git diff --check: PASS.

Evidence: [RESULT.json](../../evidence/pcm-silence-3e26-20261105/RESULT.json), [runner](../../evidence/pcm-silence-3e26-20261105/run-pcm-host.py), [RED](../../evidence/pcm-silence-3e26-20261105/red/junit.log), [GREEN](../../evidence/pcm-silence-3e26-20261105/focused/junit.log).

## Reproduction

Use the existing pinned JDK17, Kotlin2.4.0, JUnit4.13.2, Hamcrest1.3 and org.json20240303 jars. The runner resolves exact versioned jars and records SHA256 for every production/test input and jar.

```sh
python docs/evidence/pcm-silence-3e26-20261105/run-pcm-host.py --repo . --toolchain /workspace/scratch/19c84cfe59f9/build-env/toolchain --cache /workspace/scratch/19c84cfe59f9/build-env/gradle-cache/caches/modules-2/files-2.1 --case focused --out /tmp/pcm-focused
```

For the counterexample run use --case baseline. It compiles the archived test fixture against whichever source is checked out. RED requires the exact baseline production files; GREEN uses this patch. Original receipts record exact full commands. Compiler and test JVM use one active processor and bounded memory. Shared cached jars are read only; no Gradle cache mutation.

## Tests integrated and limits

Reused AvatarMeasuredSilenceTest (six cases) and AvatarSpeechPipelineTest (one case). Updated two ordering-fixture expectations to match actual measured-audio expiry while preserving rejection/ordering assertions. Extended the existing AvatarTtsLiveConnectionUiTest with measured-silence/known-buffer-exhaustion and no-PCM fallback cases, preserving the newer camera-question preference.

The Android UI tests and full Activity compilation remain **NOT_RUN**. The shared Gradle build lease was busy; no competing Gradle process was launched. Seven actual pure production Kotlin files were compiled by the standalone runner. The Activity call-site change was source-reviewed only. Audible TTS, natural voice quality, automatic ASR turn-taking, identity recognition, camera/GPU/Fold7 and new APK acceptance are not established by these tests.

## Next entry and integration

#559 remains the single APK stabilization path. Consume these four small hunks on its actual final source; run AvatarTtsLiveConnectionUiTest and the staged current-stop/next-turn regression from #556 through the real Android host build. Then run the final combined gates before any original-signed update. No history/test result transfer is implied.

No shared worktree or branch was changed. No main merge, paid execution, key read, signing, deployment or install. Revert this branch's isolated repair commit to roll back; preserve raw RED/GREEN evidence.
