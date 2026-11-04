# Separater V2-Nachtrag: die veröffentlichten Rohbelege sind jetzt geprüft

Prüfdatum: 04.11.2026. Ausschließlich geprüfter neuer Stand:
[`e3c5aeadd6336f843bd625d56431208d3a03b5ad`](https://github.com/Ullmann27/device-ai/commit/e3c5aeadd6336f843bd625d56431208d3a03b5ad),
direkter Nachfolger von `be4e49600fed1db4c697230bb8e42c150dbfbdaf`.

**Die im vorherigen Bericht festgestellte V2-Evidenzlücke ist an diesem
neueren Stand aufgelöst.** Die neu veröffentlichten Rohlogs und Receipts
bestätigen die bisherigen Summary-Zahlen: zwei vollständig protokollierte
512-Schritt-Läufe; regelkonforme Dev-Auswahl Small bei Schritt 256 und Wide
bei Schritt 128, beide mit 13/24; bestandenes Entwicklungsgate für den
begrenzten Intent-Vergleich; weiterhin zwei fehlgeschlagene ursprüngliche
Kapazitätsprüfungen und kein veröffentlichter Finaltest. [A] [S] [R]

Der frühere [be4e-Bericht](README.md), sein Skript und sein JSON bleiben
bytegleich. Seine damalige Einordnung als `SUMMARY_ONLY` gilt weiterhin
für den damals geprüften Commit. Dieser Nachtrag ergänzt neue Belege,
ohne die Prüfungshistorie umzuschreiben. Spätere Commits werden hier nicht
weiterverfolgt.

## 1. Umfang und Integritätsbindung der neuen Prüfung

[`audit_published_v2.py`](audit_published_v2.py) verarbeitet **38 gepinnte
Textquellen**, prüft vier Prozess-Receipt/Log-Paare, alle **1.024 Updates
beider Arme** und **192 Dev-Einzelfälle**. Es berechnet zudem die 32
veröffentlichten Online-Epochenmittel erneut. Alle überprüften Werte und
Entscheidungen stimmen mit den Rohbelegen überein. Ergebnis: Exit 0,

```text
PUBLISHED_V2_RAW_EVIDENCE_CONSISTENT_DEV_GATE_PASS_CAPACITY_FAIL_TEST_DEFERRED
```

Das Skript benutzt nur die hashgebundenen Textparser und die
Kapazitätsnachrechnung des unveränderten ersten Audits. Es prüft die drei
alten Dateihashes vor und nach der Auswertung. Es startet ausschließlich
lesende Git-Unterprozesse, keine Trainings-/Modellprozesse, und liest weder
Intent-Datensätze noch deren Generator, Checkpoint-, Tokenizer- oder JARbytes.
Die bereits veröffentlichten Dev-Ausgaben werden aus den `CASE`-Zeilen
neu bewertet. [A]

| Bindung | Unabhängig gegen veröffentlichte Textbytes geprüft |
| --- | --- |
| Ausführungs-Quellcommit in allen vier Receipts | `fc2eb797210bee25091caf734aacbfd694a3372e` |
| Registrierungs-SHA256 | `7f87a5a6582f23cdf1d647dba4daed8c494afa970f5224329a78bc0337b854e6` |
| JAR-SHA256 laut allen Receipts und Registrierung | `d92acf410494bba98ad5299b0abfe13bdac3bb508dd9dd7bbf25f96a3ba5a20d` |
| Gemeinsamer Beispielschedule | `0c977d357e9a3c19496890faf9f3f593169064380475a171a3553ba30364ff48` |
| Gemeinsamer Tokensequenzschedule | `56fe2778eaccc2e29e13e629295452199ba19f94211c10fbae07cd4b6f3f375c` |
| Small-Trainingslog SHA256 | `a6ce3a1c31b2faa398abf2a0d5ba1c80015c9bb01ad52a0a4faeca5ab075f154` |
| Wide-Trainingslog SHA256 | `3538ebc9857a8f2d10f184b6c03ab4179e9b17dffb54959c3fd84deb8318ac57` |

Alle gelesenen registrierten Quellen stimmen mit ihren Datei-SHA256
überein. Die vier Log-SHA256 wurden aus den tatsächlichen Git-Blobbytes
berechnet. Die Input-Hashzeilen stimmen mit den registrierten Baseline-,
Tokenizer-, TRAIN- und DEV-Identitäten überein; die zugehörigen Eingabedateien
wurden nicht geöffnet. [A]

Die Laufbelege, Checkpointzeilen, Auswahl-TSVs, Kapazitätslogs und die zuvor
veröffentlichte Summary nennen konsistente Modellidentitäten. Dies ist eine
Prüfung der veröffentlichten **Belegkette**, keine unabhängige binäre
Checkpointprüfung oder Wiederholung des Trainings.

Der Git-Diff dieses Nachtrags enthält nur Dokumentation und Ergebnisbelege;
keine ausführbaren Studienquellen wurden in `e3c5…` verändert. Eine
veröffentlichte Ergebnisergänzung ist daher keine Reparatur der getrennt
geprüften Controller-Gates. [A]

## 2. Vollständigkeit, gleiche Budgets und korrekte frische Ausgangsgates

Beide Arme protokollieren lückenlos Schritte 1 bis 512. Jeder Schritt
überwacht vier vollständige Beispiele mit je zwei Zielen — Aktionsziffer
und EOS — und erhöht den kumulativen Zähler um acht. Damit ergeben sich
**4.096 überwachte Ziele pro Arm**. Die vollständigen Beispiel- und
Tokensequenz-Hashes sind zwischen Small und Wide identisch. [A]

Die frischen Ausgangscheckpoints bestehen ihre Kapazitätsprüfungen jeweils
mit drei Ersttoken-Beobachtungen und drei vollständigen 32-Token-Ausgaben.
Die Initial-Gate-Zeile bindet den entsprechenden Rohloghash und steht vor
dem ersten Update. Auch die drei anfänglichen Wahrscheinlichkeits-Paritätsproben
je Arm erfüllen die vorgesehene Grenze von 0,00001. Die dokumentierte
Ausführung startet somit von den registrierten frischen Initialmodellen;
kein V1-Fortsetzungscheckpoint wird als Eingang protokolliert. [A] [R]

| Größe | Parameter | Updates vollständig ausgeführt | Überwachte Ziele insgesamt | Prozesszeit einschließlich Prüfungen und Dev-Auswertungen |
| --- | ---: | ---: | ---: | ---: |
| Small | 132.160 | 512 | 4.096 | 14,776 s |
| Wide | 6.562.304 | 512 | 4.096 | 653,790 s |

Beide Trainingsprozesse enden mit Exit 0 ohne Timeout. Der registrierte
V2-Rahmen beträgt 900 Sekunden und 3 GiB maximalen Heap pro Trainingsprozess,
intern 840 Sekunden für die Schleife. Wide liegt mit 653,790 Sekunden
**über der früheren 600-Sekunden-Prozessgrenze von V1**. Es besteht den
ausdrücklich neu registrierten V2-Rahmen; es ist keine Demonstration
unveränderten Trainingsressourcenbedarfs. [A] [P]

## 3. Freie Dev-Ausgaben und Auswahl

Alle 192 veröffentlichten Fälle werden erneut anhand der dekodierten
Ausgabe, `tokens=1` und tatsächlichem Stop-Grund `EOS` geprüft. Sämtliche
Einzelurteile, Klassen-/Gesamtsummen, Token- und EOS-Zähler sowie die
Antwort-/EOS-NLL-Mittel stimmen. Die EOS-Wahrscheinlichkeiten werden aus
ihren Kotlin-Float32-Darstellungen rekonstruiert; ihre neu berechneten
Double-Mittel stimmen exakt mit den veröffentlichten Werten. [A]

| Größe | Schritt | Freies Ziffer+EOS exakt | Antwort-NLL | EOS-NLL nach richtiger Antwort | Klassenrichtig, jeweils von 6 |
| --- | ---: | ---: | ---: | ---: | --- |
| Small | 0 | 0/24 | 8,842065 | 6,472776 | 0, 0, 0, 0 |
| Small | 128 | 12/24 | 1,303824 | 0,036308 | 4, 6, 0, 2 |
| **Small ausgewählt** | **256** | **13/24** | **1,353089** | **0,014164** | **2, 6, 5, 0** |
| Small | 512 | 12/24 | 1,754427 | 0,005881 | 2, 6, 4, 0 |
| Wide | 0 | 0/24 | 8,842064 | 6,472777 | 0, 0, 0, 0 |
| **Wide ausgewählt** | **128** | **13/24** | **1,713391** | **0,015203** | **2, 6, 4, 1** |
| Wide | 256 | 11/24 | 2,084924 | 0,004672 | 2, 6, 1, 2 |
| Wide | 512 | 11/24 | 2,653523 | 0,001763 | 2, 6, 1, 2 |

Die Klassenreihenfolge ist Abbruch/keine Handlung, Datei öffnen,
Informationen suchen, Notiz merken. An sämtlichen drei trainierten
Messpunkten besitzen beide Größen bei allen 24 Dev-Fällen das korrekte
Ziffer-plus-EOS-Format. Die verbleibenden Fehler sind dort falsche
Aktionsziffern. Die Notizklasse erreicht bei den ausgewählten Modellen
Small 0/6 und Wide 1/6. [A]

Die unveränderte Auswahlregel priorisiert die Zahl exakter Treffer,
danach kleinere Antwort-NLL, danach weniger Updates. Daher ist Small256
trotz höherer Antwort-NLL gegenüber Small128 die richtige Wahl: 13
Treffer haben Vorrang vor zwölf. Wide128 liegt mit 13 Treffern vor den
späteren elf. Die gespeicherten Checkpointhashes und Auswahl-TSVs bestätigen
genau diese Regel. [A] [P]

| Ausgewählter Arm | Gebundener Checkpoint-SHA256 |
| --- | --- |
| Small256 | `c129e5e902020943da8d8bc902c4bcf7ed92ce746324231fd41a02d6acae5309` |
| Wide128 | `7c39f7c522192db2a5f85a54bbcc407ca1224251077a432b836b631e6e405ab2` |

Wide erfüllt nun die beiden Entwicklungsvoraussetzungen: mindestens 12/24
und mindestens vier zusätzliche Treffer gegenüber dem eigenen W0 mit
0/24. Das erlaubt nach dem Studienprotokoll den begrenzten weiteren
Vergleich, nicht bereits einen Finaltest-Erfolg oder eine allgemeine
Modellfreigabe. [A] [P]

Der gepaarte Vergleich auf denselben 24 Dev-Fällen ergibt elf beidseits
richtige, zwei nur bei Small richtige, zwei nur bei Wide richtige und
neun beidseits falsche Fälle. **Die ausgewählten Größen haben somit
denselben Dev-Gesamtscore, keinen Nettovorteil für Wide.** Small256 enthält
zudem 2.048 überwachte Ziele bis zum ausgewählten Checkpoint, Wide128
1.024. Gleiche vollständige 512-Schritt-Studienbudgets bedeuten hier keine
gleichen Trainingspräfixe der ausgewählten Modelle. [A]

## 4. Online-Loss verbessert sich weiter, Entwicklungsleistung nicht

Die neu veröffentlichten Epochenmittel sind exakt reproduzierbar. Jedes
Mittel umfasst 32 aufeinanderfolgende Minibatch-Verluste mit jeweils acht
überwachten Zielen. Die erste→letzte Epoche ergibt:

| Größe | Epoche 1 | Epoche 16 |
| --- | ---: | ---: |
| Small | 2,9016930885 | 0,1767951860 |
| Wide | 1,4522977371 | 0,0037078357 |

Diese Verluste werden während der Updates gemessen; die Gewichte ändern
sich zwischen den Minibatches. Es handelt sich weder um eine Auswertung
aller Trainingsbeispiele an einem festen Checkpoint noch um
Generalisierungswerte. Der sinkende Online-Loss berechtigt daher nicht,
den späteren schlechteren Wide-Dev-Stand als besseren Kandidaten zu
wählen. Wide fällt nach Schritt 128 von 13 auf elf Treffer, und seine
Antwort-NLL steigt auf den festen Dev-Daten bis 2,653523. [A] [E]

Auch der Vergleich mit V1 bleibt begrenzt: Der erneut geprüfte vollständige
Harness-Diff verändert `STEPS` von 128 auf 512 bei einer Cosine-Formel mit
Nenner `STEPS - 4`. Bereits die Lernratenfolge früher Updates unterscheidet
sich. Verbesserungen gegenüber V1 können nicht allein zusätzlichen Updates
oder einer isolierten Parameterzahlerhöhung zugeschrieben werden. [A] [P]

## 5. Die ursprüngliche Kapazitätsprüfung bleibt fehlgeschlagen

| Nach der Dev-Auswahl | Small256 | Wide128 |
| --- | --- | --- |
| Ersttoken-Beobachtungen | 0/3 | 3/3 |
| Vollständige 32-Token-Ausgaben | 0/3 | 0/3 |
| Tatsächliche Token bei jeder gemessenen 32-Token-Anforderung | 0, sofort EOS | 1, anschließend EOS |
| Kapazitätsurteil | FAIL | FAIL |
| Begründung | `NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD` | `INCOMPLETE_32_TOKEN_WORKLOAD` |
| Prozess-Exit / Timeout | 1 / Nein | 1 / Nein |
| Beobachteter VMHWM | 72.380 KiB | 175.512 KiB |
| Berichtete ausgewählte Checkpointgröße | 529.671 Bytes | 26.250.247 Bytes |

Die ursprünglichen Anforderungen bleiben 64 unveränderte Prompttoken,
drei Messwiederholungen, höchstens 1.500 ms pro Ersttokenaufruf, höchstens
5.000 ms pro vollständigem 32-Token-Aufruf und höchstens 786.432 KiB
beobachtetem VMHWM; Kapazitätsprozesse behalten 1 GiB maximalen Heap und
120 Sekunden Grenze. Die Nachrechnung prüft die sechs Messaufrufe und die
beiden Warmups jedes Vorher-/Nachher-Logs sowie ihre Checkpointbindung. [A]

Wide erzielt also einen begrenzten Ausgabeunterschied gegenüber dem
früheren sofortigen EOS: ein Token wird erzeugt. **Ein Token erfüllt
die registrierte 32-Token-Arbeitslast weiterhin nicht.** Die kurzen
Nachmesszeiten sind kein gültiger Durchsatznachweis für 32 Tokens.

## 6. Status, Grenzen und nächster Schritt

Die neu überprüften Rohbelege unterstützen die bereits veröffentlichte
Kennzeichnung
`ELIGIBLE_DEVELOPMENT_CANDIDATE_TEST_DEFERRED_FOR_REGISTERED_SUFFIX_COMPARISON`.
Am genau geprüften e3c5-Stand fehlen weiterhin veröffentlichte
Studienwahl-/Freeze-Quittungen, finale Testlogs und V3-Studienergebnisse.
Die Belege melden `testEvaluated=false`; diese Prüfung liest den Test
nicht. Ihre Feststellungen gelten für den Git-Baum und belegen nicht
forensisch die Abwesenheit jedes denkbaren unveröffentlichten Prozesses. [A] [S]

Damit ist die nächste offene Aufgabe enger geworden: Die V2-Rohbeleglücke
muss nicht mehr als Hindernis gelten. Offen bleiben die unabhängig
abzusichernden getrennten Freeze-/Test-Gates, die tatsächlichen Ergebnisse
der bereits registrierten V3-Variante und die Veröffentlichung der
regelkonformen Studienwahl samt vollständiger Kapazitätsbindung vor einer
einmaligen Bestätigung. Dieser Nachtrag führt keine neue Studienwahl,
Parameter-/Lernratensuche oder Modellübernahme aus. Die vorhandene
Originalauswahl bleibt bestehen.

## Reproduktion und Quellen

Aus dem Repositoryroot mit dem gepinnten Git-Objekt und den unveränderten
drei Dateien des ersten Audits:

```sh
python experimental/weight-space-20261104/recovery-gate-repair/result-review/audit_published_v2.py --repo . --output /tmp/pr508-v2-addendum.json
```

Erwartet: Exit 0 und der oben genannte Status. Die JSON-Ausgabe enthält
alle gelesenen Quellen mit SHA256/Git-Blob-ID, sämtliche neu berechneten
Dev-Scores, Verwechslungsmatrizen, Epochenmittel, Prozessquittungen und
Kapazitätsbefunde. Sie ist deterministisch.

- **[A]** [Unabhängig nachgerechneter V2-JSON-Bericht](published-v2-results.json).
- **[R]** [Neue vollständige V2-Rohbelege im gepinnten Commit](https://github.com/Ullmann27/device-ai/tree/e3c5aeadd6336f843bd625d56431208d3a03b5ad/docs/evidence/lm1-training-recovery-20261104/study-v2).
- **[S]** [Unveränderte, jetzt gegen Rohbelege geprüfte V2-Zusammenfassung](https://github.com/Ullmann27/device-ai/blob/e3c5aeadd6336f843bd625d56431208d3a03b5ad/docs/evidence/lm1-training-recovery-20261104/study-v2/intent-summary.json).
- **[P]** [Registriertes V2-Protokoll](https://github.com/Ullmann27/device-ai/blob/e3c5aeadd6336f843bd625d56431208d3a03b5ad/experimental/lm1-training-recovery-20261104/long-study/INTENT_LONG_PROTOCOL.md) und [gebundener V2-Harness](https://github.com/Ullmann27/device-ai/blob/e3c5aeadd6336f843bd625d56431208d3a03b5ad/experimental/lm1-training-recovery-20261104/long-study/LmIntentLongStudy.kt).
- **[E]** [Veröffentlichte Online-Epochenmittel, für V2 vollständig nachgerechnet](https://github.com/Ullmann27/device-ai/blob/e3c5aeadd6336f843bd625d56431208d3a03b5ad/docs/evidence/lm1-training-recovery-20261104/intent-epoch-losses.json).
