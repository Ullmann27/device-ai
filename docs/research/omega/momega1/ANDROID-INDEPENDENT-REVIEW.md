# Independent Android review of MΩ-1

Status: **ACCEPT for the scoped host/source Android repair at 037370f0544d3ed2ee7686082c41c78046daa64d**. No remaining blocking finding within the reviewed scope. AR-1 is reproduced RED and resolved GREEN; this is not model/device/release acceptance. Reviewer owns only test/evidence/review files. Frozen cases preceded implementation inspection (local4556be48 / publication56ee1f008f9f850573758484f12d3671950d65b8; PR419).

## Finding AR-1: cancellation did not revoke prepared execution consent

**P1 / REQUEST_CHANGES at b95858c103243ec921c1de9c8fb9ae8da0e88c29.** A valid, target-matched CCC1_ABORT terminal receipt removed canonical `CCC1_TASK` but left `TYPED_PREPARED_*`. The RUN button remained enabled. An already-open execution review still matched that saved task and journal ID, restored the canceled CCC1 target and sent Termux RUN_COMMAND. Both behaviors were reproduced against actual Activity/Receiver code under Robolectric API28. The test first asserted that the fixture was recognized as a confirmed abort, then checked authorization revocation.

Reproduction: `IndependentTypedAuthorizationUiTest.canceledPreparedTaskCannotBeOfferedForExecution` and `.terminalCancellationWhileApprovalDialogOpenCannotDispatch`. Dedicated immutable source snapshot:9 tests,7 pass,2 failures,0 skips, Gradle exit1. Raw XML includes the actual unexpected RUN_COMMAND Intent. This is a consent/dispatch failure; it is **not** evidence of model execution after cancellation. Canonical Node `runTask` returns a terminal journal outcome before repair/model callbacks.

Accepted repair `037370f0544d3ed2ee7686082c41c78046daa64d`: explicit matching abort dispatch consumes local prepared consent in the same persisted edit as abort pending state; successful target-bound abort receipts remove only matching prepared authorization; pending abort for the same journal makes prepared authorization unavailable. Review confirms the receiver revocation is independent of the canonical display target, protecting late abort(A) versus displayed task B. Invalid or wrong-target receipts cannot revoke another task. No new worker/scheduler/journal/authorization framework was introduced.

## Independent validation matrix

| Candidate | Test scope | Result |
| --- | --- | --- |
| b95858c103243ec921c1de9c8fb9ae8da0e88c29 | Actual production Kotlin bridge/task/canonical JSON/build-plan;31 independent cases |31 PASS,0 FAIL,0 SKIP; compile/run exits0 |
| b95858c103243ec921c1de9c8fb9ae8da0e88c29 | Actual Activity/Receiver,9 independently authored Robolectric cases |7 PASS,2 FAIL,0 SKIP; exit1 |
| 037370f0544d3ed2ee7686082c41c78046daa64d | Same31 pure Kotlin cases extracted by exact SHA |31 PASS,0 FAIL,0 SKIP; compile/run exits0 |
| 037370f0544d3ed2ee7686082c41c78046daa64d | Original9 plus4 cancellation/preservation cases and32 authored regressions |45 PASS (13 independent+32 authored),0 FAIL,0 SKIP; exit0 |

The additional4 independent cases were frozen at localefa03299, class SHA-256 `89c88476a3abe6b829e6e5c22177a3b9c86c2c003de82c2f8c1974a4953a71eb`, before inspecting the repair. They check revocation on explicit abort before receipt, stale RUN blocked by legacy pending abort, abort(A) preserving prepared(B), and wrong-target receipt preserving authorization.

Prior actual authored RED evidence was read independently: `delta-red-corrected` at d43c043afde46bfb8dab667749f9d1c4da19f72a (7 cases,6 failures,0 skips) demonstrated lost full snapshot, stale approval dispatch, retained consumed approval and callback UI lifecycle failures; `delta-c10ab9d3` (9 cases,1 failure,0 skips) exposed missing cancel target. Earlier mixed-source/scheduler-confounded runs are not used as product RED evidence. Current acceptance cannot inherit these candidates' test results.

## Commands and reproducibility

Pure checks from this review worktree:

```sh
./tools/momega1-android-review/run-pure.sh 037370f0544d3ed2ee7686082c41c78046daa64d docs/evidence/momega1/android-review/pure-037370f0
```

The script extracts six real production source files by exact commit, records SHA-256 hashes, compiles them with the independent harness using the existing Kotlin compiler, and runs the resulting JVM artifact. No production extraction is modified and no API stubs are supplied.

QA alone schedules Gradle. From `/workspace/momega1-android`, with source/test hashes captured before and after:

```sh
source /workspace/momega1-build-env/env.sh
gradle --no-daemon --max-workers=2 --console=plain \
  -I /workspace/momega1-qa/docs/evidence/momega1/qa/android-037370f0/test-filter.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
```

The init script selects the eight named test classes, snapshots only the two independent UI class files into a dedicated source directory, and sets the test JVM to `/usr/bin/java`. For the9-case RED use the retained `ui-red-b95858c1/test-filter.init.gradle`, after copying the corresponding frozen class to its indicated path. The source files are in `tools/momega1-android-review`; raw logs, source manifests, XML and exit files are in `docs/evidence/momega1/android-review`, with a SHA-256 inventory.

## Limitations and transparent failed attempts

- Pure harness first failed to compile due to a reviewer syntax typo, then an invalid fixture attempted to canonicalize a task with a mismatched file type/chain. Both failures are preserved and explicitly excluded from product RED counts. Corrected harness completed31 cases.
- Reviewer added the separate pure-harness source in an external directory while the first UI compile had registered that directory. Although the UI class itself never changed, compilation inputs were ambiguous. QA preserved that attempt and reran from a dedicated immutable copy; only the snapshot repeat supplies the9-case RED finding.
- Main-thread Activity/receiver transitions and recreation/destruction are tested in Robolectric API28. Real Android17 process death, PendingIntent behavior across actual process death, Termux service availability and live storage failures remain unverified. Static persistence ordering is not device evidence.
- No new model-to-draft caller exists. Manual draft and full owner review are the demonstrated source path; no actual local inference, model-produced repair or end-to-end coding loop was run by this reviewer.
- FOLD7_NOT_RUN. No device identity, model weights, package installation, signing, release, owner data or deployment changed.

Scope: EXP-00/01/02/05/07/08/11/12 source/host-contract review only. Rollback: omit this review-only branch; feature rollback remains the author's isolated production commits, preserving canonical CCC1 journals and owner data. Final integration requires exact-source combined QA; passing these targeted checks alone is not RELEASE_READY.

Independent reviewer inspected the raw final XML: original9 independent cases and4 additional cancellation cases all pass. All8 XML suites total45 tests with0 failures/0 skips; captured before/after source manifests are byte-identical. Reviewer independently re-ran31 pure Kotlin checks on exact037370f0 with compile/run exits0. Final raw evidence hashes are recorded in `docs/evidence/momega1/android-review/SHA256SUMS.json`.
