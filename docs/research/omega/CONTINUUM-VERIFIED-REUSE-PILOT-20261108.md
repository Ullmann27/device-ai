# H3: measure verification-inclusive compute reuse

Status before execution: **UNVERIFIED / RESEARCH_SHADOW**. Branch
`research/continuum-verified-reuse-pilot-20261108`, stacked on H2
`9b8bf1d947323cecc75d6dd7dd2c1da8e73870b3` / #648.
The existing broker and kernel are unchanged Code50 source, originating in
#287. Native existing regression tests now actually ran: **47/47 PASS**.

## What this trial adds to ARGON #287/#635

Previous receipts check payload hash integrity, not mathematical correctness.
The new experimental admission verifier independently recomputes each cache
miss with a different matrix implementation, checking exact input binding and
output equality. Its CPU cost is **inside** the measured reuse path. Neither
local hashes nor this synthetic verifier authenticate a remote worker.

Frozen configuration: `tools/continuum-cognitive-pilots/reuse-protocol.json`.
Four workloads, 1,000 requests each: repeated expensive work (10 distinct
keys, 32x32), unique expensive keys, repeated cheap work (1x1), and epoch
changes every 20 requests. Six paired fresh-process measurements in alternating
order; 64 warmup requests, Node heap limit 128 MiB. No worker/network/GPU.

Three arms: direct existing kernel; same kernel through the **existing**
broker with independent verification; no-reuse ablation forcing a distinct
epoch every request while keeping the verifier. Validate every final result
against the independent oracle outside the timed section, identically in all
arms. Report that audit's time separately. Any wrong result fails the trial.

Hypothesis: reuse saves measured wall **and** CPU time on repeated expensive
work even with verification. Accept only if medians improve by >=10% and each
of six pairs improves both, exact results hold and all negative tests pass.
No benefit is predicted for unique or cheap work; retain every negative result.
Churn should invalidate reuse correctly and may erase the benefit.

Do not count "avoided kernel calls" alone as a speed or energy measurement.
Report production-kernel and verifier calls separately, actual wall/CPU,
heap/RSS before and after, process high-water RSS, and retained payload bytes.
Heap differences are not allocation counts; RSS includes runtime/warmup/audit.
Energy is unavailable, not zero. Matrix seeds are distinct input identities;
the original modular kernel is periodic, so they are not 1,000 mathematically
independent tasks. Final request IDs distinguish the requested outputs.

This is deliberately a bounded replication/counterexample experiment, not a
new universal compute algorithm. No cache or scheduler is wired into the
live Android chat, no trainable parameters or owner data are changed.

## Executed result

Draft #649. Protocol commit:
`90e3afdd0f39fd7cc800d1e22f0ca07d839c4c3a`.
Measured tracked source:
`64b9c6d7ae0f968c1f7d34f0ee5941bdbe9cfe5e`.
Host: Linux x64, Node v26.12.1. No source changes during measurements.
The raw working-tree record reports an untracked Python `__pycache__` from
syntax validation; it was not an input to any Node worker and was subsequently
removed. This was not a completely clean checkout, despite unchanged tracked
sources; the report retains that fact.

```sh
node --test tools/continuum-virtual-fabric/*.test.mjs \
  tools/continuum-cognitive-pilots/verified-reuse-pilot.test.mjs
node tools/continuum-cognitive-pilots/verified-reuse-pilot.mjs /tmp/h3.json
```

**55 PASS / 0 FAIL / 0 SKIP**: 47 existing tests and 8 new independent-oracle,
forged-result, binding, scope, revision, repetition, uniqueness and bounds tests.
The integrity-only control accepts an incorrect self-checksummed result; the
new mathematical verifier rejects it. This is not a change to the broker's
default-deny policy, and is not remote worker authentication.

All **72,000 final results** (4 workloads x 6 pairs x 3 arms x 1,000) match the
independent oracle. Raw samples, source/config hashes, memory observations,
per-arm audits and no-reuse ablation are in
`docs/evidence/cognitive-pilots-20261108/compute/comparison.json`.

| Workload | Direct wall / CPU median | Verified reuse wall / CPU median | Paired wins (both) | >=10% gate |
|---|---:|---:|---:|---|
| Repeated expensive | 207.777 / 212.089 ms | 17.728 / 25.454 ms | 6/6 | PASS |
| Unique expensive | 207.301 / 211.500 ms | 353.752 / 372.279 ms | 0/6 | FAIL |
| Repeated cheap | 5.457 / 8.688 ms | 9.435 / 14.209 ms | 0/6 | FAIL |
| Epoch churn | 210.504 / 215.988 ms | 192.244 / 210.856 ms | 5/6 | FAIL |

Repeated-expensive savings: **91.5% wall / 88.0% CPU** in this synthetic kernel.
There are 10 kernel calls **plus 10 verifier calls**, not free verification.
With reuse disabled, that workload requires 1,000 + 1,000 calls and measures
358.632 ms wall / 373.226 ms CPU. The no-reuse ablation retains the same verifier.
Peak process RSS for repeated-expensive: direct 64,204 KiB vs reuse 66,424 KiB;
640 UTF-8 payload bytes retained, excluding keys/maps/runtime overhead.

**Falsified generalization:** avoiding 990/1,000 production-kernel calls does
not guarantee faster computation: cheap repeated work is about 73% slower in
wall time. Unique work is about 71% slower. Churn fails the preregistered
benefit gate despite 500 cache hits. No thresholds were changed after seeing
these results. No attempt is made to promote the unfavorable workloads.

**SKIP:** energy/Joule, thermal pressure, Android/Termux, real model inference,
actual source-analysis workloads, real consenting peers. CPU time is not an
energy substitute; no GPU/RAM capacity or universal speedup was created.

Next falsifiable trial: a preregistered admission policy based on measured
stage cost and revision-local repeat rate should recover >=10% CPU savings
on a held-out mixed workload while adding <=5% overhead to unique/cheap work.
Validate on read-only real repository-analysis stages before any canonical
Kotlin ComputeFabric adapter or phone activation. Keep ToolGate, consent,
ComputeNodeRegistry and ComputeResultVerifier authoritative.
