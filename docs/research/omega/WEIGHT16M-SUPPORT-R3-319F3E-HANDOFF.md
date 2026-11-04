# LM1 sequential training-continuity support

Status: WIP / EXPERIMENTS_NOT_RUN.

Owner requested sustained sequential research on 2026-11-04. The previous three-agent round is complete in #514. This new branch targets three concrete milestones: invalid-update atomicity, complete optimizer-state recovery, and actual bounded process-interruption recovery.

Prospective plan: [support-round3/PLAN.md](../../../experimental/weight-space-20261104/support-round3/PLAN.md).

Branch: `research/continuum-lm1-resumable-training-20261104-319f3e`, based on #514 `bfed526727041b2581f22b5d613dcb5cd8c188b3`.

Latest model-quality result #508 remains failed acceptance; #525 already owns capacity/choice-gate integration. No consumed holdout is reused and no production/kernel file is edited. New kernel behavior will be an isolated patch, with original-versus-candidate compilation and actual raw results. No 16M training is scheduled under this plan.

Next: reproduce the native invalid-gradient cases, record exact source/tool identities, then implement the smallest corrective patch.

Rollback: omit this isolated research directory and handoff; the current app and active model remain unchanged.
