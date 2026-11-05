# Independent coordinator verification — 2026-11-04

**Result: functional checks passed on the combined tuning source.** Performance findings remain bounded by the component reports; this is not an Android, release or frontier-quality acceptance.

## Exact source

- Frozen baseline: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- Tested local commit: `9c111c3dac8f655eb82071289c8294689b909e2d`.
- Equivalent published commit: `db88716f899953c0bdf0fdf8430972fd6addac0d`.
- Both complete Git trees: `5d59380abc5339e2232fd84ddc3a5cc20eb0f12a`.
- The subsequent completion commit changes only reports and evidence. Production hashes are recorded in [verification.json](verification.json).

Each published intermediate tree was compared with its corresponding local tree before creating the remote commit. The three final production files match their benchmark manifests. All three original production hashes were checked against `git show` of the frozen base. The three archived LM raw-run hashes match their manifest, and the archived summary validator exited 0. All 46 source entries in both the original context manifest and the coordinator manifest matched their current files; these include generated baseline-comparison sources in addition to the repository graph.

## Independently executed checks

| Check | Result | Actual output |
| --- | ---: | --- |
| Two Node teamleader/backlog suites | 52 passed, 0 failed, 0 skipped | [worker-tests.txt](worker-tests.txt) |
| Node baseline/candidate parity | 40,960 passed | [worker-parity.json](worker-parity.json) |
| Candidate context JUnit suites | 21 passed | [context-candidate-tests.txt](context-candidate-tests.txt) |
| Same context suites with original compiler | 21 passed | [context-baseline-tests.txt](context-baseline-tests.txt) |
| Direct context parity | 462 passed: 408 contexts, 54 matching exceptions | [context-parity.json](context-parity.json) |
| LM sampler JUnit suite | 5 passed | [lm-tests.txt](lm-tests.txt) |

There are **78 unique candidate test methods**. The additional 21 original-compiler executions are not counted as new candidate tests. The five LM methods contain 35,624 sampler comparisons and 50 actual tiny-model token comparisons; these are embedded assertions, not additional JUnit methods.

The context runner compiled the real source graph, without introducing dependency stubs for this task. Kotlin/JVM toolchains and existing dependency jars were used from the workspace. This does not execute Android framework code. Nonfatal harness deprecation warnings and one shared JVM perfdata-lock warning are preserved or described in the receipt; all check commands exited 0.

The exact coordinator commands, source receipts and compiler command are retained in this directory. Reproduction instructions are in [the context harness](../../../../tools/tuning-memory-20261104/README.md), [the LM report](../../tuning-inference-20261104/RESULTS.md) and [the worker report](../workers/README.md). The coordinator did not run another timing experiment.

## Static review and decision

The runtime/worker bot independently reviewed the two Kotlin diffs and their tests. Within that narrow static scope it found no confirmed blocker:

- The sampler's strict `Float.compareTo > 0` preserves the first maximum and stable Float ordering, including NaN and signed zero. Empty-input validation and RNG usage remain consistent with the original tested contract. The positive-temperature expressions remain unchanged; their measured extra allocation is still a real reported tradeoff.
- The context byte counter starts at zero on every compile and advances only after a successful append. Rejected sections do not consume the budget. Redaction precedes byte sizing; ASCII separators prevent surrogate pairing across append boundaries. Accepted-section metadata and existing limits remain intact.

The coordinator reviewed all three production diffs and the benchmark methods. The worker selector's equivalence is restricted to valid canonical backlog objects; side-effectful getters and sparse arrays are not silently added to the contract. Original eligibility/filter order, ordering rules and object identity remain covered by the executed tests. Dependency lookup work and worker authority are unchanged.

**Decision:** retain the three changes as separately reviewable draft commits. For the LM sampler this explicitly accepts the documented +24-byte stochastic-control cost for the isolated draft, while preserving the strong deterministic-path reduction. This does not accept an unmeasured on-device or whole-application performance claim. Memory allocation reduction is accepted as the measured result; a general context-latency benefit is not claimed. Strongly filtered worker inputs have no proven general speedup.

## Compatibility dry run

The exact `AiContextCompiler.kt` blob from PR #506 at `8abea828833d8833d40e3d3d36feaa6d3685b82e` was fetched and verified as `f42f526cefaa96295205f161241fbc8035f27c01`. Running `git apply --check context.patch` on that temporary file exited 0. [context-pr506.patch](context-pr506.patch) is byte-identical to this tuning branch's production context delta.

This is only a conflict-free patch-application check. PR #506 was not changed, integrated, compiled or accepted by this check.

## Evidence integrity and limits

Copied logs and source receipts are byte-preserved; their SHA-256 hashes appear in [verification.json](verification.json). Raw JUnit outputs intentionally retain their final blank lines, and archived patch files retain required context-line spaces. The source/document/harness whitespace check excludes those raw `.txt`/`.patch` artifacts; a global `git diff --check` can report their preserved whitespace. Do not rewrite raw observations merely to make that global diagnostic empty.

No full Gradle/Android build, Lint, APK, device run, end-to-end model benchmark, battery/thermal measurement or frontier-model comparison was performed. No main merge, production release, model promotion or permanent background worker is implied.
