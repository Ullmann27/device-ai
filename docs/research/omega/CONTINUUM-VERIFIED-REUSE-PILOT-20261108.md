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
