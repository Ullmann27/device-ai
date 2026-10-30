# Continuum M9–M16 · Abschlussbericht (30.10.2026)

**Zweig:** `opus/continuum-m9-m16-20261030` · **Draft-PR:** #245 auf #244 (`0e672333`, unverändert). Kein Merge nach `main`, kein Force-Push. Alle neuen Bausteine laufen **SHADOW**. Antworten, Rangfolge, Ledger, Rechte und Modellgewichte sind unverändert.

## Gemessene Ergebnisse (jeweils vorregistriert, Holdout genau einmal)

| M | Ergebnis | Beleg |
|---|---|---|
| M9 | Auf **unbekanntem echtem Code** löst Erfahrung **19–20/44** statt 9/44 (p = 0,007–0,022) und braucht dafür weniger Testläufe. Verifikation senkt falsche Erfolge von 4 auf 1. Kontext bringt nichts zusätzlich. | `docs/research/m9/M9-RESULT-20261030.md` |
| M9 Realitäts-Kohorte | 5 echte historische Fixes, davon 4 reproduziert. **0/4** liegen im Vorlagen-Vokabular, der Agent repariert 0. Echte Fehler brauchen ein generatives Modell. | `historical-real-fixes.json` |
| M10 | Selbstkorrektur-Heuristik **widerlegt** (16 statt 19 gelöst, keine Einsparung). Dafür 0 falsche Erfolge. | M9-Bericht |
| M11 | F1-Kern auf dem Holdout: **4 → 26 richtig, 24 → 0 falsch**. Gedächtnis: **EWM2+ 36/36** gegenüber 12/36. | `m11/M11-RESULT-20261030.md` |
| M12 | Aussagenprüfer: Holdout 1 **verfehlt** (Ordinalpunkt-Fehler). Holdout 2: **48/50 erkannt (vorher 28), 0/30 Fehlalarme**. | `m12/M12-RESULT-20261030.md` |
| M13 | Gegenbeispiel-Miner mit unabhängiger Nachmessung gebaut und getestet. Nachtrag: Lauf auf `732cb740` durchgeführt. 17 Hypothesen: 1 CONFIRMED (nicht Bonferroni-fest), 1 REFUTED, 15 INCONCLUSIVE ([Ergebnis](m13/M13-RESULT-20261030.md)). | `boundaries.mjs`, `run-m13.mjs` |
| M14 | Echte `SIGKILL`-Tests: Lücke „keine Besitzer-Freigabe toter Build-Lease“ geschlossen (`--release-dead-lease`). Kein Doppel-Repair, abgeschnittener Zustand schlägt geschlossen fehl. | `m14/M14-RESULT-20261030.md` |
| M15 | Prüfer-Regression von 3,8 ms auf **1,4–2,0 ms**, Qualität identisch. Ergebnis-Cache spart **41 %** der Testläufe. | `m15/M15-RESULT-20261030.md` |
| M16 | Verbunden und per Aufrufketten-Test belegt. Gemma-Pfad → Aussagenprüfer → Trace. Gesprächspfad → F1-Kern (Schatten). EWM2-Schatten → Zeitlinie und Resolver. Worker → Lease-Freigabe und Host-Diagnose. | `.github/continuum/m9-m16-wiring.test.mjs` |

## Regression auf dem Endstand

Kotlin-Harness **1282/1282** (241 Klassen, 4 Checks; #244 hatte 1271). Node kanonisch **226/226**, Worker/CCC1/F3 **391/391**, neue Tests **63/63**, Python **10/10**. Belege liegen in `docs/evidence/m9-m16/`.

## Nicht ausgeführt / offen

- Gradle, Lint, APK, Signatur, native Gemma und **Fold7**: in dieser Umgebung nicht verfügbar.
- (M13 ist inzwischen gelaufen, siehe oben.) Ein Prüfpass des Denkrouters nach Flag des Aussagenprüfers: vorbereitet, nicht gelaufen.
- Kein Sprachmodell: Reparaturen stammen aus einem Vorlagen-Vokabular. Die historische Kohorte zeigt dessen Grenze.

## Nächster Schritt

Fold7-Abnahme dieses Commits. Danach messen, wie oft der Aussagenprüfer und die Zeitlinie anschlagen, verglichen mit dem Besitzer-Feedback. Erst dann über die Aktivierung entscheiden.
