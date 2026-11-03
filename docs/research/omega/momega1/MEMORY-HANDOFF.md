# MΩ-1 GAMMA memory handoff

Status: ACCEPTED_RESEARCH_CONTRACT_SLICE_ON_CF0 / CHANGED_SOURCE_EXTENSION_WIP. No model or device acceptance. Base `ca93fb4f`, branch `codex/momega1-memory-20261103`, draft [PR #415](https://github.com/Ullmann27/device-ai/pull/415) targets `codex/momega1-chief-20261103`.

Corrected frozen source after independent review: [`cf0f6a4276bfb9b4f61ab599f4ddbdf63e9f63bb`](https://github.com/Ullmann27/device-ai/commit/cf0f6a4276bfb9b4f61ab599f4ddbdf63e9f63bb). Original candidate `78d94a39` received REQUEST_CHANGES in [independent PR #418](https://github.com/Ullmann27/device-ai/pull/418): 27/31 independent cases passed, four failed. Independent reviewer accepted only the corrected research contract slice after 46/46 cases in evidence commit `7282bd89` on PR #418; actual inference, provider learning benefit, changed-source transfer and device acceptance remain outside that decision. Earlier pushed checkpoints: [`18faa33c`](https://github.com/Ullmann27/device-ai/commit/18faa33c) interface proposal and [`6949ab63`](https://github.com/Ullmann27/device-ai/commit/6949ab63) unchanged-base RED. Later handoff/evidence commits do not change frozen production or test sources.

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

revokeTechnicalExperience({stateDir, taskId, task, key, reason, now = Date.now()})
```

`task` is the original validated canonical coding grant. `modelTask` is that same task with only its ID replaced by `jobTaskId` of the journal receipt job, matching the actual provider invocation. Both fingerprints are bound separately. `key` is the trusted worker's existing 32-byte CCC1 runtime key and must never enter the model process or prompt. Reflection is `{summary: string<=500, limits: 1..4 strings<=200, rejectedAlternatives: 0..4 strings<=200}`. It is historical untrusted text, never an instruction or permission.

The private runtime brand establishes process execution identity only. Production model-derived strategy admission additionally requires that the original observation is immutable (`Object.isFrozen`) and has `verifiedModelExecution === true`. A configured LLAMA_CPP label, dummy GGUF, successful synthetic ELF and correct request HMAC cannot satisfy this stronger requirement. The current ALPHA producer supplies no true verified-model observation, so genuine strategy admission is BLOCKED. No new configurable or caller-asserted provenance flag or alternate authenticator was added. Persisted proofs with absent/false `verifiedModelExecution` are also ineligible; old process-only signed records cannot become strategy evidence.

Production admission requires the ORIGINAL `modelObservation` object accepted by ALPHA's private `isTrustedLocalModelObservation` export from `coding-provider.mjs`, and the ORIGINAL independent `verification` object accepted by BETA's private `isTrustedCodingVerification` export from `ccc1-redgreen-verifier.mjs`. JSON clones, shape-correct objects and success booleans cannot substitute. Neither export exists in this lane's unchanged base: optional namespace lookup denies admission. Therefore **integrated runtime/verifier invocation is NOT_RUN**, and positive admission is not claimed by these tests.

Expected runtime schema: `momega1-local-process-observation-v1`, evidence class `LOCAL_MODEL_HOST`; fields include task/source/fingerprint, model/runtime SHA-256, context/prompt/request/output/proposal SHA-256, execution ID, PID, exit and isolation. Expected verifier schema: `momega1-coding-verification`, version 1, class `HOST_INTEGRATION`, verdict `VERIFIED`, task/source/chain, patch/proposal SHA-256, RED, GREEN and nonempty diagnostics. BETA's branded verifier is responsible for actually executing fresh independent RED/GREEN and retaining test identities. The helper does not accept unauthenticated replacement counts or implement another verifier.

The helper reads the runtime-MAC-verified canonical journal, requires a completed task, actual EXECUTED/CONFIRMED verifier receipt and a confirmed critique referring to that receipt, then binds request/receipt IDs and journal head. It also revalidates the existing grant and full task/source/proposal/patch relationships. A domain-separated runtime-key MAC persists the admitted observation, verification, reflection, time and measured resources inside the same experience record. Proof JSON is capped at 180000 bytes. Call the helper BEFORE the existing legacy summary append. Existing task idempotency intentionally refuses silent legacy-record upgrades; if proof is unavailable, legacy summaries remain ineligible.

Every recall first verifies the REQUESTING task’s own original canonical grant and runtime-MAC-authenticated owner GOAL: exact task fingerprint, repository, source, fixed chain, explicit chain permission, unexpired deadline and no ABORT. Task shape, a grant without a journal, or an overlapping source file is insufficient authorization. Missing/withdrawn/expired/current-invalid authorization returns `BLOCKED_REQUEST_NOT_AUTHORIZED` with no history. A new currently authorized same-source overlapping task is tested positively.

Every recall then rereads the ledger, authenticated revocations, historical original grants and canonical journals. Exact repository, base SHA, fixed chain and an overlapping allowed source file are mandatory. Records expire at 30 days; future or extended lifetimes fail closed. At most three complete records and 4096 characters are emitted, also bounded by the task context limit; oversized items are omitted whole. Corrupt ledger or invalid revocation returns an empty BLOCKED result (current authorization is checked first, so multiple invalid inputs report that earlier denial). Other invalid/missing/stale proof yields no item. Output explicitly labels historical evidence as untrusted review data with no owner fact, trained ability or authorized skill. No OWNER_CONFIRMED conversion, training or merge/install authority exists.

Withdrawal now requires the original validated `task` as well as its CCC1 `taskId`. It does not guess authorization from an unbounded grant scan or damaged ledger. The helper persists a domain-MACed `technicalExperienceRevocation` ancillary field INSIDE THE EXISTING canonical grant, bound to the target task fingerprint, authenticated terminal journal head, reason and time. This is canonical authorization metadata, not a second experience/fact database. The atomic transaction uses a per-grant directory lease, preserves unrelated fields, refuses byte/inode/time conflicts, fsyncs temporary bytes before rename and reasserts published-file plus directory fsync before acknowledging. Idempotent retry preserves the original reason/time and retries the durability barrier after an uncertain error. Existing prepareCodingTask creates a grant once (initial null CCC1 ID, then final ID); all current consumers otherwise only read. Revocation requires a final grant and completed authenticated journal, excluding that initial writer phase. Future grant-mutating code must share this lease; the optimistic comparison cannot serialize a malicious writer ignoring the protocol.

The canonical anchor commits before the existing-ledger tombstone mirror. A full 4096-record ledger, full 16 MiB byte budget or damaged ledger may prevent the optional mirror; the helper emits `TECHNICAL_REVOCATION_MIRROR_UNAVAILABLE`, returns the canonical withdrawal result and never exceeds ledger bounds. Subsequent recall consults the anchor even if the ledger tail is truncated or the tombstone is stripped and its public chain recomputed. A tampered anchor or an uncertain withdrawal lease blocks recall. Single-ledger tampering therefore cannot restore newly withdrawn evidence.

BETA owns orchestrator wiring and ALPHA/BETA own actual prompt consumption. They received this exact API and the frozen source. **Subsequent provider prompt consumption is NOT_RUN in this lane**; standalone retrieval returning text is not proof that the provider consumed it. Independent root integration/review and actual model observations remain required.

## Actual verification and evidence

Node v24.19.0, Linux host; all data synthetic and temporary. Original source hashes and logs are in `docs/evidence/momega1/memory/receipt.json`; corrected source and follow-up logs are in `review-followup-receipt.json` in the same directory.

| Check | Result | Evidence |
| --- | --- | --- |
| New durability tests on unchanged ExperienceLog | 0 pass / 5 fail, exit 1 | `durability-red.tap` |
| Initial five durability cases after repair | 5 pass / 0 fail | `durability-green.tap` |
| Frozen focused ledger/recall suites | 38 pass / 0 fail / 0 skip, exit 0 | `technical-recall-contract-frozen.tap` |
| Existing canonical worker and repair suites | 52 pass / 0 fail / 0 skip, exit 0 | `canonical-regression.tap` |
| Original independent adversarial review | 27 pass / 4 fail, REQUEST_CHANGES | PR #418; reproduced in `review-findings-red.tap` |
| New follow-up RED cases against unchanged source | 31 pass / 7 fail, exit 1 | `review-followup-red.tap` |
| Corrected focused contract suites | 50 pass / 0 fail / 0 skip, exit 0 | `review-followup-green.tap` |
| Corrected canonical worker/repair regressions | 52 pass / 0 fail / 0 skip, exit 0 | `review-followup-canonical-regression.tap` |
| Original independent fixture on stronger evidence tier | 28 pass / 3 fail, exit 1; C01/C27/C28 assume replay of absent verified-model tier | `review-original-fixture-new-tier.tap`; original fixture preserved, not edited by feature author |
| Corrected independent research-contract acceptance | 46 pass / 0 fail / 0 skip; ACCEPT_RESEARCH_CONTRACT_SLICE | Independent PR #418, commit `7282bd89`; no actual model claim |
| Integrated ALPHA/BETA proof admission / provider prompt | NOT_RUN | Missing helper exports on this lane's base |
| Actual local inference, learning benefit, Fold7, APK | NOT_RUN | No models downloaded or executed |

Focused suite includes six REAL concurrent Node writers producing all 72 unique tasks; an actual SIGKILL child leaving a blocked lease; an actual fresh child Node process reconstructing recalled history solely from persisted fixtures; source/model/task/request/output/proposal/patch/journal substitutions; unsigned/hash-recomputed proofs; missing verification/RED/diagnostics; revoked or removed grants; authenticated and forged revocations; staleness/scope; context/record/byte bounds; sequence and torn-tail corruption. The corrected follow-up tests also cover full record AND byte withdrawal, missing current grant/journal, newly authorized requesting task, current expiry/ABORT, canonical withdrawal through damaged/replaced ledger, rehashed tombstone stripping, tampered anchor, a different valid requesting grant, RMW conflict preserving newer fields, post-rename fsync EIO with idempotent durability retry, four actual concurrent withdrawing Node processes and an actual SIGKILL after canonical grant publication. A test-only private-brand module exercises the false-model-tier and mutable-object branches; production source has no injected signer, branding seam or alternative permissive authenticator. An initial follow-up run had 44/45 pass because an old test simultaneously corrupted both current journal and ledger but expected the later ledger-status code. The test now restores the canonical journal before checking ledger-specific denial; raw failure is retained in `review-followup-initial-green-failed.tap`.

The positive restart fixture is manually signed with a disposable fixture runtime key and uses SYNTHETIC model/verifier values, including an explicit synthetic `verifiedModelExecution:true` solely for reader-contract coverage. This does not exercise successful real producer admission or establish actual inference; a free caller flag still cannot obtain the production private brand. The independent reviewer is responsible for separately updating positive disposable-key fixtures for this new required tier while retaining all original expected rejection outcomes. These are **CONTRACT_TEST** observations of persistence and rejection behavior, never actual model inference or full-loop evidence. Existing canonical regressions use the pre-existing real synthetic host worker/repair path, with no downloaded model.

Reproduce:

```sh
node --test --test-reporter=tap .github/continuum/ccc1-experience-durability.test.mjs .github/continuum/ccc1-technical-recall.test.mjs
node --test --test-reporter=tap .github/continuum/ccc1-worker.test.mjs .github/continuum/ccc1-repair.test.mjs
```

## Limits, rejected alternatives and next entry

Rejected: another memory database, Kotlin/LEX3 rewrite, broad semantic matching, silently promoting legacy terminal summaries, accepting `verified=true`, authenticating by hash alone, injecting signing callbacks, reclaiming crashed leases automatically, truncating corrupt tails and claiming model benefit from storage fixtures.

Trusted app-private parent directories, current-protocol writers and secrecy of the existing runtime key are assumptions. Hash chains and MACs cannot detect an attacker controlling the runtime key or jointly rolling back/removing BOTH canonical grant anchors and experience-ledger revocations. Complete trusted-state rollback remains outside the threat model. Unlike the first candidate, changing only the experience ledger no longer restores newly withdrawn proof while the canonical grant remains intact. Deleting an anchor alone is still covered by the surviving ledger tombstone; no historical grant-only timestamp can prove that a missing anchor once existed after BOTH authoritative copies are tampered with. Old ledger-only tombstones are not retroactively anchored; surviving absent/false model-tier proofs remain ineligible, and no automatic stale-consent restore or upgrade is implemented. A sidecar trusted monotonic anchor, device hardware rollback resistance and old-binary concurrent writer exclusion are not implemented. No physical-power-loss, Android filesystem fsync or mobile latency/RAM claim is made. The fsync/partial-write failure tests are host software fault injection, not real power-loss or full-disk observations. Exact-source matching deliberately excludes transfer to a changed commit or a task with no overlapping allowed file until another explicit provenance policy is reviewed. Legitimate future class-scoped reuse must separately bind the new current authorization and validated source relationship; no silent scope widening is authorized here. Hard count and byte limits fail rather than evict historical revocations.

Next: independent reviewer on corrected source `cf0f6a4276bfb9b4f61ab599f4ddbdf63e9f63bb`; Chief integrates only accepted files. Then reconcile actual ALPHA/BETA branded report shapes, wire the helper before summary append and wire returned untrusted historical text into the actual prompt with a paired no-recall ablation. All positive full-loop/model/device claims remain blocked until those observations exist.

Rollback: omit/revert these isolated source commits, preserving original canonical journals, experience records and revocations. Inspect any existing lease and incomplete append before resuming. Never auto-restore old owner grants, journals or consent state. The cf0 corrections are frozen. A newly authorized optional project-backed Git-ancestry policy is being developed separately with preregistered independent tests; cf0 acceptance is not inherited by that extension.

## Newly authorized changed-source extension (WIP, not yet implemented)

Chief authorized optional trusted `project` on `recallTechnicalExperiences`. Omitting it preserves exact-source behavior. Supplying it will require the actual project checkout to match current canonical authorization, clean HEAD at the approved SHA and canonical repository identity; historical/current complete commits must exist locally and the historical source must be a Git ancestor of the approved current source. Allowed-file overlap, fixed chain, current/historical grants, MACs, expiry, withdrawal and genuine-model proof remain mandatory. Fixed Git argv, no shell/network/lazy fetch/replacement objects/grafts; validation will have a hard work/time bound.

Planned output retains historical `sourceCommit`, adds `currentSourceCommit`, and labels `sourceLineage:{kind:'GIT_ANCESTOR'|'EXACT_SOURCE',historicalSourceCommit,currentSourceCommit,ancestor:true}`. This is provenance of historical review data, not semantic generalization or patch/execution authority. Root owns passing the project parameter in the orchestrator. The independent reviewer is freezing synthetic lineage fixtures before implementation inspection. Actual Qwen host weights/runtime were separately owner-approved; no success or model observation is claimed by this document.
