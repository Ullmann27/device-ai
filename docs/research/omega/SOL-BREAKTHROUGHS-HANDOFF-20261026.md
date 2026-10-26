# SOL five-core-problem continuation — 2026-10-26

Base: PR150 actual remote HEAD `ad14f8b82f9be2cd0026bc16eef71a16bec41424`; main unchanged190f5eab. Owner requests autonomous implementation, independent parallel investigations and regular pushed checkpoints. This is an explicit authorization to make routine design choices and implement on review branches, not to merge main, alter owner weights, install/sign owner releases or buy compute.

## Design / dependency order

1. CDX1: inspect gradients, conditioning, causal context and teacher-forced/free-running mismatch; compare controlled isolated candidates using real generation. Preserve all old formats/checkpoints. A larger context alone and a corrected recurrent/attention candidate are alternatives to measure, not presumed fixes. New architecture stays research-only until independent test gains.
2. Gemma/main dialogue: use existing LiteRT adapter and actual selected-model SHA contracts; diagnose which intent/evidence branches suppress generation. Add bounded context/reasoning integration and explicit error fallback through existing contracts. Verify official Android runtime compatibility before a version change. Host inference is not Fold7 acceptance. Keep Qwen.
3. Grounding/autonomy: repair proven remaining identifier/entity/relation errors without enabling failed CTX3/LM1. Reuse canonical job/backlog, proof and retry mechanisms; persistence/strategy changes must be bounded and evidence-bound.
4. Coding: connect a smallest existing typed task→generator→canonical builder/repair→independent artifact verification slice. Check actual OS isolation capability; fail closed for unknown executable source if absent. Host is optional proof, not an on-device claim. No second app/backlog/executor.
5. DeepSeek: extend actual tier-plan/provider interfaces, authenticated model identity, bounded requests and privacy. Separate executable protocol tests from genuine model inference; no GPU is currently allocated. Capacity and cached-shard calculations must expose assumptions, not equate storage to compute.

Existing research and toolchain are preferred over introducing frameworks. Technically independent investigations use distinct official branches/worktrees and Draft PRs. Heavy model/build jobs are scheduled to avoid host resource contention. Independent evaluations are frozen before candidate predictions; already consumed benchmarks remain regression/dev evidence. Any failed hypothesis and raw output is preserved.

## Verification / gates

For each source change: reproduce a behavioral failure, make the smallest justified repair, compile, run focused regression and independent benchmarks where applicable, then architecture/security review. Full appropriate Node/Android/backend checks after integration. Do not call mocked bridge output genuine model inference or generated code tested until a real compiler/test ran. Record exact SHA, command, tool/runtime/model identity, pass/fail/skip and device limits. Preserve personal facts/checkpoints and production gates.

## Status

START_CHECKPOINT / UNVERIFIED_NEW_WORK. No new implementation yet. Previous full and focused results remain at their original SHAs under `docs/evidence/sol-infrastructure-20261025` and `docs/evidence/sol-brain-20261025`.

Next: independent CDX diagnosis/alternatives, exact remaining memory eligibility failures, existing typed coding sandbox slice and DeepSeek bridge; root owns actual main-dialogue/Gemma integration and combined verification. Rollback by reverting individual new commits on a new review branch. No source branch is force-updated or closed.
