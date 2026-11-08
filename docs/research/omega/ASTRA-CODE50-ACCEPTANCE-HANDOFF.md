# Code50 acceptance repair — 2026-11-08

Status: focused host QA PASS; full unit/UI/lint acceptance pending. Integration source: `0c59dcc7b249d7eb6bfc9fb21d2306f814105ed6` (PR #603). Work branch: `chatgpt/code50-acceptance-repair-20261108`, draft PR #627. The checkpoints below are chronological; later actual runs supersede earlier NOT_RUN statuses.

## Observed blockers

- A new unresolved review on #603 reports that fuzzy implicit app requests can offer Choose/Confirm before the conversation fallback, which then swallows launch/cancel receipts.
- Actual local run: `node --test .github/continuum/owner-cockpit-voice-original-orb.test.mjs` => 2 tests, 1 PASS, 1 FAIL. The failed contract still expects `preserveLocalContext = false`, while the same-session integration now defaults it to true.
- Source inspection found a stale three-argument `shouldClarifyMessage` test call after the exact-match argument became mandatory. Android compilation has not yet run.
- Render service `srv-dbnual1srm7s73afh69g` still lists only the failed 2026-11-08T02:58:29Z deployment of the older recall repair source, not an acceptance run of #603.
- First local Gradle wrapper attempt failed before Gradle startup with Java network routing; local toolchain setup is in progress.

## Bounded work

1. Reproduce the app routing/receipt bug with behavioral tests through existing ToolGate and OwnerAppActionFlow.
2. Enforce exact installed-app evidence before selection/launch for ambiguous implicit requests; preserve explicit app selection and all tool receipts.
3. Repair stale test wiring without reverting same-session context preservation.
4. Run the requested Node contract, focused Android tests, then full unit/lint where the toolchain works. Save exact-source results.

## Boundaries and handoff

Relevant experiments: EXP-02, EXP-07, EXP-11. Existing ToolGate, receipt formats and canonical conversation routing remain authoritative. No new worker, memory store, app action permission or signing path. No main merge, release or device-installation claim.

Fold7 voice, 15-second avatar proof, FUI/thermal acceptance and original-signed Code50 APK remain NOT_RUN. Rollback: revert only this repair branch's commits. Next entry: inspect current #603 and repair branch heads, read this file, then run the targeted tests before claiming a fix.

## Test checkpoint

- Updated both stale Node assertions to the integrated `preserveLocalContext = true` default; explicit invalidation and cockpit retention assertions remain enforced. Actual combined run: 5 tests, 5 PASS, 0 FAIL, 0 SKIP.
- Updated the stale `shouldClarifyMessage` test to supply exact-match evidence.
- Added real-Activity regressions for implicit fuzzy discovery and explicit fuzzy cancellation. These new UI regressions are NOT_RUN at this checkpoint; the production routing bug remains unfixed.
- Local Gradle 8.11.1 and Android SDK 36 are available. The source's QA task-name guard rejects CLI `--tests`, so focused filtering uses an external Gradle init script without changing the project guard. The next attempt stopped before compilation because the preinstalled Java 21 runtime has no compiler. A checksum-verified JDK 17 setup is in progress.

## Compile repair checkpoint

- Temurin JDK 17.0.21+9 is installed; archive SHA-256 `8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711` matches Adoptium metadata. SDK 36/build-tools 35.0.0 and Gradle 8.11.1 run locally.
- Real Gradle run on `86333b3c1a61e7b765114bafcfd3db48ccc9afcf`: `compileDebugKotlin` FAILED with three diagnostics. `turnId !in ConcurrentHashMap` is rejected because Java `contains` means value lookup; two Evidence member accesses lacked an explicit null guard. No JUnit test executed in this failed compile.
- Minimal compile corrections: explicit `containsKey(turnId)` and `evidence != null && count > 0`. These corrections still require the next Gradle run.
- Related cockpit Node checks found one further stale geometry contract: the pane width no longer adds the removed shell rail. The assertion now follows the current available-pane contract. No layout source or safety gate changed.
- Render's historical log confirms exhausted pipeline minutes at 2026-11-08T02:58:29Z; its configured branch is the older recall RED fixture, not current Code50. No new Render deployment or billing change was requested.

## App routing red/green checkpoint

- Main compilation passed after the two compile corrections. Test compilation then exposed one missing `assertNull` import in `CockpitAnimationTriggersTest`; adding that import preserves its real PDF page-count assertion.
- Actual RED: `d6b3f8ce9bb44aab1af667020a24ff9ad5809cc0` plus that import, 22 tests, 21 PASS, 1 FAIL, 0 SKIP. The real Robolectric Activity showed an app selection dialog for `Ich brauche Sig`; `implicitFuzzyNeedReturnsToConversationBeforeAppSelection` failed at the no-dialog assertion. Explicit fuzzy cancellation already passed.
- Root correction: `OwnerAppActionFlow` now requires fresh exact installed-app evidence before Choose/Confirm for implicit requests. `AgentActivity` supplies the intake flag and does not save rejected fuzzy candidates as app pronoun context. Explicit fuzzy opening still supports selection, concrete ToolGate approval/rejection and cancellation receipts.
- Added ToolGate regressions for fuzzy implicit needs/message requests, exact ambiguous cancellation, and approved/rejected explicit fuzzy launch. Actual GREEN: 25 tests, 25 PASS, 0 FAIL, 0 SKIP; Gradle `BUILD SUCCESSFUL`. This includes Airbus2100CockpitAndCountdownTest, Argon4CockpitAndBudgetRegressionTest, AvatarMorphMapperComparisonTest and the two Activity regressions.
- Related Node contracts: 33 tests, 33 PASS, 0 FAIL, 0 SKIP across nine cockpit/voice/context/layout files. These are scoped contracts, not the whole repository's Node suite.
- Reproduce the focused Android run with JDK17, Android SDK36/build-tools35.0.0 and Gradle8.11.1: `./gradlew --no-daemon --max-workers=2 --console=plain -I scripts/continuum-code50-focused.init.gradle -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=50 -PcontinuumUiWidgetTests=true :app:testDebugUnitTest`. Configure the host's authorized proxy and CA trust when needed. The init filter avoids the existing QA task guard's rejection of CLI `--tests`; the guard is unchanged.
- Next: clean exact-SHA full `:app:testDebugUnitTest :app:lintDebug` with the same properties and no init filter. Existing lint scope is `NewApi`. Record skips explicitly, including JDK21-only LiteRT lifecycle tests under JDK17.
- #603 remains unchanged; its review is not resolved merely because #627 contains a tested correction. Main, signing credentials and release lanes are untouched. Fold7 and signed-APK gates remain NOT_RUN.
