# Team 3 — capabilities and speech result

Status: SOURCE_INTEGRATED locally, HOST_TESTED (42/42), Activity integration and physical-device validation NOT_RUN. Publication NOT_PUSHED from this lane; coordinator publishes under established authorization. Base `6ae107d43e40ea9c87b0461400c9ad0156e84585`, branch `fix/continuum-bot03-capabilities-20261104`.

## Scope and interfaces
EXP-05 honest local runtime presentation, EXP-11 canonical dashboard/backlog/bot state, EXP-12 stale callback/lifecycle continuity. AgentActivity is coordinator-owned and untouched in this branch. No inference, model-service launch, permission grant, memory change, S01 barrier modification or owner release was performed.

`OwnerCapabilitySnapshot` accepts public operational values only: current model state/name, nullable imported count, microphone/service state, internet policy, canonical backlog counters, builder-pending flag, bot-selected/turn-active booleans. It does not accept bot identities, notes, conversations, project titles or memories. `OwnerCapabilityReply.withDashboard` reuses existing canonical collector output with a 15-second monotonic freshness bound (timestamp captured before collection is queued, conservatively including callback delay); it never invokes the collector, whose collect method records metric history. Expired/failed/unobserved telemetry is unknown, not zero. Current runtime flags override older dashboard model presentation. Readiness is distinguished from import, answer quality, host results and device build proof. Exact routing delegates to the existing OwnerChatIntentRouter; ordinary/compound/tutor input remains unchanged.

`LocalSpeechSession` is the production recognizer lifecycle used by AndroidOnDeviceSpeechInput, with a small injectable platform seam for actual behavioral tests. It releases recognizer and timer exactly once, rejects replaced/cancelled callbacks, rechecks microphone permission before delivering text, reports empty result as no-match, and never retries. Reentrant owner callbacks cannot revive the outer start or deliver a transcript after cancellation/replacement. Android error constants are mapped at the adapter boundary to distinct actionable German no-input, no-match, permission, language, busy, audio, network and service failures. Explicit cancellation stays silent rather than becoming a service failure. Audio/transcripts are never retained in session state.

## Precise coordinator wiring
Apply `TEAM-CAPABILITIES-ACTIVITY.patch` with `git apply` in the integration checkout, then compile/test the combined source. Patch adds a cached canonical dashboard value + monotonic timestamp, and nullable imported count updated by the two existing catalog callbacks. `showCurrentAppCapabilities` reads current immutable runtime/public flags and emits labelled UI bubbles directly; it avoids existing addUserMessage learning/radar side effects and addNonModelReply context clearing. Exact CAPABILITIES are handled before busy/bot routing, so they never seed the bot store or call inference, including when a private bot is selected. The Send click handler is deliberately unchanged: when functioning as Stop it still cancels, while a real submitted status can inspect a busy lease. Greeting and all ordinary-message paths are preserved. Speech error UI gets full session/generation/dialog/lifecycle checks.

Activity regression source: `app/src/uiTest/java/at/heinz/deviceai/ContinuumLiveCapabilitiesUiTest.kt`, adapted from the recovered old untracked test (read-only recovery from `/workspace/scratch/952001316bdc/continuum-work/capabilities`). Three tests exercise real composer EMPTY/LOADING/READY, busy private bot status without seed/model/cancel, and existing Send-as-Stop behavior. NOT_RUN here until coordinator integrates the patch.

## Executed verification and retained failures
Real Kotlin compiler/JDK17 compilation against Android API36 succeeded. Real JUnit 4.13.2: 42 tests, 0 failures. Test suite includes 18 lifecycle/callback tests, 10 capability/privacy/read-only tests, 3 Android error mapping tests and 11 existing routing/dictation tests. No source-grep or fake inference was used as execution evidence.

Initial 40-case execution had 5 failures: terminal accept consumed the token before cleanup/delivery, and reentrant replacement revived an outer start. Raw failure retained in `capabilities-evidence/initial-failure.txt`; corrected 40-case output in `capabilities-evidence/focused-pass.txt`, final extended 42-case output and source hashes in `capabilities-evidence/final-42-pass.txt` / `final-source-hashes.txt`. Cancellation invoked from terminal state callback is separately verified to suppress text delivery. Two final tests cover reentrant preflight replacement and service-unavailable precedence over a missing permission. Committed runner independently executed successfully; final compile/test exit codes are both 0.

Reproduce focused host gate (no network, sensors, model or service):

```bash
bash tools/capabilities-speech-review/run.sh /workspace/scratch/391c0f6de827/build-env /absolute/fresh/evidence-directory
```

The runner records source hashes, git SHA, toolchain, compile/test logs and actual exit codes. Full Android Gradle compilation/lint, actual Activity Robolectric caller tests, recognizer-device/language tests, APK and Fold7 verification remain NOT_RUN by this lane. Host tests prove adapter/policy behavior, not actual microphone or phone model performance.

## Publication and continuation
Local start checkpoint `94b50f1`. Initial git push was rejected by automatic approval review because this subtask transcript did not establish trusted private destination/explicit disclosure authorization to Ullmann27/device-ai. No alternate transport was attempted. Coordinator was informed and handles established publication; this lane does not claim a remote commit or draft PR until confirmed.

Next: coordinator applies exact bounded Activity patch, runs the three real UI tests and combined compile/lint gates, reviews current app status on device without replaying consumed failed S01. Unknown bot registry total is intentionally not guessed or populated by a status request; current selected/active booleans are read directly and no private bot names are emitted. Missing device speech service/language remains an actionable unavailable state.

Rollback: revert the lane and Activity patch on the integration branch; preserve owner data, original signature and original S01 failure evidence. No device operation or new release occurred in this lane.
