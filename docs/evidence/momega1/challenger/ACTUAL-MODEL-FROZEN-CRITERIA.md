# Actual approved model acceptance — independent freeze 2026-11-03

This extends, does not replace, the original15 frozen criteria and fixed-ELF
counterexample. Frozen before examining a reviewed-pair admission implementation.
Owner explicitly approved ONLY this free-host experiment:

- Model: official Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF, revision
  f86cb2c1fa58255f8052cc32aeede1b7482d4361,
  qwen2.5-coder-1.5b-instruct-q4_k_m.gguf,
  1117320768 bytes,
  SHA256 cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046.
- Runtime: official ggml-org/llama.cpp Git commit
  6cb3e672e8d10fd4feab1a048bf2967c40623133, local CPU build.
- Build/artifacts outside Git in /workspace/momega1-local-model; max2 build jobs,
  existing4CPU/16GiB provisioned host only, no additional models or paid compute.
- No Fold7/device access; FOLD7_NOT_RUN. No owner-data/sign/install/release change.

Acceptance requirements:

A1. Independently establish source origin/commit/tree and clean tracked source;
record exact compiler/linker/CMake versions, flags, build command, exit and binary
SHA256. Build-generated or untracked executable source must not silently alter
this identity. Prefer a separate reviewed build; practical observed source+build
provenance is acceptable if exact scope/limits documented. Build toolchain and
system libs are explicitly trusted; reproducible binary is not assumed.

A2. Independently stream-hash actual full GGUF and check exact byte count against
owner-approved pin. No modelId, filename, config boolean, synthetic GGUF or self-
signed manifest substitutes. Preserve read-only original artifacts after review.

A3. Registry admission binds exact reviewed model+runtime binary tuple (and
relevant provenance) to this approved experiment. A caller-controlled runtime kind,
flag, environment hash, injected run, self-signed HMAC or previously branded
HOST_INTEGRATION observation cannot mint genuine model proof. Unknown binary,
unknown model, modified pair or replayed receipt must reject/downgrade.

A4. Actual fixed trusted CLI must spawn the admitted binary with retained model
and runtime FDs in enforced local OS isolation. Observe real model load metadata,
actual native decoding/output, normal termination, bounded tokens/time/resources,
prompt/output/proposal digests and immutable branded result. Same prompt cannot
be replaced or prewritten by runtime author. Native performance text is supporting
evidence only; official reviewed executable plus actual run establish provenance.

A5. Run at least one challenger-written blind coding prompt frozen separately
before candidate inspection. It has no preset model reply. Retain prompt/output
only because they are fully synthetic. Execute independent output-shape/boundary
checks; inference success and correctness are separate outcomes. QA's12 unknown
coding tasks remain private and are not read or reproduced by this lane.

A6. Retest frozen fixed-output ELF/dummy-GGUF A/B: runtimeKind=LLAMA_CPP alone
must remain HOST_INTEGRATION/false and cannot mint genuine observation or promote
memory/loop success. Clone/injected receipt/forged runtime-pair config cannot brand.

A7. Real-weight cancellation and deadline return boundedly with owned process
cleanup; actual model/runtime alterations fail closed without modifying approved
originals (use disposable copy/retained descriptors if needed). Preserve earlier
synthetic swap/symlink/overflow/process-budget tests as controls.

A8. No remote inference/download/fallback during actual execution. Verify native
sandbox denies host network/files/credentials; carry forward independent actual
namespace/limit tests. Concurrent chat/coding and Android resource/lifecycle remain
separate gates. CPU host inference establishes no Fold7 or learning benefit.

Decision: grant LOCAL_MODEL_HOST only for exact observed approved tuple/run.
A successful model response is not FIRST_COGNITIVE_LOOP_VERIFIED, broad model
quality, durable experience correctness, Android execution or release acceptance.
Record failures openly; do not relax criteria to make native output appear valid.
