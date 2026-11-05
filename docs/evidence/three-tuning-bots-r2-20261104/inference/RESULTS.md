# Round 2: primitive sampler weights — candidate not adopted

**Status: `RETURNED_TO_R1 / CANDIDATE_NOT_ADOPTED`. Experiment: EXP-05.**

The tested candidate reduces allocated bytes in every positive/NaN-temperature scenario. It also has higher median thread CPU time in all ten of those scenarios in the final paired host process. Root therefore withheld adoption: allocation savings alone do not establish a sufficiently clear overall benefit, especially with the observed 8–12% CPU increases at full top-k. This is not a demonstrated Android/ART or Fold7 regression. There was no second candidate or additional timing attempt.

The active production `LmModel.kt` has been restored byte-for-byte to round one. This evidence package preserves the exact candidate, its patch, functional tests, bytecode, both complete measurement processes and their source identities.

## Sources and exact candidate

Published round-one baseline: `04b46f125d1086e6ea540d3c68700407f340f607`. The measured builds used the supplied local commit `db3295d51c769afdaf809ac1dfae2ada84a3274f`. Required baseline tree: `d753188fe0e67f6c6f098db6b7073ea199e05530`.

| Item | SHA-256 / location |
| --- | --- |
| Restored active / round-one `LmModel.kt` | `d9fc7493360e526323505ff1637981a6e024f6c61a47e4d6f24df22d248df65d` |
| Exact tested candidate `LmModel.kt` | `c329c9d78d28cbcccbd94671eed4f009a6c8c95a5ba69251c07c05bd845d4448` |
| Complete archived candidate source | [candidate/LmModel.kt](candidate/LmModel.kt) |
| Minimal candidate patch | [candidate.patch](candidate.patch) |
| Original precompile source receipts | [source-before.json](source-before.json), [source-after.json](source-after.json) |
| Verified local baseline resolution | [baseline-resolution.json](baseline-resolution.json) |
| Manifest, class and raw-data hashes | [manifest.json](manifest.json) |

The only candidate change was in `LmEval.sample`:

```kotlin
val w = DoubleArray(order.size) { i -> exp(ln(p[order[i]].toDouble().coerceAtLeast(1e-30)) / temperature) }
```

It replaced `order.map { ... }` weight storage. The greedy branch, sort, top-k clamp, floating-point expression, `rnd.nextDouble() * w.sum()` and subtraction/selection loop stayed in place. The candidate removes boxed `Double` weights; it does not separately identify the specific object responsible for round one's earlier +24-byte control-path difference.

No training, weights, tokenizer, checkpoint format, Runtime connection, gates, worker/memory path or offline setting was changed. There is **no active production source change in this round** after Root's decision.

## Functional and bytecode evidence

Both the original round-one snapshot and candidate snapshot compiled locally and passed **10/10 JUnit methods**: the five existing sampling methods plus five new round-two methods. The existing e101 reference remains an additional regression check; the new reference method is byte-for-byte the actual round-one method.

- 35,624 existing direct parity cases plus 2,720 round-two cases check selected token, exception class/message, later RNG state and unchanged input bits.
- Fixed draws at probability boundaries check exact `nextDouble` call counts, including zero and NaN temperatures and empty inputs.
- Two callback cases verify that weights are computed before the RNG callback, including an RNG callback that throws.
- 70 comparisons check raw Double sum bits, including small/large terms, signed zero and NaN.
- 130 token comparisons per version cover actual tiny Transformer/minGRU forward passes with Unicode prompts across both test classes; tokenizer serialization remains unchanged.

[bytecode.json](bytecode.json) and the reproducible `verify-bytecode.py` check the compiled artifacts and installed Kotlin 2.0.20 standard library. They confirm the unchanged greedy bytecode, removed `Double.valueOf` calls for weights, primitive double-array allocation/access, and sums that begin at `0.0` and add in the same left-to-right order. The compiled round-one reference and benchmark classes are byte-identical between both builds.

This establishes the tested sampler's parity, not improved language-model quality or model-wide performance. Existing broader LM/core/runtime suites and Android builds were not run by this bot in round two.

## Fixed measurement design

[PROTOCOL.md](PROTOCOL.md) was written before timing. One candidate, two JVM processes, all 18 scenarios retained: eight existing greedy controls and ten positive/NaN-temperature cases. Each process has 324 batch observations: 18 scenarios × nine alternating reference/production pairs, after four warm-up batches per implementation and scenario.

Each case uses the same 32 deterministic arrays and RNG seed. A checksum includes tokens in order and the following RNG state. All checksums match across both implementations and both processes. Batch operation counts are 1,024 for vocabulary 258/512, 384 for 2,048 and 128 for 8,192.

Both source snapshots/jars were frozen before the coordinated timing window. The BEFORE process compares two identical round-one implementations. The final AFTER process pairs the unchanged round-one reference with the candidate in the same JVM. This avoids substituting the older e101 implementation for the actual round-two baseline.

Environment: OpenJDK 21.0.13, Kotlin compiler/stdlib 2.0.20, JVM target 17, JUnit 4.13.2, amd64 host, nine available CPUs, affinity 0–8 without pinning, `-Xms256m -Xmx256m`. Other tuning bots were idle during the assigned slot; unrelated host work was not controlled. Start load averages were 3.605 and 4.612. These are host microbenchmarks with nine within-process observations, not device estimates or confidence intervals.

## Positive and NaN-temperature results: all ten cases

Values are final-process medians per sampler call. Time units are microseconds. Allocated bytes include the same fixed per-batch harness/RNG overhead.

| Vocabulary / distribution / temperature / top-k | Allocated bytes R1 → candidate | Thread CPU R1 → candidate (µs) | CPU difference | Wall time R1 → candidate (µs) |
| --- | ---: | ---: | ---: | ---: |
| 512 / random / 1 / 50 | 15,824.055 → 14,800.055 | 62.247 → 63.039 | +1.27% | 63.720 → 63.065 |
| 512 / random / 1 / 1 | 14,264.055 → 14,192.055 | 58.896 → 59.028 | +0.22% | 58.939 → 59.047 |
| 512 / random / 1 / 512 | 30,648.055 → 20,352.055 | 70.388 → 76.460 | +8.63% | 70.601 → 76.497 |
| 512 / ties / 1 / 50 | 15,864.055 → 14,808.055 | 7.305 → 7.617 | +4.26% | 7.299 → 7.610 |
| 2,048 / random / 0.8 / 40 | 73,155.146 → 72,299.146 | 284.459 → 293.546 | +3.19% | 284.630 → 293.717 |
| 2,048 / random / 1 / 2,048 | 137,411.146 → 96,395.146 | 328.416 → 366.617 | +11.63% | 335.476 → 372.153 |
| 8,192 / random / 1 / 50 | 273,561.438 → 272,505.438 | 1,365.472 → 1,434.690 | +5.07% | 1,366.140 → 1,435.380 |
| 8,192 / random / 1 / 8,192 | 534,105.438 → 370,209.438 | 1,571.242 → 1,712.226 | +8.97% | 1,571.829 → 1,714.769 |
| 512 / random / NaN / 50 | 15,864.055 → 14,808.055 | 49.745 → 52.930 | +6.40% | 49.739 → 55.148 |
| 512 / special / 1 / 50 | 15,864.055 → 14,808.055 | 62.773 → 64.453 | +2.68% | 62.767 → 64.458 |

Savings range from 72 to 163,896 allocated bytes per call in these cases. These figures measure allocation traffic, **not peak or retained model memory**. No whole-model latency/throughput or device power measurement was made.

The positive/NaN BEFORE identical-algorithm controls' CPU differences ranged from −9.97% to +6.78%. This shows meaningful host/JIT variability. It does not justify discarding the final candidate's unfavorable timings: the full-top-k cases had +8.63%, +11.63% and +8.97% CPU medians, compared with +1.82%, −0.98% and +0.43% in the corresponding BEFORE controls. Root's decision is to preserve the candidate as evidence and retain R1, not to assert a definitive hardware regression from one host experiment.

## Greedy controls: all eight cases

All eight cases have identical allocated bytes in both implementations: 56 bytes per full batch, equivalent to 0.055–0.438 bytes per call. Their timing variation is recorded even though the greedy bytecode is unchanged; it is not claimed as a greedy optimization.

| Vocabulary / distribution | CPU R1 → candidate (µs) | Wall R1 → candidate (µs) | Allocated bytes in both |
| --- | ---: | ---: | ---: |
| 258 / random | 0.210 → 0.196 | 0.209 → 0.195 | 0.055 |
| 258 / ties | 0.540 → 0.614 | 0.539 → 0.612 | 0.055 |
| 512 / random | 0.387 → 0.386 | 0.385 → 0.385 | 0.055 |
| 512 / ties | 0.968 → 0.923 | 0.965 → 0.920 | 0.055 |
| 2,048 / random | 1.578 → 1.168 | 1.573 → 1.164 | 0.146 |
| 2,048 / ties | 3.682 → 3.182 | 3.676 → 3.178 | 0.146 |
| 8,192 / random | 4.763 → 3.845 | 4.756 → 3.837 | 0.438 |
| 8,192 / ties | 14.205 → 12.779 | 14.193 → 12.760 | 0.438 |

## Raw data and startup anomaly

[before.jsonl](before.jsonl) preserves the complete original baseline output. [after.jsonl](after.jsonl) preserves the complete candidate output, including one non-JSON JVM startup line:

```text
[0.001s][warning][perf,memops] Cannot use file /tmp/hsperfdata_agent/30 because it is locked by another process (errno = 11)
```

The line precedes metadata and warm-up. Both JVMs exited 0 and each emitted all 326 expected JSON records. The original strict parser rejected this line. The final parser admits only this specific warning type at the start, records it separately, and still rejects every other non-JSON line or a warning inserted after metadata. No raw data was removed or rewritten and no timing process was repeated.

`summarize.py` enforces every declared scenario, all nine pairs and checksum identity across both versions. Four negative checks reject unknown output, a mid-run warning, a missing measurement and a checksum mismatch. [summary.json](summary.json) includes all medians and observed minima/maxima for both processes; it does not select favorable samples.

## Reproduce the archived candidate, not the restored product source

Run from this repository with the existing Gradle 8.11.1 library directory and a separate output directory:

```bash
bash docs/evidence/three-tuning-bots-r2-20261104/inference/run.sh /path/to/gradle-8.11.1/lib /tmp/lm-sampling-r2 before
bash docs/evidence/three-tuning-bots-r2-20261104/inference/run.sh /path/to/gradle-8.11.1/lib /tmp/lm-sampling-r2 after
```

Default execution only compiles and runs the ten tests. `LM_SAMPLING_RUN_BENCHMARK=1` additionally enables the fixed full benchmark; no further timing run was made in this round after the decision.

The runner prefers the actual published baseline commit `04b46f1...`; only when that object is absent does it use the supplied local `db3295d...` fallback. It rejects a wrong object type or a tree other than `d753188...` and records the actual resolution. It never creates or invents a commit object.

Both phases extract the exact six baseline LM source files. AFTER then replaces only `LmModel.kt` with the archived candidate and verifies its fixed SHA-256. Every snapshot file is checked against the baseline/candidate before compilation. **The restored active production method is not relabeled as the candidate.** The final source-resolution adjustment was syntax/identity checked; Root will independently execute this final reproduction route. The original measured source receipts remain unchanged.

To verify the archived observations:

```bash
python3 docs/evidence/three-tuning-bots-r2-20261104/inference/summarize.py docs/evidence/three-tuning-bots-r2-20261104/inference
```

## Acceptance boundary and next step

Active product source: returned to R1. Candidate: archived, not adopted. Android/Gradle build: `NOT_RUN`. Fold7/ART measurements: `UNAVAILABLE`. End-to-end LM1 and Gemma/LiteRT performance: `NOT_MEASURED`.

The existing connection remains `LmRuntime.generate` / `LmEval.generate` → `LmEval.sample`; no Runtime integration was added. Root publishes this evidence/test package for review. Any future reconsideration needs an explicitly justified device/resource tradeoff test; this round does not authorize another optimization or timing sweep. No persisted state, weights or model formats require rollback.
