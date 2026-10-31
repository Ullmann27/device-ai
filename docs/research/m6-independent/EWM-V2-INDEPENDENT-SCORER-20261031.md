# M6 – unabhängige Auswertung des EWM-v2-Holdouts (31.10.2026)

**Status: Auswerter lokal 15/15 getestet. Tatsächlicher EWM-Lauf auf den 240 eingefrorenen Fällen: NOT_RUN.**

Bestehende Holdoutdaten, vorregistrierte Kriterien und EWM-Produktivquellen bleiben unverändert. Dieser Nachtrag liefert ausschließlich einen strengen Auswerter für spätere externe Laufbelege. Kein Android-/Gradle-Eingriff; Jules arbeitet getrennt an APK und Update-Button.

## Unabhängiger Laufvertrag

Ein unabhängiger EWM-Runner führt den vorher eingefrorenen Kandidaten genau einmal auf 240 Testpaaren aus. Soll-Labels dürfen nicht als Eingabe an den Kandidaten fließen. Die JSONL-Datei enthält für jede ID M6X-001 bis M6X-240: id, candidate_sha (volle SHA-1-Commit-ID), expected_label, predicted_label, reasons, before_current, after_current und unrelated_fact_kept.

Die drei Zustandsflags müssen aus einer **unabhängigen Inspektion des tatsächlichen EWM-Ersetzungsgraphen** stammen. Modellselbstaussagen ohne Zustandsnachweis sind kein zulässiges Testergebnis. Das erwartete Label wird erst beim Auswerten zugeordnet, nicht beim Inferenzaufruf.

## Aufrufe

    node scripts/continuum-ewm-v2-holdout-integrity.mjs
    node --test scripts/continuum-ewm-v2-holdout-score.test.mjs
    node scripts/continuum-ewm-v2-holdout-score.mjs --receipts ./m6-real-receipts.jsonl --candidate-sha VOLLSTAENDIGE_40_ZEICHEN_COMMIT_SHA

Der Auswerter prüft den unveränderten Git-Blob 622370c291ead9ab11ec8917c992037a2fcf9e17, exakt 240 Fälle und alle IDs, dass Erwartungswerte aus den eingefrorenen Daten kommen, sowie konsistente Commit-SHA, Erklärungstext und drei Wahrheitswerte pro Zeile.

## Ergebnisstatus und Exitcodes

- **2 / NOT_ACCEPTED**: fehlerhafte, doppelte, unvollständige, umgelabelte oder gemischte Belege; keine Metrik-Ausnahme.
- **1 / FAIL**: echte formal vollständige Vorhersagen, aber Gesamt-Precision positiver Ersetzung < 0,95; undefinierte Precision wegen null positiver Vorhersagen; oder mindestens ein verlorener unbeteiligter Kontrollfakt.
- **0 / PASS**: vollständige Daten, positive Ersetzungs-Precision >= 0,95, null verlorene Kontrollfakten. Bericht enthält Konfusionsmatrix, klassenweise Precision/Recall/F1, Makro-F1 und positive Precision/Recall.

**Grenze:** Die 240 Fälle stammen aus Varianten von nur 24 Basiskonstruktionen. Ein PASS allein beweist weder breite Generalisierung noch eine funktionierende Fold7-APK. Vor einem echten unabhängigen Lauf ist die Modellerfolgsquote unbekannt.

**Änderungssicherheit:** Ergänzung direkt in bestehender Draft-PR #258, kein neuer PR, kein direkter Main-Push, keine Berührung von Jules-Dateien.