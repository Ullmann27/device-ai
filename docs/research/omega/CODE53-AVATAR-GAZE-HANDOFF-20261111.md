# Code53 avatar lifecycle clock repair

Base: `fd1de22cf3f6ed159601c9b944cc25c86f0fc221`.
Integration owner: main Code53 coordinator; target remains
`integration/continuum-final-visibility-20261111` / PR #747.

## Defect and change

`AgentActivity` creates its `AvatarDirector` with `SystemClock.elapsedRealtime`;
`ContinuumHologramView` samples the pose with that same clock. Three lifecycle
clear calls still used wall time: foreground loss, detachment and disabled
motion. The reducer retains a clear watermark and rejects observations at or
before it. A wall-time watermark therefore rejected later monotonic camera
observations indefinitely.

All three clear calls now use `SystemClock.elapsedRealtime`. No renderer,
avatar asset, microphone, camera permission, model or speech provider changes.
The existing `ContinuumAvatar3dLifecycleUiTest` now sends observations through
the real Director with the production monotonic clock. It checks initial gaze,
pause/resume, motion disable/re-enable, detach/reattach and stale-sample rejection.
The former wall-time polling workaround is removed.

## Executed verification

- Source-derived clock replay against the real `AvatarPoseReducer`: old calls
  reject fresh observations at all three boundaries; corrected calls accept
  fresh observations at all three and still reject stale observations. Synthetic
  monotonic input 10,000 ms; fresh input 10,001 ms. This is an isolated mechanism
  check, **not** execution of the Android View lifecycle.
- Kotlin 2.2.21 / JDK21 / JUnit4.13.2: **76 PASS**, covering TTS PCM signal,
  speech admission/boundaries/order/clock/reattachment, Director/text fallback,
  render-quality thermal policy and essential avatar motion. These unchanged
  surrounding contracts do not substitute for the changed Android lifecycle test.
- `git diff --check`: PASS.
- Local Android attempt did not execute tests: existing task allowlist rejects
  CLI `--tests` arguments; cached Robolectric/Filament and full SDK are absent.
  No SDK recovery loop or safety-gate change was attempted.

Required hosted class: `at.heinz.deviceai.ContinuumAvatar3dLifecycleUiTest`.
Also retain `AvatarTtsLiveConnectionUiTest`, `AvatarSpeechReattachmentUiTest`
and `JarvisLiveAvatarWiringUiTest` as speech-to-visible-surface checks.

## Boundaries and continuation

Status: PORTIERT after main-coordinator integration; hosted Android validation
pending; Fold7/real camera/audio/Filament **NOT_RUN**. The existing Maya route
shares the utterance listener, PCM envelope, Director and visible avatar slot;
it measures amplitude and does not provide phoneme timestamps.

The root's separate `JarvisWorkWindowHost` compiler correction was not duplicated
in this worktree. Run the combined frozen-source QA after integrating both.
Rollback only this scoped commit if needed; do not alter original assets,
signing, package identity or internet consent.
