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

## #515 import

- Published source: `5373ec7c1399cb34fb8896f7318fdfa5437fa283`, verified through the GitHub connector.
- Read 15 production/test files at that exact commit and independently verified every decoded Git blob identity.
- Imported nine missing source/test files exactly. Four existing negative-ECHO files and the QA shell entry point already match the source.
- Kept the current QA test: it already covers the same signing exclusions and preserves additional workspace/log assertions.
- Kept the #523 optimized `development-backlog.mjs` byte-identical (`dfa1f4b762a9e45c81d47b3431ca35d013d85d9c`).
- The old local branch-bots directory is a partial dirty snapshot and was not used as an import source.

The offline CLI calls the existing BacklogStore and runTeamleaderLoop. Discovery records source/target commits and committed blobs/modes, and creates only PROPOSED / BRANCH_REVIEW tasks. A trusted asynchronous source preflight is awaited at every existing scheduler source boundary. The CLI uses the existing Termux environment resolver; the existing Java/Javac/Gradle host probes receive that selected environment explicitly. No dependency, network loop, model activation or execution grant is added.

Exact imported/preserved file identities are in `source-provenance.json`; the nine #516 identities are in `practice-source-provenance.json`.

## Actual integration verification

- Tested source commit: `d0398f4e023bc3e361de661df69d48f6d448709c`.
- Tested source tree: `f79f55af71edf10ba85b8500ccdcea864b4ce401`.
- Actual targeted Node run: **204 PASS, 0 FAIL, 0 SKIP**, across 13 test files. Duration: 8.472 seconds.
- Node `v24.22.1`, Git `2.52.0`. Full command and exact raw-log SHA-256: `node-tests.json`; raw output: `node-tests.tap`.
- Nine imported JavaScript files pass `node --check`; preserved QA shell passes `bash -n`; `git diff --check` passes. Exact commands and raw output: `static-checks.json` and `static-checks.log`.
- All 15 imported/preserved #515 code/test blobs and the optimized backlog blob rechecked. All nine #516 code/test blobs rechecked.

The suite includes branch discovery, process-level CLI, source preflight, scheduler, canonical backlog (including the existing single-pass optimization), CCC1 assignment/reporting, negative ECHO, service, mobile-builder fixtures and QA/host preflight. It uses disposable repositories/subprocess/tool fixtures; it is not an Android/device/model execution result. No additional dependency or actual Gradle invocation occurred in this worker.

Two setup runs are retained rather than counted as passes; their original TAP bytes are losslessly compressed as `.tap.gz` (the recorded SHA-256 applies to decompressed bytes). `node-tests-initial.*` records 186 passes and two module-load failures because the existing `tools/genesis-workspaces/adapter-bridge.mjs` was outside the sparse checkout. The tracked support directory was materialized. `node-tests-temp-overlap.*` records 197 passes and seven existing service-test failures because the worker selected a temporary directory inside the source checkout, correctly rejected by the service isolation gate. Repeating the same command with disposable temporary files outside all source checkouts passed all 204 cases. No product change or assertion relaxation was made between these runs.

## Parent integration

1. Import `c79486eb49d2ca5273956a161c6e3732ddfd5672` (#516 classes/roster/tests).
2. Import `d0398f4e023bc3e361de661df69d48f6d448709c` (#515 missing connections/provenance).
3. Import this evidence-only follow-up.

Parent owns Activity integration, real Android checks, shared integration evidence and remote publication. No Activity edit, full clone, heavyweight Gradle, external message, Actions run, main merge, signing or installation was performed by this worker.
