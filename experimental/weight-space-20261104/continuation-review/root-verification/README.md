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
