# Hollywood‑FUI Konstrukt v2 – Painter‑Frames (Java2D‑Stand‑in, 2026‑11‑08)

Erzeugt mit `scripts/hollywood-fui-cinematics-preview.sh` aus dem **produktiven Zeichenpfad**
(`HollywoodFuiConstruct` → `HollywoodFuiCinematics` → `ContinuumPainter`), gerendert über
`tools/continuum-jvm-preview/.../HollywoodFuiCinematicsPreview.kt` mit `Java2DContinuumPainter`.

| Datei | Inhalt |
|---|---|
| `sheet-fold-open.png` | Fold7‑offen‑Geometrie (884×760 dp), Öffnen bei 70 / 140 (Anker) / 230 / 320 (Klammern) / 440 / 560 (Raster) / 760 (Glas) / 900 (Daten) / 2400 ms (arbeitend) |
| `sheet-fold-close.png` | Schließen bei 60 / 120 / 220 / 300 ms, danach `settled-warm` (thermisch 2 + Inferenz) und `settled-low` (Energiesparen/heiß) |
| `sheet-phone.png` | Phone‑Hochformat (412×915 dp), Fenster unter dem Kopf |
| `zoom-fold.png` | Ausschnitt 140 / 440 / 760 / 2400 ms nach der 2. Iteration (Glas‑Staffelung, Neon‑Rand, Motes, Energierand) |

Die Zeile am unteren Rand jedes Frames nennt die realen Werte des Gesetzes (`anchor`, `brackets`, `grid`, `glass`,
`data`, `tilt`, `pulse`, `closeData/closeGlass/closeFrame`).

**Was dieser Nachweis NICHT ist:** kein Fold7‑Screenshot, keine Framezeit, keine GPU‑/Temperaturmessung. Die
Perspektivneigung ist hier per Scherung angenähert (Java2D hat keine 3D‑Kamera); auf dem Gerät übernimmt
`android.graphics.Camera.rotateY` im Overlay bzw. `View.rotationY` am Fensterpanel. Profildaten (Android‑Canvas‑Text)
und der Avatar werden hier nicht gezeichnet. Gerät‑Abnahme: siehe Handoff
`docs/research/omega/FABLE51-CODE51-FUI-CINEMATIC-HANDOFF-20261108.md`.
