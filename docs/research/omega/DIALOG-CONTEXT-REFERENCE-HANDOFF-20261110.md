# Dialog reference correction — first bounded milestone

Integration owner: Cinema root integrator. Target: `cinema/fluid-light-stage-20261110` / PR #715. Work branch: `fix/dialog-context-reference-20261110`, based on local root `4ac9c9b94b9fb5e391646e2e99e986db32ec7997` (published root checkpoint `ae3ce34b5aff285ed8c569ca2844975e34131a86`). Avatar commit `6d29411e2b96b5fcc1514a86728d9ad4455c1006` remains separate.

## Verified defect and live effect

The existing `LocalConversationContext.inspectRequest` strips a leading article from explicit corrections. `Nein, ich meinte den davor.` consequently became concrete focus `davor` and returned `SCOPE_UPDATED`; `AgentActivity` then acknowledged that as the corrected subject before model/tool routing. The same false acknowledgement occurred for `der von vorher`, `Build von vorhin` and `Agenten von zuvor`.

The existing ambiguity guard now includes `vorher`, `davor`, `zuvor` and `vorhin` as whole words. These unresolved references use the existing `CLARIFY_SCOPE` response and reset contract. No branch, build or agent identity is guessed. Concrete `Build 51`, `Branch Atlas` and compound `Projekt Vorhersehbar` remain accepted. `Ja, mach.` remains outside discourse-control interception; this patch does not grant or modify pending-tool approval.

Call path inspected: `AgentActivity` → `localConversationContext.inspectRequest` → existing clarification/correction branch → existing grounded-context reset → visible clarification. No Activity, reference tracker, memory core, renderer or approval implementation was edited.

## Evidence and limits

- RED: actual original context source + new regression: **6 tests, 1 failure**, expected `CLARIFY_SCOPE`, actual `SCOPE_UPDATED` for `Nein, ich meinte den davor.`
- GREEN: **41 tests, 0 failures**: `ConversationControlTest`, `LocalConversationContextInvalidationTest`, `LocalConversationFollowUpIntegrationTest`, `ResearchDialogueHandoffTest`, `SessionEvidenceIntegrityTest`.
- Runtime source and tests were recompiled using Kotlin/JUnit. Transitive dependencies used the existing actual Android compile classes JAR, pinned below; no substitute context class or extracted implementation. No Gradle run or Android/device interaction in this worktree.
- Existing complete-evidence, explicit reset, session isolation, research separation and no model/tool authority contracts pass.
- Scope is one correction-classification defect in examples A/B. Conversational entity resolution for all A–E, relative build selection, concurrent graphics/voice task tracking and pending approval binding are **not established by this milestone**. Existing ambiguous-correction policy still discards old context and asks for the specific topic.

`PORTIERT`: isolated patch ready for integration. `VERIFIZIERT`: host logic only. `GERÄTEBESTÄTIGT`: **NOT_RUN**. Applicable register boundaries: EXP-07 negative regression evidence, EXP-12 context reset/replay. No durable memory promotion, new core, APK, signing, installation or main merge.

## Reproduction

Use the actual compiled app classes as dependencies and compile the changed production source first on the runtime classpath:

```bash
DIALOG_KOTLINC=/workspace/scratch/5900afa6af29/qa-installed-tools/kotlinc/bin/kotlinc
DIALOG_DEPS=/workspace/scratch/5900afa6af29/device-ai/app/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar
DIALOG_JUNIT=/workspace/scratch/5900afa6af29/qa-installed-tools/gradle-8.11.1/lib/junit-4.13.2.jar
DIALOG_HAMCREST=/workspace/scratch/5900afa6af29/qa-installed-tools/gradle-8.11.1/lib/hamcrest-core-1.3.jar
DIALOG_JSON=/workspace/scratch/5900afa6af29/qa-gradle-home/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar
DIALOG_CLASSES=(ConversationControlTest LocalConversationContextInvalidationTest LocalConversationFollowUpIntegrationTest ResearchDialogueHandoffTest SessionEvidenceIntegrityTest)
DIALOG_TEST_FILES=()
DIALOG_TEST_NAMES=()
for name in "${DIALOG_CLASSES[@]}"; do
  DIALOG_TEST_FILES+=("app/src/test/java/at/heinz/deviceai/cognitive/$name.kt")
  DIALOG_TEST_NAMES+=("at.heinz.deviceai.cognitive.$name")
done
DIALOG_JAR=$(mktemp --suffix=.jar)
"$DIALOG_KOTLINC" app/src/main/java/at/heinz/deviceai/cognitive/LocalConversationContext.kt \
  "${DIALOG_TEST_FILES[@]}" -Xfriend-paths="$DIALOG_DEPS" \
  -classpath "$DIALOG_DEPS:$DIALOG_JUNIT" -include-runtime -d "$DIALOG_JAR"
java -cp "$DIALOG_JAR:$DIALOG_DEPS:$DIALOG_JUNIT:$DIALOG_HAMCREST:$DIALOG_JSON" \
  org.junit.runner.JUnitCore "${DIALOG_TEST_NAMES[@]}"
rm "$DIALOG_JAR"
```

SHA-256 pins:

- `LocalConversationContext.kt`: `8da16a720414bc528f44f32508e45d90e7dcb19c38d01ceade666a4a60e2e294`.
- `ConversationControlTest.kt`: `0f9c6086668dac422d03842ef7f567b7992ba1ad93923ecf08a4db7550561bd8`.
- Existing dependency `classes.jar`: `1a4cd2e8eb0c0e74e38fdc0f7f61fe3e24a2dcaf8c20050ff9ce1c525a5d0a82`.

Next: integrator reviews/cherry-picks, runs the existing Android caller tests under the root build lease, and separately scopes bounded entity/reference work if required. Rollback: revert this milestone; no schema or persisted-data migration exists.
