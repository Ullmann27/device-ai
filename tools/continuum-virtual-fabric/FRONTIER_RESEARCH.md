# Continuum Frontier Compute — measured hypotheses and selected architecture

**Date:** 2026-10-31
**Research-only:** no changes to main, installed APK, owner keys, model weights, or live M8 coding-agent.
**Outcome:** 3000 logical specialist slots + proof-gated deterministic task reuse, with counterexamples.

## 1. Physical conservation constraint

A virtual process is a scheduler object. A thousand copies of a process never generate a thousand physical processors. Even a rented VM is backed by actual physical CPU/GPU hardware. Mining ASIC SHA-256 hash rates cannot directly run LLM tensor operations. Offers of free compute require real accounts, available quota, ownership permission and provider terms.

We can, however, execute **fewer physical operations to answer the same verified deterministic requests**, or use actual authorized remote capacity. That is the feasible route.

## 2. Hypothesis register (reject/accept)

| Plan | Physical capacity gained? | Potential improvement | Decision |
|---|---:|---|---|
| Clone 3000 virtual VMs on one processor | None | Only scheduling, typically overhead | Reject as compute generation |
| Reuse Bitcoin SHA-256 ASICs as CUDA GPUs | Not directly | Specialized hashing only | Reject for LLM |
| Idle mining *GPU* rigs with owner approval | Yes, when contributed | Actual off-device GPU work | Future node integration |
| Borrow volunteers' unapproved BOINC resources | No lawful entitlement | Scientific projects only | Reject |
| Always Free ARM CPU VM | Yes, if account/availability permits | Bounded real CPU work | Future opt-in node |
| Run long-lived distributed workers on Colab Free | Not permitted | Free managed compute restrictions | Reject |
| 3000 model weights in Fold7 RAM | No | Severe memory pressure | Reject |
| **Proof-Gated Compute Reuse** | No extra hardware | Avoid redundant pure task execution | **Implement/test** |
| Shared verified tool-state and versioned memory cache | No extra hardware | Reduce repeated preprocessing | Next research |
| vLLM-style prefix reuse / batching | No extra hardware | Reduce repeated prefill, boost throughput on supported runtime | Evaluate on actual inference backend |
| Adaptive small/large model routing | No extra hardware | Skip expensive models on easy tasks | Controlled A/B experiment |
| Speculative decoding | No extra hardware | Faster decode when draft acceptance high | Investigate supported runtime |
| Signed opt-in distributed nodes | Yes, only from real hardware | Scale actual throughput | After worker transport/attestation |

Prefix caching and continuous batching are prior art, documented by vLLM. Speculative decoding is prior art in llama.cpp. Our contribution is the combined integration and strict correctness gates in Continuum, **not** a claim that cache reuse was invented here.

## 3. Implemented prototype

- `expert-mesh.mjs`: 50 / 100 / 1000 / 2000 / **3000** logical specialist declarations across five roles. No phantom physical resource admitted.
- `reuse-broker.mjs`: in-flight coalescing, exact result reuse, tenant partition, model/input/parameters/policy digests, memory epochs, default-deny receipt verification, TTL, bounded LRU, waiter and active quotas, cancellation.
- `reuse-experiment.mjs`: genuine deterministic 12x12 matrix multiplication and SHA-256 output on Node; always compares optimized outputs byte-for-byte with the baseline.
- Existing `VirtualFabric` continues to represent real CPU/GPU/RAM/VRAM reservations. The new broker runs **before** scheduling so thousands of identical pure tasks become few actual capacity requests.

**Synthetic benchmark A:** 3000 submitted pure deterministic jobs, 30 unique inputs -> exactly **30 distinct computations**; 2970 avoided; 100x fewer underlying executions. This is a mathematical work-count reduction in that workload; NOT 100x more FLOPS or 100x faster real LLM inference.

**Counterexample B:** 3000 submitted jobs, 3000 unique inputs -> **3000 computations**; 0 avoided; no benefit beyond possible overhead. Generative or nondeterministic jobs are explicitly excluded from answer caching.

**Current test status:** 43/43 pure-JS unit assertions passed in an isolated V8 harness using minimal compatibility shims for Node test/assert/crypto/timing. Original Node `--test`, Android/Gradle and real Fold7 checks are **NOT_RUN**. V8 shim hashes are NOT production SHA-256; the actual source imports `node:crypto` and requires full Node validation. The in-memory receipt verifier in the benchmark merely checks digest integrity, not genuine remote execution; production uses default-deny unless a trusted attestation verifier is injected.

## 4. Reproduce with real Node

```sh
node --test tools/continuum-virtual-fabric/*.test.mjs
node tools/continuum-virtual-fabric/reuse-experiment.mjs
```

Run once with `distinct=30` and again with `distinct=3000`; do not quote speedups without CPU hardware, Node version, cold/warm runs, repetitions and p50/p95. Wall-clock numbers from a one-shot JS microbenchmark are not comparable to real LLM tokens/sec.

## 5. Connection to the active cognitive and agent architecture

**Do not create a second production scheduler.** Adapt this broker as a governed front-end to the *existing* Kotlin `ComputeNodeRegistry/ComputeScheduler/ComputeResultVerifier`, `AiContextCompiler`, `BoundedAgentOrchestrator`, `ToolGate`, `LocalModelRuntime`, EWM/SWB and M8 coding worker.

Intended path:

```text
Owner-approved development backlog / JARVIS / Coding Agent
  -> immutable task manifest (content digests + memory epoch + privacy + revision)
  -> proof-gated reuse broker
       -> verified cached result (if valid exact match)
       -> coalesced in-flight work (same tenant & deterministic)
       -> canonical ComputeFabric authorization -> actual CPU/GPU node
  -> signed result receipt + independent validation
  -> coding-agent regression tests / skill promotion gate
  -> private result store / versioned evidence
```

Hard boundaries:
1. Never reuse different tenants' private work, stale memories, different versions or nondeterministic outputs.
2. Never treat model-generated self-evaluation as an independently proven repair.
3. No public network endpoint, automatic provider calls, paid GPU usage or 24/7 deployment in this prototype.
4. A permanent agent requires a real reachable always-on host and actual opt-in permissions; Android background scheduling is opportunistic.
5. Release only after real model FAIL->PASS benchmark, compatible owner-signed APK and on-device QA.

## 6. Next falsifiable tests

- Against the *real* M8 coding-agent, record distinct input hashes from actual repeated repository reads, test executions and tool analyses. Quantify reuse rate on a representative sample; if almost all tasks are unique, abort this optimization for that lane.
- Evaluate prefix caching independently on supported inference engines: token-level correctness unchanged, latency and memory measured.
- Add signed worker receipts and durable cache invalidation across restarts before remote execution.
- Compare opportunistic CPU Always Free VM and authentic authorized GPU nodes under zero-cost policies. Verify cloud terms and exact entitlements per provider before provisioning.

Sources:
- vLLM prefix caching: https://docs.vllm.ai/en/latest/design/prefix_caching/
- vLLM optimization features: https://docs.vllm.ai/en/latest/
- llama.cpp speculative decoding: https://github.com/ggml-org/llama.cpp/blob/master/docs/speculative.md
- Oracle Always Free: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Colab Free restrictions: https://research.google.com/colaboratory/faq.html


## 7. More promising practical experiment: distinct jobs sharing heavy intermediate stages

`shared-stage-experiment.mjs` addresses a harder question than repeated *identical* tasks:
- 3,000 **different final jobs**, each with a unique final result;
- each job depends on one of 30 source versions;
- expensive shared preprocessing is a deterministic 12×12 matrix kernel standing in for a real expensive repo parse, embedding or dependency scan;
- every job still performs its own unique final transformation;
- optimized and independent baseline final outputs are checked equal.

**Observed in the executed V8 logic tests:**
- 3,000 distinct outputs; only **30** costly shared-stage kernels, 2,970 duplicate heavy preprocesses avoided.
- Counterexample: 3,000 distinct source versions -> 3,000 heavy kernels, **zero** reuse benefit.
- This does **not** imply 100× end-to-end wall-clock speedup: each final transformation, hashing, admission and memory traffic still cost time.
- Pure-JS research suite now **43/43 PASS in isolated V8 with test/crypto shims**. Original `node --test` and all original device/LLM tests still **NOT_RUN**.

Run in a genuine Node.js 20+ environment:
```sh
node --test tools/continuum-virtual-fabric/*.test.mjs
node tools/continuum-virtual-fabric/shared-stage-experiment.mjs
```

**Engineering thesis:** The M8 coding-agent may repeatedly parse the same Kotlin/Java repository files and evaluate similar diagnostics while seeking different repairs. We should share **verified immutable intermediate results** when revision and privacy context match, then use unique final reasoning per task. This is the next plausible way to reduce real inference/tool cost. Measure distinct hashes and end-to-end latency before claiming gain, and never treat a cached *answer* as a verified code fix.

This is a project-specific integration of established memoization/prefix-cache principles, not proof of unprecedented mathematics or a newly generated computing resource.


### Additional independent Node.js arithmetic sanity check

On 2026-10-31, a **standalone local reproduction** of the same 12×12 matrix and per-task SHA-256 formulas ran under **Node.js v22.16.0** on the available test host (NOT on the Fold7, NOT the original repository's `node --test` suite). One-shot measurements:

| Scenario | Baseline heavy kernels | Reused heavy kernels | Base wall-clock | Reuse wall-clock |
|---|---:|---:|---:|---:|
| 3,000 final jobs / 30 source versions | 3,000 | 30 | 120.064 ms | 22.025 ms |
| 3,000 final jobs / 3,000 source versions | 3,000 | 3,000 | 116.838 ms | 124.851 ms |

All 3,000 final SHA-256 outputs compared equal in each scenario. These values are from **one non-controlled run of a standalone reproduction**, not a statistically valid benchmark, not the complete in-repository broker, and not a measurement of neural inference. Node CPU timing can change considerably between devices and runs.

Before claiming real Continuum speedup, benchmark **the unchanged installed model** on both versions with A/B toggles, realistic task distribution, repeated warm/cold runs, energy/thermal limits, and independent correctness tests.
