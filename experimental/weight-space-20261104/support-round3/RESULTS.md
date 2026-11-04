# LM1 training-continuity results

## M1 — completed

The original native `LmAdamW` was compiled with the same new Kotlin test as the isolated patched candidate. Actual result: original **10 PASS / 24 FAIL**; candidate **34 PASS / 0 FAIL**. All ten valid four-update parameter fingerprints match byte for byte across minGRU and transformer configurations, including clipping, tiny gradients, zero gradients and zero learning rate.

The 24 negative cases include non-finite/missing/extra/malformed gradients, aliasing with parameter storage, invalid numerical controls and finite inputs whose prospective arithmetic overflows. For rejected update calls, the test checks weight identity, step identity and a subsequent valid update against a control optimizer. That last comparison detects hidden moment corruption.

The patch validates all candidate parameter and moment values in a first pass, then commits the same native arithmetic in a second pass. It does not change epsilon convention, decay scope or valid update equations. It assumes inputs are not concurrently mutated by an unrelated caller. This is rejection-before-mutation for validation failures, not a guarantee against process termination during an in-memory commit. Durable recovery is the separate M2/M3 subject.

Production files are unchanged. The patch is an experimental candidate, not an integrated application change. No existing 16M checkpoint was trained and no quality dataset was evaluated.

Raw evidence: [M1 receipt](evidence/m1/receipt.json), [original output](evidence/m1/original/run.stdout.txt), [candidate output](evidence/m1/candidate/run.stdout.txt). Build manifests bind copied native sources, research source, compiler JARs and executable JAR hashes. Native JARs are reproducible build outputs and are not committed.

Reproduce from this directory using a fresh output directory for each build:

```sh
python build_native.py --repo /path/to/device-ai --lib /path/to/gradle-8.11.1/lib --output /path/to/new-original --extra LmSafetyChecks.kt
python build_native.py --repo /path/to/device-ai --lib /path/to/gradle-8.11.1/lib --output /path/to/new-candidate --patch optimizer-atomic.patch --extra LmSafetyChecks.kt
java -Xmx1g -XX:ActiveProcessorCount=1 -cp /path/to/new-original/checks.jar:/path/to/gradle-8.11.1/lib/kotlin-stdlib-2.0.20.jar at.heinz.deviceai.learning.lm.LmSafetyChecks
java -Xmx1g -XX:ActiveProcessorCount=1 -cp /path/to/new-candidate/checks.jar:/path/to/gradle-8.11.1/lib/kotlin-stdlib-2.0.20.jar at.heinz.deviceai.learning.lm.LmSafetyChecks
```

The original command deliberately exits 1; the candidate exits 0. They are software correctness tests on tiny synthetic parameter arrays, not language-quality experiments.

## M2 / M3

WIP / NOT_RUN. See the prospective plan.
