# Frozen contrast pilot: training audit

Source snapshot: PR #728 HEAD `46816f6a232b1b9f5ecf6ce95af8a977af305da8`.
Audit performed on the surviving local files; coordinator verifies remote bindings.

## Execution readiness

All 15 registered file SHA-256 hashes match. Registration SHA-256:
`349099d814832bb384c103afde8cdba59ee2ffe310eb1c157af009c620d1bdf6`.
Neither registered replay output nor replay evidence directory exists. No active
LmContrastPilot process was observed. The unmodified runner's replay
`--check-only` returned PASS with zero updates. No model execution or update
was performed by this reviewer.

The original command can run unchanged. Java heap is bounded to 768 MiB;
one processor; 128 updates; native training-loop deadline 600 seconds;
external entire-process timeout 660 seconds. Answer arm took 8.97 seconds,
but that is an observed previous duration, not a replay prediction.

## Mathematical and implementation checks

- Answer target positions are correct: last prefix token predicts label;
  label token predicts EOS; other prompt positions are masked.
- Four examples each have two targets. Divisor 8 implements the mean
  answer/EOS loss over the batch.
- Replay divisor is ten times both text sequences' total target count,
  implementing 0.1 times their pooled mean next-token loss, including EOS.
- Separate tapes share gradient buffers within an update; buffers clear
  once per update, so answer and replay contributions accumulate.
- Each arm reloads original weights and creates fresh optimizer moments.
  Replay samples do not consume RNG, preserving the exact answer schedule.
- Greedy exact match uses unrestricted vocabulary and requires label plus
  EOS. Four-choice accuracy is a distinct, weaker secondary diagnostic.
- Final checkpoint is written before final evaluation, with no update
  during evaluation. All registered input hashes are checked again.

No execution-blocking defect found in this bounded protocol. This is a
source review, not an exhaustive autodiff proof or production certification.
The historical optimizer's epsilon convention is inherited unchanged;
changing it here would invalidate the already registered comparison.

## Interpretation safeguards

Existing answer arm fails the generation-preservation criterion: two of four
baseline probes reach 32 tokens; zero final probes do. Baseline itself fails
the required four-of-four capacity gate. Open/search exact match is zero both
before and after, which is a floor effect: restricted classification drops
from 32/64 to 0/64 and answer NLL worsens from 3.8890 to 5.9668. Reporting only
unchanged exact retention would conceal this degradation.

Final answer-arm DEV exact match is 38/64 versus 0/64. This is open diagnostic
DEV, not independent language generalization. Both fixed arms must be retained
and neither promoted. Complete the frozen replay without tuning to these
observed results, then compare both arms with all retention and capacity rows.
