# Ref5 · Bindung an 7c3523c

Die archivierte START-Aufnahme wurde mit dem vorhandenen `tools/measure_ref5_app_colors.py` erneut gemessen (Exit 0). Quellstand **`7c3523cde9dae302a52d69818867a9abe8cf0fb7`**, Bild-SHA und Dump-SHA stimmen mit `native-canvas-7c3523cd/manifest.json` überein. Es wurden keine neuen Bilder oder App-Tests erzeugt.

## Ergebnis

Der native View-Dump weist die Chatkachel als `Rect(148,426–288,474)` bei Alpha 1 und Skalierung 1/1 aus. Alle vier Farbstichproben sowie das vollständige lokale Glowprofil stimmen mit der Baseline `b294fc9139f1a458fccf815bb931eb61b2f7b0e8` und dem getrennten Bericht zu `9468eb87` überein.

| Rolle | Native App-ROI (x0,y0,x1,y1) | Ref5 | 7c3523c |
|---|---|---|---|
| Kachelfüllung, dunkelste 30 % | `(229,440,263,470)` | `#051B30` | `#08132D` |
| Oberer Kachelrand, hellste 10 % | `(187,424,232,430)` | `#17687F` | `#31C5FD` |
| Iconkern, hellste 10 % | `(158,438,182,462)` | `#C9FFFF` | `#C9FFFF` |
| Titelband, hellste 10 % | `(192,434,281,447)` | `#FFFFFF` | `#FDFDFD` |

Rand-/Füllungs-Kontrast der aggregierten Stichproben: **Ref5 2,756; App 9,231**. Der App-Rand ist bereits deutlich heller. Die äußeren Profile von 0–12 nativen Pixeln bleiben nicht monoton; die 1/e-Breite ist **`null`**. Daraus folgt keine belastbare Blur-/Bloom-Konstante.

**Grenzen:** Original-Ref5 1448×1086, App 884×760; keine Skalierung oder Ganzbildgleichheitsmetrik. Host / nativer Android-Canvas / API 28 / 2D-Kopf, kein Gerät- oder Filamentnachweis. Titelband ohne Glyphenmaske; Stichprobenkontrast ist keine Accessibility-Abnahme, RGB-Abstand kein DeltaE. Die Baseline besitzt keinen View-Dump; ihre explizite Box-Zuordnung bleibt im Rohbericht offengelegt. Die Animationsphasen wurden nicht synchronisiert.

Der ältere `9468eb87`-Beleg bleibt unverändert gültig. Dessen Gesamt-Testlauf war **274/275**, mit einem späteren Hintergrundscrollfehler. Die **39/39** im neuen Archivmanifest gehören zum separaten Nachlauf auf `7c3523c`; diese Farbmessung führt selbst keine Funktionsabnahme aus.

## Rohbelege und SHA-256

Repo: `/workspace/scratch/76b7222771d5/continuum`

- Neue Aufnahme und Dump: `docs/evidence/astra-jarvis-visual-20261105/native-canvas-7c3523cd/fold7-inner-884x760-1-start.png` / `.views.txt`
- Vorheriger Bericht und archivierte Baseline: `docs/evidence/astra-jarvis-visual-20261105/ref5-colors-9468eb87/`
- Neuer Rohbericht: `/workspace/scratch/76b7222771d5/neon-flow/work-window/ref5-app-colors-7c3523c.json`

| Beleg | SHA-256 |
|---|---|
| Neues PNG | `e701d50e4fab09a25ab03e338c903eb4d056aabc4e475c8a672799aa3dda4771` |
| Neuer View-Dump | `c102f81183dd037bb027c3be83cdb218240c53b7157c281dbbcbefc66b27a8ff` |
| Archivmanifest | `9e68ab162e4afd6da46ad37bf070a42ae36a20328e7602e5548cb8c852e82d89` |
| Neuer Rohbericht | `54032c896836d0f0c821cec6ad442a117fb762b24d42b389a31b41db76b44501` |
| Vergleichsskript | `1a714deb4be54495939eba90186a835b3e3f460e262d54c8cb68cb70bd82ce98` |
| Importierter Messhelper | `8f43a817fe7cb3f1661079eeb316c2e14f1a6a6b2fa45f933ce7effa868c75dc` |

Referenz- und Baseline-SHAs sowie alle Referenz-ROIs stehen vollständig im neuen Rohbericht.

## Reproduktion

```bash
task_repo=/workspace/scratch/76b7222771d5/continuum
python3 "$task_repo/tools/measure_ref5_app_colors.py" \
  --repo "$task_repo" \
  --reference "$task_repo/docs/design-screenshots/ref5-fold7-offen.png" \
  --baseline "$task_repo/docs/evidence/astra-jarvis-visual-20261105/ref5-colors-9468eb87/baseline-b294-start.png" \
  --baseline-box 148 426 288 474 \
  --baseline-box-note 'Kein nativer Baseline-View-Dump vorhanden. Box aus unveraenderter JarvisCockpitGrid-Geometrie, ganzzahliger Control-Platzierung und gemessenem oberen Randmaximum bei y=426; identische Zuordnung wie im separaten 9468eb87-Bericht, keine Bildskalierung.' \
  --current "$task_repo/docs/evidence/astra-jarvis-visual-20261105/native-canvas-7c3523cd/fold7-inner-884x760-1-start.png" \
  --current-views "$task_repo/docs/evidence/astra-jarvis-visual-20261105/native-canvas-7c3523cd/fold7-inner-884x760-1-start.views.txt" \
  --current-source-label 7c3523cde9dae302a52d69818867a9abe8cf0fb7 \
  --density 1 --font-scale 1 \
  --output /workspace/scratch/76b7222771d5/neon-flow/work-window/ref5-app-colors-7c3523c.json
```
