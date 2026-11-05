# Meilensteine 2–4: Aktivierungen, EOS und unabhängige Nachrechnung

Ausführbarer Stand vor der ersten Messung veröffentlicht:
`3397fc445b5c40dee987399bd38d8c1226bd5b87`.
Registrierung: `9dd0171df1e8bf1adb0ed64cc3fa8bea1436d78773003341d75aa896492dc70e`.
Ergebnis: **Diagnose und Integrität PASS; keine neue Qualitätsabnahme**.

## Tatsächlich ausgeführt

- 20 vorab festgelegte Varianten, jeweils zwölf feste technische Eingaben;
- 240 vollständige nächste-Token-Verteilungen mit 512 Float32-Werten;
- 1.440 Aktivierungsprotokolle aus sechs Stellen im Modell;
- 48 native Ausgaben mit höchstens 32 Tokens und 5.000 ms je Aufruf;
- 240 zusätzliche Vorwärtsrechnungen in einer unabhängigen NumPy-Implementierung;
- 16 Wiederaufnahme-/Negativprüfungen am echten Controller, insgesamt null JVM-Starts.

Alle vier ursprünglichen Checkpoints und die Tokenizerdatei blieben unverändert.
Die 16 Blockinterventionen existierten ausschließlich als private Arrays im
Messprozess. Null Optimizerupdates, null neu serialisierte Modellcheckpoints,
kein Zugriff auf Trainings-/Dev-/Finaltestdateien.

## 1. Die erweiterten Kanäle unterscheiden sich funktionell

Die initialen W0-Aktivierungen unterscheiden sich in den acht Replikaten nur
im Rundungsbereich. Bei W1 liegen die Abweichungen deutlich darüber:

| Messstelle | W0 relative RMS-Streuung, Bereich über zwölf Eingaben | W1 |
|---|---:|---:|
| Embedding | 0 | 0,000326–0,000494 |
| Layer 0 nach Rekurrenz | 2,72e-7–3,01e-7 | 0,00314–0,00451 |
| Layer 0 nach MLP | 5,72e-7–6,06e-7 | 0,00719–0,00827 |
| Layer 1 nach Rekurrenz | 5,04e-7–5,27e-7 | 0,00634–0,00728 |
| Layer 1 nach MLP | 5,79e-7–6,12e-7 | 0,00356–0,00504 |
| Finale Normierung | 6,04e-7–6,50e-7 | 0,00437–0,00565 |

Damit ist auch auf diesen Eingaben die Vermutung widerlegt, W1 führe weiterhin
acht exakt gleiche Kanalrechnungen aus. Daraus folgt weder ein effektiver Rang
noch ein quantifizierter Wissenszuwachs. Ein verlustfreies Zurückfalten auf
64 Kanäle ist durch die ursprüngliche Replikationsidentität nicht gerechtfertigt.

Die maximale native Wahrscheinlichkeitsabweichung W0/S0 beträgt
**1,1920929e-6**, unter dem unveränderten Grenzwert **1e-5**. Alle zwölf
S0/W0-Ausgaben haben dieselbe Tokenzahl; beide erzeugen bei neun Eingaben
32 Tokens. Diese Länge ist keine Aussage über die Textqualität.

## 2. Der Ausgabestopp hängt vom Kontext ab

Mittlere EOS-Wahrscheinlichkeit direkt nach der jeweiligen Eingabe:

| Gruppe, je vier Eingaben | S0 | W0 | S1 | W1 |
|---|---:|---:|---:|---:|
| Allgemeine Textanfänge | 14,8792 % | 14,8792 % | 41,8506 % | 30,2483 % |
| Synthetisches Auftragsformat | 0,01584 % | 0,01584 % | 0,03919 % | 0,00812 % |
| Dasselbe Format nach einer Ziffer | 0,02842 % | 0,02842 % | 99,1843 % | 98,2638 % |

S1/W1 enden bei allen zwölf nativen Ausgaben mit EOS. Keine dieser Ausgaben
endet wegen Zeitlimit oder Kontextlimit. Beide liefern null vollständige
32-Token-Ausgaben; S1 erzeugt maximal sechs Tokens, W1 maximal einen.

Die starke EOS-Wahrscheinlichkeit nach einer Ziffer passt zum vorherigen
Ein-Ziffer-plus-EOS-Trainingsziel. Die zugleich gestiegene EOS-Neigung bei
allgemeinen Textanfängen zeigt eine zusätzliche Verhaltensänderung auf diesen
vier Proben. Das ist eine Diagnose der gefrorenen Modelle, kein neuer Blindtest
und kein Beweis, dass eine einzige Verlustkomponente die vollständige Ursache ist.

## 3. Ein einzelner Gewichtsblock erklärt die Veränderung nicht vollständig

Mittlere EOS-Wahrscheinlichkeit über alle zwölf Eingaben. Die Mischung enthält
auch die vier Eingaben nach einer Ziffer und ist ausdrücklich kein Qualitätsscore.

| Ersetzter Block | S1 erhält S0 | S0 erhält S1 | W1 erhält W0 | W0 erhält W1 |
|---|---:|---:|---:|---:|
| Keiner: Empfängermodell | 47,0247 % | 4,9745 % | 42,8401 % | 4,9745 % |
| Embedding und gebundene Ausgabeprojektion `tok` | 35,2185 % | 12,1669 % | 37,6110 % | 7,3999 % |
| Finale Normierung `nf` | 46,7860 % | 5,0491 % | 42,2715 % | 5,2338 % |
| Gesamter Layer 0 | 24,0261 % | 11,3712 % | 43,6761 % | 20,2680 % |
| Gesamter Layer 1 | 34,4952 % | 7,5653 % | 30,1123 % | 33,3861 % |

Besonders der zweite Layer beeinflusst im breiten Modell die EOS-Neigung:
W0 mit W1-Layer 1 steigt um 28,41 Prozentpunkte; W1 mit W0-Layer 1 sinkt um
12,73 Prozentpunkte. Das isolierte Zurücksetzen der finalen Normierung hat
einen viel kleineren Effekt. Keine einzelne Rücksetzung stellt das ursprüngliche
Verhalten über alle Proben wieder her. Es wird deshalb kein Blocktausch als
repariertes Modell übernommen oder nachträglich am verbrauchten Finaltest geprüft.

Blocktausch kann zusammen gelernte Repräsentationen auseinanderreißen. Die
gegenläufigen Eingriffe zeigen Abhängigkeiten in diesen Modellen; sie beweisen
keine allgemeingültige, eindeutige Trainingsursache.

## 4. Unabhängige Rechnung und Wiederaufnahme

Jeder instrumentierte JVM-Vorwärtslauf war bitgleich zum unveränderten
`LmModel.logits`. Die zusätzlich implementierte NumPy-Rechnung importiert
weder den JVM-Diagnosegraphen noch dessen Ergebnisparser. Sie lädt dieselben
echten Gewichte, rekonstruiert die Formeln separat und kontrolliert die
dekodierten Eingabe-IDs, EOS-Ränge, Entropien, Margen und Rohbeleg-Hashes.

| Unabhängiger Vergleich | Größte gemessene Abweichung | Vorher festgelegter Grenzwert |
|---|---:|---:|
| Nächste-Token-Wahrscheinlichkeit | 1,7285347e-6 | 1e-5 |
| EOS-Logit | 1,5258789e-5 | 1e-4 |
| Bester anderer Logit | 7,6293945e-6 | 1e-4 |
| Aktivierungs-RMS | 9,9209525e-8 | 1e-4 |
| Relative Replikatstreuung | 4,7429868e-7 | 2e-6 |
| Größte Replikatspannweite | 6,6757202e-6 | 1e-4 |

Alle Grenzen bestehen ohne nachträgliche Änderung. Diese unabhängige
Implementierung stammt aus derselben Root-Sitzung; es lief kein neuer Helferbot.

Die 16 Controllerprüfungen umfassen den tatsächlichen abgeschlossenen Lauf,
fehlende erste/letzte Receipts, veränderte Logs/Checkpoints/Quellen, eine aktive
Sperre, fremde Artefakte, geänderte Summary, neu hashgebundene falsche Parität
und den Versuch, Grenzwerte durch erneutes Hashen zu lockern. Alle bestehen;
kein JVM-Prozess startet. Sämtliche dauerhaften Dateien des Originaldurchlaufs
bleiben unverändert. Eine kurzlebige exklusive Wiederaufnahmesperre wird verwendet.

Beim ersten Start des neuen Wiederaufnahmeprüfers war der Classpath-Index in
der Belegauswertung falsch. Er scheiterte vor dem ersten Fall mit `IndexError`.
Der Prüfer wurde korrigiert; Messquellen, Registration und Rohbelege blieben
unverändert. Das war kein Modell- oder Paritätsfehler.

## Ressourcen, Quellen und nächste Entscheidung

Die zwanzig seriellen JVM-Prozesse benötigen zusammen **34,511 Sekunden**.
Größter gemessener Prozess-RSS: **512.372 KiB** (etwa **500,36 MiB**), unter
786.432 KiB. Diese Messprozesse halten zusätzliche Modellkopien und
Diagnosedaten; der Wert ist kein Android-Inferenzbedarf. Einzelprozesslimit
120 Sekunden, Gesamtlimit 1.800 Sekunden, JVM-Heap 1 GiB bleiben eingehalten.

Quellen und vollständige Rohbelege:
`inference-registration.json`, `inference-evidence/`,
`numerical-verification.json`, `resume-verification.json`.
Die früheren Qualitäts-/Kapazitätsanforderungen sowie der verbrauchte
48-Fälle-Finaltest bleiben unverändert. Es gibt keine Modell-Promotion.

Nächste begrenzte Frage: Welche zusätzlichen Parameter der 6,56M- und
9,71M-Konstruktionen erhalten beim ersten Rückwärtslauf tatsächlich ein
Lernsignal? Das wird separat registriert und ohne Optimizerupdate untersucht,
bevor ein neuer, qualitätsgebundener Trainingsversuch gerechtfertigt wird.
