# Originalavatar-Replay: integrierte Quelle 8c851a09

**HOST_BLENDER_NOT_FILAMENT_DEVICE**

Neun unveränderte Frames aus der grünen Host-Zeitreihe wurden auf dem originalen GLB dargestellt. Blender 4.3.2, Cycles CPU, ein Thread, 384 × 448 Pixel, acht Samples; keine Bildnachbearbeitung.

## Quellenbindung

- Öffentlicher Commit: `8c851a09d7c641bdfc02d5e2d2c432cd3fda0e8c`.
- Die acht gebundenen Avatar-, Renderer-, Framing- und Testdateien stimmen mit diesem Commit, dem unveränderten Testlauf und den Hashes vor/nach dem Replay überein.
- Der Testbeleg begann bei `7159aba8ca29d71d779e7b1b83efd00a3bee0b3f` mit dem später eingecheckten WorkWindow-Delta. Er wird nicht als sauberer 7159-Stand bezeichnet.
- Das Gesamt-Checkout war beim Rendern verändert; die Bindung gilt ausdrücklich für die geprüften Dateien. Parallele ShellLayout-Arbeit gehört nicht zum Avatar-Nachweis.
- Original-GLB: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
- Finaler Anschlusscommit: `7c3523cde9dae302a52d69818867a9abe8cf0fb7`.
  [source-final-binding.json](source-final-binding.json) bestätigt dieselben acht
  Abhängigkeiten und alle neun unveränderten PNGs. Das ist eine erneute
  Quellenprüfung des bestehenden Replays, kein neuer Renderlauf.
- Timeline: `c73c222cc552104b826f2b662046290cb7cec02bfb36380fc372c8f7ecece56c`.
- AvatarMotionEvidenceTest-XML: `73b4b7544518084081e7fd5ea28bcba5edaeedf00cf1ea8144f4e62513a01d73`.
- Replay-Skript: `959bb822f6ce389d4fddefd45196a7cf2595fe8d71f4a970369c9dd3fb854d47`.

## Prüfung

- Archivierte integrierte XMLs: **196 Tests, 0 Fehler, 0 Ausfälle, 0 übersprungen**.
- 21 Morphgewichte und die bestehenden Neck-/Head-/Augen-/Kiefertransformationen aus der Zeitreihe angewandt. Neck-Anteil unverändert.
- Größte absolute Abweichung der geprüften Transformationsmatrizen: `3.25210692154e-07`.
- Originalmaterialien unverändert; vollständiger Kopf in allen neun Porträtframes. Alle neun PNGs visuell geprüft.
- Sprechframe zeigt einen offenen Mund; erster STOP-Frame zeigt geschlossene Lippen. Bewegungstiming wird durch die Quellzeitreihe und Tests belegt.
- Frühere Replay-Dateien behalten ihre Manifest- und PNG-Hashes.

## Bilder

| Datei | Quellframe | Zeit (s) |
|---|---:|---:|
| [01-ruhe.png](01-ruhe.png) | 45 | 1.485 |
| [02-zuhoeren.png](02-zuhoeren.png) | 140 | 4.620 |
| [03-denken.png](03-denken.png) | 206 | 6.798 |
| [04-fenster-augen-vor-kopf.png](04-fenster-augen-vor-kopf.png) | 237 | 7.821 |
| [05-fenster-kopf-folgt.png](05-fenster-kopf-folgt.png) | 258 | 8.514 |
| [06-bestaetigung-nicken.png](06-bestaetigung-nicken.png) | 269 | 8.877 |
| [07-bestaetigung-ende.png](07-bestaetigung-ende.png) | 283 | 9.339 |
| [08-sprechen.png](08-sprechen.png) | 339 | 11.187 |
| [09-stop.png](09-stop.png) | 501 | 16.533 |

## Grenzen

Die Eingaben für Kamera, Fenster, Bestätigung und TTS sind geskriptete Host-Eingaben. Die Bilder belegen die Anwendung dieser geprüften Produktionsposen auf die unveränderte Originalgeometrie. Sie belegen keinen Gerätelauf, kein echtes PCM und keine Filament-Ausgabe. Beleuchtung, Farbverarbeitung und Kamera stammen von Blender; Cockpit, native Aura und Dialogüberlagerung werden hier nicht dargestellt.

## Belege

- [replay-manifest.json](replay-manifest.json): vollständige Mapper-Frames, 21 Gewichte, Matrizen und PNG-Hashes.
- [source-binding.json](source-binding.json): Commit-/Test-/Quelldateibindung.
- [verification.json](verification.json): erneute Hashprüfung, Testsumme und alte Replay-Integrität.
- [render.log](render.log): vollständiges Renderprotokoll.
