# Continuum Virtual Compute Fabric & NeMo 26.10 (research; 2026-10-31)

**Status:** Isolated prototype only. NOT wired into Android, NOT a remote execution service, NOT an active compute marketplace. It does not generate new computing power or run an actual model. The existing Continuum APK, research neuron paths, and main branch are unchanged.

## Reproducible checks

From the repository root, on Node.js 20+:

```bash
node --test tools/continuum-virtual-fabric/fabric.test.mjs
node tools/continuum-virtual-fabric/nemo-smoke.mjs --dry-run
```

The scheduler simulates **100 logical model instances** against **one actual GPU slot**. The correct capacity accounting outcome is **one running reservation and 99 queued**. This is not 100x GPU throughput. A worker is a scheduled logical unit, not a magically new GPU. We intentionally block overselling CPU/GPU slots, RAM, VRAM, expired leases, excessive tenant concurrency and unapproved commercial placements.

The launcher builds the **official NVIDIA 26.10.00 Docker command** for the published 10-step Qwen3-0.6B HellaSwag smoke recipe. It defaults to **dry-run** and does not download anything. For a host with **two compatible, authorized NVIDIA GPUs**, Docker and NVIDIA Container Toolkit, explicit execution requires:

```bash
CONTINUUM_NEMO_EXECUTE=YES node tools/continuum-virtual-fabric/nemo-smoke.mjs --execute
```

Set `CONTINUUM_CHECKPOINTS` to an absolute directory if preferred. The official recipe uses two GPUs; do not assume this tutorial executes unmodified on a one-GPU free notebook. The Docker container may download large datasets/model weights and may incur provider/storage/network costs on paid equipment.

## Integration blueprint

1. Keep the **full NeMo AutoModel framework** on a real Linux/CUDA node; Android remains a local inference/controller client.
2. Reuse the existing Kotlin `ComputeFabric.kt` authorization, `ComputeNodeRegistry`, `ComputeScheduler`, `ComputeResultVerifier`, `ToolGate` and M8 approval rather than invent a second trusted scheduler. The JavaScript prototype is for resource-scheduling research, not a substitute for Kotlin production code.
3. Define a signed training-job manifest with exact pinned container digest, model and data license, source provenance, data hashes, recipe, GPU/memory limits, cost cap, checkpoint location and result signatures.
4. Implement an opt-in remote transport and a worker that checks manifest validity before any off-device work. Never send private memories or secrets without independent owner consent and data minimization.
5. Provide resumable checkpoints, preemption handling, worker heartbeats, deadline enforcement, immutable result artifacts, signed receipts, replay prevention and rollback. The in-memory JavaScript simulation does **none** of these.
6. Verify the real GPU training run before evaluating LoRA, quantization and export. Import only models in formats supported by the existing Fold7 local inference runtime.
7. For eventual SaaS commercialization: only provision legally owned/rented capacity with **express resale rights**. Add authenticated tenants, resource isolation, signed billable metering, fair pricing, rate limits, invoices, consumer protection and GDPR compliance, plus measured unit economics.

## Available temporary resources: no free commercial cluster

- Colab Free: Google limits GPUs and **disallows distributed computing workers on free managed runtimes**. Do not use it as a resold service or circumvent limits with multiple accounts. Permitted interactive research can still be valuable.
- Kaggle notebooks: limited and variable GPU access for individual experiments. Do not assume guaranteed uptime or resale permission.
- Hugging Face ZeroGPU: primarily short, quota-bound Gradio workloads; **not a drop-in multi-hour NeMo training cloud**.
- Commercial host: needs genuine capacity and permission to resell. Creating 100 logical slots while having one GPU creates a queue, not 100 GPUs.

## Reliability and truthfulness

`registerNode` in this prototype accepts a caller-supplied manifest; it does not attest hardware ownership, authorization or vendor terms. Likewise, `complete` can be called by a local test: it is NOT a signed proof of executed work or a trustworthy billing meter. A commercial production release must reject these trust shortcuts.

The prototype is in-memory, single-process, cooperative about timeouts, and has no remote runtime, durable leases or secure transport. No training benchmark, Android APK build, device test, GPU execution, speedup, or revenue is claimed. Full build/merge remains blocked on real same-tree tests and device-owner acceptance.

Sources:
- NVIDIA 26.10: https://docs.nvidia.com/nemo/automodel/v0.7/whats-new/release-notes
- Official NeMo README/recipe: https://github.com/NVIDIA-NeMo/Automodel
- Colab restrictions: https://research.google.com/colaboratory/faq.html
- Kaggle notebooks: https://www.kaggle.com/docs/notebooks
- ZeroGPU: https://huggingface.co/docs/hub/spaces-zerogpu
