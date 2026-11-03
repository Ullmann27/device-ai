# MΩ-1 GAMMA memory handoff

Status: WIP / UNVERIFIED. Base ca93fb4f. Branch codex/momega1-memory-20261103.

Scope: existing `.github/continuum/ccc1-experience.mjs`, new narrow technical recall/proof helper and tests. EXP-02/05/07/08/11/12. No Kotlin/LEX3/model/owner data changes. Canonical journal remains task authority; `experience.jsonl` remains the only experience ledger.

Baseline inspection: ExperienceLog has an unauthenticated hash chain and task idempotency, but lacks a writer lease, fsync, sequence validation, bounded bytes/counts, torn-tail refusal and recall consumer. Successful terminal summaries do not establish model execution, independent RED/GREEN, authenticity or learning benefit. Existing LEX3 and replay cache are preserved.

Proposed interface: `recordTechnicalExperience({stateDir,taskId,task,modelObservation,verification,reflection,key,now})` reads the runtime-MAC-checked canonical CCC1 journal and records bounded technical proof in its derived experience before the legacy summary append. `recallTechnicalExperiences({stateDir,task,repoFullName,key,now,limit=3,maxChars=4096})` returns structured items and JSON text. Recall must recheck runtime authentication, the journal head and terminal receipt, source/repository/chain/file scope, 30-day freshness and authenticated revocation tombstones. Unproved legacy summaries are never eligible. Raw model claims, status booleans and hashes alone cannot admit proof.

Runtime and verifier lanes were asked to expose private in-process observation brands; only trusted process/verifier observations may enter proof. Once admitted, a domain-separated runtime-key MAC persists the binding across process restart. A runtime key is a trust boundary, not independent scientific acceptance. Fixtures must remain CONTRACT_TEST. No owner-fact conversion, training, authorized skill, merge/install authority or full-loop acceptance follows from recall.

Tests planned: RED on unchanged ledger for missing bounds/torn tail/sequence/concurrent durability, then GREEN; actual child-process write/restart/read, corruption, proof forgery, missing runtime/verifier proof, task/source/model/request/output/patch substitution, stale/revoked records, byte/count/context bounds and concurrent writers. Independent adversarial acceptance belongs to another lane.

Rollback: omit isolated commits; preserve existing journals and revocations. A crashed experience append leaves an exclusive lease and requires inspection; never silently recover uncertain data or restore stale consent.
