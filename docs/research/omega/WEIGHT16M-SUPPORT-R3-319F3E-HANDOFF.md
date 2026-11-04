# LM1 sequential training-continuity support

Status: M1_M2_M3_VERIFIED.

Owner requested sustained sequential research on 2026-11-04. The previous three-agent round is complete in #514. This new branch targets three concrete milestones: invalid-update atomicity, complete optimizer-state recovery, and actual bounded process-interruption recovery.

Prospective plan: [support-round3/PLAN.md](../../../experimental/weight-space-20261104/support-round3/PLAN.md).

Branch: `research/continuum-lm1-resumable-training-20261104-319f3e`, based on #514 `bfed526727041b2581f22b5d613dcb5cd8c188b3`.

Latest model-quality result #508 remains failed acceptance; #525 already owns capacity/choice-gate integration. No consumed holdout is reused and no production/kernel file is edited. New kernel behavior will be an isolated patch, with original-versus-candidate compilation and actual raw results. No 16M training is scheduled under this plan.

M1 actual native result: original 10 PASS / 24 FAIL; isolated repair 34 PASS / 0 FAIL. Ten valid update fingerprints remain byte-identical. Invalid updates now preserve weights, step count and future optimizer behavior. Production files unchanged. [Results and evidence](../../../experimental/weight-space-20261104/support-round3/RESULTS.md).

M2: 29 native continuity/integrity cases PASS. Complete training state after 24 uninterrupted updates is byte-identical to 8 + save/load + 16 updates. Weights-only restart and moment reset with correct step diverge. Original/candidate 24-step loss/rate/target logs are byte-identical; 34 M1 checks were rerun and still pass. No native 16M update.

M3: 38 process checks PASS at preregistered source `fcfe0a93439d087465f9d9a4a4e87daae0340bb2`. Three separate JVMs and two real SIGKILL/recovery scenarios reproduce the uninterrupted complete state and committed step logs exactly. Last confirmed checkpoint is preserved; orphan state is not promoted; competing writer and tampered inputs are blocked. 112 actual tiny updates across four scenarios, including 16 recomputed updates. No 16M update, Android run or daemon activation.

Next useful boundary: test full-state serialization/decoding at the existing 16,011,776-parameter size without any optimizer update, under a separately frozen resource budget. The current functional proof is on the 2,632-parameter synthetic fixture.

Rollback: omit this isolated research directory and handoff; the current app and active model remain unchanged.
