# Forschungsrunde 2: überprüfbares Lernen, Erfahrungsumfang und Avatarvarianten

**4. November 2026 · RESEARCH_SHADOW · WIP / neue Ergebnisse noch nicht veröffentlicht.**

Heinz hat die Fortsetzung der drei Forschungsaufträge ausdrücklich beauftragt. Runde 1 bleibt unter ihrem geprüften Commit `b90285bdb3d7e5f5dd4dc723d641182d25442ac5` unverändert nachvollziehbar. Diese zweite Runde erweitert ausschließlich den Forschungsbereich; es gibt keine Produktintegration, kein Main-Merge, keine Signierung oder Installation.

## Geprüfter Anschlussstand

Die GitHub-Metadaten wurden am 4. November 2026 um 19:08 UTC erneut gelesen. Der Vergleich von #492 `30cd3a89810ed14fa84eca95236d3177e016118d` mit `f9a594641f4f2101e84d46baa8dd87e85b5364cd` zeigt 20 zusätzliche Analyse-/Auditdateien; Runtime, Trainer und ursprünglicher Trainingstreiber wurden dazwischen nicht geändert. Die neue Studie kann deshalb ausdrücklich auf den bereits gehashten Kotlin-Quellen bei `30cd3a89` aufbauen.

Parallel bearbeitet #508 bei `7131c1a50a9c697e481a6debe259758dbf422eef` EOS-/Optimierungsdiagnostik und ein deutsches Intent-Lernexperiment. Unsere Lernaufgabe wird davon getrennt: einfache Zustandsänderung und anschließende Abfrage statt Intentklassifikation. Die zusätzlichen Integrations- und Tuningzweige #511–#513 werden hier nicht verändert.

#510 bei `2a364005b52e3240eb799f86aea31be90b390b10` dokumentiert den fehlenden erreichbaren Besitzer-/Modellhost. Das ist keine Aufforderung, historische Ressourcensperren zu umgehen oder einen früheren Modelllauf erneut auszulösen. Die aktuelle Erreichbarkeit wird gezielt geprüft; fehlende Liveausführung bleibt sichtbar.

## Drei getrennte Hypothesen und Ausführungsbereiche

### Bot Eigenständigkeit

Ziel ist zunächst ein echter lokaler Repair→Proof→Neustart→Recall-Nachweis, sofern der vorhandene, freigegebene Modell-/Workerpfad tatsächlich erreichbar ist. Es werden keine Modellbeobachtungen erfunden und keine historischen Runtime-Schlüssel aus Belegen verwendet.

Bei fehlendem ausführbarem Modellhost wird ein neues begrenztes Vertragsexperiment durchgeführt: Verhalten und Aufwand des echten technischen Recall bei 0/32/128 zulässigen synthetischen Erfahrungen, mit positiven Kontrollen, Datei-/Quellselektion und den bestehenden Zeichen-/Eintragsgrenzen. Dieser Ersatzversuch untersucht Abrufmechanik und Ressourcen, nicht autonome Reparaturleistung. Er wiederholt nicht die alte 51+9-Testzahl als neuen Fortschritt.

### Bot eigenes Sprachmodell

Ein selbst erzeugter kleiner deutscher Datensatz beschreibt Zustände und ihre Änderungen, etwa Gegenstandorte, und fragt den gültigen letzten Zustand ab. Ein ausführbares Programm prüft die erwarteten Antworten unabhängig von der Modellgenerierung. Trainings- und Prüffälle werden nach Kombinationen/Familien getrennt; einfache Abkürzungen wie der zuletzt irgendwo genannte Ort erhalten Gegenbeispiele.

Das detaillierte Protokoll, die Daten, Quellenidentitäten, Seeds, Vergleichsbedingungen, Lernrate und das feste Rechenbudget werden **vor Beginn des Trainings in einem eigenen Commit veröffentlicht**. Der Originalcheckpoint und Originaltokenizer bleiben unverändert. Jeder unabhängige Arm startet vom Original; ein durchgefallener Kandidat wird weder weitertrainiert noch ausgewählt. Freie Antwortgenerierung und bedingte Antwortwahl werden getrennt ausgewiesen. EOS bleibt regulär auswählbar, bestehende Abnahmegates bleiben unverändert.

Diese kleine Aufgabe untersucht beschränktes Zustandsverfolgen. Sie ist kein Nachweis einer allgemeinen Simulation, allgemeiner Intelligenz, eines eigenen großen Dialogmodells oder einer Android-Leistungssteigerung.

### Bot Avatar

Die Originalreferenzen werden erneut visuell geprüft. Mit dem vorhandenen GLB werden eine unveränderte Kontrolle und zwei durch Parameter beschriebene lokale Varianten hergestellt. Fokus: Augenwirkung, Oberflächenmaterial und Linienmaßstab. Kamera, Licht und Posen bleiben für den Vergleich gleich; neutrale, moderate Sprech- und Blinkpose werden betrachtet.

Die ursprüngliche Geometrie, das Rig und die 21 Morphziele werden möglichst beibehalten; jede notwendige Abweichung muss gesondert nachvollziehbar sein. Die Forschung produziert tatsächliche Blender-Ausgaben und eine technische Kontaktansicht. Ein Hostbild wird nicht als Filament-/Fold7-/TTS-Nachweis bezeichnet. Referenztreue wird offen qualitativ beurteilt; ein erfundener Prozentwert ist ausgeschlossen.

## Belege und Grenzen

Die drei Forscher schreiben nur in getrennte Arbeitsordner. Der Koordinator prüft und übernimmt abgeschlossene Artefakte in diesen Forschungsbranch. Die 75 Dateien der ersten Runde bleiben unverändert. Neue Messungen, Fehlversuche, Hostgrenzen und kompakte reproduzierbare Programme werden hier ergänzt. Die Referenzbilder werden nicht erneut veröffentlicht oder umgestaltet.

Keine kostenpflichtige KI-/CI-Ausführung, kein Download fremder Modellgewichte, keine GitHub Actions als Rechenweg und kein neuer dauerhaft laufender Dienst. Der Offline-Schalter der App bleibt unverändert. Fehlende Liveverbindung oder Geräteabnahme werden nicht durch zusätzliche synthetische Erfolgszähler ersetzt.

## Primäre Forschung zur Aufgabenwahl

- Li, Guo und Andreas, ICML 2025: [(How) Do Language Models Track State?](https://proceedings.mlr.press/v267/li25r.html). Zustandsverfolgung kann durch konkrete Aufgaben und Gegenproben untersucht werden; deren publizierte Modellresultate werden nicht auf Continuum übertragen.
- Petty, Merrill und Sabharwal, Vorabveröffentlichung vom 2. November 2026: [A Fine-Grained Hierarchy and Benchmark for State Tracking in Language Models](https://arxiv.org/abs/2611.03094). Die Arbeit unterscheidet Zuweisung, Überschreiben, selbstreferenzielle Änderungen und allgemeines endliches Zustandsverfolgen. Unser kleiner Zustandsversuch ist eine engere, einfache Aufgabe; keine direkte Replikation von StateBench.
