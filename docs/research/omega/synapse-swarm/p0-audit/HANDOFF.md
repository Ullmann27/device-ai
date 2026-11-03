# Synapse P0 independent integration audit

**Scoped source compatibility accepted; MΩ-1 remains BLOCKED_LOCAL_MODEL_UNAVAILABLE / FOLD7_NOT_RUN.**
Frozen source `713949a86e10fb4fe4cc22d03f1af1afdcdf51e8`, compared with Chief `efe622dda00e620952483dedad19257efb3284e6`.
Acceptance froze in `404b008fb95c013e8f6c12ca6b5eebbdce35f9e8` before this audit. Review-only branch `codex/synapse-p0-audit-20261103`, draft [PR421](https://github.com/Ullmann27/device-ai/pull/421). No production source edits.

## Fresh checks

| Check on 713949a | Result | Boundary |
|---|---:|---|
| Canonical loop, verifier regressions, workspace policy, isolation policy, technical recall/durability, bridge | 73 PASS / 0 FAIL / 0 SKIP | Contract + actual bounded host tests; no Docker profile execution |
| Native process observation author regressions | 22 PASS / 0 FAIL / 0 SKIP | Actual bwrap/seccomp/native processes; fixed-output ELF, not neural inference |
| Previously frozen independent runtime/provider checks | 35 PASS / 0 FAIL / 0 SKIP | Actual synthetic native processes and provider rejection |
| Previously frozen independent memory + canonical-anchor checks | 45 PASS / **1 FAIL** / 0 SKIP | Retained stale source-specific C03 failure, explained below |
| New independent admission-counterexample checks | 2 PASS / 0 FAIL / 0 SKIP | Frozen/fabricated/copied objects cannot obtain private producer/verifier identity |
| Real Docker workspace lifecycle / typed repair / independent claim review | **18 PASS / 0 FAIL / 0 SKIP** | Actual rebuilt scratch image; scripted/deterministic repair, no neural inference |
| Android full suite / actual GPU / Fold7 | **NOT_RUN here** | Root/QA owns full Gradle; no connected device |

Counts describe separate executions; do not sum overlapping suites into an intelligence score. Every command, exit, raw TAP and scope is in adjacent JSON/TAP. Root must rerun affected checks on the exact new integrated source.

The unchanged independent memory C03 asserted that both `isTrustedLocalModelObservation` and `isTrustedCodingVerification` exports were absent. That described the earlier memory-only source freeze. Accepted runtime/verifier integration now exports read-only predicates over private WeakSets. The complete original run is preserved as **45/46**, not rewritten or called green. All 45 behavioral persistence, grant, corruption and withdrawal checks passed. The two separately authored counterexamples establish that public predicates do not grant private brands to Boolean flags, JSON, frozen data, clones or prototype-derived objects. These replace the obsolete *integration expectation*, not the original frozen evidence. Production scope has no public brand setter.

## Source/review equivalence

Fresh GitHub snapshots: PR413 head `25c0f2dc9d4840f2d77be0e92882bfb53c903cd7` remains WIP final QA; PR419 head `f747661a338ad844e6f8b8efb889c1d8fd11fb92` accepts Android source `037370f0544d3ed2ee7686082c41c78046daa64d`; PR420 head `794e745d8afc28db0e190e2de8a4f18ca53f82d9` accepts workspace source `8aba17c6cc24b00a66d6c5de59d0c1fe1dc5c2db` and is merged into MΩ.

Actual Git comparison confirms the three reviewed Android production files and four reviewed workspace/executor/worker/Kotlin wiring files are byte-identical between their accepted freezes and 713949a. ChiefWorkerPool is byte-identical to efe622dd (equivalent cherry-pick already present). Read-only `git merge-tree --write-tree efe622dd 713949a` exits zero, tree `ecc41a7831df668107243d23ebf46c543768dedd`. This proves textual merge compatibility, not final QA. MΩ alone lacks the newer 3D asset/runtime and adaptive UI from Chief; preserve both parents through root-controlled integration.

713949a adds an actual `runCodingTask → bindCcc1Workspace` caller. Its retained prior RED/GREEN actual-container fixture is host software integration with a scripted candidate; no local model solved it. The old statement that this binding is still absent is superseded. The original pinned image was absent. Under the Chief's follow-up allocation, the existing `scripts/continuum-isolation.Dockerfile` (FROM scratch) was rebuilt from an otherwise empty local context with `--network=none --pull=false`, no downloads. Actual new image: `sha256:4838093b14e654fcf3b69decfe8b8a1fe75ef6c288ea5dadc4e169131716494c`. A narrowly scoped loader changes exactly one fixture image string in each of four known test modules; it changes no production module or assertion and never impersonates the old pin. All 18 actual typed/canonical-repair, lifecycle, dual-workspace and independent claim tests passed. New container IDs/policy observations, genuine synthetic RED→GREEN/fresh verification, replay rejection, bounded lease timeout and checkpoint revocation evidence are retained. This is actual host software execution, not local-model problem-solving.

## Actual caller matrix for the next increment

| Existing component | Actual caller | Missing link / constraint | Owner | Acceptance |
|---|---|---|---|---|
| LocalModelHost / LocalModelRuntime | AgentActivity.runLocal adapter → BoundedAgentOrchestrator | No authenticated Termux access to loaded Android LiteRT; no host approved weights/runtime | Chief / runtime | Genuine inference provenance + serialized leases + cancellation on device |
| Typed coding task / bridge | Manual owner draft → complete review → Builder → fixed Termux RUN_COMMAND → Node | Main model output does not automatically create typed drafts | Chief / Android | Candidate-only parsing, current model/session binding, owner approval remains separate |
| CCC1 workspace | Node runCodingTask → bindCcc1Workspace → existing repair job/claim | Current image rebuilt and host path rerun; Android process path untested | Workspace builder + independent checker | Actual isolated execution/cleanup, wrong claim/replay/source/grant denial |
| ChiefWorkspaceWiring | Existing Kotlin tests inject fixed Node port | No production Activity caller / authenticated Android transport | Chief | Real task-origin binding + canonical grant + receipt delivery + late callback rejection |
| ChiefWorkerPool | AgentActivity.showChiefWorkerStatus | Current live app use is a read-only teamleader status worker, not 20 working model bots | Registry/runtime builder | Identity registry must not label dormant metadata as active execution |
| Technical experience | runCodingTask → recallTechnicalExperiences → bounded provider prompt; verified-candidate hook → recordTechnicalExperience | No actual local neural producer can reach admitted experience tier; changed-code transfer rejected | Memory + reviewer | Genuine admitted result, restart, current task grant, historic-source label, withdrawal at full capacity |
| Existing AgentMailbox | ChiefWorkspaceWiring mailboxReporter, routines adapters | Delivery ephemeral; CCC1 journals authoritative; no new outbox implied | Workspace/communication builder | Real report correlation, bounded queue, rejected delivery visible |
| AvatarDirector/UI | Existing Chief graphical branch | MΩ branch omits newer graphics; selected bot identity must not create a second brain/state machine | Chief/UI | Preserve original orb/GLB, actual runtime state only, one selected full renderer |

## Environment and remaining P0 gates

Current workspace contains no `*.gguf`, `*.litertlm`, `*.safetensors`, `llama-cli` or `llama-server` files found by the recorded search. PATH has Node, Docker, bwrap, prlimit and gcc; no adb or llama CLI/server. No model downloads, new services, credential reads or purchases were attempted. Native fixed-output fixture success is expressly not a model proof.

The current production observation producer deliberately emits HOST_INTEGRATION and never immutable `verifiedModelExecution:true`. Even supplying a compatible model file alone does not complete MΩ: a separately reviewed genuine inference observation producer and unknown-task RED→GREEN/independent verification/experience continuity are still required. No hidden external model supplied a benchmark solution here. Device version, memory, thermals and energy remain unavailable.

Next: root merges accepted source into isolated Synapse branch, preserves graphical parent, reruns exact-source gates; provision only explicitly supported/authorized runtime and weights; complete actual local unknown-task loop before claiming S2 model autonomy or S3 learning. Registry persistence can proceed independently but cannot substitute for MΩ evidence. Quota NOT_VISIBLE; checkpoints retained remotely. Rollback omits this review-only branch; never restore owner data or revoke tombstones backwards.
