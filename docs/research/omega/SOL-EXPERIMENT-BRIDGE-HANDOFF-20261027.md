# SOL / Continuum – handoff: actual Android chat → SWB1/CRE1 → CDX1 shadow
2026-10-27. Existing primary app only. No main/production/signer changes and no changes to PR #193.

## Finding and narrow implementation
- `ContinuumCognitiveRuntime.reply()` **already** invokes `ContinuumLearningCore.replyWithEvidenceInConversation()`; CRE1 already observes the same authorized SWB1 hits and is gated to shadow or verified rerank. Do not duplicate this.
- The production Android `runContinuumDialogue()` already feeds SWB1 recall, CRE1 trace and approved local-context prompts to the existing Gemma native route under `LocalFirstPolicy`. The actual native model invocation still needs on-device confirmation; don't label it proven.
- `ContinuumDecoderBridge.experimentalPairedContinuation()` exists and already restores a frozen bound encoder for `PINNED_CURRENT` or `PINNED_PREVIOUS` CDX1 checkpoints. The missing useful **runtime** connection was access from the real conversation turn, rather than only via a separate laboratory dialog.

### New opt-in, existing chat path
In the **normal Android Chat**, send `Forschung CDX1: Welche Farbe hat die Lampe?` (or `CDX1 Schatten: ...`) about a previously *confirmed* owner fact. `AgentActivity.sendCurrentInput` dispatches to the **existing** `runContinuumDialogue` with `shadowResearch=true`. Exactly one `CognitiveDecisionBridge.respond()` computes real SWB1 retrieval and CRE1 scoring. Its `turn.memoryIds` and `decision.evidenceHits` are handed to `ContinuumCdx1ChatShadow.observe` without second memory retrieval.

The CDX1 pass only runs when the actual answer is grounded in **same-turn authorized** owner-confirmed hit, text/question fit the existing lab's 160 UTF-8 byte envelope, the checkpoint has trained updates and a valid `PINNED_CURRENT` or frozen `PINNED_PREVIOUS` encoder binding. Decoder SHA/updates/binding are checked before and after generation. A changed/invalid/unavailable checkpoint yields a truthful non-success. This uses the original `ContinuumLearningCore.experimentalPairedDecoderContinuation` on the normal background local-agent executor; no extra model/download/training or network API.

The chat reply shows the original authoritative SWB1 answer first and a clearly separated **UNCONFIRMED** CDX1 output with source-ID, full checkpoint SHA and CRE1/SWB1 trace in existing activity evidence. No research text is passed into Gemma, tools, training, owner memory or approved output. Only an explicit research query performs CDX1 work. Other chats and the existing Gemma route are unchanged. Missing/older checkpoints are never promoted just for chat. `PINNED_PREVIOUS` is permitted in **shadow-only mode** because the existing decoder restores the saved matching encoder.

## Evidence
- Exact latest bridge Kotlin file was reproduced locally byte-for-byte and `git hash-object` was checked against GitHub's original blob SHA `a05ff9d1667f95775c673411246a14358b881c50`. `kotlinc` successfully compiled it against minimal typed stubs, and a 14-assertion executable smoke passed (`PASSED=14`), covering safe opt-in parsing, actual generation invocation, source identity, denied unconfirmed sources, missing evidence, frozen previous checkpoint, and checkpoint mutation blocking.
- An executable GitHub-source static cross-file contract passed 12/12 pre-final tweak; recheck updated binding on final head. Dedicated Kotlin/JUnit tests and Node source-contract tests were committed to this branch but **full Gradle/JUnit/Android QA was not executed** and must **NOT** be labelled passed. The Render workspace has previously run out of build minutes.
- This is a genuine *new callable experiment path* in existing Android Chat, not a mere status tile. **Real device inference, CDX1 answer quality and CRE1/Gemma improvement are NOT PROVEN** until the code is built and tested on the owner's Fold7 with an actual confirmed fact and matching checkpoint.

## Next steps requiring independent evidence
1. Run `node --test .github/continuum/cdx1-chat-shadow-bridge.test.mjs`.
2. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, fix compile errors and rerun.
3. Preserve and integrate PR #193 separately when QA allows, retaining its user-specific Gemma restore policy.
4. Re-freeze source inventory; existing owner release manifest is stale. No owner-signed APK without explicit independent approval and original signing certificate.
5. Device test: import no new model; use existing confirmed fact, run native Gemma test separately, send explicit `Forschung CDX1: ...`, inspect real model/source receipt; with no checkpoint show honest fail; verify no facts/weights changed.
