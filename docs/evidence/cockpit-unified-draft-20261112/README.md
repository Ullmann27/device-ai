# Gemeinsame 3D-Szene – erste Sichtprobe

**HOST_UNIFIED_3D_COMPOSITE · DRAFT · 8 Samples**

Geprüfte Produktionsquelle: `4b5663b8e20d74a10922c8db7bc07c3954a5a9bd`.
Die Quelle blieb während des gesamten Laufs sauber und unverändert.
Dies ist der erste Sichtvergleich nach dem Wechsel auf die gemeinsame
3D-Szene, kein Android- oder Fold7-Gerätebeleg.

## Bilder

- [Fold, Ruhe](fold-idle.png), 884 × 830 Pixel einschließlich Nachweiszeile.
- [Phone, Ruhe](phone-idle.png), 411 × 1030 Pixel einschließlich Nachweiszeile.

Workstation, Originalavatar und gepackte Kugel teilen sich eine Perspektivkamera
und den Tiefenraum. Die Produktions-API `CockpitWorldTransforms` liefert ihre
Matrizen; der Monitor verwendet `CockpitStageFrame.monitorTransform()`.
Hintergrund, Header, Badges und Seitenfenster verwenden die Produktions-Painter.
Der sichtbare Dokument-/Dateiinhalt ist ausschließlich ein gekennzeichnetes
Hostbeispiel. Er wird auf das tatsächliche `monitor-live-pixels`-Mesh gelegt,
ohne Farbtönung oder zusätzliche Glasmaske.

## Nachweis

| Prüfung | Ergebnis |
| --- | --- |
| Tatsächliche Kotlinquellen kompilieren und exportieren | PASS |
| Gepackte Assetgrößen und SHA-256-Pins | PASS |
| Gemeinsame Prüfsperre für Blender | verwendet |
| Projektion der vier echten Mesh-Ecken gegen Kotlin-Quad | max. 0,0003381 Pixel |
| Avatar bei Opazität 1 | Laufzeitbytes exakt gleich Originalbytes |
| Sechs im Manifest verzeichnete Ausgabedateihashes | PASS |
| Quellen während des Laufs unverändert | PASS |
| Filament / Fold7 / Geräteframerate | NOT_RUN / NOT_RUN / NOT_MEASURED |

Asset-SHA-256:

- Workstation: `03b6364e2ff396fd9207b129e765ce5d4a93975113315cc3896844e53fd8ad30`
- Kugel: `ba4bd673e2196fcd8d970b3c83531ea26e20047ae530f092d0ef122808d6e161`
- Originalavatar: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`

Der vollständige Quellenbezug steht in [preview-manifest.json](preview-manifest.json),
die tatsächlichen Projektionswerte in [blender-manifest.json](blender-manifest.json).
Layouts und exportierte Weltmatrizen liegen daneben. Die temporären rohen
Blender-Ebenen sind im Renderprotokoll gehasht; persistiert sind die fertigen
Gesamtbilder und ihre Nachweise.

## Reproduktion und Grenzen

Die [Pipeline-Anleitung](../../../tools/cockpit-workstation/README.md) beschreibt
die benötigten lokalen Werkzeuge und Parameter. Für diesen Lauf:
`--samples 8 --draft --content example --qa-lock "$COCKPIT_QA_LOCK"`.
Blender 4.3.2, Cycles CPU, zwei Threads, Seed 731, kein Denoising.

Die Beleuchtung übernimmt native Lichtfarben und Positionen; die
Blender-Intensitäten sind eine Hostannäherung. Der Avatar zeigt die neutrale
Originalpose. Android-View-Lifecycle, App-Capture, Sprache, Modell, Lippen- und
Augenbewegungen wurden nicht ausgeführt. Die Ruheansichten zeigen Ringwinkel
bei Zeit null; diese beiden Bilder belegen noch keinen Animationsablauf.
Diese Drafts dienen dem visuellen Entscheid vor den finalen Stills und dem Clip.
