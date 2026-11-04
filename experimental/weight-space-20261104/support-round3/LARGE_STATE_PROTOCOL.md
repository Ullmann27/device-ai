# Prospective 16M state-format boundary

Status at registration: NOT_RUN. Follow-up to the completed M1/M2/M3 software milestones. This is one bounded serialization/decoding observation, not training or model-quality evaluation.

## Exact scope

Read the existing 16,011,776-parameter / 57-tensor checkpoint with SHA-256 `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312` and tokenizer SHA-256 `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`. Native LM1C decoding, tensor-layout checks and parameter finiteness remain required.

Construct fresh zero Adam moments and step zero solely for this format fixture. Stream the full `LM1T` bundle to a new content-addressed file, close that trainer, decode the bundle into a fresh trainer, verify all 16,011,776 parameter bits and all 32,023,552 moment values, and serialize again. The full bundle identity must be unchanged and existing output must not be overwritten. Original model and tokenizer hashes must remain unchanged.

**Zero optimizer updates and zero inference calls.** No historical optimizer state is available in the weight-only LM1C input. The zero-moment fixture is explicitly not a recovered historical training trajectory, a trained candidate or evidence of 16M training continuity. The demonstrated training-continuity experiment remains the 2,632-parameter fixture.

## Fixed resource limits and recording

Exactly one attempt, 120 seconds, one reported processor, 1 GiB JVM heap and at least 768 MiB free disk before starting. Failure/OOM/timeout is retained without expanding the budget or rerunning. The bundle needs approximately 12 bytes per parameter plus format metadata; temporary publication uses a hard link rather than a duplicate full file. No input or user file is deleted.

`large-state-registration.json` binds the compiled program, unchanged source inputs, patch, codec, measurement wrapper, Java executable, Kotlin standard library, exact input hashes, output directory and command. Publish it before execution. The resource wrapper records Linux maximum child-process RSS with its preflight baseline, elapsed time, exit/timeout, raw output hashes and unchanged inputs. RSS is not an Android measurement or a simultaneous sum across processes.

The resulting large bundle is a reproducible format-test artifact with unchanged model weights and fresh zero moments. Its size and hash are retained in Git evidence; no model promotion or release artifact is created.
