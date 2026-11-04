# Avatar connection evidence — 2026-11-04

The actual connection tests pass. The final selected run has **136 tests across
23 classes, zero failures/errors/skips**, and the configured **NewApi lint gate
has zero findings**. These are local host results; native graphics, real-device
execution and audible TTS remain untested.

The tested local commit is `17d9704cd3f5f3c774a7053b5aca03624ca6cde9`.
The published equivalent is `32466a2559721762a9b6745890ea286d1c2730c5`.
Their complete file tree is the same: `5394ce13bf5c3b692f58aa284d9f8a89e6be882e`.
Production app sources remain identical to #491 input `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
The logs report the commit that actually executed. Documentation-only commits
that package these receipts do not change that source result.

See the [full handoff](../../research/omega/AVATAR-CONNECTION-HANDOFF.md) for the
caller path, test boundary, clock-fixture correction, rollback and asset limits.

## Receipts

| Path | Meaning |
| --- | --- |
| `final-connected-run/` | Final 23-suite XML, full command/log, NewApi reports and exact counts |
| `clock-fixed-targeted-run/` | The three corrected callback tests, all passing |
| `disconnected-negative-control/` | Intentionally removed one PCM delivery line; one expected mouth-opening assertion failure, with restored-source proof |
| `initial-callback-run/` | Original 136-case run: 133 passes and 3 new fixture failures |
| `clock-diagnostic-run/` and `clock-diagnostic.patch` | Measured mixed real/virtual clocks and temporary diagnostic source diff |
| `fresh-compilation/` | Fresh compilation of integrated app/test sources, before the new callback fixture was added |
| `source-publication-map.json` | Exact local/public tree equality and publication method |
| `source-identity.json` | Original three production fixes and three regression files already present in #491 |
| `test-classes.json` | The exact 23 test filters; logs contain the actual Gradle argument list |
| `tts-test-review.md` | Independent static review, including the initially missed fixture clock defect |
| `live-wiring-review.md` | Actual caller/sink analysis supporting the existing direct binding |
| `validation-context.md` | Earlier toolchain/failed-fixture/asset context, explicitly time-scoped |
| `asset-check/` | Unchanged GLB bytes, known old pivot-window failure and eight checker self-tests |
| `toolchain/` | SDK/pin setup receipt and archived task-local launcher |

The negative test is expected to be red: only the `avatarDirector.audioLevel`
call was removed in an unpublished isolated worktree. It failed specifically at
`assertTrue(speaking.jawOpen > 0f)` after successful compilation. The test fixture
was byte-identical to the positive run. The worktree was subsequently restored
to the exact positive file tree; the delivery source was never mutated.

The initial and diagnostic red receipts are preserved so the test-only clock
fix is reviewable. They are not the final delivery result. The existing GLB
validator failure is separate and remains unresolved; these receipts do not
claim native rendering quality or device acceptance.

`SHA256SUMS` hashes every evidence file except itself. The archived launcher
expects a sibling SDK/JDK/Gradle/cache layout; it records how this run was made
and is not a new application workflow. The repository's normal Gradle setup can
run the same tasks and explicit class filters shown in the raw logs.
