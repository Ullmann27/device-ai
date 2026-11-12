# Code55 Experiment A: Monitor-Uniforms

RESEARCH_SHADOW / WIP. Basis `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Ziel: `work/astra55-cockpit-experiments-20261112` → #799 → #791.
Bestehende EXP-07/11/12; keine neue Capture-/Rendererarchitektur.

Nach offizieller Android-/Filament-Recherche wird genau ein A/B geprüft:
identische Monitor-Uniformwerte nicht erneut schreiben. Mindestens 50 %
weniger gemessene Material-Schreibaufrufe bei 60 Ticks/10 Frames, während
sichtbarer Zustand, Sitzungs-/Frischeprüfung, Import- und Freigabefolge
unverändert bleiben. Stop, Sitzungswechsel, überholte Bilder, Resize,
verspätete Freigabe und abgelehnter Farbraum sind Gegenproben.

Dateibesitz: CockpitLiveTexture.kt nur Materialupdates und eigene Tests/Runner.
Der laufende Audit #803 behält Capture, Dispatch und Präsentationsbestätigung.
Kandidat deaktivierbar; bei verfehltem Gate nicht übernehmen.
Startcheckpoint ohne Produktionsdiff. Tests NOT_RUN, Gerät NOT_RUN.
Aufrufbudget ist kein gemessener GPU-/FPS-/Thermikgewinn.
