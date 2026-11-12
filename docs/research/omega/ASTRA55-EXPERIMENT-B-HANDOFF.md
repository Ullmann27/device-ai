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
Startcheckpoint ohne Produktionsdiff. Tests NOT_RUN, Gerät NOT_RUN.
Transform-Aufrufbudget ist kein gemessener GPU-/FPS-/Thermikgewinn.
