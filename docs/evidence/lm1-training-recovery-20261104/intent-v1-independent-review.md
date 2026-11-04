# Unabhängiger Ergebnisaudit: Intent-V1

Stand: 2026-11-04. Geprüfter Lauf: `training-recovery-study-v1`.
Prüfumfang: vorhandene V1-Belege und deren Bindung; keine Trainingsausführung,
keine Modellinferenz, keine Änderung registrierter V1-Dateien, kein Öffnen des
versiegelten Tests.

## Urteil

**Der Entwicklungsabbruch ist korrekt. V1 belegt gelerntes Antwortformat und
verbesserte Dev-Metriken, erfüllt aber den vorher festgelegten Entwicklungserfolg
nicht. Ein unabhängiger Testnachweis oder eine Freigabe als allgemeiner
Sprachkern liegt nicht vor.**

Die Auswahl ergibt für beide trainierten Arme regelkonform Schritt 128.
Das kleine Modell erzielt 8/24, das breite Modell 10/24 exakte Dev-Antworten.
W1 verbessert sich gegenüber W0 um zehn Fälle und erfüllt damit die verlangte
Verbesserung um mindestens vier Fälle. Es verfehlt jedoch die zusätzlich
erforderlichen 12/24. `NO_DEVELOPMENT_SIGNAL` ist deshalb die richtige Entscheidung.
Die Auswertung wurde nicht durch Absenken dieser Grenze fortgesetzt. [P, S, W, N]

Bei der Prüfung existierten weder `selection-freeze.json` noch `test-*`-Logs
oder eine `intent-summary.json` im V1-Laufverzeichnis. Der No-Signal-Beleg
enthält `testEvaluated=false`. Diese Artefakte und der gebundene Controller
belegen den vorgesehenen Abbruchpfad. Sie sind kein vollständiger forensischer
Nachweis über alle eventuell außerhalb dieses Controllers laufenden Prozesse.
Der Reviewer hat den versiegelten Test nicht geöffnet. [N, C]

## 1. Quellen-, Daten- und Ausführungsbindung

Alle vier äußeren Intent-Quittungen — zwei Trainingsprozesse und zwei
nachträgliche Kapazitätsprüfungen — nennen dieselbe Registrierung, denselben
Quellcommit und dasselbe JAR. Ihre gespeicherten Log-Hashes stimmen mit den
erneut berechneten Hashes der vorhandenen Logs überein. [SR, WR, SC, WC]

| Bindung | Beobachteter Wert |
|---|---|
| Quellcommit laut Laufquittungen | `def5acf9cbba82ee51a53cc06fecc52e9cd0324d` |
| Registrierungs-SHA256 | `540f646e6963bcc9902a362f77d963fd39aa76513837125b86085768af8b8992` |
| JAR-SHA256 | `901b94e4c4d9b54c5ba422a5fa3b00bd9f28f187b68ab0b79f7f9b8fc6b62911` |
| Controller-SHA256 | `409f87f5a174804d7290c29972d6b4e2bbb19ad293ac1da027122c3f28d14d4f` |
| Intent-Harness-SHA256 | `0fb9552ad0d8cc4d2f6cc43ad8700374da27ae9ca2e794f1765fca45c11c2e9d` |
| V1-Intent-Protokoll-SHA256 | `0be368ff103e284a225d0cd30a0da0cd3a70be9ffdf3bc47b1900f8cac96a0d5` |
| Trainingsdaten-SHA256 | `a474d13be70162fb5c8452df43777f06681fec91ba1d4498ea10d453180a718b` |
| Dev-Daten-SHA256 | `c70f7e3ffac4a6f3c42ebfd29becacfe2cc4eb446fb86187298c5f264e367dfe` |
| Gemeinsamer Token-Schedule-SHA256 | `169f1e1e0338393f9100e5719c4d2cd75d72e90b4942a875b6de36574b732aad` |

Die lokale Registrierung, das ausführbare JAR und alle in der Registrierung
aufgeführten Quell-/Protokoll-/Manifestdateien sowie Train-/Dev-Dateien wurden
erneut gehasht: keine Abweichung. Die registrierte Testdatei wurde ausdrücklich
aus dieser erneuten Dateiprüfung ausgeschlossen. Gewichte, Tokenizerdatei und
Standardbibliothek wurden für diesen Audit nicht erneut gelesen; ihre
Identitäten werden aus den gebundenen Lauf- und Kapazitätsbelegen berichtet.
Die Veröffentlichung des genannten Commits auf dem Remote wurde in diesem
Audit nicht nochmals unabhängig abgefragt. [R, S, W, SR, WR]

Beide Arme protokollieren 128 lückenlos nummerierte Updates, Batchgröße 4,
Lernrate `0.0001`, Warmup 4, Weight Decay `0.01`, Seed `20261104` und zwei Worker.
Alle protokollierten Verluste und Gradientennormen sind endlich. Der Zähler
überwachter Ziele stimmt bei jedem Schritt mit `Schritt × 4 × 2` überein und
endet bei 1.024. Beispielreihenfolge und Token-Schedule sind zwischen S1 und
W1 identisch. [S, W]

| Arm | Parameter laut Manifest | Schritte | Prozesszeit insgesamt | Exitcode | Timeout |
|---|---:|---:|---:|---:|---|
| S1 | 132.160 | 128 | 7,361 s | 0 | Nein |
| W1 | 6.562.304 | 128 | 200,075 s | 0 | Nein |

Die Prozesszeiten enthalten den jeweiligen Harness einschließlich Prüfungen
und Dev-Auswertungen; sie sind keine isolierte Messung der Gradientenberechnung.
Beide liegen innerhalb der registrierten 600 Sekunden. [SR, WR]

## 2. Auswahl aus den Dev-Snapshots

Die Rohbelege enthalten jeweils genau die Checkpoints 0, 32, 64 und 128 sowie
die vier zugehörigen Dev-Messungen. Die Auswahlregel wurde unabhängig erneut
angewendet: mehr exakte Erfolge, danach kleinere Antwort-NLL, danach weniger
Schritte; Schritt 0 ist eine Kontrolle und kein trainierter Auswahlkandidat.
Die ausgewiesene Auswahl und ihr Checkpointhash stimmen mit dieser Regel und
dem zugehörigen `CHECKPOINT`-Datensatz überein. [P, S, W]

| Arm | Schritt | Exact-Digit+EOS | Antwort-NLL | EOS-NLL nach richtiger Antwort |
|---|---:|---:|---:|---:|
| S0 | 0 | 0/24 | 8,842065 | 6,472776 |
| S1 | 32 | 6/24 | 1,700410 | 0,566087 |
| S1 | 64 | 6/24 | 1,484160 | 0,150938 |
| **S1 ausgewählt** | **128** | **8/24** | **1,443976** | **0,079734** |
| W0 | 0 | 0/24 | 8,842064 | 6,472777 |
| W1 | 32 | 6/24 | 1,774986 | 0,059563 |
| W1 | 64 | 8/24 | 1,387846 | 0,040672 |
| **W1 ausgewählt** | **128** | **10/24** | **1,248373** | **0,031383** |

Die 192 `CASE`-Datensätze wurden unabhängig aus den protokollierten Ausgaben,
Tokenzahlen und Stop-Gründen erneut bewertet: zwei Arme × vier Messstände ×
24 Fälle. Jeder Messstand enthält 24 verschiedene IDs und genau sechs Fälle
je Klasse. Alle erneut berechneten Einzelurteile und Summen stimmen mit den
`correct`-Flags und `SCORE`-Summen überein. [S, W]

## 3. Formatfortschritt und verbleibende Klassenfehler

Die Ausgangsmodelle liefern in allen 24 Fällen jeweils vier Inhaltstokens und
enden am Tokenlimit; kein Fall hat das geforderte Ausgabeformat. Ab Schritt 32
liefern beide trainierten Arme in sämtlichen 24 Fällen genau eine Aktionsziffer
und enden mit EOS. Bei diesen Intent-Eingaben gibt es dann weder vorzeitiges
EOS noch fehlendes EOS. Das ist ein klar beobachteter Formatfortschritt. [S, W]

Dieser Fortschritt ist von der Zuordnung der Absichten zu trennen:

- S1 antwortet bei Schritt 32 immer `1`, bei Schritt 64 immer `2`.
- W1 antwortet bei Schritt 32 immer `0`.
- Diese konstanten Ausgaben erreichen auf dem ausgeglichenen Dev-Satz bereits
  6/24. Sie belegen für sich keine Unterscheidung der vier Absichten.

Am ausgewählten Schritt 128 ergeben sich folgende, aus den Einzelfällen
rekonstruierte Verwechslungsmatrizen. Die Zeilen sind Sollklassen; die Spalten
sind tatsächlich erzeugte, jeweils korrekt mit EOS beendete Aktionsziffern.
Ungültige Formate gibt es an diesem Messstand bei beiden Armen nicht. [S, W]

### S1: kleines trainiertes Modell

| Sollklasse | Ausgabe 0 | Ausgabe 1 | Ausgabe 2 | Ausgabe 3 | Richtig |
|---|---:|---:|---:|---:|---:|
| 0: keine Handlung/Abbruch | 0 | 3 | 3 | 0 | 0/6 |
| 1: Datei öffnen | 1 | 4 | 1 | 0 | 4/6 |
| 2: Informationen suchen | 0 | 2 | 4 | 0 | 4/6 |
| 3: Notiz merken | 2 | 2 | 2 | 0 | 0/6 |

### W1: breites trainiertes Modell

| Sollklasse | Ausgabe 0 | Ausgabe 1 | Ausgabe 2 | Ausgabe 3 | Richtig |
|---|---:|---:|---:|---:|---:|
| 0: keine Handlung/Abbruch | 2 | 2 | 0 | 2 | 2/6 |
| 1: Datei öffnen | 0 | 6 | 0 | 0 | 6/6 |
| 2: Informationen suchen | 0 | 0 | 2 | 4 | 2/6 |
| 3: Notiz merken | 2 | 0 | 4 | 0 | 0/6 |

Insbesondere erreicht die Notizklasse bei beiden Modellen **0/6**. Bei W1
ist die Dateiklasse vollständig richtig, während die anderen Klassen deutlich
schwächer bleiben. Eine zuverlässige vierteilige Absichtserkennung ist damit
nicht nachgewiesen. Da ausschließlich Symbole ausgewertet werden, haben diese
Fehlzuordnungen keine tatsächlichen Werkzeugaufrufe ausgelöst. [S, W, H]

Der gepaarte Vergleich derselben 24 Fälle lautet: sechs Fälle beide richtig,
vier nur W1 richtig, zwei nur S1 richtig, zwölf beide falsch. W1 gewinnt netto
zwei Fälle beziehungsweise 8,33 Prozentpunkte auf dem zur Auswahl verwendeten
Dev-Satz. Das ist kein unabhängiger Testnachweis und kein belastbarer allgemeiner
Vorteil zusätzlicher Parameter. [S, W]

## 4. Unveränderte Kapazitätsprüfung

Die frischen Ausgangscheckpoints bestehen ihre jeweiligen ursprünglichen
Kapazitätsgates: drei Ersttoken-Beobachtungen und drei vollständige
32-Token-Generationen. Der Hash jedes `capacity-before.tsv` stimmt mit dem
im Trainingslog gebundenen Hash überein. Die Gate-Erfolgsmeldung steht vor
dem ersten `TRAIN_STEP`. [SB, WB, S, W]

Nach dem Training scheitern beide ausgewählten Checkpoints am unveränderten
Kapazitätstest. Die Kapazitätsbelege nennen dieselben Checkpointhashes wie
die jeweilige Auswahl. In allen acht Aufrufen je Arm — zwei Warmup-Aufrufen
und sechs gemessenen Aufrufen — endet die Ausgabe sofort mit EOS und enthält
null neue Inhaltstokens. Der Befund lautet jeweils:

`NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD`

Die Prüfprozesse enden deshalb mit Exitcode 1, jeweils ohne Timeout.
Es handelt sich in diesen Belegen um fehlende Generationsarbeit, nicht um ein
Überschreiten der Zeit- oder Speichergrenzen. Die kurzen Laufzeiten der
Nachmessung dürfen nicht als Geschwindigkeitsgewinn für 32 generierte Tokens
ausgegeben werden, weil diese Arbeit nicht stattgefunden hat. [SC, WC]

Damit ist eine Regression im festgelegten neutralen Kapazitätsprompt belegt.
Die dortige sofortige EOS-Ausgabe widerspricht nicht dem korrekten Digit+EOS
auf den anders aufgebauten Intent-Prompts; beide Ergebnisse müssen gemeinsam
berichtet werden. V1 besitzt keine Grundlage für eine allgemeine Promotion.

## 5. Abbruch und Folgerung für den nächsten Versuch

Die geprüften V1-Unterlagen rechtfertigen folgende Aussagen:

1. Das Antwortformat wurde auf Dev gelernt; Antwort- und EOS-NLL sanken.
2. Die vier Klassen werden noch nicht zuverlässig getrennt.
3. Die registrierte Dev-Schwelle wurde verfehlt; der Test bleibt ungeöffnet.
4. Die ursprüngliche Generationsprüfung ist nach dem Training nicht bestanden.
5. V1 ist ein dokumentierter negativer Entwicklungsversuch, kein erfolgreicher
   unabhängiger Generalisierungsnachweis und keine Modellfreigabe.

Der angekündigte Folgeversuch mit 512 Schritten muss als neuer registrierter
Lauf ab den Originalen behandelt werden. V1-Ergebnisse werden nicht ersetzt
oder nachträglich umgedeutet. Eine auf 512 Schritte gestreckte Cosine-Kurve
ändert gegenüber V1 auch die Lernratenfolge innerhalb der ersten 128 Schritte;
der spätere Vergleich beschreibt daher einen längeren Lauf mit geändertem
Lernratenhorizont, nicht bloß das Anhängen identischer Updates. Dieser Review
bewertet keine V2-Ergebnisse und öffnet dafür keinen Test.

## Quellen und überprüfbare Belege

Alle Werte wurden aus den folgenden lokalen Belegen abgeleitet. Einträge
`CASE`, `SCORE`, `SELECTION`, `MANIFEST`, `INPUT` und `CAPACITY_RESULT` sind
maschinell lesbare TSV-Datensätze.

- **[P]** [V1-Intent-Protokoll](../../../experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md), insbesondere Auswahl, Dev-Gate und getrennte Kapazitätsprüfung.
- **[R]** [V1-Registrierung](../../../experimental/lm1-training-recovery-20261104/registration.json).
- **[C]** [Studiencontroller](../../../experimental/lm1-training-recovery-20261104/run_registered_study.py).
- **[H]** [Intent-Harness](../../../experimental/lm1-training-recovery-20261104/LmIntentStudy.kt).
- **[S]** [intent-small.tsv](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-small.tsv), SHA256 `7853830f25752e9b959ea190d242be987b1a605bd091ba8f35bd4a1d6da6f2ef`.
- **[W]** [intent-wide.tsv](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-wide.tsv), SHA256 `27737b8fdb7f2cff4d2d8cb990b897c4a3e34a081a07c97f9b6f10d95646f0e9`.
- **[SR]** [Trainingsquittung Small](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-small.receipt.json).
- **[WR]** [Trainingsquittung Wide](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-wide.receipt.json).
- **[SB]** [Initiales Gate Small](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/intent-small/capacity-before.tsv), SHA256 `2a159f50d3adcfce08dcf04927ef6647e9fe41129378dd750edb0454c410d674`.
- **[WB]** [Initiales Gate Wide](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/intent-wide/capacity-before.tsv), SHA256 `5f463ca8eeecd5445dcb6264d822f3be3479d94cf99fd4152b7e972ff35d65e7`.
- **[SC]** [Kapazitätsprüfung Small nach Training](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-small-capacity-after.tsv), SHA256 `efb099101574715ed4976fc060a49b52e4fe129f2f1aca4f349936042aaeacfc`; [Quittung](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-small-capacity-after.receipt.json).
- **[WC]** [Kapazitätsprüfung Wide nach Training](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-wide-capacity-after.tsv), SHA256 `01967e7108ec8b7b35bd12496abacfd80ae11819d435d432a06394a6b0fd5469`; [Quittung](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/logs/intent-wide-capacity-after.receipt.json).
- **[N]** [No-Development-Signal-Beleg](sandbox:/workspace/scratch/3112c86b5191/training-recovery-study-v1/no-development-signal.json), SHA256 `07c8d161e389447f21d4f2236b56f8f1a692081c2cea6ceb042730f9aeb204a6`.
