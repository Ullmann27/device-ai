# ChatGPT Code50 Recall / QA Handoff — 2026-11-08

## Scope and ancestry

- Repository: `Ullmann27/device-ai`
- Parent integration branch: `codex/continuum-code50-integration-20261107`
- Branch point: `1eb7243ba5bf8693923b35203e8e04088aa4885f`
- Latest integration SHA incorporated: `463462bab64f655ecf64421f7a89cb3f0a8fcfa5`
- Repair branch: `chatgpt/code50-recall-qa-fix-20261108`
- Base-update merge commit: `e00f21f06386ef25ecc706b7c00a270127eef0bd`
- Target: Draft PR #604 back into the Code50 integration branch; no `main` merge.
- Relevant experiments/contracts: EXP-05, EXP-06, EXP-07, EXP-11, EXP-12; owner memory and ToolGate authority are unchanged.

## Root cause

`Ctx3StateTelegram` is optional diagnostic context. A telegram longer than 3,000 characters made `LocalGroundedRecall.boundedOrNull(...)` return `null` even when the exact answer and provenance were still inside their independent bounds. `AgentActivity` then treated the complete model handoff as unsafe and returned only the deterministic recall, unintentionally suppressing the existing grounded local-model synthesis path.

The exact owner recall was never corrupted or truncated. The defect was that optional telemetry could veto a valid answer/provenance envelope.

## Changes

1. `app/src/test/java/at/heinz/deviceai/cognitive/LocalGroundedRecallTest.kt`
   - Replaced the old expectation that an oversized telegram invalidates the entire recall.
   - Added a regression asserting that valid answer/provenance survive blank or oversized optional telegrams and that the telegram is omitted.
   - Commit: `602510d244d6ce2226fa3aa7d9f1738febc73957`.

2. `app/src/main/java/at/heinz/deviceai/cognitive/LocalGroundedRecall.kt`
   - Mandatory exact answer/provenance bounds remain fail-closed.
   - Blank or oversized optional `stateTelegram` is now dropped before constructing `LocalGroundedRecall`.
   - No truncation, no new fact authority, no persistence, and no ToolGate change.
   - Commit: `543fb4a839332095808bc1bbf888812e3d7ab0a4`.

3. `.github/continuum/grounded-recall-android-wiring.test.mjs`
   - Updated the static Android wiring contract to the same rule.
   - It still requires the exact non-model fallback for invalid mandatory bounds.
   - It now requires optional telegram omission and continued `runLocal(...)` routing for valid recall/provenance.
   - It explicitly forbids the removed all-or-nothing telegram predicate.

4. `.github/workflows/continuum-code50-full-qa.yml`
   - Removed because `AGENTS.md` and `CLAUDE.md` explicitly forbid GitHub Actions as APK/CI acceptance for this repository.
   - Commit: `fcd744e4598c27a608e1dc09b1b1a547dbcfad4e`.

## Verification status

### A. Focused local Kotlin semantic check — PASS

The exact changed `LocalGroundedRecall.kt` source was compiled with Kotlin 1.9.0 on JVM 21 in an isolated local harness. A minimal `JSONObject` compatibility stub was used only because the full Android dependency graph was not present. The check verified:

- valid telegram retained;
- oversized telegram omitted without dropping answer/provenance;
- blank telegram omitted without dropping answer/provenance;
- blank/oversized mandatory answer or provenance rejected;
- direct construction with oversized telegram still rejected by the data-class invariant;
- rendered output omits `state_telegram` when the optional value is dropped.

Result markers:

```text
FOCUSED_LOCAL_KOTLIN_CHECK=PASS
mandatory_answer_provenance_bounds=PASS
optional_telegram_omission=PASS
exact_recall_preserved=PASS
```

This is a focused class-level semantic check, not an Android Gradle build or device test.

### B. Focused Node source-contract reproduction — PASS

The updated `.github/continuum/grounded-recall-android-wiring.test.mjs` was executed with Node 22.16.0 against the exact changed Kotlin class and the exact relevant `AgentActivity` source block fetched from the repair branch.

```text
# tests 1
# pass 1
# fail 0
```

This proves the repaired source contract and regex assertions are internally consistent. It is not the full repository Node suite.

### C. Intended focused Android/Gradle command — NOT RUN

```bash
gradle --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true \
  -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=50 \
  :app:testDebugUnitTest \
  --tests at.heinz.deviceai.cognitive.LocalGroundedRecallTest
```

- Render service: `continuum-code50-recall-qa-20261108`
- Render deploy: `dep-dbnual9srm7s73afh73g`
- Source commit selected by Render: `602510d244d6ce2226fa3aa7d9f1738febc73957`
- Result: `BLOCKED / NOT_RUN` before repository checkout and before Gradle.
- External cause: Render reported that the workspace had exhausted build-pipeline minutes for the current billing period.
- Therefore this is not a RED or GREEN Android result.
- GitHub Actions was not used as acceptance evidence and its branch workflow has been removed.

## Required next verification

1. On the approved Render Android QA host or a demonstrably working full local/on-device toolchain, bind the exact repair SHA and execute the focused Gradle test above.
2. Run full Code50 Android compile, unit/UI tests and lint.
3. Run the complete Node/backend regression gates separately.
4. Only after source QA passes, perform the real Fold7 15-second avatar proof, thermal/motion checks and speech checks.
5. No signed APK, installation, device verification or release is claimed by this branch.

## Rollback

Revert the Draft PR #604 commits or close the PR. The Code50 integration branch and `main` remain unchanged until an explicitly reviewed merge.
