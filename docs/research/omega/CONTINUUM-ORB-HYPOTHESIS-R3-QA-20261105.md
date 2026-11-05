# Continuum B3 – R3: echter Hypothesenmarker der Originalkugel (05.11.2026)

**Status: SOURCE_PATCHED / HOST_FRAGMENT_6_PASS / ANDROID_JUNIT_NOT_RUN / RELEASE_BLOCKED.**

## Exakt geprüfte Quellen

- C1 #519: `13c37f55789bf67b07beeb37cc1a5035526e623e`.
- Gemeinsame Basis #527: `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3`.
- Isolierter Originalkugel-Import #539: `766a1953fe1b72a890448e38178448d50140fa40`, exakt sechs Pfade.
- R1 #542: `3357c6ac9ad347f93b4aa80690ce8a00df5e2a1c`.
- R2 #550, **Eltern-Head dieses R3-Zweigs**: `e247e1b51bf74880ddfcee6b81596bc36654aaf1`, Renderer-Blob `33c2306ae7f33124228901f1202ef2b3b6a54357`.
- R3 Renderer-Blob: `b13ad7349a1ccfaa9b1e1de383d93363901b9cfc`. `ContinuumPainter.kt` als Schnittstellenprüfquelle: `9de2d2f85f61e4d06b6ae90169acfcb90fc8dac0`.
- #535: `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`; #526: `5a18f74f18d9b7d927f8c622052369496153829f`. Keine Pauschalübernahme. Neuerer eigenständiger C1-Avatar-WIP #544 ist ebenfalls **nicht** diese R3-Abnahme.

## Reproduzierter Signalbruch

`BrainOrbAnimationRenderer` markiert ausschließlich ein tatsächliches `MemoryStored(status=HYPOTHETICAL)` als `effects.hypothesisMarker(now)`. `JarvisSequenceEffects.hypothesisVisible(now)` begrenzt dies zeitlich, `ContinuumBrainSceneView` übergibt das Flag an `ContinuumBrainOrbRenderer.Signals(hypothesis=...)`.

Bis einschließlich R2 hatte `ContinuumBrainOrbRenderer` **keinen** Verwendungsort von `s.hypothesis` außerhalb Datenklasse/Dokumentation: der versprochene violett gestrichelte Hypothesenmarker blieb in der dedizierten Kugelansicht unsichtbar. Andere Effekte der generischen `JarvisSequenceEffects.drawOrbSignals` sind davon nicht automatisch betroffen. Der statische Quellen-NEGATIVCHECK auf dem unveränderten R2-Renderer meldete `STATIC_RED_CONFIRMED`; dies ist **keine** ausgeführte Android-JUnit-RED-Regression.

## Minimaler eigenständiger Fix

In **derselben Originalkugel**, nach Gehirnzeichnung und vor dem vorhandenen Rand-Bloom: exakt zwölf getrennte `strokeArc`-Segmente in `Colors.violetRim`, nur wenn `stable.hypothesis` wahr ist. Null neue Statuswerte, Timer, Events, Shader-Caches, zweite Kugeln oder Änderungen am bestätigten Puls-/Processing-/Recall-Vertrag. Die R1/R2-Phasengrenzen und alten negativen Tests bleiben erhalten.

Zwei neue Regressionen in `ContinuumOrbHypothesisMarkerTest.kt` kontrollieren zwölf Segmentwinkel mit getrenntem Bogen und das Ausbleiben des Markers bei Idle bzw. reiner bestätigter Aktivität. Diese JUnit-Datei wurde in diesem Lauf **nicht** im vollständigen Projekt ausgeführt.

## Durchgeführte / fehlende Gates

- **HOST Kotlin 1.9 / Java 21 (isoliertes exaktes Marker-Fragment, nicht Renderer-Compilation):** 6 PASS / 0 FAIL. Es werden Aufrufsignatur, Proxy-Argumentpositionen, zwölf Bögen, Trennung Idle/Confirmed/Hypothesis geprüft; lokale Quelltranskript-SHA256 `0eb8252a8fa451ae4625a2a0957a4ffbe10b487c0279389ff38fa974b1a9a8e6`.
- **Statische Quellenkontrolle:** R2-Renderer ohne Bedingung `hypothesis` (negativ), R3 mit Bedingung (positiv). Quellstände jeweils über GitHub gelesen.
- **NOT_RUN:** tatsächliches `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.ui.ContinuumOrbHypothesisMarkerTest`, die vorhandenen `ContinuumOrbCoreIntegrationTest`, `ContinuumOrbNonFiniteSignalsTest`, Android-/Robolectric-/UI-/Node-/Backend-/Lint-/Java2D-/GPU-/Fold7-/APK-Gates. Lokaler isolierter Repo-Checkout via GitHub HTTPS DNS-blockiert, Android SDK/Gradle fehlen. Kein CI-Trigger oder Signieren.
- **Keine Ergebnisübertragung:** 184 Host-UI von #535, 3.032 Android von #527 und 60 Numerikfälle von R2 sind keine auf R3 ausgeführten App-Gates.

## C1-Handoff / Integrationsgrenze

C1 muss die neue Quelländerung, nicht den kompletten QA-Zweig oder #535/#526, gegen **seinen** neuesten HEAD und abweichende Renderer-Blobs vergleichen. Erst drei gezielte Original-JUnit-Suites ausführen, anschließend gemeinsame Android-/Node-/Backend-/Lint-Gates derselben eingefrorenen SHA; Native/Java2D/GPU/Fold7 separat messen. Bei Konflikt zu bereits integrierter Originalkugel den Marker selektiv anpassen. Keine fremden ACTIVE-Worktrees, kein `main`, kein Signing/Deploy/Downloadversprechen. Original-Package `at.heinz.deviceai.continuumlocal`, Signer `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, steigender versionCode und verifiziertes HTTPS-Readback bleiben unveränderte Freigabegrenzen.
