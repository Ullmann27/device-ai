# Android general coding intake – 2026-10-24

Parent integration branch: `sol/continuum-autonomous-coding-20261024`; initial inspected HEAD `23d76d57370fbca82d399feceb6d2e66162134ab`. No subagent commits, release, signing, package/version, launcher or transport changes. Parent owns final commit.

## Implemented scope

- New `app/src/main/java/at/heinz/deviceai/ContinuumCodingTask.kt`: typed task kinds BUGFIX/FEATURE/REFACTOR/TEST/COMPILER/DEPENDENCY; full source commit; explicit 1–3 selected JS/Kotlin source paths; concrete goal and 1–8 acceptance criteria; fixed node/android chain; limits 1–3 model calls / 2048–24000 context characters. Protected tests/build/control paths rejected. TEST/DEPENDENCY are investigation drafts, not writable test/build permissions.
- Incomplete `Draft` is distinct from complete `ContinuumCodingTask` and explicit `Authorization`. JSON uses existing `DevelopmentBacklog.canonical` and SHA-256. Snapshot copies are unmodifiable; caller mutation cannot alter reviewed grants.
- Existing `ContinuumCodingConversation` APIs for the two templates preserved. General coding requirements request missing Commit/Dateien/Akzeptanz fields, never select a template or claim execution. Typed field entry creates a draft; separate `approveCodingTask(revision, fingerprint, ownerConfirmed)` requires an explicit caller event, rejects stale revisions/fingerprints and consumes one snapshot. Free-text “ja genau” cannot authorize or restore a task.
- Existing main `AgentActivity` development center/dialog have a `CODING-AUFTRAG ENTWERFEN` entry. A small form checks the fields and copies draft JSON. No worker/model/transport hook or execute button exists. It explicitly states that source has not been read, execution needs worker configuration and separate owner confirmation. Editing fields invalidates the copyable snapshot.

## Experiments and compatibility

EXP-02/05/07/08/11/12: source integrated intake; no research/model promotion or execution claim. Canonical backlog remains the only backlog. Existing two-template approval path, existing developer assistant, mobile bridge, package identity and launcher stay intact. Canonical wire fields mirror `.github/continuum/coding-task.mjs`; Android path admission is deliberately at most as broad as current Node task+critic admission. Worker still must validate semantics, exact source, capability and explicit authorization separately.

## Actual checks

1. RED: focused existing Conversation test module compiled with Kotlin 2.4.0 and ran JUnit 4.13.2. 14 tests, one expected new assertion failure: a Kotlin bug request received “Welche App meinst du: Notizen oder Zähler?”. `/workspace/scratch/android-intake-red/junit.txt`.
2. New Task tests were authored before implementation; missing typed API compilation failure recorded in `/workspace/scratch/android-intake-red/task-missing-api.txt` (missing feature, not a runtime behavioral verdict).
3. GREEN: real focused Kotlin compilation + JUnit: **21 tests passed** (14 Conversation, 7 Task), including all 12 original template tests, source/path/chain/resource negatives, exact revision/hash confirmation, single use and mutable-caller-list isolation. `/workspace/scratch/android-intake-green/compile.log`, `/workspace/scratch/android-intake-green/junit.txt`.
4. `scripts/android-kotlin-unit-check.sh /workspace/scratch/android-intake-full` attempted with installed compiler/JUnit/Android compile jars. Preliminary script reports **KOTLIN_COMPILE_CHECKED** with zero compiler errors outside unavailable LiteRT-LM sources (not an APK). Its full main+stub compile subsequently failed with **java.lang.OutOfMemoryError: Java heap space** during code generation; no whole-suite Kotlin JUnit result was produced. `/workspace/scratch/android-intake-full/compile-check.txt`, `/workspace/scratch/android-intake-full/kotlinc-main.log`, `/workspace/scratch/android-intake-full-run.log`.
5. Model/toolchain agent was informed that Android source is stable and is performing real Gradle unit/lint checks on an external source snapshot. Result must come from that agent; not claimed here.

The focused command compiles `StrictJson.kt`, `DevelopmentBacklog.kt`, `ContinuumOwnedProjectPlan.kt`, `ContinuumCodingTask.kt`, `ContinuumCodingConversation.kt` and the two targeted test files with `/workspace/scratch/toolchains/kotlinc/bin/kotlinc`, `-jvm-target 17`, JUnit 4.13.2; it runs `org.junit.runner.JUnitCore at.heinz.deviceai.ContinuumCodingConversationTest at.heinz.deviceai.ContinuumCodingTaskTest` with Kotlin stdlib and Hamcrest 1.3.

Exact focused commands from repository root:

```sh
/workspace/scratch/toolchains/kotlinc/bin/kotlinc app/src/main/java/at/heinz/deviceai/learning/StrictJson.kt app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentBacklog.kt app/src/main/java/at/heinz/deviceai/ContinuumOwnedProjectPlan.kt app/src/main/java/at/heinz/deviceai/ContinuumCodingTask.kt app/src/main/java/at/heinz/deviceai/ContinuumCodingConversation.kt app/src/test/java/at/heinz/deviceai/ContinuumCodingConversationTest.kt app/src/test/java/at/heinz/deviceai/ContinuumCodingTaskTest.kt -cp /workspace/scratch/android-deps/junit-4.13.2.jar -jvm-target 17 -d /workspace/scratch/android-intake-green/classes > /workspace/scratch/android-intake-green/compile.log 2>&1
java -cp /workspace/scratch/android-intake-green/classes:/workspace/scratch/toolchains/kotlinc/lib/kotlin-stdlib.jar:/workspace/scratch/android-deps/junit-4.13.2.jar:/workspace/scratch/android-deps/hamcrest-core-1.3.jar org.junit.runner.JUnitCore at.heinz.deviceai.ContinuumCodingConversationTest at.heinz.deviceai.ContinuumCodingTaskTest > /workspace/scratch/android-intake-green/junit.txt
```

## Limits / next / rollback

No arbitrary-source editing, general worker transport, APK execution, device UI exercise or on-device model result is established by this intake. A complete draft is not a dispatched task. A confirmed snapshot is not worker capability or installation approval. Follow-up: attach this same canonical task contract to an explicitly configured worker and revalidate exact fingerprint/commit/scope before model or patch actions; preserve the existing approval gates.

Rollback is limited to removing the new Task source/test, reverting Conversation+test additions and the two new AgentActivity entry points/dialog. No persistent data migration was introduced.
