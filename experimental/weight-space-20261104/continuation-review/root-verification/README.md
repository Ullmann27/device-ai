# Eigene Wiederholung durch Root

`reproduce.py` wiederholt die unabhängigen, quellengebundenen Prüfungen an
exakt gepinnten Eingaben. Ergebnis: **ROOT_REPEAT_PASS**, 31/31 neue
Studienwahlfälle und 6/6 Registrierungsfälle. Original und alter
Kapazitätspatch reproduzieren mit derselben 31er-Probe jeweils 28 erwartete
Fehlverhalten bei drei bestehenden Positivkontrollen.

Zusätzlich bestehen bytegleiche Wiederholung beider Roh-/Quellenaudits,
Git-Identitätsprüfung der Originale, reproduzierbarer Patchgenerator,
unabhängiger AST-/Textvergleich, tatsächliche Anwendung beider Patchvarianten
auf frische temporäre Gitbäume sowie zwei CLI-Parserkontrollen.

```sh
python -B experimental/weight-space-20261104/continuation-review/root-verification/reproduce.py
```

Erwarteter Exitcode: 0. Die Einzelberichte enthalten die tatsächlichen
Gegenprobenergebnisse. Die erste und zweite 31er-Probe liefern absichtlich
Exitcode 1; der Verifikator verlangt genau dieses RED-Ergebnis.

Keine Modellprozesse, keine echten Holdoutdateien und keine Migration
historischer Registrierungen oder Receipts. Die alte 165er-Suite wird nicht
erneut ausgeführt. [verification.json](verification.json) bindet Probe-,
Patch- und Quellhashes sowie die tatsächlichen Resultate.

[upstream-source-freshness.json](upstream-source-freshness.json) ist eine
separate spätere Git-Identitätskontrolle bei `59891cfa…`. Sie liest noch keine
neuen Ergebnisinhalte. Der ausführliche Audit und die Wiederholungen bleiben
am zuvor vollständig geprüften Commit `9ceffc26…` verankert.

## Abgeschlossener Nachtrag zum späteren Ergebniscommit

Die drei neuen Auditoren wurden von Root zusätzlich selbst ausgeführt.
Ihre Ergebnisse stimmen bytegleich mit den jeweiligen Helferdateien überein:

| Eigene wiederholte Ausgabe | Gegenstand |
|---|---|
| `final-results-59891.json` | 192 Finalurteile, separat 256 TRAIN-fit-Urteile, sechs Receipts |
| `independent-final-59891.json` | Eigenständige 192-Fall-Nachrechnung ohne fremden Parser oder Fremdaudit als Eingabe |
| `publication-audit-59891.json` | Publikations-/Quellenbindungen, 514 Textkonsistenzkontrollen |

Die tatsächlichen vier Checkpoints, vier JARs und der separate Tokenizer
wurden außerdem direkt geprüft; [BINARY_INTEGRITY.md](BINARY_INTEGRITY.md)
beschreibt diesen zusätzlichen Nachweis einschließlich reproduzierbarem Aufruf.
Der unabhängige Helfer hat den binären Reader zusätzlich als Quelltext geprüft.

```sh
python -B experimental/weight-space-20261104/continuation-review/root-verification/verify_final_bundle.py
```

Dieser letzte Aufruf prüft die schon erzeugten, gepinnten Ergebnisse gegeneinander.
Er führt keine Modelltests oder früheren Suiten erneut aus. Erwartet wird:
`REPAIR_AND_EVIDENCE_INTEGRITY_PASS__MODEL_QUALITY_AND_CAPACITY_FAIL`.

Die Reparatur besteht ihre 37 neuen Fälle. Beide unabhängig nachgerechneten
Finalberichte und die tatsächlichen Binärdateien beziehen sich auf dieselben
ausgewählten Checkpoints. Die Modellqualität und die vollständige
32-Token-Kapazität sind dennoch verfehlt. Der veröffentlichte Holdout ist
verbraucht; Auswahl und Schwellen wurden durch diese Nachprüfung nicht geändert.
