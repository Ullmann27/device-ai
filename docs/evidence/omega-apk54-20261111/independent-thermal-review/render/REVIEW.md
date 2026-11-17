# Unabhängiger Render-Review #773

**Status: BEGRENZTES QUELLREVIEW-OK. Kein neuer Testlauf, kein Gradle, keine Produktionsänderung.**

- Geprüfter Diff: `9a6de6c4547feb62c9822520596e786a852ed83b` → `1ba7b9458fd47184cedaed18e94e9aae1bd76c39`.
- Aktueller Kandidat: `8decff576dc05ed3cdb0b8ded680b6098a1d8537`.
- Alle 17 im Manifest erfassten Render-/Test-/Werkzeug-/Gate-Dateien am aktuellen Kandidaten erneut byteweise verglichen.

## Ergebnis

Keine konkrete Produktionsregression im untersuchten Render-Diff gefunden.

- `JarvisEnergyFlows`: gleiche Float-Ausdrücke/Reihenfolge, Bézier-Geometrie, Phasen, Farben, Linienbreiten, Alphawerte und Pulse. Die acht Kontrollwerte werden einmal je Linie statt für jeden Punkt erneut berechnet.
- `JarvisHeadAura`: zwei statische Pfade und ein Verlauf werden wiederverwendet; beide exakten Float-Maße bestimmen die Cache-Erneuerung. Keine Activity-/Canvas-/Surface-Referenz im Cache.
- Android-Painter: Identitäts-/Versionscache bleibt kompatibel; Transformationen erfolgen weiterhin am Canvas. Frame-Uhr und sämtliche Motion-/Lifecycle-/Budget-Gates sind unverändert.
- EXP06/11: Settings, Aufrufer, Filament-Renderer, Fensterlichtzustand, Host und Overlay bleiben gegenüber der geprüften Basis unverändert.

## Eingesehene Evidenz

- Roter Lauf: 9 PASS / 1 FAIL; grüner Lauf: 10 PASS / 0 FAIL.
- 72 echte Flow-Draw-Aufrufe: maximal 1.526 → 70 gezählte Math.sin-Aufrufe pro Helferaufruf.
- Gleicher vollständiger serialisierter Painter-Befehls-Hash: `33f640d7a6108c7245cf355eeb3e62131ad327d8fb11944e9ad2d73dd82dccd7`.
- Sieben grüne Manifestquellen stimmen mit dem geprüften Commit überein. Vier rote Produktionsquellen stimmen mit der Diff-Basis überein; Tests/Probe wurden gegen ihre geprüften Kandidatenbytes abgeglichen.
- Unveränderte Originalmanifeste und JUnit-/Work-Logs liegen unter `reviewed-evidence/`; dieser Review hat sie nicht neu erzeugt.

## Grenzen

- Die 72 Fälle betreffen **JarvisEnergyFlows**, nicht HeadAura, komplette Android-Bilder oder GPU-Ausführung. HeadAura: vier neue Wiederverwendungs-/Geometrietests plus Quellvergleich.
- Das vorhandene Manifest hasht Quellen und Bibliotheks-JARs; bereits kompilierte App-Abhängigkeiten sind nur durch ihren Verzeichnispfad bezeichnet.
- Kein Nachweis für Framezeiten, Leistungsaufnahme oder geringere Gerätetemperatur. Gemeinsame native Screenshot-Abnahme, Android-Gates und Fold7/GPU/Thermik bleiben offen.

## Quellhashes

| Datei | Geprüfter SHA256 | Aktueller Kandidat gleich? |
|---|---|---|
| `app/src/main/java/at/heinz/deviceai/ui/JarvisEnergyFlows.kt` | `8303d3709e293bb2b4f0bb2a2bde1ec08f48132233f2ba294e076c96199f31de` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/JarvisHeadAura.kt` | `89a4991fed11057832f44ed1f80e45b8e52ca4b2a56f8eabeaa41574a701a5d9` | JA |
| `app/src/test/java/at/heinz/deviceai/ui/JarvisLightStreamReuseTest.kt` | `c92259c037e05d1336fef9239b7cf5651c47ada1ddce13b44517818dce85999e` | JA |
| `app/src/test/java/at/heinz/deviceai/ui/JarvisLivingSceneTest.kt` | `dcdb3fe03c8f5a8cccea52575393aeb749f3d914f445a16db6b0544519783445` | JA |
| `tools/thermal-render/FlowWorkProbe.kt` | `e908c8f63c11c7c89e3127e5dbfd1dfcefbec7901623efc0c647f3ae3aa67424` | JA |
| `tools/thermal-render/run.py` | `e5dc4ecf0e047346d10f176bde095f734d6d152b4738379f31f9143cdbf13d4a` | JA |
| `docs/research/omega/THERMAL-RENDER-HANDOFF-20261111.md` | `d21aedc1a4d9401bb84a6409612aa7054f5374480349a6331d17c8cdb89ca51c` | JA |
| `app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt` | `3cef9cc211471ee3ad99b211bc70dbff9d7d76db02893dbe207b5a9662401b1a` | JA |
| `app/src/main/java/at/heinz/deviceai/avatar3d/FilamentAvatarRenderer.kt` | `a3a14e5777f3bccc0042f34116dda7521ef63d9d3b2e82367017aac326c08304` | JA |
| `app/src/main/java/at/heinz/deviceai/avatar3d/CinemaWindowLightState.kt` | `83ba386b6a00f3c3b07f2b229192bf2ce0998aa1ea609efb29f47e9fbd2ad4b6` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/JarvisWorkWindowHost.kt` | `f9e7e62196a6f142ad26a8292f7b2a2823a18afc23a1f2619fb0cb1eb43131a7` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiStageOverlay.kt` | `5ba470bc0cdc14ad4c459e2b3d22249fbf09cf525cae6e29e3f6241346ae9982` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/JarvisCinematicSettings.kt` | `c76dd4ac0ff091fac48824f12a2ce1950e1a025ae729e3a3e775c35be7322810` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/AndroidContinuumPainter.kt` | `156889f9e9a6952578d8e326782b9d47c4b17a2f5153a9665a46a1afacc40e11` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt` | `9f3d0b53d4c68581332be7ceb75bf9be0d7ebc41e3ae41a21ce21a2d7ae5164d` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/JarvisDesign.kt` | `0f20bceefe13a6a0d135c6a4182a6f1e5f47d45e591801993aa4ca4825f854e2` | JA |
| `app/src/main/java/at/heinz/deviceai/ui/JarvisSceneFills.kt` | `2c2f4fc578d6af6a6494e99fa0e2f710f63cd1ab2acf7048cef03da51fc1c887` | JA |

Vollständige Basis-/Kandidatenhashes und Einzelabgleiche: `review.json`.
