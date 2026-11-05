# Round 2: independent coordinator verification

**PASS. All round-2 candidates remain archived and unadopted. Active production is identical to round 1.**

These are independent correctness and provenance checks after the helpers completed their measurements. No timing benchmark was repeated by the coordinator. `verification.json` records the tested checkout, exact production hashes, preserved output hashes and raw-data validation results.

## Executed checks

| Scope | Actual final route | Result |
| --- | --- | --- |
| LM | `inference/run.sh` with `before` and archived-source `after` (separate processes) | 10/10 JUnit methods each |
| Context | `tools/tuning-memory-r2-20261104/run.py check` with the existing R1 compiler/dependency classpaths | Compile PASS; 27/27 JUnit methods each; 462 complete parity cases and 128 lifecycle comparisons PASS |
| Active worker | Three Node test files listed below | 59/59 methods; zero failed, skipped or cancelled |
| Archived binary worker | Explicit `--candidate=binary --parity-only` | 1,225 comparisons PASS |
| Archived exponential worker | Explicit `--candidate=exponential --parity-only` | 1,225 comparisons PASS |

The 96 distinct test methods include 18 newly added regressions. Repeated baseline/candidate runs are not extra methods. The direct comparison loops are separate case counts. The worker count is 960 selection cases, 27 between-call mutations, 19 operation/validation cases and 219 position boundaries per archived candidate.

LM checks ran from local root commit `ff6555efd92a2a7913495497d5d2548b40d975db`, context checks from `a62b72564cb79ddf334b183a5a9b9760372a3a5f`, and worker checks from `827e97e7bd516b2d6757062b0ea63ea3a4d6b5de`. Only independent logs and documentation were added afterward. The source receipts record the actual baseline resolution. The preferred public baseline and local fallback have the required identical tree; no public commit object was fabricated.

## Commands

The LM command was run once for each phase, with this session's existing Gradle library directory as its compiler/JUnit classpath source:

```sh
bash docs/evidence/three-tuning-bots-r2-20261104/inference/run.sh /workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib /workspace/scratch/59ad19c6d3b7/coordinator-r2/inference before
bash docs/evidence/three-tuning-bots-r2-20261104/inference/run.sh /workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib /workspace/scratch/59ad19c6d3b7/coordinator-r2/inference after
python3 tools/tuning-memory-r2-20261104/run.py check /workspace/scratch/59ad19c6d3b7/coordinator-r2/memory
node --test --test-reporter=spec .github/continuum/autonomous-teamleader.test.mjs .github/continuum/development-backlog.test.mjs .github/continuum/development-backlog-dependencies.test.mjs
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=binary --parity-only
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=exponential --parity-only
```

The two LM processes were started concurrently. Their comparison tests are functional, not timing measurements. The context environment variables were populated from the exact R1 `compile-command.json` classpaths; the expanded final command and dependency hashes are preserved in `memory/`. No dependency download occurred. These session paths document the execution environment; reproducibility on a different host uses equivalent available classpaths as described by the individual runners.

## Evidence review

The coordinator checked all three active source files against the R1 Git blobs and verified an empty production diff. It reviewed the exact candidate patches, confirmed source hashes on the independent archive routes and retained raw warnings and failures.

- LM: the strict summarizer was rerun on the original raw records; its complete result equals the committed summary. Both processes contain 18 scenarios and 324 measurement records, with all checksums complete. The AFTER startup warning remains verbatim.
- Context: four hot workloads each have 12 alternating pairs; all committed allocation, CPU and wall medians were independently recalculated. All 48 allocation comparisons favor the candidate. Exactly three cold pairs retain equal output hashes.
- Worker: both archived measurements contain the full 40-case matrix and nine alternating pairs per case. Time medians and paired ratios were recalculated. Original harness and source hashes match the archives; current reproduction timing/counting tails and fixtures are identical. The single aborted attempt has zero stdout bytes; the single successful recovery has zero stderr bytes.

The source, raw-data and functional results support reproducing the experiments and retaining R1. They do not establish Android/ART, Fold7, whole-worker, model-quality, battery or thermal performance. No additional performance run or production change was used for these checks.
