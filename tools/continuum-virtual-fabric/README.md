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


## 50/100 specialist mesh and real CPU smoke

The new `expert-mesh.mjs` declares 50 or 100 **logical** specialists, evenly divided among coding, memory, reasoning, research and NeMo training. They share **the same** bounded `VirtualFabric` reservations; each known component has a quota, and NeMo jobs wait until a real GPU with sufficient VRAM is registered. The component identity in this prototype is a test fixture, **not authenticated**: never put it on a public endpoint or treat it as a production tool grant.

```sh
node --test tools/continuum-virtual-fabric/*.test.mjs
node tools/continuum-virtual-fabric/cpu-smoke.mjs
```

The CPU smoke runs 100 **actual bounded deterministic 8×8 matrix multiplications**, calculates SHA-256 checksums, and completes their reservations sequentially using one local CPU slot. This demonstrates dispatch/accounting and real CPU work, **not** 100 independently running models, LLM generation, a neural network, novel compute capacity, or multiplicative acceleration. Current `complete()` is unauthenticated and in-process only. Never use it for customer billing.

### Coding-agent integration: next required actions

1. Use existing M8 `coding-agent-loop.mjs`, `coding-orchestrator.mjs`, `DeviceTeamleaderScheduler`, local model path and Kotlin `ComputeFabric.kt` as the canonical worker pathway. The JS mesh is a research simulation, not a second permanent scheduler.
2. Add authenticated `ComputeTask`->worker RPC only after owner approval, provenance, signed result receipts and network privacy policy are implemented. No hidden model download or off-device exfiltration.
3. Let Coding Agent submit low-risk read-only analysis, test and benchmark tasks; allow write/commit only through existing approvals and isolated executor. Reserve capacity by REAL host CPU/GPU metrics.
4. Permit Android WorkManager to periodically discover pending jobs (15-minute minimum, not exact). Foreground work requires appropriate explicit notification and lifecycle. A 24/7 autonomous service is impossible to guarantee on a sleeping/terminated phone without an actual permitted always-on host.
5. Add secure multi-device enrollments and persistent queue before adding actual concurrent nodes; validate measured throughput (jobs/s, tokens/s), p95 latency, RAM/VRAM, thermal and power use.
6. Only ship after fully verified Gradle, Kotlin/Node and Fold7 regression tests. Do not touch original owner-signing keys or model weights in this research branch.

**Project acceptance:** an independent new coding defect must be identified, fixed by a real model, and red-to-green verified on fresh tests before describing M8 as autonomous repair. The current demo does not satisfy that goal.


## 31.10.2026 — CONTINUUM JARVIS / Kotlin research-brain connection

**Entry points for every agent:** [latest canonical status](../../docs/CONTINUUM-CURRENT-STATE-20261031.md), [handoff](../../docs/handoff/CONTINUUM_FRONTIER_COMPUTE_20261031.md), [APK comparison/test checklist](../../docs/release/CONTINUUM-APK-COMPARISON-20261031.md), Root `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`.

A new Kotlin **research-only** `cognitive/ContinuumComputeReuseResearch.kt` directly calls the current `AiContextCompiler.compile` in `compileObserved(input)`. It measures the SHA-256 of the compiled context and its selected memory/source counts, and allows independently verified, pure intermediate calculation results to be stored **in a bounded in-memory cache** partitioned by owner ID, context digest, model revision, policy revision, memory epoch and stage. Return buffers are copied; TTL and bounded LRU reject stale or over-capacity entries. Verification is **default deny** and `putVerified` requires a trusted callback. Eight focused JUnit test methods were added in `ContinuumComputeReuseResearchTest.kt` covering rejection, isolation, TTL, aliases, revisions and eviction.

**Do not misrepresent this:** It is a callable adapter for the real existing compiler, **not wired into live `AgentActivity`**, not disk persistence, not remote LLM inference, not actual measured app acceleration. The eight new Kotlin/JUnit tests have **not yet been executed on the original Gradle Android project**. Running the full Gradle build, device memory migration and end-to-end app comparison remains a strict release gate.

**Prioritize real benefit:** instrument actual read-only M8 development/brain/tool tasks for file/source reuse, independently validate against baseline, then choose whether a durable encrypted/private cache and a carefully controlled ON switch are worth adding. The existing owners' model/memory store and signed APK must remain unchanged until independently verified.

Standalone source-level Node prototype tests:
```bash
node --test tools/continuum-virtual-fabric/*.test.mjs
node tools/continuum-virtual-fabric/reuse-experiment.mjs
node tools/continuum-virtual-fabric/shared-stage-experiment.mjs
```
