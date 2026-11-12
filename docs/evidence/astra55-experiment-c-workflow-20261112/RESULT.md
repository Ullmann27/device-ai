# Experiment C · Ergebnis und Entscheid

**Entscheid: bestehende Produktlogik beibehalten; zusätzlichen Planner, Retry oder Eventbus verwerfen.**
Den neuen integrativen Regressionstest in PR #807 → #804 → #799 übernehmen.
Im vorab festgelegten Umfang besteht die vorhandene Beobachtungs-/Handlungs-/Prüfkette alle Gates.
Das ist eine begrenzte Hostaussage; allgemeine autonome Android-Planung wird dadurch nicht nachgewiesen.

Produktionsbasis: `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Vorregistrierung: `8d523d5d524dd5ad5d6e2a0f4b2938c1535b972b`, öffentlich tree-identisch
als `83821ced8c6ac7706e911887a13693e89e901524` laut Koordinator veröffentlicht.
Geprüfter Quell-/Testcommit: `0fbb410c6bd6f0393c715c0c9436f3c4c2fbbce8`.

## Tatsächliche Messung

| Gruppe | Abläufe | Eingaben | Verifizierte Schritte | Abschlüsse | Früher Erfolg | Endzustand |
|---|---:|---:|---:|---:|---:|---|
| A: sofortige richtige Beobachtung | 4 | 4 | 4 | 4 | 0 | COMPLETED |
| B: verzögerte richtige Beobachtung | 12 | 12 | 12 | 12 | 0 | COMPLETED |
| Frische, widersprüchliche Beobachtung | 16 | 16 | 0 | 0 | 0 | PAUSED |
| Stop vor später richtiger Beobachtung | 4 | 4 | 0 | 0 | 0 | STOPPED |

36 Abläufe, jeder genau eine Eingabe; keine zusätzliche Eingabe während Warten, Widerspruch oder Stop.
Keine der 20 Negativkontrollen meldet Erfolg. Die drei Experiment-JUnit-Methoden und 44 bestehende
Nachbartests bestehen: **47/47 JUnit-Methoden PASS**. Kompilierung PASS.

| Injizierte Beobachtungszeit | Abschlusszeit ab Eingabe | Tonanzahl bei Cue-Post 0 / 149 / 150 / 151 ms | Maximaler Schulterwert |
|---:|---:|---|---:|
| 0 ms | 0 ms | 1 / 0 / 0 / 0 | 0,0000 |
| 70 ms | 70 ms | 1 / 0 / 0 / 0 | 0,5657 |
| 500 ms | 500 ms | 1 / 1 / 1 / 0 | 0,8000 |
| 1500 ms | 1500 ms | 1 / 1 / 1 / 0 | 0,8000 |

Zeitwerte sind **synthetische monotone Ereigniszeiten**. Die Tabelle beschreibt keinen Durchsatz,
keine CPU-Beschleunigung und keine Android-Gerätelatenz. Die Werte gelten für die gültigen Fälle;
die vollständigen 36 Datensätze stehen in `measured/metrics.csv`.

Die synchrone Originalkette liefert pro Eingabe genau ein Avatar-Inputereignis. Der tatsächliche
AvatarPoseReducer und CockpitWorkReducer ergeben dieselben oben aufgeführten Schulterwerte.
Bei sofortigem Abschluss bleibt kein zeitlich anhaltender Schulterimpuls. Bei verzögerter Beobachtung
läuft der vorhandene kurze Impuls aus; Warten erzeugt keine erfundenen weiteren Eingaben.
Ein nach Abschluss noch wartender Ton wird verworfen. Auch bei laufendem Auftrag wird ein
151-ms-Ton verworfen; 149/150 ms bleiben innerhalb der bestehenden Grenze.
Nach Abschluss/Pause/Stop sind WorkInput und Arbeits-Leseblick beendet.

Bei Stop wird die Freigabe bei 50 ms widerrufen. Der terminale Emitter-/Reducerzustand bleibt STOPPED,
und die bei 500 ms angebotene richtige Beobachtung erzeugt keinen späteren Erfolg. Nur ein bereits
bei 0 ms abgespielter Eingabeton bleibt gezählt; die drei wartenden Töne werden verworfen.
Der Workflow-Rückgabestatus ist in dieser Stop-/Widerrufskombination PAUSED. Das wird offengelegt;
das vorregistrierte Stop-Gate bezog sich auf terminale Ereignisse, Bewegung und ausbleibenden Erfolg.
Die vollständige Android-Controller-/Antwortzustellung wurde hier nicht ausgeführt.

## Was tatsächlich ausgeführt wurde

Originale `OwnerComputerWorkflow`, `OwnerComputerTaskGrant`, `VerifiedNodeAction`,
`LiveActionCockpitAdapter`, `CockpitAnimationBus`, `ComputerWorkAvatarBridge`,
`AvatarDirector`, `AvatarPoseReducer`, `CockpitWorkReducer` und `ComputerWorkCuePlayer`.
Der aktuelle Controller abonniert diese Bridge synchron; genau dies bildet der Test ab.
Nur der OS-Node-Port, die Uhr und die vorhandene asynchrone Ton-Ausführung liefern kontrollierte Fixtures.
Kein zusätzlicher Eventbus und keine nachgebaute Prüflogik ersetzen die Originalkomponenten.

18 vollständige Produktionsdateien werden mit Kotlin 2.4.0 frisch kompiliert.
Sechs unveränderte reine Typdeklarationen werden aus drei Android-gekoppelten Originaldateien extrahiert.
Fünf Testdateien ergeben die drei Experiment- und 44 Nachbarmethoden.
Alle 26 eingefrorenen Quell-/Testdateien sind vor/nach dem Lauf identisch und gegen den Quellcommit gebunden.
Alle Produktionsdateien bleiben zusätzlich bytegleich zur angegebenen Basis. Keine Produktionsänderung.
Die vorregistrierten Gates wurden nach der Messung nicht geändert.

## Reproduktion und Belege

Direkter lokaler JVM-Lauf, 512 MB Heap, ein aktiver Prozessor, eigenes Tempverzeichnis, kein Gradle.
Vorhandener Kotlin-2.4.0-/JUnit-Cache genügt; der Runner lädt keine Abhängigkeiten herunter.

```sh
python tools/astra55-workflow-experiment/run.py \
  --repo "$EXPERIMENT_REPO" \
  --cache "$EXPERIMENT_DEPENDENCY_CACHE" \
  --out "$EXPERIMENT_NEW_OUTPUT"
```

Die drei Variablen bezeichnen absolute lokale Pfade. Cache: vorhandenes Gradle-Verzeichnis
`modules-2/files-2.1`. Der Ausgabeordner muss neu sein. Relevanter tatsächlicher Output:

```text
COMPILE_EXIT 0
EXPERIMENT_EXIT 0
OK (3 tests)
NEIGHBORS_EXIT 0
OK (44 tests)
```

`measured/` enthält CSV, Zusammenfassung, Quell-/Toolhashes, Compiler-/JUnit-Ausgaben und Quellbindung.
Öffentliche Ausgaben ersetzen lokale Pfadpräfixe durch dokumentierte Platzhalter und normalisieren
abschließende Leerzeilen. `publication-provenance.json` bindet unveränderte Scratch-Rohlogs und öffentliche
Fassungen jeweils per SHA-256; Resultate und Assertions wurden nicht bearbeitet.

## Grenzen und Abschluss

ReAct/AndroidWorld liefern das Untersuchungsprinzip; kein solcher Modellagent wurde implementiert
oder auf seinem Benchmark gemessen. Der vorhandene Computerpfad bleibt ein begrenztes Rezept.
Neue Sicht-, Planungs-, Retry- oder Lernfähigkeiten werden nicht behauptet.
Keine Nachrichtenübermittlung oder sonstige reale App-Eingabe wurde ausgelöst.

PORTIERT: Test/Harness im Experimentzweig. VERIFIZIERT: Originalcode mit synthetischem OS-Port
(MOCK_TESTED), 47 JUnit-Methoden / 36 Experimentabläufe. GERÄTEBESTÄTIGT: nein.
Android-Monitorpixel, Capture, GPU, APK und Fold7: NOT_RUN. Audit #803 bleibt außerhalb dieses Prüflaufs.
Kein offener Produktversuch: Testintegration empfohlen, zusätzliche Produktlogik für diesen Befund abgelehnt.
Rücknahme: neue Test-/Harnessdateien entfernen; keine Daten- oder API-Migration.
