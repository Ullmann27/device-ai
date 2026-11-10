# Dialog reference correction — first bounded milestone

Current result: unresolved temporal corrections clarify; an exact reply to the pending research ASK request now uses its existing ticket. **87/87 Android host tests PASS**, including eight new actual Activity UI tests. Device/real microphone/network acceptance remains NOT_RUN. Final evidence: `docs/evidence/dialog-research-consent-20261110/RESULT.json`.

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

## E checkpoint — pending research reply (WIP / UI NOT_RUN)

The first milestone is published as `42ad3c9c6bd1a5df2d996948fbf5855429a572a9`, exact tree `0942cddca77003f0c066546f67167067a3265ee5`, in Draft PR #720. Direct Git push had no HTTPS username; the authorized connector published the source instead.

The owner then requested actual pending-research reply support. This checkpoint adds an optional `Im Chat antworten` route to the existing ASK dialog. Strict `Ja, mach` / decline wording consumes only its current `WebToolApproval` through the same `respond` → `completeApproval` path. Session/foreground checks, nonce/epoch/expiry and one-shot authority stay in the existing code. The Send path handles a bound reply before busy-STOP; ambiguous/unrelated text invalidates the presentation binding. Generic/destructive tool confirmations are unchanged.

Parser: **2 host tests PASS**. Existing exact-scope approval integration plus the new unbound-affirmation regression: **34 host tests PASS**, using recompiled actual consent/gate/context sources and the pinned dependency JAR. `ResearchChatApprovalUiTest` covers actual dialog, Send, waiting-turn, decline, expiry, replacement, session/foreground revocation and ambiguity. Its Android/host UI run is **NOT_RUN**, waiting for the root build lease. This WIP checkpoint is not a verified feature or device result. The root's `cancelLocalRequest` / `clearTaskEffects` methods are intentionally untouched.

## Final E verification — supersedes the WIP status

- Original Activity + new UI tests: **7 tests / 7 expected failures** on absent `Im Chat antworten` control; no compile or fixture errors.
- Bound reply implementation: **86/86 PASS**, including real dialog, Send and `sendCurrentInput` entry. Replies close only their own approval modal; real `pause()` → `resume()` cannot restore a revoked ticket.
- Additional overlap RED: while another tool confirmation was open, the research reply was still consumed (`expected false / actual true`). The existing non-web confirmation entry now invalidates only the research chat binding before presenting its unchanged tool gate.
- Final Android compile + JUnit/Robolectric: **87 tests, 0 failures, 0 errors, 0 skipped**, 13 suites. The eighth UI test covers both ordinary and destructive confirmation overlap and proves the successor dialog remains open and unapproved.
- Exact research request/mode/nonce/epoch/expiry authority remains in `ApprovedWebToolGate.completeApproval`. Bare/conditional/quoted/extended replies are not grants; unrelated/ambiguous submissions invalidate the presentation binding. Generic/destructive buttons still require their original confirmations.

Executed under the root's exclusive build lease with JDK/SDK from `/tmp/continuum-toolchain`, existing Gradle 8.11.1, `--no-daemon --max-workers=1`, in-process Kotlin, `-PdeviceAiMentorPreview=true -PcontinuumOwnerLocalQa=true -PcontinuumOwnerLocalVersionCode=52 -PcontinuumUiWidgetTests=true`, task `:app:testDebugUnitTest`. Final selected suites are recorded exactly in RESULT.json. Logs are in `/workspace/scratch/5900afa6af29/dialog-research-final-green/run.log`; baseline/overlap logs are in the adjacent `dialog-research-ui-red` and `dialog-research-overlap-red` directories. RESULT.json pins every changed runtime/test source SHA-256 and the unchanged original avatar asset.

This verifies the actual app input/dialog/gate wiring on the Android host. It does not establish real ASR, network-provider output, Fold7/native pixels, broad A–E referent resolution or release acceptance. Root-owned STOP/scene methods remain unchanged. Integrator: cherry-pick this branch's source commits and rerun the focused combined-source caller checks before any release decision.
