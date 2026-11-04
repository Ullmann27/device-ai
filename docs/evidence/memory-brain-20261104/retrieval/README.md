# Retrieval identity repair and measured host optimization

Date: 2026-11-04. Experiment scope: **EXP-06** (provenance identity) and **EXP-07** (adversarial tests and independent holdouts).

## Source and scope

Work branch: `feat/continuum-memory-retrieval-20261104`, based on `ca31eb8b24b65d084750f0ea10290dc0d6be6733`.

The only production source changed is `app/src/main/java/at/heinz/deviceai/cognitive/HybridMemoryRetriever.kt`. New tests, a frozen test reference, the synthetic benchmark and these receipts accompany it. There are no learning, model-weight, journal-format, context compiler, Android Activity, voice or avatar changes in this package.

| Source | SHA-256 |
| --- | --- |
| Original retriever at `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` (also identical at the work base) | `a5c963b7abd170fac0494c2a2d79cd0e313028189864969856aaf4bd99108aea` |
| Tested candidate retriever | `8279ac693175e929f7ec6b8d64ac61861f97085e8ee4cab8a0d4e8236e3c0a15` |

`app/src/test/java/at/heinz/deviceai/cognitive/fixtures/HybridMemoryRetriever578.kt` preserves the original source, with only a test package, shared-type imports and a provenance comment added. It is test code and does not enter the app runtime.

## Confirmed defect and resulting behavior

The original retriever accepted multiple records carrying the same evidence ID. Conflicting contents or provenance could therefore be returned under an ambiguous ID. Filtering privacy first also allowed a public alias to survive despite a conflicting hidden variant. Exact duplicate records were returned repeatedly and changed BM25 statistics: the old document map collapsed equal records while the candidate count still included every copy.

The candidate scans **all input identities before scoring or applying the result limit**. If two complete records with an ID differ, that ID is excluded for the entire call, including any later copies. This check spans privacy classes so a hidden conflict cannot be concealed by the privacy filter. Structurally equal complete records collapse to one. After identity resolution, privacy filtering precedes tokenization and both caller-supplied scorers.

This is a deliberate fail-closed contract. Same-ID differences in content, privacy, provenance, validation, source reference, evidence references, timestamps, tags, parents, confidence or type are treated as ambiguity; the retriever does not guess which version supersedes the other. Legitimate revision selection belongs before retrieval. Conflict checks do not write, revoke or promote memory records.

## Optimization and compatibility

The candidate prepares each document's term-frequency map once and uses it for document frequency and BM25. It avoids using complete memory records as map keys, reuses one token separator regex, and retains at most `limit` scored results in a priority queue instead of sorting all relevant results. Every eligible candidate is still scored; a relevant record near the end is not lost.

For **unique IDs**, the reference tests compare complete returned records and every score component exactly. The lexical/semantic/entity/temporal/trust/validation/provenance/recency weights, penalties, stop words, German normalization, token limit, scorer handling and final ordering are unchanged. Ordering remains descending score, descending update time, then ascending ID. Relevant `STALE` and `CONTRADICTED` memories remain retrievable for explicit audits. Their admission to a model prompt remains a separate compiler responsibility.

Duplicate-corpus semantics are tested separately because rejecting conflicting identities and collapsing exact copies intentionally differs from the original behavior.

## Executed verification

These are host JVM checks compiled with the project's cached Kotlin 2.4.0 compiler and JDK 17 against frozen real app/test classes from `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`. The exact source-file hashes, compiler command, JUnit command and baseline class-jar hashes are in each `provenance.json`. This package did **not** run a new full Gradle build or Android device test.

| Receipt directory | Result | Purpose |
| --- | --- | --- |
| `retrieval-red` | Compilation passed; 13 tests, **6 expected failures** | New identity/privacy regressions against the unchanged original; all existing retriever tests passed |
| `retrieval-reference-baseline` | **6/6 passed** | Frozen reference and literal corpus expectations checked before optimization |
| `retrieval-identity-green` | **34/34 passed** | Minimal identity repair plus existing retrieval/research consumers and reference checks |
| `retrieval-green` | **34/34 passed** | Final optimized candidate on the same suite |

The seven new identity tests cover conflicting content, differing provenance/evidence and other identity fields, hidden/public conflicts with scorer visibility, exact-copy collapse, third-copy revival, a late conflict with `limit=1`, and hidden unique records that must not affect visible scores.

The six reference-test methods include a fixed 96-combination grid (8 queries × 3 limits × 2 privacy sets × 2 scorer configurations) over 128 unique synthetic records. Additional cases cover tie ordering and input permutations, a late unique match after 1,024 irrelevant records, the 4,096-token boundary, existing German morphology/compounds, irrelevant and grammar-only queries, empty inputs and stale/contradicted audit retrieval. Corpus seeds and fixtures were written before the optimization.

An independent agent is preparing withheld synthetic cases in a separate worktree. The candidate author has not inspected them. **Independent holdout result: NOT_RUN in this package; the integration owner runs them after the candidate commit is frozen.**

## Paired synthetic performance experiment

Raw results: `benchmark.json`. Fixed synthetic corpora; result limit 8; no model execution; x86_64 host JDK 17; `-XX:ActiveProcessorCount=2 -Xmx640m`. Each workload checks ranked-record and exact score parity before timing, performs six warmup pairs, then eleven measured pairs with alternating baseline/candidate order. Thread allocation counters report bytes allocated during each call, not retained heap or Android memory use.

| Workload | Records | Baseline median | Candidate median | Median time reduction | Allocated bytes: baseline → candidate |
| --- | ---: | ---: | ---: | ---: | ---: |
| Small dense corpus | 64 | 2.643 ms | 2.047 ms | 22.6% | 1,029,288 → 816,864 |
| Dense corpus | 2,048 | 50.108 ms | 33.460 ms | 33.2% | 32,084,232 → 24,955,536 |
| Late unique match | 2,049 | 16.832 ms | 8.743 ms | 48.1% | 6,437,232 → 4,411,712 |
| Mostly hidden corpus | 2,048 | 8.940 ms | 5.834 ms | 34.7% | 4,065,576 → 3,308,544 |

The allocation reduction was 18.6–31.5% across these cases. All timing samples, minima and maxima are preserved; these measurements are subject to host scheduling, JIT and garbage-collection effects. They establish a bounded host retrieval improvement on the fixed synthetic workload. They do not establish real-user retrieval quality, model-answer quality, voice/avatar correctness, total app latency or Fold7 performance.

## Reproduction and handoff

The focused invocation used the integration owner's existing harness (no parallel Gradle):

```sh
python /workspace/scratch/624aeb26d481/qa/run_focused.py retrieval-green \
  --repo /workspace/scratch/624aeb26d481/agent-memory-retrieval \
  --source app/src/main/java/at/heinz/deviceai/cognitive/HybridMemoryRetriever.kt \
  --source app/src/test/java/at/heinz/deviceai/cognitive/HybridMemoryIdentityTest.kt \
  --source app/src/test/java/at/heinz/deviceai/cognitive/fixtures/HybridMemoryRetriever578.kt \
  --source app/src/test/java/at/heinz/deviceai/cognitive/HybridMemoryRetrievalParityTest.kt \
  --source tools/memory-brain-review/RetrievalBenchmark.kt \
  --test at.heinz.deviceai.cognitive.HybridMemoryIdentityTest \
  --test at.heinz.deviceai.cognitive.HybridMemoryRetrievalParityTest \
  --test at.heinz.deviceai.cognitive.HybridMemoryRetrieverTest \
  --test at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest \
  --test at.heinz.deviceai.cognitive.MemoryResearchShadowComparatorTest

python tools/memory-brain-review/run-retrieval-benchmark.py \
  --compiled-provenance /workspace/scratch/624aeb26d481/qa/retrieval-green/provenance.json \
  --output /absolute/path/to/new-benchmark.json
```

Use a fresh harness label/output path for each rerun. The benchmark runner refuses changed source files, requires a passing compilation/test receipt, and does not overwrite prior results. The session-specific harness is not part of this repository. On another configured build host, all new JUnit tests and their frozen reference are ordinary test sources and can be run through the existing Gradle task:

```sh
./gradlew --offline :app:testDebugUnitTest \
  --tests at.heinz.deviceai.cognitive.HybridMemoryIdentityTest \
  --tests at.heinz.deviceai.cognitive.HybridMemoryRetrievalParityTest \
  --tests at.heinz.deviceai.cognitive.HybridMemoryRetrieverTest \
  --tests at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest \
  --tests at.heinz.deviceai.cognitive.MemoryResearchShadowComparatorTest
```

For a portable benchmark run, compile `tools/memory-brain-review/RetrievalBenchmark.kt` with the project's Kotlin compiler and the actual unit-test runtime classpath exported by `scripts/continuum-test-classpath.init.gradle`; then run `at.heinz.deviceai.cognitive.review.RetrievalBenchmark` with that classpath and the compiled benchmark directory. Its JSON stdout includes the fixed corpus, raw timings and allocation counts. This alternative was not executed here; the captured focused-harness path was.

Next: freeze this delivery commit, run the separately withheld cases, then integrate with context/compiler and event-lifecycle changes under the integration owner's review. Public publication is handled by that owner; this subagent produces local commits only. The combined build and device gates remain open. Rollback is a reviewable revert of this package; no persisted memory format or migration is involved.
