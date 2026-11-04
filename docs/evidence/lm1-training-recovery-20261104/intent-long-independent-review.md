# Unabhängige Prüfung der LM1-Intent-Läufe V2

Datum: 2026-11-04. Umfang: abgeschlossene V1/V2-Forschungsprotokolle; Status `RESEARCH_SHADOW`.

## Ergebnis

Beide V2-Arme haben die registrierten **512 Optimierungsschritte und 4.096 überwachten Zielpositionen** vollständig erreicht. Die vorgeschriebene Auswahl ergibt **Small bei Schritt 256 mit 13/24** und **Wide bei Schritt 128 mit 13/24** richtigen Entwicklungsfällen. Beide ausgewählten Checkpoints verfehlen anschließend den unveränderten allgemeinen Kapazitätstest. Small erzeugt auf dessen Prompt sofort EOS; Wide erzeugt **ein Token und anschließend EOS**. Für Wide wäre die Beschreibung „keine Ausgabe“ sachlich falsch.

Beim breiten Modell verbessert sich die feste Entwicklungskurve nach Schritt 128 nicht weiter: Wide fällt von 13/24 auf 11/24 bei 256 und 512; seine Antwort-NLL steigt dabei von 1,713391496 auf 2,653522817. Gleichzeitig sinkt sein online gemessener Trainingsloss über die 16 Epochen erheblich. Die vorhandenen Daten zeigen somit erfolgreiche Anpassung an die wiederholten Trainingsbeispiele und eine zunehmende Lücke zur festen Entwicklungsbewertung. Sie belegen keine allgemeine Aufgabensicherheit oder Überlegenheit des größeren Modells.

Der registrierte V2-Entwicklungsschwellenwert ist erreicht. Eine abschließende Testauswertung ist in den hier geprüften V1/V2-Trainings- und Kapazitätsprotokollen nicht enthalten. Die korrekte Trennung lautet: **Entwicklungssignal vorhanden; unabhängiger Aufgabentest hier nicht ausgewertet; allgemeiner Kapazitätstest fehlgeschlagen.**

## Quellen, Identität und Prüfumfang

Die Ausführung von V2 ist an Commit **`fc2eb797210bee25091caf734aacbfd694a3372e`** gebunden. Für die Interpretation wurden das [registrierte V2-Protokoll](https://github.com/Ullmann27/device-ai/blob/fc2eb797210bee25091caf734aacbfd694a3372e/experimental/lm1-training-recovery-20261104/long-study/INTENT_LONG_PROTOCOL.md) und der [ausgeführte Kotlin-Harness](https://github.com/Ullmann27/device-ai/blob/fc2eb797210bee25091caf734aacbfd694a3372e/experimental/lm1-training-recovery-20261104/long-study/LmIntentLongStudy.kt) an diesem unveränderlichen Commit gelesen. Die zugehörigen Git-Blob-IDs sind `01c1f0508877a353924a3f35a4d7129f28f33e64` und `3e8efe053de3a3736f535e156115cb0c3ea65c6a`.

| Bindung | V1 | V2 |
|---|---|---|
| Ausführungscommit | `def5acf9cbba82ee51a53cc06fecc52e9cd0324d` | `fc2eb797210bee25091caf734aacbfd694a3372e` |
| Registrierung, SHA-256 | `540f646e6963bcc9902a362f77d963fd39aa76513837125b86085768af8b8992` | `7f87a5a6582f23cdf1d647dba4daed8c494afa970f5224329a78bc0337b854e6` |
| JAR, SHA-256 | `901b94e4c4d9b54c5ba422a5fa3b00bd9f28f187b68ab0b79f7f9b8fc6b62911` | `d92acf410494bba98ad5299b0abfe13bdac3bb508dd9dd7bbf25f96a3ba5a20d` |
| Indexplan, SHA-256 | `653f41488a28238eddd4d65a38168b4ed0879b65641108bb0ad96e5447d87d2b` | `0c977d357e9a3c19496890faf9f3f593169064380475a171a3553ba30364ff48` |
| Token-/Targetplan, SHA-256 | `169f1e1e0338393f9100e5719c4d2cd75d72e90b4942a875b6de36574b732aad` | `56fe2778eaccc2e29e13e629295452199ba19f94211c10fbae07cd4b6f3f375c` |

Innerhalb jeder Version stimmen die beiden Pläne zwischen Small und Wide überein. Über beide Versionen sind die protokollierten Eingabebindungen identisch:

| Eingabe | SHA-256 laut allen vier Trainingsprotokollen |
|---|---|
| Eigenes ursprüngliches LM1 | `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd` |
| Tokenizer | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |
| Training | `a474d13be70162fb5c8452df43777f06681fec91ba1d4498ea10d453180a718b` |
| Entwicklung | `c70f7e3ffac4a6f3c42ebfd29becacfe2cc4eb446fb86187298c5f264e367dfe` |

Die unabhängige Auswertung hat die folgenden konkreten Konsistenzprüfungen bestanden:

- Alle **1.280 `TRAIN_STEP`-Zeilen** aus V1/V2 enthalten lückenlose, eindeutige Schrittnummern. Kumulative Targets steigen exakt um acht pro Schritt. Loss und Gradientennorm sind endlich; die Zeitstempel steigen monoton.
- Alle **384 `CASE`-Zeilen** wurden gegen die 16 gespeicherten `SCORE`-Aggregate geprüft. Die 24 Entwicklungs-IDs und ihre Zielklassen bleiben über Messpunkte, Arme und Versionen unverändert. Jede Klasse enthält sechs Fälle.
- Freie Textausgabe, Tokenzahl und Stopgrund wurden für die Genauigkeit erneut zusammengeführt: Erfolg bedeutet exakt die erwartete Ziffer, genau ein ausgegebenes Token und `stop=EOS`. Es erfolgt weder Trimmen des Textes noch nachträgliche Auswahl unter vier möglichen Labels.
- Antwort-/EOS-NLL, Klassenzähler, vorzeitiges EOS, fehlendes EOS und ausgegebene Tokenzahlen stimmen mit den Einzelfällen überein. Die pro Fall als Kotlin-`Float` serialisierten EOS-Wahrscheinlichkeiten wurden vor der Mittelung als Float32 rekonstruiert. Danach stimmen auch diese Mittelwerte exakt mit den protokollierten Double-Aggregaten überein; die kürzere Dezimalschreibweise ist kein Messfehler.
- Die Auswahlregel wurde unabhängig rekonstruiert und mit `SELECTION`, `selection.tsv`, `CHECKPOINT` und dem tatsächlichen `CAPACITY_MODEL`-/`CAPACITY_RESULT`-SHA des nachfolgenden Prozesses verglichen.
- Die SHA-256-Werte der tatsächlichen Trainings- und Kapazitätslogdateien stimmen mit ihren Receipts überein. Die initialen Kapazitätslogs stimmen mit ihren Gate-Loghashes überein. Je Version sind zwölf benannte Rohdateien und deren archivierte Gegenstücke unter `study-v1/` beziehungsweise `study-v2/` byteidentisch: Trainingslogs, Kapazitätslogs, Receipts, initiale Kapazitätslogs und Auswahldateien.
- Alle geprüften Receipts melden `registeredBytesUnchanged=true`, die erwartete gemeinsame Quell-/Registrierungs-/JAR-Bindung und keinen Timeout. Diese historischen Bindungen werden anhand der Receipts berichtet; dieser Audit hat keine JVM neu gestartet und keine Modellparameter erneut berechnet.

Die ursprünglichen Prüfpfade waren `/workspace/scratch/3112c86b5191/training-recovery-study-v1` und `/workspace/scratch/3112c86b5191/training-recovery-study-v2`. Finaltestdateien wurden für diesen Audit nicht geöffnet. V3 und seine registrierten Dateien sind außerhalb dieses Prüfumfangs geblieben.

## Vollständigkeit des Trainings und Ausgangsgates

V2 verwendet 128 Trainingsbeispiele, Batchgröße vier und 32 Minibatches je vollständigem Datendurchlauf. Die 512 Updates entsprechen **16 Epochen und 2.048 Beispielverwendungen**. Pro Verwendung werden eine Antwort und ein EOS überwacht: insgesamt 2.048 Antworttargets plus 2.048 EOS-Targets. Der Zähler von 4.096 enthält damit die Wiederholungen derselben 128 Beispiele. Promptpositionen tragen im registrierten `ANSWER_AND_EOS_ONLY`-Ziel keinen eigenen Loss bei.

| Merkmal | Small | Wide |
|---|---:|---:|
| Parameter | 132.160 | 6.562.304 |
| Fertige Updates | 512 | 512 |
| Überwachte Targets | 4.096 | 4.096 |
| Entwicklungs-/Checkpoint-Messpunkte | 0, 128, 256, 512 | 0, 128, 256, 512 |
| Größte initiale Wahrscheinlichkeitsabweichung, drei Probes | 0 | 0,000002071261406 |
| Initialer Kapazitätstest | PASS | PASS |
| Gewählter Schritt | 256 | 128 |
| Targets bis zum gewählten Schritt | 2.048 | 1.024 |

Die initiale Parität liegt innerhalb des im Harness geforderten Grenzwerts `0.00001`. Die initialen frischen Kapazitätsprozesse sind jeweils an den gespeicherten Schritt-0-Checkpoint gebunden und liegen vor dem ersten Trainingsschritt. Die kompletten 512 Schritte wurden auch dann noch protokolliert, wenn der später ausgewählte Checkpoint früher im Lauf liegt.

Quellen: [Small-Trainingslog](study-v2/logs/intent-small.tsv), [Wide-Trainingslog](study-v2/logs/intent-wide.tsv), [Small-Ausgangskapazität](study-v2/intent-small/capacity-before.tsv), [Wide-Ausgangskapazität](study-v2/intent-wide/capacity-before.tsv).

## Vollständige feste Entwicklungskurven von V2

Alle Werte stammen aus denselben 24 Entwicklungsfällen am jeweiligen eingefrorenen Modellstand. Genauigkeit wird durch freie greedy Generation gemessen. NLL ist in natürlichen Logarithmen angegeben. `EOS-NLL` wird nach der korrekten vorgegebenen Antwort bewertet. Die zusätzliche kombinierte NLL ist `(Antwort-NLL + EOS-NLL) / 2` und entspricht der Gewichtung der beiden überwachten Positionen. Tabellenwerte sind gerundet; die verlinkten Rohlogs enthalten die vollständige Präzision.

| Arm | Schritt | Richtig / 24 | Antwort-NLL | EOS-NLL | Kombinierte NLL | Richtig je Klasse 0/1/2/3 |
|---|---:|---:|---:|---:|---:|---|
| Small | 0 | 0 | 8,842064727 | 6,472776360 | 7,657420544 | 0 / 0 / 0 / 0 |
| Small | 128 | 12 | 1,303824453 | 0,036307520 | 0,670065986 | 4 / 6 / 0 / 2 |
| Small | **256** | **13** | 1,353088923 | 0,014163900 | 0,683626412 | 2 / 6 / 5 / 0 |
| Small | 512 | 12 | 1,754427225 | 0,005881280 | 0,880154252 | 2 / 6 / 4 / 0 |
| Wide | 0 | 0 | 8,842063923 | 6,472777436 | 7,657420679 | 0 / 0 / 0 / 0 |
| Wide | **128** | **13** | 1,713391496 | 0,015203023 | 0,864297259 | 2 / 6 / 4 / 1 |
| Wide | 256 | 11 | 2,084923759 | 0,004671863 | 1,044797811 | 2 / 6 / 1 / 2 |
| Wide | 512 | 11 | 2,653522817 | 0,001763260 | 1,327643039 | 2 / 6 / 1 / 2 |

Die Format- und EOS-Messungen zeigen, warum geringe EOS-NLL keine korrekte Entscheidung garantiert:

| Arm | Schritt | Mittlere EOS-Wahrscheinlichkeit vor der Antwort | Vorzeitiges EOS / 24 | Fehlendes EOS / 24 | Ausgegebene Tokens gesamt |
|---|---:|---:|---:|---:|---:|
| Small | 0 | 0,147503638640 | 0 | 24 | 96 |
| Small | 128 | 0,000595919289 | 0 | 0 | 24 |
| Small | 256 | 0,000087277821 | 0 | 0 | 24 |
| Small | 512 | 0,000029864348 | 0 | 0 | 24 |
| Wide | 0 | 0,147503597041 | 0 | 24 | 96 |
| Wide | 128 | 0,000013129763 | 0 | 0 | 24 |
| Wide | 256 | 0,000007277918 | 0 | 0 | 24 |
| Wide | 512 | 0,000008559865 | 0 | 0 | 24 |

An sämtlichen trainierten V2-Messpunkten erzeugen alle 24 Entwicklungsfälle genau eine Ziffer aus `0` bis `3` mit anschließendem EOS. Dennoch sind am ausgewählten Stand jeweils elf Antworten falsch. Die Aufgabe scheitert auf diesen Fällen somit an der gewählten Klasse. Beim Small-Checkpoint 256 ist Klasse 3 sogar 0/6; Wide128 erreicht dort 1/6. Die beobachtete Formattreue allein erfüllt keine klassenweise Qualitätsanforderung.

Die Auswahl folgt dem registrierten Vorrang der **Anzahl richtiger Antworten**, anschließend der Antwort-NLL, anschließend der geringeren Schrittzahl. Deshalb wird Small256 trotz höherer Antwort-NLL als Small128 ausgewählt. Wide128 gewinnt nach Antwortanzahl gegenüber Wide256 und Wide512. Ein Vergleich der beiden ausgewählten Modelle enthält unterschiedliche Trainingsdauern; die Modellgröße ist damit kein isolierter Erklärungsfaktor.

| Gewählter Checkpoint | SHA-256 |
|---|---|
| Small256 | `c129e5e902020943da8d8bc902c4bcf7ed92ce746324231fd41a02d6acae5309` |
| Wide128 | `7c39f7c522192db2a5f85a54bbcc407ca1224251077a432b836b631e6e405ab2` |

Quellen: [Small-Auswahl](study-v2/intent-small/selection.tsv), [Wide-Auswahl](study-v2/intent-wide/selection.tsv) und die oben verlinkten Trainingslogs.

## Nachgelagerter allgemeiner Kapazitätstest

Dieser Test prüft die **ausgewählten** Checkpoints Small256 und Wide128. Er ist keine Kapazitätsmessung der letzten Schritt-512-Checkpoints. Der ursprüngliche 64-Token-Prompt und die Anforderung von 32 neu ausgegebenen Tokens bleiben unverändert; EOS wird beim Decoding berücksichtigt.

| Messung | Small256 | Wide128 |
|---|---|---|
| Drei gemessene Aufrufe mit Anforderung 1 Token | jeweils 0 Tokens, EOS | jeweils 1 Token, `MAX_TOKENS` |
| Drei gemessene Aufrufe mit Anforderung 32 Tokens | jeweils 0 Tokens, EOS | jeweils 1 Token, danach EOS |
| Beobachtungen eines ersten Tokens | 0/3 | 3/3 |
| Vollständig erfüllte 32-Token-Anforderung | 0/3 | 0/3 |
| Maximal beobachteter VmHWM | 72.380 KiB | 175.512 KiB |
| Prozessstatus | Exit 1, kein Timeout | Exit 1, kein Timeout |
| Ergebnis | FAIL | FAIL |

Small wird wegen `NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD` abgelehnt. Wide wird ausschließlich wegen `INCOMPLETE_32_TOKEN_WORKLOAD` abgelehnt. Weder ein RAM-Grenzübertritt noch ein Zeitlimit ist als Ablehnungsgrund protokolliert. Die kurzen Laufzeiten des abgebrochenen 32-Token-Auftrags belegen keine vollständig erfüllte 32-Token-Leistung.

Beide geladenen Modelle werden mit vollständigem Layout und endlichen Gewichten gemeldet. Die Checkpoint- und Tokenizer-Hashes der frischen Benchmarkprozesse stimmen mit Auswahl und Eingabebindung überein. Die Ablehnung betrifft die verlangte Generationsleistung, keine gemeldete Beschädigung des Dateiformats.

Quellen: [Small-Kapazitätslog](study-v2/logs/intent-small-capacity-after.tsv), [Wide-Kapazitätslog](study-v2/logs/intent-wide-capacity-after.tsv), [Small-Receipt](study-v2/logs/intent-small-capacity-after.receipt.json), [Wide-Receipt](study-v2/logs/intent-wide-capacity-after.receipt.json).

## Zeitgrenzen und tatsächliche Laufzeiten

| Lauf | Äußere Laufzeit in Sekunden | Zähler am letzten Update in Sekunden | Registriertes äußeres Limit | Status |
|---|---:|---:|---:|---|
| V1 Small, 128 Updates | 7,361385 | 5,141 | 600 s | Exit 0, kein Timeout |
| V1 Wide, 128 Updates | 200,075481 | 171,603 | 600 s | Exit 0, kein Timeout |
| V2 Small, 512 Updates | 14,775973 | 13,043 | 900 s | Exit 0, kein Timeout |
| V2 Wide, 512 Updates | 653,789603 | 628,021 | 900 s | Exit 0, kein Timeout |

Die V2-Schleifenzähler bleiben zusätzlich unter dem registrierten internen Limit von 840 Sekunden. Die äußeren Laufzeiten umfassen weitere Arbeit im Prozess, unter anderem Ausgangsgates und Evaluation. Der Zähler des letzten Updates ist keine isolierte Summe reiner Optimierer-Rechenzeit. Die 653,79 Sekunden für Wide verletzen das V2-Limit von 900 Sekunden nicht.

Die separaten V2-Nachtests benötigen 0,422785 Sekunden für Small und 2,027707 Sekunden für Wide bei jeweils 120 Sekunden Prozesslimit. Die Training-Receipts dokumentieren `-Xmx3g`, die separaten Benchmark-Receipts `-Xmx1g`.

Quellen: [V1 Small](study-v1/logs/intent-small.receipt.json), [V1 Wide](study-v1/logs/intent-wide.receipt.json), [V2 Small](study-v2/logs/intent-small.receipt.json), [V2 Wide](study-v2/logs/intent-wide.receipt.json) und die Kapazitätsreceipts.

## Epochenmittel aus den bereits vorhandenen Online-Losswerten

Pro Epoche wurden exakt 32 aufeinanderfolgende `TRAIN_STEP.answerEosNll`-Werte arithmetisch gemittelt. Jeder Minibatch enthält acht überwachte Zielpositionen; deshalb entspricht das arithmetische Mittel hier auch einer Gewichtung nach Targetanzahl. Jede Epoche umfasst 256 überwachte Targets. Der Loss wird auf dem jeweiligen Minibatch **vor dessen Update** ermittelt. Die Modellgewichte ändern sich zwischen den 32 Messungen.

Diese Werte sind **Online-Trainingsloss in Nats pro überwachtem Antwort-/EOS-Target**. Sie sind keine nachträgliche Bewertung eines festen Checkpoints auf dem vollständigen Trainingssatz und keine Generalisierungsmetrik. Außerdem kombinieren sie Antwort und EOS. Für einen Vergleich mit der festen Entwicklungsbewertung ist deshalb die oben zusätzlich berechnete kombinierte Dev-NLL die passende Zielgewichtung; die reine Dev-Antwort-NLL misst nur eine der beiden Positionen.

| Epoche | Schritte | V1 Small | V1 Wide | V2 Small | V2 Wide |
|---:|---:|---:|---:|---:|---:|
| 1 | 1–32 | 2,911616800 | 1,452461566 | 2,901693088 | 1,452297737 |
| 2 | 33–64 | 0,948757845 | 0,789660505 | 0,930811065 | 0,775301312 |
| 3 | 65–96 | 0,788097359 | 0,502071557 | 0,762048493 | 0,379881984 |
| 4 | 97–128 | 0,755763053 | 0,317063877 | 0,673892572 | 0,172190220 |
| 5 | 129–160 | — | — | 0,487414004 | 0,123836739 |
| 6 | 161–192 | — | — | 0,410251503 | 0,096685781 |
| 7 | 193–224 | — | — | 0,333469017 | 0,074411364 |
| 8 | 225–256 | — | — | 0,284264800 | 0,066880017 |
| 9 | 257–288 | — | — | 0,254480682 | 0,035111898 |
| 10 | 289–320 | — | — | 0,222014445 | 0,007173492 |
| 11 | 321–352 | — | — | 0,212990690 | 0,005576933 |
| 12 | 353–384 | — | — | 0,211166787 | 0,004916940 |
| 13 | 385–416 | — | — | 0,205851011 | 0,004472631 |
| 14 | 417–448 | — | — | 0,195549475 | 0,004155190 |
| 15 | 449–480 | — | — | 0,181589344 | 0,003906100 |
| 16 | 481–512 | — | — | 0,176795186 | 0,003707836 |

V1 endet registrierungsgemäß nach Epoche vier. Vollständige numerische Werte, Minima/Maxima pro Epoche, Schrittbereiche, Targetzahlen und Quellhashes stehen in [intent-epoch-losses.json](intent-epoch-losses.json). Die Berechnung verwendet ausschließlich die vorhandenen Logs; es wurde keine zusätzliche Inferenz ausgeführt.

V2 Wide passt die Trainingsbeispiele nach dieser Metrik stark an. Gleichzeitig steigt seine kombinierte feste Dev-NLL von 0,864297259 bei Schritt 128 über 1,044797811 auf 1,327643039. Auch Small verschlechtert sich bei dieser festen Metrik von 0,670065986 über 0,683626412 auf 0,880154252. Die Beobachtung ist mit einer Anpassung an trainingsspezifische Muster vereinbar. Die Logs allein isolieren keine einzelne Ursache wie Architektur, Aufgabenformulierung oder Datenverteilung und rechtfertigen keine sichere Kausalbehauptung dazu.

## Vergleich mit V1 und Grenzen der Schlussfolgerung

Die vollständigen V1-Genauigkeits- und Antwort-NLL-Kurven lauten:

| Schritt | Small richtig / 24 | Small Antwort-NLL | Wide richtig / 24 | Wide Antwort-NLL |
|---:|---:|---:|---:|---:|
| 0 | 0 | 8,842064727 | 0 | 8,842063923 |
| 32 | 6 | 1,700410277 | 6 | 1,774986061 |
| 64 | 6 | 1,484160220 | 8 | 1,387846220 |
| 128 | 8 | 1,443975840 | 10 | 1,248372773 |

Quellen: [V1 Small-Log](study-v1/logs/intent-small.tsv), [V1 Wide-Log](study-v1/logs/intent-wide.tsv). In V1 wurden beide Schritt-128-Checkpoints ausgewählt; beide erzeugten im allgemeinen Kapazitätstest null neue Tokens und EOS.

V2 beginnt erneut mit den ursprünglichen Gewichten und der frischen Verbreiterung. Es setzt die fehlgeschlagenen V1-Checkpoints nicht fort. Der Kosinus-Lernratenverlauf wird von 128 auf 512 Updates gestreckt. Dadurch unterscheiden sich nach dem Warmup bereits die ersten 128 Lernraten zwischen V1 und V2. Ein Unterschied zwischen den Runden lässt sich folglich nur der Kombination aus längerer Laufzeit und verändertem Verlauf zuordnen, nicht ausschließlich den zusätzlichen Updates.

Der V2-Wide-Stand erreicht 13/24 gegenüber 0/24 seines untrainierten W0. Er erfüllt damit die registrierte Voraussetzung von mindestens 12/24 und mindestens vier zusätzlichen richtigen Entwicklungsfällen für eine spätere Testfreigabe. Der registrierte Nachweis begrenzten Aufgabenlernens verlangt jedoch weiterhin einen separat eingefrorenen Test mit mindestens 36/48 richtigen Antworten, mindestens 8/12 in jeder Klasse und mindestens acht zusätzlichen richtigen Fällen gegenüber beiden untrainierten Kontrollen. Ein Vorteil gegenüber dem trainierten kleinen Modell hat zusätzlich eine eigene Testschwelle. Diese Anforderungen können aus den 13/24 Entwicklungsfällen nicht abgeleitet werden.

Die korrekte berichtbare Aussage ist daher: **vollständiges echtes Training, bessere Entwicklungsleistung als die untrainierten Kontrollen, kein weiterer Genauigkeitsgewinn nach dem jeweils ausgewählten Checkpoint, gleiche Entwicklungsgenauigkeit beider ausgewählter Varianten und fortbestehendes Versagen der allgemeinen 32-Token-Anforderung.** Eine Produktivfreigabe, allgemeine Sprachfähigkeit, zuverlässige autonome Aktionen oder Erfolg auf dem abschließenden Test sind durch diese Evidenz nicht belegt.

## Hashes der zentralen Rohbelege

| Beleg | SHA-256, unabhängig aus den vorhandenen Bytes berechnet |
|---|---|
| V1 Small-Training | `7853830f25752e9b959ea190d242be987b1a605bd091ba8f35bd4a1d6da6f2ef` |
| V1 Wide-Training | `27737b8fdb7f2cff4d2d8cb990b897c4a3e34a081a07c97f9b6f10d95646f0e9` |
| V2 Small-Training | `a6ce3a1c31b2faa398abf2a0d5ba1c80015c9bb01ad52a0a4faeca5ab075f154` |
| V2 Wide-Training | `3538ebc9857a8f2d10f184b6c03ab4179e9b17dffb54959c3fd84deb8318ac57` |
| V2 Small-Nachkapazität | `9fbd48e66b7993ceaf3b82bcae5e32ed934ac1b2e2baf4d7f6faf7ee7c9d7eaf` |
| V2 Wide-Nachkapazität | `d5a178857895f3aa46792b70049899fcff5752271b270dee76fd1aea8e6d65a7` |
| Epochenberechnung JSON | `8f3c21aaeedb9758ecbe618d3f5a45798647c1bc55a2f1e47e8f3c603ac95b4f` |
