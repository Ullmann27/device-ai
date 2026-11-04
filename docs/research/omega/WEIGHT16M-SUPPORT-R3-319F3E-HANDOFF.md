# LM1 sequential training-continuity support

Status: M1_VERIFIED / M2_M3_PENDING.

Owner requested sustained sequential research on 2026-11-04. The previous three-agent round is complete in #514. This new branch targets three concrete milestones: invalid-update atomicity, complete optimizer-state recovery, and actual bounded process-interruption recovery.

Prospective plan: [support-round3/PLAN.md](../../../experimental/weight-space-20261104/support-round3/PLAN.md).

Branch: `research/continuum-lm1-resumable-training-20261104-319f3e`, based on #514 `bfed526727041b2581f22b5d613dcb5cd8c188b3`.

Latest model-quality result #508 remains failed acceptance; #525 already owns capacity/choice-gate integration. No consumed holdout is reused and no production/kernel file is edited. New kernel behavior will be an isolated patch, with original-versus-candidate compilation and actual raw results. No 16M training is scheduled under this plan.

M1 actual native result: original 10 PASS / 24 FAIL; isolated repair 34 PASS / 0 FAIL. Ten valid update fingerprints remain byte-identical. Invalid updates now preserve weights, step count and future optimizer behavior. Production files unchanged. [Results and evidence](../../../experimental/weight-space-20261104/support-round3/RESULTS.md).

Next: preserve full moments/schedule/data identity in a separate training checkpoint, then compare uninterrupted and real process-resumed execution.

Rollback: omit this isolated research directory and handoff; the current app and active model remain unchanged.
