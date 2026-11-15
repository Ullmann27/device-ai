# Continuum: Port des tatsächlichen Original-Kugelrenderers

## Herkunft

Quelle ist `Ullmann27/device-ai`, festgelegt auf Commit `1b90611388cc2b1978ddfe32064a25dd3a08cee1` des Zweigs `claude/code55-cinematic-evolution-20261112`.

Der vorhandene `ContinuumHologramRenderer.kt` zeichnet über `ContinuumPainter`. Er ist kein GLB-Modell und enthält keinen GLES-Shader. Die Webfassung überträgt diesen Zeichenvertrag auf Canvas 2D. Sie ersetzt die Kugel nicht durch ein neu entworfenes Modell.

[Original-Renderer auf GitHub](https://github.com/Ullmann27/device-ai/blob/1b90611388cc2b1978ddfe32064a25dd3a08cee1/app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt)

Die acht vollständigen Quelldateien unter `originals/` wurden gegen ihre Git-Blob-Hashes geprüft. `orb-port-provenance.json` verzeichnet Pfad, Quell-URL, Git-Blob und SHA-256 jeder Datei.

## Übernommene Identität

Der Port enthält die fünf äußeren räumlichen Orbitalringe, zwei Kernringe, das Unendlichkeitszeichen, die ursprünglichen Farbstopps, Glasflächen, Lichtbögen, den mehrteiligen Sockel und die perspektivische Aufteilung vor und hinter der Kugel. Die Welt verwendet die bereits vorhandene Geometrie `REFERENCE_COCKPIT`: 128 äußere und 48 innere Netzknoten sowie 48 Haloknoten; insgesamt 224 Knoten, 381 Verbindungen und 420 Staubpartikel im Port.

Die vier originalen Layoutprofile `PHONE`, `FOLD`, `FOLD_PORTRAIT` und `REFERENCE_COCKPIT` bleiben im lesbaren Modul verfügbar. Das App-Layout nutzt `REFERENCE_COCKPIT` in beiden Bildschirmformaten, passend zur gemeinsamen Bühne von Kopf und Kern.

Die sechs originalen Zustände bleiben erhalten. In der App werden `idle` und `result` auf Bereitschaft, `listening` auf Zuhören, `forming` und `working` auf Denken sowie `speaking` auf Sprechen abgebildet. `research` und `learning` können ausdrücklich durch tatsächliche Arbeitszustände gesetzt werden.

## Bewusste Web-Anpassungen

- Die Leinwand ist transparent und folgt dem tatsächlichen Rechteck von `[data-orb-anchor]`. Ein transparenter Rand verhindert abgeschnittene Aura- und Bodenreflexionen.
- Der Raum und die Verbindungen bleiben in `scene.js`. Seine frühere Ersatzkugel ist standardmäßig unsichtbar und wird bei einem Fehler nicht reaktiviert.
- Die deutsche Beschriftung bleibt im DOM; die zusätzliche Original-Brandingzeile des Kugelrenderers wird in der eingebetteten App nicht doppelt gezeichnet.
- Der Browser behält die originale Phasengeschwindigkeit bei, lässt den double-genauen Phasenwert aber über 1000 hinaus weiterlaufen. Dadurch beginnen die Bewegungsebenen bei langen Sitzungen nicht gleichzeitig neu. Geometrie und Ringkonfiguration bleiben unverändert.
- Der Original-Audiofilter ist übertragen: 35 ms Anstieg, 150 ms Abfall und 180 ms Gültigkeit einer eingegangenen Probe. Es gibt keine erfundenen Sprachpegel. Ohne echte Pegel werden keine sprechenden Wellenformen erzeugt.
- Der Regler für Lichtintensität wirkt als gemeinsame Belichtung auf die Leinwand. Er verändert keine einzelnen Original-Farbstopps. Die Qualitätsstufe verändert die Auflösung; sie entfernt keine Ringe oder Netzelemente.
- JavaScript verwendet überwiegend double-genaue Zwischenrechnungen und Float32-Geometriepuffer. Canvas-Rasterung, Antialiasing und Alpha-Quantisierung können von Android abweichen. Eine pixelidentische Android-Darstellung wird nicht behauptet.

## Dateien und Einbindung

`orb-original-source.js` enthält den lesbaren Renderer, den Audiofilter, die Uhr und den Browseradapter. `../continuum-preview/orb-original.js` ist die lokal mit esbuild erzeugte, minifizierte IIFE. Die Laufzeit benötigt keine externe Bibliothek und kein CDN.

Skriptfolge in der App:

```html
<script src="scene.js"></script>
<script src="orb-original.js"></script>
<script src="avatar3d.js"></script>
<script src="app.js"></script>
```

Der Adapter startet nach dem DOM-Aufbau und veröffentlicht `window.ContinuumOriginalOrb` mit `getStatus()`, `resize()`, `setState(state)`, `setAudioLevel(level)` und `dispose()`.

Der vorhandene Raum veröffentlicht `window.ContinuumScene`. Dessen `setState`, `setQuality`, `setIntensity` und `setAudioLevel` senden `continuum:scenechange` mit `{state, quality, intensity, audioLevel, audioSampleAt}`. `resize()` sendet `continuum:sceneresize`. Der Original-Avataradapter kann dieselben Ereignisse verwenden.

`assets/continuum-orb-original.png` ist ein transparentes Rasterbild, das mit genau diesem Original-Port erzeugt wurde. Es dient ausschließlich als Original-Fallback, wenn Canvas 2D nicht verfügbar ist. Alternativ unterstützt die Einzeldatei-Fassung `window.CONTINUUM_ASSETS.orbFallback` als Daten-URL.

## Erstellung und Prüfung

```sh
npm ci --prefix interop
node interop/verify-orb-original.mjs
interop/node_modules/.bin/esbuild interop/orb-original-source.js --bundle --minify --format=iife --target=es2020 --legal-comments=eof --outfile=continuum-preview/orb-original.js
node --check continuum-preview/scene.js
node --check continuum-preview/orb-original.js
```

Der Prüfablauf benötigt für die Rasterbilder `@napi-rs/canvas` in der getesteten Version 0.1.100 (NPM-Alias `continuum-native-canvas`). Es ist als exakt gepinnte Entwicklungsabhängigkeit unter `interop` enthalten und wird nach `npm ci --prefix interop` regulär aufgelöst. Nur wenn das Paket dort fehlt und `CODEX_PRIMARY_RUNTIME_NODE_MODULES` ausdrücklich gesetzt ist, wird dieser Laufzeitpfad als Fallback verwendet. Es ist keine Laufzeitabhängigkeit der App.

Prüfergebnisse liegen unter `qa-orb/verification.json`. Abgedeckt sind geschlossene Tiefenabschnitte der Ringe, feste Netzverbindungen bei räumlicher Bewegung, alle sechs Zustände, echte Audiowerte samt Ausklingen, fortlaufende Phasen beim Zustands- und Layoutwechsel, 18 native Rasterframes sowie der DOM-Vertrag für Anker, Ereignisse, Aufräumen und Original-Fallback. Das Kontaktblatt `qa-orb/original-orb-six-modes.png` wurde visuell geprüft.

Diese Prüfung verwendet Node und einen nativen Canvas-Rasterizer. Sie ist kein Browser- oder Android-Gerätetest und belegt keine Smartphone-Bildrate.

## Präsenz 02: feste Kugel hinter dem Avatar

Die zentrale Bühne setzt `data-orb-motion="fixed"`. Der originale Renderer zeichnet seine Bereitschaftsgeometrie einmal je Größe/Qualität. Ringwinkel, Netz, Radius und Schwebeoffset bleiben fest; eine getrennte Belichtung reagiert weich auf Zustände. `setMotionMode("live")` bleibt für andere Szenen verfügbar. Canvas und Original-Fallback liegen lokal im Orb-Anker unter der Avatar-Ebene. Die Atmosphäre bewegt sich unabhängig weiter. Die enge Prüfung steht in `qa-orb-fixed/verification.json`.
