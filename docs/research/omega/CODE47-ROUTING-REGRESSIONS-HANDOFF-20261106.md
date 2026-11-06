# Code47 routing and STOP regression handoff

Base: c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e. Own branch: codex/continuum47-routing-regressions-20261106. Draft PR583 targets codex/continuum-code47-qa-candidate-20261106. Root checkout remains frozen.

## Verified diagnosis

- Bot routing fixture skipped onCreate, leaving localRuntime uninitialized. Normal onCreate binds LocalModelHost before buildUi. The narrow fixture now binds that real EMPTY runtime and retains no loaded-model, no inference-profile, no orchestrator and no bot-mutation checks. No production workaround for an incomplete fixture.
- Two old Node contracts rejected the added currentToolScene guard and included following AppLookup planning in the STORAGE slice. Updated contracts require all three CHAT guards, bounded STORAGE, its native probe and immediate return.
- Real DocumentFlow STOP failure: journal ABORTED, reading ended, window closed, cockpit restored, but the fixed owner STOP confirmation was missing. The delivery guard correctly rejects canceled results before the old onFailure confirmation.
- A same-session owner request could also be lost from model context at this early return. The current cancellation-token check is retained; only a same-session central, non-screen owner request is recorded as unanswered. No model text, speech or experience is delivered.

## Current production correction — WIP / UI NOT_RUN

The fixed STOP confirmation moves synchronously to cancelLocalRequest for its captured current-session DocumentDraftTurn. The old request-only stoppedDraftRequest marker is removed, preventing later genuine failures from inheriting an old STOP reason. The live draft now captures its session; recall-only draft presentations remain compatible through the existing overloaded constructor. The owner delivery guard is unchanged. Existing STOP, late-native-answer and queued-answer UI cases additionally require exactly one immediate STOP message.

## Executed checks

- Original targeted Node contracts: 7 tests, 4 pass, 3 fail, exit1.
- Updated Node contracts against frozen c3d2 production: 7 tests, 7 pass, exit0.
- Nine negative source mutations: all nine detected (remove each of three guards in each message method; remove storage probe; insert model route; remove storage return).
- First targeted UI compilation: blocked only by the internal CockpitHostFramePump test helper missing from that isolated compilation. Recompile with its actual test source planned. No stub or product workaround.
- Root combined Android run supplied real BotRouting and DocumentFlow RED results. Revised UI runtime checks remain NOT_RUN at this checkpoint. No additional Gradle launched.

## Next / boundaries

Compile the exact changed Activity and real UI test sources using the SDK classpath and bounded heap, then run STOP/late-answer/context isolation cases in a separate Robolectric temp directory. Root owns final combined SDK/UI/lint and release. No renderer, gate, app-query, package, signing or model-weight changes. EXP-05/EXP-12 contracts preserved. Rollback: revert this isolated patch; no data migration.

## Follow-up checkpoint

The changed Activity compiled against the real SDK dependency/classpath with the original DeviceAI_app_debug module name (512 MiB heap). Revised UI tests compiled with their real CockpitHostFramePump source (256 MiB heap). Six actual Robolectric cases passed: BotRouting, synchronous/repeated document STOP, late native answer, already queued answer, unchanged owner correction retained once, and different-session exclusion. The tag-bound SystemStatus WorkWindow case also passed with indeterminate progress running before and after stale completion, then stopped/hidden on actual completion; gaze/return assertions remain.

The first screen-data negative fixture used a dotted package name in its natural request and entered the existing deterministic research route; its timeout is recorded, not treated as a STOP implementation failure. The revised input explicitly requests local/offline screen description; it still uses actual confirmation, ToolGate, AndroidDeviceAppActions, AccessibilityService shadow and ScreenObservationBus. That focused check is running.

Additional owner-authorized PR578 reviews are pending their independent RED proofs: remove pre-recording of a ready Intake.Planning turn; use the latest verified owner identity in the mandatory fallback prompt. Root also owns the polite PDF parser and will supply the two existing Activity call-site updates after its RED/GREEN. No helper edits Activity concurrently.
