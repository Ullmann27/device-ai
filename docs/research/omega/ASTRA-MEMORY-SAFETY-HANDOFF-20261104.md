# Astra memory recovery safety — 2026-11-04

## Root integration update

The isolated-subtask record below is preserved as history. Root has now integrated the reviewed recovery, explicit dialog, atomic historical-import guard and genuine memory-event adapter into the existing Activity. The raw-preference Bootstrap answer/display bypasses were also closed on the existing I/O executor, with token/session/STOP safeguards. The completed common unit gate at `f14ce8e9` is **2416/0/19** (2397 passed); 21 targeted UI cases, including seven real Caller cases at test source `09e23507`, also passed on those complete app classes. See `ASTRA-JARVIS-TAKEOVER-20261104.md` and `docs/evidence/astra-takeover-20261104/final-qa.json` for current integrated status, remote snapshot and remaining Activity/device gates. The local-only / not-connected statements below describe this agent's earlier isolated checkpoint, not the final integration.

## Scope and status

Owner-authorized M2.10 repair on the existing Jarvis integration, covering EXP-06
(memory provenance) and EXP-12 (recovery without owner-consent corruption).
Base: `ad2fa70b` (application sources from `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`).
Branch: `codex/astra-memory-safety-20261104`.

Status: **RECOVERY, OWNER DIALOG AND HISTORICAL IMPORT GATES PASSED / NOT_PUSHED**. This local
checkpoint must not be reported as an integrated APK or a device result.
No existing test assertions were changed. A separate, not-yet-connected owner
review dialog is included. No Activity, provider, signing, update, S01, main,
release or remote settings were changed. Issue #41 was not refreshed
independently in this isolated subtask; the root agent owns remote review.

## Real RED evidence

Commit `d133168b7d67ee5c14ef4ee4da2246c6b346bb46` contains two new behavior tests
and the source-compatible recovery factory contract. The builder compiled its
Core/Experience overlay against the real compiled integration classes, then ran
JUnit: **2 tests / 2 expected failures**, both `Learning experience checksum
mismatch` at `activeOwnerFacts()`. This was executable RED, not a compiler error.
Builder artifacts: `direct-tests/memory-recovery-red/junit.log` and its
`provenance.json` in the session workspace. The detached
`device-ai-memory-red` worktree preserves that exact source.

## Implementation boundary

- Existing Core constructors and the default FileLearningExperienceStore keep
  strict integrity behavior. `ContinuumLearningCore.withAutomaticRecovery(...)`
  explicitly selects automatic recovery using the SAME store and OS lease.
- LEX2 and segmented **LEX3 version 1** remain canonical. No flat-LEX3/LEX4
  conversion from #371, no second memory database. Version-3 flat LEX3 and LEX4
  require a separate explicit migration; they are never silently downgraded.
- Verified surviving segments are preferred. Otherwise only a fully verified
  existing LEX2 backup is used. Missing/corrupt entries remain unavailable;
  partial recovery is not evidence that the entire old ledger was recovered.
- Damaged primary bytes and segments are preserved outside normal segment GC.
  Content-addressed evidence makes retries idempotent. No damaged record is
  accepted as a fact or as proof of consent, even if its revocation ID is lost.
- A complete, versioned, checksummed marker is atomically written and directory
  synced BEFORE publishing a recovered primary. All restored active owner facts
  are projected to OBSERVED for ordinary readers, stats, replay and temporal
  views. The complete empty marker remains after final reconfirmation.
- Missing/truncated/invalid markers fail closed. No in-process authoritative
  marker cache can hide disk failure. Cache fingerprints include the effective
  consent-marker revision, including when primary bytes are unchanged.
- A new owner decision binds the exact displayed ID/content hash subset and
  current review fingerprint. No null/all shortcut. Denied, stale, unbound,
  model-tool and foreign-content decisions are rejected. Verified tombstones
  cannot be reconfirmed. New ordinary facts do not authorize old restored facts.
- MemoryStored events are emitted only after writes/confirmation complete;
  restored held facts emit UNCONFIRMED. Event payloads contain IDs and status,
  not owner text. Existing ThinkingMemory remains unchanged.

## Required production connection after independent safety review

This branch deliberately does not edit AgentActivity. Root must replace the
actual live Core construction points with `withAutomaticRecovery(filesDir)`
(or its signer-preserving overload), including `ownerFactMemories()`, after
the safety gate passes. Merely importing the new class is not the user's
completed recovery rule. Strict archival/research readers should remain strict.

To restore consent, the UI must display `recoveredOwnerFactsForReview().facts`,
keep that exact review object, and build OwnerReconfirmationProof only in the
trusted owner's explicit decision callback. `reviewFingerprint` and
`confirmedFactHashes` must correspond to the displayed selected facts.
Call `reconfirmRecoveredOwnerFacts(review, proof)`; handle rejection by obtaining
a fresh review, never by fabricating a replacement proof or retrying silently.
This API is not a model tool and grants no device/tool permission.

### Concrete owner-dialog adapter

`ui/OwnerMemoryRecoveryDialog.show(activity, existingExecutor, core)` loads the
review and commits the selected confirmation on the supplied executor. It owns
no worker queue. Its separate `OwnerRecoveryDialogDecision` initially selects
nothing and creates one immutable OWNER_PILL proof only from the explicit
positive-button callback. The full fact text is displayed without ellipsizing;
the proof retains exactly that displayed review and selected ID/hash subset.
Selection, cancel, back and outside-dismiss before submission do not reconfirm.
After the positive click the dialog disables cancel while the authorized write
is in progress; closing its lifecycle handle suppresses UI callbacks and skips
work that has not started, but cannot roll back an already committed write.
Stale reviews and I/O failures show no successful-activation message and do not
retry with fabricated/reloaded consent. The normal automatic recovery itself
may repair storage while loading; cancel never grants restored-fact consent.

Root's smallest hookup after safety review: add a memory-center action beside
`GEMERKTE FAKTEN` in `showMemoryCenter()` (baseline AgentActivity line 2547),
calling the helper with `learningIoExecutor` and the reviewed existing Core.
Keep its AutoCloseable in an Activity field; close the previous handle before
opening another and close it in `onDestroy`. `showBootstrapMemory()` (line 6243)
is only the old startup-facts status dialog and has no existing recovery review.
`onLearningIo()` (line 6147) confirms the same executor/lifecycle convention.
Do not perform ledger reads in the center's UI construction or auto-confirm a
recovered fact through the old generic `rememberOwnerFact` path.

`cockpit/MemoryEventSource.kt` currently leaves `MemoryEventSources.production`
null. After the tested producers are integrated and reviewed, root may connect
it to `ContinuumMemoryEvents.forApp(filesDir)` through the existing source
adapter. Until then the sphere must remain quiet/offline. Do not invent events
or lift the gate in an unrelated avatar commit.

## Focused GREEN gate (builder owns all execution)

Run OwnerMemoryRecoveryTest and OwnerMemoryRecoverySafetyTest first, then the
unchanged LearningExperienceStoreTest, Lex3LearningExperienceStoreTest,
LearningExperienceReplayCacheTest, ContinuumOwnerFactTest,
ContinuumTemporalMemoryTest, ContinuumTemporalMemoryBenchmarkTest,
ContinuumThinkingMemoryTest, ContinuumMemoryStatsTest and
PersonalLearningTransferTest. New coverage includes I/O failure before marker
commit and primary publish, reconfirmation failure/events, destroyed revocation
identity, malformed/missing/symlink marker, old temporal-cache collisions,
explicit subset/replay rejection, segment survival and unsupported formats.

No Gradle execution is authorized in parallel with the builder's coordinated
run. A direct Kotlin/JUnit run is a JVM regression result, not an APK, lint or
Fold7 result. Record exact source/compiled-overlay provenance.

### Observed recovery gate

Candidate `4c133026e0cc186f36f233d65fd491641142de29` compiled against the real
baseline classes with the correct Kotlin module identity. The unchanged 95-case
selection produced 94 passes; all 15 new recovery/safety tests passed. The only
failure was the existing ten-year-history timing assertion under concurrent
compiler load: P95 5.162 ms against its unchanged <5 ms gate. The builder then
ran that exact test alone against the same candidate: **P95 1.308 ms, P50
0.866 ms, 819/819 correct, one test passed, exit 0**. Thus all 95 selected cases
have a real passing execution; this is not represented as a single clean full
Gradle suite. Logs: `direct-tests/memory-recovery-green-abi/junit.log` and
`diagnostic-ui/memory-benchmark-isolated-candidate.log` in the session workspace.

The separate dialog follow-up at
`455cf4fa656e9cfe4ca491168d3d99ffdf7f623c` compiled and passed **8/8** pure
`learning.OwnerRecoveryDialogDecisionTest` cases and **6/6** Robolectric
`learning.OwnerMemoryRecoveryDialogTest` cases (under `src/uiTest`), both exit 0.
Logs were read back from `direct-tests/owner-recovery-dialog-455/junit.log` and
`diagnostic-ui/owner-recovery-dialog-455.log`. Cases cover default-deny selection,
exact hash binding, immutable one-shot proof, cancellation, stale review,
failed durable write and late/finished-Activity callbacks. The builder executed
these targeted runs; the four previously tested recovery sources are unchanged.
Root still owns actual Activity wiring and the final integrated Gradle/APK/lint
gate. No device installation or remote push occurred in this subtask.

### Additional production-connection finding: historical preference import

Read-only review of root integration `5d44488d` found that the live chat calls
`syncOwnerFacts(bootstrapMemory.facts())` immediately after choosing recovery.
`appendMissing` skips known IDs but could recreate an absent owner ID lost with
a corrupt segment, including a lost revocation, as CONFIRMED. A complete empty
reconfirmation marker must also retain this protection after the review ends.
Three new `OwnerRecoveryHistoricalImportTest` regressions specify these two
cases and unchanged ordinary pre-recovery migration. At test-only RED commit
`1ed73af32990b186ed3745b145321c95be6f206a`, compilation succeeded and the builder
observed **3 tests / 2 expected behavioral failures**: both guarded imports
returned 1 rather than 0; ordinary migration/revocation passed. Log:
`direct-tests/owner-historical-import-red/junit.log`.

The small fix keeps public `appendMissing(List)` unchanged and routes the Core's
historical sync through internal `appendHistoricalOwnerFacts`. Its private
shared append path runs `loadValidated`, then the recovery-history gate and any
append under the SAME existing OS transaction. A complete empty marker still
blocks history-based reactivation. `confirmOwnerFact` remains the separate new
explicit-owner action; no historical facts/evidence/preferences are deleted.
Candidate `eb329841d43da40dc74d7838309676284ebd00fb` compiled and passed **25/25**
targeted tests, compile/JUnit exit 0: the same three new historical-import
regressions, all 15 recovery/safety cases and seven unchanged
`ContinuumOwnerFactTest` cases. The builder used the correct Kotlin module name
`DeviceAI_app_debug`. RED and GREEN logs were read back; GREEN evidence is
`direct-tests/owner-historical-import-green/junit.log`. No assertion or timing
threshold was weakened.

The same root-connection review separately found that legacy
`BootstrapBrain.decide(request, bootstrapMemory.facts(), ...)` can directly
answer `Prüfe Wissen:` and `Erinnerungen/was weißt du` from raw preferences,
bypassing the Core's inactive-fact projection. Root owns that Activity-side
follow-up: genuine memory answers must use currently eligible Core facts on
the existing IO executor or fail closed. The greeting path does not use the
passed facts. No Activity changes are made by this memory subtask.

## CognitiveLoop follow-up and rollback

Keep BoundedAgentOrchestrator as the only orchestration loop. Add a small memory
port around existing ThinkingMemory recall before PLAN and journal/reflection
after genuinely checked outcomes. `evaluationConfirmsOutcome=false` in the
live chat must not become a successful-world-outcome claim. Keep cancellation,
ToolGate/owner approvals, screen-context non-persistence and AiContextCompiler.
No external model connection or new queue/store is included here.

Before production connection, reverting this isolated commit restores the
existing strict behavior. After recovery metadata has been written on a device,
do not roll back to an older reader that ignores the marker: retain this reader
or explicitly re-block the recovered facts. Preserve all evidence and markers.
