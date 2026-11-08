# Continuum cognitive pilots: preregistration and handoff

Status at registration: **UNVERIFIED / RESEARCH_SHADOW**. Owner request:
#642/#643 and the 2026-11-08 research commission. No release, signing,
installation, main merge, private datasets, model calls or weight updates.

## Immutable starting point

- Entry branch `glowing-carnival`, SHA `52997ee3d94686add646d53fb11fd0b8d9e1e94f`:
  54-file skeleton, not the current app.
- All comparisons start from Code50
  `473f79727e4b52509cc7d1af71e59b80ab9b6be5`,
  branch `chatgpt/code50-qa-staging-20261108` (#630, based on #603).
- Work branch: `research/continuum-cognitive-pilots-20261108`.
- #635: `d7e5a1ec7e7f6f9564cd368f8663237edba203b6`. Its
  `HypothesisWorkspace.kt` blob `916b21b7d1d3e8c05771c7f4250d6e085e6f063f`
  is identical to Code50. Its release/QA files are older: **no wholesale merge**.
- #574: `642dc0fe9f5deca306a3ad1f5be40e5e3055abc5`, Fable conversation
  framing and work recall are already present in Code50.
- #287: `ad48ed7f81815d26940b8d36d7e28cb78ca65c19`; existing Node broker
  and Kotlin read-only reuse adapter are present, not live inference.

## Actual source connections

Paths are relative to `app/src/main/java/at/heinz/deviceai/`.
"Connected" means a source call, not proof on an installed device.

| Component | Source evidence at the pinned baseline | State |
|---|---|---|
| ContinuumBrain | `learning/ContinuumBrain.kt`, `AgentActivity.kt:6081,8171` | Owner OFF/SHADOW/ON switch; default OFF |
| AiContextCompiler / HybridMemoryRetriever | `cognitive/AiContextCompiler.kt:99,195`, `AgentActivity.kt:222` | Connected; canonical retrieval |
| LocalConversationContext / LocalFollowUpTaskFrame / LocalConversationFollowUpPolicy | `AgentActivity.kt:9631,9718`, `cognitive/LocalFollowUpTaskFrame.kt:42` | Connected, bounded session evidence, no second memory |
| ChatReasoningRouter | `AgentActivity.kt:9684`, `cognitive/ChatReasoningRouter.kt:37` | Bounded optional model advisory, not proof |
| WorkDecisionRecall | `AgentActivity.kt:2083,5376,9710` | Connected read-only journal/owner-statement view |
| LightweightJtmsEngine / HypothesisWorkspace | Definitions and tests; no production callers found | Source-integrated research only |
| CausalEpistemicLedger / CounterfactualWorldOverlay / EpistemicArbitrator | Definitions and tests; no production callers found | Source-integrated research only |
| AdaptiveReasoningStrategyMemory / ExperiencePatternMaturity | Definitions and tests; no production callers found | Research only; not autonomous weight learning |
| AnswerClaimVerifier | `cognitive/StateTelegramAnswerShadow.kt:48` | Shadow evaluator, not a universal proof checker |
| ToolWorkJournalBridge | `AgentActivity.kt:1952` | Connected receipt-to-journal path; success is not independent validation |
| ComputeNodeRegistry / ComputeResultVerifier | `cognitive/ComputeFabric.kt:63,229`, `VirtualModelFabric.kt:10` | Canonical contracts; no live peer execution established |
| ContinuumComputeReuseResearch / Node ComputeReuseBroker | Existing source and tests; no Activity caller | Research only; default-deny receipts |

Prior Astra results were read in
`ASTRA-BRAIN-INTEGRATION-HANDOFF-20261026.md` and
`CONTINUUM-COGNITIVE-CONNECTION-MATRIX-20261027.md`: selective integration,
failed SWB2 generalization and unproven autonomous model repair must not be
renamed successes. The local tree has no separately identifiable Mythos
implementation; the canonical state document uses it as a research label.
GitHub search also located #329/#360 (architecture evidence and multi-parent
integration), not a distinct proven Mythos model. Attribution remains unresolved.

## Frozen hypotheses and acceptance criteria

Related register entries: EXP-07, EXP-13, EXP-14; compute follows #287/#635.
Use original source files, no algorithm doubles. Synthetic examples are
non-personal. Freeze DEV and disjoint HOLDOUT cases before the candidate.
These are author-created prospective tests, **not an independently authored
blind benchmark**. Never reuse them as a future unseen holdout.

| ID | Comparison and falsifiable prediction | Reject / stop |
|---|---|---|
| H1 context | Bounded discourse prefixes before anaphoric questions should preserve the existing context requirement. Same frozen utterances, original policy vs minimal prefix-aware policy. All positive HOLDOUTs correct; no new false-positive negative controls. | Any formal-Sie, definition, named-person, control-character or overlength regression; no relaxation of the existing newline guard |
| H2 reasoning | Atomic batch admission of explicit priors should remove the #635 insertion-order bias and stabilize cost-aware probe selection. Original sequential admission vs batch, all four-hypothesis permutations and disjoint unequal-prior cases; normalized error <= 1e-6. Follow with external probe observations and explicit JTMS dependency/revocation checks. | Capacity/duplicate admission must be atomic, caller mutation isolated, existing behavior retained for single add; any invalid multi-hop conclusion fails |
| H3 compute | The existing broker should reduce CPU/wall time for repeated expensive pure work, with byte-identical answers, **including independent recomputation on cache admission**. No benefit predicted for unique inputs or cheap work. | Any wrong output or cross-revision hit fails. Report negative timing results; no universal speedup, energy or LLM claim |

Timing: fixed workloads, warmup, alternating A/B order, multiple measured
repetitions, raw measurements retained. CPU and memory are host diagnostics;
heap deltas are not allocated bytes, process high-water RSS is not per-call RAM.
Joules, Android thermal behavior, Gemma output quality, long-session retention,
ASR/dialect generalization, training and real autonomy remain **SKIP** without
the relevant runtime/device. No additional trainable parameters are justified
by these pilots; SWB/CRE/Gemma checkpoints remain untouched.

## Research comparison

Primary repository documentation read on 2026-11-08:

- [LongMemEval](https://github.com/xiaowu0162/LongMemEval): extraction,
  multi-session reasoning, updates, temporal reasoning and abstention.
  H1 measures only context routing, **not** LongMemEval performance.
- [ReAct](https://github.com/ysymyth/ReAct): interleaved reasoning/action.
  H2 instead verifies small explicit state transitions; a probe suggestion
  grants no tool permission and is not an executed action.
- [vLLM](https://github.com/vllm-project/vllm): batching, PagedAttention and
  prefix caching are established efficiency approaches. H3 is exact pure-work
  memoization, not a novel source of compute or a GPU-free LLM result.
- arXiv pages 2410.10813, 2210.03629, 2309.06180 and 2308.08708 were
  inaccessible (DNS failure, including a direct retry). Full-paper review and
  the consciousness-indicator survey are not claimed.

Functional comparison for subsequent experiments: Global Workspace suggests
shared bounded task state (existing conversation/context compiler); predictive
processing suggests explicit prediction-error checks (probe/observation);
active inference suggests information-gain/cost selection (existing workspace);
metacognition suggests externally scored uncertainty and strategy outcomes
(existing strategy memory). These are architectural analogies, not equivalent
implementations or evidence of subjective consciousness.

## Reproduction / rollback

The source-bound runner and result paths are added in this checkpoint series.
Requires a real Kotlin compiler, JDK and JUnit4; no Android stubs are used.
The host initially lacked `kotlinc`; official Kotlin 2.2.21 was downloaded
with its published SHA-256 verified, plus JUnit 4.13.2 / Hamcrest 1.3, outside
the repository. No installer is shipped or automatically run.

Rollback: keep the baseline branch and weights; do not merge these Draft PRs.
No database migration or durable memory write exists in these experiments.
If reviewed source is later integrated, revert its narrow commit rather than
resetting a shared branch. Actual results, SHAs and next trials follow below.

## Milestone H1: measured context-routing result

Draft #647. Frozen preregistration/data commit:
`d237523bc65988e6dd1110562c19227799783562`.
Candidate changes **one anchored anaphora regex** in the existing policy:
optional `Und/Aber/Also`, preserving case-sensitive pronouns, formal-address,
definition, control-character and length boundaries. No new parser/store.

Actual commands:

```sh
export CONTINUUM_JUNIT_JARS=/path/junit-4.13.2.jar:/path/hamcrest-core-1.3.jar
bash scripts/continuum-context-pilot.sh /tmp/h1-red 473f79727e4b52509cc7d1af71e59b80ab9b6be5
# Expected RED exit 1: 20 tests, 1 failure, 0 skips.
bash scripts/continuum-context-pilot.sh /tmp/h1-green
# GREEN: 20 tests, 0 failures, 0 skips; frozen fixture assertions also pass.
python3 tools/continuum-cognitive-pilots/measure-kotlin-pilots.py \
  /tmp/h1-red/context.jar /tmp/h1-green/context.jar /tmp/h1-paired
node --test tools/continuum-virtual-fabric/*.test.mjs
# Existing native baseline: 47 PASS, 0 FAIL, 0 SKIP.
```

Six paired JVM launches per arm, alternating order, seven measured batches
after each warmup. Raw evidence: `docs/evidence/cognitive-pilots-20261108/`.

| Metric | Code50 | Candidate |
|---|---:|---:|
| DEV routing | 2/4 | 4/4 |
| Disjoint HOLDOUT routing | 1/12 | 12/12 |
| Negative controls correct | 8/8 | 8/8 |
| Median wall time / 2,400 routing calls | 12.585 ms | 10.249 ms |
| Median thread CPU / 2,400 routing calls | 11.379 ms | 9.376 ms |
| Maximum process RSS across launches | 86,572 KiB | 86,024 KiB |

This is a deterministic routing improvement, **not** evidence that Gemma
resolves these references correctly. Host timing includes JIT/GC variability;
the small timing difference is not a claimed speed breakthrough. The live
frame call was source-traced, not Android-executed. The old policy is the
prefix-removal ablation. More than four-turn continuity, ambiguous local
referents, ASR/dialect, end-to-end inference, Android/Gradle and joules: **SKIP**.
Next falsifiable context trial: freeze independently authored ambiguous
conversations, measure actual answer accuracy and unnecessary clarification
with the same Gemma checkpoint on the phone.
