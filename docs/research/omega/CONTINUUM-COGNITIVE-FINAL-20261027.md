# Continuum: verified cognitive integration checkpoint, 27 October2026

The new bounded capabilities are correlated cognitive diagnostics, explicit correction handling that cannot silently preserve the old grounded topic, and an actual two-tool read-only sequence through the existing agent. No general intelligence, new neural generalization, successful model-generated repair or complete phone-to-learning autonomy is claimed.

Draft [PR182](https://github.com/Ullmann27/device-ai/pull/182), editable branch `sol/continuum-cognitive-evidence-20261027`. **Final validated code: `656b11afd00d96b063e6bc6be2b2d9db229847fa`**, source tree `2a071c2001ef65fe24a72f56f760dbf1f4fabe72`. The subsequent final commit changes documentation/evidence only; retrieve its exact ID from the PR head or `git log -1`. No self-referential result hash is asserted. Full implementation commits: [JSON](../../evidence/cognitive-20261027/implementation-commits.json); complete changed-file inventory: [text](../../evidence/cognitive-20261027/changed-files-from-pr180.txt).

## Integration and repaired defects

1. Main chat already has LocalFirstPolicy -> BoundedAgent -> LiteRT stream/respondToTools and optional ChatReasoningRouter. Latest PR181 review was correct: a null CognitiveDecisionBridge adapter does not mean missing model inference. No second brain, model adapter or duplicated inference added.
2. New `CognitiveTurnTrace` follows actual memory/core/CTX3/CRE1/runtime/tool entry points with one correlation. It records typed events, SHA256 fingerprints of references, elapsed time, actual inference attempts versus completion, extra advisory calls and later owner feedback. Existing runtime journal adds a separate bounded32-entry metadata file with atomic writes, JVM/OS locking, strict codec and symlink refusal. No raw prompts/replies/private IDs in this trace, no new fact or permission authority. Fingerprints are not anonymization. Examples published here are synthetic only.
3. Removed `persistRequestedMemory`: a secondary post-model substring match plus reply quality could authorize a semantic write outside the explicit confirmation path. Existing literal REMEMBER -> rememberOwnerFact and correction/revocation confirmation remain. No owner data was deleted.
4. Existing LocalConversationContext recognizes anchored explicit corrections, asks about ambiguous/negated scope and unclear “remember this decision”, and reports tentative focus separately from stored knowledge. New combined probe found that changing prompt context alone left the grounded topic stale. MainActivity now clears stale model turns/scoped topic, awaits EXISTING core conversation reset on the existing learning-I/O executor, and only then acknowledges. Reset failure reports failure and clears tentative focus. The durable reset prevents old-topic borrowing after core recreation; facts and model bytes remain identical.
5. Existing ReadOnlyToolPlan accepts1..3 distinct registered READ_ONLY tools. A narrow explicit storage+Downloads request selects two existing fixed steps, with the same ToolGate, budgets, cancellation, receipts and no automatic retry after failure/unknown/replay. Main chat is source-wired; actual host JVM test executes FileStore measurement and directory listing, independently checks total bytes/known filenames and two fresh hashed receipts. The host JDK runner is real filesystem execution, not Android Binder or a neural plan. Android still cannot promote legacy shell text to positive experience; evaluationConfirmsOutcome remains storage-only.
6. Real command counterexample: missing directory + `ls ... | head` returned zero. DeviceToolExecutor now uses the quoted ls command directly; existing bounded process enforces output/time limits and propagates nonzero status. Native negative test reproduces the old false green and verifies failure, valid listing and safe literal quoting.
7. PR180 native launcher omitted CheckedShellCallDispatcher. Repaired the launcher, preserving original PR180 safety code. Actual official Android/Shizuku/JUnit graph now compiles and runs both callback/dispatcher classes,11 real JVM tests.
8. Existing worker discovers the new memory/context/trace harness through its normal tracked Node-test inventory; no second scheduler or permissions system.

[Source-linked connection matrix](CONTINUUM-COGNITIVE-CONNECTION-MATRIX-20261027.md) uses every requested category and distinguishes main source integration, shadow and actual runtime execution. PR180 and PR181 have PR179 as shared base;180 source safety was retained,181's two docs were selectively copied. Synapse branches were fetched/read, not blindly imported. No earlier whole-file provenance conflict was reintroduced.

## Measured comparisons and research outcomes

| Experiment | Baseline/control | Candidate/result | Interpretation |
|---|---|---|---|
| Explicit discourse handling: development+validation | Old offline dispatcher1/8 | Existing-context extension8/8; false scope0 | Narrow deterministic handling, not full LLM quality. |
| Same task family, frozen final before candidate implementation | Old offline dispatcher4/12 | Published candidate12/12; false scope0 | Developer-authored synthetic labels; not independent human-sealed tasks or a neural generalization claim. Final now opened; never use it again as unknown. |
| Correction + next follow-up + core restart | Old topic Saphir still grounded after correction to Kobalt | Existing reset makes follow-up unresolved rather than falsely grounded; facts/weights unchanged | Reproduced real cross-state defect, repaired. Probe and new regression are published. |
| Storage and Downloads | Existing BootstrapBrain chooses only list_files | Same agent completes two actual host operations; two fresh receipts and independent oracles | New bounded procedural ability. Phone and learned strategy transfer NOT_RUN. |
| Actual small local coding model | Isolated baseline12cases:8pass/4fail | Three real Qwen calls, all rejected CODING_NOOP_PATCH; ABSTAINED | Negative model-quality result. No tests weakened, no no-op applied, no learning label emitted. |
| Preprogrammed reference repair | Same original failing source/tests | RED12/4fail -> GREEN12/0fail -> fresh GREEN12/0fail; stable hashed identities | Verifies the real Docker/CCC1 arrangement, NOT model intelligence. No separately sealed behavior oracle was run for this fixture. |
| CTX3 / CRE1 / decoder / SWB2 research | Existing sources, snapshots, qualification gates and regressions examined | CTX3 stays opt-in shadow; CRE1 only qualified pool rerank; CDX1/CDXA1 not activated; SWB2 rejected | No new independent quality experiment establishes a winner here. Three new neural ablations requested in the broader mission remain unfinished. |

Source snapshot/protocol and all case labels: `experimental/cognitive-20261027/`. The offline comparison excludes actual Gemma answers; those may perform better than the old deterministic dispatcher. Do not reinterpret the12/12 result as overall conversation accuracy. Per-decision latency for this corpus was not measured. Existing opened52/60/504/592 research examples were not described as new holdouts.

## Real local inference, resources and model identity

Public `Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF`, revision `ebb2015119c907b064c512bf053e945850b5875f`, Apache2.0, Q4_K_M artifact SHA256 `1d9614638d18024d0fbb36575a15f1302a3adf044df10345688ec4f6e1c4ff32`. Downloaded491400064 weight bytes, verified exact length/hash. This is a separate host research model, not the owner's Gemma or SWB1 checkpoint.

Actual llama.cppb11861/73adf89e3 CPU process,2 inference threads,4096context,one slot,authenticated127.0.0.1:8089. Existing canonical coding provider/CLI and owner-bound CCC1 task were used. Three server prompt/decode timings corroborate actual local inference; the provider's conservative verifiedModelExecution=false flag was NOT altered into an attestation. Temperature0; server seed not explicitly pinned, so cross-hardware bitwise determinism is not claimed. Three calls consumed the configured budget; there was no fourth attempt on the same task.

Measured server peak resident memory749604KiB, weight storage491400064bytes, three-call coding cycle51.391s. Parent-only CPU/RSS is separately labelled in result.json, not substituted for server resources. Download39.0s; actual wire overhead/loopback traffic bytes not measured. No external inference or paid API cost. Physical storage and CPU were required: this is not Virtual Model Fabric distributed execution. Research server was stopped afterward.

Source model URL, artifact/binary hashes, timings, provider hashes, canonical negative result, baseline JUnit, reference verdict and public original Git source bundle are under [local-model-coding](../../evidence/cognitive-20261027/local-model-coding/). No API secret or CCC1 HMAC runtime key was published. No productive weights or owner chat data were touched.

## Actual verification results

Nodev24.19.0; Java21.0.12.1; selected official Kotlin compiler2.2.0/JUnit4.13.2. Official Android-all15 and Shizuku13.1.5 interfaces compiled on host, not an Android SDK/AGP application build. Project Gradle/Kotlin version compatibility and phone Binder/LiteRT execution still require full app/toolchain validation.

| Command / run | Result | Evidence |
|---|---|---|
| Existing local worker, first full cycle ondd04c137 |1150PASS/1FAIL/0SKIP; source stable, lease held | worker-first-failed; single stale source assertion expected the old three-argument runLocal call. Corrected expectation preserves all recall safeguards. |
| Existing local worker, same output directory, final code656b11af | **1152PASS/0FAIL/0SKIP**,281.408s,191 tracked files,ALL_EXECUTED_PASS,sourceUnchanged=true,leaseHeld=true | worker-final raw TAP, immutable cycle receipt and events. |
| Actual JVM suites nested inside final worker | **81** agent/context/gate/process; **56** memory/CRE1/CTX3; **11** callback/dispatcher, allPASS | Worker TAP contains real JUnit outputs; selected graph source-hash inventories also published. These are not added again to Node totals. |
| Full38scripts test files on656b11af | **283PASS/1FAIL/0SKIP**,73.287s,Exit1 | scripts-final raw output + exact command/inventory. Sole failure OWNER_V25_BLOCKED: frozen approved source changed. Release gate remains intact, not weakened for research. |
| Prospective offline final |12/12 vs4/12,zero incorrect scope commitments | context-development/final-result.txt, protocol and frozen hashes. |
| Docker reference | RED->GREEN->freshGREEN,12identities unchanged | reference-control.json. No merge approved. |
| Android UI/Gemma/Fold7/complete Gradle app/APK/signature/install | **NOT_RUN** | No device/full configured toolchain here; APK work explicitly excluded. |
| New neural training, private chats, productive model promotion, real DeepSeek/distributed fabric | **NOT_RUN** | No invented inference or checkpoint success. |

Across the two final Node inventories1436checks =1435PASS+1release-gateFAIL,0SKIP. Do not call the entire repository all-green. The first failed full worker is retained instead of replacing it with the successful retry.

Four checks: (1) syntax/API selected native compiles, full Node inventories and JS parse; (2) actual worker process crash/lease/STOP/timeout regressions, callback races, durable trace/reset tests; (3) owner-write boundary, actual no-op rejection, output/exit tampering, immutable test parity and source hashes; (4) actual host natural request->typed plan->ToolGate->filesystem->independent oracle->receipt/episode/restart. Full Android->Gemma->coding->learning is source-audited but not end-to-end proven.

## Remaining work and exact continuation

No live subagents were present; no new agents were dispatched. Existing read-only health worker can run repeatedly after owner activation; it does not autonomously change production source, publish PRs from unknown patches or train productive weights. Health-test subprocesses are not an OS sandbox. Node heap256MiB, concurrency2,deadline360s and evidence64MiB are bounded; Java children have their own declared heap limits. Thermal/battery enforcement for a physical Android device was not validated. Unknown execution receipts are not retried; STOP/recovery tests stay in the existing worker suite. No permanent loop was left running.

1. Check out this normal branch in a new worktree without overwriting another developer:

```sh
git fetch origin sol/continuum-cognitive-evidence-20261027
git worktree add -b research/continuum-next-cognitive-20261027 /absolute/new-continuum-worktree origin/sol/continuum-cognitive-evidence-20261027
```

Inspect `git log -8`, matrix and retained failed evidence. Do not reset/force-push another branch. All full tested code is656b11af; later evidence commit is docs-only.

2. Recreate official dependencies from existing manifests `docs/evidence/sol-jarvis-20261026/fabric-jvm-dependencies.json` and `docs/evidence/shell-repair-20261027/official-host-dependencies.json`. Preserve version/hash checks; use real compiler/JUnit, not declaration or assertion shims. In the current shared environment the exact paths are:

```sh
export CONTINUUM_FABRIC_JVM_DEPS=/workspace/toolchains/kotlin
export CONTINUUM_SHELL_ANDROID_DEPS=/workspace/toolchains/continuum-shell-android-deps
bash scripts/continuum-cognitive-action-jvm.sh /new/external/action-proof
bash scripts/continuum-dialogue-jvm.sh /new/external/memory-proof
bash scripts/continuum-shell-android-compile.sh /new/external/callback-proof
```

Selected scripts are invoked with bash, not assumed executable. Actual process safety was tested against official APIs; still no phone Binder claim.

3. Repeat the same full health cycle after configuring the existing registry snapshot and external Docker policy:

```sh
export PATH="/workspace/toolchains/continuum-kotlinc/bin:$PATH"
export CONTINUUM_FABRIC_SNAPSHOT_FILE=/workspace/continuum-local-state/jarvis-fabric-native-green/registry-snapshot.json
export CONTINUUM_TEST_ISOLATION_CONFIG=/workspace/continuum-local-state/jarvis-docker-report-cap/isolation.json
node scripts/continuum-local-development-worker.mjs --once /new/external/health-evidence
```

These environment values were actually used. On a different host build the existing scratch isolation Dockerfile, pin its actual image ID, public Node toolchain root and non-root UID; do not assume this host's paths/image exist. Use a clean committed research branch or the worker refuses it. `--loop` requires explicit owner activation; creating `/external/health-evidence/STOP` stops it. Keep raw logs and the existing failure classification. Full scripts command is recorded in scripts-final/run.json; expect the unchanged v25 release guard to block research sources, not an APK success.

4. **Next highest-value code test:** run the exact multi-turn correction sequence with the real existing Android/local model path and new trace, including ambiguous/negated correction, explicit literal memory confirmation, revocation and process restart. No APK release requested: prepare native/app-compatible tests first. Extend trace to omitted direct/web/development routes only when needed. Incomplete traces have bounded events/counters (lower bounds), and interrupted UI turns may lack a final journal snapshot. Synchronous advisory-runtime exceptions can lack a terminal event; an invocation without completion is not success. Improve these diagnostics before treating counts as complete observations.
5. Experience reuse remains UNFINISHED. Reuse the existing ExperienceLookup/ExperienceCompiler, validate episode references against fresh durable SUCCEEDED receipt IDs, tool names, exact argument fingerprints and output digests, deduplicate receipts across episodes, enforce owner approval and then propose only known safe plans. Do not learn from copied success labels, model prose or infrastructure failures. Validate on newly authored frozen tasks; current final corpus is opened.
6. The requested three new neural research comparisons have not been completed. Prioritize CTX3 correction abstention, CRE1 eligible-pool reranking and Synapse-v2 relation/negation proof checking against unchanged baseline on NEW splits. Original branches/hashes are in synapse-branch-audit.json. Keep Synapse outside main until actual benefit exists; no blind import. CDXA1 is untrained, SWB2 was rejected, existing opened research sets are not pristine. Productive SWB1 stays unchanged.
7. Local coding runtime is real but this small model failed3/3outputs. Preserve that negative finding. A separately authorized better local model or new distinct task can be tried with its own manifest and fixed budget; do not reset the exhausted task's budget or silently substitute a reference patch. Reuse existing `scripts/continuum-coding-benchmark.mjs` and `scripts/continuum-coding-reference-control.mjs`. Recover original exact fixture with its public Git bundle and prepare a fresh owner research grant; never publish HMAC keys. Freeze a separately authored behavioral oracle before any next inference. Finish RED->GREEN->freshGREEN with a genuinely model-generated changing patch before claiming repair intelligence.

DeepSeek remains NOT_CONFIGURED; ComputeFabric/Virtual Fabric projection remains non-executable planning here. No new distributed-serving infrastructure was built without a concrete compute resource. Main package `at.heinz.deviceai.mentorpaired`, owner certificate, memories and productive model checkpoints remain preserved. No APK, main merge, release freeze update or model promotion was performed.
