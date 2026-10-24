# Device AI Continuum runtime/security audit

**Final handoff:** The initial findings below describe base `aa8780a`. The lead subsequently integrated and committed the executor/provider changes at `2c985fba1556a7b7133defa1a4601e4e4df53fdb`. Actual direct executor, integrated profile and controlled two-file owner-worker proofs are recorded in `/workspace/scratch/isolation-capability.md`. The initial disconnected/ignored-executor finding is superseded by those integration changes. The two-file proof is infrastructure-only, with a fixed fixture provider; it is not generative/model-quality evidence. No additional critical false-success issue was found in the final focused source review.

Audited source: `/workspace/continuum`, HEAD `aa8780a8489e9e972e9a35a5af9e22866a0ea6e2` (2026-10-24). Repository operations were read-only; synthetic proofs and logs are under `/workspace/scratch`. No branch changes, commits, repository edits, APKs, installation, model downloads or actual model inference were performed.

## Findings that should control the implementation

1. **The optional OS executor is not integrated into the repair pipeline.** `.github/continuum/repair-loop.mjs:168-171` rejects a non-null executor, including genuine branded executors (`ISOLATION_EXECUTOR_NOT_INTEGRATED`); with no executor, `runProfile` directly executes the fixed host command at line 189. The command runner inherits the host environment at lines 56-72. Git worktrees protect source organization, not host files, credentials, network or sibling processes.
2. **The owner worker silently ignores an `executor` argument.** `.github/continuum/backlog-repair-worker.mjs:79` destructures `candidateProvider` but no executor; its candidate and independent calls at lines 127-128 and 159 do not carry one. `scripts/continuum-isolated-repair-proof.mjs:14` nevertheless passes an executor and expects broad isolated behavior at lines 15-16. Those expectations do not describe this implementation. A safe independent reproduction confirms zero executor invocations and an external host marker written by the fixed baseline test profile: `/workspace/scratch/runtime-executor-ignored-proof.mjs` and `.json`. It used a temporary Git fixture, wrote no secrets, and cleaned up. The correctly reported worker result was `FAILED / NO_FAILURE`, so this proof establishes the execution boundary, not a false repair verdict.
3. **Current free model code remains intentionally barred.** `repair-critic.mjs:99-100` requires `LOCAL_MODEL_PATCH` to replace one complete pure numeric module. `local-numeric-patch.mjs:3-48` accepts a single exported function, up to four unchanged parameters, bounded arithmetic/comparison/conditional expressions, no arbitrary identifiers, calls, imports, properties or statements. Do not remove this restriction merely because the executor module exists. Broader code requires an explicitly separate strategy and real confinement for baseline, candidate and independent runs, with no host fallback.
4. **Neither the Android user path nor CCC1 currently invokes a model candidate provider.** `autonomous-teamleader.mjs:327-345,373-378` builds the default owner driver and calls the repair worker without `candidateProvider`. `ccc1-orchestrator.mjs:219-220` calls the ordinary mobile coding/repair jobs; `continuum-mobile-coding.mjs:293` resolves the default driver. The provider works when explicitly supplied to the Node API in tests; that is a different claim from an owner-triggered generative CCC1 job.
5. **The live local HTTP test is a protocol fixture, not model evidence.** `local-brain-repair.test.mjs:57-70` explicitly states this and returns `TEST_PATCH`. Its assertions at lines 86-87 correctly retain `verifiedModelExecution:false`. `repair-loop.mjs:501-505,531-532` also does not claim inference. `queryLocalBrain` sends a caller-provided/default model label (`scripts/continuum-local-brain.mjs:79,92,106`) but does not attest which GGUF bytes the server loaded. Keep model hash, server binary/version, actual invocation count, device/runtime profile, and protocol-fixture status separate.

## Actual owner-to-runtime paths

### Conversation to an owned Android template

`ContinuumCodingConversation.kt:15-68` is a deterministic bounded requirements dialogue: Notes or Counter, offline only, 512 characters/turn and 32 turns; ambiguous/quoted/conditional/unsupported/negative requests stop readiness. `approve` at lines 72-77 checks current revision and plan hash, then consumes approval once.

Its only UI caller is `ContinuumM5LabActivity.kt`. The dialog at lines 84-103 rechecks Activity lifetime, conversation object identity, draft version, empty draft and catalog validity, then opens the existing Builder with project ID/hash. It does not execute a tool or build. SavedState restoration replays requirements, not approval (lines 18-32,53-63); consumed approvals clear replayable turns (lines 95-98). This path is not a free-form code generator or an LLM requirements interpreter.

### Owner contract test

`ContinuumMobileBuilderActivity.kt:149-174` takes a module/export/cases, validates and asks for explicit start. `ContinuumMobileCodingJob.kt:16-59` only produces a fixed Node executable + fixed builder `code-test`/`code-cancel` argument vector; module path and identifier are tightly bounded. `continuum-mobile-coding.mjs:130-192` maps a UUID to canonical owner TEST backlog task, accepts it, and drives the existing Teamleader/owner contract worker. This generates a bounded contract test for existing code; it is not arbitrary application authorship.

### Owner CCC1 repair

`ContinuumMobileBuilderActivity.kt:181-227` lets the owner select fixed `node|android|jvm` chain and compile/submit/run/status/abort. Mutating steps have explicit dialogs. `ContinuumCcc1Bridge.kt:15-27,59-74` maps only fixed operations and validates UUID/task arguments. `continuum-mobile-builder.mjs:361-388` adapts these into existing `ccc1-cli` compile/submit/run-once/status/abort calls; no model text becomes a shell command. `scripts/continuum-teamleader-termux.sh:27-31,146-163` uses the same builder, CLI and external state, not a second backlog.

Submission runs preflight and creates the bound CCC1 goal/journal (`ccc1-worker.mjs:58-85,143-162`). Execution is `runCcc1Cycle` → `Ccc1Reasoner` → `runTask` (`ccc1-worker.mjs:210-238`) → owner-granted request gate (`ccc1-gate.mjs:38-59`) → `runMobileRepairJob` (`ccc1-orchestrator.mjs:215-226`) → canonical owner REPAIR task (`continuum-mobile-coding.mjs:250-322`) → `executeOwnerRepairTask` → `repairSession`, critic, fixed test chains, fresh independent worktree, receipt verifier. Successful patches remain owner-review artifacts.

### Android transport and receipt protection

`ContinuumMobileBuildPlan.kt:15-21` pins the Termux package, service, Node executable, project and builder script. `ContinuumMobileBuilderActivity.kt:331-412` rejects dead Activities, missing permission/Termux and already-pending slots; persists pending state before dispatch. Callback uses explicit non-exported receiver + mutable one-shot PendingIntent with operation/UUID in populated URI (lines 373-387).

`ContinuumMobileBuildResultReceiver.kt:37-75` matches pending ID and operation, URI identity, result fields and truncation. It stores validated summaries rather than terminal output; abort has its own slot and task binding (lines 77-107). `ContinuumCcc1Bridge.kt:112-135,187-222` validates schema/version/operation/exit/error, bounded tasks/status, and only exposes a verified patch hash when COMPLETED + intact + matching confirmation prefix. These are IPC/data-integrity checks, not remote attestation of Termux or its model.

## Failure paths and remaining limits

- **Traversal and symlinks:** `ccc1-path-policy.mjs:9-42` canonicalizes existing ancestors, including links, and rejects state in source. Preflight rejects protected branches, dirty source, missing/escaping runtime files and Node <20 (`ccc1-local-preflight.mjs:19-64`). Owner-worker output paths resolve existing ancestors and reject overlap with source/backlog (`backlog-repair-worker.mjs:27-43,83-89`). Candidate read/write targets use `boundedWorktreeFile` (`repair-loop.mjs:338-349,406-412,578-582`): ordinary existing files, no traversal or linked parent, all targets checked before any write.
- **TOCTOU remains a stated trust assumption:** `ccc1-path-policy.mjs:4-7` expressly requires runtime ownership/protection against concurrent filesystem changes. State key/receipt/patch readers use normal follow-symlink reads (`ccc1-journal.mjs:19-25`; `continuum-mobile-coding.mjs:77-83`; `ccc1-worker.mjs:175-182`). They must live outside candidate-accessible storage. A 0600 key does not protect against arbitrary child code run under the same host UID; this is why actual confinement matters.
- **Crash/replay:** submission UUID reservation is exclusive, validates same goal, and unresolved reservations require inspection (`ccc1-worker.mjs:143-162`). Recovery derives deterministic per-request UUIDs, distinguishes NOT_STARTED / FINISHED_UNRECORDED / INTERRUPTED, verifies journal and bound HEAD, prunes only missing worktree records (`ccc1-recovery.mjs:17-36,54-84`). Interrupted claimed work requires owner approval; completed receipts replay rather than execute again (`ccc1-orchestrator.mjs:194-210`; `continuum-mobile-coding.mjs:260-265`). HMAC/hash-chained journal rejects forgery under its separate runtime key (`ccc1-journal.mjs:1-6,19-25`). Preserve these existing contracts for any new provider.
- **Cancellation:** abort first marks the canonical backlog job ABORTED, then tries owner journal ABORT; active writer returns WRITER_ACTIVE instead of falsely reporting completion (`ccc1-worker.mjs:103-127`). Worker checks abort at boundaries and uses a watchdog to stop a running test process group (`backlog-repair-worker.mjs:114-121`; `repair-loop.mjs:61-75`). Existing tests cover detached grandchild, timeout, owner abort, parent death and pre-aborted task. Docker integration needs equivalent teardown/abort evidence, not just cancellation flags.
- **Budget:** CCC1 gate and orchestrator enforce owner grants, rounds, deadlines, accumulated CPU and parent RSS (`ccc1-gate.mjs:38-51`; `ccc1-orchestrator.mjs:179-180,213-220`). These are not hard child resource limits. The optional container executor defines memory/PID/CPU/time limits; default host subprocesses do not inherit those container limits.
- **Correctness evidence:** owner worker requires patch bytes/hash/report agreement, then independent clean application and a real second fixed-profile run (`backlog-repair-worker.mjs:145-194`). The verifier checks job/commit/chain, patch bytes, failing baseline, commands and counts (`ccc1-verifier.mjs:53-94`). Same-candidate execution in two directories is useful regression evidence, not an independent semantic oracle for unrestricted code.
- **Learning boundary:** `repair-ledger.mjs:79-88,109-122` already recognizes `LOCAL_ISOLATED_PATCH`, preserves pending owner review and excludes broad code from learned experience/replay even with `independentPass:true`. Preserve this. Existing narrow numeric local recipes replay only under explicit local scope opt-in (`local-brain-repair.test.mjs:48-54`; `repair-loop.mjs:524-527`).
- **Control paths:** critic blocks test/control/build/credential paths (`repair-critic.mjs:10-24`) and patterns (`37-42`), but regex filtering is not an arbitrary-JS sandbox. New local broad scopes must be trusted allowlists, not model-supplied paths; keep `.github`, scripts, test fixtures, manifests, build configs, policy, verifier and runtime control files immutable. The existing narrow provider scope grammar excludes dot-prefixed `.github` (`local-brain-repair.mjs:8-17`); do not accidentally widen it through a direct provider callback.

## Optional model/runtime integration

`createLocalBrainCandidateProvider` is a trusted opt-in adapter (`local-brain-repair.mjs:10-41`): 1–2 approved JS paths, bearer key checked before spawn, small source/diagnostic prompt, fixed non-shell CLI, timeout/output bound, cleared NODE_OPTIONS/coverage/proxy flag. `queryLocalBrain` only contacts `http://127.0.0.1:8089`, denies redirects, caps streaming response bytes, constrains requested JSON and independently checks exact patch keys/path/text sizes (`continuum-local-brain.mjs:7-13,45-69,77-155`). It returns untrusted, non-auto-apply data.

`continuum-local-llama-termux.sh:5-44` is manual opt-in ARM64 Termux startup with fixed loopback listener, required cache-disable/auth flags, one parallel slot and bounded context. It does not download models. Existing file/type/key-length checks do not establish server binary provenance or loaded GGUF hash. Android's `LocalModelHost.kt:18-22` owns its separate LiteRT catalog/runtime; a loaded Android `.litertlm` model is not proof that the Termux llama.cpp worker uses that model. Current documentation explicitly distinguishes those (`CONTINUUM-MODULE-WIRING-AUDIT-20261024.md:7-11`; `CONTINUUM-LOCAL-RUNTIME-OPTIONS-20261024.md`).

## Optional Docker executor: initial source audit, before the successful follow-up proofs

`isolated-executor.mjs:73-97` accepts immutable local image IDs, unprivileged UID/GID, narrow numeric resource policy and concrete toolchain roots. It snapshots tracked files and rejects symlinks/unmerged/unsafe or oversized source (`54-70`); container gets no host writable bind, no network, read-only root, dropped capabilities/no-new-privileges, limited PIDs/memory/CPU and tmpfs work areas (`105-127`). Teardown checks kill/remove and rejects uncertainty (`129-152`). Caller-supplied executor objects cannot confer trust because instances are branded in a WeakSet (`14-15,162`).

Important compatibility limits: snapshot includes tracked paths only, so a newly generated untracked contract test is absent unless deliberately admitted by a trusted snapshot contract. Read-only source prevents tests that legitimately mutate project source. There is no automatic Node dependency-cache mount; Android requires the fixed toolchains and read-only Gradle modules cache. Profile support and trusted output handling need explicit integration; do not add a hidden fallback. Returned reports are explicitly untrusted (`17-25,159`), so a container protects host state without attesting test semantics.

## Executed verification and host inventory

Observed tools: Node `v24.19.0`, Git `2.52.0`, Java runtime `21.0.12.1`, Python `3.12.14`, Docker server `28.4.0`, unprivileged UID/GID `1000:1000`. Initial PATH had no `javac`, `kotlinc`, `kotlin`, `gradle`, `adb` or `shellcheck`; no Android SDK or Gradle/Kotlin installation was found in the checked standard `/opt` locations. `docker image ls` returned no local images. During this initial audit no Docker image was built/pulled and no container isolation or physical Android test was performed; the later approved Docker proofs are recorded separately below and in `isolation-capability.md`. Managed environment policy reported enforced unrestricted HTTP networking and no bound secrets; no credential contents were inspected.

Executed at audited source, no skips:

```sh
node --test .github/continuum/isolated-executor.test.mjs \
  .github/continuum/ccc1-path-policy.test.mjs \
  .github/continuum/ccc1-local-preflight.test.mjs \
  .github/continuum/local-numeric-patch.test.mjs \
  .github/continuum/local-brain-repair.test.mjs \
  scripts/continuum-local-brain.test.mjs
# 29 passed, 0 failed, 0 skipped. Log: runtime-security-targeted.tap

node --test --test-concurrency=2 .github/continuum/ccc1-bridge.test.mjs \
  .github/continuum/ccc1-repair.test.mjs .github/continuum/ccc1.test.mjs \
  .github/continuum/step-watchdog.test.mjs scripts/continuum-mobile-coding.test.mjs
# 37 passed, 0 failed, 0 skipped, ~19.7 seconds. Log: runtime-security-ccc1.tap

node /workspace/scratch/runtime-executor-ignored-proof.mjs
# executorInvocations:0, hostMarkerWritten:true, originalFixtureClean:true.
```

The tests above exercise real temporary Git worktrees, HTTP loopback fixtures, process kills and replay/cancellation. They do not establish model competence, container integration or Android toolchain success.

Useful next targeted commands after implementation: existing guard/transport tests above; relevant `repair-loop.test.mjs`, `repair-ledger-independent.test.mjs`, `backlog-repair-worker.test.mjs`, `cegis-critic-safety.test.mjs`, `repair-cycle-safety.integration.test.mjs`; new genuine container adversarial proofs. Full Node/backend gate is `.github/workflows/continuum-v19-master-integration-qa.yml:44-47`. Android gate uses Gradle 8.11.1 + JDK17 + SDK36 and `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with preview/widget-test flags (workflow lines 48-58). No `gradlew` exists in this tree; docs showing `./gradlew` need adaptation to installed Gradle.

If deliberately provisioning Kotlin-only checks, `scripts/android-kotlin-unit-check.sh:11-19` requires Kotlin CLI, javac, JUnit/Hamcrest and the external pinned framework/dependency jars. It uses compile-only LiteRT stand-ins and expressly excludes Robolectric UI, Gradle/AGP/lint/APK/device (`1-9,23-37`). A minimal pure-JVM subset could compile `ContinuumCodingConversation`, `ContinuumOwnedProject`, `ContinuumMobileCodingJob`, `ContinuumMobileBuildPlan`, `ContinuumCcc1Bridge`, `StrictJson` and their direct dependencies/tests after toolchain setup; do not call that an Android build.

## Bounded extension acceptance recommendations

1. Thread the trusted executor explicitly through the canonical owner worker/session, all baseline/candidate/independent profiles, and any syntax/compile phase that executes candidate-controlled tooling. Reject unknown/unavailable executor or profile before host execution; add regression proving `executor` is never silently ignored.
2. Keep deterministic mode default. Select any generative mode, scope, model and external capability only from trusted owner configuration. Preserve existing CCC1/backlog IDs, grants, leases, abort/recovery and owner-review semantics; avoid a new queue or receipt protocol unless versioned compatibility requires it.
3. First prove containment using deliberately hostile fixture code: host marker/home/source/key reads and writes denied, env secret absent, remote and host-loopback access denied, subprocess/resource limits enforced, detached descendants killed after timeout/cancel/parent death, torn/lost callback recovery safe. Test both candidate and independent generations and teardown failures. These require real executor invocation counters and observable host-side denial evidence, not receipt fields alone.
4. Keep broader code a distinct `LOCAL_ISOLATED_PATCH` review-only strategy. No training/replay/confirmed knowledge or automatic merge/install. Treat code-generated test reports as untrusted and add independently authored holdouts for the capability claim.
5. Add provider-negative cases for traversal/linked parents, stale source hash, duplicate/extraneous patch fields, protected paths, oversized/truncated/slow responses, wrong auth, model change/unavailable toolchain, abort during model call, replay with conflicting goal/config and process death before/after execution. Record source/config/model identity without raw prompts, secrets or model text in public receipts.
6. Mobile-first remains binding: Docker availability on this Linux host is optional capacity, not a required Android service. Report `UNAVAILABLE` honestly for a missing Termux confinement/runtime/toolchain capability; keep the narrow existing route functional. Compile preflight's READY presently checks repository/Node/state, not actual Android/JVM toolchain readiness.

## Instructions/document provenance

Read `AGENTS.md`; `CONTINUUM-EXPERIMENTVERTRAG.md`; entire `CONTINUUM-EXPERIMENTE.json`; current linked Opus-night status and active-checkpoint; CCC1-on-device, three-pass-audit, module-wiring and local-runtime docs. Historical document SHAs and historical CI/device totals were not treated as current-run proof. Live Issue #41 was not independently fetched in this read-only subtask; the lead should use its own fetched issue context for final scope. Relevant register: EXP-00/01/02/05/07/08/09/11/12. Contract requires one versioned backlog/repair pipeline, fixed commands, real negative tests, honest unavailable toolchains, isolated opt-in research and owner review before main/install.

## Final security handoff after integration

The original executor-ignore gap is fixed in the integrated source. A direct branded `executor.execute` proof, the actual `runProfile` integration, and a controlled two-file owner REPAIR all executed inside the real kernel/container boundary. Container network/host-state/privilege/process-limit probes and timeout teardown passed. The two-file baseline ran 3 tests with 2 failures, its fixed fixture patch passed all 3, and the independent run passed all 3; original source remained clean, no merge authority was granted, and broad-candidate learning remained empty/PENDING_OWNER_REVIEW. A missing cancellation opt-in was rejected before backlog claim. These controlled provider proofs establish infrastructure, not generative inference.

The lead confirmed that the initially observed omission of isolation metadata in independent profile receipts has been fixed; current final source review uses HEAD `2c985fba1556a7b7133defa1a4601e4e4df53fdb`. The lead's separate real-model proof and exact frozen-source run are separate evidence and should be described by that runner.

Remaining limits: candidate-controlled JUnit/XML and exit behavior remain untrusted even in an isolated second run; they are not an independent semantic oracle. Preserve review-only/no-learning handling. Docker cancellation is explicitly CHECKPOINT_BOUNDARY_ONLY, with timeouts bounding a running step; it is not the existing host watchdog's in-step abort. Node containment and bounded real-model tests do not establish Android/Fold7 execution, Kotlin/Gradle APK capability, production reliability or generalized coding competence. No further proof or test loops were started after the lead requested finish/handoff.
