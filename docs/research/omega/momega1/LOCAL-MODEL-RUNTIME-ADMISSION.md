# MΩ-1 explicitly approved local host model/runtime candidate

Status: DOWNLOAD/BUILD_IN_PROGRESS; not admitted, no inference acceptance.
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
