# Astra55: Host-Szenenprüfung, 12.11.2026

**Kein Android-Screenshot, keine Live-App-Aufnahme, kein Fold7-Gerätetest.**

| Datei | Gesamtes Layout | Gerenderter Szenenausschnitt | Verfahren |
|---|---|---|---|
| `fold-host-scene.png` | 884 × 760 dp | 852 × 480 dp, zweifache Ausgabeauflösung | Original-GLB + echte Szenengeometrie, Cycles CPU |
| `phone-host-scene.png` | 412 × 915 dp | 388 × 531 dp, zweifache Ausgabeauflösung | Original-GLB + echte Szenengeometrie, Cycles CPU |

Die Bildunterschrift gehört zum Nachweis, nicht zur App. Die native Dock- und Eingabeleiste liegt außerhalb
dieses Ausschnitts und wird separat durch die tatsächliche Android-Canvas-Oberfläche geprüft.

## Beobachtet

- Originale Avataridentität; die Kugel kommt aus dem unveränderten Kotlin-Renderer.
- ∞-Zeichen hinter der Stirn; Avatar-Deckkraft 0,62.
- Weitere Kamera zeigt Kinn und Schulteransatz oberhalb des aufrechten Monitors.
- Dachstreben schneiden den Kopf auch im schmalen Layout nicht.
- Der Monitor ist leer: keine erfundenen WhatsApp-, Facebook-, Datei- oder Live-Inhalte.

## Quellen

Originalavatar SHA-256:
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`

Eigene Cockpit-GLB SHA-256:
`563d4ab95ce98d603bd48d68c48abf2d709748a604eea7cf8bfd9a43d06b2193`

`host-render-geometry.json` enthält Größen, Kamerawerte und den Hash der zugehörigen Layoutquelle.
`source-inputs.sha256` bindet weitere Produktionsquellen. Reproduktion: `tools/cockpit-workstation/README.md`.

## Prüfung und Grenzen

`CockpitStageFrameTest`: **3/3 bestanden**, echte Produktionsquelle mit Kotlin 2.0.20, JDK 17 und JUnit 4.13.2.
Geprüft: nicht gespiegelte Projektion an beiden Yaw-Grenzen, begrenzte Puffergröße bei gleichem Seitenverhältnis,
Ablehnung ungültiger Geometrie. Siehe `stage-tests.txt`.

Das Rendern selbst wurde in beiden Größen abgeschlossen und visuell geprüft. Host-Licht und Schatten sind keine
Filament-Abnahme. Die Android-Gesamtsuite wird im Integrationszweig geführt. Auf dem Fold7 fehlen weiterhin die
Prüfung von GPU-Komposition, Bildfreigabe, tatsächlichen App-Fenstern, Klick-/Scroll-Wirkung, Bildrate und Temperatur.
