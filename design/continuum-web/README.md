# Device AI Continuum – bewegter Arbeitsraum mit den vorhandenen Originalen

Diese isolierte Gestaltungsfassung setzt das gewünschte Raster um: Chats und Projekte links, Werkzeuge oben, Avatar und Linkleiste in der Mitte, Diagramme und Quellen rechts. In Präsenz 02 steht der Originalavatar mittig vor der geometrisch ruhenden Originalkugel. Zwei neue interaktive Hologramme flankieren diese Einheit. Sie verbindet den vorhandenen Kopf und Kugelrenderer mit kontinuierlicher Atmosphäre, räumlichen Arbeitsflächen und einer bedienbaren Argon4-Demonstration.

## Öffnen

- Private Webvorschau und Projektdateien: https://device-ai-continuum-heinz.electemu.chatgpt.site
- Zugehöriger Draft-PR: https://github.com/Ullmann27/device-ai/pull/850
- Figma, früherer nativer Designstand: https://www.figma.com/design/rN6yiKiRwzeOPxnqEuaN7O

Der bestätigte Bereitstellungsstatus steht in der Auslieferungsnachricht. Für den direkten lokalen Betrieb aus diesem Repository:

```sh
cd design/continuum-web/continuum-preview
npm start
```

Node.js 20 oder neuer genügt für den lokalen Server; für den Betrieb werden keine Pakete installiert. Das vollständige Originalmodell und die Schriften sind enthalten. Die Entwicklungswerkzeuge unter `interop` haben einen separat dokumentierten Node-Versionsbereich.

## Herkunft und Erhaltung

Ausgangsbasis ist `claude/code55-cinematic-evolution-20261112`, Commit `1b90611388cc2b1978ddfe32064a25dd3a08cee1`.

- `continuum-preview/assets/continuum-avatar.glb` ist **byteidentisch** zum vorhandenen Android-Asset: 12.038.416 Bytes, SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`. Geometrie, Gesicht, vorhandenes Rig, 21 Morph-Ziele und Materialbestand wurden nicht ersetzt.
- `interop/orb-original-source.js` überträgt den tatsächlichen `ContinuumHologramRenderer.kt` und seinen `ContinuumPainter`-Vertrag. Acht Originalquellen und ihre Hashes liegen bei. Die alte Ersatzkugel ist abgeschaltet.
- Blender verändert am Originalkopf nur die Szenenbeleuchtung und Kamera. Der neue Displaykörper ist ein separates Exportasset. Seine `.blend` und `.glb` liegen im vollständigen Downloadpaket; Builder und Prüfdaten liegen hier.

## Was die Fassung enthält

Sieben Szenen und sieben Bereiche; getrennte lokale Chatverläufe; Seitenleisten als Schubladen auf schmalen Displays; rechts gekoppelte Phasen, Erklärungen und Quellen; abbrechbaren 950-ms-Aufbau; Materialstärke und Rückkehr der Arbeitsfläche; unregelmäßige Blick-, Blink- und Haltungsanimation; unabhängige Kugel-, Partikel- und Lichtbewegung.

Die Lippenanimation nimmt echte Viseme-Ereignisse entgegen. Der Sprechzustand allein erfindet keine Mundformen. Ohne WebGL zeigt die Fassung ein Blender-Bild desselben Originalkopfs; die Kugel und Atmosphäre bleiben separat bewegt.

## Nachweise und Grenzen

**VERIFIZIERT:** 21 gezielte DOM-Prüfungen ohne Fehler, Original-GLB mit tatsächlichem GLTFLoader geparst, Morph-/Blick-/Sprechvertrag geprüft, Original-Kugel in sechs Zuständen und 18 Rasterbildern geprüft, Blender-Front- und Rückansicht des separaten Displays tatsächlich gerendert. Die Einzeldatei enthält alle notwendigen Laufzeitassets und bewahrt die Modellprüfsumme.

**PORTIERT:** visuelle Webfassung und Darstellungsadapter. Kein Android-Backend, kein Mikrofon und keine TTS-Verbindung; Argon4 ist eine gekennzeichnete Demonstration. Der separate Display-GLB wird im Web noch durch einen bedienbaren CSS-Displaykörper dargestellt.

**GERÄTEBESTÄTIGT:** nicht behauptet. Kein Fold7-GPU-, thermischer, Laufzeit- oder hörbarer Lippensynchronitätstest. Kein APK gebaut oder installiert, keine Signaturänderung, keine Änderung an den bestehenden Android-Renderern oder Experimentstatus.

Die native Figma-Datei zeigt den früheren Entwurf. Ihr späterer Codeupload wurde mit HTTP 400 abgewiesen und hat keinen Code-Layer erzeugt. Aktuelles Grid und Originale liegen in dieser Webfassung.

## Weiterarbeiten

- `Continuum_Designuebergabe.md`: Gestaltung, Licht- und Bewegungsentscheidungen.
- `Continuum_Toolkette_und_Uebergabe.md`: kostenlose Werkzeuge, Importwege und fertige Arbeitsanweisungen.
- `interop/README.md`: reproduzierbare Builds und Prüfungen.
- `handoff-3d/`: Blender-Builder, Herkunft, Displaystruktur und Importanweisungen.
- `tools/build-standalone.py`: eigenständige HTML-Datei erstellen.
- `tools/package-continuum.py`: vollständige Projekt- und Webexportarchive erstellen; dafür werden die gerenderten 3D-Dateien und die Raumstudie aus dem Downloadpaket oder den dokumentierten Buildern benötigt.

Die Änderungen bleiben auf einem eigenen Designbranch; dieser PR ist ein Entwurf für Prüfung und spätere Integration.

## Präsenz 02

Der genehmigte Stil und beide Schriften bleiben erhalten. Die Originalkugel wird im festen Modus einmal je Layout/Qualität gerastert und reagiert nur mit weicher Belichtung. Der Avatar bleibt proportional, mit neutralerem Gesichtslicht und Blick auf tatsächlich ausgewählte Diagrammpunkte. Neue SVG-Hologramme teilen sich die Phasendaten mit dem bestehenden Arbeitsraum; das Layout folgt der verfügbaren Bühnenbreite. Drei atmosphärische Tiefen, schräge Lichtstrahlen und sechs langsame Lichtadern bewegen den Hintergrund. Die Module sind unter `presence-layout.css`, `presence-instruments.css` und `presence-instruments.js` getrennt.
