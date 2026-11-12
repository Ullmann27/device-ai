# Code55 Renderpipeline – Integrationscheckpoint

Verantwortlich: Astra, PR #799 → #791. Besitzerauftrag: vorhandene gemeinsame 3D-Implementierung fertigstellen, kompilieren, gezielt prüfen, unsignierte APK bauen, anschließend kontrolliert in #791 übernehmen. Kein Main-Merge, keine Signierung/Installation.

## Konkreter Zwischenstand

Produktionsstand vor dieser Dokumentation: `fa170ef88eb34a1fe19a4c6302594e97d560e7ec`.

- Gemeinsame Filament-Engine/Scene/Kamera für Originalavatar, räumliche Originalkugel und Arbeitsplatz; externer HardwareBuffer-Monitor angeschlossen.
- Finale fünf Orbitgruppen und die verbesserte #801-Workstation integriert. Assets/Shader/Originalavatar strukturell geprüft: PASS.
- Tatsächliche GLB-Geometrie: größter ursprünglicher Z-Wert 1,506001; größter rotierender Ringradius 1,439293. Beide liegen unter der Laufzeitreserve 1,72. Der Tiefenvertrag wird damit auch von den gelieferten Meshes eingehalten.
- Fokussierter Gradle-Lauf auf `8a0ec80dcd2ee973eea856fbed0d60a463794468`: **367 Tests, 0 Fehler, 0 übersprungen**, 50 Suiten, BUILD SUCCESSFUL in 5m15s. Original-XML und Ergebnis in `docs/evidence/astra55-render-final-20261112/focused-pre-final/`.
- Der spätere Service-Fix aus #802 samt Ergänzungen ist übernommen. Direkte native Host-Gegenproben: **12/12 PASS**, einschließlich ursprünglicher sechs #802-Tests. Quellbindung/Rotkontrolle: `docs/evidence/astra55-pr802-integration-20261112/fgs/`.
- Auf `fa170ef88e` ist die Produktionskompilation abgeschlossen; der anschließende Testcompiler verlor den Gradle-Daemon. Keine Testabnahme dieses Vorbereitungsversuchs. Der erneute Lauf trennt die Phasen und begrenzt JVM-Prozessorzahl/Speicher.
- APK: NOT_BUILT. Fold7 / Filament-GPU / echte App-Pixel / Thermik: NOT_RUN.

## Quellenabgleich

- #801: `871a25cd5de72917cbe3fba54bb8a0d0fab7b103`; geprüfte Material-/Geometrieänderungen und aktuelle Rundgeometrie portiert. Kein zweiter Renderer übernommen.
- #802: `c1d41bcdce73b42cbce3b77920ecd86360b379fe`; Ereignis-, Scroll-, Fenster-/Journal-, Foreground-Service- und Chat-Sitzungskorrekturen integriert. Neuere Screenshot-/Farb-/Frischeprüfungen erhalten.
- Originalavatar SHA256 unverändert: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

## Nächster zwingender Schritt

Kompilation aller Testquellen abschließen → gemeinsame vollständige Android-Host-/UI-Suite → unsignierte Code55-APK → SHA/Artefakt/Restfehler dokumentieren → geprüften Stand nach #791 übernehmen. Zwölf Blender-Bilder der sechs Phasen in Fold-/Phone-Profil entstehen aus tatsächlichen Assets/Matrizen; sie sind keine Android-GPU-Geräteaufnahme.
