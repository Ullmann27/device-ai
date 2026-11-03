# Independent GAMMA challenger B assessment

**Conclusion: no correctness blocker found in the frozen challenges; stable same-type replay is materially cheaper. Actual app/caller speedup is not established.** This is bounded host research evidence, not Android/device or training acceptance.

The challenger read baseline API/storage source and existing callers, wrote and committed its own executable fixtures, then requested the candidate SHA. Author tests, scores, memory handoff and PR399 body were not consulted. Freeze: [`e788d770f212a9b93f5a6ac703b67e82422e1839`](https://github.com/Ullmann27/device-ai/commit/e788d770f212a9b93f5a6ac703b67e82422e1839), UTC `2026-11-03T18:39:25Z`. `FROZEN.sha256` remains valid after evaluation. The initial preregistration was pushed at `76e9a3b6`, Draft [PR406](https://github.com/Ullmann27/device-ai/pull/406) targets the Chief branch.

- Baseline: `64ec06a73f50cae9ee49035c840710d87777d50f`.
- Candidate source: `a6007442768b24f5aa700c61dae7c46049c1911a`.
- Frozen harness SHA256: `1da1535deb7ad7e8212b173a1f328fc55bd5b34810663754ae86bc5ed8130ff8`.
- Official toolchain: Kotlin JVM 2.4.0, OpenJDK 17.0.20.1, Linux 6.18.57 x86_64; JVM heap cap 384 MiB; host reports 5 processors. No heavy Gradle.
- Candidate source inspection after freeze found only `ContinuumLearningExperience.kt` changed in the learning package. LEX3 source hash is identical across arms.

## Correctness and failure classes

The same ten frozen challenge groups pass against baseline and candidate. Selection compares full records with an independent chronological/status/type oracle; timestamp ties, all types/statuses, varying limits, and invalid limits are covered. The fixture is generated synthetic data, including synthetic records carrying the protected trust enum.

| Failure class | Actual probe and outcome |
| --- | --- |
| Selection/order/filter drift | All seven types; limits 1,128,7,64,2,127; full-record equality PASS |
| Caller result aliasing | Returned list clear attempt; candidate mutable result cannot poison subsequent replay PASS; baseline result rejects clear |
| Stale same-instance status | Revoke via same object, reconfirm via another object, compare full replay PASS |
| Concurrent separate instances | Real writer thread alternates eight statuses while reader performs twelve replays; final committed revocation excluded PASS |
| Cross-process stale read / lease bypass | Child JVM holds real OS lock while a reader and a second child writer wait; neither bypasses lease, post-commit revocation excluded PASS |
| Hidden corruption in unselected records | Corrupt a segment containing none of selected IDs, keep byte length and mtime identical; warm replay throws PASS |
| Segment loss/symlink | Delete unselected segment, then replace with symlink to synthetic copy; both fail closed; byte restoration recovers PASS |
| Manifest damage / missing primary | Corrupt manifest checksum, then remove primary with segments intact; both fail closed PASS |
| Whole-store replacement | Remove entire warmed directory; replay empty; recreate with disjoint record; only new generation returned PASS |
| Unbounded retained cache / changed caps | Instance graph retains at most128 LearningExperience objects after each type and at4096 records; baseline retains0; constants remain4096/32MiB PASS |

The retained-record probe follows fields of project classes and list/map/array elements, and counts distinct record identities. It does not establish total peak heap usage. The 4096-record fixture is populated successfully; 32MiB is checked as the unchanged source constant, not by a >32MiB overflow fixture. Same-instance mutation is serial in this harness; concurrency uses independent instances plus a child JVM. Third-party integration QA is responsible for any additional same-instance race/capacity boundary cases.

## Performance interpretation

See [raw median table](BENCHMARK-TABLE.md) and all raw `.log` files. Each benchmark-only JVM warms a separate256-record ledger, then measures256/1024/4096 records with256-character payload padding. Scenarios have3 cold,6 warm,4 caller-shape,4 alternating,3 mutation samples. Sampling and scenario order were frozen before candidate access. Each sample reports wall nanoseconds and current-thread allocated bytes. Disk population is outside measured operations; mutation-and-replay includes mutation I/O. The alternating scenario begins with one cache hit before its type misses; use its median with that qualification.

At4096 records, stable same-type replay median is **49.085ms baseline versus4.925ms candidate** (~10×); baseline repeat is57.519ms. Allocated bytes fall from**101.876MiB to8.621MiB** per call (~91.5%). The cache retains128 records; it still reads/hashes all segments, so this does not remove file I/O or establish constant cost in ledger size.

The source48→intent96 caller-shaped pair is **109.221ms baseline versus116.986ms candidate** (baseline repeat112.356ms), with essentially unchanged allocation203.753→203.808MiB. Cold4096 construction+replay is51.009→55.728ms (baseline repeat62.892ms); mutation+replay131.050→134.792ms (repeat142.123ms). These timings show no measured benefit for those scenarios; run-to-run variation prevents a robust small-regression claim. Small256-record cold/type-switch timings vary more and are all disclosed in the table.

The actual baseline `ContinuumLearningCore` calls replay after an intent confirmation mutation, and its source-learning path replays SOURCE_TEXT48 then INTENT_LABEL96. Those shapes invalidate or replace this single-type selection. The harness isolates this read-pair shape and mutation+replay; it does **not** run model loading, training, audits, UI, or Android end-to-end. There is no evidence here that app learning becomes10× faster.

The first candidate process ran correctness before benchmarks (`candidate-all.log`) and therefore had a different JIT history. Its correctness results are used, but its timing rows are excluded from the comparison table. Fresh candidate benchmark-only and a second baseline benchmark-only JVM were run sequentially afterward. Even the frozen replay-based warm-up gives the baseline more full-decode warm-up than the cached candidate; host scheduling/JIT effects and tiny sample sizes remain limitations. Allocation reductions on stable replay are stronger evidence than precise timing ratios. No confidence interval or broad throughput claim is made.

## Exact reproduction and evidence

Run from the branch root; scripts source `/workspace/continuum-build-env/env.sh` and compile only the two storage files plus frozen harness:

```bash
bash docs/evidence/gamma-challenger-20261103/run.sh 64ec06a73f50cae9ee49035c840710d87777d50f check > docs/evidence/gamma-challenger-20261103/baseline-check.log 2>&1
bash docs/evidence/gamma-challenger-20261103/run.sh 64ec06a73f50cae9ee49035c840710d87777d50f bench > docs/evidence/gamma-challenger-20261103/baseline-bench.log 2>&1
bash docs/evidence/gamma-challenger-20261103/run.sh a6007442768b24f5aa700c61dae7c46049c1911a all > docs/evidence/gamma-challenger-20261103/candidate-all.log 2>&1
bash docs/evidence/gamma-challenger-20261103/run.sh a6007442768b24f5aa700c61dae7c46049c1911a bench > docs/evidence/gamma-challenger-20261103/candidate-bench.log 2>&1
bash docs/evidence/gamma-challenger-20261103/run.sh 64ec06a73f50cae9ee49035c840710d87777d50f bench > docs/evidence/gamma-challenger-20261103/baseline-bench-repeat.log 2>&1
python3 docs/evidence/gamma-challenger-20261103/summarize.py > docs/evidence/gamma-challenger-20261103/BENCHMARK-TABLE.md
sha256sum -c docs/evidence/gamma-challenger-20261103/FROZEN.sha256
```

All five run commands exit0; both correctness arms report10 groups passed; all frozen hashes recheck OK. `summarize.py` is a post-evaluation descriptive report generator, not a frozen test change. Raw logs include exact source hashes/tool versions. No production edits, owner data, model weights, main merge, sign/release/install, cloud inference, or new infrastructure. Android/device/Gradle: NOT_RUN. Quota: NOT_VISIBLE. Further claims require the independent final integration QA at its exact combined SHA.
