# Tuning bot 2, round 2: reuse existing redaction patterns

**RETURNED_TO_R1 / CANDIDATE_NOT_ADOPTED.** The candidate lowers hot allocation,
but the secret-rich workloads show substantially higher thread CPU cost. Root
declined adoption. The active compiler has been restored byte-for-byte to R1,
including the R1 byte counter. The exact candidate, narrow patch, tests and all
measurements are archived. One primary measurement was executed; no follow-up
measurement or alternative variant was run.

## Baseline and scope

Published R1 baseline: `04b46f125d1086e6ea540d3c68700407f340f607` (PR #511).
This run resolved the local equivalent `db3295d51c769afdaf809ac1dfae2ada84a3274f`
and verified tree `d753188fe0e67f6c6f098db6b7073ea199e05530` before compiling.
The runner prefers the published commit and does not require the local-only
commit on a fresh GitHub checkout. Exact resolution attempts are recorded.

Baseline compiler SHA-256:
`2100c6d1b4ecd0e2398ef4039266d9456cc33a69b4ea3700b5e4b79b9c7a5a13`.
Candidate compiler SHA-256:
`cc95d26e60fe464223db1db171234e2d9eca0c215e94a9069e1a14d00fd9d2d0`.

The measured candidate changed only
`app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt`: the two
existing regex literals became private class-level `val` patterns.
`redactSecrets` reused them in the original order,
with identical transforms and replacement strings. R1 byte accounting remains
unchanged. No input/result/matcher cache, new secret rule, retriever change,
recall integration, repair, schema change or model promotion is introduced.

In that candidate, two compiled patterns remain reachable with the compiler class. They retain
only the fixed pattern definitions, not user input. This is a bounded lifecycle
tradeoff; retained heap and peak RAM are not measured. EXP-05 context delivery,
EXP-06 provenance and EXP-12 identity/owner-memory contracts remain unchanged.
Existing offline/ToolGate/CCC1/signing boundaries are untouched.

## Semantic and concurrency evidence

The official [Java 21 Pattern documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html)
states that compiled patterns are immutable and can be shared across threads;
match state belongs to a matcher, which must not be shared unsafely. This is a
JVM semantic reference, not an Android/Fold7 acceptance claim.

Actual Kotlin 2.4.0 bytecode has a final `Regex.nativePattern` and creates a
matcher for each relevant replace/find call. Candidate bytecode contains two
private static final `Regex` fields, initialized in the class initializer;
`redactSecrets` reads them and invokes the same replace overloads. Excerpts are
saved with the source/dependency manifests. The `javap` executable was absent
from PATH; the installed JDK's `jdk.jdeps/com.sun.tools.javap.Main` provided the
real disassembly without downloads.

### Concrete existing issue for the repair team

`assignmentReplacementStillPrecedesBearerReplacement` passes on both R1 and the
candidate: input `authorization=Bearer abcdefgh` becomes
`authorization=[REDACTED] abcdefgh`. The first assignment rule consumes only
`Bearer`, leaving the following text present. This is an existing R1 limitation
and remains unchanged in the candidate. **Complete removal of all secret formats
is not established.** A separately scoped repair should address the recognition
rules and their regression tests. This tuning round does not expand the rules,
change another branch or create an external message/ticket.

## Executed correctness checks

Compilation: **PASS**, Kotlin 2.4.0, OpenJDK 21.0.13, JVM target 17. The unchanged
R1 real source graph, the new test class and isolated harnesses are compiled
against cached actual dependencies; no substitute implementation is supplied.

- **27/27 JUnit PASS against R1** and **27/27 against the R2 candidate**.
- These retain the original 21 compiler/byte-budget/integrity/prompt tests and
  add six redaction tests: exact replacements/nonmatches, rule order,
  Unicode/unpaired surrogates, reused/new instances and failure recovery,
  96 parallel distinct inputs, and whole-section budget decisions.
- **462/462 unchanged R1 parity cases PASS**: 408 complete contexts, including
  294 truncated results, plus 54 identical exceptions.
- Complete UTF-16 text, encoded UTF-8 bytes, ordered memory/source IDs,
  truncation, exception class/message and clock counts remain identical.
- **128/128 additional lifecycle comparisons PASS**, covering both measurement
  fixtures and reused/fresh-per-call compiler instances.
- Both regex literals and shared policy/input/output declarations pass a
  literal source check before compilation.

The unchanged parity outcome digest is
`7852ec89ff0c15508ed1d897b435d30d752528f04a2575817429b7be2b43eb85`.
No R1 source, harness or evidence file is overwritten by R2.

## Measured hot results and rejection decision

Each of the two fixed inputs ran with a reused compiler and with a fresh
compiler per call. The minimal input used 10,000 warmup pairs and 12 alternating
rounds of 2,000 calls/version; the secret-rich input used 1,000 warmup pairs and
12 alternating rounds of 200 calls/version. Both versions received identical
inputs and policies. Full output hashes match; result checks are outside the
timed loops. Values below are medians of per-call batch measurements.

| Workload / lifecycle | Output bytes (both) | Allocated bytes/call, R1 → candidate | Allocation reduction |
| --- | ---: | ---: | ---: |
| No secrets / reused | 451 | 14,424 → 7,160 | 7,264 B / 50.36% |
| No secrets / fresh per call | 451 | 14,448 → 7,184 | 7,264 B / 50.28% |
| Secret-rich / reused | 5,332 | 197,840 → 170,208 | 27,632 B / 13.97% |
| Secret-rich / fresh per call | 5,332 | 197,680 → 170,008 | 27,672 B / 14.00% |

All 48 paired allocation measurements are lower for the candidate. The richer
context contains the same four memory IDs and two research sources, with
`truncated=false` in both versions.

| Workload / lifecycle | Thread CPU µs, R1 → candidate | CPU change | Wall µs, R1 → candidate | Wall change |
| --- | ---: | ---: | ---: | ---: |
| No secrets / reused | 11.589 → 9.305 | -19.71% | 11.789 → 9.361 | -20.60% |
| No secrets / fresh per call | 15.975 → 12.329 | -22.82% | 16.119 → 12.902 | -19.96% |
| Secret-rich / reused | 279.007 → 392.124 | **+40.54%** | 295.782 → 393.430 | **+33.01%** |
| Secret-rich / fresh per call | 253.774 → 328.781 | **+29.56%** | 264.549 → 328.702 | **+24.25%** |

Percentages here compare the separate medians. `summary.json` also preserves
paired relative statistics and ranges. In each secret-rich lifecycle, CPU is
worse in **11/12** paired rounds. The lower allocation does not justify adopting
this general-path candidate with that measured CPU tradeoff. Its precise cause
was not independently isolated; no JIT, scheduling or cache explanation is
asserted as fact. No general statement that regex reuse is slower is made.

Host: Linux x86_64, nine visible/affinity CPUs, OpenJDK 21.0.13. Load averages
were 7.54 before and 7.26 after the full measurement. These are host-only results,
not Fold7/ART latency or model-speed evidence.

## Separate exploratory cold control

Exactly three fresh-JVM pairs measured the first constructor plus first
compile, including candidate class/companion initialization, after fixture and
counter setup. All output hashes match the same 451-byte minimal result.

| Metric | R1 median (range) | Candidate median (range) |
| --- | ---: | ---: |
| Wall time | 10.048 ms (6.573–10.218) | 8.533 ms (8.047–10.867) |
| Thread CPU time | 9.743 ms (6.531–10.240) | 8.452 ms (7.928–10.207) |
| Allocated bytes | 263,896 B (identical in all three) | 265,360 B (identical in all three) |

The first-use interval allocates **1,464 B more** for the candidate. This is
aggregate allocation during the measured interval, not retained-heap size or a
measurement of the patterns alone. Three overlapping timing samples per version
do not establish a startup speed improvement. All individual process results
and their alternating order remain in the archive.

## Prespecified measurements and reproduction

`tools/tuning-memory-r2-20261104/README.md` fixes the two workloads, lifecycle
modes, warmup, A/B order, sample counts and three fresh-JVM cold pairs before
measurement. The primary hot matrix records actual thread allocation, thread
CPU time and wall time. The separate cold interval includes the first compiler
constructor plus first compile, including candidate companion initialization,
after fixture/counter setup. Three cold pairs are exploratory start-cost
evidence, not a statistically established startup improvement.

Classpaths are supplied using the same existing
`CONTINUUM_KOTLIN_COMPILER_CP` / `CONTINUUM_KOTLIN_DEPENDENCY_CP` as R1; the
manifest records all actual jar paths and hashes.

```bash
python3 tools/tuning-memory-r2-20261104/run.py check /workspace/scratch/59ad19c6d3b7/memory-context-r2-proof
python3 tools/tuning-memory-r2-20261104/run.py measure /workspace/scratch/59ad19c6d3b7/memory-context-r2-proof
```

Versioned evidence belongs only to
`docs/evidence/three-tuning-bots-r2-20261104/memory/`. Root serializes timing
against the other bots; source and dependency hashes are checked again before
measurement. Raw samples are retained without exclusion.

## Archive binding after returning active source to R1

The measured full candidate is now
`tools/tuning-memory-r2-20261104/candidate/AiContextCompiler.kt`, with the exact
candidate hash above; `candidate.patch` contains its narrow original delta.
The patch is a zero-context unified diff, validated with
`git apply --unidiff-zero --check` against restored R1, without applying it.
The active app file has the exact baseline hash above and no production diff.
The new test class is retained.

The runner now selects the archived candidate explicitly, verifies its fixed
hash and replaces the active compiler entry in the host source list. It resolves
the baseline public-first with the original tree guard. It must not compare R1
against restored R1 and label it as the measured candidate. Use a new external
output directory when reproducing the archive route.

The original `manifest.json` is historical evidence of paths at measurement
time. `archive-binding.json` links those paths to the archived candidate and the
restored runtime source. `runner-at-measurement.py.txt` preserves the exact
executed driver's bytes/hash. Routing to the archive changes no measured Kotlin
source or fixture. After root's no-more-JVM instruction, only source/hash/syntax
checks were performed here; independent execution of the archive route belongs
to root's next check. There is no additional timing result.

## Boundaries and continuation

Full Gradle/Android/lint, APK/signing/install, ART/Fold7, real model answers,
thermal/battery and retained-heap measurements are **NOT_RUN**. Context equality
does not prove a gain in model quality or token generation. No paid service,
download, new worktree, main merge or push is performed by this child.

Root independently reviews and publishes the evidence/test archive to draft
#511. **Do not cherry-pick the archived pattern delta into production** as an
accepted optimization. Later #506/#512 context integration remains outside this
round. The active source already is R1; no data, model-weight or schema migration
or runtime rollback remains necessary.
