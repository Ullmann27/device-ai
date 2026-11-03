# Independent ALPHA / DELTA criteria — freeze 2026-11-03

Baseline ca93fb4f; candidate implementation NOT READ. Baseline interfaces only:
createCodingCandidateProvider and local-brain query/CLI. Experiments EXP-00/01/02/03/05/07/08/11/12.
This document is the immutable threat contract; later API adapters may be added with separate hashes.
Passing synthetic fixtures is CONTRACT_TEST, never proof of model inference or Android execution.

1. IDENTITY: a modelId, bearer token, supplied digest or verified=true is insufficient. Inference must bind an approved model file and runtime binary actually opened/executed to a request and exact output.
2. TOCTOU: wrong, missing, replaced, symlinked or concurrently modified weights/runtime must fail closed. Hashing a path then spawning that mutable path is insufficient. Describe FD/executable binding and immutable dependency boundary.
3. ENDPOINT: loopback transport is not endpoint identity. Wrong service/restarted server/wrong UID/model swap must not gain model execution proof. Shared LiteRT IPC needs peer/process identity and loaded-weight lifecycle.
4. INVOCATION: require a real child or authenticated native generation, terminal status, actual bounded output. A scripted child is a contract fixture even with pinned SHA.
5. CORRELATION: task fingerprint, source SHA, request nonce/digest, runtime+model identity, output digest and accepted candidate digest stay bound. Alter one field and reject; different requests cannot reuse the same receipt.
6. REPLAY: consumed/UNKNOWN requests remain consumed across crash/restart. Concurrent providers cannot consume one grant twice; restart must not reset limits.
7. REVOKE: revocation before invocation and while pending prevents successful acceptance; expired authorization fails closed. Report if no revocation surface exists.
8. TIMEOUT: deadline covers identity admission/hash, subprocess execution/output, descendant cleanup and parsing. A child ignoring TERM may not hang. Timeout cannot later turn into success.
9. CANCEL: cancellation reaches owned process/native generation; join/reap or UNKNOWN remains visible. No immediate second allocation while first ownership is uncertain.
10. BUDGETS: bound prompt/context, output bytes/tokens, model calls, duration, active processes, RAM/CPU. An option alone is not enforcement; document OS limits and failure behavior.
11. NETWORK: no remote fallback, redirect, DNS/model download or secret-bearing ambient environment. Verify actual OS network denial before calling inference offline; absence of HTTP code is insufficient.
12. CONCURRENCY: chat plus coding cannot corrupt one session or exceed capacity. Busy refusal is acceptable; duplicate model allocation under unknown lifecycle is not.
13. AUTHORITY: model text cannot authorize tools, edits outside task, credentials, verification, persistence promotion or installation. Evidence stays outside source and excludes raw private prompts/output.
14. DEVICE: Fold7/Android17/ARM64, runtime availability, resource and lifecycle evidence must be observed on the authorized device. No connection => FOLD7_NOT_RUN; host fixtures do not substitute.
15. MEMORY follow-up: canonical durable records must bind accepted source/candidate, independent verification and task; corruption, stale/revoked proofs, restart and concurrent writers cannot promote or recall false successes. Retrieval benefit requires actual paired inference; persistence alone proves no learning.

Decision: accept source hardening separately from LOCAL_MODEL_HOST, FOLD7_MODEL,
FOLD7_END_TO_END and FIRST_COGNITIVE_LOOP_VERIFIED. Missing native/OS proof is BLOCKED,
not an invented fail/pass. Provider forged-receipt tests below deliberately demand rejection
of explicit verified=true claims without independently established execution binding.
