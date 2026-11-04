# Three-helper avatar continuation evidence — 2026-11-04

**Final selected tests: 168 cases in 28 classes, zero failures/errors/skips.
Configured NewApi analysis: 0 findings, exit 0 in the separate final run.
Current asset: separate hinge/fixed-region V2 PASS, with 16 passing adversarial
self-tests. Actual old V1 remains FAIL and is retained as a distinct result.**

The actually executed final local source is
`f5dc6e08acb88b30273410b8efcc7e0db446020b`; the published equivalent is
`df3caae21c1729de2b73a688241d3d53ef3783d4`. Their complete tree is identical:
`58dc99f0145272775e0a2554d7d8a4ccc7d6be4a`. This evidence/handoff packaging
does not change the tested app or test sources.

The [full handoff](../../research/omega/AVATAR-RIG-CONTINUATION-HANDOFF.md)
explains the production behavior, existing #502/#503 source reuse, three actual
helper contributions, independent review, integration boundaries and rollback.
The prior 136-case checkpoint remains historical and source-bound.

## What the receipts establish

The new installed-listener cases first reproduced duplicate-start and backward-
audio-marker anchor rewinds. The repaired Activity/Director boundary accepts a
callback before anchoring PCM and forwards the actual range frame. Valid numeric
ranges keep timing without invented letter visemes. Actual interrupted-stop and
successor protection are covered through the installed listener.

Reviewed existing motion supplies immediate PP closure, idempotent repeated render
time, gaze fixations and double blinks. A separately reproduced negative-zero
binary-search crash is repaired. Nine idle regressions and retained generic
AA-to-SS jaw-transition cases accompany the integration.

The first combined run exposed one obsolete PP/time assumption in a cross-mapper
test. The corrected test preserves every viseme, checks the complete weight array
and inner jaw, and adds an actual source-to-pose-to-mapper case. The visible app
path already calls the mapper directly; the unused alternate source/pose bridge
is not presented as a new live connection.

## Receipt index

| Path | Meaning |
| --- | --- |
| `validation-summary.json` | Machine-readable final selected-gate status and explicit run boundaries |
| `source-publication-map.json` | Local/public commit and full-tree mapping for every source checkpoint |
| `source-identity.json` | Sixteen changed app/test file hashes, five changed production files, unchanged rig/checker inputs |
| `upstream-inputs.json` | Pinned PR inputs and exact upstream file blobs |
| `test-classes.json` | Exact 28 Gradle test filters |
| `callback-baseline/` | Six listener cases before repair: two observed assertion failures, exit 1 |
| `callback-connected/` | The 33-case intermediate speech subset, all passing, exit 0 |
| `callback-repair.md`, `callback-acceptance.patch` | Cause, exact independently reviewed correction and fixture boundary |
| `combined-initial/` | First 167-case integration: one old PP comparison failure, exit 1 |
| `combined-final/` | All 168 final XML cases pass; overall process later exits 1 when its lint daemon disappears |
| `lint-retry-1536m/` | Second incomplete lint attempt; daemon disappears, cause unconfirmed |
| `lint-final/` | Successful bounded-JVM lint, raw log/GC log, zero-issue reports and task-outcome metadata |
| `lint-local-review.md` | Local AGP 8.10.0 in-process/heap-property inspection, without an invented crash diagnosis |
| `signed-zero/` | Original/fixed Kotlin probe, exact source hashes, six-file patch and motion review |
| `lipsync-contract/` | One-file test repair, original red XML and immutable-source live-caller analysis |
| `independent-review/` | Separate callback review, actual V1 failure and eight passing V1 self-tests |
| `rig-helper/` | First fresh current-asset V1/V2 receipts and exact helper/integrated input equality |
| `hinge-v2-final/` | Coordinator's current-asset V2 command, 16 adversarial cases and exact inputs |
| `rig-validation.md` | Why actual V1 FAIL and separate V2 PASS coexist; measured hinge/neutral conditions |
| `agent-contributions.json` | The three completed helper scopes and handoff identities |

## Test success and the later lint process failures

The final combined command completed all selected test suites and advanced into
lint analysis. Its daemon then disappeared, so its overall exit code is **1**.
The passing 168-case XML result is not mislabeled as a wholly successful combined
build. A standalone 1536 MiB attempt failed similarly.

The final separate command succeeded on the same code with heap/metaspace/codecache
limits, two active processors, explicit OOM exit and GC logging. All three lint
analysis tasks executed. The report task reused the unchanged zero-issue reports
as UP-TO-DATE; this package does not claim those report files were freshly
regenerated. The project's configured rule is NewApi, not every lint rule.
No rule, source or gate was disabled. There is no confirmed cause for the two
earlier daemon exits; success with bounded settings is not retrospective proof
of OOM.

The standalone signed-zero probe also distinguishes process and case results.
It catches exceptions: the original process exits 0 while recording three
ArrayIndexOutOfBoundsException cases. The corrected nine inputs record no case
exceptions. The JUnit regression is separately included in the 168-case run.

## Asset and execution boundary

The GLB remains SHA-256
`5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
The existing original `f39a89fc...` file satisfies V2's pinned input; no synthetic
replacement or threshold relaxation was needed. Actual V1 exits early on its
fixed jaw/eye windows. Existing V2 checks the real hinge and preserved neutral
child/control transforms, then runs unchanged V1 on a labeled diagnostic
coordinate view. Its PASS is distinct from the actual V1 FAIL.

The verified UI boundary is synthetic PCM/accepted-request callbacks through the
real installed Activity listener and scheduled frame to the visible view reducer
under Robolectric LEGACY host graphics. Device execution, native GPU pixels,
audible TTS, complete synthesis permission handling, pose intersections and
reference likeness remain outside these receipts.

`SHA256SUMS` hashes every file in this evidence directory except itself. Raw
failures and static-review limits are intentionally retained. Logs identify the
actual local source; immutable public equivalents are recorded separately.
