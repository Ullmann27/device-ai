# Native runtime closure — interrupted before implementation

Status: **NOT_IMPLEMENTED / NOT_RUN / UNVERIFIED**. Owner's immediate closeout at reported 2% remaining budget overrides further work. No native model, service, C fixture or test was started in this lane. No production source was edited. The current S01 unknown-runtime/global execution barrier must remain.

Branch: `codex/independence-native-closure-20261103`.
Worktree: `/workspace/independence-native-closure`.
Exact baseline: `d2ae042fb95298ccb849a4ba05c9410f74675006`.
Prior MCP work is separately secured in PR459; this branch does not replace it.

## Confirmed code inspection

- `scripts/continuum-local-model-observation.mjs`: `launchLocalModel` already waits for `close` after requested timeout/cancellation, but its `error` event currently rejects immediately. Nonzero exit/signal discards closure details. Fixed namespace/seccomp/rlimits are present; 1800 output-token limit and two native threads remain unchanged.
- `queryPinnedLocalCoding` authenticates actual successful process observations and invalid decoded responses. It has no separate authenticated native-runtime failure envelope.
- `scripts/continuum-local-brain-cli.mjs` emits only the existing invalid-inference envelope; ordinary runtime failures emit a typed stderr code and exit 1 without structured closure evidence.
- `.github/continuum/coding-provider.mjs` authenticates successful/invalid decoded results with a per-request HMAC and private WeakSet. Failed native execution currently loses its exact diagnostic reason in the general process-failure path.

Chief-reported trigger, **not independently rerun here**: S01 CLI exited 1 with zero stdout after 32.52 s; native peak approximately 2.08 GiB and 61.22 CPU seconds. The launcher has `--cpu=ceil(timeoutMs/1000)` with two native threads. Do not infer the exact cause from a signal or raise any budget silently.

## Agreed next implementation contract — still a proposal

Exclusive files for this builder: the two scripts above, `.github/continuum/coding-provider.mjs`, their direct tests and this handoff. Chief owns coding-orchestrator propagation; service builder owns snapshots, resource-lease/barrier decisions and explicit owner budget configuration. No scheduler, runtime pin or release changes.

1. Launcher records a negative observation **only after actual `close`** of the fixed real process. `error`, timeout and cancellation events only request teardown; they must not themselves attest closure. A thrown spawn or absent PID cannot yield closure authority. Keep an internal private brand so injected spawn callbacks or forged events cannot mint production observations.
2. Preserve stdout/stderr SHA-256 and observed byte counts, never raw private output. Distinguish EXIT_NONZERO, SIGNALLED, TIMEOUT, CANCELLED, OUTPUT_LIMIT, GENERATION_UNOBSERVED and OUTPUT_INVALID; do not claim an unobserved CPU-limit cause. Validate admitted model/runtime file identities again after close; changed identities fail closed.
3. Separate wire mode `LOCAL_CODING_RUNTIME_FAILURE_V1`, schema `momega1-local-runtime-failure-observation-v1`, `trusted:false`, `autoApply:false`, `verifiedModelExecution:false`. Its HMAC covers the complete negative observation using the existing per-request observation key. Existing success/invalid-model-response protocols stay unchanged.
4. Planned observation fields: `evidenceClass`, `processClosed:true`, `executionId`, `taskId`, `sourceCommit`, `taskFingerprint`, `requestIndex`, `contextSha256`, `promptSha256`, `requestSha256`, `runtimePromptSha256`, `runtimeSchemaSha256`, `modelSha256`, `runtimeSha256`, reviewed pair/model/runtime revision and evidence IDs, `errorCode`, `terminationReason`, `exitCode`, `signal`, `pid`, `stdoutSha256`, `stderrSha256`, `stdoutBytes`, `stderrBytes`, `elapsedMs`, `isolation:'BWRAP_UNSHARE_ALL'`. No fabricated output-token count.
5. Provider accepts closure authority only after its real `spawnSync` returns normal **CLI exit 1**, with no parent timeout/error/signal. It validates exact schema, MAC, current binding and fixed approved model/runtime pair. Only then add `cliProcessClosed:true`, `cliExitCode:1` and brand the frozen object in a **separate private WeakSet**. Export `isTrustedLocalRuntimeFailureObservation(observation)`. Attach it as `error.localRuntimeFailureObservation` and retain a diagnostic trace. Serialized copies and caller booleans never carry authority, and this object must never satisfy `isTrustedLocalModelObservation`.
6. Small actual C fixtures may emit authenticated CONTRACT_TEST diagnostics but must never be whitelisted as a reviewed model or acquire production teardown authority. No real-model run or global service lease was authorized for this builder lane.

## Frozen next validation work — all NOT_RUN

- Actual C nonzero exit, signal, cancellation, timeout, oversized output and missing generation counter: closure only after close, hashes/counts match, no raw output and no inference claim.
- Spawn throw and error-before-close: no premature certificate; injected fake spawn cannot brand.
- Post-launch file change: no admitted bound closure report.
- Full fixed CLI round trip and normal exit 1; malformed MAC/binding, wrong pair, altered schema, parent error/signal and injected provider callback are rejected.
- Private WeakSet isolation: forged or JSON-round-tripped objects rejected by both relevant trust predicates.
- Preserve existing native process, success, invalid-response, provider and remote-transport regression tests.
- Chief and separate challenger review exact resulting source SHA before any service resource barrier is relaxed. A real approved-runtime positive closure proof remains a separate controlled run.

Independent challenger `/root/independence_mcp_challenger` had begun freezing this lane; no candidate was supplied because implementation had not started. Resume by reconciling its frozen criteria and the latest Chief/service branches, then write meaningful RED tests before the minimal source change. This document does not authorize clearing S01 or automatically replaying its task.
