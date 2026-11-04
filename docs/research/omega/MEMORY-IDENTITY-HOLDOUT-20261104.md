# Frozen synthetic memory identity holdout

Date: 2026-11-04. Scope: EXP-06 provenance identity and EXP-07 independent functional checks.

## Provenance and separation

- Baseline source: `ca31eb8b24b65d084750f0ea10290dc0d6be6733`.
- Branch: `test/continuum-memory-holdout-20261104`.
- Test: `app/src/test/java/at/heinz/deviceai/cognitive/MemoryIdentityHoldoutTest.kt`.
- The fixture author read only the baseline retrieval API and existing baseline tests. No candidate production source, candidate diff, or candidate test was inspected before this fixture was committed.
- The synthetic test inputs are independent of the implementation agent's development tests. They are intentionally held back from that agent until its candidate freeze. The implementation contract itself was shared beforehand.
- There are 25 JUnit test methods. Several apply the same invariant to a bounded set of permutations or metadata values; method count is not a count of independent real-world questions.
- All IDs, records, reference strings, query text and timestamps are synthetic. `holdout://` strings are test identifiers, not verified evidence or live URLs.
- This initial freeze is `LOCAL_ONLY / NOT_PUSHED` and `NOT_RUN`. The coordinating root agent owns publication. This agent does not push or modify production files.

## Preregistered contract

Exact duplicate records with the same ID count once, including in corpus statistics, scorer calls and the result limit. Any discrepancy within one ID group excludes that identity before privacy filtering and before either optional scorer can observe it. The group cannot be resolved by input order or by a majority of repeated records. An inaccessible conflicting counterpart still makes the identity ambiguous.

For unique, safe records, retrieval scores, deterministic order and existing scorer fallback/clamping behavior must remain unchanged. Relevant `STALE` and `CONTRADICTED` records remain retrievable for audit under the existing score penalties. This test does not impose an answer-generation policy.

## Fixture coverage

| Methods | Property |
| --- | --- |
| 1–2 | Exact structural copies and repeated references do not inflate scores or consume distinct result slots |
| 3–14 | Content/negation, privacy, provenance, both timestamps, evidence, confidence, type, tags, parents, source and validation discrepancies exclude a same-ID group |
| 15–17 | Duplication cannot outvote a conflict; excluded groups cannot change clean corpus scores; hidden/ambiguous records never reach either scorer |
| 18–19 | Unique input permutations and ID ties remain deterministic; stale/contradicted audit retrieval remains available |
| 20–23 | Unicode/case and late terms within the existing token bound; absent topics; non-finite scorer handling and finite clamping |
| 24 | All ten existing score components and result order are pinned for a small unique safe fixture, with tolerance `1e-12` |
| 25 | List-order discrepancies in tags, parent IDs and evidence references remain identity discrepancies |

The numerical fixture was calculated from the inspected baseline formula and is to be checked against the actual baseline run before judging the candidate. No semantic recall threshold, new API, storage format or production behavior beyond the shared identity contract is required. Late terms are within the existing 4,096-token bound; no expanded token budget is assumed.

## Execution and reporting

The existing coordinator harness can compile only this new test against the actual cached baseline classes. Baseline failure is expected for identity rejection/deduplication checks; a baseline failure must not be described as a Kotlin compiler failure or as a candidate result.

```sh
python /workspace/scratch/624aeb26d481/qa/run_focused.py memory-holdout-baseline-20261104 \
  --repo /workspace/scratch/624aeb26d481/agent-memory-holdout \
  --source app/src/test/java/at/heinz/deviceai/cognitive/MemoryIdentityHoldoutTest.kt \
  --test at.heinz.deviceai.cognitive.MemoryIdentityHoldoutTest
```

Record the freeze commit, test-file SHA-256, exact source/classpath provenance, compile exit, JUnit count and failures. Keep the test file unchanged when applying it to the frozen candidate. If a fixture defect is discovered, preserve and disclose the original freeze, justify the correction separately, and do not describe a revised suite as the original blind holdout.

These are deterministic JVM functional checks. They provide no model generalization, Android/Fold7 performance, battery, latency, persistence or end-to-end UI evidence. Rollback is removal of this test and this document only; no production API is changed.

## Recorded freeze and baseline result

The initial fixture freeze is commit `d698969f11a00806286c7864640d9af5bad69471`.
Test-file SHA-256: `088a7fc5a42017d68f21ce019d067d278c7b58f055321b3bf0931691eebeb339`.
The test file was not changed after this commit. Candidate production source was still not inspected when recording these results.

The command above actually ran against the clean fixture commit. Compilation exited `0`; JUnit 4.13.2 ran all 25 methods, with **7 PASS and 18 FAIL**, exit `1`. The 18 failures are the identity contract checks (methods 1–17 and 25). The seven compatibility checks (methods 18–24), including the complete numerical fixture, passed. This confirms that the new contract is not already satisfied by the baseline and that the frozen compatibility expectations match its actual behavior.

The coordinator harness reused cached application classes whose recorded compilation source is `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`, with baseline checkpoint `7b7c39d7`. Both production files used by this test were independently compared byte-for-byte against that source and against this worktree; they are identical. The harness does not represent a fresh full Android build of `ca31eb8b`.

| Evidence | SHA-256 |
| --- | --- |
| Baseline `HybridMemoryRetriever.kt` | `a5c963b7abd170fac0494c2a2d79cd0e313028189864969856aaf4bd99108aea` |
| Baseline `MemoryEcology.kt` | `0226d435ebab3fa4b1f8e135520bfbe283fb532dd89076cad7700e5bcb6426c2` |
| Cached application classes JAR | `7b7a2213d3e4a5ba88efffaa13a1261a6923eb11f8fdc4943ad1a2de0d398316` |
| Empty successful `compile.log` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `junit.log` | `c53b7ec1805e7af75a91a0d6c91dec1f37d4b74ced5c52af8e280154f974bbba` |
| Run `provenance.json` | `9f0e7d8c7b9c58f892cfb6eb8f598274c3288858bb5ad738865a98b34f5845e7` |

Raw run directory: `/workspace/scratch/624aeb26d481/qa/memory-holdout-baseline-20261104/`.
It contains the copied frozen test source, compile command, classpath provenance, compile log and full JUnit log. The coordinating agent can preserve that evidence when publishing the synthetic fixture. No candidate result is claimed here.

Next step: the coordinator first records the implementation candidate's commit, then applies the unchanged test file to that candidate and records exact production source hashes and the new result. Passing this functional holdout alone is not an APK, device, model-quality or production-release approval.
