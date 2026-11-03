# Approved host model prerequisites — replication

2026-11-03; source baseline `8826713d70128df440502fb0676a23f5d1dfb492`.
Branch `codex/synapse-model-prerequisites-20261103`.
This lane changes documentation only. All runtime/source/model files remain outside
Git in `/workspace/synapse-local-model`. Quota `NOT_VISIBLE`.

## Scope and consent

Existing explicit consent is recorded in `../momega1/MODEL-ADMISSION.md` (resolve
from `docs/research/omega/`, not from this directory). It covers only the exact
official Qwen2.5-Coder1.5B Q4_K_M and locally built official llama.cpp on the host.
Chief assigned prerequisite replication and explicitly deferred compilation until
the combined Android build permits it. No compilation or inference yet. No frozen
benchmark task/answer files inspected, no development-model patch supplied.

Fresh PR427 head `c78cf8a551a4d12f5dbb27ec3641fd4ae9e6f76d` records reviewed
runtime producer source `9e28d24bcf9ef11d9b19f5fec1be60efe2c6ab96` and independent
pair admission. Its other-environment artifact path is absent in this workspace.
Local searches under `/workspace` and `/tmp` found no GGUF, llama-completion or
runtime-env.json. Available tools expose no cross-environment artifact transfer;
GitHub Actions artifact tools were not used. Replication is required.

## Immutable pins

* Model: `Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF`.
* Revision: `f86cb2c1fa58255f8052cc32aeede1b7482d4361`.
* File: `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`,1117320768 bytes.
* SHA256: `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`.
* Official HF API rechecked the revision, LFS hash/size and Apache-2.0 license.
  The pinned LICENSE file was fetched and read before the weight download.
* Official runtime: `https://github.com/ggml-org/llama.cpp`, source
  `6cb3e672e8d10fd4feab1a048bf2967c40623133`, expected tree
  `42065e172024f95eb242763caa06812c09d363e7`, MIT.
* Expected llama-completion:12476968 bytes,
  SHA256 `c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a`.
* CMake3.31.6 official manylinux2014 x86_64 wheel SHA256
  `1c8b05df0602365da91ee6a3336fe57525b137706c4ab5675498f662ae1dbcec`.

## Current observations and pending gates

18GiB free initially. Current GCC14.2/ld2.44 executable hashes and all five
ELF-loader/C/C++/math support library hashes match the prior independent build
provenance. Weight download, exact shallow source fetch and CMake staging started.
The matching toolchain makes byte equality plausible; no binary equality is claimed.

Next: finish downloads, assert full model byte count/SHA and GGUF magic/version,
assert clean source/tree and licenses, record exact reproduced build recipe with
source path mapping. Wait for Chief before CMake configure/build (max2 jobs).
Keep any mismatch blocked; never change the runtime admission hash to fit a new
binary. No APK/device/install/release/learning claim follows from these prerequisites.
