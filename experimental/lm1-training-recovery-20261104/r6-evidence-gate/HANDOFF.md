# G1-R6-LM1-CAPACITY-EVIDENCE-20261104

Status: CLAIMED; own integration and synthetic tests NOT_RUN.

Isolated branch from #508 59891cfad6dd015f47c187cc688ea94b0026ad7d. Source donor: #492 0af14f75b04e68ba849a92316b8be51041e8738b, combined capacity-and-choice patch. Do not replace it with the older capacity-only patch or #497's initial repair.

Plan: verify the three active runner blob identities; reproduce synthetic missing/corrupt/foreign capacity rejection cases; transplant byte-identical reviewed fixtures into the corresponding v1/long/suffix runner paths; independently check sealed data/checkpoint selection before test dispatch; document residual direct-entry limitations explicitly. Historical files and receipts remain untouched. New runner sources require prospective registration, not editing historical registration.json.

No model subprocess, training, inference, actual holdout/test data, historical logs, checkpoints, production app files or promotion are in this task. Only synthetic fixtures and allowed research source changes. No actions, paid tools, main merge, release or installation.

The upstream 165 capacity and 37 newer choice/registration cases are author evidence, not executions by this session. Own results will be recorded separately. Preserve valid measured capacity FAIL without converting it into a missing-evidence failure or a model PASS.

Rollback: do not merge this isolated branch if gates fail; retain original #508 and #492 unchanged.
