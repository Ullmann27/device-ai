# Memory / brain handoff — 2026-11-04

Branch: `feat/continuum-memory-brain-20261104`; draft PR #494 targets PR #483's
integration branch, base 578bdfd12f896cc990bf1a043bc1b248ba3adb6d. Original scope
and parallel ownership are in [the coordination ledger](MEMORY-BRAIN-COORDINATION-20261104.md).
This file is the canonical resumption entry point. Read the actual branch head
rather than treating a previous checkpoint as current.

## Implemented and measured

- AiContextCompiler frames full sanitized memory evidence, source references
  and independent validation references. Whole-group budget and original IDs
  remain intact. RED 4 cases; GREEN 28 tests.
- In-memory memory and experience stores freeze defensive collection snapshots
  on write, preventing later mutation of evidence and evaluator history.
  RED 5 failures / 6 cases; GREEN 35 tests.
- HybridMemoryRetriever rejects all conflicting same-ID records before privacy
  filtering/scoring, deduplicates identical records, and keeps bounded top-K.
  Exact unique-ID reference parity includes a 96-combination grid. Candidate
  frozen at c3e52210; implementation suite 34 tests. Synthetic paired host
  medians improved 22.6–48.1% on four measured workloads; no Android claim.
- Actual compiler recall publishes only included hit metadata and a salted
  query hash through the existing per-app hub. Six new cases; GREEN 26 tests.
- ContinuumMemoryEventSource adapts that same hub to the existing mapper with
  close/race/lifecycle handling and bounded metadata. GREEN 15 tests; a removed
  synchronization mutation fails the intended concurrency case.
- Independent 25-case holdout was frozen before candidate inspection. Baseline
  7 PASS / 18 FAIL; unchanged candidate holdout 25 PASS. Combined component run
  at local cbb9b18b: **98 PASS**, 0 failures; Kotlin compilation passed.

Counts above are overlapping test runs, not a sum of unique tests. Receipts are
in `docs/evidence/memory-brain-20261104` and `docs/evidence/memory-events-20261104`.

## Actual app connection and ownership

#491 owns Activity/avatar/voice, recovery and cognitive-loop files. It now has
MemoryEventSources.forApp(File). Integrate this work by delegating that factory
to ContinuumMemoryEventSource.forApp(appDirectory), and instrument the actual
AiContextCompiler with `withRecallObserver(ContinuumMemoryRecallPublisher(filesDir))`.
Keep mapper.detach() before subscription.close(). The prior event-emitting
retriever has no normal production caller on the inspected baseline.

#492 owns weights/training; #496 owns its bounded support repairs. Do not alter
foreign worktrees. No second database/orchestrator, weight promotion, private
source publication, main merge, signing-key change or installation.

## Current gates and next entry

Independent production review and compiler-to-mapper end-to-end component test
are in progress. Full combined Gradle/unit/lint is **NOT_RUN** at this checkpoint;
Activity connection, Fold7, microphone/TTS/GPU and final APK are **NOT_RUN**.
The other workstreams are still changing. Do not build or present an old-head
APK as the finished combined request.

Reproduce final Android gates with existing JDK17 / SDK36 and the shared
`/workspace/scratch/b3170a7ae924/gradle-build.lock`, serially:

```sh
./gradlew --offline --no-daemon --max-workers=1 --console=plain -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
./gradlew --offline --no-daemon --max-workers=1 --console=plain -Pkotlin.compiler.execution.strategy=in-process :app:lintDebug
```

Use JAVA_HOME/ANDROID_HOME/ANDROID_SDK_ROOT from the existing local toolchain;
never run concurrent heavy Gradle workers or trigger GitHub Actions. Focused
host receipts preserve exact executed commands and compiled source hashes.

## Recovery and rollback

All changes are isolated in this draft branch. Keep the source branch and its
published checkpoints intact. If a combined integration fails, discard only its
new isolated checkout or revert selected feature commits in that integration
branch; never reset a foreign branch, delete owner data or rewrite main. Existing
ledger schemas, model weights, package/signature and update channel are retained.
Public project publication is explicitly owner-authorized; see AGENTS.md rule 5.

## Later checkpoint: review and app integration

The compiler status-projection gap is now fixed at bfc427c6, with 3 additional
cases (RED 2 failures; GREEN 47 relevant tests including the unchanged holdout).
The actual compiler→hub→mapper→orb chain passed 9/9 tests. A disconnected-observer
negative control fails exactly three cases, so the integration check detects a
missing producer.

Complete Android source/test compilation succeeded. The first full suite ran
2,442 cases with one old idle-versus-thread-termination assertion race and 19
existing skips. The corrected test adds a bounded join of the actual worker,
keeps all old assertions, and passed a fixed 100-case repeat run. See
`docs/evidence/calculation-shutdown-race-20261104` and the retained original run.

The isolated combined branch integration/continuum-memory-avatar-hooks-20261104
contains actual lazy Activity initialization and factory delegation. Its exact
avatar/recovery input is f14ce8e9; weights remain unchanged. It is a provisional
snapshot: other chats' later frozen heads still need reconciliation. Factory
regressions passed 14/14; source contracts 3/3. The prepared two-case Robolectric
Activity/Handler test remains NOT_RUN until the queued build gate executes.

The independent static review found no confirmed unaddressed blocking issue in
its reviewed scope; explicit partial-review limits and build-lock attempts are
in `docs/evidence/memory-brain-20261104/final-review/REVIEW.md`. Final full combined
Gradle/lint, device and APK gates remain open. This is not release acceptance.
