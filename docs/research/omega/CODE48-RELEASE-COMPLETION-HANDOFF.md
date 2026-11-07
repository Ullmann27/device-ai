# Code48 — release completion

Status: focused repair gate PASS (79/79); full release gates PENDING.
Continuation 2026-11-07. Coordination point: PR593.
Base: PR592 `4c198ec81462702e5b39b28a6ead2b42d084d4c4`, including
PR591 `75d9a7b719915bf81b2fc5bd0eecebc48908ad46`.
Branch: `codex/continuum-code48-release-completion-20261107`.

## Recovered work

The interrupted predecessor retained seven production changes and four test
files locally. Their exact bytes are copied into this isolated continuation;
the predecessor worktree and branches are preserved. The manifest is under
`docs/evidence/code48-release-completion-20261107/recovered-source-manifest.json`.

- PR528: `ContinuumLearningCore` validates a bounded source batch before mutation,
  prioritizes its records in replay and records completion in the core.
  `ContinuumSourceNovelty` persists unfinished rounds before archival so a
  cancelled round remains eligible after restart. Activity/public-study callers
  use the validated batch and retain evidence-save failure status.
- PR531: `AiContextCompiler` redacts credential aliases and rejects ambiguous
  wrapped authorization values before publishing recall observations.
- PR558: `ContinuumBrainOrbRenderer` draws the existing hypothesis signal in
  the separate Brain dialog without inventing a confirmed-memory pulse.

## Verification plan and current evidence

- New recovered tests: NOT_RUN at this checkpoint. Classes:
  `RecoveredContextCredentialBoundaryTest`, `ContinuumSourceRoundRecoveryTest`,
  `ContinuumSourceRoundJournalTest`, `ContinuumBrainHypothesisUiTest`.
- Complete Android/UI/Lint, Node, backend and unsigned APK/AAB: NOT_RUN for this
  source. The predecessor 75d Android log ends during `testDebugUnitTest`, has
  no completion receipt and has no remaining process; it is INTERRUPTED, not PASS.
- Run existing Gradle QA with `continuumUiWidgetTests=true`,
  `continuumLocalStandaloneQa=true`, `deviceAiMentorPreview=true`,
  `continuumStandaloneVersionCode=48`: `:app:compileDebugKotlin`,
  `:app:testDebugUnitTest`, `:app:lintDebug`.
- Run `scripts/continuum-reliability-qa.mjs` critical/full-node, backend
  install/check/tests, and the existing original-publisher release tests.
- Build `:app:assembleRelease :app:bundleRelease` with the existing unsigned
  ARM64 gate after the final source is frozen. Bind every result to that SHA.

## Release and next entry

The active owner task requests completion and delivery of Build48 through the
existing original signer/update channel after verification. Target package
`at.heinz.deviceai.continuumlocal`, versionCode48, versionName0.3.47.
Publisher service `srv-dbf1tr2vcj2c73ehpfog`; re-read actual branch/deploy state
before any authorized publication. No main merge, key change, paid runner,
model-weight publication or device installation is part of this work.

Next: compile and execute recovered tests, repair evidenced defects, freeze the
verified release source, then run the unchanged original publisher and independently
verify the served APK, certificate, version and catalog. Physical Fold7 behavior,
temperature, GPU/FPS and reference-video equivalence remain NOT_RUN.

Experiments: EXP-02/07 test evidence, EXP-04 learning quality, EXP-11 event paths,
EXP-12 interruption/recovery. Existing consent, ToolGate, model/holdout and
owner-memory boundaries remain intact. No PCM/signer format migration.
Rollback: omit this continuation or revert its scoped commits; keep PR591/592.

## First actual gate — 6032da0d

The real SDK/Gradle focused run compiled production and all test sources and
executed ten classes: 77 tests, 75 passed, two failed, zero skips. All source
recovery, journal, credential and existing learning regressions passed. Both
Brain tests failed before event assertions because the paused Choreographer had
not delivered the new dialog's actual attachment traversal. The fixture now
advances the real main looper by two frames after navigation, as the existing
Activity fixtures do. No production method, event, clock, attachment or renderer
is replaced. The attached/shown, exact-event, native-pixel, close and fresh-event
assertions remain unchanged. Repeat status: NOT_RUN at this checkpoint.

Raw log, complete JUnit XMLs, receipt and summary:
`docs/evidence/code48-release-completion-20261107/focused-red-6032/`.

## Recovered review: 79/79 host tests

The next frozen-source run on 6161132e passed 76/77: dialog attachment and
native hypothesis pixels passed, while the close callback awaited the same
real window traversal. The close fixture now also delivers two frames; all
visibility, native-pixel, subscription-retirement and fresh-event assertions
remain intact. Its raw failed run is retained under `focused-red-6161/`.

Review found terminal source records could occupy unfinished-round capacity.
Three unchanged-production regressions failed: revoked-source retry allocation,
121 refused records, and a journal containing 120 stale revoked markers.
`ContinuumSourceNovelty` now derives excluded hashes from the existing validated
ledger, prevents pending markers overriding revocation/rejection, and retires
only those terminal markers before checking retry capacity. Genuine incomplete
work remains pending. Refused work is never labeled completed or learned.

The resulting real Gradle/SDK run passed **79/79, zero failures/errors/skips**,
including the three regression cases and the two native Brain dialog tests.
The source overlay, source hashes, full XMLs, raw log and fixed test filter are
under `recovered-review-green/`; the assertion-RED is under `journal-review-red/`.
Final whole-app/release gates on the published frozen source remain PENDING.

The separate suggestion to consume a token across an ambiguous credential
line boundary is not applied: that token can be an owner prohibition, so
redacting it can reverse meaning. The established fail-closed compiler contract
and zero-recall-publication tests are retained. AgentActivity already handles
model-run failure; this is a rejected ambiguous context, not an unhandled app
crash. Malformed credential text may require the owner to clarify its framing.

## Fresh branch inventory

The fresh remote inventory contains 656 heads. Since the prior 653-head audit,
the additional Code48 heads are all ancestors of this candidate. The two other
changed heads modify state/checkpoint or scheduled-ledger data only; their changed
paths contain no new APK production code. Only path/tree metadata was inspected
for those data changes. Full inventory and comparisons: `branch-refresh.json`.
This does not claim every historical feature is product-integrated or device-tested.

Release source is frozen after these repairs. Use the original publisher's
mandatory complete Node/backend/Android/UI/lint/unsigned gates before its original
signer runs. No gate or signer is bypassed, and no duplicate publisher is created.

## Shared release pipeline

The owner requested coordination with the other Code48 conversation. Its seven
production files and four regression files are already recovered here; the
original worktree and PR592 remain intact. The live agent registry in this
session exposes only its coordinator, so no direct cross-chat delivery or
acknowledgement is claimed. Use PR593 and this existing handoff for shared state.

1. Integration owner: PR593 continuation. Freeze one published source SHA after
   the recovered changes and terminal-source repair. No competing source edits
   are included after that freeze; new findings must identify their exact delta.
2. QA owner: existing continuous owner-release script. Run its complete
   Node/backend/Android/UI/lint and unsigned APK/AAB gates on that same SHA.
3. Publication owner: the existing original signer on
   `srv-dbf1tr2vcj2c73ehpfog`. Recheck branch/deploy state, update with an expected
   SHA lease, preserve signing secrets, and start at most one deploy.
4. Acceptance: independently fetch the published catalog and APK; verify source,
   version48, package, bytes, digest and original signing certificate. Record
   the actual deploy identifier and results in PR593. Fold7 acceptance stays open.

This reuses the existing release pipeline; it creates no new paid agent service,
runner, credential, worker protocol or competing receipt system.
