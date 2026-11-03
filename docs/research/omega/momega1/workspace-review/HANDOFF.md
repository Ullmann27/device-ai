# Independent workspace review handoff

Decision: REQUEST_CHANGES on exact candidate 4665302f6c967c1bfb1e13c9acf01fad4e7eec20. Criteria frozen before inspection at c081ca09c2992fdb3127951b9d74b81f7e26d6ec; review PR https://github.com/Ullmann27/device-ai/pull/420. Only review tests/docs are authored here.

## Findings

P1 WR-01: `.github/continuum/ccc1-workspace.mjs` checks canonical state only while binding. Its returned view subsequently executes after OWNER ABORT, canonical backlog claim requiring owner approval, or MAC journal corruption. Fresh binding rejects each state but the cached view runs a real Docker profile with status0. Execute must revalidate current canonical request/goal/journal/recovery and enforce canonical deadline; lifetime/claim ownership must prevent stale execution. No second journal or scheduler.

P1 WR-02: the same bound request executes twice into distinct containers with the same task/job/request/hash. A local view is correlation, not durable replay protection. Wire the existing canonical execution/claim lifecycle and fail closed on uncertain/repeated execution. The adapter currently does not consume/claim the canonical request.

P2 WR-03 (integration blocker for untrusted/stale callback transport): `ChiefWorkspaceWiring.mailboxReporter` accepts old same-worker reports, prefixes the current task/source/request, and generates a fresh random message ID for every duplicate. Independent native Kotlin tests show old task9999 becomes current task1111 and duplicate report queues twice as DELIVERED. Existing pool invokes once under normal operation, so this is not proof of an external attack surface; before caller integration, bind a typed observation/report identity to its generation and preserve one terminal delivery identity. No success may be inferred from report prose.

## Fresh results

- Independently authored Node adversarial: 4 tests, 0 pass, 4 fail, exit1. Real Docker observed on every counterexample; logs include actual IDs, configuration, source digests and confirmed teardown.
- Author Node contract + real two-container confinement tests rerun independently: 5 tests PASS, exit0. Readonly snapshot, host/peer filesystem denial, network denial, CPU/RAM/PID caps, timeout cleanup, observed simultaneous containers and canonical fresh-bind rejection covered.
- Native Kotlin focused compiler against actual precompiled application classes: exit0. Relevant pool/mailbox sources byte-compared to candidate; dependency/class hashes retained.
- Existing ChiefWorkerPoolTest + ContinuumRoutinesTest + author workspace adapter tests: 19 PASS, exit0.
- Independent Kotlin stale/duplicate tests: 2 FAIL, exit1.
- Actual Kotlin Chief pool → fixed Node → Docker → existing mailbox and cancellation host tests: 2 PASS, exit0.
- No remaining continuum containers observed after tests.

First Node attempt used invalid ABORT code OWNER_CANCEL (test setup defect); retained as attempt1. Corrected to canonical OWNER and reran all four: each failure is missing expected rejection after a real successful Docker profile. Synthetic runtime keys/repositories were removed; receipts and observations remain.

## Reproduce

Create detached target checkout at candidate SHA and use an absolute WORKSPACE_REVIEW_TARGET. No image pull/download required; pinned empty image sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1.

```
WORKSPACE_REVIEW_TARGET=/workspace/momega1-workspace-review-candidate node --test --test-reporter=tap tools/momega1-workspace-review/adversarial.test.mjs
WORKSPACE_REVIEW_TARGET=/workspace/momega1-workspace-review-candidate WORKSPACE_REVIEW_EVIDENCE=/absolute/fresh/evidence bash tools/momega1-workspace-review/kotlin-review.sh
```

Author Node rerun cwd candidate: `CONTINUUM_WORKSPACE_HOST_PROOF=1 CONTINUUM_WORKSPACE_EVIDENCE_DIR=/absolute/fresh/evidence node --test --test-reporter=tap .github/continuum/ccc1-workspace.test.mjs .github/continuum/ccc1-workspace-host.test.mjs`.

Raw evidence under docs/evidence/momega1/workspace-review. No Gradle launched by reviewer; QA owns build scheduling. HOST_INTEGRATION only: two observed concurrent containers on shared4CPU/16GiB, not ten VMs/Android workspaces. FOLD7_NOT_RUN; actual model unavailable. No owner data, signing, installation, paid resource, Render or main merge. Rollback: omit review-only commits. Next: author freezes repair, reviewer reruns unchanged counterexamples and regression evidence; Chief owns caller integration.

Expanded initial review: 6/6 independent counterexamples fail at4665302 (exit1). Added WR-01 cases: tracked source changed after bind still executes although fresh binding blocks dirty checkout; controlled-clock advance beyond canonical goal deadline still executes. Deadline test uses a replaced Date.now only in the reviewer process, with a real Docker profile; it is not a wall-clock expiry experiment. Initial expanded attempt tried a goal with less than the minimum 1-minute request budget and failed admission (harness defect, preserved); corrected to admit a61-second goal then advance the controlled clock beyond it.

The author now proposes removal of all unclaimed execution and activation solely from existing canonical owner REPAIR claims. Repair criteria supplement distinguishes legitimate multiple fixed profiles under a current claim from replay without durable claim authority. No repaired candidate inspected yet. Remote c87e0b06234d5611bd49c101373f0b48e64c5f4a verified via ls-remote after an overlapping push produced a stale-ref rejection; no credential changes.

Prospective claim-adversarial.test.mjs written against the author's announced API before repair inspection:12 cases, syntax check only PASS; functional NOT_RUN until repair freeze. Tests require no unclaimed execution, positive repeat fixed profiles within live canonical claim, token/id/path checks, owner/backlog abort, MAC/source mutations, expiry and profile containment. They intentionally replace the previous direct-executor positive assumption; the six original counterexamples remain retained unchanged.
