# Tuning bot 2: context compiler byte accounting

## Scope and source binding

- Repository: `Ullmann27/device-ai`; task branch `tuning/memory-59ad`.
- Verified base: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`, tree
  `72e62267ba22309d60ad13abb3e2a5c6a155e544` (PR #491).
- Root coordinates publication into draft PR #511. This child does not merge,
  publish a release, sign an APK or modify another worktree.
- Baseline `AiContextCompiler.kt` SHA-256:
  `60437408f411e7435825930a400b012f16decb79898d9d6743e4adfb651693c1`.
- Tested candidate source SHA-256:
  `2100c6d1b4ecd0e2398ef4039266d9456cc33a69b4ea3700b5e4b79b9c7a5a13`.

This is one behavior-preserving allocation optimization in the existing
`AiContextCompiler.compile` path. `appendSection` adds each accepted candidate's
already calculated UTF-8 byte count to a local counter, rather than converting
and encoding the entire accumulated buffer for every attempted section.

Every later candidate starts with ASCII `\n\n## `. A surrogate at the end of the
previous section therefore cannot pair with a character from the next candidate.
Per-candidate encoded sizes remain additive even for malformed UTF-16. Rejected
or blank sections spend no bytes; required sections still fail with the same
exception. The maximum accepted sum is the existing policy limit, at most
65,536 bytes. The counter is local to one compilation, with no shared cache.

## Files and preserved contracts

Production change:
`app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt` only.

New regression tests:
`app/src/test/java/at/heinz/deviceai/cognitive/AiContextByteBudgetTest.kt`.

Reproduction tools: `tools/tuning-memory-20261104/`.
Evidence: `docs/evidence/three-tuning-bots-20261104/memory/`.

The existing source graph remains `AiContextInput` → `AiContextCompiler` →
`HybridMemoryRetriever`/research sanitizer/tool catalog → `CompiledAiContext`.
Ranking, time/privacy filtering, provenance, revocation/contradiction handling,
secret expressions, section order, budgets, IDs and source URLs are unchanged.
Personal facts are neither rewritten nor promoted. No token reduction or extra
usable context capacity is claimed: output bytes and selected content match.

Related register contracts: EXP-05 (local model context), EXP-06 (source and
provenance), EXP-12 (identity/owner-memory continuity); EXP-07 supplies the
independent regression principle. No experiment is promoted, no schema or
interface changes. Existing offline switches, ToolGate, CCC1 and signer paths
are untouched.

PR #494 already optimized `HybridMemoryRetriever`, and PR #506 has separate
context/recall integration work. Neither is repeated here. PR #505's calendar
retriever repair is outside this change. Root separately checks the narrow
patch against the current integration file before later integration.

## Actual correctness checks

The selected graph contains 40 complete real production/test source files.
There are no substitute source implementations. Cached SDK/dependency classes
are used for compilation; no Android framework execution is claimed. The
original compiler class is taken directly from `git show` at the verified base
and renamed so both versions can run in one JVM. Their common policy/input/
output declarations are compared literally before sharing them.

- Kotlin 2.4.0 / OpenJDK 21.0.13, JVM target 17: compilation **PASS**.
- Baseline JUnit: **21/21 PASS**.
- Candidate JUnit: **21/21 PASS**.
- Suites: existing `AiContextCompilerTest` (4), existing
  `AiContextIntegrityTest` (9), existing `LocalPromptSizeAndStabilityTest` (2),
  new `AiContextByteBudgetTest` (6).
- Direct original/candidate comparison: **462/462 PASS**, with 408 successful
  contexts (294 marked truncated) and 54 identical exceptions.
- Compared: full data-class result, exact UTF-16 string, encoded UTF-8 bytes,
  ordered memory IDs, ordered research URLs, truncation flag, exception class
  and message, clock callback counts.
- Cases include single/multibyte characters, combining marks, NUL, valid and
  unpaired surrogates, section boundaries, all secret patterns, whole groups,
  exact and +/- one-byte budgets, later inclusion after omission, blank inputs,
  repeated compile calls, mandatory contradictions, privacy/validation states,
  duplicate records, source limits, native/full tool catalogs and private frame.

Parity outcome digest (UTF-16 code units, retaining unpaired surrogates):
`7852ec89ff0c15508ed1d897b435d30d752528f04a2575817429b7be2b43eb85`.
An early toolchain setup probe lacked transitive source files and cached
OkHttp/Okio entries; supplying the real dependencies resolved compilation.
No production repair was made in response.

## Primary measurement: all four fixed workloads

The harness/input definition was frozen before measurement. Each workload uses
600 warmup pairs, then 12 alternating A/B and B/A rounds of 160 complete compiler
calls per implementation. Both get the same immutable input and policy. A
volatile reference consumes results; parity is checked outside timed loops.
HotSpot `ThreadMXBean` measures actual per-thread allocation. `System.nanoTime`
measures host wall time. All samples are retained; none are removed.

| Workload | Output bytes (both) | Allocated bytes/call, baseline → candidate | Allocation reduction | Wall median µs, baseline → candidate | Median paired time reduction |
| --- | ---: | ---: | ---: | ---: | ---: |
| Minimal control | 451 | 15,856 → 14,912 | 944 B / 5.95% | 18.357 → 24.699 | 3.57% |
| Mixed ASCII, 16k budget | 14,235 | 845,048.1 → 723,384 | 121,664.1 B / 14.40% | 1,188.754 → 1,143.065 | -2.13% |
| Mixed Unicode, 64k budget | 56,189 | 2,288,028.7 → 1,604,999.7 | 683,029 B / 29.85% | 3,117.518 → 2,520.506 | 17.83% |
| Omitted memory, 8k budget | 5,428 | 449,544 → 410,416 | 39,128 B / 8.70% | 1,237.732 → 1,158.267 | 0.95% |

Values are medians of per-operation batch measurements; fractional allocation
bytes arise from averaging a batch. Positive reduction means lower candidate
cost; the negative paired ASCII timing means a slower paired median. Ratios of
separate timing medians and medians of paired ratios are different statistics.

The minimal control's separate wall medians are **34.55% worse** in this primary
run, while 8/12 paired rounds are faster. This conflict is reported, not hidden.
The Unicode workload is faster in 11/12 paired rounds; the ASCII and omitted
memory workloads in 6/12 each. Host load averages were 9.06 before and 8.38 after.
These data support reduced allocation across all four workloads, not a general
wall-time improvement claim.

## One bounded diagnostic of the minimal-control conflict

Root authorized exactly one follow-up, keeping production source and minimal
fixture unchanged: 10,000 warmup pairs, 20 alternating rounds of 2,000 calls,
plus per-thread CPU time. It has a separate harness, manifest and raw results.

| Metric | Baseline median | Candidate median | Reduction of medians | Candidate lower rounds |
| --- | ---: | ---: | ---: | ---: |
| Wall time | 14.429 µs | 13.462 µs | 6.70% | 13/20 |
| Thread CPU time | 12.641 µs | 12.103 µs | 4.25% | 14/20 |
| Allocated bytes/call | 15,272 B | 14,424 B | 848 B / 5.55% | 20/20 |

Full result/byte parity remains PASS, with 451 output bytes. Host load was 5.78
before and after. Warmed allocation values differ from the shorter primary run;
both are recorded. The longer control does not show a persistent small-input
regression, but timing still varies. No further measurement loop was run.

## Commands, artifacts and limitations

Toolchain classpaths are supplied through
`CONTINUUM_KOTLIN_COMPILER_CP` and `CONTINUUM_KOTLIN_DEPENDENCY_CP`; their exact
paths and SHA-256 hashes are in `manifest.json`. Dependency versions and setup
instructions are in `tools/tuning-memory-20261104/README.md`.

Executed commands:

```bash
python3 tools/tuning-memory-20261104/run.py check /workspace/scratch/59ad19c6d3b7/memory-context-proof/final
python3 tools/tuning-memory-20261104/run.py benchmark /workspace/scratch/59ad19c6d3b7/memory-context-proof/final
python3 tools/tuning-memory-20261104/run.py control /workspace/scratch/59ad19c6d3b7/memory-context-proof/final
```

Key evidence: `baseline-junit.txt`, `candidate-junit.txt`, `parity.json`,
`benchmark.json`, `summary.json`, `control.json`, `control-summary.json`, and
source/dependency manifests. Toolchain warnings in `compiler.txt` concern only
the standalone version invocation's implicit Kotlin-home lookup; the actual
compile command explicitly supplies dependencies with `-no-stdlib -no-reflect`.
The host-only measurement helper has a Java 21 deprecation warning for thread ID
access; compilation and execution passed.

**Not run:** full Gradle/Android/lint, APK, signing, Fold7/ART, real model-answer
evaluation, thermal or battery measurement. Lower host allocation does not
prove lower peak RAM, faster token generation or better reasoning. All context
quality claims are bounded to identical output and the executed regression
cases. Concurrent host load remains a limitation of wall-time samples.

Next: Root independently reviews and publishes the commit to draft #511. On the
later combined source, run the existing Android gates and repeat a small context
workload on Fold7/ART if device performance is to be claimed. No release or main
merge is authorized by this result. Rollback is the isolated commit revert;
there is no stored data, model-weight, schema or policy migration.

## Coordinator publication and independent verification

Published in [Draft PR #511](https://github.com/Ullmann27/device-ai/pull/511), through commit [`db88716`](https://github.com/Ullmann27/device-ai/commit/db88716f899953c0bdf0fdf8430972fd6addac0d). Earlier local-only statements above describe the bot handoff before publication. The coordinator then reran relevant functional checks on the combined source; see [the final three-bot handoff](THREE-TUNING-BOTS-HANDOFF-20261104.md) and [the actual coordinator evidence](../../evidence/three-tuning-bots-20261104/coordinator/README.md). All documented performance limitations remain in force.
