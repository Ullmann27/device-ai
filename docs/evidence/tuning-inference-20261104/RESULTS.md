# LM1 greedy sampler tuning — 2026-11-04

**Experiment:** EXP-05. **Status:** source tuning with isolated host JVM verification; Android/Fold7 acceptance is not available. This is a sampler microbenchmark, not an end-to-end model or Gemma benchmark.

## Exact change and source identity

Base: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` (verified PR #491 base). Branch: `tuning/inference-59ad`; publication is coordinated through Draft PR #511.

Only `LmEval.sample` in `app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt` changes in production. When `temperature <= 0.0`, one scan selects the first maximal value using `Float.compareTo`. This removes the full vocabulary sort and its temporary collections. `topK.coerceIn` still runs before returning, preserving the existing failure on an empty array. The positive/NaN-temperature sorting expression and sampling arithmetic remain the original expressions.

- Final candidate B `LmModel.kt` SHA-256: `d9fc7493360e526323505ff1637981a6e024f6c61a47e4d6f24df22d248df65d`.
- Preliminary candidate A SHA-256: `3b554b00e5119edc3e063c4310efc3d471b071a22fe61111ed283418ec84343d`. A moved the `coerceIn` result outside the branch; B confines validation to the greedy branch and retains the original positive sorting expression. A's patch and every raw sample are retained.
- [manifest.json](manifest.json) records baseline/final source hashes, toolchain, test/harness hashes, compiled class hashes and raw-data hashes. The compiled benchmark and sorted-reference classes are byte-identical between the original before build and final B build.
- [source-after.json](source-after.json) was emitted by the final build before compilation. Original before-run source hashes are reconstructed explicitly from the clean, verified base in `manifest.json`; they are not represented as a pre-run receipt.

No model weights, LM1 training configuration, tokenizer, merge order, token IDs, checkpoint format, gates, memory/worker code, or offline switch change.

## Actual callers and scope

`LmRuntime.generate` calls `LmEval.sample` after the existing probability calculation. `LmRuntime.Request` defaults to `temperature=0.0, topK=1`. `LmEval.generate` also calls the sampler. Test/research callers include `LmRuntimeDemo`, `LmExperiment`, and `LmStep7`.

A search of this source tree's `app/src/main/java` found no caller that loads `LmRuntime` outside `LmRuntime` itself. The existing LM1 inference component is therefore the verified scope. This change is not evidence that the live Gemma/LiteRT main-chat path becomes faster. Forward computation, model load time, time to first token, total tokens/second, battery use and thermals were not measured.

## Quality and compatibility checks

Five JUnit methods passed against the unchanged base and again against final B. They contain 35,624 direct sampler parity cases plus 50 next-token comparisons over actual tiny Transformer/minGRU forward passes with Unicode prompts. Checks include:

- Stable ties and the first index, NaN payloads, infinities, signed zero, raw random Float bit patterns and vocabulary sizes through 8,192.
- Negative/zero temperatures and top-k boundaries including minimum/maximum integers.
- Positive, infinite and NaN temperatures, seeded sampled outputs and subsequent RNG state.
- Empty inputs: exception class/message and untouched RNG state.
- Input-array raw bits remain unchanged; Unicode tokenization/serialized tokenizer bytes are unchanged.

The sorted reference method was compared byte-for-byte with the method in the base source before the first measurement. The versioned runner repeats that comparison. Bytecode inspection of final B confirms a primitive `java.lang.Float.compare(FF)I` in the greedy loop.

All 162 individual batch measurements (81 reference/production pairs) in each of the three archived runs are present. `summarize.py` checks every scenario, every repetition and checksum equality across both implementations and all three runs. This is exact token-selection parity for the tested inputs, not a claim of better language-model quality.

## Host measurement method

OpenJDK 21.0.13, Kotlin compiler/stdlib 2.0.20, JVM target 17, JUnit 4.13.2; AMD EPYC 9V74 host, amd64, 9 available CPUs, allowed CPU affinity 0–8 with no pinning. Heap `-Xms256m -Xmx256m`. Other chats could create load; start load averages were 5.667 before, 4.539 in A and 4.343 in final B. No CPU frequency or thermal control was available.

Each of nine scenarios uses 32 deterministic arrays. Four warm-up batches per implementation precede nine alternating reference/production pairs. Batch sizes are 1,024 operations at vocabulary 258/512, 384 at 2,048 and 128 at 8,192. A checksum is consumed after every batch. Thread CPU time, wall-clock time and `ThreadMXBean` allocated bytes are collected for each complete batch. These are one-process host observations, not confidence intervals or device measurements.

The original production before-run was performed **before** editing `LmModel.kt`. The final table compares B with the unchanged sorted reference **interleaved in the same final process**, reducing dependence on between-run host load. All original observations remain in [before.jsonl](before.jsonl), the preliminary [candidate-a-after.jsonl](candidate-a-after.jsonl), and final [after.jsonl](after.jsonl). [summary.json](summary.json) contains medians and observed min/max values for every metric, version and scenario.

## Final B: every scenario

Values are medians per sampler invocation; CPU/wall units are microseconds. Allocations include the fixed per-batch harness/RNG overhead. Greedy B allocated 56 bytes per entire batch (0.055–0.438 bytes per invocation), without vocabulary-sized per-call allocation.

| Vocabulary / distribution / temperature | CPU reference → B (µs) | Wall reference → B (µs) | Allocated bytes reference → B |
| --- | ---: | ---: | ---: |
| 258 / random / 0 | 31.227 → 0.278 | 31.927 → 0.276 | 7,552.055 → 0.055 |
| 258 / all ties / 0 | 6.301 → 0.779 | 6.347 → 0.776 | 7,552.055 → 0.055 |
| 512 / random / 0 | 61.615 → 0.566 | 61.846 → 0.563 | 14,120.055 → 0.055 |
| 512 / all ties / 0 | 4.866 → 1.379 | 5.015 → 1.376 | 14,120.055 → 0.055 |
| 2,048 / random / 0 | 310.125 → 2.359 | 310.148 → 2.353 | 71,731.146 → 0.146 |
| 2,048 / all ties / 0 | 19.093 → 5.529 | 19.063 → 5.521 | 65,616.146 → 0.146 |
| 8,192 / random / 0 | 1,522.409 → 9.227 | 1,525.693 → 9.214 | 271,817.438 → 0.438 |
| 8,192 / all ties / 0 | 75.319 → 22.359 | 75.558 → 22.410 | 243,088.438 → 0.438 |
| 512 / random / 1, top-k 50 control | 69.546 → 71.028 | 69.539 → 71.214 | 15,800.055 → 15,824.055 |

### Positive-temperature tradeoff remains open for review

The control's additional **24 bytes per invocation (+0.152%)** occurred in all nine measured repetitions of both A and B. Before the change, the two separately compiled but algorithm-identical implementations both measured 15,800.055 bytes per invocation. Thus the allocation difference was **not** already present in the original before comparison and must not be dismissed as pre-existing measurement noise. Its precise JIT cause was not isolated, and Android ART behavior was not measured.

The CPU median difference for the control was −3.9% in the original identical-algorithm comparison, +1.8% in A, and +2.13% in B. A small real positive-path slowdown remains possible; shared-host timing does not establish its precise magnitude. The patch is a strong greedy-path improvement with this measured control-path cost, **not an unconditional no-regression claim**. No further optimization was attempted after the complete B comparison.

## Reproduce without downloads

Run from a checkout of the candidate commit with the existing Gradle 8.11.1 `lib` directory (providing the Kotlin 2.0.20 compiler/stdlib and JUnit 4.13.2). The output directory is separate from the source tree.

```bash
bash docs/evidence/tuning-inference-20261104/run.sh /path/to/gradle-8.11.1/lib /tmp/lm-sampling before
bash docs/evidence/tuning-inference-20261104/run.sh /path/to/gradle-8.11.1/lib /tmp/lm-sampling after
```

Those commands compile and run the five tests. Set `LM_SAMPLING_RUN_BENCHMARK=1` for each invocation to run the full measurements. **`before` now extracts all six production LM Kotlin files from the fixed base with `git show`; it never relabels the current candidate as the baseline.** The runner checks reference identity, emits source receipts and compiles those extracted files. `after` snapshots the current production files into its output directory before compilation.

To validate the archived observations and reproduce their summary:

```bash
python3 docs/evidence/tuning-inference-20261104/summarize.py docs/evidence/tuning-inference-20261104
```

Actual commands used the already installed local toolchain under `/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib`; compilation/test output and measurement output were written under the bot's own workspace. No Gradle build, toolchain download, paid service or full model training was performed.

## Remaining acceptance and rollback

Android/Gradle build: `NOT_RUN`. Fold7/ART timing, allocation behavior and thermals: `UNAVAILABLE`. Existing wider LM/core/runtime suites were not rerun by this bot; the new five-test suite is the executed scope. No APK, Main merge, model promotion or production release is implied.

Review should explicitly accept or reject the small measured stochastic-path allocation cost. A later authorized device check should compare the same caller/checkpoint/seed/prompt and both sampling modes, including end-to-end latency and time-limit behavior. Rollback is a normal revert of this isolated tuning commit; no migration or data rollback is required.

Primary references: [Kotlin stable descending sort](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/sorted-by-descending.html), [Android Microbenchmark guidance](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview). These document API semantics and measurement cautions; the numerical results above come solely from the archived local runs.
