# MΩ-1 independent memory review

Status: WIP / UNVERIFIED. Independent challenger, not feature author.
Candidate: `78d94a3959f655cd512025824ca92d78e698a5a1`; production diff from `ca93fb4f` is preserved at `docs/evidence/momega1/memory-review/candidate.diff`. Review branch starts at Chief `cf34fb6a`.

Scope: corruption/truncation/rehashed ledger/revocations, capacity and failed writes, real Node multi-process/restart, provenance and replay, stale journals, model identity and current authorization. Added tests will be frozen before execution. Disposable-key synthetic evidence is CONTRACT_TEST only. Missing provider/verifier brands make production admission NOT_RUN. Different-source transfer remains a requirement to assess, never authorization to widen rights.

Only new review tests and evidence are owned here. No production edits, model invocation, Android/Gradle run, signing, installation, deployment, main merge or owner data. EXP-02/05/07/08/11/12. Rollback: omit this review branch; evidence does not alter production.
