# Cinema55 – 3D-Cockpit-Audit (Agent E, 12.11.2026) – STAND: WIP, unvollständig

Basis: `claude/code55-cinematic-evolution-20261112` @ `af04d96ed`. Nur Codelesen; keine Geräte-/GPU-Messung.
Filament/GPU/Fold7: **NOT_RUN**. Host-Bilder (Robolectric) zeigen den 2D-Vorschaukopf, nie das GLB in Filament.

## 1. Renderpfade heute (Datei:Zeile)
| Teil | Pfad | Befund |
|---|---|---|
| Avatar 3D | `avatar3d/FilamentAvatarRenderer.kt:70-152` | Eigene Engine/Scene/View/Kamera/SwapChain auf **TextureView**-Surface; `View.BlendMode.TRANSLUCENT` (`:83`), Bloom LOW 128/4 (`:89-100`), orthografische Kamera (`:258`), feste Lichter (`:132-141`), Zähne ausgeblendet (`:129`). |
| Avatar-Framing | `avatar3d/AvatarFraming.kt:49-103` | Ortho-Fenster: Kopfachse bei `anchorX`, Kronenrand fest; reine Arithmetik pro Größenänderung. |
| Dokumentbühne | `avatar3d/FilamentDocumentStage.kt:39-71` | **Zweite Scene/View/Kamera + eigene SwapChain**, teilt Engine/Renderer/Loader (`FilamentAvatarRenderer.kt:313-324`). Perspektivkamera (`:93-97`). **Texturplatz existiert**: Material `pdf-page`/`baseColorMap` (`:64-65`), Upload nur bei neuem Bitmap (`:119-145`), ≤ 1024 px. Asset `cinema3d/document-workspace.glb` (`:169`). |
| Host-View | `ContinuumHologramView.kt:556-583` | TextureView als Kind Index 0; `onDraw` (`:1070`) zeichnet Aura (`:1108-1124`), Filament-Frame (`:1134-1164`), 2D-Fallback-Kopf (`:1166-1172`), **Kugel per Canvas** (`:1173-1193`). onDraw liegt **unter** den Kindern → auf dem Gerät liegt die Kugel unter der Avatar-TextureView, im Host über dem 2D-Kopf (unterschiedliche Reihenfolge). |
| Kopf/Kugel-Regionen | `ContinuumHologramView.kt:419-431` | `setCockpitRegions` **verbietet Überlappung** von Kopf und Kugel → „Kugel hinter Jarvis“ braucht einen expliziten neuen Modus. |
| Kopf-Maske | `ContinuumHologramView.kt:1267-1280` | `drawChild` legt die TextureView in `saveLayer` + DST_IN-Verlauf; Layer-Deckkraft (0,55–0,65) ist hier ohne Dreieck-Sortierprobleme möglich. Deckkraft heute hart `1f` (`:1163`). |
| Originalkugel | `ui/ContinuumHologramRenderer.kt:49-67, 154, 234` | Geometrie-Presets; Sockel/Boden/Marke (`:305-336`, `:693`) hängen am Preset. **Infinity-Logo** `drawInfinity` (`:601-619`): Breite 0,58·rs, Mitte `cy + 0,02·rs`; Ringe bis 1,42·rs. |
| 2D-Vorschaukopf | `avatar/ContinuumAvatarRenderer.kt:452-556` | Einheiten: Augen x = ±28, y ≈ −20; Brauenwulst y ≈ −36 (`:114-115`) → Stirnpunkt ≈ (0, −50) Einheiten. |
| Layout | `ui/JarvisShellLayout.kt:205` (Fold-Referenz), `:263` (Phone), `:356` (Reflow); `ui/JarvisCockpitGrid.kt:143-153` | Heute: Held → **Eingabe** → 3 Arbeitskarten → Dock → Status. Design: Eingabe direkt über den Buttons. |
| Dock | `ui/JarvisTiles.kt:210` (`bottomNav = railNav`, 11 Ziele), `:285` Spalten (48-dp-Regel), `:324` Zeichnung | Touch-Views aus denselben Boxen: `ContinuumHomeView.kt:741-748`; Zeichenaufruf `JarvisShellRenderer.kt:273-280`. Label `caption` 10 dp **ohne Schriftskalierung** (`JarvisTiles.kt:369`). |
| „Mehr“ | `JarvisShellLayout.kt:104-116`, `ContinuumWorkAreaNavigationSpec.kt:39` | „Mehr“ nur im Fold-Grid; Phone hat nur Glocke/Einstellungen. |

## 2. Was der Host kann / nicht kann
Robolectric (API 28, NATIVE graphics) zeichnet den echten Android-Canvas-Pfad inkl. Glas, Kugel, 2D-Kopf, Layout,
Touch-Views und Schriftgrößen. Es gibt **keine** Filament-Engine, keine TextureView-Inhalte, kein Bloom, keine GPU-Zeiten.

## 3. Umgesetzt (dieser Zweig)
- `ui/CockpitGlassButtons.kt` (WIP, **noch nicht verdrahtet, nicht kompiliert/getestet**): 8 Ziele in Besitzerreihenfolge
  mit Farben, Spaltenregel (≥ 72 dp → 1×8, sonst 2×4), Labelstil (nie kleiner als 100 %-Größe), Glasmaterial mit Cache.

## 4. Offen
Siehe `docs/handoff/CODE55-AGENT-E-UEBERGABE.md`.
