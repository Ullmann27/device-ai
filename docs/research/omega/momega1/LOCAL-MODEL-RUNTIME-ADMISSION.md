# MΩ-1 explicitly approved local host model/runtime candidate

Status: model bytes/hash/GGUF structure verified; runtime build pending; not admitted.
Chief authorization source: [MODEL-ADMISSION.md](MODEL-ADMISSION.md).

Owner explicitly approved the following local download and HOST execution on
2026-11-03 (relayed by Chief), overriding the earlier no-download gate only for
this exact model and locally built official llama.cpp. No cloud inference, paid
resources, device claims, weight promotion, main/release/signing or installation.

Model: Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF
File: qwen2.5-coder-1.5b-instruct-q4_k_m.gguf
Revision: f86cb2c1fa58255f8052cc32aeede1b7482d4361
Size: 1117320768 bytes
SHA256: cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046
License: Apache-2.0
Official runtime: https://github.com/ggml-org/llama.cpp
Source commit: 6cb3e672e8d10fd4feab1a048bf2967c40623133
Build: CPU only, BUILD_SHARED_LIBS=OFF, GGML_NATIVE=OFF, at most2 compiler jobs.
Artifacts remain outside Git at /workspace/momega1-local-model.

Current reviewed baseline95b6cb1f classifies arbitrary admitted ELF/GGUF pairs as
HOST_INTEGRATION with verifiedModelExecution=false. Configured kind/boolean and
CLI HMAC never establish genuine model execution. A tiny code-reviewed exact
model/runtime-binary tuple registry is planned. Its entry remains candidate until
independent challenger verifies official clean source, actual build provenance,
exact binary identity and local runtime load/generation. Only then may the fixed
CLI mint LOCAL_MODEL_HOST/verifiedModelExecution=true for that exact reviewed pair.
Dummy GGUF, arbitrary ELF, injected transports and JSON clones must still fail
that gate. This is independently reviewed operational trust, not a mathematical
attestation of neural computation or a general quality claim. Fold7 NOT_RUN.

## Actual artifact and compatibility observations

Official source was shallow-fetched at the exact commit, tree
42065e172024f95eb242763caa06812c09d363e7. Tracked source remained clean before
and after build. First llama-cli target was unavailable with server disabled;
Chief explicitly approved the same-source classic llama-completion target.
CPU/static build exit0, max2jobs, GCC14.2.0 and CMake3.31.6. Official CMake wheel
SHA2561c8b05df0602365da91ee6a3336fe57525b137706c4ab5675498f662ae1dbcec.
Binary12476968bytes SHA256
c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a.
llama/ggml are statically linked; only hashed system C/C++/math/compiler support
libraries and ELF loader remain dynamic. Source, object/static library and tool
hash inventories plus actual commands/cache/logs are committed under
`docs/evidence/momega1/runtime/actual-model/`. Build provenance SHA256
ff9b688bbdcda0cea5dbf36f63313a01f055259f241f07eaeb7776327b05f067.

First native counting smoke loaded this actual model and decoded199tokens but the
adapter rejected new timestamped common_perf_print counters. Raw RED preserved.
After narrow counter compatibility repair, same prompt produced identical raw
output hash779830bedbcdd7cd319c5c7afdfe1f24beae2d8eb15d8f5191dc144125a75e30.
Rerun was contended with other CPU work; not a speed measurement.

The fixed adapter now uses ChatML only for the approved Qwen weight digest,
upstream native JSON-schema-file constrained decoding through read-only FD7,
and exact pinned-native trailer normalization. Raw output stays hashed;
serialized prompt AND native schema file hashes are authenticated. No markdown
extraction, patch preset, repaired-test claim, relaxed proposal validation or
budget increase. Small author synthetic coding smoke via default fixed CLI
returned a valid proposal,123decodedtokens in25.981s, within60s, no patch applied.
This smoke preceded final compatibility freeze and is compatibility evidence only.

Compatibility source freeze8425b119e1b6485527de285dd3375f233b27fdf5:
137/137 Node runtime/coding/GGUF regression checks PASS, zero fail/skip.
Challenger reported a second independent build is byte-identical; its untouched
blind response rerun and final independent artifact approval remain pending.
Registry remains CANDIDATE_INDEPENDENT_REVIEW_REQUIRED; current observations stay
HOST_INTEGRATION with verifiedModelExecution=false until that review completes.
No model weights or native executables committed; Fold7 NOT_RUN.
