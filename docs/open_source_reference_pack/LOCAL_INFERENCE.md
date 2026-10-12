# Local Inference Adoption Plan

## Primary: LiteRT-LM
Create a provider-neutral `LocalModelRuntime` and isolated `LiteRtLmRuntime`.

Required capabilities:
- load/unload local model;
- expose metadata/capabilities;
- create/reset conversation;
- stream output with Kotlin Flow/callback abstraction;
- cancel generation;
- expose token/context/memory/latency metrics;
- accept structured tool definitions when supported;
- parse tool-call output as data without executing tools;
- choose CPU/GPU/NPU by capability/operator policy;
- fail closed and recover safely.

Use Google AI Edge Gallery for model catalog/import/download/lifecycle patterns. Model downloads are operator-controlled, checksum validated and stored separately from identity/memory.

## Secondary: llama.cpp
After the runtime interface is stable, add a GGUF backend experiment to prove backend interchangeability and benchmark CPU/Vulkan/OpenCL/other supported mobile paths.

## Alternate: ExecuTorch
Evaluate after the first working local path, especially for models/export flows that materially outperform the primary backend.

## Acceptance
Inference must require no network once model bytes exist locally. Invalid model bytes are rejected. Tool calls never bypass Device AI's ToolRegistry/risk gate.
