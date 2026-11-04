# Selbstlernen: vollständige Quellenbindung im bestehenden Replay

Stand: 2026-11-04. Status nach Experimentvertrag: **MOCK_TESTED**, ausgeführt als Host-JVM-Test mit echten Kotlin-Lernklassen und synthetischen temporären Daten. Kein Android-/Fold7-Nachweis.

## Belegter Fehler

`ContinuumLearningCore.learnFromSources` prüfte nur, ob alle angeforderten Quellen aktiv im Ledger vorkamen. Anschließend wählte `FileLearningExperienceStore.replay(SOURCE_TEXT, 48)` unabhängig die jüngsten Einträge. `append` aktualisiert die Zeit eines bereits vorhandenen Eintrags nicht. Eine ältere erneut eingereichte Quelle konnte deshalb aus dem Trainingssatz fallen, obwohl ihr ID-Status und Audit fortgeschrieben wurden.

Der echte Baseline-Aufruf reproduzierte **`completed=true`, 550 Schritte, eine behauptete Quellen-ID**, während die ältere Quelle im gemessenen Trainingssatz fehlte. Der gemessene Ausgangsverlust betrug `0.6927586491041351`; der unabhängig mit dem unveränderten Encoder berechnete Satz einschließlich der angeforderten Quelle hatte `0.6927604443382278`.

Der bestehende Aufrufer `ContinuumPublicStudyScheduler` ruft `ContinuumSourceNovelty.markCompleted(filesDir, pending)` auf, sobald `outcome.completedEvaluation` wahr ist. Der Fehler konnte somit Quellen ohne tatsächliche Teilnahme an dieser Lernrunde als bearbeitet markieren.

Zusätzliche reproduzierte Grenzfälle:

- 48 neuere Beobachtungen ohne Themenannotation verdrängten die angeforderte Quelle. Gemessen wurden nur sechs Bootstrap-Beispiele statt sieben einschließlich der Quelle.
- Nach Normalisierung zu kurze Themen wurden still aus den Trainingspaaren entfernt.
- Wiederholte gleiche Eingaben vervielfachten die berichteten IDs und überfüllten das Auditfeld.
- Das vorhandene `LearningExperience.sourceRef` erlaubt höchstens 500 Zeichen. Eine ID hat 27 Zeichen; 17 mit Kommas getrennte IDs benötigen 475 Zeichen, 18 benötigen 503. Der bisherige Audit-Aufruf konnte deshalb nach Statusänderungen werfen.

## Begrenzte Änderung

Produktiver Änderungsumfang: ausschließlich `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt`, 22 hinzugefügte und 14 entfernte Zeilen gegenüber der untersuchten Basis.

1. Alle aktuellen Quellen werden vor Schreibzugriffen syntaktisch und mit der bestehenden Subword-Validierung geprüft.
2. Die erzeugten Erfahrungen werden anhand ihrer bestehenden Inhalts-ID dedupliziert.
3. Mehr als **17 verschiedene aktuelle Quellen** werden vor Ledger-/Modelländerungen mit `null` abgewiesen. Das entspricht dem bereits vorhandenen Auditformat. Der bestehende Scheduler behandelt einen fehlenden Bericht als aufgeschoben.
4. Aktuelle zulässige Quellen erhalten Vorrang im begrenzten Replay. Historische Einträge füllen verbleibende Plätze; Einträge ohne ausreichend lange Themenannotation verbrauchen keine Trainingsplätze.
5. `replayExamples` zählt die tatsächlich verwendeten Quellenpaare.

Es bleibt bei einem Audit pro Runde, maximal 48 Quellen im Replay und dem bestehenden Trainingslimit von 96 Beispielen. Intent-/Semantik-Holdouts, Annahmeschwellen, Abbruchregeln, Gewichtsübernahme und Scheduler wurden nicht geändert. Eine abgeschlossene Bewertung bleibt von einer angenommenen Gewichtsänderung getrennt.

## RED → GREEN

Die ursprüngliche 48er-Annahme und die endgültige 17er-Grenze werden ausdrücklich getrennt dokumentiert.

| Lauf | Regressionstests | Fehler | Bedeutung |
|---|---:|---:|---|
| `red-original-48` | 8 | 6 | Ursprüngliche Testfassung; maximal 48 aktuelle Quellen angenommen |
| `red-completed-48` | 8 | 6 | Echte abgeschlossene Runde für die alte Quelle zusätzlich belegt |
| `red-v2-17` | 8 | 5 | Endgültige 17er-Vertragsgrenze, vor dem Produktionsfix ausgeführt |
| `green-v2-17` | 26 | 0 | Dieselben acht v2-Tests plus 16 bestehende Core- und zwei LearningIntegration-Tests |

Der v2-Testquelltext ist zwischen RED und GREEN byteidentisch:

`581eb469af8353dfc1fc563aff95ca8f8c96a468605593dcc4c4f5a7f60c1539`

Baseline-Core SHA-256:

`909b3269977cdaf415ab9951e8e6ef1642c8cc68b2d3cae04dedf69dac173205`

Geprüfter Core nach Änderung:

`3883330655a6f3750da418f758ac39d6c3542a8a34dc9788f6bec749c259e2cf`

Die acht neuen Tests prüfen alte erneut vorgelegte Quellen, annotationlose Historie, atomare Ablehnung von 18/48/49 aktuellen Quellen, normalisiert kurze bzw. untrainierbare Themen, vollständige 17er-Auditreferenzen, normalisierte Duplikate, Widerruf und eine regulär abgeschlossene Lernrunde. Abgebrochene Runden bleiben unvollständig und verändern keine Modellbytes. Bestehende Core-Tests decken zusätzlich unter anderem fehlende Ledgerkapazität, Owner-Fakten und Intent-Replay ab.

## Reproduktion und Evidenz

Runner: `tools/cognition-learning-loop/self-learning/run_host.py`.

```sh
python3 tools/cognition-learning-loop/self-learning/run_host.py \
  /tmp/continuum-source-coverage-run \
  --base-jar /workspace/scratch/344de8bc2104/memory-jvm-base.jar \
  --existing-tests
```

Das Ausgabeziel muss neu sein. Der Runner erwirbt beide bestehenden Build-Locks in der abgestimmten Reihenfolge, mit insgesamt höchstens 45 Sekunden Wartezeit. Er baut `ContinuumLearningCore`, `ContinuumLearningExperience`, `TrainableSubwordEncoder` und `MentorSource` aus eingefrorenen aktuellen Quelldateien mit Kotlin 2.4/JDK 17 neu und führt die tatsächlichen JUnit-Tests aus.

Transitive Abhängigkeiten stammen aus einem unveränderlichen Snapshot vorhandener C1-Klassen. JAR SHA-256:

`44e45d4c047a1cd65a4ca8b8faccae498a559b09cb4bd5ae99a04db4ac94048d`

Der vollständige Abhängigkeitsgraph wurde hierfür nicht neu kompiliert. JARs und Testquellen sind je Lauf gehasht; alle vier Läufe bestätigten unveränderte Abhängigkeitsbytes. Befehle, Compilerprotokolle, JUnit-Protokolle, Quellsnapshots und Laufzeiten liegen unter:

`docs/evidence/cognition-learning-loop-20261104/self-learning/`

`summary.json` enthält die maschinenlesbaren Ergebnisse und Grenzen. Die originale RED-Evidenz wurde erhalten, nicht nachträglich durch die endgültige Testsuite ersetzt.

## Grenzen

Gemessen ist korrekte Bindung des bestehenden Quellenlernens an die angeforderten Erfahrungen. Daraus folgt keine nachgewiesene höhere allgemeine Modellqualität. Ausschließlich temporäre synthetische Lerndaten und Modellzustände wurden verändert; keine produktiven Modellgewichte, keine persönlichen Inhalte, keine kostenpflichtigen APIs, keine GitHub Actions, kein APK-/Gerätelauf.

Der neue explizite Grenzwert von 17 aktuellen Quellen betrifft Aufrufer großer Batches. Solche Aufrufe werden wahrheitsgemäß aufgeschoben; automatisches Aufteilen wurde nicht ergänzt. Der produktive Public-Study-Pfad verwendet den bestehenden Scheduler und dessen Abschlussentscheidung.
