# Continuum Cognitive Runtime – Integration handoff

Stand: 2026-10-16. Working branch: `copilot/pr-12-working-first-vertical-slice`, PR #19.

## Origin and scope

Opus 5.5's interrupted local session is visible in user-provided screenshots:
it edited AgentActivity owner-fact flows, local-model routing, pronoun context,
the JVM wiring test, and was executing an isolated Kotlin build. No such changes
were committed to PR #19 at the time of inspection (prior head `07d1437d`).
The changes below are **independently reconstructed and committed** from the
existing GitHub code, not recovered copies of Opus's unpublished workspace.

## Implemented end-to-end path

`AgentActivity.runContinuumDialogue` -> `ContinuumCognitiveRuntime.reply`
-> `ContinuumLearningCore.replyWithEvidence` -> the existing persistent
`ContinuumMemoryDialogue` SWB1 path -> exact same authorized SWB1
candidates observed by `Cre1InferenceEncoder` (CRE1 shadow mode) ->
confirmed answer or existing local model fallback.

- The SWB1 answer and candidate list share one synchronized model/experience
  snapshot. CRE1 cannot add memories or turn a refused query into an answer.
- With no CRE1 research snapshot, its seed-only embeddings are labelled
  `UNTRAINED_SHADOW`; with a checksum-valid updated snapshot they are labelled
  `TRAINED_SHADOW`. Damaged snapshots are preserved and excluded.
- Follow-up pronouns pass bounded prior-question context to the local model
  without manufacturing a user fact or falsely stating that a referent is
  known. Only the original user message is used for explicit memory-write
  authorization.
- Explicit confirmed facts can immediately retry an earlier unanswered
  conversation turn, but only the ordinary SWB1 evidence gate can ground it.
- Local-model results write **metadata only** (successful/failed, timestamp,
  at most 16 entries). No generated model text enters this journal.
- Chat self-diagnosis phrases such as `Continuum, überprüfe deinen Lernkern`
  report SWB1 training status, CRE1 checkpoint state and bounded local-model
  outcome counters from the background I/O executor. This deterministic
  diagnostic is not yet an autonomous source-patching developer agent.

## Added regression checks

`ContinuumCognitiveRuntimeTest.kt` checks grounded recall with both kernels,
empty-memory abstention, recovery across simulated app restart, real trained
CRE1 checkpoint observation, corrupted snapshot behavior, owner-confirmed
follow-ups and contextual model prompts.

`ContinuumRuntimeEventJournalTest.kt` checks metadata-only event records,
bounded history and failure on corrupted records.

`.github/continuum/android-integration-wiring.test.mjs` now checks that
Android uses the unified runtime, that SWB1 and CRE1 share an atomic evidence
snapshot, that original owner-message authorization is retained and that local
model outcomes are reported without exposing model text.

### Verification performed

- GitHub source-level interface and wiring assertions: **9/9 passed**.
- Isolated Kotlin/JVM type-check of the reconstructed cognitive runtime
  against matching minimal interface stubs: **passed** (Kotlin 1.9).
- Isolated Kotlin/JVM compilation and real disk/reload smoke test of the
  metadata-only runtime journal: **passed** (21 outcomes reduced to 16).
- A full Android/Gradle build and real on-device chat test have **NOT** been
  completed for this commit. The GitHub Actions billing/runner failure causes
  jobs to terminate before compilation; do not claim green CI or offer a new
  APK from this head.

## Required final acceptance after runner recovery

1. `node --test .github/continuum/android-integration-wiring.test.mjs`
2. `node --test .github/continuum/cre-sgd-parity.test.mjs`
3. `node .github/continuum/cre-parity.mjs prepare <temp-dir>`
4. `CONTINUUM_CRE1_PARITY_DIR=<temp-dir> ./gradlew :app:testDebugUnitTest`
5. `node .github/continuum/cre-parity.mjs verify <temp-dir>`
6. `./gradlew :app:lintDebug :app:assembleDebug`
7. Real Android device/emulator: confirm owner fact, ask question, ask pronoun
   follow-up, retract fact, observe abstention, run `Continuum status`,
   reboot, and verify the persistent CRE1 shadow result and event counters.

**Not achieved:** quality-approved CRE1 answer promotion, independent neural
free-text generation, verified local LiteRT-LM operation with a real model,
or fully autonomous code patch synthesis. These must remain separate gated
development milestones.
