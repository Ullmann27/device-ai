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


## OWNER STOP — secure continuation at reported20%usage

Status: **STOPPED / PUSHED_REVIEW_BRANCHES / NO_NEW_EXPERIMENTS**. Owner requested careful commit/push and branch-local work protocols before ending. No new native/Gradle/full-suite/research test was started after stop. No source from sibling branches was integrated without independent review. All work remains in the official private repository, accessible to authorized developers.

### Exact branch checkpoints

| PR / branch | Pushed final checkpoint | Work protocol and actual boundary |
|---|---|---|
|151 `sol/continuum-brain-breakthroughs-20261026` | Executable freeze `cf96c19fb22c5a3b1b06f259c3b2e92866077c07`; later closure is evidence/register/docs only | THIS file + `docs/evidence/sol-breakthroughs-20261026/README.md`. Main-dialogue long-request/synthesis repair:9focused JVM pass; actual AgentActivity SDK compile/full regression/device/source review **NOT_RUN**. |
|152 `sol/cdx-generation-repair-20261026` | `e53c91404fce44ac32aa448b901a9eeeb90d2431` | `docs/research/omega/SOL-CDX-HANDOFF-20261026.md`, `experimental/brain/CDXA1-RESUME.md`. Original collapse reproduced;30gradient checks maxabs5.85e-11. Matched centered30k TRAIN1/4→4/4, known-fact wordingDEV2/8→4/8; changedfacts/unseen/unsupported remain0/4each. All12checkpoints/rawoutputs. CDXA1 source2064params +frozenTRAIN/DEV and7numerical/unit checks preserved **UNTRAINED/UNVERIFIED**, no quality or comparator result. |
|153 `sol/grounding-relations-20261026` | `b41408b3cae889b4727b00648a0340419298b1f0`; sourcefreeze `4bf74b1b827aab56bbe03e032c21c03c508484c8` | `docs/research/omega/SOL-GROUNDING-HANDOFF.md`. Mirrored all-ID and bounded location subject/relation eligibility.91focused+16broaderNode,42actualKotlin pass; old positive/golden retention checked. FullNode interrupted for resource coordination; independent60-case evaluation **NOT_RUN_OWNER_STOP**, no new generalization claim; Gradle/device pending. |
|154 `sol/deepseek-authenticated-bridge-20261026` | `2fb604609d8f7c86ec49fe5ebd4d9158b80beb5a`; implementation `a1d62830967b8a223eaa064d1b02fdd7eb2f8abb` | `docs/research/omega/SOL-DEEPSEEK-HANDOFF-20261026.md`.59focusedtests including actual local TLS fixture/provider/CLI, capacity/revision/no-charge assertion/consent/deadline/error/offline boundaries. Synthetic protocol is NOT DeepSeek inference. No operatorendpoint/GPU/download/deployment/training. |
|155 `sol/coding-discovery-safety-20261026` | `fd6d96ee239a5c48ca562d1837146dcd5a6319a3`; source `91dac7eb78426ab43a02aed52d202b701d8f28f6` | `docs/research/omega/SOL-CODING-DISCOVERY-HANDOFF-20261026.md`. Durable request reservations +same-task diagnostic reuse; Node1247pass6skip, backend60pass. Real Docker pipeline7baseline2fail→7candidatepass→7independentpass, source injected/not neural. No followupGemma fixture/server/model execution started. ArbitraryFEATURE→APK and Fold7 remain blocked. |

Every row names an official DraftPR at `https://github.com/Ullmann27/device-ai/pull/<number>` and immutable checkpoint at `https://github.com/Ullmann27/device-ai/commit/<sha>`. Main stays190f5eabaa60fc59292b2b80d3086aafd7743a80; PR150 staysad14f8b82f9be2cd0026bc16eef71a16bec41424.

### Parent's actual native results

Frozen independent20-task benchmark, same actual Gemma bytes and same CPU/4096context/256output/greedy/thinking-off: API0.18.1 **18/20**, API0.18.0 **18/20**, all raw outputs identical. Both Python/Kotlin code-output predictions wrong, no broad coding competence claimed. Native host0.18.0 works with selected file; no Android ARM64 acceptance implied and Android dependency unchanged. Public Qwen reference611MB actual pinned original:11correct/8incorrect/19completed of20, then native−9/SIGKILL; preserve partial rawRUNNING report and explicit interrupted manifest. Fixed240CPU-second/8GiB bounds in force, exact kill cause unconfirmed. No retry after stop; no exact owner-installed Qwen identity established. Qwen is retained.

All parent raw reports, errors, process exits, model/wheel/runtime provenance and limits are in `docs/evidence/sol-breakthroughs-20261026`. This frozen20-case TEST is consumed. CDXA1 independent32+8fixture and grounding60fixture were authored by separate evaluators without code/train/prediction inspection, byte-preserved and published before use; both evaluation protocols **NOT_RUN**, labels must not enter training. Requested parent/coding source reviews are NOT_RUN; there is no independent clearance.

### First actions for next AI

1. Re-read AGENTS/contract/Issue41, THIS file and real remote PR heads; never reset another developer's branch. Confirm all sibling worktree/source commits still match before using them. Original PR150 and all historical branches remain open.
2. Review and SDK-compile parent AgentActivity policy/handoff changes first; exercise cancellation, oversized recall/error fallback, direct recall, long requests and existing ToolGate/privacy tests. Source review was not completed at owner stop. Do not reuse old PR150 full-suite results as proof of new code.
3. Run the frozen independent grounding evaluator on baselinec391b832 versus candidate4bf74b1b before tuning; archives contain fixture/generator/null-output NOT_RUN report. Any discovered counterexample needs its own new checkpoint and evaluation split.
4. For coding integration, original canonical dependency **216fa837ee8d40b8bb5c32b7ca374d21045a0553** then **91dac7eb78426ab43a02aed52d202b701d8f28f6**. Read their branch-local history/handoff first. For DeepSeek pick its documented source sequence, with original216fa837 before remote seam4a606b3c; **SKIP duplicate31331293**, which was the sibling cherry-pick. Do not cherry-pick all history blindly or lose evidence/docs. Per-branch initial handoff commits may be needed before commits modifying those files.
5. CDX: first read causal/ablation evidence. Centered conditioning improves bounded memorization only, not unseen facts. CDXA1 remains isolated/untrained; resume exact frozen training protocol and compare existing CSX/CDX/deterministic methods fairly before opening independentTEST. Keep all old and12new snapshots; no automatic promotion or owner-memory write.
6. Only after selective reviewed integration run appropriate fullNode/backend/Android/lint checks on one exactSHA, plus fresh independent quality tests. Schedule heavyjobs sequentially on this small shared host. A previous fullNode job was interrupted for coordination; Qwen comparison is incomplete. Source/host/device/production statuses remain distinct.

No model/repair output was executed outside existing controlled Docker pipeline; no services, paid resources, main merges, owner signatures/releases, installations, model replacement or private-data upload. No research/model/build containers/processes remain after closure. The next AI can reproduce all useful work from GitHub; large official toolchains/model originals are external caches with pinned source/hashes, not owner weights to be copied into Git. Revert individual source commits on a new review branch if needed; checkpoints/history/facts remain preserved.
