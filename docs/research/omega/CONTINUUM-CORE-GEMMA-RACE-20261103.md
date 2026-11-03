# Continuum Ω gegen Gemma 4 – kontrolliertes KI-Wettrennen

**Status 03.11.2026: QUELLSTAND + ISOLIERTER KOTLIN-SMOKE. Kein echter Fold7-Zeitvergleich, keine native Gemma-Antwort und kein neuer APK-Build in diesem Benchmark.**

## Ziel

Der Continuum-Kern (deterministische Regeln, SWB1/CRE1, EWM2, bestätigtes Gedächtnis, RoutineBook und sichere Skill-Ausführung) soll mit der Zeit **mehr Aufgaben ohne Modellgenerierung** beantworten. Gemma bleibt anspruchsvoller, generativer Fallback. Das Wettrennen bedeutet, dass Continuum schneller und belastbarer wird – Gemma wird **niemals künstlich verlangsamt**.

**Nicht automatisch als Sieg werten:** Schnelle, halluzinierte, aus nicht bestätigten Erinnerungen abgeleitete oder unsichere Antworten.

## Vorhandene Komponenten wiederverwendet

- benchmark/BenchmarkRunner.kt, BenchmarkModel.kt und BenchmarkReport.kt messen bereits Latenz, Werkzeugfehler und weitere Werte.
- cognitive/ContinuumBenchmark.kt enthält die bestehenden Kategorien und Metriken.
- Neu: benchmark/CoreGemmaRace.kt nutzt diese Runner; jeweils **eine Ausführung nach der anderen** mit abwechselnder Reihenfolge Core→Gemma / Gemma→Core (kein gleichzeitig konkurrierendes RAM-/NPU-Lastprofil).
- Neu: benchmark/CoreGemmaRaceTest.kt enthält synthetische Regressionen für Messpaare, Sicherheits-Veto, fehlende Grading-Belege, Abbruch, langsame p95-Spitze und alternierende Reihenfolge.

## Messprotokoll vor echten Gewinnbehauptungen

1. Ein unabhängiger Prüfauftraggeber friert **neue, bislang ungesehene Aufgaben** aus Gedächtnis, Routinen, Android-Werkzeugen, längeren Dialogen, Logik und Programmierung ein. Identische Aufgabenstellungen an beide Teilnehmer; dieselbe zulässige Faktenlage und dieselben Berechtigungsgrenzen.
2. Auf **demselben Fold7** die tatsächlichen Instrumentierungsadapter verbinden: A = realer Continuum-Chat-/Gedächtnis-/Skillpfad; B = tatsächliche LiteRT-LM-Gemma-4-Inferenz. Gemessen wird die vollständig nutzbare Antwort inklusive Abruf und Prüfung, nicht nur ein einzelner Modell-Token.
3. Temperatur, Akku, Energiesparmodus, Cache und warm/cold-Modellstatus erfassen. A/B und B/A abwechseln. Ergebnisse von Kalt- und Warmlauf **getrennt** darstellen.
4. Antworten von einem von den Kandidaten getrennten Referenz- oder Blindprüfer bewerten: Korrektheit, Quellen-/Faktenbindung, Regelkonformität, negative Absichten, Verweigerungen und unerlaubte Werkzeugaktionen. Die Qualität stammt **nie** aus modellseitigem success, eigenem Modelltext oder factualSupport.
5. Pro Kategorie n, **p50**, **p95**, Fehler-/Blockierungsquote, Qualität, Werkzeugaufrufe, Energie und Modellaufrufe berichten. Eine ungültige oder fehlende Antwort wird nicht aus der Stichprobe aussortiert.
6. Die Auswertung verlangt mindestens **30 vollständige Paare aus 3 Kategorien mit jeweils mindestens 3 Fragen**, mindestens 95 % fallweise Qualitätsparität (innerhalb von 0,05 Qualitätsstufen), höchstens 0,02 Rückstand beim mittleren Qualitätsscore, **keine Sicherheitsverletzung** und mindestens 20 % schnellere Core-p50 UND -p95. Danach ist lediglich ein **vorläufiger Benchmark-Vorteil** belegt.
7. Ein echter Gerätesieg erfordert zusätzlich extern überprüfte Geräte-/Modell-/Commit-/Aufgaben-SHAs, nachvollziehbare Rohantworten, Laufprotokolle und eine unabhängige Qualitätsabnahme. Die aktuelle Kotlin-Datenklasse erzeugt **keine kryptografische Gerätesignatur**. Das Feld measurementEvidenceIsDeviceVerified bleibt deshalb ausdrücklich false.

## Nächster technischer Anschluss

- Zwei BenchmarkSystem-Adapter auf echte, **fest spezifizierte** Antwortwege setzen; Referenzantworten aus frischem Holdout und nicht aus vorherigen Trainingstexten.
- Gemma-Inferenz nicht parallel zur aktiven normalen Chatgenerierung starten; kein Rennen als unbemerkte Akku-/Wärmelast bei jeder Nutzerfrage. Opt-in-Testmodus, CPU-/Thermal-/Batterie-Gates und explizite Besitzerentscheidung.
- Bestehendes AgentActivity/ChatReasoningRouter/ChatSpecialistChain wiederverwenden; keine neue Chat-Engine oder Schatten-Gemma-Instanz.
- Erst wenn einzelne Aufgabenklassen die Qualitäts- und Sicherheitskriterien wiederholt erfüllen, darf der deterministische Pfad im normalen Router bevorzugt werden; unklare Fälle gehen weiterhin an Gemma oder verlangen Rückfrage.

## Grenzen der heutigen Prüfung

CoreGemmaRace.kt wurde in einem isolierten Linux-Host-Smoke mit Kotlin 1.9 gegen kompatible Typ-Stubs kompiliert und auf fünf typische Vergleichssituationen geprüft. Ein zunächst gefundener Long/Int-Vergleichsfehler wurde korrigiert. **Das ist keine vollständige Gradle-/JUnit-, LiteRT-LM-, Android- oder Fold7-Abnahme.** Die JUnit-Datei ist im PR, aber nicht mit Android SDK ausgeführt.

Damit darf sich keine APK als schneller als Gemma 4 ausgeben. Der Benchmark ist ein Baustein des Code40-Kandidaten, nicht dessen Releasefreigabe.
