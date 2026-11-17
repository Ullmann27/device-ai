# Sovereign Brain IV — live handoff

Parent PR357 HEAD `d49031843b03af6ce21427a41e761d671aa05b58`, freshly checked against GitHub. PR354 `c3697ecfc5dd800bddba816cab937e11bdfd8269`, PR356 `d85810508744ce61a9092a1ac531fc4fdf5e843c` remain separate. No old S1–S3 work repeated. New isolated worktree/branch `research/continuum-sovereign-brain-iv-20261102`; main unchanged.

## Actual authorization

The owner explicitly instructed this session: “CONTINUUM Ω – OPERATION SOVEREIGN BRAIN IV”, start PR357/d4903184, implement A/B/C and S4/S5/S6, use an isolated draft branch or conflict-free357 continuation, no main merge, owner APK/signing, personal-data or productive-weight changes. The primary authorization is the owner message in this conversation, not a fabricated GitHub approval. A faithful scope record is stored here; no independently verified GitHub owner-comment URL exists for that chat message. This authorizes implementation/testing/commit/push/draft and fresh independent review; it does NOT authorize release, weights promotion or new OS rights.

## Initial findings and review status

Three Gitar threads are still open. Current final357 retains duplicated raw/compiled requests and incompatible budgets, the frozen corpus has72 rows/54 unique cases, and prior command strings contain placeholders. Initial aabd8ea7/babba332 source repairs are inherited; they are not fresh review approval. Independent read-only baseline review requested; no GitHub thread marked resolved, no Changes-requested claim dismissed by own tests.

## Concrete plan / entry points

1. A: factor the actual Activity prompt preparation into one pure Kotlin `LocalChatPromptAssembler`; budget complete header/task/context/observations/tail jointly, preserve UTF8 and character bounds, return explicit clarification on mandatory overflow, no duplicate task. Existing compiler and privacy gates retained. RED/GREEN on actual shared assembler for long German/CJK/emoji tasks, full memory and contradictions. Add separate72/54 report without changing frozen JSON/SHA.
2. B/S4: a cognitive request boundary over EXISTING `LocalModelRuntime`, task/evidence/policy binding and untrusted output/proposals. Wire main/advisory inference through it; preserve LiteRT/Gemma and no PR356 file edits. Two host-only replaceable runtime adapters exercise identical rules, revocation/identity/cancel/deadline negatives. No real GGUF/Fold7 switch claim.
3. C/S6: existing `ReadOnlyToolPlan`/BoundedAgent/ToolGate/AgentTaskEvidence: real multi-step storage+authorized-file-listing task in disposable host scope, separate independent verification, receipt replay/restart/cancel/timeout/parallel controls. Investigate baseline start race and oversized results before fixes. ExperienceCompiler proposals remain owner review only, CCC1 unchanged.
4. S5: inspect frozen public research CDX checkpoint and source manifest (not the unavailable audited Fold7 checkpoint). Freeze new authored evaluation before any experiment; teacher-forced/rollout, zero/shuffled/role conditioning, copy/EOS/capacity limits, negative results. No productive checkpoint/weights reads or writes. Keep historical0/20 device failure distinct.

## Verification setup and exact existing commands

Workspace currently supplies Kotlin2.4/JDK21 compiler module, Android-all15, JUnit4.13.2/Hamcrest and the existing compile-only LiteRT stub in `/workspace/continuum-toolchain`; SHA provenance in parent's evidence. Exact full host command:

```bash
PATH=/workspace/continuum-toolchain:$PATH CONTINUUM_ANDROID_DEPS=/workspace/continuum-toolchain CONTINUUM_JUNIT_JARS=/workspace/continuum-toolchain/junit.jar:/workspace/continuum-toolchain/hamcrest.jar CONTINUUM_KOTLIN_STDLIB=/workspace/continuum-toolchain/stdlib.jar bash scripts/android-kotlin-unit-check.sh /workspace/brain4-full-host
node scripts/continuum-reliability-qa.mjs critical --out /workspace/brain4-critical
node scripts/continuum-reliability-qa.mjs full-node --out /workspace/brain4-full-node
```

These are actual current environment paths, not portable-toolchain availability promises. Raw executed argv, source manifests and new results will be saved below `docs/evidence/sovereign-brain-iv-20261102/`. Baseline cached classes `/workspace/sovereign-reviewed-full/main` are only for fast RED reproduction; final verification must recompile all changed sources. Native Gradle/LiteRT/device NOT_RUN unless actually executed. Start at `AgentActivity.runLocal`, `AiContextCompiler.compile`, `LocalModelReasoningAdapter.execute`, `ReadOnlyToolPlan.generate/evaluate`, `ToolGate.execute`.

## Ledger

- [x] Fresh heads, AGENTS and original final357 evidence/review threads read; baseline review requested.
- [ ] A RED/GREEN +72/54 split; exact commit/logs.
- [ ] B model-neutral production-call integration +two simulated runtime adapter controls.
- [ ] C real multi-step task, independent verification and concurrency/recovery negatives.
- [ ] S5 immutable checkpoint diagnostic/new cases; all negative results retained.
- [ ] Fresh full relevant suites and independent final-source review; no own-tests-only closure.

All NEW functionality NOT_RUN at this initial checkpoint. Rollback: review/revert only new branch commits; no reset/shared-branch mutation. Keep one brain/memory/worker/model host and existing CCC1 contracts.

## Owner stop / handoff ledger

Owner explicitly requested immediate clean finish/commit/push on the reported15% quota. Task scope reduced to the actual audit checkpoint; no product implementation started. Completed: isolated branch/Draft358, fresh GitHub/open-review audit, independent host bug repro and72/54 reanalysis. A/B/C/S4–S6 remain NOT_IMPLEMENTED/NOT_RUN. Continue from the current handoff, never restart oldS1–S3 or infer approval from parent PASS.
