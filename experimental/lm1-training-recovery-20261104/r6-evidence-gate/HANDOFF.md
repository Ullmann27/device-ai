# G1-R6-LM1-CAPACITY-EVIDENCE-20261104

Status: SOURCE_INTEGRATED / V1_SYNTHETIC_GATES_PASS / OVERALL_ACCEPTANCE_BLOCKED.

Own draft #525 is isolated from #508 59891cfad6dd015f47c187cc688ea94b0026ad7d. Integration commit333ac4826259bc1ecbfb69cd6eae837dbf4a91d1 transplants the exact three reviewed runner fixtures from #492 0af14f75b04e68ba849a92316b8be51041e8738b. No historical registration, dataset, model, checkpoint, log or receipt was changed.

## Exact source reuse

| Runner | Original Git blob | Integrated Git blob |
| --- | --- | --- |
| v1 | 28908e07d6217f10882429047c853d1dd8667912 | 9f0181b2fdf459bcd01af1b0ce87a5cb1e43c15c |
| long | 30119435feed1728c5d22fec40830654a7a19861 | 9aa88b121e3e48fbf2f3c2612c674f38bb7c9265 |
| suffix | 417e4b3289bb692e72dc80398b51b67e106b8907 | 7b83e7f2e6d94675b1f63d7b04553ce62002ba9a |

The donor manifest is an unchanged historical provenance record: its NOT_APPLIED status describes the donor publication, not this new isolated integration. V2/V3 receive the combined capacity-and-study-choice patch, not the older capacity-only patch. New execution requires a new prospective registration; do not rewrite historical registration.json to bypass the source mismatch.

## Own executed tests

The entire original and repaired v1 modules were materialized from the connector and verified against their Git blob and SHA-256 identities. A new independent 26-case synthetic harness executes the real verify, selection, post_capacity_evidence, freeze_evidence and test methods. Only JVM dispatch is replaced with deterministic synthetic output; subprocess.Popen is forbidden. No actual model, JAR, dataset, tokenizer or holdout is executed/read. Synthetic files explicitly contain synthetic marker bytes, not model weights.

Same test file, original: 2 PASS / 24 FAIL, exit1.
Same test file, repaired: 26 PASS / 0 FAIL, exit0.

Cases cover valid measured PASS and FAIL propagation into freeze and summary; missing logs/receipts, malformed receipts, foreign source/registration/JAR, wrong log hash, wrong command, timeouts, inconsistent exit status and truncated logs both before freeze and after freeze. Changed sealed synthetic bytes and selected checkpoint bytes are rejected before simulated dispatch. Those last two checks already passed on the original runner; they are retained behavior, not a new fix.

Original invalid post-freeze cases reached four simulated dispatches; repaired invalid cases reached zero. Valid measured FAIL remains visible and permits the synthetic intent report without a model-promotion claim.

Test source blob b40c80250335d1ef04f5261a0c3763da7ab680db; SHA-256 35f1cf699c0cae0867a02911188f3c5bc15a498d3638015d29b4051d4f04b31b. The published file was read back and matches executed bytes. See results.json for each case.

## Boundaries and remaining acceptance

1. The 26 own checks exercise v1, not the v2/v3 CLI. Their byte-identical donor fixtures retain the upstream 165 historical capacity and 37 newer choice/registration cases as AUTHOR evidence; this session did not rerun those suites or add their counts to its own tests. Re-run the appropriate combined suites before accepting all three runners.
2. Static review of unchanged LmIntentStudy.kt lines223-231 confirms that direct four-argument JVM test mode does not itself consume a frozen selection or expected sealed hash. Registered Python execution validates the seal/selection before dispatch, but standalone Kotlin invocation is a separate unclosed boundary. No direct model call was attempted. Overall task acceptance remains BLOCKED until that entrypoint is bound or explicitly excluded by the owner; do not claim universal test-read authorization.
3. No historical research outputs were reopened to optimize results, no real holdout evaluated, no model quality or capacity improvement claimed.

## Reproduce safely

From the candidate checkout:

```
R=experimental/lm1-training-recovery-20261104
python "$R/r6-evidence-gate/test_capacity_gate.py" "$R/run_registered_study.py" /tmp/r6-lm1-green.json
cp "$R/r6-evidence-gate/original_v1.py.txt" /tmp/r6-lm1-original.py
python "$R/r6-evidence-gate/test_capacity_gate.py" /tmp/r6-lm1-original.py /tmp/r6-lm1-red.json
```

Expected exits0 and1 respectively. No JAR should ever be started by these checks. They operate exclusively under newly created temporary synthetic directories. Do not use the runner's real train/intent/test CLI as an acceptance shortcut.

Next: v2/v3 synthetic combined-suite repetition and a separately tested minimal Kotlin entrypoint authorization with matching prospective runner protocol. Preserve S0/W0 controls and S1/W1 selection identity; do not authorize arbitrary checkpoints by accepting a self-declared hash alone.

Rollback: do not merge #525 while remaining gates are open. Revert only its runner integration if rejected; preserve #508/#492 and all original research artifacts. No production app/runtime, main, Actions, signing, release, installation or model promotion was changed.
