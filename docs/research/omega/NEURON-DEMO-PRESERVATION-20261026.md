# NEURON · DEMO safe replay with an existing owner-trained model

Date: 2026-10-26
Review branch: chatgpt/continuum-feedback-process-safety-20261026
Previous user-visible message: "NEURON · DEMO nicht gestartet: Bereits trainierte Beispiele sind vorhanden."

## Reproduced cause, not a crashed model

The active Android AgentActivity.kt deliberately blocks NeuralCommandKind.DEMO whenever lab.lessonCount() > 0. The NeuralDialogue.trainDemo method explicitly requires no pre-existing lessons and would otherwise replace the temporary instance's lessons with its synthetic DEMO_TRAINING. The old screen therefore refuses a trained owner to avoid losing existing data.

## Source fix (review only)

- app/src/main/java/at/heinz/deviceai/learning/NeuralDemoSandbox.kt: creates an independent NeuralDialogue, calls trainDemo on that disposable instance, returns only neural measurements; no old snapshot or owner data can be passed to the helper, and it does not return a saveable model.
- AgentActivity.kt: the DEMO branch never restores saved weights into the demo, calls NeuralDemoSandbox.run, displays synthetic before/after, holdout and out-of-vocabulary measurements, and leaves save=false. Cancellation also prevents adoption. Owner consent prompt now accurately describes non-persistent training.
- NeuralDemoSandboxTest.kt: JUnit source verifies non-empty pretrained state is byte-identical and has identical lesson count and fingerprint after a demo, interrupted demo preserves model, empty-model demo leaves model untouched.
- neuron-demo-isolation-wiring.test.mjs: source-wiring assertions ensure the Activity actually imports the helper, old block is removed, no save or production snapshot update occurs, and the native regression sources exist.
- scripts/continuum-neuron-demo-review-jvm.sh: independent native Kotlin/JUnit runner for LearningSnapshot, TinyNeuralNetwork, NeuralDialogue, the sandbox and the three JUnit test cases, requiring existing local Kotlin/JUnit jars.
- scripts/continuum-research-local-preflight.sh: includes new neural source-wiring Node test and optional native Kotlin suite. It remains fail-closed and never performs an Android release.

## Verification

- 10/10 source-wiring and side-effect invariants verified by reading current exact source files from GitHub, including negative checks for old message / saving and positive checks for new helper and three JUnit regressions. This is STATIC source verification, NOT a JVM or Android compiler success.
- 6/6 native Node TAP-parser tests and 10/10 research evidence-gate tests have been executed locally on existing isolated research files; those runs do not compile Android or prove the new Neuron demo runtime.
- Full Android testDebugUnitTest, the new native Kotlin JUnit suite, Fold7 installation, actual run of the new Neuron Demo button, real CCC1 Docker path and signing: NOT_RUN. The newly added source files are NOT included in the previous frozen v25 owner release manifest and therefore must not be silently signed as version 0.3.24.

## Approval and rollout

The published 0.3.23 owner APK is unchanged and still contains the original blocking message. A future update must preserve the exact application id at.heinz.deviceai.mentorpaired and owner certificate, increase versionCode above any installed update, independently validate all source blobs, build/test, and require owner release approval. Never delete existing training, reset model weights or install an independently test-signed APK over the existing app.

## Reproduce

With existing Kotlin/JUnit jars configured:
CONTINUUM_FABRIC_JVM_DEPS=/path/to/existing/jars bash scripts/continuum-research-local-preflight.sh /external/evidence/path

This tests local Node integration + the two Kotlin source graphs. Before release additionally run full Gradle, manifest inventory and device tests.
