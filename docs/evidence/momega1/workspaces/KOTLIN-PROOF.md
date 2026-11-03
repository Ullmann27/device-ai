# Chief workspace host proof

HOST_INTEGRATION; no Android IPC, model inference or verified coding success.

Production addition is only `chief/ChiefWorkspaceWiring.kt`. The existing
ChiefWorkerPool runs its WorkerJob; the existing bounded AgentMailbox receives
AgentMessage to `chief`. No second scheduler/mailbox/persistence is created.
A trusted injected ChiefWorkspacePort must enforce existing CCC1 authorization,
confinement and cancellation. Typed metadata is correlation, never a grant.

The adapter checks full scope equality, generated workspace ID, evidence digest,
teardown and profile exit range; nonzero profile exits become BLOCKED. One job
cannot execute twice. DONE says a host observation arrived; the message explicitly
keeps `verification=PENDING`. Mailbox rejection/error/scope mismatch invokes a
mandatory delivery callback. Delivery is not durable; caller must surface failure.
CCC1 journals/backlog remain the recovery authority.

Actual host JUnit uses fixed test-only ProcessBuilder commands from
ChiefWorkspaceWiringHostTest to ccc1-workspace-chief-host.mjs, which derives
synthetic task bindings from real MAC-verified CCC1 journals and delegates to
createIsolatedExecutor. Its only verbs are prepare/run/active/cancel and worker
index0/1. Its canonical-job Docker label permits cancellation of only the
correlated runner; the original executor performs final inspect/rm. Model text
supplies no shell command, image, executable or profile expansion.

Results: five focused Kotlin contract tests PASS, two actual host integration
tests PASS. The first host test runs two real Docker profiles via the actual
ChiefWorkerPool and reads two actual AgentMailbox observations. The second
observes one actual running container, cancels through the existing pool, confirms
the trusted Node runner and container stopped, receives CANCELLED in the same
mailbox, and asserts no successful profile receipt exists. Raw receipts, JUnit
and mailbox/cancellation observations are in `chief-host/`.

The focused build compiles only the new adapter/tests using Kotlin2.4.0 and
JDK17 against existing Gradle-compiled real pool/mailbox classes, without stubs.
`kotlin-classpath-source.txt` confirms identical relevant baseline sources;
`kotlin-proof-manifest.json` pins actual class/dependency/source hashes.
No independent-review credit is claimed by this authored proof.

First exact-classpath RED omitted the new adapter and failed on its absent types.
Before that, an exploratory incomplete-graph compile also failed from missing
app dependencies; it is not the RED acceptance claim. An initial 4-case JUnit run
failed because the test read the mailbox immediately after pool.awaitIdle:
the existing pool removes its slot before its report callback finishes. The test
now waits for its delivery latch. This was a test synchronization defect, not a
production pool change. Final logs preserve the matched-classpath RED, compile,
5-case GREEN and two-case real host proof.

Reproduce focused build with the QA-provisioned dependencies and an exact-source
compiled baseline (no download or Gradle invocation in this lane):

```sh
workspace_jars=/workspace/momega1-build-env/jvm-deps
workspace_compile_cp=$(find "$workspace_jars" -name '*.jar' -printf '%p:' | sed 's/:$//')
workspace_runtime_cp="$workspace_compile_cp:/workspace/momega1-android/app/build/tmp/kotlin-classes/debug:/workspace/momega1-android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes:/workspace/momega1-build-env/android-sdk/platforms/android-36/android.jar"
/workspace/momega1-build-env/jdk17/bin/java -Xmx512m -cp "$workspace_compile_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$workspace_runtime_cp" -jvm-target 17 -d /tmp/momega1-workspace-classes app/src/main/java/at/heinz/deviceai/chief/ChiefWorkspaceWiring.kt app/src/test/java/at/heinz/deviceai/chief/ChiefWorkspaceWiringTest.kt app/src/test/java/at/heinz/deviceai/chief/ChiefWorkspaceWiringHostTest.kt
/workspace/momega1-build-env/jdk17/bin/java -cp "/tmp/momega1-workspace-classes:$workspace_runtime_cp" org.junit.runner.JUnitCore at.heinz.deviceai.chief.ChiefWorkspaceWiringTest
CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF=1 CONTINUUM_WORKSPACE_CHIEF_EVIDENCE_DIR=/tmp/fresh-chief-host-proof /workspace/momega1-build-env/jdk17/bin/java -cp "/tmp/momega1-workspace-classes:$workspace_runtime_cp" org.junit.runner.JUnitCore at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest
```

The host image is already pinned in the test harness. The evidence directory must
be fresh. Synthetic fixture repos and runtime keys are ephemeral and are not
committed. There is no production Activity/runtime transport, Android device,
model execution, memory learning or release proof. Real integration needs the
Chief's reviewed caller wiring and independent critic, then exact-source QA.
