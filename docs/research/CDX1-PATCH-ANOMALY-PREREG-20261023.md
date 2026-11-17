# Experiment D – CDX1 als Patch-Anomalie-Sensor: Vorregistrierung (23.10.2026)

Festgelegt **vor** dem ersten Messlauf. CDX1 (`TinyContextDecoder`, Byte-Modell, Kontext 8 Bytes) bleibt in jedem
Fall **beratend**: es kann keinen Patch freigeben, blockieren oder den CCC1-Verifier übersteuern.

## Frage

Erkennt CDX1 defekte Reparatur-Patches, die die vorhandenen billigen Prüfungen (`git apply --check` und
`node --check` auf dem Ergebnis) **nicht** erkennen – bei wenig Fehlalarmen – und besser als ein triviales
Byte-n-Gramm-Modell?

## Daten (deterministisch, Seed 20261023)

- Hunks aus der Git-Historie von `.github/continuum/*.mjs` bis Commit `81180313` (PR-#97-Basis), `git diff -U2`,
  ohne neu angelegte oder gelöschte Dateien; Hunk-Text ≤ 480 Bytes; mindestens eine hinzugefügte Zeile mit ≥ 10
  Zeichen; Eltern-Datei + Hunk lässt sich anwenden und besteht `node --check` (nur solche gelten als GÜLTIG).
- Aufteilung nach SHA-256 des Dateipfads: TRAIN < 0,6 ≤ CAL < 0,8 ≤ TEST (alle Hunks einer Datei in einem Teil).
- Gruppen (je gültigem Hunk höchstens eine Variante je Gruppe, angewendet auf eine zufällige hinzugefügte Zeile):
  `VALID`; `SYNTAX` (eine Klammer gelöscht, sonst `{` eingefügt); `MISSING_TOKEN` (ein Bezeichner/Schlüsselwort
  entfernt); `TRUNCATED` (Zeile bei 30–90 % abgeschnitten); `ODD_BYTES` (3–6 Zeichen aus einer festen Menge
  ungewöhnlicher Zeichen eingefügt); `SEMANTIC` (eine syntaktisch gültige Operator-/Literalvertauschung, z. B.
  `===`→`!==`, `&&`→`||`, `<=`→`<`, `true`→`false`); `INCOMPLETE_HUNK` (eine Hunk-Zeile entfernt, Kopf unverändert).
- Parser-Urteil je Fragment: `git apply --check` und – falls anwendbar – `node --check` auf Eltern-Datei + Hunk.

## Modelle und Werte

- CDX1: `TinyContextDecoder(seed = 91)`, trainiert nur auf TRAIN-Texten (gültige TRAIN-Hunks und TRAIN-Dateiabschnitte
  ≤ 480 Bytes), fester Plan: 400 000 Updates, Lernrate 0,035.
- Vergleich: Byte-4-Gramm mit Witten-Bell-Interpolation auf denselben TRAIN-Texten.
- **Primärwert** `S_max`: Maximum über 16-Byte-Fenster der mittleren Byte-Überraschung (Bits); Sekundär `S_mean`.
- Schwelle: 95.-Perzentil von `S_max` auf CAL-`VALID`. TEST wird genau einmal ausgewertet, nicht zum Einstellen genutzt.

## Messgrößen (TEST)

Trefferquote je Gruppe, Fehlalarmquote auf `VALID`, AUROC je Gruppe gegen `VALID`, dieselben Größen auf der
**vom Parser nicht erkannten** Teilmenge, Zeit je Fragment (JVM x86_64, keine Android-Aussage), Parameter/Speicher.

## Entscheidungsregel

CDX1 hat **Zusatznutzen**, nur wenn auf der vom Parser nicht erkannten TEST-Teilmenge gilt: Trefferquote ≥ 0,20 **und**
≥ Fehlalarmquote + 0,10 **und** AUROC(CDX1) ≥ AUROC(4-Gramm) + 0,05. Sonst: kein CDX1-Signal in CCC1-Quittungen;
Parser-/Apply-Prüfung und echte Tests bleiben maßgeblich; schlägt das 4-Gramm CDX1, wäre es der bessere Kandidat für
einen späteren rein beratenden Hinweis.
