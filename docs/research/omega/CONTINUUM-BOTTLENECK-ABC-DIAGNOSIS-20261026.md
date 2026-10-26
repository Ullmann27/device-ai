# Continuum bottleneck diagnostic A/B/C — first vertical slice

Date: 2026-10-26. Research-only. Parent: **PR #165**, pinned HEAD `da81db718612aa6f12ba27bd6db043e57c739e5f`. No production main merge, no owner data access, no execution grant, no automatic learning.

## Why a new tiny adapter

Existing `coin-wm-v3-structured-shadow.mjs` can skip the neural top-ranked operator when it cannot translate the source and return an operator from a lower rank. That is useful for generating candidates under a fixed candidate budget but **does not measure genuine Pass@1 of the neural strategy selector**. Treating the first *applicable* operator as neural rank 1 creates favorable selection bias.

The new `.github/continuum/ccc1-bottleneck-diagnosis.mjs` implements a **separate immutable stage-1 research diagnostic** with three matched arms:
- `REFERENCE_PATCH`: controller-supplied reference source, not assumed tested or proven; canonical CCC1 typed task and source hash binding required.
- `GIVEN_OPERATOR`: the controller selects one registered atomic statement operator; exactly one attempt, regardless of whether it applies.
- `NEURAL_TOP1`: read the complete ranked NTW1-v3 operator list **before** evaluating the reference patch; attempt only `ranking[0]`; **never fall back to rank 2** if rank 1 cannot generate a patch.

The adapter imports the *real existing* `validateCodingTask`, `taskFingerprint`, `CoinWmV3ShadowModel`, `isStructuredStatementOperator` and `validateCcc1CandidateBinding`. It does not create a second Coding Agent and never calls the executor or repair loop.

Each result preserves experiment/case IDs, full source commit, canonical task SHA, source SHA, declared model checkpoint/test manifest SHAs, an independently calculated **observed research model state hash**, bounded operator-description hash, full raw rank ordering, top-1 selection, number of operator attempts, translation result, optional research candidate and its SHA.

**The declared checkpoint and test-manifest SHAs are NOT externally authenticated.** They are explicitly labeled `checkpointAttested:false` and `testManifestAttested:false`. The operator description hash binds IDs and kinds, not the closure source. No one may promote model weights or trust these fields as signed receipts.

Every candidate remains `CANDIDATE_UNVERIFIED`. All independent tests are **`NOT_RUN`** (baseline, primary, fresh worktree and additional oracle). `verifiedSuccess:false`, `learningEligible:false` and `promotionEligible:false` always. Frozen result snapshots detect unexpected model weight mutations during ranking/translation. The new adapter never receives the owner grant or a user-selected shell test command and does not run any candidate code.

## First real diagnosis *setup* — immutable sort

The checked-in test fixture uses the already documented sort-mutation operator:
- source: `function sortVisibleCatalog(items) { return items.sort((a, b) => a - b); }`
- controller reference: `function sortVisibleCatalog(items) { return [...items].sort((a, b) => a - b); }`
- allowed file `src/sort.mjs`, typed task `BUGFIX` and an exact 40-hex pinned *synthetic* source commit (test-only, not a real Git checkout).
- two actual registered atomic operators `STRUCT_OP_sort` and `STRUCT_OP_divide`.

Case 1 fixes the model policy bias to favor sort:
- A returns a source-bound reference candidate.
- B returns a translated sort candidate.
- C ranks the sort operator first and returns one translated source-bound hypothesis.
- All three remain NOT_RUN/UNVERIFIED; this is a **translation/linkage result, NOT 3 repaired coding tasks**.

Case 2 fixes the model policy bias to favor the unrelated divide operator:
- A and B still return the known reference/forced sort candidates.
- C reports **`NOT_APPLICABLE`** for `STRUCT_OP_divide`, zero candidate, selectedRank=1, operatorAttempts=1.
- A naive fallback approach might have returned sort and misleadingly scored neural Pass@1 as success. This explicit negative case demonstrates why the extra diagnostic is necessary.

## Exactly what was independently tested

1. **Native Node.js v22.16.0** local harness with lightweight test-only stand-ins for the imported Canonical Coding Task, small neural ranker, branded transformer and CCC1 validator: **4/4 PASS** in the final run. This is an isolated *adapter control-flow* test, not native execution with the complete GitHub module graph.
2. Re-fetched **exact original GitHub blobs** for coding-task, NTW1, real structured transformer, CCC1 verifier, new diagnosis module and new test bodies. Executed the original test callbacks against actual original production module bodies inside a fresh V8 isolate with host-only **test shims** for Node crypto, passive VM syntax compilation, `critique`, serialization and OS. **4/4 PASS**. In this mode crypto is *not real SHA-256* and no OS sandbox was instantiated.
3. **NOT RUN**: full native `node --test` on exact new PR commit; authorized Docker-based CCC1 RED → GREEN → fresh GREEN; independently sealed holdout; Android/Fold7/owner-signed APK. None should be inferred from (1) or (2).

## Next highest-value step

On a full trusted checkout, run:
```bash
node --test .github/continuum/ccc1-bottleneck-diagnosis.test.mjs
node --test .github/continuum/ccc1-independent-verifier.test.mjs
node --test .github/continuum/coin-wm-v3-shadow-model.test.mjs
node --test .github/continuum/coin-wm-v3-numeric-safety.test.mjs
node --test .github/continuum/structured-statement-translator.test.mjs
node --test .github/continuum/structured-binding-safety.test.mjs
```

Then create **one genuinely approved controlled source commit** with a real failing test. On a properly authorized, OS-isolated worker, take the known-correct reference candidate through CCC1 first; if it fails, fix the test harness and trust boundary, not the neural model. If it passes, test the forced operator, then the frozen neural top1 using the *same* CCC1 evidence and budget. Only after independently checking an oracle and retaining bounded signed evidence should broader 12-case development coverage be added. These published development tasks must never later be represented as sealed holdout tests.

A future trusted evidence recorder is still required because `runIndependentCcc1PatchVerification` discards raw temp outputs and emits only a reduced report. Do not weaken its safety gates or add a user-supplied `sandboxRunner` to compensate.

No review result from this research adapter may automatically train NTW1, update the repair ledger, merge to main, or install an Android app.
