# R2 archive: existing secret-redaction pattern reuse was not adopted

**Status: RETURNED_TO_R1 / CANDIDATE_NOT_ADOPTED.** The measured candidate reduced
allocation, but increased thread CPU cost in the secret-rich workloads. Active
production retains R1, including its byte counter. No further variant or timing
round was run.

The archived candidate moves the two unchanged `Regex` literals from
`AiContextCompiler.redactSecrets` into private class-level immutable values.
Replacement order, callback, replacement strings and matcher use are unchanged.
No input text, result or matcher is cached. R1 byte accounting remains intact.
Its complete measured source and narrow patch are in `candidate/`.
The patch uses zero context (`git apply --unidiff-zero --check`); the runner
reads the complete archived source and does not apply the patch to production.

The Java 21 [Pattern documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html)
specifies that compiled patterns may be shared between threads, while matching
state belongs to a matcher. The local Kotlin 2.4.0 bytecode and parallel tests
check the concrete use here. This is not an Android device acceptance result.

## Exact baseline and offline reproduction

The preferred baseline is published R1 commit
`04b46f125d1086e6ea540d3c68700407f340f607`. A local-only bootstrap may resolve
`db3295d51c769afdaf809ac1dfae2ada84a3274f` instead. Both must have tree
`d753188fe0e67f6c6f098db6b7073ea199e05530`; the runner verifies the tree and records
all resolution attempts and the actual commit. GitHub reproduction needs only
the published commit. The e101 pre-R1 implementation is not the R2 baseline.

Use the same existing classpaths as R1: Kotlin compiler/dependencies through
`CONTINUUM_KOTLIN_COMPILER_CP`, and Kotlin stdlib, JUnit/Hamcrest, annotations,
Android SDK, LiteRT-LM, OkHttp/Okio through `CONTINUUM_KOTLIN_DEPENDENCY_CP`.
No dependency download, Gradle build, model or Android framework execution is
performed. New output stays separate from R1.

The current runner explicitly replaces the active compiler source in the host
source list with `candidate/AiContextCompiler.kt`, requiring SHA-256
`cc95d26e60fe464223db1db171234e2d9eca0c215e94a9069e1a14d00fd9d2d0`.
It never labels the restored R1 source as the R2 candidate. Start with a new
output directory for reproduction. `archive-binding.json` links the original
measurement paths, exact source hashes and saved driver to the archive; the
original raw manifest remains unchanged. Root verifies the archive route
independently; archiving itself does not repeat measurements.

```bash
python3 tools/tuning-memory-r2-20261104/run.py check /absolute/external/r2-output
python3 tools/tuning-memory-r2-20261104/run.py measure /absolute/external/r2-output
```

`measure` is a separately scheduled action, after correctness gates. It checks
all source, harness and dependency hashes from `check` before execution.

## Correctness

The runner reuses the unchanged R1 complete source graph, four existing JUnit
suites (21 tests), and original 462-case parity harness. It generates the real
baseline compiler from the resolved R1 commit, changing only its class name.
Common input/output/policy declarations and both regex literals are checked
literally before compilation. Both versions use the same real dependencies.

Six new JUnit methods cover exact key/bearer replacements and nonmatches,
unchanged rule order, Unicode/unpaired surrogates, repeated/fresh instances,
failure recovery, parallel distinct inputs and whole-section byte boundaries.
They run against both versions. A further 128 direct result/byte comparisons
exercise both lifecycle modes on the measurement fixtures before timing.

## Frozen measurement matrix

Both workloads run with one reused compiler and with a fresh compiler instance
for every call. The fresh mode includes constructor cost for every operation;
both hot modes have already initialized their classes. Inputs and policies are
identical within each A/B pair and their UTF-16 SHA-256 hashes are recorded.

| Workload | Content | Warmup pairs per mode | Rounds | Calls per batch/version |
| --- | --- | ---: | ---: | ---: |
| `no-secrets` | The R1 minimal fixture, two required sections | 10,000 | 12 | 2,000 |
| `secret-rich-sections` | All assignment spellings, bearer text, memory, contradictions, research and skills | 1,000 | 12 | 200 |

Order alternates A/B and B/A in warmup and measured rounds. Each operation calls
the actual compiler. A volatile reference consumes results; equality/hash
checks occur outside measured loops. Measurements record wall nanoseconds,
current-thread CPU nanoseconds and actual allocated bytes through HotSpot
`ThreadMXBean`. No forced GC, discarded samples or timing gate is used.

After the hot matrix, exactly **three fresh-JVM pairs** separately measure the
first compiler constructor plus first `compile`, including candidate companion
initialization. Fixture/counter setup is outside that interval. Pair order
alternates. Cold output hashes must match. This tiny cold control is exploratory
startup-cost evidence, not a statistically established startup improvement or
whole-app startup benchmark.

One primary run was executed, with no diagnostic follow-up. R1 evidence and
source files remain unchanged. Full Gradle/lint/APK, ART/Fold7, thermal,
battery and model-output evaluations remain separate unexecuted gates.
