# Personal voice independent evaluation — 11 November 2026

Status: bounded round complete. Both weight candidates REJECTED; native context
adapter shows limited rule/confirmed-review benefit. No production integration.
Research base PR #728 / `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.
Bot 3 owns only `experimental/personal-voice-20261111/evaluation/` and this handoff.

The current owner instruction authorizes one isolated personal-voice round with
exactly three helpers, at most two native LM1 candidates, public synthetic text
and no production integration. Existing AGENTS/experiment contracts were read;
EXP-05/EXP-12 apply, Fold7/ASR/device acceptance remains separate.

Initial source evidence confirmed the three historical candidates failed gates,
labels 0/1/2/3, original LM1 and tokenizer hashes. Protocol and independent 64-case
TEST were frozen before results in remote `9deac1792e79736167c5169c916941d45c8f9a96`
(PR #755). No TRAIN/DEV examples were inspected before that freeze. After both
candidate hashes and tests completed, automated overlap/sample-schedule audits
read training data. The shared-worktree access rule is a process control, not an
encryption claim. No private transcripts or owner recordings were collected.

## Actual results

- Original/replay/preservation: exact 0/17/17 of 64; restricted 16/17/17 of 64;
  complete opposing pairs 0/1/1 of 32; identical-wordbag pairs all 0/17.
- Restricted historical open/search 32/18/25 of 64; continuation lengths
  7,4,32,32 -> 0,4,2,1 / 6,4,3,1. Both candidates fail retention and output-length
  gates. Each produces 16 new valid-but-forbidden free-output action labels;
  these are prediction proxies, no actual device action.
- Independent tensor auditor: all 132,160 numerical parameters changed per arm;
  exact original architecture/tokenizer. All 128 sampling schedules reconstructed
  per arm; no TEST exact/wordbag overlap with TRAIN, DEV or supervised replay.
- Raw results and weight report published in
  `7473f01ab6fe1b14c72e436c5d399f15c31c7fdb` before component completion.
- Frozen native context suite: A 8/24 -> B 22/24; automatic subset 4/10 -> 8/10;
  three explicit human repairs and seven integrity checks are separate benefits.
  Two paraphrase/Austrian cases remain unsupported, no baseline pass regresses.
  All 96 advisory-label invariance probes hold; no tool grants/training starts.
- Native component compile uses 40 real repository sources and official jars,
  including Android factory compile-only. Initial build/dependency failures and
  one source-change guard refusal are retained; final immutable snapshot run exit
  0, wall 0.5164 s, user/system CPU 0.6492/0.1245 s, peak RSS 52,088 KiB.
- 12 evidence-validation/gate tests passed. Independent context and training
  audits reproduce their captured JSON exactly; frozen input hashes unchanged.

## Reproduction and handoff

Read `evaluation/WEIGHT-RESULT.md`, `CONTEXT-RESULT.md`, and raw receipts under
`results-v1/` and `component-results-v1/`. `evaluate.py`, `audit_training.py`,
`run_context.py`, and `analyze_context.py` expose `--help` and fresh output paths.
All are under `experimental/personal-voice-20261111/` and use existing native
LM1/context APIs. Source/compiler/jar/weight/data hashes are bound in receipts.

No further candidate or tuning round is authorized by this completed experiment.
The consumed cases must not be reused as unknown generalization evidence.
Future work should investigate semantic learning/objective and generation
preservation, not merely count more updates. The larger-model path needs actual
trainable architecture/format/hardware and an independent owner-approved dataset.

Full application A/B/C, true WER/CER, natural spoken responses, Android Keystore
runtime, phone temperature/energy and physical Fold7 remain NOT_RUN. Never infer
those outcomes from the synthetic native component tests.

Rollback: omit isolated evaluation directory. No app, model selection, checkpoint,
Gemma, main, signing, release or existing evidence was changed.
