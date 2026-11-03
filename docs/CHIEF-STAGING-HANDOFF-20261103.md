# Continuum Ω – Chief-Team-Staging (03.11.2026)

## Nachweislevel und Zweck
**SOURCE_ONLY – der integrierte Quellbaum und neue Tests sind auf GitHub gepusht; keine Host-/Android-Ausführung wurde in dieser Chat-Sitzung durchgeführt.** Dieser eigene Branch baut ausschließlich auf `integration/continuum-grok1-369-372-20261103` (Code39-Nachfahre `9d0596b68563a1f9afe65da86864f52f9106dc5c`) auf. Kein Merge nach `main`, keine Änderung von Paket/Signatur/Render/Owner-Dateien, keine releasefähige APK.

## Übernommene Quellstände (blobgenau)
- Chief-Architektur #379 + kritische Reparaturen #381 + nachweisgebundene Delegationsauswahl #383; Quelle `07be97934af0fc8d43cc346ee420138867fb71fa`. Nur `ToolRegistry.kt`, Chief-Produktivcode, Chief-Tests und `docs/CHIEF-PATTERN.md`; alle aus Quelle unverändert übernommen.
- Routinen-/Mailbox-Kern #376 + Reviewfixes #382; Quelle `503402e953fd8f457cfde399806eb7a67456c0e7`. Zwei Kotlin-Dateien einschließlich Tests übernommen.
- **14 Dateien** auf der vorherigen #375-Basis über einen isolierten Baum-Commit vereinigt; keine fremden Kopien von `TEST_RESULTS.md` überschrieben.

## Tatsächlich vorhandene Funktionsgrenzen
1. `ChiefDelegationCoordinator` erlaubt ausschließlich registrierte, per trusted Callback bestätigte Spezialisten (max. 20). Ein typisiertes `ChiefDelegation`-Objekt wird gegen Fähigkeiten und Tool-Allowlist geprüft. Es delegiert über den realen `ChiefWorkerPool.submit`; die Worker-Factory kann den Kandidaten über `specialistForTask(taskId)` auflösen.
2. `ChiefWorkerPool` nutzt maximal vier Worker-Threads, liefert ein Ergebnis pro Auftrag, besitzt Cancel-/Timeout-Signale. Der zugehörige `ChiefJobs`-Adapter bricht den *eigenen* BoundedAgent/ToolGate ab. Nur wenn `modelOwnedByJob=true` **und** wirklich eine exklusiv zugewiesene Sitzung vorliegt, darf er native `cancelGeneration` auslösen; shared Gemma darf kein globales, dauerhaftes Cancellation-Flag abbekommen.
3. `RoutineBook` beansprucht fällige Routineausführungen einmalig (`due/triggered → report/release`), `AgentMailbox` ist hinsichtlich Empfängerzahl, Nachrichten und UTF-8-Bytes begrenzt. Sie enthält Daten, keine Werkzeugfreigaben.
4. `recordVerifiedOutcome` kann das spezialisierte Auswahl-Ranking nur nach unabhängigem `verifyOutcome`-Callback, fertig gemeldetem Auftrag und einmaliger gebundener Quittung verändern. **Default: kein Verifier, kein Lernen.** Es ist vorläufig RAM-basiert; kein dauerhaftes Modelltraining und keine dauerhafte Strategiedatenbank.
5. Vorhandene Unit-Tests aus den Chief-Zweigen sowie neue sicherheitsrelevante Regressionen liegen vor. **Nicht als ausgeführt behaupten.**

## P0 vor Integration in den Geräte-Laufzeitpfad
- HOST-JVM: `./gradlew --no-daemon :app:testDebugUnitTest --continue`; Node: gesamtes passendes Testprofil einschließlich .github/continuum; `:app:lintDebug`; `:app:assembleDebug`. Exakte Logs/XML, Exit-Codes, verwendeten Git-SHA und Basisvergleich dokumentieren.
- Besonderes Review: Shared-/Exclusive-Modell-Cancellation, niemals post-cancel neue Tool-Aufrufe oder unbewiesene „gestoppt“-Meldung; zeitgleiche Worker-Aufträge; Mailbox-Last/UTF-8; Routine-Mehrfachdispatch; Worker-Tool-Allowlist/Owner-Freigabe; fehlgeschlagene Attestation darf Lernranking nicht erhöhen.
- End-to-End: Chief-Modell muss natürliche deutsche Aufträge erst in **validierte typisierte Intent-/Delegation** übersetzen. `ChiefDelegationCoordinator` mit realer ToolGate-Policy, Worker-Factory und dem Hauptchat verdrahten; danach `AgentMailbox` Worker→Chief und Android-JobScheduler/PC-Worker kontrolliert integrieren. Einzelne testbare Android-Geräte-Leseaktion (READ_ONLY) als erstes Endziel, keine fremden Effekte.
- Review der bestehenden PRs #371 (Gedächtnis/Bestandsdaten), #372 (Journal-Nachrichten), #373 (CCC1-Proof) bleibt erforderlich. Die alten 1.856 Host-Tests in #375 belegen **nicht** diesen neuen Staging-Commit.
- Geräte-Freigabe erst nach originalsigniertem, prüfbarem Code40-Build über die bisherige Owner-Render-Pipeline und ausdrücklicher Eigentümerabnahme.

## Nicht erreicht
Kein tatsächlicher Android-Hintergrundworker, kein auf dem Fold7 gemessener parallel arbeitender 20-Spezialisten-Betrieb, kein dauerhaftes Lernmodell, kein vollständiges selbstständiges CCC1-Patch-/Test-/Reparatur-E2E und kein neuer animierter 3D-Avatar. Diese Behauptungen wären zum jetzigen Zeitpunkt unzutreffend.
