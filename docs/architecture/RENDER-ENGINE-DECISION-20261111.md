# Render-Engine-Entscheidung: Godot 4 / Unreal Engine / bestehendes Filament (11.11.2026)

**Status:** Entscheidung auf Dokumenten- und Codebasis. **Kein Prototyp gebaut**, keine Engine installiert, keine
APK gebaut. **Alle Gerätemessungen (APK-Zuwachs, Speicher, GPU-Zeit, Wärme, Fold7): NOT_RUN.**
Besitzervorgabe: „Keine Architekturstudie ohne Umsetzung“ – daher kurz, mit konkretem nächstem Umsetzungsschritt.

## Ist-Stand im Code (Filament 1.56)

- Abhängigkeiten: `filament-android:1.56.0`, `gltfio-android:1.56.0` (`app/build.gradle.kts:302-303`).
- Originalavatar: eigenes `Engine`, `Scene`, `View`, **orthografische** `Camera`, eine gerichtete + zwei Punktlichter
  (`avatar3d/FilamentAvatarRenderer.kt:72-155`, Lichter `:132-147`), Morph-Gewichte pro Bild
  (`:296`), Takt = bestehender View-Frame (`render`, `:239`; „never posts a callback of its own“).
- Dokumentbühne: `avatar3d/FilamentDocumentStage.kt` ist **echte Filament-3D-Geometrie**
  (`cinema3d/document-workspace.glb`, Pin `ASSET_SHA256` `:170`; Glas/Rahmen/PDF-Seite, Seite als dynamische
  `SRGB8_A8`-Textur ≤ 1024 px `:121-133`, perspektivische Kamera, Gier/Neigung aus dem nativen Fenster `:94-104`).
  **Aber:** eigene `Scene`/`View`/`Camera`/`SwapChain` auf eigener `Surface` (`DocumentStageView`); geteilt werden
  nur `Engine`/`Renderer`/`AssetLoader` (`FilamentAvatarRenderer.kt:310-322`). Avatar und Monitore stehen **nicht** in
  einer Szene, teilen keine Kamera, die Avatarlichter erreichen die Monitore nicht.
- Alle Materialien der GLB sind `KHR_materials_unlit` (`tools/cinema-document/build_asset.py:78-80,95`) → keine
  Beleuchtung/Reflexion auf den Monitoren.
- Alle übrigen Arbeitsfenster sind 2D-Canvas mit `android.graphics.Camera`-Pseudoperspektive
  (`ui/HollywoodFuiStageOverlay.kt:298-308`).
- Die Quell-GLB enthält bereits ungenutzte Monitore: `work_steps_*`, `side_rail_*`, `telemetry_*`
  (`tools/cinema-document/scene_work_screens.source.glb`); `build_asset.py` übernimmt nur Dokument/Ergebnis.
  Jeder Rahmen hat 9 840 Indizes, ausgerollt ≈ 236 KB → drei weitere Rahmen sprengen `MAX_BYTES` 512 KiB
  (`FilamentDocumentStage.kt:171`), solange nicht ein normiertes Rahmennetz per glTF-Knoten mehrfach instanziert wird.

## Kriterien und Bewertung

| Kriterium | Godot 4 (als Android-Bibliothek) | Unreal Engine 5 | Bestehendes Filament |
|---|---|---|---|
| Eine APK, ein Paket | Möglich: AAR `org.godotengine:godot` auf Maven Central, Host-Activity implementiert `GodotHost`, `GodotFragment` [1]. | **Nein** für Einbettung: der dokumentierte Weg ASIS betreibt die Engine als Android-**Service in einer eigenen APK**, an die eine separate Client-APK bindet; nur Quell-Build UE ≥ 5.8 [2]. | Ja, bereits in der App. |
| Ein Renderer / eine Avatarinstanz | Zweiter Renderer neben Filament (oder kompletter Ersatz); „only a single instance of the Godot Engine is supported per process“ [1]. | Zweiter Renderer + zweiter Prozess/APK [2]. | Ein `Engine`/`Renderer` (heute zwei Szenen, s. o.). |
| Original-glTF mit 21 Morph-Targets | glTF 2.0 empfohlen; Blendshapes/Morph-Targets nur mit passender Blender-Exporteinstellung dokumentiert [3]. Import wäre ein neuer, ungeprüfter Pfad. | Morph-Targets über Interchange-glTF dokumentiert nur als API [4]; Gemeinschaftsberichte über fehlerhafte Morph-Importe in 5.4–5.5 [5]. | Läuft heute: `gltfio` mit Morph- und Skin-Animation [6], 21 Targets per Vertrag geprüft (`FilamentAvatarRenderer.kt:103-108`). |
| Gemeinsamer Bildtakt mit dem nativen Fenstersystem | Engine besitzt eigene Render-View/-Thread und Hauptschleife (Einbettung „as an Android view“ [1]); Kopplung an den Fenstertakt wäre Neuentwicklung. Automatische Größen-/Ausrichtungswechsel „not supported and may cause a crash“ [1] – kritisch beim Falten des Fold7. | Engine rendert in vom Client gelieferte Surfaces, Takt im Service [2]. | Bereits gekoppelt: Rendern nur im bestehenden View-Frame. |
| Wärme/GPU-Budget neben lokaler LLM-Inferenz | Zusätzliche Engine-Last; Mobile-Renderer braucht Vulkan, Compatibility hat „high scaling cost“ [7]. Budgetkopplung an `JarvisRenderQuality` neu zu bauen. | Mobile-Skalierung über Scalability Settings [8]; zweiter Prozess. | Vorhandenes Einzelbudget (`JarvisRenderQuality`, EXP_15) steuert schon Bloom/Qualität (`FilamentAvatarRenderer.setQuality`). Android empfiehlt, Last anhand `getThermalHeadroom` zu senken [8]. |
| APK-Größe (nur Indiz, keine APK-Messung) | AAR 4.7.2: **103 251 267 Bytes** (Maven-Central-Verzeichnis, alle ABIs, komprimiert) [9]. | Keine offizielle Mindestgröße; Epic nennt nur Optimierungen (z. B. Relocation-Tabelle 62 MB → ≈ 8 MB) [10]. | Bereits enthalten: `filament-android-1.56.0.aar` 3 815 202 Bytes, `gltfio-android-1.56.0.aar` 13 557 880 Bytes (HTTP `Content-Length`) [9]. Zuwachs 0. |
| Aufwand bis „3D-Kontrollraum“ | Hoch: neue Engine, Avatar-Neuimport, Lippen-/Blick-/Licht-Mapping neu, Fold-Konfigurationswechsel absichern. | Sehr hoch und widerspricht „eine APK“. | Niedrig–mittel: Monitore in die **Avatarszene** verschieben, eine Kamera, Lichter wirken; Geometrie und Pipeline existieren. |

Filament selbst ist laut Projekt „designed to be as small as possible and as efficient as possible on Android“ und
unterstützt OpenGL ES 3.0+ und Vulkan auf Android [6]. Die App nutzt 1.56; aktuell ist 1.80 [6] – ein Update ist
kein Teil dieser Entscheidung.

## Empfehlung

Beim bestehenden Filament-Pfad bleiben und ihn zu **einer** 3D-Arbeitsszene ausbauen; Godot und Unreal würden eine
zweite Engine (Unreal sogar eine zweite APK) einführen, den bewährten 21-Morph-Avatarpfad neu bauen und den
gemeinsamen Fenstertakt aufgeben. Das eigentliche Defizit ist nicht die Engine, sondern dass Avatar und Monitore heute
in zwei getrennten Filament-Szenen mit unbeleuchteten Materialien liegen. Kein zusätzlicher Renderer ohne neuen Beleg.

## Nächster konkreter Umsetzungsschritt (noch NICHT umgesetzt)

1. Reines, getestetes Platzierungsmodell `avatar3d/WorkspaceSceneLayout.kt`: aus Avatar-Viewport, orthografischem
   `AvatarFraming.Window`, Kameraachse (inkl. aktuellem Kamerahub) und den nativen Fensterrechtecken → Weltmatrizen der
   Monitor-Entities im Kameraraum des **Avatars** sowie 3D-Blickziel = Mitte des aktiven Monitors. Ortho: Pixel→Welt
   ist linear (gleicher Maßstab horizontal/vertikal, da Fensterbreite = Höhe·Seitenverhältnis, `AvatarFraming.kt:57-60`).
2. Opt-in-Pfad in `FilamentAvatarRenderer`: `document-workspace.glb` mit demselben `AssetLoader` in **dieselbe**
   `Scene` laden (gleiche `View`/`Camera`/Lichter) statt der separaten Bühne; Platzierung in `render()` vor
   `r.render(view)` anwenden (kein zweiter Takt). Standard **AUS** bis zur Fold7-GPU-Prüfung; native Rückfallebene bleibt.
3. Bekannte Hürde: Die Avatar-`TextureView` ist im Cockpit nur so groß wie die Kopfbühne
   (`ContinuumHologramView.kt:1051-1054`); Monitore außerhalb davon wären unsichtbar. Der Viewport muss für den
   Arbeitsraum auf die Bühne erweitert werden, ohne die Kopfframing-Regeln (`AvatarFraming`) zu verletzen.
4. Beleuchtete Monitore erfordern ein neues Asset (anderes Material, neuer `ASSET_SHA256`) – nur offen und mit neuem
   Pin, nie still.

## Quellen

1. Godot Android library (Godot 4.7): https://docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html
2. Unreal ASIS Overview: https://dev.epicgames.com/documentation/unreal-engine/android-single-instance-service-overview-for-unreal-engine
3. Godot available 3D formats (4.7): https://docs.godotengine.org/en/stable/tutorials/assets_pipeline/importing_3d_scenes/available_formats.html
4. Unreal glTF import / Interchange: https://dev.epicgames.com/documentation/unreal-engine/importing-gltf-files-into-unreal-engine, https://dev.epicgames.com/documentation/unreal-engine/API/Runtime/InterchangeEngine/UInterchangeMeshUtilities?lang=en-US
5. Gemeinschaftsberichte (nicht offiziell): https://forums.unrealengine.com/t/ue5-4-1-gltf-morph-targets-are-corrupted-on-import-but-work-in-gltf-viewer/1873937, https://forums.unrealengine.com/t/ue5-5-gltf-morph-targets-are-imported-but-no-animation-curves/2051604
6. Filament README: https://github.com/google/filament
7. Godot renderers: https://docs.godotengine.org/en/stable/tutorials/rendering/renderers.html
8. Android ADPF Thermal API: https://developer.android.com/games/optimize/adpf/thermal
9. Maven Central: https://repo1.maven.org/maven2/org/godotengine/godot/4.7.2.stable/, https://repo1.maven.org/maven2/com/google/android/filament/filament-android/1.56.0/, https://repo1.maven.org/maven2/com/google/android/filament/gltfio-android/1.56.0/
10. Unreal Android-Binärgröße (5.9): https://dev.epicgames.com/documentation/unreal-engine/reducing-android-binary-size-in-unreal-engine-projects?lang=en-US
