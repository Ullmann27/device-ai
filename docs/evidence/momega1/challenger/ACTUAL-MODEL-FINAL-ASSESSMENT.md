# Independent actual-model final assessment

**SOURCE ACCEPTED:** 9e28d24bcf9ef11d9b19f5fec1be60efe2c6ab96 for the exact reviewed
CPU model/runtime pair and genuine host observation boundary. **LOCAL_MODEL_HOST
observed. No accepted repair, FIRST_COGNITIVE_LOOP_VERIFIED, learning, Android or
release claim. FOLD7_NOT_RUN.**

Owner-authorized pair:
- Official Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF, revision
  f86cb2c1fa58255f8052cc32aeede1b7482d4361,
  qwen2.5-coder-1.5b-instruct-q4_k_m.gguf,1117320768bytes,
  SHA256 cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046.
- Official ggml-org/llama.cpp commit6cb3e672e8d10fd4feab1a048bf2967c40623133,
  tree42065e172024f95eb242763caa06812c09d363e7; Linux x64 CPU llama-completion,
  12476968bytes, SHA256 c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a.

## Independent provenance

Official HF revision LFS metadata and an independent full-file streamed hash agree
with owner-approved size/hash. Official GitHub commit/tree agree with the clean
source checkout, including no untracked source. Git connectivity verification
passes. CMake3.31.6 wheel hash independently matched PyPI; GCC14.2.0, binutils2.44
and tool hashes recorded. Reviewer performed a separate two-job build into another
build directory: configure/build exit0,269.488seconds, source clean afterwards,
**byte-for-byte identical runtime binary**. No additional model or paid resources.

Static llama/ggml libraries are in the binary; system libc/libstdc++/libm/libgcc_s
and loader remain trusted host dependencies, independently hashed. This is observed
source/build provenance under that trusted compute base, not hardware attestation
or cryptographic proof of arbitrary neural computation.

Pair-review artifact APPROVED-PAIR-REVIEW.json is immutable and has SHA256
88e886df6bd60e953605f49e2768529002c9925430a88b5d1467cf95ee40c9fe.
Author build-evidence SHA is
ff9b688bbdcda0cea5dbf36f63313a01f055259f241f07eaeb7776327b05f067.
Registry promotion8425b119→9e28d24b changes only status and independent review SHA
in production. Getter returned null before promotion. No configurable registry,
setter, arbitrary pair admission or env-only genuine flag exists.

## Actual blind task and honest failures

Challenger prompt and eight boundary checks were frozen before candidate
inspection; native author did not inspect prompt or prescribe output. QA's separate
12 unknown coding tasks were never read by this lane.

1. Original formatter/native interface: actual model load and generated content,
   but exit137 after74368ms and fenced JSON plus repetition. Strict parse rejected;
   no accepted proposal. The120CPU-second hard limit is the likely termination
   cause; wall watchdog did not fire. Raw failure preserved, not rewritten.
2. Corrected8425b119 Qwen ChatML plus native JSON-schema-file interface, same user
   prompt: exit0,124 native decoded tokens,15484ms, raw output SHA
   3f2d6dfcd0b83f26db3d8e64c3bca7ece477aed6a7a375d9cabbf256f70e85c1.
   Actual model/runtime retained FD pins stable before/after. Known exact runtime
   trailer removed only for parsing; raw output stays hashed. Model proposed one
   bounded patch. **Independent tests:6/8 PASS,2 FAIL**. It fixes empty/exact-multiple
   cases but discards legitimate remainder/wide batches. No repair accepted.
3. Final9e28d24b DEFAULT provider/fixed trusted CLI, same frozen task/source through
   production context serialization:142 decoded tokens,26562ms, normal exit and
   genuine LOCAL_MODEL_HOST observation. It proposed the same incorrect patch;
   independent tests remain6/8 PASS,2 FAIL. Model hypothesis also misidentifies
   which input lengths trigger the original bug. Evidence is useful precisely
   because actual inference does not imply a correct repair.

Final provider original observation is private-branded and frozen; JSON clone,
even refrozen, is untrusted. Exact source/task/request/prompt/schema/output/proposal
bindings are authenticated. trace and nested verifiedModelExecution=true only for
the reviewed tuple. Model output remains trusted:false/autoApply:false. Source was
unchanged. A second request and a fresh provider on the same durable evidence both
reject the consumed model-call budget. The provider result and durable trace are
retained in actual-native-compatible/final-provider-result.json.

## Independent controls and real-weight negatives

**50/50 independent controls PASS,0 FAIL,0 SKIP**, exit0 on final9e28d24b. Includes
all original frozen provider/C probes, registry exact/malformed/unknown/env-only
admission controls, and10 genuine-brand forgery tests. An injected run carrying
exact reviewed-pair fields plus a recomputed valid per-request MAC still cannot
obtain a private brand. Re-MACed wrong nonce/source/context/prompt/schema/model/
runtime/candidate hashes reject. Error envelopes cannot forge trusted inference.
The original fixed-output ELF/dummy GGUF counterexample remains non-genuine;
runtimeKind alone produces at most process evidence, never actual inference.

Actual approved-model AbortSignal at4002ms returned CODING_CANCELLED at4045ms with
no observation. A disposable copy of the real model was replaced at4001ms during
execution: retained FD keeps original bytes, but post-run path identity invalidates
acceptance (LOCAL_RUNTIME_FILE_CHANGED at13792ms), no inference receipt returned.
Approved original model stat and final full SHA remained unchanged. No productive
weights modified. Synthetic runtime/model swap, unsafe link, overflow, network,
host-file, fork and resource-limit controls remain passing.

## Resources and limits

Executable-inode-matched /proc sampling observes the actual native runtime,
calibrated with a64MiB native allocation. Direct compatible run sampled peak RSS
2063420KiB and final provider2078688KiB (~1.98GiB),3 threads (main+2 workers).
These are sampled observations, may miss transients, and are not standalone
performance benchmarks. The first run overlapped briefly with another smoke and
compilation; timings are not compared as speedups. Both builds used at most2 jobs.

Parent-process-tree and RUSAGE_CHILDREN measurements undercount bwrap's inner
process here. Their small numbers are explicitly invalid as model RSS and must
not be reused. The raw earlier fields are retained with this correction.

Actual launches retain enforced network/PID/user namespaces, read-only admitted
FDs, no host checkout/credentials,4GiB address-space limit, CPU budget,64 FDs,
1MiB file limit,64KiB output cap,1800-token/8192-context/2-worker limits and fork
denial. Upstream whole-task revoke, owner/UI STOP, global chat/coding capacity,
Android/ARM64 behavior and genuine paired recall benefit remain separate gates.
This review grants no new tool authority, installation, signing or release rights.

## Reproduction

Final controls:
```
MOMEGA1_TARGET=/absolute/checkout/of/9e28d24b \
MOMEGA1_REVIEWED_RUNTIME_SHA256=c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a \
MOMEGA1_EXPECT_REVIEWED=1 node --test \
 tools/momega1-challenger/provider-contract.test.mjs \
 tools/momega1-challenger/runtime-review.test.mjs \
 tools/momega1-challenger/reviewed-pair-contract.test.mjs \
 tools/momega1-challenger/genuine-brand-forgery.test.mjs
```
Actual host drivers and exact arguments are recorded in native resource receipts.
Artifacts stay outside Git at /workspace/momega1-local-model; source/build recipes,
raw synthetic output, hashes, failures and review certificates are versioned.
No extra inference is necessary to reproduce already observed evidence claims.
