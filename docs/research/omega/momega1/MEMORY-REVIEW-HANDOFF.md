# MΩ-1 independent memory review

**Verdict: REQUEST_CHANGES for end-to-end memory acceptance on candidate `78d94a3959f655cd512025824ca92d78e698a5a1`.** Independent reviewer, not feature author. Review [draft PR #418](https://github.com/Ullmann27/device-ai/pull/418) targets Chief. No production edits were made.

31 frozen independent cases: **27 PASS / 4 FAIL / 0 SKIP, exit 1**. Author focused tests: **38 PASS / 0 FAIL / 0 SKIP, exit 0**. Canonical worker/repair regressions: **52 PASS / 0 FAIL / 0 SKIP, exit 0**. Counts are separate, not a combined acceptance score. All are Node host/CONTRACT_TEST evidence; no model inference.

Candidate production diff from `ca93fb4f` is preserved in [candidate.diff](../../../evidence/momega1/memory-review/candidate.diff). Review branch starts at Chief `cf34fb6a`. Tests and fixture were frozen and pushed at [90ff6ed2](https://github.com/Ullmann27/device-ai/commit/90ff6ed2) before first execution; SHA-256 verification after execution passed. These are independently authored cases after API/spec/source inspection, not blind holdouts. The candidate was extracted by `git archive`; no production file in any author's worktree was modified.

## Findings and disposition

1. **P1 — full ledger prevents withdrawal but continues recalling the target.** `ccc1-technical-recall.mjs:120` routes withdrawal through the ordinary append path; `ccc1-experience.mjs:105` rejects every new record at 4096. C17 creates one eligible synthetic proof plus 4095 valid filler records, calls real revoke, observes `experience log record bound`, then restarts Node and receives that same proof. This is an ordinary capacity condition, with no corruption, runtime-key compromise or rollback. Hard bounds correctly prevent overflow but do not make subsequent recall fail closed. Before admission, reserve enough revocation capacity (record and byte budgets), or use an explicit authenticated fail-closed policy in the existing canonical state. Do not silently evict revocations or add an unreviewed competing memory database. Full **byte** capacity withdrawal is source-indicated by line 111, not separately reproduced; oversized-byte rejection was tested.

2. **P2 — the requesting task's current grant is not checked by this API.** `ccc1-technical-recall.mjs:127` validates task shape, and line 151 loads only the historical task grant. C11 submits a new task ID/goal with matching source and overlapping path but no grant and obtains historical context after restart. Thus “every recall revalidates authorization” applies only to the original historical task. This is a missing boundary for future prompt integration, not a demonstrated command-execution or ToolGate bypass: the helper is read-only, requires the trusted runtime key, and has no production prompt caller here. Require a current granted request identity at the boundary or provide independently tested caller enforcement before integration. A task-shaped model object must never serve as authorization.

3. **Rollback/integrity limitation, two reproduced failed requirements — revocation existence is not authenticated.** C14 removes only the complete final revocation line; C15 preserves its task ID but replaces its body with harmless text and recomputes the public hash chain. Both fresh processes recall the withdrawn proof without needing the runtime key. `ccc1-technical-recall.mjs:137` skips records with no `revocation`, and no trusted monotonic head proves a revocation existed. This requires write access to the trusted experience file. The author already excludes deliberate rollback of trusted state, so this is not a new exploit within a fully trusted-filesystem assumption, and not a claim that MAC forgery succeeds. The concrete boundary is narrower than “complete state rollback”: modifying only the experience ledger suffices, while grants/journals/key remain unchanged. Explicitly retain that threat-model limitation; if durable revocation must survive ledger rollback/tampering, acceptance needs a reviewed authenticated canonical revocation/head anchor. MACs on surviving positive records alone cannot establish absence or freshness.

4. **Requirement gap — changed-code related-task recall remains unavailable.** C12 provisions a new task/grant with a different source commit and related counter goal, then restarts and receives zero items. Line 147 requires identical source SHA; line 148 also requires path overlap. This is a defensible conservative interim boundary and must not be silently widened. It does **not** satisfy the user's required new related task with different code/solution after restart. A subsequent explicit provenance policy must bind current authorization and the validated source relationship; then run actual prompt consumption and paired no-recall comparison. A different goal on the same commit is insufficient evidence for changed-code transfer.

5. **Integration remains NOT_RUN.** C03 confirms this exact candidate lacks ALPHA `isTrustedLocalModelObservation` and BETA `isTrustedCodingVerification` exports; C02 rejects copied self-certified observations. Therefore there is no successful production `recordTechnicalExperience` admission on this source and no complete real model → independent verifier → durable record → prompt recall loop. Future ALPHA/BETA shape reconciliation must be reviewed on a new exact combined SHA. No inherited acceptance.

## What the independent tests establish

- Fresh real Node processes reconstruct synthetic fixture proof; four simultaneous Node writers preserve 32 unique tasks, then a fifth process reads all 32.
- Actual SIGKILL during the ledger's write leaves an incomplete line plus lease; the next writer refuses after its bounded wait. Partial-write ENOSPC injection yields no acknowledgment and damaged-ledger refusal. Injected fsync EIO yields an uncertain complete record with no success acknowledgment; a subsequent durable idempotent retry returns false. These are software fault injections, not physical power-loss or real full-disk experiments.
- Torn bytes, invalid UTF-8, interior JSON corruption, duplicate tasks, oversize records/files, invalid revocation MAC, stale journal head, removed/altered historical grant, expired proof, wrong runtime key, copied task identity and unauthenticated output/model substitutions are rejected. Whole items are omitted under a tight context bound.
- A fresh MAC written by the fixture key holder authenticates synthetic provenance and can change a model digest (C27). This is the intended signer trust boundary, never independent proof of model inference. C28 records that the API has no current-model identity input/check: historical model identity remains labelled even if an extra current-model field is supplied. Historical review text across model swaps may be permissible; it cannot become a cached current-model success or learned ability. No key, owner data or real model file was committed.

## Reproduce and raw evidence

Node `v24.19.0`, Linux `6.18.57`. From a repository containing candidate SHA:

```sh
mkdir -p /tmp/momega1-memory-candidate-78d94a39
git archive 78d94a3959f655cd512025824ca92d78e698a5a1 .github/continuum scripts | tar -x -C /tmp/momega1-memory-candidate-78d94a39
MOMEGA1_CANDIDATE_ROOT=/tmp/momega1-memory-candidate-78d94a39 node --test --test-reporter=tap tools/momega1-memory-review/tests/challenge.test.mjs
```

Then, from that extracted candidate directory:

```sh
node --test --test-reporter=tap .github/continuum/ccc1-experience-durability.test.mjs .github/continuum/ccc1-technical-recall.test.mjs
node --test --test-reporter=tap .github/continuum/ccc1-worker.test.mjs .github/continuum/ccc1-repair.test.mjs
```

Evidence directory: [`docs/evidence/momega1/memory-review`](../../../evidence/momega1/memory-review). `challenge.tap` preserves all four assertion failures. Each TAP has its own `.exit` file. `receipt.json` records exact commands, source/test/log hashes and outcomes. Initial extraction mistakenly requested nonexistent root `package.json` and failed with exit 1 before test execution; the corrected archive command above succeeded. No test failure was removed or reclassified as passing.

## Acceptance limits and next step

The ledger's bounded durability checks and narrow historical filtering have positive independent evidence. Overall requirement acceptance is withheld for capacity revocation, current request authorization integration, missing live-brand/prompt wiring and changed-code transfer. Revocation rollback is an explicit additional threat-model limitation. The conservative scope boundary is accepted as an interim research restriction, not as completion of the requested learning loop.

Chief and author should fix/resolve findings in their allocated production lane, freeze a new candidate and request independent rerun without changing these expected outcomes. Chief received the raw failure summary; author is currently absent from the live-agent list, so handoff forwarding is through Chief. Original candidate remains immutable.

EXP-02/05/07/08/11/12. No production edits, models, Android/Gradle run, signing, installation, deployment, main merge or owner data. Fold7 access is unavailable; FOLD7_NOT_RUN. Physical power-loss, Android fsync/SELinux, provider prompt consumption, real inference, learning benefit and end-to-end mobile acceptance are NOT_RUN. Rollback: omit the review branch; it changes only new tests/evidence/handoff, not stored owner state.
