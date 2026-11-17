# Completed connections: LM1 sampler (#511 R1)

Status: `LOCAL_JVM_VERIFIED / NOT_PUSHED`. This isolated contribution is for the
coordinator's draft #523 review; no Android/device or full-app acceptance is claimed.

## Frozen source and exact scope

- C1 contribution base: `3510c26110098b42d46bb3b7788027fa7aed59e4`.
- Source #511 published commit: `73b64ec299fd314dfdf07835fc7d8fc0d74b51a2`.
- Source local equivalent: `2f20c19b7e37197a6accac0c226b5401e25f1f99`.
- The coordinator verified their shared complete tree:
  `f6297e08d03eae74283f43b1ce5abf80c5f7e666` and separately checked the remote
  `LmModel.kt` blob. Both imported files were copied unchanged from the frozen local commit.
- `app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt`:
  Git blob `f583da534acc9e0e71ee0ead39256243a0e2894f`.
- `app/src/test/java/at/heinz/deviceai/learning/lm/LmSamplingTest.kt`:
  Git blob `58505c9dfef9ef167f1dbb4bf52c0b0778218190`.

The production diff is confined to `LmEval.sample`: nine insertions and one
removed line. At `temperature <= 0`, a scan retains the first maximum using
`Float.compareTo`; `topK.coerceIn` still validates even empty input. Positive and
NaN temperature retain the existing sorted weighted sampling path. The imported
five-test class checks selection, exceptions/messages, input bits and subsequent
RNG state. R2 weight candidates and R2 tests were not imported.

## Actual connections and limits

`LmRuntime.generate` already calls `LmEval.sample` in `LmRuntime.kt:61`.
Its request defaults are temperature 0 and top-k 1. `LmEval.generate` calls the
same sampler in `LmModel.kt:277`. A tracked-source search at the C1 base for
`LmRuntime` under `app/src/main/java/at/heinz/deviceai` found references only
inside `LmRuntime.kt`, with no external production loader/caller in that search.
This contribution adds no new call path or model activation. LM1 is separate from
the active Gemma/LiteRT chat; these checks establish no chat-speed or quality gain.

No timing benchmarks, model training, APK build/sign/install, model promotion,
worker/security/Activity changes or main merge were performed. The retained R1
change's historical measurements and stochastic-mode allocation caveat remain in
#511's `THREE-TUNING-BOTS-HANDOFF-20261104.md`; they were not repeated here.

## Fresh verification

The actual working source was compiled using cached Kotlin 2.4.0 and JDK 21.0.13,
JVM target 17, then run using JUnit 4.13.2. Only `LmTensor.kt`,
`LmBpeTokenizer.kt`, `LmModel.kt` and `LmSamplingTest.kt` were compiled, with no
Android shims. Compiler heap was bounded at 384 MiB and test heap at 256 MiB.

| Check | Actual result |
| --- | --- |
| Kotlin compilation | Exit 0, empty stdout/stderr |
| `org.junit.runner.JUnitCore at.heinz.deviceai.learning.lm.LmSamplingTest` | Exit 0, `OK (5 tests)`, empty stderr |
| Frozen test sampler versus C1 base sampler | Exact function text equality |
| Imported source/test versus frozen #511 blobs | Exact equality |
| `git diff --cached --check` | Exit 0 after raw-output encoding |
| Full Android/Gradle/device acceptance | NOT_RUN by this implementer; coordinator owns combined validation |

The five existing methods include 35,624 looped sampler parity cases, four direct
edge assertions and 50 generated-token comparisons across tiny Transformer and
minGRU models with Unicode prompts. These counts come from the unchanged test
loops; they are not additional JUnit methods or performance measurements.

`verification.json` records exact command argument arrays, tool versions, source
SHA-256 hashes, source Git blobs, exit codes and output digests. `junit.json` stores the
unaltered JUnit stdout as a JSON string. The first staged whitespace check exited
2 because the raw JUnit text ends in a blank line; encoding those exact bytes in
JSON resolved the whitespace diagnostic without editing the captured output. The test jar remains a disposable local output outside the
repository. For reproduction, run the recorded compile and test argument arrays
from the repository root after mapping the cache/output paths to an installed
local toolchain; no downloads are part of these commands.

## Handoff and rollback

The coordinator should review/cherry-pick this single contribution, publish it
through #523, and perform the combined gates on the resulting tree. No remote
state was changed by this implementer. To roll back, revert this contribution
commit; no data or model migration is involved. Shared entrypoints and other
implementers' files remain outside this slice.
