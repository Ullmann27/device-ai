# Wave 2: connect completed avatar motion and speech guards

Date: 2026-11-04. Base: `e5c06e6bf657db556bc4861c7f46a55ebcd6e03a`.
Own branch: `integration/wave2-avatar-20261104`.
Scope: existing native presentation and speech-event connection only.

## Exact upstream provenance

- #502: `6cb9e760f0a16a6f34a8442abf9b79dcf2705799`.
  Adopted `avatar3d/AvatarIdleMotion.kt`, its mapper call site and the existing
  `tools/fable-avatar-motion-check` host checker.
- #503: `2582be86c121d7f6d1179c7edc49e979bee8ba9c`, directly based on #502.
  Adopted `avatar/AvatarContract.kt`, `avatar/AvatarDirector.kt`, the speech
  changes in `avatar3d/AvatarMorphMapper.kt`, and all seven changed/new JUnit files.
- All four production files were byte-identical to the pinned #503 snapshot
  before the small integration repairs below. Verified Git blobs, in that order:
  IdleMotion `c60d0e180d2d3393b2db355b188a61dcba42f79e`;
  MorphMapper `d1961d03d43652ab52deb0a2676c4de8dc285889`;
  Contract `156f4de1cb935ef7ada9e131ae7e3796ca5cf218`;
  Director `c6873d90c761019b40ce7b26c56c10f057061e2c`.

This is a selective source integration. No foreign branch was reset, no asset
was replaced, and no historical upstream test receipt is presented as our run.

## Missing connections repaired

### One admission result for speech events and PCM

`AvatarDirector.speechStarted` and `wordRange` now return whether the real
callback was admitted. Duplicate, retired, foreign, invalid or backward events
return false. The Activity integration owner must call the Director first and
only then update the PCM anchor/start state when true. Sink failures remain
isolated and do not turn an accepted engine event into a rejected event.

An ordered punctuation/whitespace range is a valid engine callback. It updates
the text/frame watermarks and returns true without creating visemes or extending
the previous word's fallback-motion interval. Equal audio markers remain valid
when text ranges advance.

`AgentActivity.kt` and its independent caller test belong to the parent
integration task and are intentionally not changed by this commit. The Boolean
return values alone do not establish that the caller already consumes them.

### Reattach live speech without replaying callbacks

The actual view clears its reducer's transient speech when reparented. Upstream
`attach` restored only SpeechStarted; the new duplicate-range guard then rightly
rejected the old word, leaving the new surface without its still-live cues.

The Director now retains at most 64 cues from the last accepted word. On attach
it restores only still-live/upcoming cues with their original timestamps and
durations. It never restarts the word. New utterances, accepted punctuation and
matching SpeechEnded clear this cache. An attach whose clock precedes the word
start clears the cache instead of rescheduling that word in a different epoch.
The reducer rejects identical cues before changing its queue or default clock,
so repeated attach calls cannot multiply the queued word.

The real #495 reparent test sends the same word again after navigation and waits
past its original 320 ms lifetime. That expectation relies on the removed replay
behavior. The parent integration must instead test the still-valid word before
expiry or deliver a genuine next range. The guards must remain intact.

## Intentional inherited expectation changes

- `AvatarSpeechOrderingRegressionTest`: a 50 ms second sample now yields
  0.21990544 rather than 0.256 under the inherited time-based 33 ms smoothing.
- `AvatarDirectorTest`: saturation fixtures use actual 33 ms sample intervals.
- `AvatarMorphTransitionTest`: an active PP cue closes outgoing vowels and jaw
  immediately. Equal render timestamps preserve a blend; clock rollback resets it.
- `Avatar3dContractTest`: a fresh mapper isolates each name-binding assertion.
- The #502 host checker had two stale assertions after #503 integration: PP
  opening and snap-on-equal-time. These were updated to the same inherited
  policies; the remaining 24 host assertions were retained.

## Actual local checks

Direct Kotlin 2.4.0 compilation, JDK 17, JUnit 4.13.2 and org.json 20240303 from
the existing local toolchain/cache. No network downloads, Gradle cache copies,
Android emulation or production-class stubs were used. All avatar/avatar3d
JUnit sources ran against the real production classes and packaged GLB.

| Stage | Actual observed result |
| --- | --- |
| Exact imported #502/#503 production + upstream JUnit | 74 passed, 0 failures |
| Imported #502 host checker + #503 speech semantics | 24 passed, 2 stale expectation failures |
| Six added reattach tests before the connection repair | 80 JUnit total; 77 passed, 3 assertion failures |
| Final integrated sources + five admission tests | 85 JUnit passed, 0 failures |
| Updated combined motion checker on final sources | 26 checks passed, 0 failures |
| `git diff --check` | passed |

Raw logs, actual command arrays and final source digests are in
`docs/evidence/wave2-avatar-20261104/`. These checks were made in the worktree
based on the base SHA above; the final source manifest identifies the exact
compiled source bytes. Full Gradle/Android compilation and real Activity caller
tests are separately owned by the parent integration task.

No new test run proves native Filament GPU behavior, actual acoustic timing,
Fold7 open/closed layout, visible face quality or APK/device acceptance. Those
remain NOT_RUN here. The existing asset/visual acceptance limitations remain.

Two independent sibling agents reviewed the final Director/Reducer and the
new tests read-only. Neither reported a blocking finding. They did not execute
tests or inspect the device; their review is additional source review only.

## Safety, experiments and handoff

Presentation-only integration; no EXP-00/01/02/05/08/11/12 capability promotion,
new pipeline, model, memory, owner-consent or offline-policy change. Existing
worker/CCC1, package identity, signing, release/versionCode and main are unchanged.
No #487 extra visual sources and no #495/#505 duplicate implementation.

Next: consume Boolean admission in the real Activity callback before changing
PCM state, update the reparent fixture as above, then run the combined exact
commit through the parent-owned Gradle/SDK test gate. Independently review
time-bounded reattachment and punctuation admission.

Rollback: revert this isolated integration commit before dependent Activity
Boolean-callsite changes, or revert both together. Do not partially replace the
mapper without its IdleMotion class or mix obsolete PP/timestamp assertions.

Remote status at handoff: LOCAL_ONLY / NOT_PUSHED by this child. The parent
owns the single shared integration PR and any authorized remote publication.
