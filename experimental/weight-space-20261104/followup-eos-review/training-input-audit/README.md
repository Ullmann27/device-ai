# Trainingsinputs und EOS-Ziele unabhängig geprüft

**Prüfstand:** PR #492, `30cd3a89810ed14fa84eca95236d3177e016118d`.
**Status:** `RESEARCH_SHADOW / MODEL_FREE_PASS`.
Es wurden keine Modellgewichte geladen, keine Modell-/Trainingsprozesse
gestartet und keine Entwicklungs- oder Holdouttexte gelesen. Die Ursache
der beobachteten EOS-Entscheidung bleibt durch diese Datenprüfung allein
**ungeklärt**.

## Tatsächlich nachgezählt

[audit_training_inputs.py](audit_training_inputs.py) rekonstruiert unabhängig
in Python die Bereinigung, Segmentierung, Byte-BPE, Java-Zufallsfolge und
Next-Token-Ziele. Der Leser importiert weder den Trainingsdriver noch
Modellcode. Abnahme sind die exakten historischen SHA-256-Werte des Korpus,
der Indexreihenfolge und der vollständigen Tokensequenz sowie alle 16
kumulativen Zielzahlen in **allen vier** veröffentlichten Trainingslogs.
Diese Gegenprüfung besteht: [target-replay.json](target-replay.json).

| Eigenschaft | Ergebnis |
|---|---:|
| Ursprüngliche TRAIN-Dokumente | 77 |
| Bereinigte Segmente / unterschiedliche Segmenttexte | 2.520 / 2.511 |
| Korpusbytes einschließlich Zeilentrenner | 429.290 |
| Ausgewählte Segmente / unterschiedliche Texte | 64 / 64 |
| Inhaltstargets | **3.229** |
| EOS-Targets | **26** |
| Alle Targets | **3.255** |
| EOS-Anteil | **0,798771 %** |
| BOS-Targets | **0** |
| Ausgewählte vollständige Segmente mit EOS | 26 |
| Lange ausgewählte Segmente, auf 63 Inhaltstokens gekürzt, ohne EOS | 38 |
| Ausgelassene spätere Inhaltstokens dieser 38 Segmente | 2.485 |
| Längster tatsächlicher Trainingseingang einschließlich BOS | 63 Positionen |

Der Tokenizer hat 512 IDs: BOS 510, EOS 511. Alle 2.520 Segmente decodieren
beim unabhängigen BPE-Read zurück in ihre ursprünglichen UTF-8-Bytes.
Schritte 7–9 enthalten jeweils 252 Inhaltstargets und **kein EOS**. Der letzte
Schritt enthält 106 Inhaltstargets und drei EOS, zusammen 109. Die vollständige
Batchzusammensetzung steht im JSON. Eine pauschale EOS-only-/BOS-Labelkorruption
ist damit für diese konkreten Läufe widerlegt. Ein kleiner EOS-Anteil beweist
umgekehrt nicht, dass diese Targets keinen Einfluss auf die Entscheidung haben.

## Quellenbefund

- [LmWeightExperiment.kt](../../LmWeightExperiment.kt), `train`, Zeilen 95–118:
  `BOS + erste 63 Inhaltstokens`; EOS wird nur angehängt, wenn das ganze
  Segment höchstens 63 Inhaltstokens hat. Die ausgewählten EOS-Endpositionen
  liegen nach 5 bis 60 Inhaltstokens. Ein vollständiges Korpussegment muss
  kein semantisch vollständiger Satz sein.
- [LmModel.kt](../../../../app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt),
  `LmTrainer.step`, Zeilen 220–245: Eingabe ist die Folge ohne letztes Token,
  Ziel dieselbe Folge ohne BOS. Kein Off-by-one-Fehler ist in diesem Pfad
  belegt. Die globale Zielzahl des Batches wird als Normalisierer übergeben.
- [LmTensor.kt](../../../../app/src/main/java/at/heinz/deviceai/learning/lm/LmTensor.kt),
  `Tape.crossEntropy`, Zeilen 299–329: Jede Sequenz trägt NLL und Gradienten
  geteilt durch diese globale Zahl bei. Getrennte Worker-Puffer werden einmal
  vor dem Optimiererschritt summiert. Keine zusätzliche Batchdivision oder
  doppelte Gradientennormalisierung ist in diesem geprüften Pfad belegt.
- `LmEval.segmentBits` in `LmModel.kt`, Zeilen 252–267, maskiert das letzte
  EOS-Ziel ausdrücklich mit `-1`: Entwicklungs-BPB bewertet Inhalt,
  nicht die Entscheidung über das Ausgabeende. Minimal besserer Inhalts-BPB
  und schlechtere EOS-Ausgabe können daher zusammen auftreten.

Die Losswerte verschiedener Trainingsschritte beziehen sich auf unterschiedliche
Batches. Der Rückgang vom ersten zum letzten Logeintrag ist daher kein
kontrollierter Vorher-Nachher-Vergleich. Eine neue numerische Autodiff- oder
Optimizer-Prüfung wurde in dieser Teilaufgabe **nicht** ausgeführt.

## Optimierer und kleine Kontrollen

`LmAdamW` in `LmModel.kt`, Zeilen 161–196, verwendet Beta1 0,9, Beta2 0,98,
Epsilon 1e-8 und globales Gradient-Clipping. Weight Decay gilt nur für
Gewichtsmatrizen mit Blattnamen beginnend mit `w`; Token-/Positions-Embeddings,
Biases und Normgewinne sind ausgenommen. Das EOS-Embedding erhält keinen
**direkten** Weight-Decay-Abzug. Indirekte Effekte anderer Gewichte bleiben offen.

Die Implementierung verwendet `lr * sqrt(1-beta2^t) / (1-beta1^t)` als
Schrittfaktor und danach `m / (sqrt(v) + eps)`. Ein Vergleich mit einer anderen
Adam-Implementierung muss auch Epsilonposition und Decay-Ausschlüsse beachten.
Diese vorhandene Variante wird hier nicht als belegte EOS-Ursache ausgegeben.

Jeder Fortsetzungsaufruf erzeugt einen neuen `LmTrainer` mit leeren Adam-Momenten.
Das ist eine Gewichtsfortsetzung mit neuem Optimiererzustand; es ist keine
bitgenaue Wiederaufnahme der früheren Adam-Momente und des früheren Lernratenschritts.
Beide kleinen Kontrollen verweisen auf denselben ausgegebenen Checkpointhash
`7e6d136d6b31b8f7e9839b344be39a38d0f00146286b315a04ecfb4c4997b91e`.
Es gibt keinen Beleg, dass allein die größere Parameterzahl den EOS-Abbruch
verursacht. Gleiche Ausgangsfunktion garantiert keine gleich großen effektiven
Updates nach einer Verbreiterung.

## Kleinste anschließende Messung

PR #508 besitzt am gelesenen Stand
`def5acf9cbba82ee51a53cc06fecc52e9cd0324d` bereits einen gesonderten
EOS-Diagnoseharness. Keine parallele LR-Tuningreihe anlegen. An seinen
vorgesehenen Messpunkten 0, 1, 4 und 16 genügen folgende Ergänzungen:

1. TRAIN-Zielzahl 3.255, EOS-Zielzahl **26** und vollständige
   Token-Schedule-SHA gegen diesen unabhängigen Beleg prüfen.
2. Inhalt-NLL und terminale EOS-NLL auf **demselben festen Referenzsatz**
   getrennt berichten; keine Losswerte unterschiedlicher Batches vergleichen.
3. EOS-Logit minus bester zulässiger Inhalts-Logit und tatsächlich erzeugte
   Tokens bei identischer Promptposition vor/nach dem Update vergleichen.
   Ein Vorzeichenwechsel lokalisiert die Entscheidung; die Losskurve allein nicht.
4. Kleine Kontrolle und verbreiterte Variante getrennt auswerten. Die bereits
   vorregistrierten Interventionen prüfen die Updategrößen-Hypothese.

Die Kapazitätsprompt-Eingabe hat laut gebundener Benchmarkquelle 65 Positionen
einschließlich BOS, der längste hier tatsächlich trainierte Eingang 63.
Bei einem rekurrenten Modell ist das **kein belegter Kontextfehler**, sondern
eine getrennt zu messende Bedingung. Kein EOS-Verbot, kein Mindestlängentrick
und keine Lockerung des ursprünglichen 32-Token-Gates.

Abnahme: identische Datennachweise, getrennte Verlust-/Entscheidungsgrößen und
vollständiger Bericht auch bei negativem Ergebnis. Der Modellvergleich bleibt
hier `NOT_RUN`; keine neue Hyperparameterwahl anhand eines Holdouts.

## Reproduktion und Grenzen

Aus dem Repository-Stamm, mit einem vorhandenen geprüften Eingabeordner und
einem neuen Ausgabeort:

    python3 experimental/weight-space-20261104/followup-eos-review/training-input-audit/audit_training_inputs.py --repo . --inputs /path/to/verified-inputs --output /path/to/new-target-replay.json

Benötigt werden nur `lm1-tokenizer.lmbp`, `original-train-docs/paths.tsv`
und die 77 gebundenen Markdown-Dateien. **Kein Checkpoint ist erforderlich.**
Das Programm verweigert das Überschreiben vorhandener Evidenz.

| Bindung | SHA-256 |
|---|---|
| Bereinigter Korpus | `27e67455e23dcc410855b2fd0f0292f9a00556d72d62aefb018e9eab4be034c9` |
| Indexreihenfolge | `346e93c510dafe5e12ee6a0e54ae94a0f0d099e7c0fc8c6f8973e5ff9a6b5066` |
| Vollständige tatsächliche Tokensequenz | `8e0d6ef53de6c84e48c74ecc4dbd4b70e25632caadc624f31ad0ac4aad681fee` |
| Tokenizer | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |

Der Python-Port ist nur für diesen gebundenen Datenstand bestätigt; er ersetzt
keinen allgemein freigegebenen Kotlin-Tokenizer oder Korpusparser.
Quellhashes, alle Dokumenthashes und alle vier Trainingsloghashes stehen im
JSON-Beleg. Betroffen sind EXP-05 und EXP-12; es gibt keinen neuen App-Anschluss
und keine Änderung von Runtime, Optimierer, Gates oder historischen Ergebnissen.
