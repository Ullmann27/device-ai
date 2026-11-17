# Code48: tatsächlich vorhandene und fehlende Zweiganschlüsse

Android-Bezugsstand: `3ff83387af64bf9f99f4440f657f3d27937ca777`
(PR591), Baum `5206abd91b3740838661aa0e27e6702b3891591b`.
Die 69 weiteren Pfade wurden auf `5d91442e98f78a2af623b8a3c74920805f670cb9`,
Baum `ae79915e7b6df784f0ba78ead4a44b44dedec08a`, geprüft. Der einzige spätere
Android-Produktionsunterschied ist die separat belegte Reparatur der vorhandenen
Home-Hinweiskarte; Szenen- und Ereignisverbindungen blieben dabei unverändert.
Dies ist eine Bestandsaufnahme, keine Freigabe zusätzlicher Laufzeitfunktionen.

## Umfang und Aussagekraft

653 Origin-Heads wurden nach Abstammung und fehlenden Produktionspfaden
inventarisiert. Zusätzlich wurden 96 geschlossene PR-Heads abgeglichen;
alle lagen im aktuellen Stand oder in bereits erfassten Origin-Historien.
Der Abgleich betrifft diese Heads, nicht sämtliche Git-Blobs oder Trees. Das bedeutet
nicht, dass jeder alte Gesamtzweig vollständig übernommen oder jede Zeile
aller Zweige funktional geprüft wurde.

Das daraus abgegrenzte Inventar von 56 ursprünglich fehlenden Android-Pfaden
ist vollständig semantisch zugeordnet: 15 Animation/UI, 19 Lernen/Gedächtnis,
13 Kernpfade und 9 größere Restbausteine. Die unabhängige Mengenprüfung findet
56 eindeutige Pfade, keine Lücke und keine Doppelzuordnung. Bei 3ff ist der
ursprüngliche Pfad `Video3WarpScene.kt` wieder vorhanden. Die anderen 55
fehlenden alten Namen sind ausdrücklich nicht 55 fehlende Produktfunktionen:
mehrere wurden umbenannt, ersetzt oder waren schon damals nur Forschung.
Zusätzlich sind alle 69 ursprünglich fehlenden Nicht-Android-Pfade zugeordnet:
29 Lernen/Forschung, 22 Orchestrierung und 18 Release/Gerät. Die unabhängige
Mengenprüfung bestätigt zusammen genau 125 historische Pfade ohne Lücke,
Doppelzuordnung oder zusätzliche Pfade. Darunter befinden sich Dokumentation,
Tests und vier generierte Python-Cachedateien. 125 Pfade sind deshalb weder
125 Produktfunktionen noch 125 fehlende Anschlüsse. Auf der finalen 5d-Quelle
ist der wiederhergestellte `Video3WarpScene.kt` vorhanden; 124 alte Namen fehlen
weiterhin, mit den nachstehend dokumentierten Ersatz- und Forschungsgrenzen.

Die vier vorher als `unresolved` markierten Heads sind ebenfalls geklärt:
`continuum-agent-state`, `continuum-state` und die beiden LM1-/NF-Checkpoint-
Historien besitzen eigene Roots ohne gemeinsamen Vorfahren. Ihre aktuellen
Trees enthalten Zustands-/Daten-/Checkpointpfade und keinen App-/Skriptcode.
Es wurden ausschließlich Commit-/Tree-/Pfadmetadaten gelesen, keine Gewichte,
Zustandsdaten, Tokenizer, Manifeste oder anderen Blob-Inhalte. Daraus folgt keine
Aussage über Modellqualität, Kompatibilität oder Nutzungsfreigabe.

## In der aktuellen Code48-Quelle angeschlossen

| Bereich | Tatsächliche Verbindung | Beleggrenze |
| --- | --- | --- |
| PR487-Dateiflug | Positive Dokument-/Recall-Ereignisse → bestehender Sequence-Host → endliche große Papier-/Ordnerszene → derselbe Original-Orb → Cockpit | Native Hostframes und fokussierte Laufzeittests; kein Geräte-FPS-/1:1-Referenzbeweis |
| Originalkopf | Vorhandene Cue-Kette, Tiefeneintritt, Verkleinerung und Icon-Burst mit Lifecycle-/STOP-Grenzen | Original-Assets erhalten; GPU-/Fold7-Abnahme offen |
| PDF-Arbeit | Reale Chat-Anfrage öffnet Home/Arbeitsfläche; akzeptierte Szene wartet auf tatsächliches Layout | Synthetische Dokumentdaten im echten Activity-/UI-Pfad |
| Gedächtnis/STOP | Gepufferte Ereignisse behalten ihre ursprüngliche Präsentationsgeneration; alte Ereignisse werden verworfen | Keine neue Memory-DB oder automatische Faktpromotion |
| Trainingsstatus | Typisierter Abbruch wird als Zeitbudget-Verschiebung beziehungsweise Besitzerunterbrechung erfasst; echte I/O-Fehler bleiben Fehler | Reproduzierter Host-Fehler; tatsächlicher aktueller Telefonfehler ohne Monitorcode unbekannt |
| Dashboardlast | Teure Status-/Holdout-Erfassung wird an sichtbare Flächen gebunden | Keine gemessene Temperatur-, Akku- oder Modellgeschwindigkeitsverbesserung |

## Konkrete weitere Kandidaten

| Ursprung | Was zusätzlich vorhanden ist | Was zum heutigen Produkt fehlt |
| --- | --- | --- |
| #487 | Antwortoptionen, große Alarm-/Hinweisszenen | Aktuelle Options-/Ereignismodelle, tatsächliche Caller und Freigabe-/Tap-Verbindung; kein nachgewiesener Virenscanner |
| #239 | Freigegebenes Web-Dossier als flüchtige Kandidaten im bestehenden Lernstoffdialog | Aktuellen Modell-Toolausgang mit Session-/Widerrufsgrenzen anbinden; Quellen bleiben geprüftes Lernmaterial, keine automatischen Owner-Fakten |
| #310 | Zusätzliche ausdrücklich verbietende Web-Formulierungen | Einzelne Formulierungen in die stärkere heutige Policy aufnehmen; alte Policy nicht zurücksetzen |
| #239/#311 | Tutor-Batch-Hinweis und verständlichere Importvorschau | Kleine UI-Anschlüsse; keine Scheduler-/Trainingsreparatur |
| #26/#27 | Bestätigte Antwort für exakt passende Frage, Revision und selektiver Widerruf | Store, Codec, Recovery, Export und UI mit aktuellen Owner-Faktgrenzen verbinden |
| #245 | Diagnose möglicher unbelegter Antwortdetails | Aktuelle Trace-Integration und Prüfung von Kosten/Fehlalarmen; kein Wahrheits- oder Entailmentbeweis |
| #310/#309, #233 | Genauere M4-Termux-Fehlertexte und breiterer Selbstprüfungs-Intent | Bestehende Diagnose-/Statuswege gezielt ergänzen |
| #208 | Alter angeschlossener Termux-Auditpfad für fünf Rollen mit acht benannten Vertragstestdateien und Callback | Skript, genehmigten Dispatch und aktuelle Callback-Provenienz gemeinsam portieren; keine fünf laufenden KI-Modelle und kein in diesem statischen Audit ausgeführter Lauf |
| #316/#318/#326 | Diskbasierter Großquellenimport und persistente Textabschnitts-Provenienz | SAF-/UI-Anschluss, Gesamtquote, Crash-/Abbruchvertrag und begrenzte Auswahl für den Modellkontext |
| #385/#386/#387 | Begrenzter Aufgaben-DAG und beweisgebundenes Strategieregister | Aktuelle Scheduler-/Worker-/CCC1-Ergebnisse vertrauenswürdig anbinden; Planer und Ledger führen selbst keine Aufgaben aus |
| #490 | Alternative CognitiveLoop-Verträge und Adapter | Modell, Planner, Activity und Memory fehlen laut eigenem Handoff; bestehender BoundedAgentOrchestrator bleibt der produktive Laufkreis |

## Zusätzliche Werkzeugfunde außerhalb Androids

| Ursprung / Gruppe | Tatsächlicher Mehrwert | Anschluss- und Aussagegrenze |
| --- | --- | --- |
| #258: EWM-QA | Eingefrorener 240-Fall-Korpus, Integritäts-/Receiptprüfer, Metamorphieprobe und Audit gegen bloßes Schlüsselwort-Raten | Beide gepinnten EWM-Produktionsblobs sind bereits bytegleich vorhanden. Corpus und Spezialprobes fehlen; zusätzliche QA, kein neuer Gedächtniskern. Keine frischen Resultate dieser alten Experimente. |
| #208: öffentliche Coding-Dokumentation | Sieben feste offizielle Quellen mit begrenzten Abrufen und ausdrücklicher Netzfreigabe | Spezialisierter CLI existierte; Anschluss an heutigen Coding-Kontext/App fehlt. Der generische Public-Web-Pfad hat einen anderen Vertrag. |
| #258: Hostfehlerdiagnose | Konservative Klassifikation bestimmter JUnit-Fehlersignaturen | Nur Zusatzdiagnose `HOST_ENVIRONMENT_SUSPECTED`; darf keinen fehlgeschlagenen Test zu PASS umdeuten. |
| #208: Microagents-Frontend | Serieller Aufruf acht fest benannter Vertragstestdateien für fünf Rollen | Sechs Dateien bestehen heute, zwei fehlen. Gemeinsam mit dem historischen Android-Termux-Callback aktualisieren; keine fünf LLM-Instanzen. |
| ARGON / EXP20 | Bandbreitenannahmen, synthetischer Cachevergleich, Host-I/O und alte CRE1-Versuchsrunner | Keine aktivierbare Telefon-Rechenleistung. M5b nutzt den bestehenden CRE1-Kern; Android ruft ihn bereits über `trainExperimentalCre1` auf. ARGON-I/O erzeugt entgegen seiner README eigene temporäre Daten, standardmäßig 2048 MiB. |
| Historische Coding-/Dialoglabore | Reproduzierbare Versuchsarme, eingefrorene Holdouts und lokal begrenzte Antwortaufzeichnung | Teilweise fehlen komplette Experimentpakete. Keine zweite Produktorchestrierung und keine Aussage zum tatsächlichen Handy-Trainingsfehler. |
| #268/#270/#271/#273: Cloud-Forschung | Synthetische NVIDIA-Benchmarks, technischer Remote-Review, Quoten-/Einmalclaim-/Rücklese-Verträge und optionaler externer Neuro-Dienst | Separate externe Betriebsfunktionen. Kein Provider gestartet, kein aktiver Cron nachgewiesen, keine aktuelle Kosten-/Modellverfügbarkeit geprüft. Kein APK-Training oder Update durch bloßes Vorhandensein dieser Skripte. |
| Release-/Gerätewerkzeuge | Mehr-App-/Split-APK-Inventur, Archivbyteprüfung, strukturiertes Fold7-Protokoll und zusätzlicher unabhängiger Signatur-Readback | Aktueller Code-39+-Publisher mit Originalsignatur besteht bereits; alte v23-/v34-Skripte sind keine fehlende Releasekette. Ein Geräteprotokoll ist kein ausgeführter Gerätetest. |

Die 69 Pfade umfassen 76 unterschiedliche Originalvarianten: 29 im Lernbereich
(davon vier nur anhand Pfad/Header klassifizierte Bytecodeartefakte), 27 in der
Orchestrierung und 20 im Release-/Gerätebereich. Alle zugeteilten Originalblobs
sind vorhanden. Bei 18 von 20 damaligen Caller-Suchen des Release-/Geräteaudits
fehlen zusätzliche lokale historische Git-Objekte; dort sind positive Aufrufe
belegt und offene Aufrufer ausdrücklich markiert. Auch die übrigen Nulltreffer
beziehen sich auf benannte geprüfte Einstiege, nicht auf einen vollständigen
semantischen Callgraph sämtlicher alter Branches.

## Folgeversionen statt veralteter Einzelstände

- #326 (`e193411d37339b4c91a4e24ef341b03794a0aa7f`) ist Nachfahr von #318.
  Der Loader prüft tatsächliche Chunkhashes, Zeilenangaben, UTF-8-Grenzen,
  Dateiende und Gesamthash. Das ist ein überprüfter Quellcodeunterschied,
  kein in diesem Audit erneut ausgeführter Test oder neuer App-Caller.
- #387 (`eed77e56b526d199141b658a4bdd38acd2d896e0`) ist kein direkter
  Nachfahr des verglichenen #386-Heads. Der DAG berücksichtigt bereits
  bestehende transitive Abhängigkeiten, bevor er Dateikonfliktkanten ergänzt.
  Derselbe korrigierte Blob liegt auch am inventarisierten #385-Head.
  Das Ledger ist #386/#387 bytegleich. Der zusätzliche CLI-Einstieg nutzt
  tatsächlich den vorhandenen CCC1-Worker, aber weder Kotlin-DAG noch Ledger.

## Bereits ersetzt und ausdrücklich zurückgestellt

Aktuelle Importkonstanten und Importaufrufer ersetzen das alte Gemma-Preset.
Aktives Zeitgedächtnis und der vorhandene Specialist decken die alten
MemoryTimeline-Kernbereiche ab. PCM, Navigation, Gesprächsmodus und mehrere
Phase-1-Kontext-/Store-/Deliberation-Grundlagen haben heutige Nachfolger.
Die alten Klassen werden deshalb nicht als neue Fähigkeiten doppelt eingebaut.

ARGON-, Synapse-, Interactive- und Relationsbausteine bleiben Forschungs- oder
Shadow-Beiträge, solange aktuelle Daten-, Lebenszyklus-, Ressourcen- und
Qualitätsverträge fehlen. Alte Modellannahmen sind keine gemessenen
Geräteleistungswerte. Der Inworld/RemoteMentor-Prototyp wurde ausdrücklich
zurückgestellt und wird durch dieses Audit nicht aktiviert.

## Vollständige Nachweise

- [Abdeckungsprüfung](../../evidence/code48-branch-semantic-audit-20261107/coverage-receipt.json)
- [Vollständige 125-Pfade-Abdeckung](../../evidence/code48-branch-semantic-audit-20261107/complete-coverage-receipt.json)
- [Animation/UI](../../evidence/code48-branch-semantic-audit-20261107/animation/matrix.md)
- [Lernen/Gedächtnis](../../evidence/code48-branch-semantic-audit-20261107/learning/matrix.md)
- [Kernpfade](../../evidence/code48-branch-semantic-audit-20261107/core/matrix.md)
- [Neun Restpfade und Folgeversionen](../../evidence/code48-branch-semantic-audit-20261107/remaining/matrix.md)
- [29 Lern-/Forschungswerkzeuge](../../evidence/code48-branch-semantic-audit-20261107/nonandroid-learning/matrix.md)
- [22 Orchestrierungswerkzeuge](../../evidence/code48-branch-semantic-audit-20261107/nonandroid-orchestration/matrix.md)
- [18 Release-/Gerätewerkzeuge](../../evidence/code48-branch-semantic-audit-20261107/nonandroid-release-device/MATRIX.md)
- [Vier eigenständige State-/Checkpoint-Heads](../../evidence/code48-branch-semantic-audit-20261107/independent-state-heads/matrix.md)

Die JSON-Belege enthalten ursprüngliche Commit-/Blob-IDs und aktuelle
Callerstellen. Lokale Analysepfade in Originalreceipts sind Herkunftsangaben;
die öffentlichen Git-Objekte bleiben die reproduzierbare Quellautorität.
Private Referenzmedien, Schlüssel und private Modellgewichte sind nicht Teil
dieser Übergabe. Der Audit hat keine Forschung automatisch in Produktion
umgeschaltet und keinen Trainings- oder Provideraufruf ausgeführt.
