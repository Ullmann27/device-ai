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

## M3 — completed

**38 process-recovery assertions passed.** The prospective source and generated registration were published at `fcfe0a93439d087465f9d9a4a4e87daae0340bb2` before native training. One initial invocation was blocked because the remote commit was still being fetched; its preflight refusal is preserved with zero native processes/updates. The unchanged committed experiment then ran once in a fresh output directory after fetch completion.

| Fixed scenario | Physically executed updates | Committed steps | Full state vs reference |
| --- | ---: | ---: | --- |
| One uninterrupted JVM | 24 | 24 | reference |
| Three separate JVM segments | 24 | 24 | byte-identical |
| SIGKILL before checkpoint publication | 32 | 24 | byte-identical |
| SIGKILL after publication, before completion | 32 | 24 | byte-identical |

Both intentionally killed JVMs actually exited with signal 9. The last completed head remained at step 8. The before-publication case had only the original checkpoint; the after-publication case retained a step-16 orphan. Neither incomplete segment was promoted. Eight lost/uncommitted updates were recomputed in each interrupted scenario, accounting for the additional work. All committed per-update logs exactly match the uninterrupted reference.

A second writer was actually blocked by the OS file lock while the native worker was paused, with no extra attempt created. Reopening a completed job started no further training. Tampered receipt ancestry, a damaged checkpoint, wrong job identity and the four-attempt budget all blocked new training.

Total across the four tiny scenarios: **112 actual optimizer updates**, two deliberate native-process kills, zero 16M updates. The tests establish this Linux/JVM recovery path, not Android lifecycle behavior or unbounded overnight service. Full evidence: [M3 manifest](evidence/m3/manifest.json), [all actual run commands and checks](evidence/m3/raw/receipt.json), [prospective protocol](RECOVERY_PROTOCOL.md).

Reproduction uses `build_native.py` with the combined patch plus `LmTrainingFixture.kt`, `LmTrainingState.kt` and `LmSegmentWorker.kt`. On another host, generate and publish a new registration with `register_recovery.py`, then execute `test_recovery_processes.py --repo /path/to/repo --source-commit FULL_REGISTRATION_COMMIT --output /path/to/new-output`. Old registrations bind their exact executable/source paths and must not be edited to describe a later run.

## M4 — completed at the actual 16M size

The separately preregistered source `c11b5d2de9c39d4447fcc03cf33e6d07753b1737` executed once, exit 0, without timeout or optimizer update. Native decoding read all **16,011,776 parameters / 57 tensors** from the existing pinned model. The format fixture created fresh zero moments, saved and decoded the complete training bundle, checked **32,023,552 moment values**, and serialized it again with exactly the same full-file identity.

| Observation | Actual value |
| --- | ---: |
| Complete training bundle | 192,143,140 bytes / 183.242 MiB |
| Whole state-only probe | 8.036579358 seconds |
| Maximum monitored-child RSS | 732,224 KiB / 715.0625 MiB |
| Preflight-child RSS baseline | 11,648 KiB |
| Optimizer updates / inference calls | 0 / 0 |
| Model and tokenizer input hashes | unchanged |

Canonical parameter fingerprint before and after: `cd90c599dbe271e5ccc9f2281f52d0e84fb7c259d0c18527d47e600a0ea7bba7`. Complete bundle SHA-256: `24661ee1a2db0e00bafc93ae4ea142b3cb996bd28ed64bb720a135bdea50c026`.

The memory observation is Linux `RUSAGE_CHILDREN` maximum with the recorded preflight baseline; it is not simultaneous summed RSS, an isolated optimizer peak or an Android measurement. The observation fits the registered 1 GiB heap / 120-second limits. No bound on device memory or future nonzero 16M training follows from it.

**The 16M moments are fresh zeros. Historical optimizer moments were never present in the LM1C input.** This is real format-size/integrity evidence, not a claim that a historical 16M training trajectory has been resumed. Nonzero continuation is demonstrated only by the separate tiny M2/M3 fixtures.

Evidence: [native rows](evidence/m4/native.tsv), [resource receipt](evidence/m4/receipt.json), [source/compile manifest](evidence/m4/manifest.json), [prospective protocol](LARGE_STATE_PROTOCOL.md). The large derived bundle is a reproducible format-test output; its size/hash are archived, not promoted as a trained model.

## Final scope

Four milestones completed sequentially, without new subagents. Production model/runtime files remain unchanged; all kernel changes are isolated patch artifacts. No main merge, new 16M training, quality-test reuse, APK, device installation, app-network change or background daemon. Remaining work is independent patch integration review, nonzero 16M continuation under a newly justified protocol, Android filesystem/lifecycle testing, and fresh independent quality confirmation after the failed #508 acceptance.
