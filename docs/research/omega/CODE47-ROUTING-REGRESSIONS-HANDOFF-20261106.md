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
