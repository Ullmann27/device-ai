# EXP-12 / distill-v1 independent data-flow preflight

Status: SOURCE_DATA_FLOW_PASS / DYNAMIC_EVIDENCE_PENDING / MODEL_RUN_NOT_STARTED_BY_REVIEWER.

Scope: one bounded, prospectively registered frozen-teacher candidate.
This review follows the already visible EXP-05 results. It is neither a
blind study nor an independent confirmation set. No dataset is changed.

## Existing input identities

| Input | SHA-256 |
|---|---|
| 16 replay texts | `b5fdd3c9528fe5a0f7a1dbc24db9e7169f0a00f9b31a211aff4ca8c6c97553c1` |
| 8 retention texts | `699fe7bc86b5fb9b1de9dc765b2b732b390911b30178dc8012320e6989d17019` |
| 64 known-task retention rows | `dad78e44b0bdefc006d8130d4d320a33eef245cca925671c3343e9da7e25a92a` |
| 128 answer TRAIN rows | `3666036e3b63db7738bebc391bd9058ca63bb0d1ea01b240726223c2ddc9039c` |
| 64 answer DEV rows | `057dac485d2d4d39ebcfcdc7de3fe15c9c6c4bebd2bea888b4db303949798935` |

Read-only inspection confirms 16 unique replay lines, 8 retention lines and
zero exact-line overlap. This is exact-text separation, not proof of semantic
independence. Existing contrast data remain shortcut-prone and consumed DEV.

## Mandatory source checks before execution

- Teacher cache input must derive only from `replay.txt` and original weights.
- Cache must cover all 16 replay sequences exactly once, preserving their
  positions and full-vocabulary probability arrays.
- Answer loss must use only TRAIN rows; hard replay and KL must use only the
  paired replay rows. DEV, retention rows/texts and continuation prompts must
  never enter optimizer gradients or teacher-cache construction.
- Teacher probabilities must be detached from trainable candidate state.
  Verify original checkpoint and cached probability hashes before/after.
- Fixed coefficient 1.0, 128 updates and existing schedule must be frozen
  before EXP-12 / distill-v1 execution. No checkpoint choice, early stopping or parameter
  changes may be driven by DEV, retention or continuation results.
- Save every failure and complete raw initial/final case coverage. An
  unfavorable result must not cause an unregistered replacement run.
- Parent publication must include source/JAR hashes, exact command, limits,
  all input hashes and explicit criteria before the candidate model run.

These checks can establish implementation/data flow. They cannot establish
that the design is independent of prior visible EXP-05 evidence; it is not.

## Capacity and retention interpretation

Original continuation probes reach 32 tokens only for IDs 2 and 3. IDs 0
and 1 stop after 7 and 4 tokens. The new preservation criterion must bind
those original successful IDs individually at 32 tokens, not only the total
count, to avoid hiding one regression behind improvement on another prompt.

Baseline capacity remains 2/4, so preserving both original successes cannot
be called full-capacity success. This is a prospective preservation gate,
not a reinterpretation of EXP-05's failed full-capacity requirement.

Known-task exact accuracy begins at zero. Require the newly proposed
restricted-choice retention count also to remain at least 32/64; exact
0-to-0 alone demonstrates no retained competence. Even meeting this combined
gate does not establish correct unrestricted action generation.

DEV exact gain of at least 8/64, retained-text BPB rise at most 5%, and the
above retention/preservation gates can label a favorable exploratory slice
only. Report pair-level success and compare both prior arms separately.
No result authorizes model promotion or general-language claims.

## Post-run compatibility

The existing pair diagnostic can analyze EXP-12 / distill-v1 unchanged if `INPUT` and
`CASE` schemas remain unchanged, with complete DEV steps 0 and 128. It
checks dataset/tokenizer hashes, metadata, duplicate/missing IDs and exact
label-plus-EOS outcomes. New loss diagnostics must be reported separately.

## Reviewed implementation

Reviewed `training/distill-v1/LmDistillPilot.kt` at SHA-256
`24a79ea89e2a57ae5cbb1bf6aff7a5a0c94465d3db55a8fbc4c62dd5b6f338b1` and
`LmSoftTargetKl.kt` at
`a0584ecea66b458555da0964cf24371043886de952d7059de117e62bd652d9ce`.

- Lines 142, 152 and 173 form the sole teacher-data path:
  `replay.txt` -> `replay` samples -> `teacherCache`. All sixteen lines are
  mapped once; each cached matrix covers every BOS/content input position
  and all vocabulary columns.
- Lines 168-172 decode the original checkpoint separately and reject shared
  parameter arrays. The teacher uses `Tape(training = false)` and constant
  parameter nodes. Cache matrices are newly allocated probability arrays.
- Lines 148-150 and 188-207 restrict answer gradients to the TRAIN groups.
  Lines 208-217 use the same two replay indices for hard CE and teacher KL.
  DEV/retention sample construction validates token budgets only; its
  returned samples are not retained in the optimizer training collections.
- Score/retention functions return no optimization controls. They run at
  steps 0 and 128 only. Fixed coefficient 1.0, rate schedule, iteration count
  and cyclic replay schedule have no dependency on evaluation results.
- The RNG is created after teacher inference and baseline evaluation. Each
  update draws four TRAIN indices exactly as in EXP-05. New SCHEDULE rows
  expose those IDs and replay indices without extra RNG calls.
- Lines 235-241 compare teacher-weight/cache digests before and after all
  updates. This is an implemented assertion; its successful execution is
  still pending. Original checkpoint/data hashes are also rechecked.

No data-separation or metric-driven-tuning defect was found in these source
versions. This pass does not replace compiled-source binding, KL-gradient
tests, prospective registration, or post-run hash/schedule evidence. The
reviewer performed no native inference or optimizer update.
