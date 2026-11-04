# AiContextCompiler byte accounting: reproducible host comparison

The production change only maintains the number of UTF-8 bytes in accepted
sections. Every later candidate begins with ASCII newlines and `## `, so an
unpaired surrogate at the end of a previous section cannot pair with a character
in a later candidate. Adding the existing candidate byte counts is therefore
equivalent to encoding the entire accumulated buffer again. Rejected and blank
sections do not change the counter. Accepted bytes remain within the existing
maximum of 65,536, so the counter cannot overflow.

No redaction expression, ranking rule, selected section, memory ID, research
source, policy, budget or exception is intentionally changed. This comparison
does not infer model quality or Android/Fold7 speed from host measurements.

## Reproduce without Gradle or downloads

Run from the repository containing the baseline commit
`e101e393dd6c80803ac9d9ac91fccd9812c4a18d`:

```bash
export CONTINUUM_KOTLIN_COMPILER_CP='classpath of the existing Kotlin compiler and its dependencies'
export CONTINUUM_KOTLIN_DEPENDENCY_CP='classpath of the existing Kotlin stdlib, JUnit4, Hamcrest, annotations, Android SDK android.jar, LiteRT-LM classes.jar, OkHttp and Okio'
python3 tools/tuning-memory-20261104/run.py check /absolute/external/output
python3 tools/tuning-memory-20261104/run.py benchmark /absolute/external/output
```

The environment used for this comparison has Java 21, Kotlin 2.4.0, JUnit 4.13.2,
Hamcrest 1.3, Android SDK 36, LiteRT-LM 0.18.0, OkHttp 4.12.0 and Okio 3.6.0.
The LiteRT-LM `classes.jar` is extracted from the existing cached AAR. The runner
does not fetch dependencies, launch Gradle, access the network, invoke Android
framework methods, execute a model or generate an APK. The manifest records
the full actual classpaths and SHA-256 hashes.

## Correctness gate

`sources.txt` selects complete real production/test source files; it provides no
substitute implementations. The original compiler class comes directly from
`git show` at the baseline, with only its class name changed. Its unchanged
policy/input/output declarations are shared with the candidate after a literal
equality check. The four JUnit suites run against each implementation: compiler,
new byte-boundary tests, existing context integrity, and prompt size/stability.

The direct comparison checks complete `CompiledAiContext` equality (including
the exact UTF-16 string, ordered memory IDs, ordered research URLs and
`truncated`), encoded UTF-8 byte equality, budget compliance, exception type and
message, and clock callback counts. Its deterministic cases cover ASCII,
multibyte characters, combining marks, NUL, valid surrogate pairs, unpaired
surrogates within and across section boundaries, secret redaction, exact and
one-byte budget boundaries, blank/omitted/required sections, duplicate records,
privacy/validation states, source limits, both tool catalogs and a private bot
frame. Extra selection cases use fixed seed `0x434F4E54455854`.

## Measurement protocol frozen before execution

Four fixed full-compiler workloads are defined in `benchmarkCases()`:

| Workload | Purpose |
| --- | --- |
| `minimal-control` | Two required sections, no optional content |
| `mixed-ascii-16k` | Mixed sections under the default byte budget |
| `mixed-unicode-64k` | Larger multilingual sections under the maximum budget |
| `omitted-memory-8k` | Oversized memory group followed by smaller optional sections |

Each workload first passes exact original/candidate parity. Both implementations
receive the same immutable input and policy. Each has 600 warmup executions,
paired in alternating order, followed by 12 paired rounds of 160 executions per
implementation. Measured order alternates A/B and B/A. Results are consumed via
a volatile reference; no hashing or comparison occurs inside the timed loop.
The complete result is checked again after each paired round.

`System.nanoTime()` records host wall time. HotSpot `ThreadMXBean` records bytes
allocated by the measuring thread, rather than approximate heap occupancy.
No forced GC or timing-based acceptance threshold is used. Every raw sample is
retained. Report median timing, paired relative differences and allocation
changes separately; noisy wall time is not a Fold7 performance claim. The runner
checks all source/dependency hashes against the correctness manifest before
measuring, and records host load before and after.

Full Android/Gradle/lint, ART allocation behavior, actual device latency,
thermal/battery impact and model-answer evaluations remain separate checks.

## One bounded control follow-up

The primary run had conflicting minimal-control wall-time summaries: the ratio
of separate medians was worse, while the median paired difference was better.
The original raw samples are retained without exclusion. One separately
approved diagnostic therefore repeated **only the identical minimal input and
unchanged production code**, with 10,000 warmup pairs and 20 alternating rounds
of 2,000 calls. It also measured current-thread CPU time to reduce dependence on
host scheduling. No additional measurement loop followed this diagnostic.

```bash
python3 tools/tuning-memory-20261104/run.py control /absolute/external/output
```

This compiles the separate `ContextCompilerControlCheck.kt` against the already
checked real classes. `control-manifest.json` records its source hash and the
unchanged production hashes; `control.json` retains every sample. The original
four workloads remain the primary report. See the versioned evidence and
handoff for the actual results and limitations.
