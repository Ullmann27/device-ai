# Continuum – Originalavatar, Licht und Webintegration

## Geprüfter Ursprung

Die verwendete Datei ist **unverändert** aus `Ullmann27/device-ai`, Commit `1b90611388cc2b1978ddfe32064a25dd3a08cee1`, Pfad `app/src/main/assets/avatar3d/continuum-avatar.glb`.

| Prüfung | Ergebnis |
|---|---|
| Dateigröße | 12.038.416 Bytes |
| SHA-256 | `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a` |
| Git-Blob-SHA-1 | `3498c003a8bf186a8abb47048e2d36be4810b88c` |
| glTF-Koordinaten | Y nach oben, Front nach +Z, Maßstab in Metern |
| Blender-Import | Z nach oben, Front nach −Y |
| Modellbestand | 31 Knoten, 25 glTF-Meshes, 26 gerenderte Mesh-Primitiven, 8 Materialien |
| Skelett | `AvatarRoot → Neck → Head`; Augen und Kiefer unter `Head` |
| Morphziele | 21 benannte Ziele in der Gesichtsfläche sowie beiden NeuralThreads-Primitiven |
| Eingebettete Animationsclips | Keine |

Die byteidentische Vorschaukopie liegt in `continuum-preview/assets/continuum-avatar.glb`. Die Herkunftsdatei `continuum-avatar-NOTICE.json` liegt daneben. Es wurde kein Ersatzkopf, keine neue Textur und kein neues Rig erzeugt. Die ursprüngliche NOTICE-Bewertung der Referenztreue bleibt bestehen; der Import ist keine neue Identitätsfreigabe.

## Blender-Datei und Bild

`Continuum_Originalavatar_Lichtstudie.blend` enthält das echte importierte Modell samt gepackten Texturen, einer orthografischen Kamera und vier zusätzlichen Studiolichtern. `Continuum_Originalavatar_Beleuchtung.png` ist ein tatsächlich mit Blender Cycles auf der CPU gerendertes, transparentes RGBA-Bild mit 1280 × 960 Pixeln und 128 Samples. Der installierte Blender-Build enthält kein OpenImageDenoise; das Bild wurde deshalb ohne diesen Nachbearbeitungsschritt berechnet.

Das Skript verändert keine importierten Geometrien, Materialien, Texturen oder Morphziele und exportiert keine neue GLB. `Originalavatar_Diagnose.json` dokumentiert Quellhash, Struktur, importierte Daten und denselben Mesh-/Morph-Fingerprint vor und nach Aufbau der Beleuchtung.

Erneut ausführen, aus dem Projektverzeichnis:

```sh
blender --background --factory-startup -t 4 \
  --python handoff-3d/inspect_original_avatar.py -- \
  --source continuum-preview/assets/continuum-avatar.glb \
  --output handoff-3d --render
```

Die GLB-SHA muss exakt passen, sonst bricht das Skript ab. In Blender können ausschließlich Kamera und die separat benannten Lichter bearbeitet werden, ohne das Originalmodell anzutasten.

## Webadapter

`interop/avatar3d-source.js` lädt die Original-GLB mit Three.js 0.187.0. `interop/avatar-motion.js` bildet Augen, Hals, Kopf und vorhandene Morphziele ab. Die gepinnte lokale Abhängigkeit und der esbuild-Build stehen in `interop/package.json` und `package-lock.json`.

```sh
cd interop
npm ci --no-audit --no-fund
npm run build:avatar
node verify-avatar-contract.mjs
```

Die HTML-Seite benötigt `.avatar-zone .avatar-crop` und lädt `avatar3d.js` nach `scene.js`. Das transparente Canvas bleibt unsichtbar, bis das Originalmodell geprüft, geladen und mindestens einmal erfolgreich gerendert wurde. Die Seite kann schon währenddessen den ruhigen PNG-Avatar zeigen. Bei fehlendem WebGL2 oder einem verlorenen Kontext bleibt das echte Blender-PNG sichtbar; Atmosphäre und Kugel laufen unabhängig weiter.

```html
<script defer src="scene.js"></script>
<script defer src="avatar3d.js"></script>
```

Für eine separat erzeugte Ein-Datei-Vorschau können vor dem Avatar-Skript zwei Data-URLs gesetzt werden:

```js
window.CONTINUUM_ASSETS = {
  avatarModel: originalGlbDataUrl,
  avatarFallback: originalBlenderPngDataUrl
};
```

Ohne diese Angaben lädt der Adapter `assets/continuum-avatar.glb` und `assets/continuum-avatar-original.png`. Auf HTTPS/localhost prüft er zusätzlich den SHA-256 mit WebCrypto; andernfalls prüft er Dateigröße, Knoten und Morphvertrag und kennzeichnet den fehlenden Laufzeit-Hash in `getStatus()`.

## Bewegung und Sprache

Der Kopf nutzt die bestehenden Originalknoten. Wie im Code55-Mapper tragen Hals und Kopf 65 % beziehungsweise 35 % der gemeinsamen Drehung. Kritisch gedämpfte Augen folgen schneller als der Kopf. Unregelmäßige Blickziele und Blinkabstände, kleine Haltungsänderungen sowie langsame Reflexionsbeleuchtung verwenden voneinander unabhängige Zeitabläufe. Der Zeitgeber läuft bei Zustandswechseln weiter.

Der Originalbestand ist eine Büste mit Hals-/Kopf-Rig. Eine vollständige Körper-, Haar- oder Stoffsimulation wird damit nicht behauptet. Das ruhige Heben der Büste und die kleine Halsbewegung vermitteln Präsenz; die Originalgeometrie bleibt erhalten.

Ein Zustand `speaking` allein bewegt den Mund **nicht**. Echte Sprachereignisse können gezielt angeliefert werden:

```js
ContinuumAvatar.setSpeechFrame({
  viseme: 'AA',       // Originalname: SIL, PP, FF, TH, DD, KK, CH, SS, NN, RR, AA, E, I, O, U
  weight: 0.8,       // 0…1, aus dem tatsächlichen Sprachereignis
  jawOpen: 0.7,      // 0…1, tatsächliche Öffnung
  durationMs: 160    // 20…500; alte Ereignisse verfallen automatisch
});
ContinuumAvatar.clearSpeech();
```

Auch `continuum:viseme` und `continuum:speechend` werden akzeptiert. Der Adapter überträgt die ursprüngliche 70-ms-Überblendung und kompensiert bereits im Visem enthaltene Mundöffnung. P/B/M schließen den Mund auch bei hoher übermittelter Lautstärke. Es ist keine Spracherkennung oder neue Stimme eingebaut.

Weitere Schnittstellen:

```js
ContinuumAvatar.setState('working');
ContinuumAvatar.setGaze(0.48, -0.28, 2200);
ContinuumAvatar.resize();
ContinuumAvatar.getStatus();
```

Der Adapter hört `continuum:scenechange` mit `{state, intensity}` und `continuum:sceneresize`. Zustände sind `idle`, `listening`, `forming`, `working`, `speaking` und `result`. `continuum:avatarchange` meldet den aktiven Renderer, Integritätsstatus und tatsächliche Bildanzahl.

## Tatsächlich geprüft und Grenzen

Die Blender-Szene wurde ausgeführt und das PNG visuell geprüft. Das Original wurde mit dem tatsächlichen Three.js-GLTFLoader vollständig eingelesen; alle drei Morph-Primitiven, Knoten und acht Materialien wurden verifiziert. Der reproduzierbare Vertragstest prüft die Byteidentität, Stille ohne echte Sprachdaten, Visemkompensation, P/B/M-Schluss, Verfall alter Ereignisse, Augen-vor-Kopf sowie die echte gebündelte Fallback-Ausführung ohne WebGL.

Diese Prüfungen ersetzen keinen Android-Filament-Gerätetest und keinen WebGL-Leistungstest auf einem Fold. Der Blender-Render verwendet Cycles; der Webadapter verwendet Three.js und dieselben Originalmaterialien. Lichter und Tonemapping sind Darstellungseinstellungen der jeweiligen Szene. Die native Android-App, ihre Signatur und ihre Sprachpipeline wurden hierdurch nicht geändert.
