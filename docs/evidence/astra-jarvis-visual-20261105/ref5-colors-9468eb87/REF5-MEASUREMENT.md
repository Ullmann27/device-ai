# Ref5 · semantische Farbmessung

**Der Screenshot aus `9468eb87` ist ein gültiger Farb-/Renderbeleg. Der zugehörige Gesamt-Testlauf bestand 274 von 275 Tests; eine spätere Hintergrundscroll-Assertion schlug fehl. Für diesen Stand wird kein Gesamt-GREEN behauptet.** Die anschließende Korrektur betrifft den Scroll-Callback und verändert keine Produktfarben. Neue Daten-Scroll-Aufnahmen sind separat zu bewerten.

## Vier gemessene Rollen

Original-Ref5: **1448 × 1086 px**. Baseline und finale App: jeweils **884 × 760 px**, nativer Android-Canvas auf dem Host, API 28/mdpi. Keine Skalierung, Streckung oder Ganzbildmetrik.

Der finale View-Dump weist `ShellActionView tag=jarvis-tile:chat` mit **`Rect(148,426–288,474)`**, Alpha 1 und Skalierung 1/1 aus. Die Baseline hat keinen View-Dump: Ihre gleiche Box ist ausdrücklich über die unveränderte Referenz-Layoutgeometrie, ganzzahlige Control-Platzierung und das gemessene Randmaximum bei y=426 zugeordnet.

ROI-Angaben: `(x0,y0,x1,y1)` in den jeweiligen Originalpixeln, rechte/untere Grenze exklusiv. `dark` ist der Kanalmedian der dunkelsten 30 %, `peak` der Kanalmedian der hellsten 10 % gemäß dem vorhandenen `measure_ref5_colors.measure`.

| Rolle / Verfahren | Ref5-ROI → RGB | App-ROI → RGB, Baseline und Final | Final − Ref5, RGB |
|---|---|---|---|
| Kachelfüllung / dark | `(440,590,500,640)` → `#051B30` | `(229,440,263,470)` → `#08132D` | `+3 / −8 / −3` |
| Oberer Kachelrand / peak | `(360,566,440,572)` → `#17687F` | `(187,424,232,430)` → `#31C5FD` | `+26 / +93 / +126` |
| Cyan-/Weißkern des Icons / peak | `(315,580,350,610)` → `#C9FFFF` | `(158,438,182,462)` → `#C9FFFF` | `0 / 0 / 0` |
| Weißes Titelband / peak | `(375,577,415,595)` → `#FFFFFF` | `(192,434,281,447)` → `#FDFDFD` | `−2 / −2 / −2` |

**Diese vier Stichproben sind in Baseline und Final identisch.** Der bestehende App-Rand ist gegenüber Ref5 deutlich heller. Daraus folgt keine weitere pauschale Aufhellung. Die Titel-ROI beruht auf einer geschätzten Schriftoberkante; sie ist keine Glyphenmaske.

## Kontrast und äußeres Lichtprofil

Das linearisierte sRGB-Verhältnis der Randstichprobe zur jeweiligen Füllungsstichprobe beträgt **2,756 in Ref5** und **9,231 in beiden Appbildern**. Es beschreibt genau diese aggregierten Farbstichproben und ist keine Accessibility-Abnahme von Text oder Bedienelementen. RGB-Abstand ist kein wahrnehmungsbezogenes DeltaE.

Das äußere Profil umfasst **0 bis 12 native Pixel** über dem oberen Rand, beginnend bei y=568 in Ref5 beziehungsweise y=426 in der App. Alle drei Profile sind **nicht monoton**; benachbartes Szenenlicht trägt zu den Werten bei. Die 1/e-Breite bleibt deshalb im Rohbericht **`null`**. Daraus lässt sich keine verlässliche Blur-/Bloom-Konstante ableiten. Baseline und Final besitzen auch in diesem ausgewählten Profil dieselben Werte.

Keine Aussage über Gesamtbildgleichheit, Filament, Geräte-GPU oder Fold7-Farbtreue. Der Host zeigt einen 2D-Kopf; API-31-Blur und API-33-AGSL werden durch diesen API-28-Beleg nicht abgenommen. Animationsphasen und dynamische Hintergründe wurden nicht zwischen den Aufnahmen synchronisiert.

## Quellstände und Rohbelege

- Baseline-Quelle: `b294fc9139f1a458fccf815bb931eb61b2f7b0e8`
- Quelle des finalen Messbildes: `9468eb87cd875c5e07f689aeb53767880ccd6765`
- Ref5-PNG: `/workspace/scratch/76b7222771d5/continuum/docs/design-screenshots/ref5-fold7-offen.png`
- Baseline-PNG: `/workspace/scratch/76b7222771d5/build-env/baseline-images/fold7-inner-884x760-1-start.png`
- Messbild 9468: `/workspace/scratch/76b7222771d5/build-env/final-visual/fold7-inner-884x760-1-start.png`
- Nativer Dump: `/workspace/scratch/76b7222771d5/build-env/final-visual/fold7-inner-884x760-1-start.views.txt`
- Rohbericht: `/workspace/scratch/76b7222771d5/neon-flow/work-window/ref5-app-final-colors.json`
- Kurzbericht: `/workspace/scratch/76b7222771d5/neon-flow/work-window/ref5-app-final-colors-summary.json`
- Gesamt-Testlog: `/workspace/scratch/76b7222771d5/build-env/logs/final-cockpit-validation.log`

| Quelle | SHA-256 |
|---|---|
| Ref5-PNG | `05fe60aa19e297fdf7bb4a0a286cebafa72c7a023a26e65e65adc38a7077892f` |
| Baseline-PNG | `057dde500c57bdff75c0d77ddba6eeb7782346285933ffd333fdb985b9765b31` |
| Finales PNG | `dcafb2da871c0184f7fcbb2a76e0452247fd77196669ba20c13c2574925f3e1e` |
| Finaler View-Dump | `18a0894b21def175fd759b190dd17ef030a31010acecc2462d86358ac98606bd` |
| Importierter Helper `tools/measure_ref5_colors.py` | `8f43a817fe7cb3f1661079eeb316c2e14f1a6a6b2fa45f933ce7effa868c75dc` |
| Vergleich `tools/measure_ref5_app_colors.py` | `1a714deb4be54495939eba90186a835b3e3f460e262d54c8cb68cb70bd82ce98` |
| Rohbericht | `0f015f1b9efbe4cf130f72cd828c49e3dacf6cbeb15ac72cf4aaa68ca1ef3b4b` |

Unabhängige Gegenprüfung: drei Bildprüfsummen und alle zwölf ROI-Farbwerte wurden erneut aus den Original-PNGs berechnet; alle stimmen überein. Die Summary verweist auf den passenden Rohbericht-SHA.

## Reproduktion

Der folgende vorhandene Befehl enthält sämtliche Bild-, Dump-, ROI-, Quellstand- und Ausgabeparameter und verwendet die Repo-Kopie des Vergleichsskripts:

```bash
bash /workspace/scratch/76b7222771d5/neon-flow/work-window/reproduce-final-colors.sh
```

Die Ausführung dieser Messung endete mit Exit 0. Sie ersetzt keine erneute Funktionsabnahme des anschließend geänderten Scroll-Callbacks.
