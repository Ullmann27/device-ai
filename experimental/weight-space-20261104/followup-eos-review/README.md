# Unabhängige EOS-Nachprüfung ohne neuen Modelllauf

**Status: RESEARCH_SHADOW / VERIFIED_ARCHIVED_LOG_FINDINGS.**
Experimentbezug: EXP-04, EXP-05, EXP-07 und EXP-12. Geprüfter Ausgangsstand:
PR #492, `30cd3a89810ed14fa84eca95236d3177e016118d`.

## Belastbarer Befund

Der sofortige Ausgabeabbruch nach der Fortsetzung ist in den archivierten
Kapazitätslogs belegt. Er tritt aber nicht erst in einem vergrößerten Modell
auf. Auch die unvergrößerte kleine Kontrolle kippt nach demselben kurzen Training
am Kapazitätsprompt auf EOS. Außerdem war das Original bereits vor dieser Runde
bei vier der sechs Instruktionsproben leer. Die beiden vorher nichtleeren
Antworten waren ebenfalls falsch.

Der neue [Prüfer](analyze_archived_eos.py) liest ausschließlich vorhandene
Kapazitäts-/Instruktionslogs samt Receipts und fünf unveränderte Quellen.
Er bindet die Logs an ihre Prüfsummen, Modell-/Tokenizer-/JAR-/Driveridentitäten,
gleiche Prompts und vollständige Trial-IDs. Die tatsächliche Ausführung prüfte
**16 Log-/Receipt-Paare und insgesamt 37 Quelldateien**, ohne Modellprozess,
Trainingsprozess, Checkpointbinärdatei oder Holdoutdatei. Das vollständige
[Ergebnis](archived-eos-findings.json) enthält jeden Kandidaten, die
Instruktionsübergänge und alle gelesenen Dateihashes.

| Vergleich, in beiden Profilen | Kapazitätsprompt vor Training | Kapazitätsprompt danach | Instruktionsproben vorher | Instruktionsproben danach |
|---|---|---|---|---|
| Original → kleine Kontrolle | 8/8 vollständige Aufrufe | 8/8 sofort EOS, 0 Tokens | 4/6 sofort EOS; 2/6 nichtleer, falsch | 6/6 sofort EOS |
| Große Initialversion → große Fortsetzung | 8/8 vollständige Aufrufe | 8/8 sofort EOS, 0 Tokens | 4/6 sofort EOS; 2/6 nichtleer, falsch | 6/6 sofort EOS |

Die acht Kapazitätsaufrufe je Kandidat sind ein Aufwärmpaar und drei gemessene
Paare, jeweils für ein beziehungsweise 32 neue Tokens. Es sind Wiederholungen
**desselben festen Prompts**, keine acht unterschiedlichen Sprachaufgaben.
Die beiden kleinen Kontrollen besitzen denselben trainierten Checkpointhash;
sie sind keine unabhängigen Trainingsseeds.

| Index | Instruktionsprobe | Vor Training | Nach Training |
|---:|---|---|---|
| 0 | 17 × 24, nur Zahl | EOS/0 | EOS/0 |
| 1 | 2 + 3, nur Zahl | EOS/0 | EOS/0 |
| 2 | Nur das Wort „Hallo“ | 32 falsche Tokens | EOS/0 |
| 3 | Nur das Wort „fertig“ | 32 falsche Tokens | EOS/0 |
| 4 | Zahl nach 9 | EOS/0 | EOS/0 |
| 5 | „Haus“ kleinschreiben | EOS/0 | EOS/0 |

Damit ist eine ausschließlich durch Modellvergrößerung verursachte Erklärung
unzureichend. Ebenso wäre die Aussage falsch, das Original habe vor der
Fortsetzung bereits korrekt Instruktionen befolgt. Die Beobachtung beweist
keine universelle EOS-Ausgabe bei sämtlichen möglichen Prompts.

## Quellprüfung und offene Ursache

`LmRuntime.kt` wählt standardmäßig deterministisch (`temperature = 0`, `topK = 1`),
maskiert BOS und beendet die Ausgabe ausdrücklich bei gewähltem EOS
(Zeilen 22 und 60–62). `LmModel.kt:LmEval.sample` wählt dafür das größte
verbleibende Wahrscheinlichkeitsgewicht (ab Zeile 285). Das berichtete
`stop=EOS, newTokens=0` entspricht diesem vorgesehenen Kontrollfluss. Die
Kapazitätslogs melden weder Promptkürzung noch einen Zeit-/Kontextabbruch.

Der tokenweise minGRU-Pfad verwendet dieselben Matrizen und dieselben
Rekurrenz-, Normierungs- und Aktivierungsformeln wie der volle Vorwärtslauf in
`LmModel.kt` und `LmTensor.kt`. Der vorhandene
`LmRuntimeTest.stepwiseMinGruGivesBitIdenticalLogitsToTheFullForwardPass`
vergleicht ausdrücklich auch ein kleines trainiertes Modell. Dieser Test wurde
in dieser Nachprüfung **nur als Quelle gelesen**, nicht erneut ausgeführt; er
belegt nicht selbst die Parität der konkreten großen fortgesetzten Checkpoints.

Das Training konstruiert `BOS + höchstens 63 Inhaltstokens` und fügt EOS nur für
vollständig enthaltene Segmente hinzu (`LmWeightExperiment.kt`, Zeilen 100–101).
Die Inhalts-BPB maskiert dagegen den EOS-Zielwert (`LmModel.kt`, Zeile 262).
Deshalb kann eine minimale Verbesserung der Inhalts-BPB gleichzeitig mit einer
verschlechterten freien Generierung auftreten. **Daraus folgt noch kein
bewiesener Fehler in der Zielkonstruktion oder im Gradienten.**

Die bisherigen Rohlogs enthalten weder EOS-Wahrscheinlichkeiten noch den
Abstand zwischen EOS und dem besten Inhaltstoken. Es lässt sich daraus nicht
bestimmen, ob eine schon kleine Greedy-Entscheidungsgrenze umkippt, ob EOS an
offenen Präfixen global bevorzugt wird oder welche Parameterupdates dafür
verantwortlich sind. Ein konkreter Runtime-/Optimizerfehler wurde in dieser
Quellprüfung nicht nachgewiesen. Deshalb wurde keine spekulative Reparatur
eingebaut.

## Anschluss an die bereits vorhandene Diagnose in PR #508

Unabhängig gelesen wurde PR #508 am festen Kopf
`def5acf9cbba82ee51a53cc06fecc52e9cd0324d`, mit Basis auf dem oben genannten
#492-Ergebnisstand:

- [LmRecoveryDiagnostic.kt](https://github.com/Ullmann27/device-ai/blob/def5acf9cbba82ee51a53cc06fecc52e9cd0324d/experimental/lm1-training-recovery-20261104/LmRecoveryDiagnostic.kt),
  Git-Blob `8644cf774168403a52158e0790744bce94ec8e2e`.
- [DIAGNOSTIC_PROTOCOL.md](https://github.com/Ullmann27/device-ai/blob/def5acf9cbba82ee51a53cc06fecc52e9cd0324d/experimental/lm1-training-recovery-20261104/DIAGNOSTIC_PROTOCOL.md),
  Git-Blob `ee4ba5439a7d59beaa38fc2fa7d4a49c82afcad7`.

Diese neue Diagnose deckt die fehlende Messung bereits ab. Sie protokolliert
bei Schritt 0, 1, 4 und 16 die EOS-Wahrscheinlichkeit und EOS-minus-bestes-
Inhaltstoken-Marge am Kapazitätsprompt, an drei vollständigen Entwicklungstexten
und an deren echten halben Tokenpräfixen. Inhaltsverlust und terminaler
EOS-Verlust werden getrennt; der erste Minibatch bleibt als Verlustreferenz
fest. Der initiale Kapazitätstest wird tatsächlich über einen eigenen
Java-Prozess mit `-Xmx1g` und 120 Sekunden Frist gestartet, nicht in der
Trainings-JVM. Nach Training verlangt das Protokoll erneut den unveränderten
separaten Kapazitätstest. Eine fehlgeschlagene Textgenerierung darf nicht durch
Unterdrücken von EOS oder erzwungene Mindestlänge als bestanden erscheinen.

Die Vierarm-Ablation vergleicht kleine Kontrolle, breite Standardfortsetzung,
ein Achtel des finalen Norm-Parameterupdates und ein Achtel der globalen
Lernrate. Sie ist geeignet, die Hypothese einer zu großen effektiven
Updategröße nach Verbreiterung einzugrenzen. Ein etwaiger Erfolg dieser
Intervention erklärt jedoch nicht automatisch das EOS-Problem der kleinen
Kontrolle. Die globale Lernrate verändert außerdem den effektiven Weight Decay;
die Norm-Intervention verändert Parameterdeltas, behält aber die vorhandenen
Adam-Momente. Diese Einschränkungen sind im fremden Protokoll korrekt genannt.

**Aussagegrenze des Quellreviews:** Die Stepper-/Full-Forward-Parität ist in
diesem Diagnosecode vor Training geprüft; die späteren `EOS_PROBE`-Messungen
nutzen den vollen Vorwärtslauf. Falls aus den finalen Margen eine Runtime-Ursache
abgeleitet werden soll, gehört ein schreibgeschützter Vergleich des konkreten
finalen Checkpoints zwischen beiden Pfaden dazu. Das ist eine fehlende
diagnostische Abgrenzung, kein bereits nachgewiesener Implementierungsfehler.

Die PR-#508-Ablationen wurden hier nicht dupliziert und nicht ausgeführt.
Dieser Review genehmigt weder deren Orchestrierung noch Intent-Studie;
deren Gates und Registrierung werden getrennt unabhängig geprüft.

## Reproduktion, Grenzen und Rückfall

Aus dem Repository-Stamm:

```bash
python3 experimental/weight-space-20261104/followup-eos-review/analyze_archived_eos.py
```

Der Prüfer verwendet nur die Python-Standardbibliothek und schreibt selbst
keine Dateien. Bei abweichenden Identitäten oder unvollständigen Rohzeilen
bricht er ab. Seine Ausgabe ist ein neuer historischer Diagnosebericht, keine
erneute Modellmessung und keine Wiederholung der 75 Gate-Fälle.

Bestehende Schnittstellen bleiben unverändert. Alle neuen Dateien liegen in
diesem isolierten Unterordner. Runtime, ursprüngliche Gates, Checkpoints,
Kandidatenwahl und archivierte Ergebnisse werden nicht geändert. Rollback ist
das Nichtübernehmen dieses zusätzlichen Reviewordners; bestehende Forschung
bleibt vollständig erhalten. Android-/APK-/Modellqualitätsabnahme bleibt
außerhalb dieses Nachweises.
