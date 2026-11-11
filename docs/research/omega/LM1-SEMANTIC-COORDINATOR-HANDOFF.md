# LM1 semantic experiment — coordinator

Base: PR728 / `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.
Scope: EXP-05 / EXP-12; exactly three helpers and at most two bounded candidates.
Current state: PREPARATION; no new training, no product changes.
Read `experimental/lm1-semantic-20261111/PROTOCOL.md` and `COORDINATION.json`.
Data, training and independent evaluator have isolated named branches.
Native git push lacks credentials; authorized GitHub connector fallback is being prepared.
Next: complete source/artifact/toolchain verification, freeze independent holdout and data/retention gates, then run only the authorized candidates if gates pass.
Device, APK, signing: NOT_RUN and outside this experiment.
Rollback: do not select research weights.
