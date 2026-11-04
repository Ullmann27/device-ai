# Getrennte Einordnung der bestehenden GLB-V1-Evidenz

Datum: 2026-11-04. Quellstand dieses Helfers:
`ac2ca364b3e21655fa249d3db943c26751e92bd5`.

Datei: `app/src/main/assets/avatar3d/continuum-avatar.glb`.
SHA-256: `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
Größe: 10.165.644 Bytes.

## Eigene aufbewahrte Ergebnisse

Der bestehende V1-Checker `tools/epsilon-avatar-contract/validate_glb.py` lieferte
Exit 1 und `FAIL` für `rig_hierarchy_orientation`:
`Eye/Jaw pivots outside preregistered metre-scale bust anatomy`.
Die gespeicherte Augenentfernung beträgt `0.06102388724684715` Meter.
Die acht vorhandenen Checker-Selbsttests liefen mit Exit 0 und `OK` durch.
Stdout, Stderr und Exitcodes beider Läufe liegen unverändert neben diesem Bericht.

Reproduktionsaufrufe aus dem Repository-Wurzelverzeichnis:

```sh
python3 tools/epsilon-avatar-contract/validate_glb.py app/src/main/assets/avatar3d/continuum-avatar.glb
python3 -m unittest discover -s tools/epsilon-avatar-contract -p test_validate_glb.py
```

Diese Befehle beschreiben die Reproduktion; für den begrenzten Abschlussreview
wurden die vorhandenen Ergebnisdateien gelesen und keine Suite erneut gestartet.

## Tragweite des negativen Ergebnisses

Der alte Checker fordert unter anderem eine absolute Kieferhöhe von 0,20–0,25 m
und einen vertikalen Augen-Kiefer-Abstand von 0,04–0,09 m. Die zuvor aus den
Node-Translationen ermittelten Weltwerte des aktuellen Assets sind ungefähr
0,267 m beziehungsweise 0,0214144 m. Die feste V1-Anatomieannahme passt damit nicht
zu diesem Export. Der Checker kehrt nach diesem Fehler früh zurück; seine späteren
Skin-, Morph- und Rotationsprüfungen wurden für diesen Lauf nicht durchlaufen.

**Dieser Befund allein beweist weder einen kaputten Kiefer noch eine ungültige
Modelldatei.** Die acht grünen Selbsttests belegen ihrerseits nicht, dass das
aktuelle Asset sämtliche Prüfungen bestanden hat. Es wurden weder Grenzen noch
Asset noch bestehende Validatoren verändert.

Die zuvor gelesene Rig-Struktur verwendet `AvatarRoot`, `Neck` und `Head` als
Skin-Joints. Kiefer und Augen haben eigene starre Mesh-Kinder. Eine isolierte
Prüfung, dass Kieferrotationen die Außenhaut nicht bewegen, beweist deshalb noch
keine richtige Bewegung der starren Kieferkinder. Die aktuelle Datei enthält keine
Animationsclips. Dies sind Strukturbeobachtungen, keine visuelle oder umfassende
geometrische Abnahme.

Der statisch gelesene Kotlin-`AvatarGlbContract` bindet das aktuelle Asset an seinen
Hash und verlangt nicht die genannten absoluten V1-Höhenfenster. Der Python-V1-
Fehler darf daher nicht als ausgeführter Kotlin-Vertragsfehler ausgegeben werden;
dieser Helfer hat den Kotlin-Vertrag nicht ausgeführt.

## Separater V2-Stand

Root und der Rig-Helfer melden inzwischen einen frischen bestandenen Lauf des
bestehenden V2-Checkers auf genau dem aktuellen `5f49…`-Asset. Dieser Bericht
beansprucht keinen eigenen V2-Lauf; dessen genaue Quittung und die 16 bestehenden
adversarial Selbsttests gehören zur separaten Rig-/Root-Evidenz. Eine historische
V2-Quittung für ein anderes `6b199…`-Asset wird hier nicht als Beleg für dieses
Asset verwendet. Es wurde kein zweiter Geometrie-Testapparat entwickelt.
