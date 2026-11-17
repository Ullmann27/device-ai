# Independent7B admission freeze — 2026-11-03

Frozen before examining the runtime author's7B extension. Owner consent is recorded
at Chief5feb1a93ffe5db29163e33e4a0ea002ff25252fe. Approved ONLY official
Qwen/Qwen2.5-Coder-7B-Instruct-GGUF revision13fb94bfda8c8cf22497dc57b78f391a9acb426a,
qwen2.5-coder-7b-instruct-q4_k_m.gguf,4683073536bytes,
SHA256509287f78cb4d4cf6b3843734733b914b2c158e43e22a7f4bf5e963800894d3c;
same independently reproduced llama-completion binary
c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a.
Existing provisioned host only, at most8GiB for the model job, no paid resource,
cloud inference, additional model, device, install, signer or release authority.

S1. Independently check official revision metadata and full local file hash/size
with bounded-memory streaming. Recheck the known exact native executable and
preserved source/build provenance; no need to rebuild unchanged binary again.

S2. Large-file admission is narrow to the exact owner-approved7B tuple. Preserve
2GiB generic/model-unavailable safeguards and previous1.5B defaults. Wrong size,
wrong model hash, unknown runtime, forged manifest, symlink/hardlink or file swap
cannot use a caller's declared model name/hash/token to obtain expanded rights.
Candidate7B registry getter remains null until independent actual review.

S3. Genuine observation requires actual fixed trusted CLI/process, retained
model/runtime FDs, stable bytes/inodes, exact request/prompt/schema/output/candidate
correlation, reviewed tuple and private immutable brand. Runtime kind, boolean,
self-signed envelope and injected run cannot certify actual7B inference. Wrong
1.5B/7B pair mixing or stale nonce is rejected. Model identity stays fixed per task.

S4. Owner's maximum8GiB job budget must be respected during admission/hash AND
native load/generation. Distinguish hard per-process limits, actual sampled RSS,
and aggregate model-job containment. An8GiB native RLIMIT plus unbounded model-
reading parent is not an8GiB total-job guarantee. Capture enforced limit values,
actual executable-inode memory/CPU observations and failure behavior. No full-file
Node Buffer read for4.68GiB weights. Report missing cgroup delegation explicitly;
do not silently raise limits, disable isolation or treat low wrapper RSS as model
memory. Native threads/processes/context/output/time remain bounded.

S5. No remote model/network fallback, download during generation, or private
host/credential access. Approved GGUF is mounted read-only by retained FD;
read-only native runtime and trusted system dependencies only. Preserve namespace,
fork-denial, environment and fixture-network controls.

S6. Actual approved7B generation must terminate normally with native decoding
and a bounded proposal or explicitly authenticated invalid-model-response. Preserve
raw synthetic stdout/stderr, hashes, timings, resource observations and exit.
Use the prior frozen reviewer task/acceptance unchanged for a controlled comparison;
it is a consumed task, not a newly blind holdout. Do not read QA's12 tasks. Model
quality/repair correctness remains independent of model size or inference proof.

S7. Real7B cancellation/deadline and model-change rejection must return boundedly,
clean owned processes and never return a successful genuine observation. Approved
original weights remain untouched; use disposable copies or separately retained
read descriptors for mutation controls. Synthetic native swap/overflow fixtures
remain valid controls; if a heavy negative cannot safely fit8GiB, record NOT_RUN
and the reason instead of exceeding budget.

S8. Preserve1.5B genuine admission and process-only fixed-ELF counterexamples.
Two models must not allocate together or switch within one task. Run one heavy
native experiment at a time after UI render priority clears; coordinate with QA.
No speed comparison while another heavy workload runs, and no fake memory metric.

Decision: exact7B pair approval is scoped LOCAL_MODEL_HOST only after provenance,
actual bounded native execution and resource acceptance. End-to-end coding,
reflection/persistence/recall benefit, Android17/ARM64/Fold7 and release remain
separate. FOLD7_NOT_RUN. No feature production edits by challenger.
