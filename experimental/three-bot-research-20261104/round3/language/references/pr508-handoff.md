# LM1 training recovery — 2026-11-04

Status: four diagnosis arms and intent rounds v1/v2 completed; final registered suffix comparison v3 running. The fresh final test remains unevaluated. Draft PR #508 owns this isolated work.

## Source of truth

- Parent core: PR #492, commit `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Own branch: `research/continuum-lm1-training-recovery-20261104`.
- V1 registered execution: `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`.
- V2 registered execution: `fc2eb797210bee25091caf734aacbfd694a3372e`.
- V3 and final Dev-only choice registration: `be4e49600fed1db4c697230bb8e42c150dbfbdaf`.
- Historical helper PR #497 must not replace the strengthened integrated G1 gates in #492.

All new code lives under `experimental/lm1-training-recovery-20261104/`; evidence lives under `docs/evidence/lm1-training-recovery-20261104/`. Registered v1/v2 source and inputs stayed unchanged while later variants were added in separate subdirectories.

## Completed findings

Four 16-step diagnoses used the exact same TRAIN schedule (3,255 targets each). All improve the fixed first TRAIN minibatch, but none passes the final unchanged capacity benchmark. Wide standard and final-norm-delta/8 worsen fixed development BPB. Global LR/8 improves BPB slightly without restoring the required generation workload. The implemented norm scaling is numerically verified near1/8.

The EOS behavior is context-specific. At all actual half-prefix probes EOS remains below the best other token. At the fixed capacity prompt a competing-logit rank change can make EOS win even when its absolute probability decreases. These data do not establish a global EOS collapse.

| Fresh intent round | Selected small Dev exact | Selected wide Dev exact | Final-test disposition |
|---|---:|---:|---|
| v1:128 updates, long suffix | 8/24 at128 | 10/24 at128 | NO_DEVELOPMENT_SIGNAL; no test |
| v2:512 updates, long suffix | 13/24 at256 | 13/24 at128 | Eligible; test deferred before the registered v3 comparison |
| v3:512 updates, short suffix | 13/24 at512 | Intermediate16/24 at128; run still active | No final selection or test yet |

The models learn digit-plus-EOS formatting, but development transfer is incomplete. V2 wide answer NLL worsens from1.713 at128 to2.654 at512 while its online training loss becomes very small. Its capacity follow-up produces one content token before EOS, not the required32; small produces immediate EOS. These are failures, not performance improvements.

The isolated suffix-path probe finds strong damping on the original layer-0 direct state path across the common long suffix. It does not measure the whole network gradient. V3 changes only the prompt suffix from a newline plus `Aktion:` to a newline; all four eventual controls use the same selected wrapper. It tests the complete wrapper effect, not forget gates alone.

## Final evaluation rule

V3 is the last development variant in this round. Among complete eligible v2/v3 studies, select by wide Dev exact descending, answer NLL ascending, and retain v2 on an exact tie. Publish the chosen complete four-arm freeze before evaluating the48 final cases once. Require at least36/48 wide exact, at least8/12 per class and at least eight more than both untrained controls for the limited task criterion. Report a wide-versus-small advantage separately. The unchanged capacity benchmark remains separate and cannot be replaced with one-digit completion.

## Independent work and reproduction

The training helper independently audited diagnosis and long-training logs; the validation helper audited selection guards and all v1 case judgments; the architecture helper supplied primary-source research, the reproducible suffix-path measurement and controlled-diff reviews. Root performs compilation, bounded runs, prospective publication, final selection and evidence integration.

See `docs/evidence/lm1-training-recovery-20261104/REPRODUCE.md`, the registration files, per-process receipts, raw logs and independent reviews. The input reconstruction helper has reproduced the original checkpoint, tokenizer and corpus with exact hashes from pinned git objects.

Research foundations: Net2Net (https://arxiv.org/abs/1511.05641), Tensor Programs V / muP (https://arxiv.org/abs/2203.03466), and TinyStories (https://arxiv.org/abs/2305.07759). They motivate hypotheses; they do not establish success for LM1. No full muP implementation or imported TinyStories dataset is claimed.

## Boundaries and rollback

Only isolated copies of the owner's LM1 are trained locally. No foreign pretrained weight download, paid inference API, production training, runtime/active-selection change, model promotion, Android installation, main merge or release. Keep the original model and reject research candidates when their gates fail; no deployed application behavior needs reverting. Preserve negative evidence.
