# BOT07 – Avatar-Vertragstest

## Tatsächliche Ergebnisse

- Bestehende unittest-Suite: **PASS**, 8 Tests, Exitcode 0.
- Vorhandene Validator-CLI auf `app/src/main/assets/avatar3d/continuum-avatar.glb`: **FAIL**, Exitcode 1.
- Validator-Messwerte: nicht verfügbar Vertices, nicht verfügbar Dreiecke, nicht verfügbar Morph-Primitives.
- Vom Validator gemeldete Fehler: 1. Vollständige Details stehen in `bot07-avatar-validation.json` sowie den Rohprotokollen.
- Die SHA256-Werte beider GLB-Dateien sind identisch.
- Support-SHA256: `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
- Fremdcheckout-SHA256: `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
- Support-GLB während der Prüfungen unverändert: True.

## Prüfgrenzen

Geprüft wurden ausschließlich die bestehende Testsuite, der vorhandene numerische GLB-Geometrievertrag und die Dateiidentität. Ein bestandener Geometrievertrag belegt keinen Screenshot, keine visuelle Ähnlichkeit und keinen Fold7-Look. Renderer, Android-Build, native Laufzeit und Gerät wurden nicht ausgeführt. GLB, Renderer und Design wurden nicht verändert; der Fremdcheckout wurde ausschließlich lesend verwendet.

## Rohbelege

`bot07-unittest.stdout.txt`, `bot07-unittest.stderr.txt`, `bot07-validator.stdout.txt`, `bot07-validator.stderr.txt`, `bot07-avatar-validation.json`, `bot07-execution.json`, `bot07-hashes.json`.

## Präzisierung durch die Koordination

Die Weltkoordinaten wurden anschließend mit dem vorhandenen Parser unabhängig
ausgelesen (`bot07-pivots.json`): Augenhöhe 0,288414419 m, Kieferdrehpunkt
0,266999998 m, vertikaler Abstand 0,021414421 m. Der bestehende Vertrag verlangt
für den Kiefer 0,20–0,25 m und für den Abstand 0,04–0,09 m. Die Augenhöhe liegt
innerhalb ihres Bereichs. Das Avatar-Team muss den vorgesehenen Kieferdrehpunkt
und die Herkunft dieser Vertragsgrenzen klären; eine ungeprüfte Verschiebung des
Assets oder Lockerung des Prüfers wäre keine belegte Reparatur. Das Asset blieb
auch bei dieser Präzisierung unverändert.
