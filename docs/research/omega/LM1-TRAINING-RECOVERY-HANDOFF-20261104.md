# LM1 training recovery — 2026-11-04

Status: COMPLETED. All ten registered training runs and the single frozen final-test evaluation are complete. Limited task performance improved, but the acceptance criterion failed and no wider-model advantage was established. Draft PR #508 owns this isolated work.

## Source of truth

- Parent core: PR #492, commit `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Own branch: `research/continuum-lm1-training-recovery-20261104`.
- V1 registered execution: `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`.
- V2 registered execution: `fc2eb797210bee25091caf734aacbfd694a3372e`.
- V3 and final Dev-only choice registration: `be4e49600fed1db4c697230bb8e42c150dbfbdaf`.
- Final four-arm freeze publication: `9ceffc26a2159c8dc726c1f13d2882119159c130`, published before the first final-test evaluation.
- Historical helper PR #497 must not replace the strengthened integrated G1 gates in #492.

All new code lives under `experimental/lm1-training-recovery-20261104/`; evidence lives under `docs/evidence/lm1-training-recovery-20261104/`. Registered sources and inputs stayed unchanged while later variants were added in separate subdirectories.

## Completed findings

All ten training runs completed: **2,368 optimizer updates and 31,452 supervised target tokens** in aggregate. Four 16-step diagnoses used the same historical TRAIN schedule of 3,255 targets each. All reduced the fixed first TRAIN minibatch loss, but none passed the unchanged final capacity benchmark. Wide standard and final-norm-delta/8 worsened development BPB; global LR/8 improved it slightly without restoring capacity. EOS failures were context-specific, not evidence of a global EOS collapse.

| Fresh intent round | Selected small Dev exact | Selected wide Dev exact | Disposition |
|---|---:|---:|---|
| v1: 128 updates, long suffix | 8/24 at 128 | 10/24 at 128 | NO_DEVELOPMENT_SIGNAL; no test |
| v2: 512 updates, long suffix | 13/24 at 256 | 13/24 at 128 | Eligible; not selected by the registered Dev-only comparison |
| v3: 512 updates, short suffix | 13/24 at 512 | 16/24 at 128 | Selected, frozen and evaluated once |

V2 tested longer fresh training with a stretched cosine schedule. V3 changed only the prompt suffix from `\nAktion:` to `\n`, apart from class/experiment identities. The original layer-0 probe supports local state damping as a hypothesis, not a whole-network gradient diagnosis. The short wrapper also changed untrained token preferences. Its advantage over V2 was compared only on Dev; the final test evaluated V3 alone.

## Frozen final outcome

| V3 arm | Selected step | Exact digit-plus-EOS | Correct by class 0 / 1 / 2 / 3 |
|---|---:|---:|---|
| S0: original small | 0 | 0/48 | 0 / 0 / 0 / 0 |
| W0: untrained width-8 copy | 0 | 0/48 | 0 / 0 / 0 / 0 |
| S1: trained small | 512 | **31/48** | **7 / 12 / 10 / 2** |
| W1: trained wide | 128 | **25/48** | **6 / 9 / 7 / 3** |

Both trained arms produced valid digit-plus-EOS format on **48/48** cases. Paired correctness was: 20 both correct, 11 small-only, 5 wide-only and 12 neither. This establishes improved free task performance on this fixed confirmation set relative to the matching untrained controls. It does not establish general language or tool competence.

**Acceptance: FAIL.** W1 did not reach 36/48 overall or 8/12 in every class; S1 also misses those thresholds. The selected wide model trails the small model by six cases, so no wider-model advantage is established. The Dev ranking reversed on the final set. Selected checkpoints have different update counts, so this is not an isolated causal comparison of parameter count.

A post-selection TRAIN evaluation, without weight updates, found **120/128** exact for both trained models, with class counts **24 / 32 / 32 / 32**. Together with the final results, this identifies incomplete transfer to new formulations, especially note/remember intent (class 3) and cancellation/no action (class 0). It does not identify a particular internal cause.

The unchanged post-selection capacity checks both **FAIL**: S1 produces immediate EOS and zero content tokens; W1 produces one content token and then EOS. Neither completes the required 32-token workload. Task formatting does not substitute for that gate.

V3 was the last development variant. The chosen complete V3 quartet was published before testing; no further training or model selection followed the final test. Future development requires newly specified independent confirmation data. This evaluated test must not become a renewed confirmation set after tuning.

## Independent work and reproduction

Independent helpers audited diagnosis/training logs, selection guards, case judgments and controlled harness diffs. Root handled compilation, bounded execution, prospective publication, the frozen choice and evidence integration.

See the final [RESULTS.md](../../evidence/lm1-training-recovery-20261104/RESULTS.md), [case audit](../../evidence/lm1-training-recovery-20261104/final-case-audit.json) and [REPRODUCE.md](../../evidence/lm1-training-recovery-20261104/REPRODUCE.md), together with registrations, receipts and raw logs. Original checkpoint, tokenizer and corpus reconstruction was verified against exact hashes from pinned git objects.

Research foundations: Net2Net (https://arxiv.org/abs/1511.05641), Tensor Programs V / muP (https://arxiv.org/abs/2203.03466), and TinyStories (https://arxiv.org/abs/2305.07759). They motivate hypotheses; they do not establish success for LM1. No full muP implementation or imported TinyStories dataset is claimed.

Owner archive: `LM1_Training_Research_20261104.zip`.
SHA-256: `428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016`.

## Boundaries and rollback

Only isolated copies of the owner's LM1 were trained locally. No foreign pretrained weight download, paid inference API, production training, runtime/active-selection change, model promotion, Android installation, main merge or release. Keep the original model and reject research candidates when their gates fail; no deployed application behavior needs reverting. Preserve negative evidence.
