# Device AI Continuum: local model/runtime feasibility

Research date: 2026-10-24 UTC. Primary sources were fetched through the configured session proxy and their bodies saved in `/workspace/scratch/sources`. These are observations of published documentation, not measurements of a Galaxy Z Fold7. No remote model inference API was used.

## P0 decision

Use **Qwen2.5-Coder-1.5B-Instruct Q4_K_M GGUF with llama.cpp on the present x86_64 CPU host** for the first genuine model-generated patch experiment. It is an available, coding-specific, Apache-2.0 model with an official Qwen GGUF artifact of 1,117,320,768 bytes. A small successful patch is evidence of that task only; it is not evidence of repository-scale autonomy.

The present host reports **x86_64, 9.7 GiB total RAM, no swap**, with roughly 9.2 GiB available before model loading. The actual process-group resource boundaries are tighter: `/sys/fs/cgroup/memory.max` is **8,589,934,592 bytes (8 GiB)** and `cpu.max` is **200000 100000 (2 CPU quota)**. `llama-cli`, Ollama and CMake were initially absent. GNU C++ and Python were available. The official llama.cpp prebuilt Linux x64 runtime executes here, so building a compiler toolchain is unnecessary for P0.

The chosen model was downloaded completely and its SHA-256 matches the official Hugging Face LFS object identity. Actual local inference measurements follow below.

## Verified availability and practical distinctions

| Candidate | What its primary source actually says | Practical implication |
|---|---|---|
| Qwen3-Coder-Next | **80B total**, **3B activated**, 48 hybrid layers, 262,144 native context; model card declares Apache-2.0 | Activated parameters describe compute, not the complete stored weights. A simplistic 4-bit estimate is 80B × 0.5 bytes = **40 GB / 37.3 GiB just for weights**, before quantization metadata/runtime/cache. This estimate rules it out on this 9.7 GiB host, and does not establish Fold7 viability. |
| Qwen2.5-Coder-0.5B-Instruct GGUF | Official coding family, Apache-2.0; Q4_K_M file measured by HF metadata at **491,400,064 bytes** | Smaller fallback for latency or weak device memory; coding quality must be checked by actual tasks. |
| Qwen2.5-Coder-1.5B-Instruct GGUF | 1.54B parameters, 28 layers, 12 query / 2 KV heads, 32,768 model context; Apache-2.0 | Chosen P0. Artifact size is **1.041 GiB**. Start with 2,048–4,096 context, bounded output and a single sequence. **Estimated** resident working set 1.3–2.0 GiB at short context; this is not a measurement. |
| Qwen2.5-Coder-3B-Instruct GGUF | 3.09B parameters; metadata and actual LICENSE specify **Qwen Research** custom terms, not Apache-2.0 | Do not propagate the 1.5B license to 3B. Review its actual license before distribution/commercial use. |
| Qwen3-0.6B LiteRT-LM | LiteRT community card provides INT8 `.litertlm` 586 MB / 4,096 context; dynamic INT4 artifact 329 MB / 4,096 context; Apache-2.0 | Small ready LiteRT candidate, but it is the general Qwen model, **not Qwen3-Coder-Next** and not a coder-specific variant. Artifact and runtime must be paired. |
| Qwen2.5-1.5B-Instruct LiteRT-LM | Listed in official LiteRT overview and provided by litert-community | Another available general model for LiteRT; do not relabel it Qwen2.5-Coder. |
| Gemma 4 | Official Google card lists **E2B, E4B, 12B, 26B A4B, 31B**, both pretrained and instruction-tuned, and Apache-2.0 | Gemma 4 is released as of this research date. Prior knowledge that it is unavailable would be stale. Availability does not measure this particular device. |

Gemma 4 details that affect memory claims:

- E2B: **2.3B effective / 5.1B with embeddings**; E4B: **4.5B effective / 8B with embeddings**. Both use per-layer embeddings and up to 128K context. “Effective” must not be mistaken for the whole weight file or a measured RAM requirement.
- 12B: 11.95B parameters; 31B: 30.7B; 26B A4B: 25.2B total / 3.8B active. The large models have up to 256K context. Context maxima are capabilities, not sensible mobile defaults.
- The official Google **E2B QAT Q4_0 GGUF** artifact metadata reports **3,349,516,256 bytes**, plus a separate **986,833,664-byte multimodal projector**. Those are file sizes, not process peak RAM. This exceeds the requested ≤2 GB download limit for this experiment.
- The LiteRT E2B card describes mixed 2/4/8-bit mobile quantization: text-only weight footprint can be as low as 0.8 GB with **1.12 GB of embedding parameters memory-mapped**, and vision/audio loaded on demand. This is a vendor claim and platform-dependent. It is not equivalent to assuming every 5.1B parameter is permanently resident. The official LiteRT overview lists the E2B model at **2.58 GB**.
- Official LiteRT throughput tables name Samsung S26 Ultra, iPhone 17 Pro, MacBook Pro and other devices; those numbers are **vendor benchmarks**, not measurements of the Fold7 or this host. No Fold7 hardware, ABI, free memory, Android process behavior, thermal throttling or battery drain was measured here.

## Runtime and file compatibility

**llama.cpp** is MIT licensed. Its Android guide explicitly supports Android Studio bindings, Termux builds, and Android NDK **arm64-v8a** cross-compilation. Its binding reads GGUF metadata and loads GGUF weights. The guide recommends starting around **4,096 context**, warning excessive context may kill the terminal through memory use. This establishes an ARM64 Android path; it does not establish that arbitrary Ubuntu ARM64 binaries run on Android, nor guarantee Snapdragon GPU/NPU performance.

**LiteRT-LM** is a different runtime. Google's file-builder documentation defines `.litertlm` as a container of **TFLite models, tokenizer files, external weights and metadata**. A raw PyTorch checkpoint first requires a supported conversion to `.tflite`. No fetched primary source establishes that LiteRT-LM directly loads `.gguf`; changing a file extension is not conversion. Use an already converted model, or implement and validate the model-specific conversion path. Support for “Qwen” in a family list does not prove support for Qwen3-Coder-Next's hybrid MoE architecture.

Current LiteRT-LM documentation calls Python, Kotlin and C++ stable; Swift and Web are early preview. Its README advertises CPU/GPU/NPU support and function calling. Those are runtime capabilities; function-call correctness and native acceleration still need target-device tests.

## Repository retrieval, action loops and verifiers

Use retrieval to fit a **small relevant working set** into the real context window rather than feed an entire repository. RepoCoder is a primary research example of iterative similarity retrieval plus generation for repository-level completion. For a first implementation, deterministic file/symbol/path and lexical search with nearby imports/callers/tests is sufficient and easier to audit than a new embedding service. Treat this as an engineering choice, not a claim that lexical search equals a published retriever.

ReAct's primary paper describes interleaving reasoning and environment actions. The useful implementation contract here is a bounded **retrieve/read → propose edit → execute independent verifier → inspect result → repair or stop** loop. ReAct does not itself create a trustworthy shell, tests, sandbox or secure approval policy.

SWE-agent's primary paper demonstrates that a carefully designed agent-computer interface matters for navigation, editing and executing tests. SWE-bench's official repository provides containerized evaluation of generated patches. Neither result establishes that a 1.5B quantized model can solve SWE-bench or autonomously maintain an arbitrary repository.

For the local proof, preserve the original task, model prompt, model response, exact patch, verifier stdout/stderr/exit code and timing. Keep expected behavior in a verifier that the model cannot silently replace with its patch. Run a deliberately defective baseline first, then run the same verifier after applying the model-produced patch. A successful import, a syntactically valid diff, or the model saying “tests passed” is not the verifier result. Keep at least one independent regression check and bound repair attempts/token budget. Do not claim broad quality from one toy task.

## Downloaded runtime (measured)

- Runtime archive: `/workspace/scratch/llama-b11861-bin-ubuntu-x64.tar.gz`
- Archive size: **22,439,458 bytes**
- Archive SHA-256: **`af86309b14d55f77b59053b8e8e064e1ce27c58f288adb474ff99d61b29a8e76`**
- Executables: `/workspace/scratch/llama-runtime/llama-b11861/llama-completion`, `llama-cli`, `llama-server`.
- Actual `llama-cli --version` exit code **0**: `0.7.0-dev (build 11861, commit 73adf89e3)`, GNU 11.4.0 for Linux x86_64.
- CPU-only load should explicitly use `--device none -ngl 0`; short context `-c 2048`, bounded generation `-n 256`, and 2 threads are conservative starting values, not a measured optimum.
- `llama-completion` supports `--single-turn --simple-io --no-display-prompt` and raw non-conversation ChatML via `--no-conversation`. Prefer a noninteractive CLI or localhost-only server for automated adapters.

## Downloaded model (measured)

- Path: `/workspace/scratch/models/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`
- Actual complete file size: **1,117,320,768 bytes**.
- Computed SHA-256: **`cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`**, exactly matching official LFS metadata.
- The original single-stream download was slow. It was safely stopped with 251,658,240 prefix bytes retained. Eight HTTPS range requests returned verified `206 Content-Range` responses, downloaded the remainder in about 54 seconds, and were concatenated with the prefix. The entire file hash was checked before exposing the final `.gguf` path. This is a local file, not remote inference.

## Actual CPU inference smoke test (measured, not Fold7)

Command and complete logs are preserved in `/workspace/scratch/local-smoke-metrics.json`, `local-smoke-prompt.txt`, `local-smoke-stdout.txt` and `local-smoke-stderr.txt`. The runtime was explicitly CPU-only with 2 threads, 2,048 context, 128 maximum new tokens, temperature zero, noninteractive raw ChatML and no warmup.

- Exit code **0**, end-to-end subprocess wall time **2.741 seconds**, child process peak RSS **1,829,844 KiB (1.745 GiB)** measured with `getrusage(RUSAGE_CHILDREN)`.
- Runtime-reported prompt processing: **90.65 tokens/s** for 76 prompt tokens; decode: **35.25 tokens/s** for 22 runs. These are this tiny warmed-file-cache task's observations; they are not sustained device benchmarks.
- The model generated `def clamp(value, lower, upper): return max(lower, min(value, upper))`. Seven independent host-owned table cases passed, including outside values, both boundaries, an interior value, floats and equal bounds. Extracted emitted code and verifier result are saved as `local-smoke-extracted.py` and `local-smoke-verifier.json`.
- The model ignored the instruction to omit Markdown and emitted a Python fence. The CLI also appended `[end of text]`. Adapters need explicit output parsing/validation; naive execution of the entire stdout would fail. This is an observed limitation, not a speculative concern.
- This smoke test demonstrates actual local code generation and independent functional checks. It is **not** itself a repository patch experiment, and does not demonstrate mobile operation or repository-level autonomy.

## Primary sources fetched

All links below were requested directly. `raw/main` and `master` are live sources; local snapshots retain the fetched body. Two attempted guessed paths returned 404 (`/gemma/docs/gemma-4`, `/edge/litert-lm/models`), so the facts above use the successful canonical sources instead.

1. Qwen3-Coder-Next official model card: https://huggingface.co/Qwen/Qwen3-Coder-Next/raw/main/README.md
2. Chosen Qwen coder official card: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/raw/main/README.md
3. Chosen model artifact list, file sizes, SHA-256 LFS identities: https://huggingface.co/api/models/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/tree/main
4. Chosen model bytes: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf?download=true
5. Chosen model license: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/raw/main/LICENSE
6. Small coder card: https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/raw/main/README.md
7. Small coder artifact metadata: https://huggingface.co/api/models/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/tree/main
8. 3B coder official card: https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/raw/main/README.md
9. 3B custom license: https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/raw/main/LICENSE
10. Official Google Gemma 4 model card: https://huggingface.co/google/gemma-4-E2B-it/raw/main/README.md
11. Gemma 4 Apache license page: https://ai.google.dev/gemma/docs/gemma_4_license
12. Official Gemma E2B GGUF file sizes: https://huggingface.co/api/models/google/gemma-4-E2B-it-qat-q4_0-gguf/tree/main
13. LiteRT E2B artifact card and memory explanation: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/raw/main/README.md
14. Official llama.cpp README: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/README.md
15. Official llama.cpp license: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/LICENSE
16. Android/ARM64 guide: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/android.md
17. Build guide: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/build.md
18. Exact downloaded runtime release metadata: https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/b11861
19. Exact runtime bytes: https://github.com/ggml-org/llama.cpp/releases/download/b11861/llama-b11861-bin-ubuntu-x64.tar.gz
20. Official LiteRT-LM repository: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/README.md
21. Official LiteRT overview/model list/vendor performance tables: https://ai.google.dev/edge/litert-lm/overview
22. LiteRT `.litertlm` file format/build guide: https://ai.google.dev/edge/litert-lm/file_builder
23. Official LiteRT Gemma 4 guide: https://ai.google.dev/edge/litert-lm/models/gemma-4
24. Small ready Qwen3 LiteRT artifact card: https://huggingface.co/litert-community/Qwen3-0.6B/raw/main/README.md
25. Ready Qwen2.5 LiteRT artifact card: https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/raw/main/README.md
26. RepoCoder paper: https://arxiv.org/abs/2303.12570
27. ReAct paper: https://arxiv.org/abs/2210.03629
28. SWE-agent paper: https://arxiv.org/abs/2405.15793
29. Official SWE-bench evaluation repository: https://raw.githubusercontent.com/SWE-bench/SWE-bench/main/README.md

