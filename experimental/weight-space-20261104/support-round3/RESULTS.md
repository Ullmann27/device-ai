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

## M2 — completed

The separate `LM1T` research format persists parameters, both Adam moments, optimizer step, numerical controls, schedule settings, worker count, tokenizer hash, source hash, deterministic data hash and seed. The codec streams its payload, binds both body checksum and full-file hash, checks layouts/finiteness before creating a usable trainer, and publishes immutable content-addressed files without replacing an occupied target. File and directory synchronization and hard-link publication were actually exercised on this Linux filesystem; Android filesystem support has not been tested.

**29 native continuity/integrity cases passed.** A fixed minGRU fixture trained for 24 updates produces exactly the same entire training-bundle bytes as an 8-update prefix followed by save/load and 16 more updates. Both the weight-only restart and a separate moment-reset control with the correct step counter diverge. These are tiny deterministic software fixtures, not quality validation or 16M training.

The original native trainer and the candidate also ran the same 24-update fixture independently: every per-step loss/rate/target line and final weight fingerprint is byte-identical. All **34 M1 checks were rerun on the combined patch** and remain green; they are not counted as 34 new test types.

Additional rejected inputs include wrong source/data/seed/tokenizer/schedule, full-file identity mismatch, corruption/truncation/trailing bytes, weights-only files, a rehashed negative variance, symlink inputs/output directories and an already occupied damaged target. The old target remains unchanged. Checksums establish byte integrity, not third-party authorship or cryptographic process attestation.

Source frozen before this test: `2dfe0852a1bb9e32bf701bb0bc8bc594bcca7c0a`. [M2 receipt](evidence/m2/receipt.json), [continuity output](evidence/m2/candidate/continuity.stdout.txt), [original training output](evidence/m2/original/fixture.stdout.txt), [candidate training output](evidence/m2/candidate/fixture.stdout.txt).

## M3

WIP / NOT_RUN. Next: exact continuation across separate JVM processes and deliberately terminated work. An interrupted, uncommitted segment must be replayed from the last completed checkpoint; its orphan output must never masquerade as a completed run.
