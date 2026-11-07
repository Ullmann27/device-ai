# Continuum Original-Kugel: isolierter Integrations-Slice (05.11.2026)

**Status: SOURCE_STAGED / TEST_NOT_RUN / RELEASE_BLOCKED.**
Zweck: Bereits abgeschlossene, auf dem Quellstand #535 geprüfte Verbesserung der **existierenden** `ContinuumBrainOrbRenderer` samt wiederverwendbaren Shader-Fills auf den im Integrations-PR #527 kontrollierten Quellstand übertragen. Die Besitzer-Anforderung bewahrt die Originalkugel; keine zweite Orb-Architektur.

## Fixierte Eingänge

- Integrationsbasis #527: `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3`, Baum `a49784b0e2f602d48400b2b375357c4261a1b5d3`.
- Designquelle #535: `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`.
- `ContinuumBrainOrbRenderer.kt`: unverändertes Basisblob im Integrationszweig und #526 `a6182160fe41bb8bf0bf921478dc2d6c7a02d1a9`; Designblob `8f6862667abc1f5fcd7678c3d885f2ac8b522333`.
- `ContinuumBrainSceneView.kt`: identisches Basis-/#526-Blob `d4ab0affa7089415f0ee7cbee40baa32e1db08a8`; Designblob `82d09af83a332c368aab83a3e65df085f1e22766`.
- `JarvisSceneFills.kt`: neue separate Hilfsklasse aus #535, Blob `f595e9fbefcd2b695f3847c352fc77e05c98e483`; in #527 und #526 nicht vorhanden.
- Eigenständige neue Regression `ContinuumOrbCoreIntegrationTest.kt` nur für die geänderte Originalkugel.

## Eng begrenzter Inhalt

Die vorhandene Gehirnkugel erhält deutsche Beschriftung, mehrfarbigen Rand, mehrstufige Strahlen, leuchtenden Sockel und den vom echten Pulse-/Processing-Signal steuerbaren inneren Lichtkern. Die vorhandenen `ContinuumBrainOrbRenderer.Signals` bleiben Quelle der Arbeits-/Gedächtniswirkung; keine erfundenen Daten. Die zusätzliche Fill-Hilfsklasse bietet wiederverwendbare Shader-Objekte.

**Nicht geändert:** `AgentActivity`, `ContinuumHologramView`, bestehende Avatar- oder Gesprächslistener, andere Arbeitszweige, `main`, GLB, VersionCode, Paket, Signatur, Original-Downloadkanal, Besitzerdateien.

**NICHT BEHAUPTET:** Dies ist keine komplette Übernahme von #535 oder #526 und kein neuer gesamter Integrationsstand. Keine Android-/AGP-/Robolectric-/Lint-/APK-/Fold7-Prüfung wurde auf diesem zusammengesetzten Quellstand ausgeführt. Die ursprünglichen 184 Host-UI-Pässe von #535 sowie 3.032 Android-Pässe von #527 gelten nur für ihre jeweiligen exakten Originalquellen.

## Nächste verbindliche Abnahme durch C1 / C2

1. Den genauen Head dieses Drafts und die vier Quell-Dateiblobs neu abgleichen; bestehende C1-/C2-Worktree-Locks respektieren.
2. Erst isolierte neue JUnit-Regression, danach die vorhandenen Orb- und vollständigen Android-/UI-Tests auf dem neuen Quellstand tatsächlich ausführen.
3. Die übrigen #526/#535-Anschlüsse (3D-Kopf/Sprachführung/Dialogsynchronität/Neon/Layouts) als **getrennte** Quellen-Slices integrieren; Konflikte namentlich auflösen, keine alten Implementierungen blind überschreiben.
4. Danach gesamten Releasekandidaten mit Originalpaket `at.heinz.deviceai.continuumlocal`, Originalsigner `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, freigegebenem SHA, Release-Freeze und streng höherem versionCode als live veröffentlichtem Katalog prüfen.
5. Originalsignierte APK und `latest.json` nur über den bestehenden Render-Publisher veröffentlichen, HTTPS-Readback und bisherigen In-App-Downloadbutton kontrollieren.

**Rollback:** diesen Draft aus der Integration auslassen. Er verändert keinen anderen Branch.
