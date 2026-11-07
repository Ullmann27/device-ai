# Code48 — release completion

Status: WIP / UNVERIFIED. Continuation 2026-11-07.
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
