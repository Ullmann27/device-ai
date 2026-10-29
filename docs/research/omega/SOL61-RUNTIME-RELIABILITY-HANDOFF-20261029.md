# Continuum reliability — final host verification, 2026-10-29

PR #223; branch `sol/continuum-runtime-reliability-20261029`.
Baseline: PR218 `0b2a9dddfe172eac82923092fcdaa974afc6e25a`.
Verified program source: `c8ea465f3ff6cd87feb763df7f83d8dfc8d70a6a`.
The closing commit adds only this handoff and evidence; PR description records its exact SHA.

## Implemented and retained

- `70dc8637`: rescue preserves valid leading UTF8 BOM (Kotlin's actual ledger preserves U+FEFF), and retains at most512KiB+1bytes of failed ADB output with truncation metadata. Node spawnSync may exceed maxBuffer by a read chunk.
- `c8ea465f`: actual test found "-s" reached ADB selection. USB serials must now begin with an alphanumeric character; tests still reject injected/remote values before ADB. Authorization, fixed original package/path,256records, hashes and read-only rules remain.
- `787dc76a`/`47dce52d`: direct lstat rejects dangling lease/mutex paths before write/flock; real Git directory resolution rejects dangling parents. Both one-shot and loop reject already-set STOP/abort before lease acquisition. Existing live leases remain untouched; interrupted loops return nonzero CLI status. No second backlog/worker.
- `8d19b95b`/`1be9b151`: latest8 numeric/enum-only process-local Gemma profiles. Invocation, prior-conversation release, conversation creation, token-count query, native dispatch entry/return, first native text, cancellation, native terminal callback and runtime delivery have separate monotonic offsets. No prompts/answers/model paths/error text retained. Native prefill is UNKNOWN_NOT_EXPOSED. Memory is JVM_HEAP_ONLY, not process/native/GPU/PSS. No new monitor spans measured JNI/callback actions; independent source review found no new profiler lock inversion.
- `8df58244`: last safe profile reaches the existing One-App status-copy and timeout diagnosis.120s waiting budget now starts before synchronous model invocation. A blocking JNI call still cannot be preempted by this Java wait loop; actual device timeout cause is not proven or fixed by increasing the limit.
- Prior218 LEX2 read-only preview, revocation gates, SAF readback, transient chat draft and process-group shutdown remain. All original app data/signatures/weights and main are untouched. No APK/sign/install/download/training.

These changes use existing runtime/ToolGate/CCC1/health-worker interfaces. The canonical CCC1 persisted task/repair loop remains; no new quality-selection heuristic or model-generated coding proof was added.

## Actual verification

Execution was initially UNAVAILABLE; the selected shell recovered during closeout. Earlier NOT_RUN checkpoints are historical, superseded only for the host checks actually run below.

**PASS on exact source c8ea465f3ff6cd87feb763df7f83d8dfc8d70a6a:** unfiltered standalone-QA Kotlin/JUnit and configured NewApi lint:1185tests/227suites,0failures/errors/skips; Node218/218PASS,0failures/skips; required CRE1/SWB1/SWB2 parity PASS. Gradle exit0, Node exit0, strict reporter PASS. Final Gradle BUILD SUCCESSFUL in3m37s. No APK produced.

Actual toolchain: Linux x86_64, Node24.19.0, existing Temurin17.0.21/JDK17, Gradle8.11.1, SDK36 and build-tools35.0.0 aapt2. No version switch/setup/download. Project lint is the configured NewApi gate. Android JUnit runs on host, not physical Android.

- Initial targeted run on8df58244:61/62PASS,1FAIL; original output retained.
- Initial full run on8df58244: Gradle exit0 /1185tests,227suites,0failures/errors/skips; Node217/218PASS,1FAIL; strict overall FAIL. No failure concealed.
- Existing negative test exposed the "-s" serial validation defect; no test weakened. Corrected26/26rescue suite PASS, exit0.
- Separate unchanged218baseline + candidate public regression sources:10selected cases,9FAIL/1PASS, exit1. Expected failures cover BOM, failed-output bound, pre-start cancellation/live lease and dangling entries. The intentionally failing baseline is not a new implementation failure. Only synthetic temporary files/processes were used; no private/device data.
- Supplemental16cases executed exact pure worker functions in V8 before shell recovery. These never counted as Node/process/Android proof.
- Bash/node syntax checks and source readback also passed, supplementary only.

[Raw evidence](../../evidence/runtime-reliability-20261029/) contains command/exit metadata, original failures, final Gradle/Node/parity logs, JUnit XML, source blobs and SHA256manifest. Public parity fixture weights are deliberately not copied. No raw owner backups, signing material or private content was read/published.

## Opus / EWM1 boundary

Only architecture and preregistration documents were read. TEST4 and its data were not read, edited or executed. Before source commits Opus's changed paths were compared; no overlap. His branch `opus/continuum-brain-research-20261029` remains independent. No EWM1 algorithm/weights or PR221 native callback rewrite integrated.

The requested later EWM1 port was deferred when owner reduced remaining budget to15% and asked to close. No untested placeholder port is advertised as integrated. Existing `AuthorizedConversationMemoryShadow` remains the same bounded diagnostic path; future authorized adapter must retain defaultOFF, provenance, current revocations and unchanged baseline fallback. Current Gemma pipeline has no EWM1 promotion.

## Fold7 next steps

On an owner-reviewed clean checkout of exact source c8ea465f, using the existing Termux native paths (the canonical runner supplies AIDL/aapt2 flags):

```sh
bash scripts/continuum-fusion-full-regression.sh "$(git rev-parse HEAD)"
```

Then, only with already authorized local ADB/regular Android pairing:

```sh
bash scripts/continuum-generation16-rescue.sh
```

It supports USB/loopback transports only, writes private local captures, and fails safely when original package is not debuggable. No run-as availability was established here. Keep Generation16 installed/unmodified. Original Keystore identity cannot be assumed transferable; missing original signing key still prevents installing217onto the historical package.

For a separately owner-approved next Continuum Lokal update (normally code28), inspect real direct/normal-chat/Stop/reopen behavior and copy the numeric profile from the existing One-App center. Compare dispatch-enter/return, first callback and terminal offsets; do not label the difference as native prefill. No APK is produced by this checkpoint.

Still NOT_RUN: Fold7 ARM64 regression, original256private records extraction/migration, Gemma native performance/PSS/quality/offline/fold lifecycle, Node20/22compatibility, new generative CCC1 repair and EWM1 productive integration. No measured Gemma speedup or intelligence/learning improvement claimed. Auto-practice/public research were not reenabled.

Rollback is ordinary review/revert on this isolated branch. Never replace original app/signature or restore stale ledger consent. Exact tested source and raw original failures are retained for the next developer.
