# Continuum Bunker V04

## Grundlage

Die vorhandene `source/Continuum_Mission_Desk_V3.glb` bleibt unverändert. Die
SHA-256-Werte stehen in `source.json`. Die Entwicklung verwendet den
Android-Stand `b8c31554ce7416540692f1d30b559be3712495d7` aus PR #848 und die
beiden im Workflow bezeichneten Ansichten: kompakt und erweitert.

Die vollständige Szene enthält den weiterentwickelten V3-Arbeitsplatz, einen
modellierten Bunkerraum, Bodensegmente, tragende Rippen, Tür, Serverracks, Lüftung,
Leitungen und konstruierte Monitorrückseiten. Graphit, Titan, Beton sowie
Cyan-, Blau- und Violettakzente bilden das Materialsystem.

## Zwei Ausgaben

| Ausgabe | Verwendung | Umfang |
| --- | --- | --- |
| `Continuum_Bunker_Stage_V04.glb` | Bearbeitbare Gesamtszene | 320 Meshes, 53 Materialien, 101.824 Dreiecke; vier Originalclips |
| `Continuum_Bunker_Stage_V04_Android.glb` | Ergänzung der bestehenden Filament-Szene | 55 Meshes, 23 Materialien, 70.020 Dreiecke; keine zweite Figur oder Bildschirmtextur |

Die Android-Datei ist identisch mit
`app/src/main/assets/cinema3d/continuum-bunker-v04.glb`. Ihr separates Manifest
bindet Dateipfad, Größe und SHA-256. Fehlende oder beschädigte Dateien lassen
die bisherige Szene bestehen.

Die Laufzeitdatei hat drei unabhängige Wurzeln:

* `continuum-bunker`: Raum und Architektur; einheitliche Skalierung.
* `continuum-mission-desk`: Schreibtisch; unabhängig von der Monitorfahrt.
* `continuum-monitor-engineering`: Rückseite, Halterung und Kabel; folgt der
  vorhandenen Monitormatrix.

Das ursprüngliche Avatar-Asset, seine Skin-/Morph-Daten, das Orb-Asset und das
Live-Monitor-Material bleiben bytegleich. Der Avatar ist nur als eigener
Begleiter in der Blender-Datei und ihren Renderbildern enthalten. Die App lädt
ihn weiterhin über den bestehenden Avatarpfad. Die Android-GLB enthält keine
zusätzliche Animation; die bestehende Laufzeit steuert Avatar, Orb und Monitor.

`glb_contract.py` erhält bei der vollständigen GLB die originalen Zeit- und
Rotationsaccessoren aller vier V3-Clips bytegenau. Die Prüfung kontrolliert
GLB-Struktur, Puffergrenzen, endliche Werte, Indizes und diese Animationen.
Sie ersetzt keinen vollständigen Khronos-Konformitätstest.

## Erzeugen und rendern

Aus dem Repository mit Blender 4.3.2:

```sh
blender -b --python tools/continuum-bunker/build_bunker.py -- --output /absolute/output
blender -b --python tools/continuum-bunker/build_bunker.py -- --output /absolute/output --reuse --render Front,Rear --samples 64 --percentage 90
blender -b --python tools/continuum-bunker/build_bunker.py -- --output /absolute/output --reuse --render Top,FoldWide,MonitorRear --samples 32 --percentage 80
```

Das erste Kommando erzeugt beide GLBs und die `.blend`-Datei. Die weiteren
Kommandos verwenden diese gespeicherte Szene. Sieben Flächenlichter, Cycles
auf CPU, AgX und zurückhaltender Compositor-Glow bestimmen das Licht.
Die Draufsicht verwendet eine orthografische Kamera unter den Deckenträgern.
Für die Detailansicht der Monitorrückseite wird der davorstehende
Avatarbegleiter nur im Render ausgeblendet.
Die Renderbilder sind geometrische Inspektionen; ihre beschrifteten Monitore
zeigen ausdrücklich eine 3D-Vorschau. Sie sind keine Android-Screenshots.

## Native Android-Inhalte

`ContinuumWorkspaceSummary` liest Projekte, offene Arbeit, Modell und
Systemwerte aus dem vorhandenen Dashboard-Zustand. Unbekannte Messwerte
bleiben `—`; fehlende Projekte werden nicht als erfundene Aufträge ausgegeben.
Breite Ansichten nutzen die bestehenden seitlichen Karten. Der native
Arbeitsbereich wird perspektivisch auf die Monitorfläche gelegt und ist nur
bei erfolgreich eingereichter ruhender Kamera ohne gebundene Bildquelle
sichtbar. Eine gebundene Quelle hat auch während des Wartens Vorrang.

Es entstehen weder ein zweiter Renderer noch ein zusätzlicher Frame-Takt.
Die bisherigen acht Navigationsziele, Live-Tabs, Sprach-/Avatarsteuerung,
Dokumentdarstellung und Capture-Pfade bleiben bestehen. Fensterbreite und
Schriftgröße bestimmen die Anordnung; es gibt keine fest codierte Fold7-
Displayauflösung. GPU-Leistung und Verhalten auf echten Geräten müssen mit
einem autorisiert gebauten APK am Gerät geprüft werden.
