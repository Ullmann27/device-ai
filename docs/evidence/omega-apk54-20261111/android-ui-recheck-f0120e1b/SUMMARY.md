# Code54 UI-Pflichtlauf + NewApi-Lint auf `f0120e1b2` (HOST)

- Quelle: `f0120e1b231efc31237a0a13aa59f9cbcc3c490a`, Arbeitsbaum sauber, `sourceUnchanged: true`.
- Kommando (gleich wie `harness/run-android-phase.py --phase ui-recheck`, Pfade an diese Umgebung angepasst):
  `./gradlew --no-daemon --continue -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true
  -PcontinuumOwnerArm64=true -PcontinuumStandaloneVersionCode=54 -PcontinuumLocalStandaloneQa=true
  -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=<dir> -I harness/test-memory.gradle
  -I tools/capture/force_capture.gradle -I harness/omega-ui-recheck.init.gradle :app:testDebugUnitTest :app:lintDebug`
  (`CONTINUUM_QA_HEAP=3072m`, `CONTINUUM_QA_METASPACE=896m`, JDK 21, Gradle 8.11.1).
- **Ergebnis: 25 Fälle, 25 PASS, 0 FAIL, 0 SKIP** (`OwnerAppActionWorkSceneUiTest`, `OwnerAppPlanningContextUiTest`,
  `OwnerObjectStateConversationUiTest`), `suite_end` vorhanden. Damit sind die 14 Fehler von `c163641b` auf diesem Stand nicht mehr reproduzierbar.
- **NewApi-Lint: „No issues found“**, BUILD SUCCESSFUL.
- Nicht enthalten: volle Android-Suite, APK-Bau, Fold7 (NOT_RUN zum Zeitpunkt dieses Belegs).
