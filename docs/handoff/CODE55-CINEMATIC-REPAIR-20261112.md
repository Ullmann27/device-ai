# Code55 – Monitorlicht und durchgängiges Bloom

Integrationslinie: `integrate/code55-apk-fable-20261112`, Ausgangspunkt
`2e0668165c` mit Fable PR #808 (`7c1fd7baf06e6753f6285d7b13baf4f0585bb916`).
Integrator: Root. Reparatur bestehender Darstellung, kein neues Rendersystem.

## Änderungen

- `FilamentCockpitStage` reicht dieselbe Monitor-Matrix an Geometrie und Licht.
  `CockpitMonitorLightTransform` verwendet die vollständigen gedrehten Achsen.
  Die Lichtquelle bleibt 30 % der Monitorhöhe unter der Mitte und 22 % der
  größeren Monitorausdehnung vor der Fläche. Die Reichweite folgt der Größe.
- Position und Reichweite werden unabhängig nach erfolgreichem Upload gecacht.
  Ein fehlgeschlagener Aufruf entwertet den betreffenden Cache vor der Übergabe;
  ein Rücksprung zur früheren Position kann keine teilweise geschriebene Position
  verbergen. Unveränderte Werte erzeugen keine weiteren nativen Aufrufe.
- `FilamentAvatarRenderer` schaltet Bloom bei sichtbaren Livepixeln nicht mehr
  für die ganze Szene aus. Bestehende Stärke, Schwellwert, Auflösung und
  Qualitätssteuerung bleiben erhalten. Der Sprachenergiecache wird bei einem
  Wechsel zwischen Portrait und Cockpit zurückgesetzt.
- Originalavatar, Morphs, fünf Kugelringe und bestehende Animationskette bleiben.
  Blick, Sakkaden, Blinzeln, Atmung, Kopf/Hals und Visemen waren bereits in
  `AvatarMorphMapper` / `AvatarIdleMotion` / `applyWorkBody` angeschlossen.

## Quellenbefund

Der vorherige Lichtpunkt verwendete nur Matrixdiagonale und Translation.
Bei einem gültigen Frame 1080×1920, Monitor `(120,900)-(960,1700)`, Yaw 8°,
Pitch 12° beträgt der rechnerische Unterschied zur vollständigen Transformation
etwa 18,74 Bildpunkte horizontal und 32,40 vertikal. Das ist Geometriearithmetik,
keine Bild- oder Gerätebeobachtung.

`tools/cockpit-workstation/materials/live-monitor.mat` ist unlit und begrenzt
SDR-Farbwerte auf [0,1]. Die Filament-1.56-Implementierung entfernt bei aktivem
Bloom-Schwellwert Werte bis einschließlich 1:
[bloomDownsample2x.mat](https://github.com/google/filament/blob/v1.56.0/filament/src/materials/bloom/bloomDownsample2x.mat).
Normale App-Pixel erzeugen deshalb keinen eigenen Bloom-Beitrag. Streulicht
heller Nachbarflächen und tatsächliche Lesbarkeit bleiben visuell zu prüfen.
Die gültige Cubemap-API wurde gegen
[Texture.java 1.56.0](https://github.com/google/filament/blob/v1.56.0/android/filament-android/src/main/java/com/google/android/filament/Texture.java)
geprüft; Fables RGB/FLOAT/POT-Parameter erfüllen deren Vertrag.

## Prüfung und Grenzen

- `git diff --check`: bestanden.
- Neue `CockpitMonitorLightTransformTest`: fünf Methoden, darunter neun
  Yaw/Pitch-Kombinationen, Größenwechsel ohne Positionswechsel, Cache-Reset,
  teilweise fehlgeschlagener Positionsupload und fehlgeschlagener Reichweitenupload.
- Ausführung der neuen JVM-Tests und Gesamtkompilation: durch Integrator offen.
  Kein eigener paralleler Gradle-Lauf.
- Filament-Renderbild, Fold7-Bildrate, Wärme und Lichtabstimmung: nicht gemessen.
- `PORTIERT`: lokaler gemeinsamer Kandidat. `VERIFIZIERT`: nur Diffprüfung und
  genannter Quellen-/Geometriebefund. `GERÄTEBESTÄTIGT`: nein.

Relevante Regression:
`./gradlew :app:testDebugUnitTest -PcontinuumUiWidgetTests=true --tests 'at.heinz.deviceai.avatar3d.*'`.
Die Versionierung der eigentlichen Build- und Testbelege erfolgt durch Root.

Bestehende EXP-07-/EXP-11-Integrationsprinzipien bleiben kompatibel; keine
Aktivierung zusätzlicher Experimente. Rückfall: diesen Reparaturdiff zurücknehmen;
Fables Studio-Cubemap und Monitorlicht bleiben separat in PR #808 erhalten.
