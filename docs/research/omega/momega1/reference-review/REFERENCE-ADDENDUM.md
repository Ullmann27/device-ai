# Additional references and observed-gaze contract

This extends the original acceptance freeze without replacing it. The reviewer
sampled both newly supplied 20.032-second reference videos at twelve time-spanning
keyframes each. The first half repeats the primary head/dashboard sequence; the
second half adds the following visual ideas. Audio listening remains NOT_RUN.

| Reference | Full-file SHA-256 | Additional observed visuals |
|---|---|---|
| gemini_generated_video_f6e57d6b.mp4 | c73ce6cf204fe2c29cc572da80514c7a2c8657a411c049255a476647bfe7c878 | Documents/folders stream through a spatial workspace; small assistant figures appear; light trails connect a central figure and worker panels |
| gemini_generated_video_3d63b568.mp4 | 7bd81c97a8c6d1a8e84b776461bbb454dcb4368777c6ecf97a90c1be7d10ea1d | Mail/document intake, PDF panels, contextual action choices around the head, then weather/calendar/resource panels |

Both files are 1280×720 with audio streams. Hashes were independently checked
against the actual local files. Private media, frame sheets and Drive identifiers
are not committed. Keyframe sampling is not complete audiovisual playback.

## Additional effect acceptance, before implementation review

The existing colors, fonts, buttons and layout remain authoritative. These clips
extend the motion/interaction reference; they do not authorize a replacement
dashboard, copied values, permission changes or external service connections.

| ID | Frozen requirement | Evidence needed |
|---|---|---|
| E01 | Document arrival has a real origin | An actual existing attachment/retrieval/tool event produces one bounded, identified cue; no documents appear solely because a timer or idle animation ran |
| E02 | Worker figures represent real workers | Each semantic worker/count/status comes from the existing live task/worker state; decorative sparks/rings may be cosmetic but cannot claim jobs or results |
| E03 | Progress and completion are truthful | Awaiting approval, execution, verification, failure and completion use actual lifecycle signals; animation completion never marks the task successful |
| E04 | Cue replay is bounded | Duplicate callback/event identifiers do not manufacture repeated arrivals; cancellation, replacement, pause and stale events cannot replay old work after resume |
| E05 | Effects obey resource and motion limits | Finite positions, bounded queue/particle counts and lifetimes; reduced-motion mode remains static and meaningful; no renderer-owned worker/scheduler |
| E06 | Contextual action choices remain authorized | Every displayed action maps to an available existing action, scope and confirmation process; mail/PDF/calendar visuals cannot execute an unavailable service or silently grant permission |
| E07 | Status data has provenance | Resource panels use measured data or explicit unavailable state; weather/calendar/mail content uses a permitted actual source and its freshness; reference numbers never become production data |
| E08 | Visual comparison uses the real renderer | Inspect time-separated frames/short sequences for head, document/worker arrival and cancellation while holding our design constant; source assertions alone cannot pass visual similarity |

The clips are synthetic reference material. They prove neither autonomous workers,
email access, weather retrieval, PDF analysis nor the displayed system metrics.
No new online dependency or permission is inferred from their appearance.

## Observed-gaze test freeze

The UI author supplied the proposed contract before candidate source inspection:
`GazeObserved(gaze, confidence, atMillis)`, `GazeCleared(atMillis)`, an additive
`AvatarPose.gazeObserved=false`, and `AvatarDirector.onObservedGaze(gaze,
confidence, ageMillis)`. The reviewer has not read the candidate implementation.
Aggregate author test counts are not independent evidence.

Thirteen additional checks are frozen in `ObservedGazeChallengeTest.kt`:

1. Legacy poses do not claim observation.
2. Confidence 60 and 100 and age 0 and 750 ms are accepted in the director's clock.
3. Confidence 59/101 and age -1/751/Long.MAX_VALUE clear an earlier observation.
4. TTL is live at 750 ms and expires at 751 ms, restoring task gaze.
5. A future sample is not displayed as current.
6. Older out-of-order samples cannot replace the newest sample.
7. Clear watermark rejects earlier and equal-timestamp samples.
8. Task states override observation and returning idle cannot revive it.
9. Director accepts tracking only in IDLE/LISTENING/SPEAKING.
10. A valid observed look overrides cosmetic idle drift exactly.
11. Utterance-bound speech preserves the independently valid look while closing
    the mouth on interruption.
12. Nonfinite and out-of-range gaze cannot enter the contract.
13. The existing 460-second shared animation clock wraps without a visible
    head/eye transform jump (per-channel difference at 60 Hz ≤0.15 degrees).

Reflection makes absent additive APIs an explicit baseline failure while compiling
the real unmodified production sources. These are host contract tests, not camera
tracking, gaze accuracy, audio/visual synchronization, visual appearance or device
acceptance. Lifecycle UI hooks and any real camera adapter still require separate
source and runtime checks. Existing twelve mapper tests remain unchanged.
