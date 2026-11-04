# Avatar alive — implementation checkpoint

Status: HOST_UNIT_UI_LINT_APK_PASS / REFERENCE_IMPLEMENTATION_PARTIAL / FOLD7_NOT_RUN. Owner instruction on 2026-11-04: animate the approved corrected Continuum avatar through the existing application path.

**Latest clean-source gate:** GitHub `d2eaa6d8ff8b2bdc4ed277a9987e8efa7c40d39b`, exact tree `4aa6b68d74901eb839b877cc66807ca57a34f835`. Full unit suite: **2,620 passed, 19 skipped, 0 failures/errors** (2,639 total). Selected Android UI suite: **40/40 passed**. Lint: **0 issues**. Debug APK built and its approved GLB/NOTICE verified byte-for-byte. Evidence: `avatar-alive-20261104/final/`. Earlier pending/failed checkpoints below are retained as history and are superseded for these listed host gates. Full video equivalence, native Fold7 rendering and audible synchronization remain open.

## Pinned starting point

- Official parent: PR #521, `2350866904cb82484148c0c8c63416fa51034269`.
- Local execution twin: `a6f0b09bf53229ef40f039e0418e811852d32a11`, tree `c26e1f73d49901c7e9beb6f5fe8b639e5736702a`.
- Owner-reviewed head candidate: SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`. Its local corrected GLB, NOTICE and runtime hash must be imported together and verified before promotion.
- Own branch: `codex/continuum-avatar-alive-20261104`; original worktree remains untouched.

## Bounded implementation

1. Preserve the reviewed head, the existing 21 morph targets and the single Director/View/frame clock.
2. Reproduce and import the existing negative-cycle sampler correction from PR #520.
3. Correct display-camera eye contact in the current native mapper/renderer without replacing owner-controlled hardware gaze input.
4. Connect a subtle resting smile through the existing state/expression path; preserve neutral speech, blocked/approval states, articulation and STOP behavior.
5. Verify real source-bound Director → reducer → mapper behavior, current asset provenance, Android compilation and available lifecycle tests.
6. Render a short sequence from actual compiled production poses and the corrected GLB. Mark synthetic input, host rendering and device limits explicitly.

## Design decisions

Use existing Kotlin/Filament components and packaged local model. The alternative of independent animation clocks or generated video would not verify the actual app path. Default facial warmth is cosmetic, not a claim of inferred emotion. Speak/approval/error handling takes priority over decorative expression. No new cloud service, permissions, microphone/camera activation or persistent worker.

## Initial verification boundary

At the initial checkpoint, baseline tests, new failing cases, fixes, Android build and rendered sequence were pending. The later dated results below supersede that initial status. No parent tests are claimed as current-source execution. Native Fold7 pixels, audible synchronization and performance require actual device evidence.

No main merge, release, installation, paid API, model training or APK signature change. EXP-12 identity continuity and existing avatar contract remain applicable. Rollback omits this branch; previous branches and owner files remain intact.

## Functional checkpoint

- Approved head is packaged as SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; runtime pin and NOTICE agree. Existing geometry, rig and 21 targets are retained.
- `AvatarDirector` emits WARM only for IDLE. The reducer suppresses it during admitted speech, even before runtime flags catch up. The mapper uses a restrained 0.18 Smile weight only on an idle, closed, silent mouth. The 2D fallback keeps its prior neutral appearance.
- `FilamentAvatarRenderer` passes the validated portrait azimuth into its mapper. Owner gaze now points toward the existing -22-degree camera; framing is preserved. Canonical zero-camera callers retain their existing gaze contract.
- Imported the signed-zero/full-negative-cycle correction from #520, source commit `9094032b05257ef12f882e37cefd7dd8f7ebc203`, production blob `31801578f47f6a677c58283a408bc2840297de6d`.
- Baseline: 86 JUnit + 26 motion checks passed. New behavioral cases then reproduced 5 failures: two negative-time crashes, two camera-relative gaze failures, missing idle smile. The constructor parameter was introduced without behavior for this red run.
- First functional host gate: **101 JUnit tests + 26 motion checks passed**, including actual PCM envelope -> Director -> reducer -> mapper, vowel-to-bilabial closure, interruption and stale callback rejection.
- Raw baseline/red/green outputs: `avatar-alive-20261104/`. Synthetic test PCM is not evidence of audible device synchronization.
- `ExportAvatarAlive.kt` uses the actual audio signal, Director, reducer and mapper to export a 12-second synthetic runtime sequence. The Blender replay checks canonical control matrices and skinned vertices before rendering. Video/render and Android gates are pending at this checkpoint.
- Native Fold7 visual/lifecycle/performance, audible TTS and owner-camera checks remain OPEN. The earlier rear-head texture/protrusion findings are not repaired by this motion change.

## Verified host milestone and expanded owner scope

Functional GitHub commit: `0a79c703be62f4ade6737eaa9d13fea1dc35c5bb`; exact source tree `5e3bf0670aa6f463fdc785cabfdc8fc0b25f5266`.

- Android Kotlin/Java compilation and debug APK packaging completed successfully. Lint: 0 errors, 0 warnings. The APK contains the exact approved `c8b88385…b9a` GLB and matching NOTICE. No device was attached; no APK was installed.
- Full Android unit suite: 2,633 tests, 1 failure, 19 skipped; 2,613 passed. All 101 avatar/audio tests passed. The sole failure is the pre-existing landscape selector height of 47.999996 dp versus an exact 48 dp assertion. The unchanged initial tree independently reproduces the same failure (4 layout tests, 1 failure); relevant source/test blobs are identical. Raw logs and comparison are preserved.
- All 192 actual production-pose frames rendered successfully at 16 fps, 384×512, over 12 seconds. Maximum canonical control-matrix error: 3.6881e-7; maximum skinned face position error: 6.6009e-8 m. The encoded H.264 review is 384×560 with status captions, 192 frames, 12 seconds, no audio.
- Review-video SHA-256: `bf20f48a0c1d97b784a036f1c902a902c47ffe5fecb215f7ee0faf6fdaed2f1d`. This is a synthetic-input host milestone, not acceptance against the owner's videos and not native-device evidence.

The owner subsequently made the original Drive videos, including four additional avatar clips, binding functional and motion references and authorized continued multi-milestone work while unavailable. All accessible files in the identified Jarvis Video folder are being inspected. Match/activation/workflow claims remain OPEN until the reference-to-source comparison is complete. Private original media and Drive identifiers stay outside Git.

## Measured-silence checkpoint

- Added an utterance-bound nullable PCM read: `null` means no measured PCM, while `0` means known silence or the end of known audio. The Activity passes this distinction through its existing frame callback.
- Four new failing behavioral cases reproduced false motion after silent PCM, exhausted PCM, or expired audio with a queued vowel. Current result: **107 JUnit tests and 26 motion checks passed**. Missing-PCM fallback and valid audio resumption remain functional; stale utterances cannot claim current PCM.
- The reducer suppresses queued mouth shapes during measured silence without discarding still-current word cues. Invalid future audio closes the mouth until a fresh valid sample arrives.
- Two ordering tests were adapted to the strengthened silence contract: the clock-rejection fixture now keeps its vowel within live audio, preserving its ability to detect a clock rewind; the invalid-future case now expects a closed mouth, then still verifies resumption on valid audio. No assertions were removed.
- Landscape controls now start on integer dp coordinates, correcting the independently reproduced 47.999996-dp target height. The unchanged layout regression test still requires every control to be at least 48 dp. Android revalidation is pending this source checkpoint.
- A disposable API30 emulator was configured, but its own startup check failed due to insufficient disk space (about 1.2 GiB free); `/dev/kvm` is also unavailable. No install occurred. `emulator-unavailable.txt` preserves the failure.
- All ten original Drive files were downloaded and hashed. The five avatar clips (original plus four additions) were sampled at 0.5-second intervals; all sampled sheets were visually inspected. The other five feature/tutorial videos were visually sampled at 5 or 20 seconds. Audio audition/transcription of the new clips was NOT_RUN. See the reference matrix for exact scope and gaps.

## Head effects and lifecycle checkpoint — validation in progress

- Source after measured-silence commit `d65b5b23fff6aa19c80413ac80095daa9f387266`. The full 2,639-test host unit run passed all 107 avatar/audio cases but found one layout overlap introduced by rounding the top bar upward. Lint and debug packaging passed. `silence-fullgate-first.*` preserves this failed overall gate. The header now rounds downward; the 48-dp selector correction remains. The four unchanged layout tests then passed.
- `CockpitEntered` and confirmed, nonempty `DocumentLearned` events now enter a bounded head-icon overlay through the existing adapter and View. The overlay is drawn after child composition, including the existing native TextureView, and uses the existing frame schedule. It rejects stale, future and repeated timestamps, expires after 1.95 seconds, and is cleared by pause/detach/reduced motion. No new View, scheduler, permission, cloud call or media asset.
- Initial effect regression: 4 failures in 5 new pixel tests before wiring. After wiring, all 5 passed; the existing icon preview and all 4 layout tests also passed. A first test harness attempt was interrupted because Robolectric's automatic Choreographer advancement kept generating frames; the corrected native-Canvas fixture uses an explicitly paused Choreographer and bounded 16-ms steps. This was a fixture correction, not a phone-performance finding.
- A real TTS callback test now distinguishes measured zero/exhausted PCM from an engine without PCM. The no-PCM fallback passed. Repeating the Activity fixture exposed a foreign-parent failure before the second silence assertion. Three focused regression cases reproduce (1) sequential Activity startup, (2) overlapping Activity ownership, and (3) unrelated work cancellation erasing a document effect. All three failed before the follow-up correction; exact file hashes and logs are in `lifecycle-red.*`.
- Follow-up source: Home filters live adapters by actual parent, removes pending docking callbacks on detach, and a rejected foreign attach cannot clear the current adapter. Activity destruction unregisters only its own live surface. Document arrival lifetime is separate from owner-work scope cleanup. The existing single-surface ownership requirement remains enforced.
- The regular already-enabled local voice greeting now uses the existing cockpit greeting text. It still runs through the existing initialized TTS listener, offline voice selection, readiness and foreground gates.
- **Current follow-up UI/full-unit/lint/APK gates: NOT_RUN at this source checkpoint.** Run those next before treating these new lifecycle changes as verified. Experiment boundary: EXP-12 identity continuity; no memory/model promotion. Rollback: omit this draft branch; no main/release/signing/device change.

## Isolated lifecycle verification and bounded host fixtures

Production source: GitHub `6cab1b9add227c2e0e615191210014d445040567`, local execution twin `04db6204e5de4fd7cc805b49fac02df738704564`, exact tree `0cace1f535c6b9cbd3084c085ab58e6309914e13`.

- **13/13 host UI cases passed**: six visible head-effect cases, six real Activity TTS callback/lifecycle cases, and a rejected foreign-host attach preserving its existing surface. The previously failing sequential/overlapping Activity and unrelated-task cleanup cases now pass. Logs and suite counts: `lifecycle-isolated.*`.
- A broader UI run was stopped after about six minutes without a completed result; it is `INTERRUPTED`, not PASS. A second run with per-test progress and a 75-second task bound identified `JarvisAvatarSlotUiTest.registeredRendererIsDockedFedAndDetached` as blocking. It timed out with no completed test; `shell-clock-diagnostic.*` records that result.
- The existing native-Canvas shell/slot fixtures now pause the Robolectric Choreographer before Activity setup and advance frames only through their bounded idle calls. The slot fixture explicitly selects the host 2D renderer, matching the other Canvas fixtures. No production frame policy or existing assertion is changed. `bounded-ui.init.gradle` makes further diagnostics bounded and reproducible.
- **Broader UI, full unit, lint and APK revalidation remain pending at this test-fixture checkpoint.** No Fold7, native Filament, audible speech or performance result is implied by host Canvas.

The bounded combined UI run then completed: **36 passed, 4 failed during native fixture window setup**. All speech/range, reattachment, navigation, sizing, head-effect and layout assertions passed. The four setup failures were `ActivityController.windowFocusChanged` receiving a missing ViewRoot after the clock was paused. Advancing the controlled clock 40 ms between `visible()` and `windowFocusChanged(true)` allows the actual simulated window to attach. The unchanged slot/phone/fold/tablet assertions then all passed (**4/4**). `ui-clock-first.*` and `shell-window-green.*` preserve both outcomes. No production scheduling was changed to make these fixtures pass. The final clean-source combined gate follows this checkpoint.

## Final verified host checkpoint

All following gates ran on a clean worktree at local commit `5a85676afdc44524306a982dba79659e4c029117`, byte-identical tree `4aa6b68d74901eb839b877cc66807ca57a34f835` to official GitHub commit `d2eaa6d8ff8b2bdc4ed277a9987e8efa7c40d39b`. Final evidence-only additions do not change this tested production or test source.

| Gate | Actual result | Evidence |
|---|---|---|
| Full Android unit suite | 2,639 total; 2,620 passed; 19 skipped; 0 failures/errors | `final/full.json`, `final/full.txt` |
| Avatar/audio subset within full suite | 107 tests, 0 failures/errors | Per-suite entries in `final/full.json` |
| Selected Android UI/Canvas suite | 40 passed; 0 skipped/failures/errors | `final/ui.json`, `final/ui.txt` |
| Lint | 0 issues | `final/lint.xml` |
| Debug APK | Build PASS; 89,752,346 bytes; no installation | `final/package.json` |
| Approved asset and NOTICE | Packaged bytes equal repository bytes; GLB runtime pin agrees | `final/package.json` |
| Device availability | 0 devices listed; no native/device execution | `final/device-availability.json` |

The UI gate includes four layout cases that also belong to the ordinary unit suite; do not add the two totals as unique tests. Toolchain: JDK17, Gradle8.11.1, Kotlin2.4.0, Android compile SDK36/build tools35.0.0; Robolectric4.14.1/SDK28 for UI cases. Raw log headers preserve exact task arguments and source identities. Existing compiler/plugin deprecation warnings are separate from the clean lint result.

APK SHA-256: `f79a049992a76e0c2d9f80c1c57674a603bb6db91121448b11d034e63dbf47ee`. This is the existing default **debug QA** package `at.heinz.deviceai`, versionCode4/versionName0.3.1, not the owner's `Continuum Lokal` release/update. No original signing key, installed package or release metadata was changed. Approved GLB remains `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`, 12,038,416 bytes.

### Remaining acceptance and next entry point

1. Keep the reviewed head identity and original orb. Review this draft on parent #521; do not merge main or install/sign an owner update automatically.
2. Obtain native device evidence for the exact integrated source: actual Filament pixels, speech audio/STOP, navigation/fold transitions, thermal/performance and permitted camera behavior. Current ADB has no device; the disposable emulator failed its disk gate. Host Canvas and Blender are not substitutes.
3. Continue the reference matrix: exact phoneme timing/audio audition, depth/shrink startup cues, full-body/orb scene, general document intake and actionable answer choices, real account/weather/calendar sources. The connected head burst closes one concrete gap; it does not implement all ten videos.
4. Inspect the existing rear texture/stretch and nape findings before claiming all-view likeness. Do not silently replace the owner's approved face to hide them.

The 12-second silent pose replay remains a separately pinned earlier milestone at source `0a79c703be62f4ade6737eaa9d13fea1dc35c5bb`, not a recording of the final Android build. Its input poses, asset, replay errors and video hash are preserved in `video-provenance.json` and `render-summary.json`.
