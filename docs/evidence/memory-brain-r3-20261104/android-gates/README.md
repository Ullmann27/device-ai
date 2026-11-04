# R3 Android gates — exact results and raw receipts

Tested local commit: `ec2543bef908d7d346a9767f9b095e84254090d1`.
Published source-equivalent commit: `3704b62585c6ce6881ab0dfe0c9947cddd1531c2`.
Full tree: `ed4984ba9ac873e081a66d7a7be871f029ef5be5`.
App subtree: `befaef97b94b38ebeba65c0160e332d747dafd7a`.

Both executed commands started from that clean identity. Their full source
manifests before and after are identical. Later evidence-only commits do not
retroactively become the executed Git commit.

| Run | Result | Tests / lint |
| --- | --- | --- |
| Space preflight | NOT_RUN, wrapper exit 75 | 0 free bytes at this attempt; no Gradle process started |
| Full Android unit suite, with lint requested | FAIL, Gradle exit 1 | 2,638 total; 2,618 PASS, 1 FAIL, 19 SKIP; 467 suites; lint not reached |
| Actual attached Activity + lint | PASS, Gradle exit 0 | 2/2 PASS; NewApi-only lint 0 issues |

Production and test compilation completed. All 14 new R3 tests pass in the
full Android run: 4 contradiction regressions, 6 independently frozen holdouts
and 4 mapper regressions. They overlap with the separately recorded focused
23/23 and 4/4 runs and must not be added to the 2,638 total.

The single full-suite failure is
`ContinuumLandscapeCockpitLayoutTest.everythingFitsOneScreenWithUsableTargetsAndNoOverlap`:
`832.0 x 700.0 font 1.0 VIEW_AVATAR ≥ 48 dp`. The original XML, stack and
assertion remain unchanged. The failure is handed to C1/UI; this memory patch
does not edit the layout or lower the threshold.
The subsequent [read-only geometry diagnosis](../layout-diagnostic/README.md)
identifies a Float rounding boundary: the actual computed height is
47.999996185302734375f, one representable Float below 48. It reproduces the
original JUnit failure and verifies the relevant source blobs against C1's
intake. It makes no physical-device hit-target claim.

## Scope and toolchain

The second command explicitly selects `at.heinz.deviceai.MemoryRecallActivityUiTest`
with `-PcontinuumUiWidgetTests=true`. It exercises the actual attached
AgentActivity, lazy compiler getter, real memory subscription and cleanup
closures, background hub, Android Handler/main looper, real animation bus and
BrainOrbAnimationRenderer/JarvisSequenceEffects. The test intentionally does not
invoke the full Activity onCreate/onDestroy or native rendering initialization.
No model dialogue, microphone, TTS, GPU, Fold7 or other physical device was run.

`:app:lintDebug` uses the repository's existing `checkOnly += setOf("NewApi")`.
The empty XML is evidence for that configured check, not for all lint rules.
The UI source set is enabled in this separate run; the full unit run uses the
default project unit-test source set. No broad UI-suite result is claimed.

The source-bound runner is `tools/memory-brain-r3/run_android_gate.py`, SHA256
`c0332b570f9ae378eadb1afadd8c4748af981b2e36e8c7677febae568a88b394`.
It uses JDK 17, Gradle 8.11.1, Android SDK 36, AGP 8.10.0, Kotlin 2.4.0,
the existing offline dependency cache, no build cache, one worker and in-process
Kotlin compilation. Test-task up-to-date skipping is explicitly disabled.
It acquires C1's two existing locks in the same order with a bounded wait and
checks source identity before and after execution. The literal commands, lock
paths, environment paths, times and exit codes are in each raw `result.json`.

## Portable evidence contents

- `summary.json`: machine-readable combined status and limits.
- `full-unit/`: original result, verbatim Gradle output copied as `gradle.txt`,
  complete 467-suite index with totals, and selected original XML for the new
  memory tests and the layout failure.
- `activity-lint/`: original result, Gradle output, original Activity XML,
  complete one-suite index and original lint XML.
- `preflight/`: the earlier no-space refusal, kept distinct from executed runs.
- `raw-receipts.tar.xz`: **all 480 raw files**, including all 467 full-run XML
  files, the separate Activity XML, both complete before/after source manifests,
  original Gradle logs, init scripts, result records and lint XML.
- `raw-receipts-manifest.json`: archive SHA256 plus every member's size and
  SHA256. Every member was reopened and byte-compared after archiving.

The archive contains only the three named receipt directories. No test output
was rewritten to hide the failure, skips, synthetic-model limits or mock
benchmarks. Original trailing whitespace in the two Gradle outputs and the
diagnostic JUnit log is preserved; those three verbatim log paths are excluded
from the evidence commit's whitespace-formatting check. No APK was requested
or built.
