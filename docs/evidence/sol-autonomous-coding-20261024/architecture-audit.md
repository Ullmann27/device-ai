# Device AI Continuum architectural audit

Pinned source: `aa8780a8489e9e972e9a35a5af9e22866a0ea6e2` (PR124 release integration), inherited PR123 `cc453fac`. Read-only source audit; production checkout untouched. Sources were extracted with `git archive` to `/workspace/scratch/aa8780a-source`; every line reference below is relative to that immutable snapshot. References to other branches explicitly name the ref. No model execution, Termux/device test, APK build, or install is claimed.

Read: `AGENTS.md`; experiment contract/register; linked current-entry, active-checkpoint and CCC1 interface documents. Relevant experiments: EXP-00/01/02/03/05/08/09/11/12. Issue41 is an external live requirement; this subtask did not independently fetch its current body. Source instructions require mobile-first, one shared backlog/repair/evidence pipeline, interchangeable toolchains/model candidates, fixed commands, and no promotion from untrusted text to authority.

## Actual invocation chain

1. **Android boundary.** `ContinuumCcc1Bridge.kt:15` exposes COMPILE_REPAIR, SUBMIT_REPAIR, RUN_ONCE, STATUS, ABORT only. `command` at :59 constructs the fixed Node executable and builder script plus validated chain/UUID/task-id arguments. The current Android CCC1 route cannot submit a free general coding task.
2. **Existing transport.** `scripts/continuum-mobile-builder.mjs:66` has the five fixed CCC1 operations; :358-388 delegates to the existing CCC1 CLI and filters review data for Android. This is the existing Termux callback transport, not a model-driven shell.
3. **One scheduling adapter.** `ccc1-cli.mjs:117` invokes `runTeamleaderLoop` with `work => runCcc1Cycle`. The worker selects one nonterminal/non-owner-waiting task at `ccc1-worker.mjs:218-235`; terminal experiences are recorded idempotently.
4. **Durable reasoner orchestration.** `ccc1-orchestrator.mjs:151` obtains the existing journal/writer lock and recovery; :179 checks the bound commit; :219-226 calls `runMobileRepairJob` / `runMobileCodingJob`, then the matching receipt verifier. :254-264 gates each reasoner request before it can run. Backpressure permits one pending request.
5. **Canonical backlog.** `scripts/continuum-mobile-coding.mjs:267-281` creates/accepts an owner REPAIR in `development-backlog.json`; :293 runs `runTeamleaderCycle` using `localTeamleaderDriver`. :300 rejects another task being processed first. Job UUID maps deterministically to one backlog task (:39); receipts prevent finished-job replay (:260-265).
6. **Repair worker.** `autonomous-teamleader.mjs:373-378` calls `executeOwnerRepairTask`. `backlog-repair-worker.mjs:45-50` requires READY owner REPAIR pinned to HEAD/fixed chain; :105-128 claims and calls `repairSession` in a new worktree, with cancellation checkpoints/watchdog.
7. **Candidate gate/execution.** `repair-loop.mjs:495-511` resets the worktree and executes baseline fixed profiles; :524-557 obtains an optional local candidate; :572-615 runs the shared critic, exact edit application, JS/Kotlin syntax gates, fixed regression chain and evidence sufficiency. Only then is a patch produced (:618-624).
8. **Fresh independent check.** `backlog-repair-worker.mjs:145-174` rehashes the patch, validates the report, creates a second clean worktree, reapplies the same patch, reruns the fixed chain, and persists independent verification. :180-194 atomically submits/completes the same backlog task with executed-test evidence. Failures remain failures and negative ledger experience (:197-205).
9. **Receipt authority.** `ccc1-verifier.mjs:53-94` binds repair receipt to job/commit/chain, reads and hashes patch bytes, requires a real failing baseline, green candidate and independent runs with adequate counts and fixed commands. `ccc1-orchestrator.mjs:227-233` alone emits verifier RECEIPT with HMAC. The reasoner can critique that verdict but cannot originate a verifier receipt. Patch review still precedes merge/install.

The second worktree establishes independence from candidate build state. It is not another organization's judge, remote attestation, or an OS sandbox. Reports and native/toolchain runtime behavior need their own evidence.

## Verified gaps and available seams

### 1. General task contract is missing; this is not general code synthesis

- `ccc1-envelope.mjs:24-39`: only `node-contract-test` and `node-repair` capabilities/permissions.
- `ccc1-envelope.mjs:67-103`: goal is text plus primitive examples OR one fixed repair chain; TEST_REQUEST has one module/export/cases OR a chain.
- `ccc1-interpreter.mjs:45-72`: candidate index scans only exported functions in `.github/continuum/*.mjs`; no Kotlin/Java repository search.
- `ccc1-interpreter.mjs:89-101`: ordinary goals become at most four primitive input/output acceptance cases. No examples means abstention, not feature implementation.
- Contract mode adds a bounded test of an already-existing export (`owner-contract-worker.mjs:105-151`); it does not implement that export.
- `repair-loop.mjs:511`: a green baseline returns `NO_FAILURE`. A requested new feature in an otherwise green project never reaches candidate generation. General tasks require explicit acceptance semantics, rather than fabricating diagnostics or weakening repair verification.

### 2. Existing local inference adapter is not connected to the Android/CCC1 repair path

- `local-brain-repair.mjs:10-38` creates an explicitly scoped candidate provider; `developer-agent.mjs:168-177` is the production caller that constructs it, only under `owner-repair --local-brain true --local-paths ...`.
- `executeOwnerRepairTask` already accepts and forwards `candidateProvider` (`backlog-repair-worker.mjs:79,127-128`).
- `localTeamleaderDriver()` has no provider options and calls the worker without one (`autonomous-teamleader.mjs:327,373-378`). `runMobileRepairJob` likewise has no candidate-provider setting (`continuum-mobile-coding.mjs:250,293`). Default CCC1 repair therefore stays deterministic.
- Current scope: 1–2 explicitly allowed `.mjs`/`.js` paths, source <=1400 characters each (`local-brain-repair.mjs:8-25`); model output is one unique exact hunk, <=1600 characters each side (`repair-loop.mjs:537-547`).
- Shared critic requires exactly one *complete pure numeric module* for `LOCAL_MODEL_PATCH` (`repair-critic.mjs:99-100`). General Kotlin/multifile code does not pass this policy today.
- Low-level multiple edits already exist: `applyEdits` returns a map of all changed files (`repair-strategies.mjs:63-71`), critic default allows up to 3 files/40 changed lines (`repair-critic.mjs:8,101-121`), and repair application checks all destinations before first write (`repair-loop.mjs:577-582`). This is a useful existing seam.
- Creation is not supported by the repair path: `boundedWorktreeFile` requires an existing ordinary file (`repair-loop.mjs:334-349`), and `applyEdits` rejects missing files (`repair-strategies.mjs:67`). New-file creation needs an explicit separate permission/operation and safe-parent/nonexistence checks; do not accidentally treat empty oldText as create.

### 3. Runtime, data-only proposal and execution are separate

- Android `LocalModelHost.kt:13-22` supplies the process singleton LiteRT runtime/catalog. `LocalModelReasoningAdapter.kt:29-52` binds model identity/hash and deadline; :93 uses `streamThinking`; :107-123 rejects tool proposals. It cannot perform privileged coding work.
- Android `DevelopmentCoordinator.kt:99-122` already runs local DEVELOPER/CRITIC/TEST_DESIGNER/JUDGE roles over selected sources and emits data-only multi-file proposals. `DevelopmentTypes.kt:84-119` already defines hashed source snapshots and expected-base-hash edits. This is candidate material, not real executed-test verification.
- `DeviceDevelopmentBacklog.kt:75-95` records this as kind IMPLEMENT/source android and submits a PROPOSAL hash, leaving VERIFYING; subject holds only source count/first path. It does not transmit complete structured edits/acceptance/source commit into the Termux repair worker. The logical backlog format is shared, but private Android and Termux files are not magically one synchronized store.
- Node `continuum-local-brain.mjs:7,77-80,126-152` is a distinct authenticated fixed-127.0.0.1 llama.cpp text/patch client. It validates bounded output and labels it untrusted. `checkLocalBrain` health is not model digest/execution attestation. Android LiteRT loaded identity is not proof this Node endpoint used the same model.
- `learning/lm/LmRuntime.kt` has generation/loading APIs but no main-source caller outside its own class. The stale `ccc1-interpreter.mjs:6` comment mentions `ccc1-lm1-core.mjs`, which does not exist at the pinned tree. CCC1 CORES use lexical/probe/disambiguation routes, not LM1.

### 4. Retrieval exists in parts, but is not connected as task context

- `code-index.mjs:109-146` builds behavioral fingerprints/closure hashes and test links for the same narrow exported-module index. `ccc1-worker.mjs:225-234` writes it on idle but constructs task candidates directly with `indexExports`; it never consumes the saved index for context retrieval.
- `code-analyzer.mjs:126,202,262` already supplies a Kotlin symbol index, call chains, and source collection. Repair context constructs it (`repair-loop.mjs:406-429`), currently for deterministic strategies.
- `scripts/continuum-local-context.mjs:24-66` already packs bounded hashed excerpts and reports omissions/truncation; :69-83 provides diagnosis-only loopback requests. It is standalone; callers supply fragments, not repository retrieval.
- Reuse these components behind a read-only scoped context interface. A behavioral fingerprint is a retrieval hint, not proof the candidate is correct. Keep hash/base binding separate from semantic ranking.

### 5. Isolation capability is present but deliberately disconnected

- `isolated-executor.mjs:73-162` has a branded optional Docker executor with pinned image, no network, read-only snapshot, limits, and fixed profiles. It is optional host capacity, not an Android requirement.
- At pinned HEAD, `repair-loop.mjs:168-176` rejects every trusted non-null executor with `ISOLATION_EXECUTOR_NOT_INTEGRATED`; `repairSession`/owner worker don't accept it. General candidate execution cannot claim this confinement.
- Keep existing restricted numeric mode safe. An arbitrary JS/Kotlin candidate needs a real suitable executor capability (or must return UNAVAILABLE), particularly because executing test suites may import/run candidate source. Worktrees only separate state.

## Duplicated / disconnected chains

- `.github/continuum/reasoner-dialog.mjs` has its own journal/state/verifier and calls the same `runMobileCodingJob` (:119,:168,:224); `hypothesis-reasoner.mjs` duplicates indexing/ranking. Current references are pilot/test scripts, not the Android builder's active CCC1 route. Preserve as research/historical provenance; do not extend it into a second active pipeline.
- Kotlin four-role proposal generation is an existing frontend candidate producer. Make it an adapter to CCC1/canonical worker instead of adding a second executor/verifier.
- `developer-agent repair` and Teamleader baseline repair also share `repairSession`; use shared stage extraction if IMPLEMENT support is added, preserving owner-repair baseline and verifier rules.
- The shared Node/Kotlin backlog schema already has generic kind/subject/artifact support (`development-backlog.mjs:21-29,35-42`, `DevelopmentBacklog.kt:27-34`). Extend versioned content-addressed task/candidate artifact contracts, not a new queue or parallel state machine.

## Remote branch comparison (actual fetched refs)

Comparisons use `git rev-list --left-right --count aa8780a...REF`; unique changes checked from merge base, not merely commit dates.

| Ref | Pinned-only / ref-only commits | Coding conclusion |
| --- | ---: | --- |
| integration/continuum-0320-all-connected-20261024 @ cc453fac (PR123) | 14 / 0 | Already ancestor. PR124 adds release/source-gate/build metadata/test changes; worker/repair/model adapters are unchanged. |
| sol/continuum-0320-integration-20261024 @ 3d7326d0 | 42 / 0 | Ancestor; no missing newer coding implementation. |
| opus/continuum-sol-handoff-20261024 @ d0f0892e | 117 / 0 | Ancestor. |
| sol/continuum-max-20261024 @ f65181fe | 110 / 0 | Ancestor. |
| chatgpt/continuum-cdx1-postrun-audit-20261024 @ 06e102e7 | 42 / 3 | CDX postmortem/cycle tests and AgentActivity guard; no general coding worker. |
| chatgpt/continuum-core-mirror-20261024 @ 69145686 | 42 / 5 | UI and UI test changes only. |
| integration/continuum-0320-core-cdx-20261024 @ 23fe6916 | 42 / 6 | Combined UI/CDX research changes; no canonical task or local coding adapter. |
| opus/continuum-next-20261023 @ ef3832ec | 166 / 1 | Unique test-source context-negation fix; not coding capability. |
| opus/ccc1-e2e-20261023 @ 8f88cc44 | 290 / 1 | Unique handoff doc only. |
| opus/continuum-mobile-coding-e2e-20261022 @ 1d92db03 | 610 / 6 | Important missing *functional candidate*: real isolated-profile execution, trusted executor threading, LOCAL_ISOLATED_PATCH, bounded Kotlin excerpts. Old branch is far behind; selectively port needed logic and preserve current watchdog/recovery/evidence/mobile changes. |

For the last ref: `local-brain-repair.mjs:10` adds sourceMode numeric/isolated, supports Kotlin paths and excerpts near diagnostics; `repair-loop.mjs:416-469` threads executor into baseline/checks and selects LOCAL_ISOLATED_PATCH; `runProfile` invokes executor.execute. Current pinned tree intentionally has the fail-closed stub instead. Whole-file replacement would discard current in-flight cancellation watchdog and newer evidence/lease guarantees. Other named 24-Oct runtime/dialogue/release branches were ancestors of the pinned source.

## Minimal compatible implementation interfaces

1. **Versioned TaskSpec artifact, accepted through existing CCC1/backlog.** Bind task/order UUID, exact repo/base commit, goal, explicitly authorized read/write paths, acceptance/profile IDs and acceptance-artifact hash, provider policy, limits. Reference this by hash from existing task subject; keep large sources/edits out of subject's 300-character values. Add an explicit IMPLEMENT/GENERAL capability/message payload with protocol hash migration; preserve old journal hashes. A green baseline remains valid for implementation tasks only when independent owner-defined acceptance tests demonstrably distinguish the requested new behavior. Do not reinterpret old REPAIR receipts.
2. **Read-only ContextProvider.** Input is TaskSpec + bounded query/diagnostic; output is source/base-bound fragments `{path, blobSha256, startLine, endLine, excerptSha256, content}` plus truncation/omission metadata. Allow literal search, symbol/call-chain lookup, and file reads only inside an allowlisted clean snapshot. Select with existing Kotlin index + bounded text search; use code-index behavioral hints where applicable. Exclude secrets/control-plane data. Queries never become shell commands. Reuse context packer; the provider, not model output, supplies authoritative bytes/hashes.
3. **CandidateProvider returns structured Candidate only.** Bind schemaVersion/task/base/context hash/provider identity, `edits[]` containing path + expected full-file hash and exact replacement/hunk, notes; model cannot set strategy/permissions/test commands/verdict/merge flags. Validate all edits and aggregate limits atomically before any write. Use existing `applyEdits`/critic/syntax/regression stages; add policy-specific general capability rather than removing numeric restriction globally. Translate Android `ProposedEdit` data to the same shape if desired. An adapter selection is trusted configuration, not a task-supplied executable/path/URL.
4. **Thread trusted provider/executor settings through existing driver.** Add trusted options to localTeamleaderDriver -> executeOwnerRepairTask/shared candidate session, and later CCC1 worker configuration; keep model/transport selection distinct from task data. Optional OS executor must remain fail-closed and must cover baseline, candidate and independent checks with cancellation/timeouts. Android/Termux primary provider should report actual capability; Docker/PC remains optional.
5. **One common execution/verification stage.** Extend/refactor the existing candidate session to support acceptance-bound IMPLEMENT alongside failing-chain REPAIR; route existing backlog kinds to it. Reuse the same worktree, lease, cancellation, critic, fixed-profile executor, independent replay and receipt code. Receipt v2 should bind TaskSpec/acceptance/context/candidate hashes, actual provider/model identity when known, executor/toolchain identity, per-profile executed/skipped/failed counts, and actual patch bytes. HMAC and hash claims are references, not model quality or device proof.

Smallest first measurable slice: a typed task with owner-fixed acceptance on an isolated fixture, literal/symbol retrieval, and one candidate that changes two existing noncontrol files through the existing worker, verified again in fresh worktree. Negatives: wrong task/base/path/hash, stale snippets, duplicated/overlapping edits, test/control-file writes, empty create, shrinking test coverage, unavailable executor, model absence/oversize, cancellation, and forged completion receipt. Test retrieval success and candidate quality separately. Leave arbitrary file creation/platform recipes for explicit follow-on capabilities.

## Verification performed

- Git archive of the exact pinned commit; git object/ancestry/diff inspection across fetched remote refs. No branch switch or production write.
- Read actual code entrypoints and all seams above; searched production references separately from tests/pilot scripts.
- `node scripts/continuum-module-wiring-audit.mjs` at pinned snapshot exited 0. Its report explicitly identifies no owner-chat LM1 call and no direct Android GGUF-helper call; this is only a static source inventory.
- No new code or tests were authored; no live local model/runtime, SDK build, native Android/Termux cycle, or independent hardware evidence was produced in this audit.
