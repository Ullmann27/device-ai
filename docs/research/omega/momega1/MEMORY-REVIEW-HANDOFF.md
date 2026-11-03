# MΩ-1 independent memory review — Git-lineage and recall control

**Verdict: ACCEPT_BOUNDED_GIT_LINEAGE_RESEARCH_POLICY on `ed6e59101d33faa830b1ac2c0beb8fb4f0972128`.** Also accept the explicit recall control in Chief `22136ce6c3c23682a40006d446ad9b7234ac76e0` at the independently tested provider boundary with that helper. This is not real-model inference, transfer benefit, learned capability or final combined-source acceptance. [Review PR #418](https://github.com/Ullmann27/device-ai/pull/418).

| Independently executed check | Outcome |
| --- | --- |
| Preregistered lineage cases against old accepted `cf0f6a42` | **16 PASS / 11 FAIL / 0 SKIP; exit 1** |
| Same unchanged lineage cases against `ed6e5910` | **27 PASS / 0 FAIL / 0 SKIP; exit 0** |
| Prior frozen 46 cases against merged `ed6e5910` | **45 PASS / 1 FAIL / 0 SKIP; exit 1**, baseline metadata assertion C03 only |
| Author focused suite freshly rerun on `ed6e5910` | **50 PASS / 0 FAIL / 0 SKIP; exit 0** |
| Canonical worker/repair freshly rerun on `ed6e5910` | **52 PASS / 0 FAIL / 0 SKIP; exit 0** |
| Independent recall-control boundary composition | **6 PASS / 0 FAIL / 0 SKIP; exit 0** |

The 27 lineage cases were frozen/pushed at `fd9250b168b16102bd10540a5a79243dd97bdea4` **before author implementation**; author explicitly held and was released only after this freeze. Baseline RED is retained at review commit `a5b58daf`. Source/API inspection before that freeze covered the existing accepted implementation and agreed future interface, not a future implementation. No lineage test or expectation changed between RED and GREEN. Tests use real temporary Git commits/worktrees, fresh Node processes and manually signed disposable-key synthetic proofs. No QA holdout contents, original benchmark tasks, weights, model binaries or owner data were used.

## Accepted policy and review evidence

`recallTechnicalExperiences` now accepts optional trusted `project=null`. Omitted/null preserves the former exact-source gate. A supplied project must resolve to the actual Git root, with clean tracked/staged/untracked state, current HEAD equal to the approved source, matching canonical repository identity, locally available real commit objects, and historical commit an actual ancestor of current. Replacement refs and graft-file presence are conservatively refused. No lazy fetch or model-selected command/path is used. The caller, not model output, supplies project.

The independently frozen cases prove positive direct/distant ancestor recall after restart, exact-source compatibility, canonical local basename identity and actual linked worktrees sharing the object database. Negative cases cover stale current HEAD, tracked/staged/untracked dirt, dirty same-SHA supplied projects, changed origin, invalid/nested project roots, reverse and unrelated history, missing objects, replace refs, grafts, incomplete shallow history and nonoverlapping allowed paths. Current grant removal/expiry/ABORT, original canonical revocation after ledger tombstone deletion and proof-MAC substitution remain effective, with explicit eligible positive preconditions before mutation.

Each item retains historical `sourceCommit` and scoped `sourceFiles`, adds `currentSourceCommit` and `sourceLineage` (`GIT_ANCESTOR` or `EXACT_SOURCE`, exact old/current SHAs, ancestor flag). Returned text explicitly states that Git history does not establish semantic validity on current code. This supplies a conservative historical-provenance rule for the user's different-code scenario; actual usefulness or successful model transfer is still unmeasured. No free cross-repository or semantic generalization, owner fact, tool permission or patch authority follows.

Source review confirms fixed local Git argv, sanitized Git environment, no shell, disabled hooks/fsmonitor/lazy fetch/replacements/grafts for graph queries, bounded stdout and wall time (32 calls, 10 seconds aggregate, 3 seconds per command, 64 KiB output; at most eight distinct historical-source checks). Checkout HEAD/clean state/identity/replacement/graft conditions are checked again before returning nonempty context. These budget/race checks were inspected; this review does not claim adversarial wall-time fault injection for every Git subprocess.

Canonical identity intentionally reuses the existing GitHub owner/name or local/<checkout-basename> convention. A local basename is not a global repository UUID or cryptographic origin certificate. Root must select the trusted actual checkout. QA was notified that sibling worktrees with different basenames would produce different local identities; its separately frozen paired v2 uses .../{arm}/device-ai accordingly. Git object storage, trusted runtime key and canonical grants remain trusted; malicious simultaneous canonical-grant plus ledger rollback remains outside scope.

## Baseline metadata change, fully disclosed

The prior C03 test asserts `isTrustedLocalModelObservation` and `isTrustedCodingVerification` are absent, which was true on standalone `cf0f6a42`. GAMMA merged Chief `713949a8`, which supplies these real exports. Therefore the unchanged C03 fails with actual function versus expected undefined. This source-shape difference was reported to Chief **before the candidate run**, is preserved in `lineage-independent-regression.tap`, and is not counted as PASS or silently rewritten. All other 45 prior cases pass, including rejection of unbranded copied success objects and false persisted model tier. Presence of the export is not evidence of actual successful model admission; real approved producer observation and full loop remain separate QA gates.

## Explicit recall-off control review

The separately frozen six cases (`d673a504`) use an exact temporary composition of Chief `22136ce6` `.github/continuum` and `scripts`, replacing only `ccc1-technical-recall.mjs` with the exact `ed6e5910` blob. This composition is not a final combined Git SHA; no production worktree file was edited. The helper SHA-256 is `aab07d4c04fc069186b62e1c6a5d8e442624e6753b7f1cc0f64c77aaaef4c7f7`.

Each case first establishes eligible synthetic ancestor history. Actual `runCodingTask` calls with omitted/true `technicalRecallEnabled` deliver that history (old/current SHA and ancestry labels) to the real `providerFactory` call; false delivers `''`. These cases intentionally throw at the factory boundary before model inference or repair execution. Separate terminal synthetic tasks verify returned `technicalRecallCount` 1 versus 0, with no merge/training authority. NonBoolean values refuse before state I/O. Historical experience bytes stay unchanged before provider dispatch. Separate synthetic cases establish control behavior; they are not a measured matched inference experiment. QA owns the actual paired same-snapshot/same-source model runs.

## Exact reproduction and artifacts

Lineage candidate:

```sh
mkdir -p /tmp/momega1-memory-candidate-ed6e5910
git archive ed6e59101d33faa830b1ac2c0beb8fb4f0972128 .github/continuum scripts | tar -x -C /tmp/momega1-memory-candidate-ed6e5910
MOMEGA1_CANDIDATE_ROOT=/tmp/momega1-memory-candidate-ed6e5910 node --import ./tools/momega1-memory-review/tests/fixture-tier-compat.mjs --test --test-reporter=tap tools/momega1-memory-review/tests/git-lineage.test.mjs
```

The existing synthetic tier loader only transforms the original reviewer fixture URL to add manually signed `verifiedModelExecution:true`. It never changes candidate modules or establishes real model provenance. All original/supplemental/lineage/ablation freeze hashes were rechecked after execution.

Commands, directories, exact source hashes, TAP hashes and exits: [`lineage-receipt.json`](../../../evidence/momega1/memory-review/lineage-receipt.json) and [`ablation-receipt.json`](../../../evidence/momega1/memory-review/ablation-receipt.json). Raw RED, GREEN, prior metadata failure and control outputs remain in the same directory. New diff: [`lineage-candidate.diff`](../../../evidence/momega1/memory-review/lineage-candidate.diff). Root and QA received accepted API/control boundaries promptly; author source remains frozen at `ed6e5910` (later `4f822df5` is docs only).

Next acceptance belongs to the exact integrated caller/producer/helper source and QA's actual approved local-model experiment. No real inference, model-quality gain, paired transfer benefit, Fold7, Android build, signing or install was performed by this reviewer. Rollback: omit reviewer tests/docs; production choices remain Chief-owned. Previous verdicts below remain immutable historical evidence, superseded only within the exact new source scope stated here.

---

# MΩ-1 independent memory review — corrected candidate

**Verdict: ACCEPT_RESEARCH_CONTRACT_SLICE on `cf0f6a4276bfb9b4f61ab599f4ddbdf63e9f63bb`.** The four original failing expectations are resolved. This accepts the bounded memory/revocation research implementation for Chief integration, **not** end-to-end MΩ1, actual inference, learned capability, changed-code transfer or mobile acceptance. Independent reviewer authored no production changes. [Draft PR #418](https://github.com/Ullmann27/device-ai/pull/418).

| Fresh independent run on corrected source | Result |
| --- | --- |
| Original 31 frozen cases plus 15 new canonical-anchor cases | **46 PASS / 0 FAIL / 0 SKIP; exit 0** |
| Author focused durability/recall suite | **50 PASS / 0 FAIL / 0 SKIP; exit 0** |
| Canonical worker/repair regressions | **52 PASS / 0 FAIL / 0 SKIP; exit 0** |

All are Node host CONTRACT_TEST evidence. Do not sum them into a real-model acceptance score. Candidate production source is byte-exact `git archive` output. Original `challenge.test.mjs` and `fixture.mjs` hashes still match the first freeze `90ff6ed2`. New cases and compatibility loader were frozen at `ef17f793`; one supplemental fixture correction was frozen at `c746ff0a` before rerun. Final checksum verification and `git diff --check` passed.

## Corrected contract and independent findings

- Withdrawal now authenticates and atomically persists `technicalExperienceRevocation` inside the **existing original canonical grant**, before trying the optional experience-ledger mirror. Original C17 and supplemental R02 confirm withdrawal at 4096 records; ledger size stays 4096 and a fresh process returns no historical item. An explicitly different, valid current task remains authorized, ruling out a vacuous result from withdrawing the requesting task itself.
- Original C14/C15 and R03/R04 confirm that deleting the ledger tombstone tail or stripping/re-hashing its body no longer resurrects the proof while the canonical grant anchor survives. R05/R06 reject a modified-MAC or null canonical anchor even when the ledger mirror is absent. R07 checks task fingerprint/journal-head binding, unrelated grant-field preservation and idempotent withdrawal.
- Current requester authorization is enforced. Original C11 blocks a task with no grant. R01 positively recalls synthetic history for a distinct task with a valid current grant and authenticated GOAL. R12/R13 remove that current grant or append a valid OWNER ABORT and observe `BLOCKED_REQUEST_NOT_AUTHORIZED`.
- R08 executes four concurrent Node withdrawal processes: exactly one returns true, three return false, and the grant remains valid. R09 changes the canonical grant during the temporary-file fsync: the operation refuses instead of overwriting the newer field. R10 injects post-rename EIO: no success acknowledgment, the committed anchor still blocks, and retry reasserts durability. R11 SIGKILLs a real process during temporary-file fsync: unchanged original grant plus an uncertain lease block historical recall. These are host software fault injections, not physical power-loss/Android guarantees.
- The corrected provenance requirement is conservative: original private runtime brand **and** frozen observation **and** `verifiedModelExecution === true` are required for production admission; the persisted reader also requires the true tier. R15 signs a false tier with a disposable fixture key and confirms it stays ineligible. Exact source still lacks the ALPHA/BETA exports (C03), and the current actual runtime producer does not establish true model execution. Production strategy admission remains blocked, as intended. No reviewer result demonstrates real positive model admission.

## Synthetic compatibility and retained failed run

The new `fixture-tier-compat.mjs` Node loader transforms only the exact review fixture URL and inserts `verifiedModelExecution:true` into its synthetic observation. It checks an exact single-occurrence fixture anchor, does not transform any candidate module, does not modify the original frozen files, and does not change any expected assertion. The fixture already signs its manually constructed proof with a disposable key; this true flag is test data, not model provenance. C01/C27/C28 therefore exercise real eligible reader paths again instead of being disabled by the stronger tier.

The first supplemental run was **45 PASS / 1 FAIL, exit 1**: R13 omitted the mandatory `ABORT.code` fixture field, so canonical journal construction failed before reaching the tested recall assertion. Added only `code:'OWNER'`; expected denial unchanged. Original raw output remains in `corrected-challenge-initial-fixture-failure.tap` and its `.exit`. The successful complete rerun is `corrected-challenge.tap`. No candidate bug was reclassified as passing and no original failed expectation was weakened.

## Remaining boundaries

- R14 constructs a **valid current grant and journal on a different source SHA** and observes status OK with zero items. Exact source/path matching is an accepted narrow interim restriction, **not fulfillment** of the user's changed-code related-task recall requirement. Any future extension needs explicit current authorization/provenance policy and independent review; no silent rights widening.
- Canonical grants and runtime key remain trusted. Joint rollback/removal of the grant anchor **and** ledger tombstone can defeat historical revocation, even if task journals remain unchanged. Whole trusted-state rollback is outside scope; there is no hardware or monotonic antirollback protection. Existing current-protocol grant mutators must share the withdrawal lease; malicious or old writers ignoring it are outside the supported concurrency contract.
- Full-byte capacity withdrawal was exercised by the freshly rerun author suite; independent supplemental R02 specifically covers the 4096-record bound. The independent original suite also checks oversized-byte refusal. No real ENOSPC filesystem or physical disk-loss test was performed.
- No source integration into current ALPHA/BETA callers was performed in this reviewer branch. Combined caller shapes, provider prompt consumption and final exact combined SHA still require Chief/QA validation. Real inference, learning benefit/ablation, new-source transfer success, Gradle/Android/Fold7, release/sign/install are NOT_RUN here.

## Reproduction and evidence

From the review checkout:

```sh
mkdir -p /tmp/momega1-memory-candidate-cf0f6a42
git archive cf0f6a4276bfb9b4f61ab599f4ddbdf63e9f63bb .github/continuum scripts | tar -x -C /tmp/momega1-memory-candidate-cf0f6a42
MOMEGA1_CANDIDATE_ROOT=/tmp/momega1-memory-candidate-cf0f6a42 node --import ./tools/momega1-memory-review/tests/fixture-tier-compat.mjs --test --test-reporter=tap tools/momega1-memory-review/tests/challenge.test.mjs tools/momega1-memory-review/tests/canonical-anchor.test.mjs
```

From the extracted candidate, run the two original focused/regression commands preserved below. All actual commands, directories, exact source/test/log hashes and exit codes are in [`corrected-receipt.json`](../../../evidence/momega1/memory-review/corrected-receipt.json). New production diff is [`corrected-candidate.diff`](../../../evidence/momega1/memory-review/corrected-candidate.diff). Author updated handoff/API were read and reconciled with source before final verdict. Chief and author received the final acceptance boundaries.

Rollback: omit this tests/docs-only review branch. No production, owner state, models, main, signing, installation or deployment were modified. EXP-02/05/07/08/11/12. Original review below is retained as historical evidence and is superseded only for the corrected exact candidate.

---

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
