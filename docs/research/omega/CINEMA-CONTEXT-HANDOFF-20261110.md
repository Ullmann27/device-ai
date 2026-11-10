# Cinema context research — 10.11.2026

Owner task: parallel context research, isolated from cinema rendering.
Base: `17491f4d8103f0e55836369333fab07e80e6e9c2`, PR #706.
Branch: `research/continuum-context-cinema-20261110`.
Owner of this slice: context_research; integration owner: the main Cinema integrator.
Experiments: EXP-06 (source identity), EXP-07 (negative controls), EXP-12 (context reset).

## Concrete result

`Ctx3Context` previously selected the FIRST candidate for
`Nicht die erste, sondern die zweite Option` and rewrote both selectors to the
first entity. `Nicht die erste` and `Die erste oder die zweite` also selected a
candidate instead of asking for clarification. Three genuine failing JUnit
methods were reproduced against the unchanged Code52 source (12 tests total).

The existing resolver now collects actual selector spans, rejects negated
selectors, accepts one explicit `nicht … sondern …` replacement, and rewrites
only the selected span. Unsupported/multiple positive selections clear focus
and require clarification. A changed source version or missing ordered candidate
set still prevents resolution. A failed explicit replacement also clears the rejected old focus, so the next pronoun cannot revive it. Named-entity correction behavior is unchanged.

This is an existing **SHADOW** path, not a new resolver in the live answer path:

`ContinuumLearningCore.replyFromMemory / replyWithEvidenceScoped`
→ `Ctx3Shadow.evaluate` → `Ctx3Context.decide`
→ source-filtered `ContinuumMemoryDialogue.rank` → diagnostic comparison.

`Ctx3Shadow` remains opt-in, read-only and unable to replace the user's response,
run tools, write facts or alter weights. No flag is enabled by this patch. This
fix therefore does **not** prove that the owner's current APK now handles these
sentences correctly in ordinary conversation.

## Verification

- Initial directed run: 12 methods, 3 failures; log in
  `docs/evidence/cinema-context-20261110/baseline.txt`.
- Follow-up self-review found stale focus after an impossible replacement. One
  additional genuine RED method (15 total) is retained in `review-red.txt`; the
  subsequent fix clears focus only when an explicit correction cannot resolve.
- Final directed run: 15 methods, 0 failures; added reverse-order, unsupported
  grammar, trailing negation, source-change and missing-focus controls.
- Source-bound context/voice regression run: 106 methods, 0 failures, no skips.
  QA independently reran the final source: 106/106 PASS, with byte-identical
  source/test hashes. Its scoped source/negation review found no further issue. Actual Kotlin 2.4.0/JDK21 compilation; pure JVM only, no substitute product
  classes or Android stubs. Source hashes, tool versions and log are retained.
- Full Android Gradle build, full repository tests, real model inference,
  Ctx3Shadow end-to-end execution and Fold7: **NOT_RUN** for this research slice.
- The fixtures are author-created regression cases, **not** independent holdout
  tasks, not a general language benchmark and not a measured intelligence gain.

Reproduce the 106-method run:

```sh
KOTLINC=/path/to/kotlinc \
CONTINUUM_JUNIT_JARS=/path/to/junit-4.13.2.jar:/path/to/hamcrest-core-1.3.jar \
bash scripts/continuum-context-cinema-check.sh /tmp/cinema-context-check
```

The script writes its actual HEAD, worktree state, input hashes, toolchain,
compiler output and JUnit exit status. Evidence from a different SHA must not be
substituted for a new run. It does not download dependencies or call a network.

## Source audit: existing capabilities and gaps

| Area | Existing path | What remains unproven or missing |
|---|---|---|
| Session follow-ups | `LocalConversationFollowUpPolicy` → `LocalFollowUpTaskFrame` → `AgentActivity.runLocal` / `AiContextCompiler` | Bound context routing exists; real Gemma answer accuracy needs model/device trials. Prior tool requests are deliberately not recycled as authorization. |
| Topic return | `LocalConversationContext.situationHint` → `LocalConversationSituationTracker` → `ConversationSituationModel` | Topic labels are navigation hints, not facts. Reading a return hint activates a topic; repeated reads intentionally have no second return hint in current tests. |
| Ordered references | `Ctx3Context` through the existing `Ctx3Shadow` | Correction defect repaired here; SHADOW status retained. Live answer promotion requires separate evidence and integration decision. |
| Decisions/open work | `WorkDecisionRecall`, `OwnerCorrectionIntent`, `CockpitWorkJournal` | Existing journal-backed recall must be tested with actual displayed candidates and cancellation; no inferred completion from planned work. |
| Tool approval | `AgentActivity.showStructuredToolConfirmation` → existing proposal/ToolGate, plus exact-request `InternetConsentPolicy` tickets | Current source pauses hands-free while a dialog owns the window. A free-standing spoken `ja` is not shown to consume a pending exact tool approval. Do not report natural voice approval as implemented. |
| Voice turns | `AndroidOnDeviceSpeechInput` → `SpeechTurnRecovery` / `ContinuousConversationMode` → normal chat | Warm/cold recovery is bounded. Host policy tests do not prove ten reliable native listening turns. |
| Early speech | `ApprovedStreamingSpeechAssembler` → `StreamingSpeechShadowTrace` in `AgentActivity.generateLocal` | Current candidate observer records SHADOW timing; this is not proof of audible sentence-by-sentence model streaming. |
| Mouth/voice | TTS `onBeginSynthesis`, `onAudioAvailable`, `onRangeStart` → `voiceSignal` / `AvatarDirector` | Actual PCM/range hooks exist. Text ranges are not measured phonemes; device acoustic timing and lip alignment remain unmeasured. |
| Latency | `VoiceTurnLatencyTrace` with monotonic stages | `TTS_STARTED_CALLBACK` is explicitly not evidence of an audible speaker frame. |

## Seven-day protocol

This is a seven-workday research sequence, not a scheduled background job and
not a claim that the following experiments have already run.

| Day | Work | Measurable acceptance / stop criterion |
|---|---|---|
| 1 | Freeze exact base/candidate SHAs, fixture IDs and expected outcomes. Record current 106-method result. Prepare 60 development dialogues and commission 40 independently authored holdout dialogues, spanning corrections, pronouns, task continuity, stale facts and approvals. | Every case has session/source IDs and an explicit expected referent or required clarification. Freeze holdout hash before the final implementation; no training or tuning on holdout. Current 15 directed methods remain regression cases. |
| 2 | Compare current live context framing and SHADOW reference candidates using the same complete dialogues. Include `der davor`, multiple names, interrupted responses, negative selectors and source revocation. | Zero false source bindings in safety cases. Report correct binding, wrong binding, clarification and coverage separately; any wrong confident binding blocks promotion. Show every baseline/candidate difference. |
| 3 | Exercise existing temporal memory and evidence binding with confirmed, planned, contradicted, revoked and stale records. Vary prompt budget and session restarts. | No planned/unconfirmed statement becomes a confirmed fact; zero cross-session leaks or stale-source resurrection. Partial-context cases abstain rather than drop a late correction. Preserve canonical memory schemas. |
| 4 | Design a pending-approval conversational adapter around the existing exact proposal/call identity, session, expiry and ToolGate; build only after that contract is reviewed. | `ja` with no matching pending request does nothing. Changed arguments/session, expired request, cancellation, negative answer and double consumption do nothing. Destructive two-stage approval remains intact. 100% of these gates must pass; no live external mutations in the test corpus. |
| 5 | Run three offline ten-turn Fold7 sessions using existing ASR/TTS recovery, with silence, busy/client errors, owner interruption and background/resume. Use `VoiceTurnLatencyTrace`. | Zero stuck-listening states and zero late-turn acceptance; record all failures. Report ASR-final→first text, approved chunk, TTS callback and listening reopen p50/p95 separately. Compare same model, thermal state and task set; callbacks are not acoustic measurements. |
| 6 | Evaluate sentence candidates through existing quality/evidence checks before any audible streaming promotion. Replay actual utterance/range/PCM events into the existing AvatarDirector. | Rejected/cancelled/unapproved/code-only candidates never play. One utterance identity owns voice/mouth events. Measure lip offset only with synchronized real audio/video; no phoneme-quality claim from text-range tests. Offline stays offline. |
| 7 | Run frozen holdout once, full relevant JVM/Node/Android checks and owner-visible Fold7 replay; compare to baseline at equal budgets. | Publish per-case outcomes, latency distributions, memory/thermal observations and failures. Integrate only improvements with zero new safety regressions and no context-coverage regression. Otherwise retain SHADOW and state the rejected hypothesis. |

## Integration and rollback

The canonical target for this task is the main integrator's Cinema development
branch from #706, not the protected Code52 release or `main`. The research PR
must name that target and its exact current SHA when proposed. The three code
and check files can be reviewed/cherry-picked together after exact-source checks;
there is no reason to merge unrelated old research branches or enable SHADOW.

PORTIERT: existing SHADOW call path uses the repaired resolver.
VERIFIZIERT: only the 106 pure-JVM methods listed above.
GERÄTEBESTÄTIGT: **NOT_RUN**.

Next executable step: run this script after integration, then Android compilation
and the existing Ctx3Shadow/session suites on that exact SHA. Do not claim a live
conversation improvement until the normal response path is measured separately.
Rollback: revert only this slice's commit; no memory, data, model, permission,
rendering, package or signer migration is involved.
