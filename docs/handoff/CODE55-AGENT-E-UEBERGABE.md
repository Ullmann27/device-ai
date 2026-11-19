# Code55 Agent E – Übergabe 3D-Cockpit (12.11.2026, WIP)

Zweig `claude/code55-agent-e-3d-cockpit-20261112` (Basis `af04d96ed`). Abbruch an der Besitzer-Budgetgrenze.
**Status: WIP.** Keine sichtbare Änderung in der App; keine neuen Nachher-Bilder (Vorher = `docs/evidence/code55-cinema55-baseline-20261112/`).
Filament/GPU/Fold7: NOT_RUN. Gradle/Kompilierung der neuen Datei: NOT_RUN.

## Renderpfad-Befunde (Details: `docs/research/omega/CINEMA55-3D-COCKPIT-AUDIT-20261112.md`)
- Avatar: eigene Filament-View auf TextureView (`FilamentAvatarRenderer.kt:70-152`, ortho `:258`).
- Dokumentbühne = zweite Filament-Scene/View/SwapChain mit **vorhandenem Texturplatz** `pdf-page/baseColorMap` (`FilamentDocumentStage.kt:64-65, 119-145`).
- Kugel ist Canvas (`ContinuumHologramRenderer.kt:234`) in `ContinuumHologramView.onDraw` (`:1173-1193`) → auf dem Gerät **unter** der Avatar-TextureView, im Host **über** dem 2D-Kopf.
- `setCockpitRegions` verbietet Kopf/Kugel-Überlappung (`ContinuumHologramView.kt:419-424`).
- Avatar-Deckkraft hart 1 (`ContinuumHologramView.kt:1163`); Layer-Maske in `drawChild` (`:1267-1280`).
- Dock = 11 Rail-Ziele (`JarvisTiles.kt:210`), 48-dp-Spaltenregel (`:285`), Label ohne Schriftskalierung (`:369`).

## Sichtbar neu
Noch nichts verdrahtet. Neu im Code: `app/src/main/java/at/heinz/deviceai/ui/CockpitGlassButtons.kt` (Renderer + Geometrie).

## Nächste Schritte
1. **Glasbuttons verdrahten:** `JarvisTiles.bottomNav` aus `CockpitGlassButtons.buttons`; `bottomNavColumns` →
   `CockpitGlassButtons.columns`; in `drawBottomNav` `CockpitGlassButtons.draw(p, body(cell), spec, active, pressed,
   fontScale = shell.profile.fontScale)` (Aufruf `JarvisShellRenderer.kt:273` um `fontScale` ergänzen).
   `logic/agents/system` in `ContinuumWorkAreaNavigationSpec.moreEntries`; `headerActionBoxes` (`JarvisShellLayout.kt:104`)
   auch ohne Grid mit „Mehr“ (≥ 144 dp). Tests anpassen: `JarvisCockpitUxArchitectTest:21-60`, `JarvisShellContentTest:35-67`,
   `LuxuryHoloButtonRendererTest:85-128`, `JarvisThermalVisualContractTest:26`, `CinemaButtonMaterialUiTest:96-154`
   (`bottom:system` → `bottom:device`), `JarvisReferenceHitTargetsUiTest:109-121`.
2. **Eingabe über Buttons:** `JarvisCockpitGrid.create` (`:143-151`), `compactCinema` (`JarvisShellLayout.kt:335-344`),
   `compactReference` (`:179-192`): Held → Arbeitskarten → Eingabe → Dock; im Reflow Eingabe nach TILES.
3. **Kugel hinter Jarvis:** Preset `Geometry.BEHIND_AVATAR` (centerY .5, Radius .33 des Quadrats, ohne Sockel/Boden/Marke);
   Kugelmitte = Stirnpunkt, rs so, dass Logo-Breite 0,58·rs ≈ Augenmittenabstand. Host: Stirn (0, −50) Einheiten des
   2D-Kopfes; Filament: Joints `EyeLeft/EyeRight` (World-Transform) über das Ortho-Fenster projizieren
   (`FilamentAvatarRenderer.kt:117-119, 253`). `setCockpitRegions` um Modus „orbBehindHead“ erweitern.
4. **Transparenz/Stirnlogo:** Avatar als ganze Ebene 0,55–0,65 deckend (TextureView-`alpha` statt `1f` bei `:1163`
   bzw. `saveLayerAlpha` in `drawChild`) → keine Sortierfehler, Tiefentest bleibt intern. Host: Kugel zuerst, dann
   2D-Kopf in `saveLayerAlpha`. Bloom-Schwelle prüfen (Augen/Stirn nicht überstrahlen). Abnahme nur auf Fold7-GPU.
5. **3D-Monitor:** `FilamentDocumentStage`/`document-workspace.glb` als aufrechten Monitor wiederverwenden (Yaw ≤ 8°,
   Pitch 0); Texturplatz an Agent Cs Latest-Frame-Holder; ohne Quelle „Keine Live-Quelle“/„Pausiert“, nie Fake-Inhalt.
   Klickring/LIVE-PAUSIERT über Agent Ds `cognitive/CockpitWorkEvent.kt`.
6. **Tests/Bilder:** Phone 2×4 / Fold 1×8, Reihenfolge/Farben/≥ 48 dp/Labels 100 % + 200 %; Captures mit
   `-PcontinuumUiEvidenceDir` auf `*CinemaNativeSequenceUiTest` / `*ContinuumReferenceCockpitUiTest`, Maße wie Baseline.
