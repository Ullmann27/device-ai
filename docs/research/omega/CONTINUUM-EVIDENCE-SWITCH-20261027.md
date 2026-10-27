# Continuum – evidence-switch counterprobe, constrained Sol final round

## Source identity, non-negotiable boundary

Source base: exact integration commit `7665a3dcb02f9bd1fa79cb6d4b63e95b147eb9bb` (`integration/continuum-owner-v26-live-candidate-20261027` as examined). Full Android conversation entry: `AgentActivity.runContinuumDialogue` → `CognitiveDecisionBridge.respond` → `ContinuumCognitiveRuntime.reply` → `ContinuumLearningCore.replyWithEvidenceInConversation`. CRE1 already observes and, only if independently qualified, may reorder **the same authorized candidates**. There is no second chat or additional Gemma engine.

The user has revised priority to: finish one verifiable existing conversation chain before activating another research module. **No CDX1 deployment is proposed or included in this branch.** The earlier separate experimental branch `sol/continuum-experiment-bridge-20261027` contains preliminary CDX1 changes and is **not a validated output of this counterprobe; do not merge or present it as success**. PR #193 is separate Gemma-reopen work and is untouched.

## One fixed, falsifiable counterprobe (four pairs, eight turns maximum)

Actual repository-native JVM test `app/src/test/java/at/heinz/deviceai/cognitive/ContinuumEvidenceSwitchProbeTest.kt` invokes the same production `CognitiveDecisionBridge(ContinuumCognitiveRuntime(...),null).respond(question)` path, in disposable temporary directories and separate conversation IDs; no real owner facts or model weights. `null` is the real absence of a JVM-native Gemma engine, **not a model-output mock**.

| Pair | Identical query within pair | Authorized evidence change | Hard failure criterion |
| --- | --- | --- | --- |
| Entity | `Wo legt Projekt Orion seine Proben ab?` | `Kammer Rot` → `Kammer Blau` | Old room cited, missing exact source ID |
| Negation | `Ist die Testtür Delta geöffnet?` | `ist geöffnet` → `ist nicht geöffnet` | Negation lost/reversed |
| Similar entity | `Wo steht Gerät Kappa 7?` | `Raum Silber` → `Raum Kupfer`; `Kappa 70` distractor | Distractor as evidence or old room |
| Revocation | `Welchen Schlüssel nutzt Labor Sigma?` | `Schlüssel Grün` revoked, then `Schlüssel Lila` confirmed in a fresh conversation | Revoked source ID or old answer survives |

Every test turn logs real deterministic answer, source IDs, CRE1 state and proposed shadow-only score order, qualified-manifest gate, SWB1 model hash, elapsed milliseconds and `modelId=NOT_RUN gemmaCalls=0`. Tests require an exact and unique approved owner-memory source, provenance, exact expected text, absence of the forbidden fact, zero model calls and no CRE1 promotion. If any retrieval fails these checks, the test correctly **FAILS**; the assertions must not be weakened to manufacture a pass.

**CRE1:** In these fresh isolated directories, there is no approved promotion manifest. `UNTRAINED_SHADOW` and the manifest gate are expected. The proposed ordering is diagnostic only and cannot change answer/source authority. Tests do not claim a new independent holdout or training.

**Gemma:** The owner's Fold7 screenshot proves the original Gemma 4 E4B `.litertlm` file can report loaded; it does not give this JVM/connector session access to native inference. Zero model calls are made. An actual real Gemma evidence-switch run in the **existing Android app** remains `NOT_RUN`, not pass. No new model, download, provider or emulator is introduced. If tested later with the genuine on-device model, keep at most these four pairs and eight short calls, record model identity and latency, and treat an unavailable inference engine as `NOT_RUN`.

**CDX1:** Intentionally `NOT_RUN`, because it comes only **after** this first controlled experiment is independently executed and assessed. If later justified, use at most four synthetic questions with paired/mismatched/irrelevant evidence on isolated checkpoint copies; never write or promote owner weights. A mere output difference is not an improvement.

## Actual executed actions and observations

- Exact GitHub HEAD read and real production call sites inspected: `AgentActivity.kt`, `CognitiveDecisionBridge.kt`, `ContinuumCognitiveRuntime.kt`, `ContinuumMemoryDialogue.kt`, `ContinuumLearningCore.kt`, `ContinuumCognitiveRuntimeTest.kt`, `CognitiveDecisionBridgeTest.kt`.
- GitHub commit of four-pair regression source completed.
- Read-back source checks: **14/14 static invariants pass**; this is a source audit only, not a test-runtime outcome.
- Full `./gradlew :app:testDebugUnitTest` and physical device/Gemma inference: **NOT_RUN**. No JUnit test transcript exists for these eight turns yet. The Render workspace previously rejected full QA because its build-minute quota was exhausted; do not reuse historical Android QA from another commit.
- No user fact, model checkpoint, private app data, `main`, release APK or owner signing key changed.

## Required independent command (not executed here)

```sh
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.ContinuumEvidenceSwitchProbeTest'
```

Capture unedited JUnit XML and complete stdout `EVIDENCE_PAIR` lines; check exactly eight turns and four pairs, no mock native model success. Investigate and repair any FAIL before changing production retrieval. Do not interpret the same published fixture as a fresh unseen holdout.

**Result:** Test harness and bounded provenance gate prepared; genuine per-turn outcomes remain **NOT_RUN**. No neural or language-understanding improvement is claimed.
