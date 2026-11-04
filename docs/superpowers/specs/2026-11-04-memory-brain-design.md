# Memory and brain integration design

## Intent

Improve the existing local memory path and prove that genuine memory activity
can reach the visible avatar through the current cockpit contracts. Owner
direction requires cooperation with the concurrent weight and visual workstreams
and a combined APK only after those changes are finished and checked.

## Decisions

Retain Kotlin/Android, the existing stores, retriever, context compiler,
orchestrator and event hub. Use focused behavioral regressions and deterministic
synthetic experiments. No external model dependency or new database.

Considered approaches: replace memory wholesale; add a second research memory;
or strengthen the actual existing path. The third is selected by engineering
judgment because it preserves stored data, existing integrations and the active
recovery work. Research fixtures must never become owner facts.

## Scope

- Retrieval: preserve meaningful content and privacy while making selected
  memory IDs unambiguous; measure quality and retrieval cost before adoption.
- Context: memory content is evidence, not a second owner/system instruction.
  Preserve complete selected groups, late corrections, bytes and original IDs.
- Store boundaries: consumers cannot alter stored provenance, evidence or
  experience outcomes by mutating a previously returned collection.
- Avatar integration: adapt actual ContinuumMemoryEvents into the existing
  MemoryEventSource contract; close must prevent stale callback delivery.
  No private memory text travels through the presentation bus.
- Validation: focused RED→GREEN, unchanged regression suites, source-bound
  cross-module tests, Android compile/lint and final combined APK gate.

## Ownership

This branch owns AiContextCompiler, HybridMemoryRetriever, MemoryEcology and
their tests plus a new event-source adapter. Other conversations retain
learning Core/ledger/recovery, model weights/training, AgentActivity, cognitive
loop integration and avatar/UI/voice. The final Activity hookup must be applied
against their exact accepted source, not by overwriting their working files.

## Limits and acceptance

No schema/hash migration, main merge, permission expansion, paid service or
productive weight promotion. No signing/installation or publisher alteration
at this stage. All failures and unexecuted device checks remain visible.
The combined build must preserve the original package and update identity.

Initial baseline: 31 focused JUnit tests passed, 0 failures/errors/skips, with
real Android Gradle compilation at 7b7c39d7 (app sources equal to 578bdfd1).
This is host evidence, not Fold7/audio/GPU acceptance.
