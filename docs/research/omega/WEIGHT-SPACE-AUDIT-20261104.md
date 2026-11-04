# Eigene Modellgewichte: abgeschlossener Versuch vom 04.11.2026

**Status: RESEARCH_SHADOW.** Der vorhandene trainierte LM1-Sprachkern wurde von
132.160 auf **16.011.776 echte Modellparameter** erweitert. Alle festgelegten
Kapazitätsstufen bis zu dieser Größe bestanden die Prüfungen auf dem Host. Die
nächste Stufe wurde vor ihrer Erzeugung an der bestehenden Größenbeschränkung
der aktuellen Laufzeit gestoppt. Eine physische Belastungsgrenze wurde damit nicht
ermittelt.

Die Erweiterung bewahrte die ursprüngliche Vorhersagefunktion. **Eine höhere
Antwortqualität wurde nicht nachgewiesen.** Die beiden zusätzlich trainierten
Varianten wurden nach fehlgeschlagener Generierungsprüfung verworfen. Die
festgelegte Qualitätsauswahl behält das ursprüngliche kleine Modell; der große,
bestandene Checkpoint bleibt als experimenteller Kapazitätskandidat erhalten.
Belege: [Stufen](weight-space-20261104-evidence/ladder.json),
[Auswahl](weight-space-20261104-evidence/frozen-selection.json) und
[Endauswertung](weight-space-20261104-evidence/final.json).

## 1. Ausgangsmaterial und Quellenstand

Die eigene SWB1-Sicherung enthält 4.113 Parameter und den gespeicherten
Update-Zähler 335.150. Alle Werte sind endlich; Dekodieren und erneutes Kodieren
ergaben identische Bytes. Das Original blieb unverändert. Seine SHA-256 lautet
`5ba8ba7e22efc19a099148d224ab000dcd21953bb74b5d6504f49621fe56b2b6`.
SWB1 wurde mit vorhandenen selbst verfassten Regressionsfällen geprüft. Die
Teilmenge `ANDROID_VALIDATION` wurde ebenfalls ausschließlich auf dem Host
ausgeführt. Sie ist weder ein neuer unabhängiger Holdout noch ein Gerätenachweis.
[SWB1-Prüfdatei](weight-space-20261104-evidence/swb1-baseline.json).

Für die Erweiterung wurde der bereits trainierte **LM1-minGRU** verwendet:
Vokabular 512, Breite 64, zwei Schichten, MLP-Breite 256, Kontext 256.

- Ausgangscheckpoint: `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.
- Gebundener Tokenizer: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
- Datenzweig: `d2304a7658e1256b160c8012e1bac28268ff64a5`.
- Laufzeitbasis aus PR #478: `d8fd7f7cc5aa721b2c4a975c527c5123e216a944`.
- Versuchsstand vor den Läufen: `ea9ea37fccccda09aebeae2884dc138ba6241951`.
- Nachgehärteter Orchestrierungsstand: `ee5779725690c573d3310c061c6e2e6faf5f0a8b`.

LM1 ist das eigene kleine Sprachmodell; es handelt sich nicht um Gemma oder
Qwen. Das Video und die verifizierten Primärquellen betreffen eine andere,
gezielte Änderung von Qwen-MLP-Spalten. Unsere Normskalierung ist eine separat
gekennzeichnete Kalibrierung und keine Replikation dieser Methode. Der
[Quellenvermerk](WEIGHT-SPACE-SOURCES-20261104.md) dokumentiert auch, dass
Bildmontagen und OCR-Untertitel gesichtet wurden, aber keine vollständige
Audio-Transkription vorliegt.

## 2. Vorab festgelegte Messung und Auswahl

Gemessen wurde unter Linux x86_64 mit OpenJDK 21.0.12.1, neun gemeldeten CPUs
und einem Inferenzthread. Kapazitätsprüfungen starteten jeweils eine frische JVM
mit maximal 1 GiB Heap. Fortsetzungstraining verwendete zwei Worker und maximal
3 GiB Heap. Das [Umgebungsprotokoll](weight-space-20261104-evidence/environment.json)
bindet JAR, Quelltexte, Daten und das ursprüngliche Protokoll über Hashes.

Die Auswahl erfolgte auf einem getrennten Entwicklungsdatensatz anhand der
**Bits pro UTF-8-Byte (BPB; kleiner ist besser)**. Innerhalb von 0,00001 BPB
galt die kleinere zulässige Variante als vorzugswürdig. Die Kandidaten wurden
am **04.11.2026 um 10:21:38 UTC** vor Öffnen des Holdouts eingefroren. Die
Auswahldatei hat SHA-256
`8c433e76c28ceafa5e5149c9b0469d37eb4e217f1887868bce2a2ed1330dc6f9`.

Der versiegelte Holdout umfasst **32 separat verfasste synthetische deutsche
Absätze, 3.973 ausgewertete Bytes und 2.082 Inhaltstoken**. Seine SHA-256 ist
`d5a5858f0eaff24495cd98c337fc662271c4a1b41027b1142764735c244942af`.
Er wurde erst nach der Auswahl für die finale Auswertung verwendet. Diese
kleine Sprachwahrscheinlichkeitsprüfung ersetzt keinen breiten Fähigkeits-
oder Praxistest. Maßgeblich bleiben das
[Versuchsprotokoll](../../../experimental/weight-space-20261104/PROTOCOL.md)
und die [eingefrorene Auswahl](weight-space-20261104-evidence/frozen-selection.json).

## 3. Direkte Gewichtsänderung: alle sechs Kandidaten schlechter

Jeweils ein Normtensor wurde unabhängig mit 0,75 oder 1,25 multipliziert.
Sonstige Tensoren und das Original blieben unverändert. Alle sechs Kandidaten
hatten schlechtere Entwicklungs-BPB als das Ausgangsmodell; ausgewählt wurde
deshalb die unveränderte Variante.
[Kalibrierung](weight-space-20261104-evidence/calibration.json).

| Kandidat | Entwicklungs-BPB |
|---|---:|
| Original | **2,352541** |
| `l0.n1 × 0,75` | 2,686109 |
| `l0.n1 × 1,25` | 2,571146 |
| `l1.n1 × 0,75` | 2,442079 |
| `l1.n1 × 1,25` | 2,461609 |
| `nf × 0,75` | 2,377438 |
| `nf × 1,25` | 2,464431 |

## 4. Stufenweise Erweiterung bis zur Laufzeitgrenze

Die Breitenstufen replizieren gelernte Kanäle mit angepassten eingehenden
Gewichten und angepasstem finalem Normfaktor. Deterministische, paarweise
gegenläufige Störungen (`noiseStd=0.001`, Seed `20261104`) ermöglichen später
unterschiedliche Lernschritte der Kopien. Zusätzliche Residualschichten beginnen
mit nullgesetzten Ausgangsprojektionen und erhalten dadurch zunächst die
ursprüngliche Funktion. Es wurden echte Matrizen geladen und ausgeführt.

Jede Erweiterungsstufe bestand den Vergleich vollständiger Vorwärtsrechnung,
rekurrenter schrittweiser Berechnung und **dreier identischer Greedy-Ausgaben**.
Die größte beobachtete Logitabweichung betrug 0,0000247956, die größte
Wahrscheinlichkeitsabweichung 0,0000011623; die schrittweise Logitabweichung lag
höchstens bei 0,0000152588. Das sind gemessene numerische Übertragungsabweichungen,
keine Qualitätsgewinne. [Paritätsbelege](weight-space-20261104-evidence/ladder.json).

Die Kapazitätsprüfung verwendete einen festen Prompt mit 64 Inhaltstoken,
einen Aufwärmdurchgang und drei Messungen. Zulässig waren maximal 1.500 ms
für den separaten Ein-Token-Aufruf, 5.000 ms für den vollständigen
32-Token-Aufruf und 768 MiB Prozessspeichermaximum. Außerdem mussten tatsächlich
ein beziehungsweise 32 neue Token entstehen. Jeder Prüfprozess hatte ein
120-Sekunden-Limit.

| Stufe: Breite / Schichten | Parameter | Ein-Token-Aufruf, max. ms | 32-Token-Aufruf, max. ms | VmHWM, MiB | Ergebnis |
|---|---:|---:|---:|---:|---|
| 64 / 2, Original | 132.160 | 6,145 | 13,538 | 80,79 | bestanden |
| 128 / 2 | 460.928 | 15,807 | 27,546 | 89,82 | bestanden |
| 256 / 2 | 1.708.288 | 61,892 | 107,197 | 126,31 | bestanden |
| 512 / 2 | 6.562.304 | 207,120 | 309,075 | 152,57 | bestanden |
| 512 / 3 | 9.712.128 | 297,939 | 382,238 | 197,75 | bestanden |
| 512 / 4 | 12.861.952 | 357,807 | 563,861 | 234,17 | bestanden |
| **512 / 5** | **16.011.776** | **505,048** | **755,396** | **257,21** | **letzte bestandene Stufe** |
| 512 / 6, nur vorab berechnet | 19.161.600 | — | — | — | vor Erzeugung abgelehnt |

Der Ein-Token-Wert ist die Gesamtdauer eines separaten Aufrufs einschließlich
Promptverarbeitung, keine direkt gemessene Streaming-Latenz. Für VmHWM gilt
konservativ der größte Wert aus sämtlichen `CAPACITY_MEMORY`-Messpunkten und
dem `CAPACITY_RESULT`-Eintrag. Bei der letzten Stufe beträgt dieser Wert
**263.388 KiB = 257,21484375 MiB**. Der spätere Zusammenfassungseintrag enthält
262.548 KiB; der höhere frühere Messpunkt `measured_3_first` ist maßgeblich.
Alle übrigen Tabellenwerte bleiben nach der Prüfung sämtlicher Messpunkte
unverändert. Das Speicherlimit von 768 MiB wird weiterhin eingehalten.
[Unabhängige Ergebnisprüfung](weight-space-20261104-evidence/independent-result-audit.json)
und [unverändertes Rohprotokoll](weight-space-20261104-evidence/logs/depth-5-capacity.tsv).

Ausgeführt wurde `LmCapacityBenchmark.kt` mit SHA-256
`89b067df80edbb104712c31b382c6d84d142fff258bbf156989a06e40e63d209`.
Die Tabelle berichtigt ausschließlich die Auswertung der vorhandenen
Messpunkte. Eine nachträgliche Korrektur der Maximalwerterfassung im Code ist
von diesem bereits abgeschlossenen Modellmesslauf zu unterscheiden; die
ursprünglichen Rohlogs wurden nicht verändert.

Der letzte bestandene Checkpoint umfasst **64.048.511 Bytes**, SHA-256
`93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312`.
Die nächste Stufe hätte bereits **76.646.400 Parameterbytes** benötigt und
überschreitet damit den bestehenden 64-MiB-Checkpointvertrag
(67.108.864 Bytes). Sie wurde nicht alloziert oder ausgeführt.
**Erreicht wurde eine Software-Kompatibilitätsgrenze.** Keine gemessene Stufe
verletzte zuvor die festgelegten Zeit- oder Speichergrenzen.

## 5. Fortsetzungstraining und tatsächliche Qualität

Beide Varianten erhielten denselben eingefrorenen Trainingsplan aus dem
ursprünglichen TRAIN-Dokumentsplit: **16 Schritte, jeweils insgesamt 3.255
Zieltoken**, Batchgröße 4, AdamW-Lernrate 0,0001, Gewichtszerfall 0,01,
zwei Aufwärmschritte und Gradientengrenze 1. Datensatz-, Tokenizer- und
Tokensequenz-Hashes stimmen zwischen beiden Läufen überein. Alte VALIDATION-
und TEST-Dokumente gehörten nicht zur Fortsetzung. Der vorbereitete Korpus
enthielt 77 Dokumente und 2.520 Segmente; davon wurde nur der feste Plan
verarbeitet. [Trainingsbelege](weight-space-20261104-evidence/frozen-selection.json).

Im kleinen Modell änderten sich 132.159 Gewichte, im großen **16.011.740**.
Die protokollierten Trainingszeiten betrugen 758 ms beziehungsweise 52.765 ms.
Die maximale Differenz zwischen zuvor replizierten Embeddingkanälen erreichte
im großen Modell 0,000894234. Die zusätzlichen Parameter waren somit lernfähig.

| Variante | Parameter | Entwicklungs-BPB | Holdout-BPB | Strikte Antwortproben | Zulässig nach Generierungstest |
|---|---:|---:|---:|---:|---|
| Original | 132.160 | 2,352541416 | 3,130515333 | 0/6 | ja; ausgewählt |
| Große Übertragung, vor Fortsetzung | 16.011.776 | 2,352541414 | 3,130515322 | 0/6 | ja; Kapazitätskandidat |
| Kleines Modell nach Fortsetzung | 132.160 | 2,352506801 | 3,127703803 | 0/6 | nein |
| Großes Modell nach Fortsetzung | 16.011.776 | 2,463361438 | 3,189226226 | 0/6 | nein |

Original und große Übertragung sind bei dieser Messgenauigkeit qualitativ
gleichwertig. Das kleine fortgesetzte Modell hat geringfügig niedrigere BPB;
das große fortgesetzte Modell verschlechtert beide BPB-Werte. **Beide
Fortsetzungen erzeugten im Kapazitätstest jedoch sofort EOS und null neue
Token.** Damit fehlten sowohl eine beobachtete erste Ausgabe als auch die
vollständige 32-Token-Arbeit. Kurze Laufzeiten solcher Abbrüche gelten nicht
als bestandene Leistungsprüfung. Beide Kandidaten sind ausgeschlossen.
[Endauswertung](weight-space-20261104-evidence/final.json),
[kleiner Fehlversuch](weight-space-20261104-evidence/logs/continued-small-capacity.tsv),
[großer Fehlversuch](weight-space-20261104-evidence/logs/continued-expanded-capacity.tsv).

Alle vier Varianten verfehlten sämtliche sechs fest vorgegebenenen Rechen-
und Befolgungsproben bei exaktem Zeichenkettenvergleich. Die Proben und
Ausgaben sind in den vier `*-probes.tsv` unter
[Rohprotokolle](weight-space-20261104-evidence/logs/) erhalten. Aus diesem
Versuch ergibt sich kein nachgewiesener Gewinn an allgemeiner Intelligenz
oder zuverlässiger Anweisungsbefolgung.

Identische Adam-Einstellungen isolieren außerdem nicht den Einfluss der
Parameterzahl: Replikation, finaler Normfaktor und neue Residualpfade verändern
die Optimierungsbedingungen. Geprüft wurden diese beiden konkreten
Fortsetzungsrezepte. Der Fehlschlag beweist nicht, dass größere Modelle
grundsätzlich schlechter lernen.

## 6. Verifikation, Entscheidung und Wiederholung

Die reale JVM-Testsuite bestand **24/24 JUnit-Tests in 3,055 Sekunden**,
einschließlich Tensorübertragung, Quellimmutabilität, Checkpoint-Rundlauf,
schrittweiser Inferenz und tatsächlicher Lernschritte.
[JUnit-Protokoll](weight-space-20261104-evidence/experiment-junit.txt).
Zusätzlich bestanden **24 separate fokussierte Orchestrierungsprüfungen**
für Identitätsbindung, abgelehnte Fehlzustände und Auswahlregeln. Das sind
keine weiteren JUnit-Tests oder Modellbenchmarks; ihr Wiederholungslauf
startete keine Modellprozesse.
[Prüfbeleg](weight-space-20261104-evidence/orchestration-checks.txt).

Nach Abschluss der Modellmessung wurden zwei konkrete Auswertungsrisiken im
Werkzeug abgesichert: Der Speicherbericht behält das Maximum aller beobachteten
VmHWM-Werte; die Schlussauswertung bindet sich an einen vorab eingelesenen
Auswahlsnapshot und bricht bei zwischenzeitlicher Änderung ab. Der abschließende
Kotlin-Build ist ohne Warnungen erfolgreich. Ein zusätzlicher Baseline-Smoke-Test
bestätigt die Maximalwertausgabe; zwei synthetische Auswahltests bestätigen
unveränderte und während der Auswertung manipulierte Snapshots. Außerdem wurden
die historischen Eingaben aus Git rekonstruiert: 80 geprüfte Dateien sind mit
den tatsächlich verwendeten Eingaben bytegleich. Diese Nachprüfung änderte
keinen historischen Modellwert und startete keine neue Optimierungsrunde.
[Nachprüfungsnachweis](weight-space-20261104-evidence/post-review-validation.json).

Die unabhängige Abschlussprüfung verifizierte 15 Checkpoints und 49
Ausführungsnachweise. Ihr strenger Gesamtprüfstatus bleibt wegen der offen
dokumentierten historischen Speicherabweichung negativ; die Korrektheit aller
Kapazitätsentscheidungen ist separat bestätigt. Diese Differenz wird nicht als
vollständig fehlerfreier historischer Bericht ausgegeben.

Die Originale blieben unverändert. Die eingefrorene Auswahl bevorzugt das
Original wegen gleicher Qualität gegenüber der großen Übertragung und
geringerer Größe. Für den Auftrag, die Kapazität schrittweise auszubauen,
bleibt der oben identifizierte 16-Millionen-Checkpoint als letzte bestandene
experimentelle Stufe verfügbar. Die fehlgeschlagenen Fortsetzungen werden
nicht zum Ersatzmodell erklärt.

Das bereitgestellte Modellpaket enthält den letzten bestandenen Checkpoint,
den gebundenen Tokenizer, eine deutschsprachige Ergebnisübersicht und
Dateiprüfsummen. Paketidentität und Download stehen im
[Dateinachweis](weight-space-20261104-evidence/deliverable.json).

Android-/Fold7-Validierung ist **NOT_RUN**. `adb` war nicht im PATH;
eine Geräteverbindung ist nicht verifiziert. Die gemessene Host-Flüssigkeit
belegt deshalb keine entsprechende Telefonleistung. Eine produktive
Integration wurde nicht vorgenommen; der Arbeitsstand befindet sich in
[Draft-PR #492](https://github.com/Ullmann27/device-ai/pull/492).
Main, aktive Modellwahl, Signierung und S01-Grenze bleiben unverändert.

Die Ausführungshinweise stehen im
[Experiment-README](../../../experimental/weight-space-20261104/README.md).
Für die Nachprüfung sind insbesondere `environment.json`, `calibration.json`,
`ladder.json`, `frozen-selection.json`, `final.json` sowie die vollständigen
Befehle, Rückgabecodes und Hashes der `*.receipt.json` im
[Evidenzverzeichnis](weight-space-20261104-evidence/) maßgeblich. Ein neuer
Optimierungsversuch benötigt ein neues vorab festgelegtes Verfahren und einen
frischen Holdout; die hier offengelegten Testtexte sind jetzt verbraucht.
