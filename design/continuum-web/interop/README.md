# Continuum – portable Asset-Adapter und Prüfwerkzeuge

Die Dateien unter `continuum-preview` laufen ohne NPM-Installation. Die folgenden Entwicklungswerkzeuge sind für den erneuten Aufbau und die gezielte Prüfung der Original-Asset-Adapter vorgesehen.

## Installation

Aus dem obersten Projektordner:

```sh
npm ci --prefix interop
```

Benötigt wird eine von jsdom 31 unterstützte Node-Version: **22.22.2 oder neuer innerhalb von 22.x, 24.15.0 oder neuer innerhalb von 24.x, oder mindestens 26.0.0**. Das Lockfile enthält die konkreten Paketversionen.

Die DOM-Prüfung verwendet `jsdom@31.0.1`. Dessen optionaler Canvas-Peer benötigt mindestens die 1.x-Generation und ist mit `@napi-rs/canvas@1.1.1` gepinnt. Der Original-Kugelrenderer behält unabhängig davon seine tatsächlich geprüfte Version `@napi-rs/canvas@0.1.100`, installiert unter dem NPM-Alias `continuum-native-canvas`. So entsteht kein Peer-Konflikt und es werden weder `--force` noch `--legacy-peer-deps` benötigt.

Die Skripte lösen ihre Pakete zunächst regulär aus `interop/node_modules` auf. Nur bei einem fehlenden direkten Paket und ausdrücklich gesetztem `CODEX_PRIMARY_RUNTIME_NODE_MODULES` ist ein Fallback auf diese vorhandene Entwicklungsumgebung möglich. Ein fest eingebauter Workspace- oder Runtime-Pfad wird nicht benötigt.

## Gezielte Befehle

```sh
npm run verify:avatar --prefix interop
npm run verify:orb --prefix interop
npm run verify:dom --prefix interop
```

Die Kugelprüfung verwendet einen nativen Canvas-Rasterizer. Die DOM-Prüfung verwendet jsdom. Beides ist kein Browser-, GPU- oder Android-Gerätetest.

Die dokumentierte Offline-Raumstudie erneut rendern:

```sh
npm run render:room --prefix interop
```

Für die Videoausgabe dieses Renderers muss **FFmpeg** auf dem ausführbaren Suchpfad liegen. Der Renderer erstellt seine Ausgaben relativ zu diesem Projekt; er benötigt keine ursprünglichen absoluten Rechnerpfade.

Die Avatar-Webintegration erneut bündeln:

```sh
npm run build:avatar --prefix interop
```

Die originale Avatar-Datei liegt unter `continuum-preview/assets/continuum-avatar.glb`. Herkunft, Integritätsprüfung und Blender-Aufruf sind in `../handoff-3d/Originalavatar_Import_und_Webintegration.md` dokumentiert. Das zusätzliche Arbeitsdisplay besitzt ein eigenes reproduzierbares Skript und eine eigene README unter `handoff-3d`.

## Präsenz 02

Die neue Mitte verwendet eine feste Originalkugel hinter dem proportional eingepassten Avatar. Die seitlichen SVG-Instrumente beziehen Werte und Auswahl aus derselben App-Datenquelle. Zusätzliche gezielte Befehle:

```sh
npm run verify:orb-fixed --prefix interop
npm run verify:presence --prefix interop
npm run render:presence --prefix interop
```

Die QA-Raster werden von den Prüfskripten neu erzeugt; im Downloadpaket liegen ihre Berichte und Quellen. Der Film zur zentralen Anordnung ist eine native Canvas-/SVG-Raumstudie mit ruhig gerendertem Originalkopf, kein Browserrecording.
