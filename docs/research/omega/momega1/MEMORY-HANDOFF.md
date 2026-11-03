# MΩ-1 GAMMA memory handoff

Status: FROZEN_CONTRACT_CANDIDATE / INDEPENDENT_ACCEPTANCE_NOT_RUN. No model or device acceptance. Base `ca93fb4f`, branch `codex/momega1-memory-20261103`, draft [PR #415](https://github.com/Ullmann27/device-ai/pull/415) targets `codex/momega1-chief-20261103`.

Frozen source: [`78d94a3959f655cd512025824ca92d78e698a5a1`](https://github.com/Ullmann27/device-ai/commit/78d94a3959f655cd512025824ca92d78e698a5a1). Earlier pushed checkpoints: [`18faa33c`](https://github.com/Ullmann27/device-ai/commit/18faa33c) interface proposal and [`6949ab63`](https://github.com/Ullmann27/device-ai/commit/6949ab63) unchanged-base RED. Later handoff/evidence commits do not change frozen production or test sources.

Scope: existing `.github/continuum/ccc1-experience.mjs`, new `.github/continuum/ccc1-technical-recall.mjs`, narrowly related new tests and this handoff/evidence. EXP-02/05/07/08/11/12. No Kotlin/LEX3/model/owner-data/provider/orchestrator changes. Canonical journal remains task authority; `experience.jsonl` remains the only experience ledger. Existing LEX3 4096-record work and replay cache are preserved.

## Existing-path inventory and change

`coding-orchestrator.runCodingTask` and `ccc1-worker.runCcc1Cycle` already derive terminal `experienceOf` summaries and append to `ExperienceLog`. The existing log had an unauthenticated hash chain and task idempotency, but lacked a writer lease, fsync, sequence validation, byte/count bounds, torn-tail refusal and a retrieval consumer. A terminal success summary does not establish actual model execution, independent RED/GREEN, authenticity or learning benefit. The new helper reads the existing journal and grants, extends the existing summary and returns bounded historical context; it creates no scheduler, database or fact type.

The existing ledger now bounds records at 4096, file bytes at 16 MiB and each complete line at 256 KiB. It validates UTF-8, sequence, chain, unique task IDs and complete newline termination. Direct storage symlinks and parent aliases are refused. An exclusive directory lease covers read/check/write/fsync; writers retry contention for at most two seconds. Acknowledgments include file and parent-directory fsync, including idempotent retries. A real killed writer leaves its lease behind: inspection is required before any manual removal, and uncertain tails are never silently repaired.

## Integration contract

Exports from `ccc1-technical-recall.mjs`:

```js
recordTechnicalExperience({
  stateDir, taskId, task, modelTask, modelObservation,
  verification, reflection, key, now = Date.now()
}) // boolean append result; throws on missing proof/binding

recallTechnicalExperiences({
  stateDir, task, repoFullName, key, now = Date.now(),
  limit = 3, maxChars = 4096
}) // {schema:'momega1-technical-recall', version:1, status, items, text}

revokeTechnicalExperience({stateDir, taskId, key, reason, now = Date.now()})
```

`task` is the original validated canonical coding grant. `modelTask` is that same task with only its ID replaced by `jobTaskId` of the journal receipt job, matching the actual provider invocation. Both fingerprints are bound separately. `key` is the trusted worker's existing 32-byte CCC1 runtime key and must never enter the model process or prompt. Reflection is `{summary: string<=500, limits: 1..4 strings<=200, rejectedAlternatives: 0..4 strings<=200}`. It is historical untrusted text, never an instruction or permission.

Production admission requires the ORIGINAL `modelObservation` object accepted by ALPHA's private `isTrustedLocalModelObservation` export from `coding-provider.mjs`, and the ORIGINAL independent `verification` object accepted by BETA's private `isTrustedCodingVerification` export from `ccc1-redgreen-verifier.mjs`. JSON clones, shape-correct objects and success booleans cannot substitute. Neither export exists in this lane's unchanged base: optional namespace lookup denies admission. Therefore **integrated runtime/verifier invocation is NOT_RUN**, and positive admission is not claimed by these tests.

Expected runtime schema: `momega1-local-process-observation-v1`, evidence class `LOCAL_MODEL_HOST`; fields include task/source/fingerprint, model/runtime SHA-256, context/prompt/request/output/proposal SHA-256, execution ID, PID, exit and isolation. Expected verifier schema: `momega1-coding-verification`, version 1, class `HOST_INTEGRATION`, verdict `VERIFIED`, task/source/chain, patch/proposal SHA-256, RED, GREEN and nonempty diagnostics. BETA's branded verifier is responsible for actually executing fresh independent RED/GREEN and retaining test identities. The helper does not accept unauthenticated replacement counts or implement another verifier.

The helper reads the runtime-MAC-verified canonical journal, requires a completed task, actual EXECUTED/CONFIRMED verifier receipt and a confirmed critique referring to that receipt, then binds request/receipt IDs and journal head. It also revalidates the existing grant and full task/source/proposal/patch relationships. A domain-separated runtime-key MAC persists the admitted observation, verification, reflection, time and measured resources inside the same experience record. Proof JSON is capped at 180000 bytes. Call the helper BEFORE the existing legacy summary append. Existing task idempotency intentionally refuses silent legacy-record upgrades; if proof is unavailable, legacy summaries remain ineligible.

Every recall rereads the ledger, authenticated revocations, current original grant and canonical journal. Exact repository, base SHA, fixed chain and an overlapping allowed source file are mandatory. Records expire at 30 days; future or extended lifetimes fail closed. At most three complete records and 4096 characters are emitted, also bounded by the task context limit; oversized items are omitted whole. Corrupt ledger or invalid revocation returns an empty BLOCKED result. Other invalid/missing/stale proof yields no item. Output explicitly labels historical evidence as untrusted review data with no owner fact, trained ability or authorized skill. No OWNER_CONFIRMED conversion, training or merge/install authority exists.

BETA owns orchestrator wiring and ALPHA/BETA own actual prompt consumption. They received this exact API and the frozen source. **Subsequent provider prompt consumption is NOT_RUN in this lane**; standalone retrieval returning text is not proof that the provider consumed it. Independent root integration/review and actual model observations remain required.

## Actual verification and evidence

Node v24.19.0, Linux host; all data synthetic and temporary. Source hashes and log SHA-256 values are in `docs/evidence/momega1/memory/receipt.json`.

| Check | Result | Evidence |
| --- | --- | --- |
| New durability tests on unchanged ExperienceLog | 0 pass / 5 fail, exit 1 | `durability-red.tap` |
| Initial five durability cases after repair | 5 pass / 0 fail | `durability-green.tap` |
| Frozen focused ledger/recall suites | 38 pass / 0 fail / 0 skip, exit 0 | `technical-recall-contract-frozen.tap` |
| Existing canonical worker and repair suites | 52 pass / 0 fail / 0 skip, exit 0 | `canonical-regression.tap` |
| Independent adversarial acceptance | NOT_RUN; root assigning separate reviewer | No inherited acceptance |
| Integrated ALPHA/BETA proof admission / provider prompt | NOT_RUN | Missing helper exports on this lane's base |
| Actual local inference, learning benefit, Fold7, APK | NOT_RUN | No models downloaded or executed |

Focused suite includes six REAL concurrent Node writers producing all 72 unique tasks; an actual SIGKILL child leaving a blocked lease; an actual fresh child Node process reconstructing recalled history solely from persisted fixtures; source/model/task/request/output/proposal/patch/journal substitutions; unsigned/hash-recomputed proofs; missing verification/RED/diagnostics; revoked or removed grants; authenticated and forged revocations; staleness/scope; context/record/byte bounds; Unicode, sequence and torn-tail corruption. The positive restart fixture is manually signed with a disposable fixture runtime key and uses SYNTHETIC model/verifier values. These are **CONTRACT_TEST** observations of persistence and rejection behavior, never actual model inference or full-loop evidence. Existing canonical regressions use the pre-existing real synthetic host worker/repair path, with no downloaded model.

Reproduce:

```sh
node --test --test-reporter=tap .github/continuum/ccc1-experience-durability.test.mjs .github/continuum/ccc1-technical-recall.test.mjs
node --test --test-reporter=tap .github/continuum/ccc1-worker.test.mjs .github/continuum/ccc1-repair.test.mjs
```

## Limits, rejected alternatives and next entry

Rejected: another memory database, Kotlin/LEX3 rewrite, broad semantic matching, silently promoting legacy terminal summaries, accepting `verified=true`, authenticating by hash alone, injecting signing callbacks, reclaiming crashed leases automatically, truncating corrupt tails and claiming model benefit from storage fixtures.

Trusted app-private parent directories, current-protocol writers and secrecy of the existing runtime key are assumptions. Hash chains and MACs cannot detect a deliberate rollback of the COMPLETE trusted state or an attacker controlling the runtime key. Restoring an older ledger may lose revocations; it is forbidden as automatic recovery. A sidecar trusted monotonic anchor, device hardware rollback resistance and old-binary concurrent writer exclusion are not implemented. No physical-power-loss, storage-syscall fault injection, Android filesystem fsync or mobile latency/RAM claim is made. Exact-source matching deliberately excludes transfer to a changed commit until another explicit provenance policy is reviewed. Hard count and byte limits fail rather than evict historical revocations.

Next: independent reviewer on source `78d94a39`; Chief integrates only accepted files. Then reconcile actual ALPHA/BETA branded report shapes, wire the helper before summary append and wire returned untrusted historical text into the actual prompt with a paired no-recall ablation. All positive full-loop/model/device claims remain blocked until those observations exist.

Rollback: omit/revert these isolated source commits, preserving original canonical journals, experience records and revocations. Inspect any existing lease and incomplete append before resuming. Never auto-restore old owner grants, journals or consent state. No further feature changes are planned before independent findings.
