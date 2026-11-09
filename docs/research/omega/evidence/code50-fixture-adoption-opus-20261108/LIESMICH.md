# Übernahme der Storage-Fixture-Korrektur (500 → 1500 ms) — 08.11.2026

**Status:** Test-Fixture-Änderung in `.github/continuum/storage-fabric.test.mjs` Zeile 42. Kein Produktionscode, kein Android-Bezug.

## Entscheidung

Agent 1 („Teststabilität“) hat vor dem Messen eine Entscheidungsregel festgelegt. Danach gilt: Die Korrektur wird übernommen, wenn alle vier Bedingungen erfüllt sind.

| Bedingung | Ergebnis |
|---|---|
| (a) B ohne Fehler in den neuen Lastrunden | erfüllt, 0/20 |
| (b) gepooltes einseitiges Fisher-p ≤ 0,10 | erfüllt, A 7/31 gegen B 0/31, p = 0,00535 |
| (c) Mutationsnachweis: Fail-Closed-Prüfung neutralisiert macht die drei pinnenden Tests in A und B rot | erfüllt (`mutation-result.txt`) |
| (d) Audit weiterer kurzer Timeouts dokumentiert | erfüllt, nur Liste, keine weitere Änderung |

Die Fixture-Änderung weicht keine Assertion auf. Die Fail-Closed-Logik in `storage-fabric-catalog.mjs:103` und der eigene 50-ms-Timeouttest bleiben unverändert.

## Grenzen

- Die Runden sind nicht vollständig unabhängig (gleicher Host, gleiche Brennerlast). Die Abwechslung A/B mildert Drift, ersetzt aber keine Randomisierung.
- Gemessen wurde auf einem Host mit 2 CPU-Kernen, nicht auf einem GitHub- oder Render-Runner.
- B hat bisher 0/31 Fehler. Das heißt „bisher nicht fehlerhaft“, nicht „bewiesen fehlerfrei“.

## Verifikation der Änderung (Host-Sandbox, nur diagnostisch, Node 24.22.2, nicht-root-Nutzer `qa`)

Quellbaum: Stand `cc8ee5f` (Codebäume identisch zu `68cc491`) plus genau diese eine Zeile.

| Gate | Ergebnis |
|---|---|
| `critical` Lauf 1, 2, 3 | 3 × PASS |
| `full-node` | PASS, 2393 bestanden / 0 fehlgeschlagen / 37 übersprungen (2430 gesamt) |
| Backend `npm test` | 64/64 bestanden |

Details: `lauf-zusammenfassung.json`. Die Sandbox hat nur den störenden `core.fsmonitor`-Eintrag aus dem injizierten `GIT_CONFIG` im Kindprozess entfernt (siehe Evidenzordner `code50-storage-flake-lead-20261108`).

## Dateien

- `rounds-load-N20.jsonl`: 40 Lastrunden von Agent 1 (je 20 für A und B)
- `mutation-result.txt`: Ausgabe des Mutationsnachweises
- `fisher.py`, `audit_timeouts.py`: Auswertung und Timeout-Audit
- `lauf-zusammenfassung.json`: Kurzfassung der vier Gate-Läufe auf dem Änderungsstand

Der gesamte Android-Build ist von dieser Änderung nicht berührt (keine Android-Datei referenziert `storage-fabric`). Die Android-Ergebnisse aus Lauf 42478725998 (4032 Tests, 0 Fehler, 20 übersprungen, Lint sauber) bleiben gültig, sind aber nur Diagnosebelege.
