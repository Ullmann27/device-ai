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


## First main-dialogue repair / pre-inference freeze

Two independently reproduced plumbing failures: (1) every grounded recall suppresses an explicit why/compare/analyse task; (2) original requests longer than512 characters are replaced by the memory engine's512-character input before model inference, deleting later instructions. RED logs archived. The bounded policy now retains exact direct recall, allows explicit synthesis through the existing local agent, preserves original long model requests, carries retrieval as labelled JSON data and falls back to the exact original recall on failed synthesis. Model conclusions grant no fact/tool/write authority. Nine focused JVM checks PASS; actual AgentActivity SDK compile is still PENDING. Task-intent detection is an explicit lexical rule, not neural semantics. Qwen retained.

Independent evaluator authored20 previously unused synthetic German cases without predictions/candidate-code inspection; fixture SHAea8f83cb45765a9034fd777954e4e62a36fd35647c41d9922bbe2fe0b0d841a7, committed BEFORE inference. Runtime matrix is predeclared: selected GemmaAPI0.18.1 and0.18.0, public Qwen3-0.6B reference0.18.0, same4096context/256output/2CPUthreads/greedy/explicit thinking-off. Reference Qwen actual original SHA is separately pinned; installed Fold7 Qwen identity is unknown. Model acceptance/results are NOT_RUN at this checkpoint.

Official Google Maven metadata confirms Android0.18.1 exists. Official release notes identify its patch as an EmbeddingGemma2 source-build ABI fix; Kotlin API sources do not change between0.18.0/0.18.1. Do not invent a Gemma4-specific bug or infer device compatibility from host version alone. Android remains0.18.0 pending actual evidence. Official PyPI0.18.0 wheel checksum verified and isolated host toolchain installed; comparison will execute actual selected bytes. Existing0.18.1 smoke results remain separate.
