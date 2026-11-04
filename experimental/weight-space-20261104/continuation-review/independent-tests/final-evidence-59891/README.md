# Unabhängige Nachrechnung des veröffentlichten Finaltests

## Ergebnis und Umfang

Geprüfter Ergebniscommit: `59891cfad6dd015f47c187cc688ea94b0026ad7d`.
Vorheriger Auswahl-/Freeze-Commit: `9ceffc26a2159c8dc726c1f13d2882119159c130`.
In den historischen Ausführungsbelegen genannter V3-Quellcommit:
`be4e49600fed1db4c697230bb8e42c150dbfbdaf`.

**Die veröffentlichten Textbelege stimmen überein. Das vorab vereinbarte
Modellerfolgskriterium ist verfehlt.** Der eigenständige Checker hat alle
192 `CASE`-Urteile der vier Arme dekodiert und nachgerechnet. Seine 105
Konsistenzkontrollen bestehen; diese Zahl bezeichnet Belegkontrollen, keine
weiteren Modelltests. Exitcode 0 bestätigt die Konsistenz des negativen Ergebnisses.

| Arm | Gemeldete Parameter | Vorher gewählter Schritt | Exakt richtig | Richtig je Klasse 0/1/2/3 | Fehlendes EOS |
|---|---:|---:|---:|---|---:|
| S0 | 132.160 | 0 | 0/48 | 0 / 0 / 0 / 0 | 48/48 |
| W0 | 6.562.304 | 0 | 0/48 | 0 / 0 / 0 / 0 | 48/48 |
| S1 | 132.160 | 512 | 31/48 | 7 / 12 / 10 / 2 | 0/48 |
| W1 | 6.562.304 | 128 | 25/48 | 6 / 9 / 7 / 3 | 0/48 |

Jeder Arm enthält dieselben 48 eindeutigen IDs und protokollierten Sollklassen
in derselben Reihenfolge, zwölf je Klasse. Erfolg verlangt unverändert exakt
die richtige Ziffer als Text, genau ein Inhaltstoken und Stop-Grund `EOS`.
Jedes protokollierte Richtig/Falsch-Urteil stimmt mit dieser Nachrechnung
überein. S1 und W1 haben jeweils auf allen 48 Fällen das gültige
Ziffer-plus-EOS-Format; beide untrainierten Kontrollen haben es auf keinem Fall.
Die Kontrollen erzeugen über alle 48 Fälle denselben Text, dieselbe Tokenzahl
und denselben Stop-Grund.

Die vollständigen Verwechslungsmatrizen, NLL-Mittelwerte, EOS-Mittelwerte,
Token- und Stoppzählungen stehen in `audit.json`. Ein W0-JVM-Hinweis auf eine
gesperrte `hsperfdata`-Datei bleibt als Diagnosezeile erhalten und wird nicht
als zusätzlicher Testfall gezählt.

## Unveränderte Abnahme

Der Checker liest das bereits vor den Ergebnissen vorhandene V3-Protokoll
und vergleicht es byteweise mit dem Ergebniscommit. Er bindet die strenge
Wertung außerdem an den Kotlin-Harness und die numerischen Kriterien an den
historischen Python-Controller. Die relevanten Quelltexte sind gegenüber dem
genannten Ausführungscommit unverändert.

| Vorab festgelegte W1-Bedingung | Nachgerechnet | Erfüllt |
|---|---|---|
| Mindestens 36/48 exakt richtig | 25/48 | Nein |
| Mindestens 8/12 in jeder Klasse | 6 / 9 / 7 / 3 | Nein; Klassen 0, 2 und 3 |
| Mindestens acht mehr als S0 | +25 | Ja |
| Mindestens acht mehr als W0 | +25 | Ja |
| Zusätzlicher Größenvorteil: mindestens vier mehr als S1 | −6 | Nein |

Die beiden veröffentlichten Summary-Flags
`limitedBenchmarkLearningCriterionPassed=false` und
`widerSelectedCheckpointHasFourCaseAdvantage=false` sind damit korrekt.
Die ursprünglichen allgemeinen Kapazitätsanforderungen bleiben bestehen;
die Ein-Ziffer-Aufgabe ersetzt insbesondere keine 32-Token-Anforderung.
Die allgemeine Kapazitätsprüfung wird in diesem engen Finaltest-Nachtrag
nicht erneut ausgeführt oder aus den Intent-Antworten abgeleitet.

Der gepaarte Vergleich derselben IDs ergibt **20 beide richtig, 11 nur Small
richtig, 5 nur Wide richtig und 12 beide falsch**. Die jeweiligen IDs sind im
JSON enthalten. Der Unterschied 31 gegen 25 ist deskriptiv: Die vorab gewählten
Checkpoints haben unterschiedliche Trainingspräfixe, 512 beziehungsweise
128 Updates. Daraus folgt kein isolierter kausaler Effekt der Parameterzahl
und keine Signifikanzbehauptung. Die im Protokoll genannte Abhängigkeit von
Vorlagenfamilien schließt die Behandlung als 48 unabhängige Ziehungen aus.

## Bindung an die vor dem Test veröffentlichte Auswahl

Studienwahl, Freeze und Vorab-Verifikation sind in beiden gepinnten
Veröffentlichungsständen bytegleich. Die Studienwahl nennt V3 und ihr
vollständiges `selectedFreezePayload` entspricht dem Freeze. Dieser hat den
SHA256 `6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4`.
Die spätere Summary bindet genau diesen Hash.

| Arm | Checkpoint-SHA256 im früheren Freeze und späteren TEST_INPUT |
|---|---|
| S0 | `70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435` |
| W0 | `af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d` |
| S1 | `90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f` |
| W1 | `0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c` |

Alle vier `TEST_INPUT`-Datensätze nennen außerdem denselben, im Freeze
gebundenen Testhash. Jeder veröffentlichte Prozessbeleg bindet seinen
tatsächlichen Loghash, den Quellcommit, die Registrierung und den gemeldeten
JAR-Hash. Er nennt den eingefrorenen Checkpointpfad, Exitcode 0, keinen Timeout,
die unveränderte Heapgrenze `-Xmx3g` und 900 Sekunden Prozessfrist; die jeweils
protokollierte Laufzeit liegt darunter. Dies ist ein Abgleich der Belegangaben,
keine neue Messung der tatsächlichen Ressourcennutzung.

## Wiederholung nach Integration der Reparatur

Der bereits unabhängig geprüfte reparierte V3-Runner ist auf
`67da16654388d322f3735a67f3fbf1d42635ffccf6ac62668ff46b80b47f1942` gebunden.
Eine reine AST-Prüfung bestätigt: Seine top-level Sperre für vorhandene
`test-*.tsv` **oder** `test-*.receipt.json` liegt vor dem ersten Prozessaufruf.
Die vorhandenen unabhängigen Proben
`later_test_log_already_exists` und `later_test_receipt_already_exists`
sind an genau diese Quelle gebunden. Beide wurden bereits an der echten
Methode mit ausschließlich abgefangener Prozessgrenze ausgeführt: Ablehnung
`FINAL_TEST_ALREADY_STARTED`, null Prozessgrenzenaufrufe, bestehende Belege
unverändert. Sie wurden für diesen Nachtrag nicht erneut ausgeführt.

Der Ergebniscommit enthält vier passende Logdateien und vier passende
Quittungen. **Sind diese Artefakte im aktiven Studien-Logverzeichnis vorhanden,
sperrt der reparierte Runner einen neuen Finaltest vor dem ersten Arm.**
Vorgeschaltete Registrierungs- oder Freigabeprüfungen können historische
Belege schon vorher zurückweisen. Dieser Befund behauptet weder einen neu
ausgeführten Replay-Versuch noch, dass die historischen Finalprozesse bereits
die spätere Reparatur benutzt hätten. Historische Quittungen werden nicht
auf die neue Quelle umgeschrieben.

## Reproduktion und Grenzen

Vom Repository-Stamm aus:

```bash
python3 experimental/weight-space-20261104/continuation-review/independent-tests/final-evidence-59891/check_final_evidence.py --output experimental/weight-space-20261104/continuation-review/independent-tests/final-evidence-59891/audit.json
```

Der Checker verwendet ausschließlich die Python-Standardbibliothek und
`git show` für eine explizite Liste erlaubter Textpfade. Er importiert keinen
Studienrunner und keinen fremden Audit-Parser. Sein JSON enthält eigene
Prüfergebnisse, Quellen-/Dateihashes sowie den Hash des Checkers. Die 15
bereits eingefrorenen Dateien des übergeordneten Testordners werden gegen
ihre bisherigen Hashes geprüft und bleiben unverändert.

- Keine Modell-, Trainings-, JVM- oder Datensatzprozesse wurden gestartet.
- Keine echten versiegelten Testdateien, Datensatzgeneratoren, Checkpoints,
  Tokenizer oder JAR-Binärdateien wurden geöffnet. Die Sollklassen sind somit
  zwischen den veröffentlichten Logs abgeglichen, nicht erneut gegen die
  Originaltestdatei validiert. Genannte Binäridentitäten wurden als
  Belegangaben verglichen, nicht von diesem Checker nachgehasht.
- `final-case-audit.json`, dessen fremder Parser und die nachträglichen
  `train-fit-*`-Daten sind keine Eingaben dieser eigenständigen Nachrechnung.
- Die Prüfung früherer Git-Objekte ist keine kryptografische Zeitbeglaubigung
  der Veröffentlichung und kein Ausschluss unprotokollierter Vorgänge.
- Aus dem nun verbrauchten Finaltest entsteht keine neue Auswahl-, Trainings-
  oder Abnahmeregel. Die Bestätigungsrunde endet mit dem negativen Ergebnis;
  eine neue unabhängige Bestätigung braucht den zuvor unabhängig festgelegten
  neuen Bestätigungssatz. Es folgt keine Modell-Promotion.

### Gepinnte Quellen

Im Ergebniscommit: `docs/evidence/lm1-training-recovery-20261104/study-v3/`
mit `logs/test-{S0,W0,S1,W1}.tsv`, den vier entsprechenden Quittungen und
`intent-summary.json`. Die drei Vorab-Dokumente liegen unter
`docs/evidence/lm1-training-recovery-20261104/final-selection/` und werden
zusätzlich im früheren Auswahlcommit gelesen. Die Quellen des Vertrags und
Scorers liegen unter `experimental/lm1-training-recovery-20261104/suffix-study/`.
Die vollständige Allowlist und alle verwendeten Hashes stehen im Checker
beziehungsweise unter `inputs` in `audit.json`.
