# Code55 Experiment B: räumliche Transformationen

RESEARCH_SHADOW / WIP. Basis `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Ziel: `work/astra55-cockpit-experiments-20261112` → #799 → #791.
Bestehende EXP-07/11/12; gemeinsame Filament-1.56.0-Szene bleibt erhalten.

Nach offizieller Khronos-/Filament-/Android-Recherche wird genau ein A/B
geprüft: identische lokale Matrizen vor denselben Transform-Aufrufen
überspringen. Gate: mindestens 20 % weniger native Transform-Aufrufe im
eingefrorenen Replay bei identischen lokalen/Weltmatrizen und Projektionen.
Gegenproben: bewegter Elternknoten, Resize, Pitch/Z/Umkehr, veränderlicher
Scratchpuffer, neue Instanz und fehlgeschlagener Upload.

Dateibesitz: Transform-Update-Lane FilamentCockpitStage.kt, kleiner
CockpitTransformUpdates-Helfer, eigene Tests/Runner. Keine Änderung an
Assets, Materialien, Originalringen, Layout, Clock oder Avatarrenderer.
Kandidat deaktivierbar; bei verfehltem Gate nicht übernehmen.
## Implementierungscheckpoint

Der bestehende Transformblock ist unverändert berechnet; seine neun Aufrufe
gehen durch einen instanzgebundenen Cache. Verglichen werden alle 16
Float-Bitmuster, ohne Epsilon. Jeder erfolgreiche Upload erhält eine eigene
Kopie. Uploadfehler verwerfen auch den vorherigen Cacheeintrag. Nicht endliche
Matrizen werden weitergereicht, aber niemals gespeichert. `cacheTransforms=false`
am internen Stage-Konstruktor stellt den ursprünglichen Schreibpfad her.

Der eigene Harness extrahiert den Transformblock direkt aus Baseline und
Arbeitsquelle. Nur RectF-Datenhülle und TransformManager sind Hostgrenzen.
Aktuelle Produktionsklassen erzeugen die Matrizen; die tatsächlichen beiden
Stage-GLBs liefern die Hierarchie. Alle lokalen und Weltkomponenten werden
pro Frame exakt verglichen. Zusätzlich werden deaktivierter Kandidat,
Elternbewegung, Resize, Seitenmonitor-Sichtbarkeit und Umkehr geprüft.

Quellen und Repro unter `docs/evidence/astra55-experiment-b-scene-20261112/`.
Erster Compilerlauf meldete eine fehlende bestehende Testabhängigkeit
`GlbSkinningRig`; die Quellenliste wurde ergänzt. Wiederholung läuft.
Status dieses Checkpoints: WIP / noch kein bestandener A/B-Nachweis.
Vollständige kombinierte Androidklassen prüft Root separat. Gerät NOT_RUN.
Transform-Aufrufbudget ist kein gemessener GPU-/FPS-/Thermikgewinn.
