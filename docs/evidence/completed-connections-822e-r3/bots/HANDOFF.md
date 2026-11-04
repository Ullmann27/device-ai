# Completed bot connections — R3

## #516 import

- Integration base: `f4b0a403b4e51fc0b0df5d697345d6f94308af05`.
- Pinned source: `2cd6381f924019c044f3c265b461012b08082222`.
- Source tree: `80cc6628c5a69a851915621af4e2ebd2ec4fb80e`.
- Fetched exact commit from verified local source `/workspace/scratch/8f5df1fd155a/continuum-internal-bot-training`.
- Imported five production files and four new practice tests, byte-identical to the pinned source.
- `ChiefBotConversation.kt` and `ContinuumBotRosterDialog.kt` matched the source merge base before import. Existing C1 changes are preserved.
- `AgentActivity.kt` is owned and integrated separately by the parent; this commit does not touch it.

The imported classes connect Kora, Link and Iris private exercises through the existing conversation prepare/generate/complete/history path. Ordinary history retains V2 encoding; history with response-bound practice feedback uses the V3 reader/writer. Owner/private-history isolation and accepted terminal completion remain required.

## Verification

- `git diff --cached --check`: PASS.
- Imported blob verification against the pinned commit: PASS (nine files).
- Gradle/Android tests: **NOT_RUN in this worker**; the parent owns actual Android verification.
- Historical source handoff reports 70 delta JVM cases; that result is source evidence, not a new integration test run.
- No model inference or on-device learning result is claimed.

## Next bounded work

Import the missing completed #515 branch discovery/offline CLI and Termux diagnostic connections from exact published commit `5373ec7c1399cb34fb8896f7318fdfa5437fa283`, preserving existing C1 negative-ECHO fixes and #523 single-pass backlog optimization.
