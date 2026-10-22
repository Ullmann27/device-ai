# Device AI Continuum – Intelligence Lab v1 / echter CDX1-Antwortaudit
Stand 22.10.2026 · EXP-04, EXP-05, EXP-12 · SOURCE_INTEGRATED (Review-only).

## Warum
Der bisherige CDX1-Gate prüft Trainings- und Audit-Cross-Entropy, aber **nicht**, ob der generierte Text eine unbekannte Frage richtig beantwortet. Ein fallender Verlust ist kein Beweis für korrekte Aussagen. Die erste Produktlücke ist eine reproduzierbare, eigenständige Antwortmessung, nicht noch eine größere Trainingszahl.

## Implementierung
- `ContinuumIntelligenceAudit.kt`: fest eingefrorene **20** positiv beantwortbare und **5** aus dem Beleg nicht beantwortbare synthetische Fälle. Andere Roboternamen und teilweise andere Satzstrukturen als `ContinuumCdxLargeCurriculum`; neue Testfragen sind **keine** Android-Owner-Daten oder echte Fakten. Bewertet nur exakte Normalform des Zielwortes; Teiltreffer und angeschmückte Antworten sind falsch. Für negative Beispiele gelten ausschließlich wenige explizite Enthaltungsformen als korrekt; geratenes Faktum zählt als unbelegte Behauptung.
- `ContinuumIntelligenceOnDevice.kt`: liest den **aktiven bestehenden** SWB1/CDX1-Paarcheckpoint über `ContinuumLearningCore.pairedDecoderStatus` und `experimentalPairedDecoderContinuation`; schließt fehlende, korrupte und LKG-Recovery-Modelle aus. Jede einzelne Inferenz muss vorhanden sein. Die Modellidentität (SHA-256, Updates, Binding-Status) wird vor und nach sämtlichen 25 Fällen verglichen; veränderte oder unterbrochene Läufe liefern keinen gültigen Score.
- `ContinuumLearningCore.intelligenceAudit` verwendet denselben process-weiten `CORE_LOCK` wie die existierende Lern- und Modellschreibschicht: kein konkurrierendes Training im Prozess während eines Audits. **Keine Checkpoints, SWB1-/CDX1-Gewichte, Trainingscursor, persönlichen Erfahrungen, Cloud-Zustände oder Chatfreigaben werden geschrieben.** Externe andere Prozesse müssen weiterhin ihre eigenen File-Locks einhalten.
- `ContinuumTrainingMonitorActivity`: klar erkennbarer, explizit angetippter read-only Laborknopf im bestehenden Monitor. Eine begrenzte Hintergrund-Thread-Aufgabe (kein UI-Hauptthreadblock), Fortschritt ohne simulierte Modellverbesserung. Bei Pause/Abbruch endet die Prüfung konservativ; Anzeige nur aggregierter Score mit Modell-/Testhash, keine rohen KI-Ausgaben, kein automatischer Start/Training.
- `ContinuumIntelligenceAuditTest.kt`: acht Kotlin-JUnit-Tests für 20+5-Splits, Baseline-/Canary-Verhalten, partielle Antworten, falsche Behauptungen, defekte Hashes, fehlenden Checkpoint und sofortige Stornierung.

## Abnahmeprotokoll
1. **Synthetische Evaluator-Kontrolle** mit bekannter Antwortliste muss 20/20 und 0/5 unbelegte Behauptungen ergeben. Derselbe Antwortenvektor mit fünf erfundenen Behauptungen muss 20/20 und **5/5** unbelegte Behauptungen ergeben und das Forschungsziel verfehlen. Das belegt den **Prüfer**, nicht den Modellgewinn.
2. **Reales SWB1/CDX1-Messprotokoll** erst nach Android-Gradle- und Samsung-Gerätetest. Feste Forschungs-Zielmarke: mindestens 16/20 exakt richtige unbekannte Antworten und 0/5 erfundene Antworten. Eine niedrige Leistung wird ohne Tuning protokolliert, nicht verborgen.
3. `gradle :app:testDebugUnitTest --tests at.heinz.deviceai.learning.ContinuumIntelligenceAuditTest`
4. `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiMentorPreview=true` nur auf korrekt eingerichtetem, isoliertem Buildhost/Render-Preview und mit bisheriger Signaturprüfung.
5. Auf Samsung bei erhaltener Installation den **aktiven Modell-Hash/Updates vor und nach** der manuellen Messung vergleichen. Keine Deinstallation; nur bestehendes signiertes Paket aktualisieren nach eigenständiger Owner-Freigabe.

## Ausgeführte lokale Compiler-/Kontrollprüfungen

Getestete Umgebung: Linux x86_64, OpenJDK 21, **kotlinc 1.9.0**. Beide neuen produktiven Kotlin-Module wurden aus dem Reviewbranch bytegenau lokal reproduziert; die von `git hash-object` ermittelten Git-Blob-SHAs stimmten mit GitHub überein:
- `ContinuumIntelligenceAudit.kt`: `614d75868289e2db68feeefd9a211f9ee924b71f`
- `ContinuumIntelligenceOnDevice.kt`: `e3b3e91c4f50802d420fd298611b1fcf8ec19545`

`kotlinc ContinuumIntelligenceAudit.kt -d audit.jar` — erfolgreich.
`kotlinc ContinuumIntelligenceAudit.kt Smoke.kt -include-runtime -d intelligence-smoke.jar` + `java -jar intelligence-smoke.jar` — erfolgreich: **sechs synthetische Evaluator-Kontrollgruppen**, darunter 20/20 Oracle-Antworten, 0/5 Halluzinationen; fünf künstliche falsche Antworten ergeben 5/5 unbelegte Behauptungen.
`kotlinc ContinuumIntelligenceAudit.kt ContinuumIntelligenceOnDevice.kt StubCore.kt OnDeviceSmoke.kt -include-runtime -d ondevice-smoke.jar` + `java -jar ondevice-smoke.jar` — erfolgreich: **sechs isolierte Stub-Vertragsgruppen** (25 Outputs, fehlender Checkpoint, Modell-Hash-Wechsel, nicht verfügbare Inferenz, LKG-Recovery, Cancel).

Dies ist echte Kotlin-Kompilation der zwei Modell-/Audit-Module und echte Laufzeitausführung der Kontrollfälle, **aber keine Ausführung der kompletten Android-Gradle-/JUnit-Suite**. `StubCore.kt` ist ein lokaler Prüf-Doppelgänger der Modell-API, nicht der private Samsung-Checkpoint. Die Android-Monitor-Activity wurde bislang nur im GitHub-Quellcode verifiziert und muss separat mit Android-SDK kompiliert werden. Weder ein realer neuronaler Qualitätsgewinn noch eine auf dem Gerät ausgeführte Inferenz ist daraus ableitbar. Die tatsächlichen JUnit-Tests im Repository sind noch auszuführen.

## Grenzen: Was die Ergebnisse nicht beweisen
- 20 bekannte und 5 negative **autorisierte, aber synthetische** Beispiele, nicht allgemeines Sprachverständnis, neue Kenntnisse oder Coding-Intelligenz.
- Die Positivfälle enthalten relativ einfache Fragen mit einzelner Beleginformation. Einige Werte stammen aus dem Vokabular des alten Curriculums, trotz neuer Formulierungen und Identitäten. Ein guter Score könnte teilweise Vorwissen/lexikalische Wiedererkennung statt allgemeine Transferleistung zeigen. Deshalb als nächstes wirklich andere Datendomänen und adversariale Negation/Zahlen/mehrteilige Belege unabhängig einfrieren.
- Nach dem ersten Test sind die öffentlich in Git lesbaren Holdouts für adaptives Training **verbraucht**; keine Schwellen, Auswahl oder Prompt-Strategie anhand dieser Testantworten nachjustieren und danach dieselben Fälle als blind behaupten.
- Ein positiver Forschungs-Score autorisiert **keine** Promotion in produktiven Chat oder Faktenwissen. Kein Training in diesem PR.
- **Noch keine Android/Gradle-/Samsung-Run-Daten und keine Messung des privaten Samsung-Checkpoints** in diesem Branch. Erlaubter Status erst nach tatsächlichem Gerätebeleg anheben.

## Rollback und handoff
Neue Dateien und zwei klar umrissene Änderungen an bestehender Kern-/Monitor-Schicht können als Draft-PR unangewendet bleiben oder einzeln rückgängig gemacht werden. Kein `main`-Commit, keine Releasezweig-Änderung, kein Signier-/Daten-/Modell-Migrationsschritt. Danach: unabhängige Android-Kompilation, 25-fache reale Inferenz, Qualitätsbericht und **erst danach** isoliertes Kandidatentraining mit extern eingefrorenem neuem Holdout.
