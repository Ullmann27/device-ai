# Independent Android planning-path review

Status: **ACCEPT scoped source/host planning path at4b98da9362f91ab9930a7f4b279737a9e80f94bd**. No remaining blocking finding within the reviewed scope. Real Android inference and device acceptance remain NOT_RUN. Baseline19474261c15a3503a9f041244b4b4c3eaed4636f. Frozen before candidate implementation/inspection. Reviewer owns tests/evidence/docs only; author owns AgentActivity plus narrowly scoped helper. Existing ToolGate/runtime/manifest/IPC/scheduler remain outside feature scope. No device access: FOLD7_NOT_RUN.

## Frozen adversarial cases

1. Admission must require explicit owner full lowercase40-character source SHA and1-3 permitted source paths before generation. Missing/partial SHA, invalid count, duplicate/traversal/absolute/forbidden/control paths, mismatched language/chain and invalid limits must block inference and any draft dialog. No invented metadata.
2. Model output is a proposal only. Model cannot choose/change task ID, SHA, source path scope, fixed chain or limits; tool/shell commands and authorization fields never become authority. Valid model goal/kind/acceptance may prefill a typed draft but cannot approve, prepare, dispatch or install.
3. Strict bounded whole-document parsing rejects surrounding prose/fences, duplicate or unknown keys, trailing content, malformed Unicode, numeric overflow, oversized output, excessive goal/criteria count/length, unknown kind, wrong types and omitted required model fields. Rejection must leave no draft/authorization state.
4. Exact owner-scope immutability must survive mutated input collections and generation. A replacement source/path/pin, session switch, new turn or model identity after generation invalidates the old result. Equal display strings are insufficient if the active turn differs.
5. Explicit cancellation during generation and after worker completion but before UI delivery, Activity destruction/recreation, replaced session, switched model, failed/partial generation and unverified model binding must not open a draft dialog. Check on UI delivery, not solely before posting.
6. Planning has no tools/side effects: no ToolGate execution, advisory tools, continuation loop, context-triggered commands, CCC1 writes or direct authorization. Bounded orchestration has zero tool calls/retries; no alternate model/runtime engine is introduced. A malicious model tool call is rejected or shown as inert text only.
7. Existing normal chat path retains its prior orchestrator/tools/context behavior. Explicit planning opt-in cannot silently affect following normal chat turns; no stale planning-mode state leaks after success, failure or cancellation.
8. Full proposal path genuinely reaches the existing manual draft dialog with every owner-bound field and proposed criterion; full existing review and explicit approval are still required. Do not infer model-to-coding integration from an unused helper or test-only adapter. Existing runLocal model/session/turn binding must remain intact.
9. Failure-safe lifecycle: absent model readiness, busy turn, destroyed Activity, superseded generation or missing scope reports failure/blocked state without queueing an auto-run. Multiple/stale completions must not duplicate proposal dialogs or reuse old execution consent.
10. Evidence: reproduce meaningful RED on baseline/new unchanged candidate tests, then GREEN on exact final source. Synthetic/injected model output is CONTRACT_TEST, not local model execution. Host process assertions do not establish Android17/Fold7 execution.

## Execution plan

Inspect actual baseline runLocal and draft paths. Freeze independent test assertions before author implementation outcomes are shared. Start bounded pure Kotlin/parser/controller checks; QA alone schedules Gradle/Robolectric after immutable external test snapshots. Record source SHAs, command exits, counts/skips, raw failure evidence and hashes. No feature edits or central contract changes by reviewer. Rollback is omission of this review-only branch.

## Frozen concrete checks and baseline audit

Concrete independent cases are committed locally at9faa4f39, publicationcad0961aeba733f990d288782f6192fc0e0ae632 (PR435), before implementation/results. `pure/IndependentCodingPlanningTest.kt`:20 test methods, with parameter loops for metadata/path limits, unknown authority fields, strict schema/type/overflow/Unicode/output-size rejection, immutable owner scope, exactly one isolated generation, cancellation, model swap and tool-event rejection. Runtime events are injected through the real LocalModelRuntime interface and explicitly constitute CONTRACT_TEST, not inference. `ui/IndependentCodingPlanningIntakeUiTest.kt`:2 methods invoking the actual existing AgentActivity draft dialog, requiring the optional model-planning button and invalid-owner-metadata refusal before runtime access. Both classes remain UNVERIFIED pending execution.

Baseline source19474261 has no model-to-draft planning entry. Its existing runLocal creates a BoundedAgentOrchestrator over LocalModelRuntime, compiles ordinary chat context, optionally invokes ChatReasoningRouter advisory, then uses LocalToolContinuation. Existing LocalModelReasoningAdapter already supplies bounded isolated streamThinking and rejects tool events. Existing BoundedAgentOrchestrator's zero tool budget by itself does not cap model calls (a rejected proposal loops until step limit), and it normally writes experience/checkpoint records even without tool execution. These constraints were communicated before implementation; independent review will check that the optional planning mode explicitly excludes those behaviors while preserving normal chat.

The existing runLocal delivery block checks Activity destruction and active cancellation object, then clears that object before handling success. Planning must evaluate cancellation/session/model/foreground/turn ownership before clearing/consuming the token and before opening an initialDraft dialog. Real device lifecycle is not established by source inspection or pure helper checks.

QA currently owns an exclusive native-model benchmark. No reviewer Gradle run has occurred; author baseline RED tests and the two independent UI baseline cases are queued. A missing helper compilation error will not be reported as a behavioral product RED. First runtime/UI results must record actual source/compiled-artifact identity and any source-equivalence qualification.

## Actual baseline RED

QA executed the immutable intake class on e8c155e706924d3f611cdce4591849efe0902067, whose entire `app/src/main` is byte-equivalent to19474261. Independent intake2 tests:0PASS/2FAIL/0SKIP, Gradleexit1. One assertion records missing entry button (expected1,actual0); the other cannot select the absent entry. These demonstrate missing model-planning entry, not parser/cancellation security defects. Combined authored+independent baseline13 cases all failed. Captured source manifests before/after are byte-identical (cmp exit0). Actual raw XML/logs/source manifests/harness hashes/command/init script are preserved under `docs/evidence/momega1/android-plan-review/baseline-red-e8c155e7`.

Independent delayed-delivery suite adds8 cases through actual runLocal with a real isolated receipt store and fake LocalModelRuntime. It waits for the real background executor to enqueue completion before modifying cancellation, session, turn token, model hash, Activity finish or pause/return. Positive case requires full existing draft prefill and no service/activity dispatch; every case checks no tool receipt. Current pre-implementation fixture hashes: pure20-case class `1e35b24cdf4860ebf61ec92da050547d1f76e0640f38eb26857299c3d0e1e0dd`; delivery8-case class `6dca854b74d2613aac52103715adfb87fe3d5fc92536a03e20d469c26731a1a3`; intake2-case class unchanged `c229de166f49f588d85a57046a388873bb6fc28fff33bd4718bdbc7832e1f33f`. Fixture revisions before candidate inspection allowed canonical-equivalent valid JSON and initialized the genuine required receipt store; assertions were not weakened based on product outcomes.

## Frozen candidate source audit

Inspected4b98da9362f91ab9930a7f4b279737a9e80f94bd only after all30 independent methods were frozen. Production diff is limited to AgentActivity and new ContinuumCodingPlanning; central ToolGate/runtime/manifest/orchestrator classes are unchanged. Exact source/blob hashes and diff are under `candidate-4b98da93`.

The new button validates owner metadata, refuses busy/background/shutdown entry, and passes an immutable planning object to the existing runLocal. Within the same BoundedAgentOrchestrator, planning chooses a CancellableAgentModel using existing LocalModelReasoningAdapter.streamThinking; no second runtime is created. The helper permits one invocation, bounds output to8000 characters/UTF-8 bytes, rejects every model-supplied scope/approval key, and validates the eventual Draft with existing ContinuumCodingTask validation. Source/path/chain/limits/app-generated ID are pinned outside model output. Memory retrieval is empty, experience is in-memory only, checkpoint persistence is null, maxToolCalls/maxRetries are0, and the model adapter rejects tool events before producing any proposal. Normal-chat AgentModel body and default policy remain intact behind the null planning branch.

The UI completion gate is in the actual runLocal callback, before consumption of active cancellation ownership: same session, foreground flag, unchanged pause epoch, uncancelled token and exact imported model identity are required. The existing token identity check rejects superseded completion; clearing the token prevents duplicate delivery. A valid result only opens the existing draft dialog with initial fields, then exits before normal chat persistence. Existing draft validation and prepare/RUN owner approvals remain downstream. This is source review; executed30-case evidence is pending QA and no acceptance is claimed yet.


## Final independent acceptance

Exact candidate4b98da9362f91ab9930a7f4b279737a9e80f94bd passed **30/30 independent methods**:20 parser/runtime contract checks,2 actual intake UI checks, and8 actual runLocal delayed-delivery UI checks. Total focused run:102/102 across17 classes,0 failures,0 errors,0 skips, Gradleexit0. Reviewer independently parsed every retained XML and compared the before/after source manifests byte-for-byte. The baseline intake2 genuine failures now pass on the feature source. No new blocking security defect was found in this candidate; the baseline RED established absent integration rather than a preexisting authorization exploit.

Actual call-path evidence uses the real Activity→runLocal→BoundedAgentOrchestrator→existing LocalModelReasoningAdapter with an injected runtime, then the real existing draft dialog. Native completion is deliberately queued before cancellation/session/turn/model/foreground-epoch changes in the adversarial cases. Positive delivery retains owner full SHA/path/budgets and proposed criteria, with zero started services/activities and an empty real tool-receipt store. Owner preparation and execution remain separate explicit approvals; the model creates only draft data.

The first candidate test compilation failed because the independent RuntimeFixture omitted abstract LocalModelRuntime.unload(callback). No tests executed in that attempt; stale XML copied by the QA wrapper is excluded. Reviewer added only the required method in both fake implementations, with no assertion changes. Compile-failure log/exit/harness hashes/QA ATTEMPT-STATUS are preserved. Final class hashes are pure `be19e5189c6b4c559cc97981484916a1be73adc4f18d8bfab552e6a30a57dc9f`, intake unchanged `c229de166f49f588d85a57046a388873bb6fc28fff33bd4718bdbc7832e1f33f`, delivery `681c1c3c137e22b8924586ecea3be8b4b2337bc07f5345e6db71a9e07c30ab28`.

## Reproduction and handoff

All raw accepted evidence and command/init script are in `docs/evidence/momega1/android-plan-review/green-4b98da93`; the original baseline evidence and rejected harness compilation remain alongside it. `SHA256SUMS.json` hashes every retained evidence artifact. QA alone executed Gradle; no simultaneous reviewer Gradle job was started. Kotlin/JVM contract tests ran as part of this exact-source Gradle suite; no separate standalone-native test result is claimed.

The actual command, from the author's frozen candidate worktree, was:

```sh
source /workspace/momega1-build-env/env.sh
gradle --no-daemon --max-workers=2 --console=plain \
  -I /workspace/momega1-build-env/planning-final-v2.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
```

For independent-only replay, check out exact4b98da93 in a separate worktree; replace the init argument with this review checkout's `tools/momega1-android-plan-review/test-filter.init.gradle` and add `-Dmomega1ReviewRoot=/absolute/path/to/review-checkout`. This portable helper selects only the3 independent classes; it was added for reproducibility after the accepted QA run and is not a separately tested run. The retained original QA init is the acceptance artifact and selects17 classes.

Normal-chat preservation is supported by unchanged existing AgentModel body/default policies and the adjacent BoundedAgent/LocalModelReasoningAdapter/LocalTurnModelBinding regression classes (16 cases). An entire ordinary-chat UI flow was not separately exercised by this reviewer. Actual Android process death, native LiteRT inference quality, physical-device resource use, and Fold7 end-to-end coding execution are not established by injected-runtime Robolectric tests. **FOLD7_NOT_RUN; no real model benchmark or release acceptance from this lane.** No ToolGate/runtime/manifest/IPC/scheduler, productive model, signing, installation or owner-data changes were made by the reviewer.

Chief may integrate this exact reviewed feature source (or prove equivalent reviewed production bytes) and the review artifacts, then perform combined exact-source QA. Review-only rollback is omission of this branch; feature rollback is the author's isolated planning commit. Existing canonical CCC1/approval boundaries remain authoritative. No main merge or release is authorized by this review.
