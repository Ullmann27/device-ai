# Continuum — Als der Junge den Kreislauf schloss / ARGON-4 Rechenforschung

STATUS: OWNER-CONSTRAINT / RESEARCH_ONLY / UNPROVEN (2026-11-08). Never merge into Code 50 while APK release QA is in progress.

## Guiding hypothesis
Build a sovereign local intelligence loop on a single Android device without mandatory datacenter, dedicated GPU, external cloud API or powerful PC. Later, if additional users voluntarily participate, coordinate real, independently verified work from their devices. This is inspired by the owner's reference to the book “Als der Junge den Kreislauf schloss”. The complete original book text and the exact physical mechanism have not been provided here; no fictional quotations or mechanisms may be attributed to it.

Physical reality: computation needs hardware and energy. We aim to **avoid redundant work, reuse exact deterministic intermediates, route sparse logical experts, time-slice limited CPU capacity and optionally distribute eligible tasks**, improving verified problem solving per real resource unit. Never describe virtual experts as physical GPUs or mathematical models of memory/bandwidth as actual hardware capacity.

## Existing research code — reuse, do not duplicate
- ARGON 4 PR #240, F1/F2/F3: F2 includes synthetic 500-GB data transfer/streaming and expert-cache simulations with 10/10 reported Python tests. No device memory expansion, real GPU-free LLM throughput or speedup was measured.
  https://github.com/Ullmann27/device-ai/pull/240
- PR #242 connected selected F1/F3 as bounded SHADOW components. F2 remains inactive until actual hardware evidence; no automatic promotion.
  https://github.com/Ullmann27/device-ai/pull/242
- PR #287 has 50–3,000 logical experts and strict same-input reuse. Its controlled experiment shared 30 expensive deterministic stages among 3,000 final tasks: 30 stage executions in the repeated-work case, 3,000 for the all-distinct negative control. 43/43 isolated JS shim tests are reported; original full runtime, real device and distributed inference NOT_RUN.
  https://github.com/Ullmann27/device-ai/pull/287
- Anchor sources: tools/continuum-virtual-fabric/expert-mesh.mjs, fabric.mjs, reuse-broker.mjs, shared-stage-experiment.mjs; existing ComputeNodeRegistry, ToolGate, ComputeResultVerifier, AiContextCompiler and CCC1 evidence policy stay authoritative.
- PR #635's separate HypothesisWorkspace order-dependence pilot is not a physical compute-speedup proof.

## Experiments in order
C0. First-device sovereignty: run offline-only tasks on fixed CPU/energy/RAM budgets; record answer quality and latency. No other device or PC required.
C1. Exact-reuse broker: compare 3,000 tasks/30 shared stages against a 3,000-unique negative control, with stable digest keys binding model revision, tenant, input, parameters, policy and memory epoch. Record CPU milliseconds, wall-time and cache bytes; measure miss and invalidation correctness.
C2. Sparse logical expert routing: compare fixed real CPU budgets for one policy, 50 experts and 3,000 logical experts without multiplying model instances. Report useful verified successes, scheduling overhead and energy; prove whether routing actually helps.
C3. Time division / phase-shift scheduling: controlled time slices on one CPU across 1/2/4/8 simulated workers. Compare queue fairness, STOP latency, thermal adaptations and end-to-end time; a time slice is not extra parallel hardware.
C4. ARGON F2 virtual storage/streaming: test bounded physical cache, chunking, model states and 500-GB *logical* address-space scenarios only if measurable. Report actual RAM/PSS, bytes moved, read latency and failures, never imply installed 500-GB memory.
C5. Multi-user optional fabric: start with isolated synthetic peers, then consenting real devices if available. Authenticate work packets, protect data per user and encrypt/verify results, bound quotas and audit cancellation. Negative cases include forged receipts, replay, stale policy epoch, unrelated results and malicious nodes. User prompts, private memory, owner files and model weights must not leave the device by default.
C6. Collectively useful learning: compare model/task quality versus local-only baseline with frozen holdouts and real energy/network costs. More devices alone is not evidence of smarter reasoning; no silent learning from third-party data.

## Nonnegotiable constraints
No centralized coordinator requirement. No GPU, PC or datacenter purchase gate. No hidden CPU/battery contributions. No unauthorized cross-user data exchange or tool execution. Device AI Continuum must remain functional as one-user offline assistant when all peers disconnect. All functionality is opt-in and fail-closed. Source patches and test evidence stay on independent research PRs; Sonnet's Code 50 release branches (#603/#630) and owner signing are untouched. Real FLOPS, model quality gains and network speedups are NOT_YET_PROVEN.

## Falsifiability
Preregister each test's source SHA, frozen tasks, negative control, reproducible seed, physical CPU quota, memory budget and pass/fail metrics. Measure real latency, energy and memory on the hardware actually used, not on virtual labels. Compare against a one-device CPU baseline and independent correctness checks. Use privacy-safe synthetic fixtures until owner consent and real participants exist.
