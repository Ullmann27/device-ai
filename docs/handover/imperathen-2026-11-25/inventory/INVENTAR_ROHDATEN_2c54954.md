> Herkunft: automatisch erhobene Rohinventur (nur lesend) auf `2c549548` durch einen Hilfsagenten dieser Sitzung. Stichprobe selbst nachgeprüft und übereinstimmend: 51 .blend, 137 GLB, 3 Audiodateien, 14 Missions-JSON, 113 Haupt-/96 Nebenmissionen, 0 Stealth-Treffer, 114 Skripte mit 31 626 Zeilen, 114 Godot-Tests, 6 Videos. Alle übrigen Angaben sind nicht einzeln nachgeprüft. Die Pfadangabe `scratchpad/scripts/glbinfo.py` bezieht sich auf die Sitzungsumgebung und liegt nicht im Repository.

# IMPERATHEN – Technisches Inventar (Rohdaten)

Repo: lokaler Klon `/home/user/ullmann27/imperathen`, Commit `2c549548497aad901bda32d71c19c2a1961d9591` (Branch `integration/imperathen-unified-20261125`, lokal `unified`, Draft-PR #86), flach (depth 1). Erstellt 2026-11-25, rein lesend. Alle Zahlen aus den genannten Befehlen; Pfade relativ zum Repo-Root. Hilfsskript: `scratchpad/scripts/glbinfo.py`.

Gesamtumfang: `find . -path ./.git -prune -o -type f -print | wc -l` = 2982 Dateien; `du -sh .` = 1.2G (inkl. .git). Dateiendungen (Top): 819 .log, 418 .png, 312 .json, 244 .gd, 237 .import, 199 .uid, 189 .md, 137 .glb, 133 .py, 54 .webp, 51 .blend (Histogramm über `find … | awk -F. '{print tolower($NF)}' | sort | uniq -c`).

Git-LFS: `grep -rl '^version https://git-lfs' . --exclude-dir=.git` -> 0 Treffer. **Keine LFS-Stubs**; `.gitattributes` setzt nur eol/binary.

---

## 1. Blender

- **51 .blend-Dateien, 191.412.602 Bytes** (Tabelle A1 unten). Keine davon liegt unter `prototype/`. In `prototype/` wird `.blend` nur als Textverweis genannt (`prototype/assets/asset_manifest.json:11` "source": "art/source/frontier_hut.blend"; Z. 92 soldiers). Godot importiert keine .blend direkt (keine `.blend.import`).
- **36 Python-Skripte mit `import bpy`** (Tabelle A2), verteilt auf `art/**` (10) und `tools/**` (26).
- Nicht im Repo, aber vom Code/CI erwartet: `art/meshy_youth/roman_youth_rigged.blend` (Ziel in `tools/characters/build_meshy_player.py:200`) -> `ls art/meshy_youth`: nicht vorhanden. Die Stadt-`.blend` aus `tools/world/build_roman_test_city.py` entsteht nur als CI-Artefakt (`docs/32_city_asset_integrations/REAL_CITY_PACK_STATUS.md:54-57`: "Sie liegen nach dem Build nicht automatisch im Quellbranch").

## 2. 3D-Modelle

- **137 GLB, 0 glTF, 254.605.340 Bytes** (`find … -iname '*.glb' -o -iname '*.gltf'` + `du -cb`). `prototype/assets`: 130 Dateien / 222,7 MB; `art/visual_benchmark/assets`: 7 / 20,2 MB.
- Alle 137 haben gültigen glTF-Header (Skript: 0 Fehler). Generator in fast allen: "Khronos glTF Blender I/O v4.3.x"; `viva_roma/props` und `viva_country` sind eigene Generatoren ("IMPERATHEN original VIVA mesh", "IMPERATHEN VIVA ROMA country").
- **61 GLB mit Skin + Animation**. Joint-/Clip-Sets (Skript-Gruppierung):
  - 20 Dateien, 15 Joints, 11 Clips: idle, march, run, gather, talk, train, work, block, attack, hit, death (frontier_civilian_man/woman/elder, hero_quality_0..3, villager_*, …)
  - 24 Dateien, 15 Joints, 11 Clips: idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death (frontier_legionary/officer/spearman, soldiers heavy/spear/officer near/mid, village_characters heavy/spear/officer)
  - 6 Dateien, 17 Joints, gleiche Militär-Clips (frontier_archer_0..3, soldiers/archer_near, archer_mid)
  - 8 Dateien, 15 Joints, 11 Clips idle, march, run, talk, work, gather, train, attack, block, hit, death (meshy/characters unit_germanic_axe_fighter_0..3, unit_roman_signifer_0..3)
  - 3 Dateien, 15 Joints, 2 Clips idle, march (landscape/farmer, han_farmer, wa_farmer)
  - Ferne LODs `soldiers/*_far.glb` (4): kein Skin, keine Clips. `art/visual_benchmark/assets/characters/*.glb` (4): kein Skin, keine Clips.
- **Integration (Ladepfad im Code):** alle 130 GLBs unter `prototype/assets` werden von `.gd` geladen – direkt per `res://`-Literal oder per Formatstring (`village_person.gd:17,22,161,196-199`, `soldier_visual.gd:106-108`, `meshy_character_registry.gd:5`, `meshy_equipment.gd:5`, `city_viva_closeup_props.gd:47-48` (portal/helmet_lod%d), `country_field.gd:72,97` (terrain_%d, scatter_library), `country_scenery.gd:17-37`, `closeup_lab.gd:128-141`, `asset_library.gd` (world_expansion via manifest)). Die 7 GLBs in `art/visual_benchmark/assets` werden **nicht** geladen; `frontier_house.glb` und `village_well.glb` sind dort md5-identische Kopien der `prototype/assets/architecture`-Dateien (`md5sum`: ea59d4bb…, 772ab098…).
- **Im Code referenziert, aber im Repo NICHT vorhanden** (`grep -rhoE '"res://[^"%]+"' … | test -e`): `res://assets/world/roman_city_test.glb`, `roman_city_collision.glb`, `roman_forum_paving_detail.glb`, `roman_forum_paving_collision.glb`, `roman_city_manifest.json`, `res://assets/animations/accad_youth_motion.json`; zusätzlich per Formatstring `res://assets/characters/hero_meshy_italia_%d.glb` (Spieler-Jugendlicher, 4 LODs, `village_person.gd:18`) und `res://assets/world/roman_meshy_temple_%d.glb` (`city_exploration.gd:25`). Diese werden erst in CI erzeugt/gestaged (`.github/workflows/fold7-city-apk.yml:95-111`: `stage_original_youth.py` aus Archiv in `$RUNNER_TEMP`, ACCAD-Retarget; privater Tempel nur mit Secret `IMPERATHEN_PRIVATE_MESHY_TEMPLE_URL`, `docs/33_fold7_release/QA_CANDIDATE.md:36-40`). Laut Manifest `docs/28_city_sandbox/original-youth-runtime.json`: 35 Bones, 11 Clips, Archiv 4.563.165 Bytes, `raw_meshy_source_included: false`.
- Weitere 3D-Quellen: `art/hero_quality/source/*.obj/.mhclo/.target/.mhw` (MakeHuman-Basis, 5 .obj), `prototype/assets/characters/hero_hand_grip_0..3.res` (4 Godot-Ressourcen, geladen in `hand_pose.gd:14`), `game/Imperathen/` = Unreal-Projektgerüst (`Imperathen.uproject`, 3 .cs), nicht Teil des Godot-Spiels.

## 3. Skelette / Animationen

- `find … -iname '*.bvh' -o -iname '*.fbx' -o -iname '*.anim'` -> **0 Dateien**. BVH wird nur in CI heruntergeladen: ACCAD `Male2_bvh.zip` (`tools/characters/retarget_accad_youth.py:17`, Workflow `fold7-city-apk.yml:105`), CC BY 3.0 (Z. 19).
- ACCAD-Retarget-Clips für den Jugendlichen (`retarget_accad_youth.py:20-33`): Gaits march (Male2_B3_Walk), run (Male2_C3_Run); Kampf jab_left/right, hook_left/right, uppercut_left/right, front_kick, side_kick, block. Ergebnisdatei `prototype/assets/animations/accad_youth_motion.json`: nicht vorhanden (nur CI).
- `prototype/assets/animations/hero_cc0_library.tres` (1.616.783 B, AnimationLibrary): `grep -c '^\[sub_resource type="Animation"'` = **36**. Report `hero_cc0_retarget_report.json`: accepted_clips 36, failures [], rejected 0, `runtime_approved: false`, `root_motion: false`, `visual_skin_gate: "not run"`, Quelle "CC0 KinemIQ v1.0", Ziel hero_quality_0.glb. Clips: Action_dance, Action_interact, Action_pick_up_from_a_table, Action_sit_down, Action_stand_up_from_sitting, Block_with_the_sword, Death_fall_backwards, Dodge_roll, Hit_knockback, Hit_to_the_chest, Hit_to_the_head, Idle_arms_folded_waiting, Idle_crouching, Idle_relaxed, Idle_sitting, Idle_talking, Idle_with_a_sword, Idle_zombie, Jump_in_the_air, Jump_landing, Jump_take_off, KinemIQ_Mannequin (Referenz), Move_climb_up_1_m, Move_get_up_from_the_ground, Punch_cross_right_2, Punch_hook_right_2, Punch_jab_left_2, Run_jog_forwards, Run_sprint, Strike_zombie_scratch, Sword_slash, Walk_carrying, Walk_crouched_forwards, Walk_formal, Walk_forwards, Walk_zombie_forwards. Laufzeit nutzt laut `prototype/assets/animations/README.md:5` nur Spine/Head-Rotation aus Idle_relaxed und Idle_talking (`cc0_gestures.gd`).
- Prozedurale/eigene Bewegung: `scripts/visuals/character_motion.gd` (753 Z., eigene deterministische Posen), `natural_motion.gd` (163), `facial_motion.gd` (123), `hand_pose.gd`, `youth_sole_contact.gd`.
- `quality/motion_contract.json` (24.432 B): 18 Familien. Aktiv: hero(4 Ressourcen), frontier_people(28), legacy_people(10), meshy_people(8), battle_rigs(8), distant_ranks(4), regional_workers(3), service_gear(2), meshy_gear(6), village_navigation, raid_motion, cinematic_people; support animation_support(6); static static_environment(24); **inaktiv** horse_and_other_animals, mounted_units, rolling_machines, articulated_equipment (je 0 Ressourcen). 92 runtime_scripts. Offene Anforderungen (wörtlich): meshy_people "Complete world-space foot-contact and transition acceptance for both Meshy rigs remains open."; distant_ranks "Measured contact/phase/reaction and visible-distance acceptance of the distant-rank shader remains open."; regional_workers "Complete task/contact acceptance of every exported regional worker remains open."; raid_motion "Continuous raid physics is implemented; full contact, collision, transition and visual acceptance remains open."
- `quality/motion_reviews.json` (43 B): `{"schema_version":1,"reviews":[]}` -> **keine eingetragenen Motion-Reviews**.

## 4. Renders und Aufnahmen

483 Bild-/Videodateien (Tabelle C1): prototype 117, docs 231, art 118, tools 17. Videos: 5 .mp4 + 1 .webm (alle in docs/ bzw. art/soldiers; **kein Video im Spielpaket prototype/**).

Einordnung (nach Begleitdoku, Zitat):
- **Echte Blender-Renders:** `art/hero_quality/review` ("tatsächlich mit Blender Cycles gerenderte Ansichten", `art/hero_quality/README.md:8`); `art/landscape/review` ("echte Blender-Cycles-Renderings der wieder importierten Spiel-GLBs", `art/landscape/README.md:7`); `art/frontier_people/review` ("echte Blender-Studioaufnahmen", `art/frontier_people/README.md:109`); `art/weapons/weapons_studio_preview.png` ("Offline-Cycles-Render", `art/weapons/README.md:23`); `art/world_expansion/vegetation/review` ("tatsächliche Cycles-CPU-Renderings", `art/world_expansion/vegetation/README.md:107`); `art/landscape/meshy/*.png` ("Blender 4.3.2 und Cycles erzeugen die PNGs", `art/landscape/meshy/README.md:17`); `tools/city_detail/characters_review/*.png` (Generator-Docstring `characters_blender.py`: "Inspect and render the real hero…").
- **Echte Godot-Aufnahmen (Desktop, Mesa llvmpipe/Software-GPU, oder Android-Emulator x86_64):** `art/frontier_people/runtime_review` ("echte Godot-Viewport-Aufnahmen", `art/frontier_people/README.md:110`); `art/architecture/review` ("Alle sechs Bilder stammen aus Godot 4.7.2, Compatibility auf Mesa llvmpipe", `art/architecture/README.md:13`); `art/soldiers/field_march.webm` ("72 Einzelbilder stammen aus dem Godot-Viewport", `art/soldiers/README.md:34`); `docs/13_visual_upgrade/evidence` ("Godot-Aufnahmen auf Linux", `docs/13_visual_upgrade/ABNAHME.md:23`); `docs/20_meshy_assets/evidence` (`CHARACTER_INTEGRATION.md:85` "Godot 4.7.2, OpenGL Compatibility und Mesa llvmpipe"); `docs/25_team_a/.../geometry-final` (`CONTACT_GROUNDING_CONTINUATION.md:71`); `docs/24_ap1_ap2/evidence` (`DELIVERY.md:179` "llvmpipe/Mesa unter Xvfb"); `docs/14_character_realism/evidence/after/hero_march.mp4` (`DELIVERY.md:59` "Godot: tatsächlicher Marschzyklus als MP4"); `docs/16_opening/evidence/*/opening-runtime-excerpt.mp4` ("aus dem tatsächlichen aktuellen Renderer … Es gibt keine gesprochenen Stimmen", `docs/16_opening/evidence/sky-seam/README.md:46,48`); Android-Screenshots `docs/17_village_life/evidence/android-delivery/screenshots` ("die übrigen Bilder gehören zum x86_64-Emulatorlauf", `SCREENSHOTS.md:5`), `docs/19_motion_combat/evidence/android/ci31` (Emulator, Report `model: sdk_gphone64_x86_64`); `docs/19_motion_combat/evidence/touch` ("historischen Bilder … Godot 4.7.2 … Mesa llvmpipe", `TOUCH.md:153-154`).
- **KI-/Konzeptbilder:** `art/portraits/source/*.png` (6) – "mit OpenAI ImageGen erstellt" (`art/portraits/README.md:12`); daraus abgeleitet die 18 Spielporträts `prototype/assets/portraits/*.webp` (im Spiel geladen, `figure_gallery.gd:42`); `art/hero_quality/reference/character_target.png` – "newly generated art-direction reference … with the host's image-generation tool. It is not a Blender render…" (`art/hero_quality/reference/README.md:3-5`). Die in `docs/31_fold7_high/FOLD7_GRAPHICS_TARGET.md:7-11` beschriebene "KI-generierte IMPERATUM-ROME-Marktszene" (mit "erfundenen" 58 FPS/97 % GPU/42 °C) liegt **nicht** im Repo (`find`/`grep -i imperatum` in Medienliste: 0).
- **Texturen (keine Renders):** u. a. `prototype/assets/characters/*.png` (36), `roman_material_lab` (21), `weapons` (15), `village` (8), `village_characters/faces/*.webp` (12; "face UV maps come from this repository's own gallery renders", `art/village_characters/README.md:5`), `atlas/earth.jpg` (Natural Earth II, `docs/12_integration/ATLAS_SOURCES.md:18`), `art/visual_benchmark/materials` (24).
- **Aktuellster Stand (docs/28–33): keine Bild-/Videodatei** in diesen Ordnern. Visuelle Referenzen des Fold7-Kandidaten werden nur als CI-Artefakt erzeugt (`docs/33_fold7_release/QA_CANDIDATE.md:50-55`).

## 5. Missionen, Dialoge, Stimmen, Cinematics

- **Spiel-Missionsdaten:** `prototype/assets/story/missions/band1/` = 14 Dateien I-01…I-14 + `index.json` (Reihenfolge I-01..I-14). Geladen von `scripts/story/mission_data.gd:6` (ROOT `res://assets/story/missions/`), verwendet in `village_childhood.gd:41,43`, `companion_story.gd:14`. Inhalt (Python-Zählung): 35 `steps` (I-01 3, I-03 3, I-04 5, I-05 4, I-06 3, I-07 15, I-09 2) und `bridges` in I-02 (2), I-08 (2), I-10..I-14 (je 1). I-00 existiert als Cinematic-Szene `scripts/cinematic/i00_stage.gd` ("Lightweight cinematic scaffold for I-00").
- **Kampagnen-Register** `prototype/assets/story/campaign_arc.json` (geladen via `campaign_bible.gd`, genutzt in `village_childhood.gd:580` für Titel): 4 Bände; Hauptmissionen 32 (I-00..I-31) + 25 (II-00..II-24) + 25 (III-00..III-24) + 31 (IV-00..IV-30) = **113**; Nebenquests 4×24 = **96** (NQ-1-01…NQ-4-24). Nur Band 1 hat Felder story_core/cast/location/narrative_status; Bände 2–4 nur id+title. Entspricht `docs/23_aeterna/MASTERPLAN.md:8`: "113 Haupt-/96 Nebenmissionen registriert, nicht implementiert."
- Spielbar mit Schrittdaten: 14 (I-01..I-14) + I-00 als Cinematic = 15 von 113. I-15..I-31 und alle Bände 2–4 nur Registereintrag. Laut `docs/23_handoff_sol/UEBERGABE.md:108-110`: I-15 "nur konzipiert, kein Code", I-16 ff. "nicht begonnen", Nebenquests "nicht umgesetzt, kein Nebenquest-System". In `.gd` vorkommende Missions-IDs: nur I-00…I-14 (`grep -rhoE 'I-[0-9]{2}' prototype/scripts`).
- **Dialoge:** keine eigene Dialogdatei (`find -iname '*dialog*'` -> nur Test/Workflow/PNG). Dialogtext steckt in den Missions-JSON (`text`, `result`, `objective`-Felder) und in GDScript (`village_childhood.gd`, `village_life.gd`, `opening_story.gd`, `frontier_chapter.gd`, `season_campaign.gd`, `origins.gd`). Drehbuch-DOCX (4 Bände) nicht im Repo (`campaign_arc.json` source_file; `docs/23_handoff_sol/UEBERGABE.md:128`: liegen außerhalb des Repos beim Projektinhaber).
- **Audio:** 3 Dateien (`find … *.wav *.ogg *.mp3`): `prototype/assets/frontier_ambience.wav` (352.844 B, mono 22.050 Hz, 8,0 s; Wind/Vögel, synthetisch, `tools/build_frontier_ambience.py:1`), `prototype/assets/ui/intro_trailer.ogg` (182.013 B, 11,6 s), `prototype/assets/ui/menu_theme.ogg` (986.048 B, 58,2 s) – beide "eigene numpy-Synthese" (`docs/22_title_menu/DELIVERY.md:23,77`). Alle drei geladen (`main.gd:927`, `imperial_intro.gd:15`, `roman_style.gd:9`); UI-Klick wird prozedural erzeugt (`game_ui.gd:2215-2232`). **Keine Sprachaufnahmen** ("Eine zusätzliche Vertonung wurde nicht erstellt", `docs/24_ap1_ap2/DELIVERY.md:34`; "recorded voices, phoneme lip sync … are not claimed", `docs/28_city_sandbox/ORIGINAL_CHARACTERS_AND_CINEMATICS.md:8`).
- **Cinematics:** keine Videodatei im Spiel; `grep VideoStream|.ogv|.webm|.mp4` in `prototype/scripts` -> 0. Echtzeit-Szenen: `scripts/cinematic/i00_stage.gd` (158), `opening_director.gd` (406, "Five authored shots"), `opening_checkpoint.gd` (52), `scripts/story/mission_cinematics.gd` (60; Missionsübergänge aus Dialogzeile + Ergebnis, aufgerufen in `main.gd:549,856-857`), `ui/imperial_intro.gd` (722, Titelsequenz), `ui/opening_overlay.gd` (438), `ui/imperial_fx.gd` (408).

## 6. Implementierte Mechaniken (Code)

114 `.gd` in `prototype/scripts`, 31.626 Zeilen (Tabelle E1). Kern:
- Autorität/Save: `domain/world_model.gd` (1298; SCHEMA_VERSION 5, `save_game` mit .bak/.tmp-Wiederherstellung Z. 320-355; Save `user://imperathen_campaign.json`, `main.gd:21`). Weitere Saves: `user://imperathen_city_trade.json`, `…_city_exploration.json`, `…_city_support.json`, `…_opening.json`, UI-Settings `user://imperathen_ui.cfg`.
- Bewegung/Kamera/Touch: `world/game_world.gd` (1768), `camera_obstruction.gd`, `ui/touch_pad.gd`, `touch_look.gd`, `touch_action.gd`, `touch_modal.gd`, `safe_area.gd`, `village_navigation.gd`.
- Nahkampf: `domain/combat_actions.gd` (358; 15 Techniken: jab/hook/uppercut L/R, headbutt, front/side/high_kick, cut_inward/outward, thrust, overhead, turn_cut; mit windup/active/reach/damage/stamina), `unarmed_combat.gd`, `world/player_action_world.gd`, `ui/combat_action.gd`.
- Taktische Schlacht: `battle/battle_model.gd` (655), `formation_slots.gd`, `valley_field.gd`, `tactics/valley_scene.gd/valley_hud.gd/valley_landscape.gd` (7 Formationen laut README).
- Strategie/Saison: `strategy/season_campaign.gd`, `globe.gd`, `atlas_screen.gd`, `atlas_data.gd`, `origins.gd` (5 Herkünfte).
- Dorf-Ökonomie/Bau/Berufe: `domain/village_economy.gd` (559), `story/village_life.gd` (890; Überfälle, Alarm, Wachen-Einteilung), `ui/village_life_panel.gd`.
- Handel/Inventar: `city_trade.gd` (526; Inventar, Kauf/Verkauf, Denarii), `city_trade_catalog.gd` (16 Waren, 6 Läden), `city_support_panel.gd`, `world/city_market_bridge.gd` (`open_inventory`).
- NPC: `domain/npc_behavior.gd` (514), `npc_social_memory.gd` (156), `world/village/village_population.gd` (553), `world/city_ambient_people.gd`, `city_exploration_crowd.gd`, `story/companion_follow.gd`, `companion_story.gd`, `world/companion_world.gd`.
- Story: `story/village_childhood.gd` (1057), `mission_data.gd`, `chapter_abilities.gd` (3 Fähigkeiten in `chapter1_abilities.json`), `campaign_bible.gd`, `opening_story.gd`, `frontier_chapter.gd`, `life_path_policy.gd` ("Opt-in design component").
- Welt/Streaming: `world/regional_terrain.gd`, `world_stream_plan.gd`, `regional_village.gd`, `world/city_exploration.gd` (830; Menüpunkt "Stadt frei erkunden", `game_ui.gd:326`), `city_training.gd`, `world_expansion/*` (Vorschau-Werkstatt, separate Szene).
- Grafik/Performance: `ui/runtime_display.gd` (Autoload, Framemessung), `rendering/fold7_quality_policy.gd` (Governor; Adreno-830-Erkennung), `prototype/assets/quality_profiles.json` (4 Profile low/balanced/high/very_high; max_3d_pixels 921.600 / 2.073.600 / 3.686.400 / 5.760.000; `device_validation.status: "not_yet_run_on_android_hardware"`), `ui/android_graphics_probe.gd`.
- Nicht über das Hauptmenü erreichbare Einzelszenen: `scenes/viva_country/country_walk.tscn`, `scenes/viva_roma/closeup_lab.tscn`, `scenes/world_expansion/preview.tscn`, `scenes/city_support_district.tscn`, `scenes/roman_material_lab/material_preview.tscn` (`grep 'res://scenes/'` in scripts: nur in Test; Start per Fixture-`project.godot` in `tools/*/prepare_fixture.py`).
- **Stealth: nicht vorhanden.** `grep -rni -e stealth -e schleich -e sneak` (gesamtes Repo ohne .git/.log) -> 0; in .log ebenfalls 0. `perception` nur 3 Zeilen in `npc_behavior.gd:173,477,480` (Positions-Snapshot für NPC-Entscheidung, keine Entdeckung). Vorhanden sind nur Sichtlinien-Raycasts: `npc_social_memory.gd:66-83 clear_sight` (NPC-Gespräche), `village_life_world.gd:61`/`village_life.gd:361` (Überfall-Gegner greifen nur bei freier Sicht innerhalb 2,2 m an), `game_world.gd:535-543`. "Wache" kommt nur als Rolle/Text vor (z. B. `village_layout.gd:45-46` Straßen-/Torwache, `battle_model.gd` Talwache). Kein Sicht-/Hör-Entdeckungssystem, kein Schleichen, keine Wachen-Alarmstufen.
- **Pferd/Reiten: nicht vorhanden** (`grep -i 'horse|pferd|riding|reiten'` nur Textzufallstreffer; motion_contract: horse_and_other_animals/mounted_units `inactive`).

## 7. Godot-Tests

114 `.gd` in `prototype/tests` (+1 in `prototype/scripts/tests`). 72 enthalten ein `*_PASS`-Literal, 1 `FRONTIER_POPULATION_OK`, die übrigen drucken `*_RESULT checks=%d failures=%d`, "…: %d checks passed" oder Review-/Capture-Marker (Tabelle F1). Fixtures: `prototype/tests/fixtures/mission_bridge_data/*.json` (4). Nicht direkt in `tools/*.py`/`.github/workflows` referenziert (`grep -l <name>`): city_support_graphics_review, frontier_graphics_review, frontier_people_review, life_path_policy_test, live_graphics_review, menu_review, motion_mesh_clearance, motion_skin_measurement, motion_world_measurement, village_render_probe, visual_benchmark (einige sind Hilfsmodule anderer Tests). Tests wurden **nicht ausgeführt** (Read-only-Inventar).

## 8. Python/Node/C++-Tests unter tests/

Siehe Tabelle G1 (`ls tests/*.py | wc -l` = 21 Python-Dateien: 18 `android_*_test.py`, `meshy_gait_reach_test.py`, `motion_quality_gate_test.py`, `pipeline_test.py`), `core.test.mjs` (18 `test(`-Aufrufe, testet `preview/core/game.mjs` = Web-Vorschau, nicht Godot), `native_entry_test.cpp` (Unreal-Entry-Logik, via `tools/verify_unreal.py:48-50`). Nicht ausgeführt.

## 9. Grafik/Performance/Speicher/Gerät – belegte Werte

- **Physisches Gerät (Fold7/SM-F966B): keine Messung im Repo.** `find -iname '*soak*' -o -iname '*thermal*' -o -iname 'system.json'` -> 0 Evidenzdateien. Aussagen: "Ohne vom Nutzer autorisierten ADB-Zugriff auf das tatsächliche SM-F966B-Gerät kann ChatGPT keine reale 20-Minuten-Messung liefern." (`docs/33_fold7_release/QA_CANDIDATE.md:79-80`); "Entwicklungs- und Abnahmevertrag, kein bereits erreichter FPS-Wert." (`docs/31_fold7_high/FOLD7_GRAPHICS_TARGET.md:3`); Tracker M3.3 "BLOCKED | physisches Gerät, reale Telemetrie; Emulator reicht nicht" (`docs/23_aeterna/TRACKER.md:19`); `quality_profiles.json:111` "not_yet_run_on_android_hardware". Werkzeug vorhanden: `tools/fold7_physical_qa.py` (expected_model "SM-F966B", Z. 92).
- **Zielwerte (keine Messung):** Fold7-Profile Hoch 60 FPS-Cap / 3.686.400 px, Sehr Hoch 30 / 5.760.000, Ausgewogen 60 / 2.073.600, Leicht 30 / 921.600 (`FOLD7_GRAPHICS_TARGET.md:20-23`). Masterplan-Hypothesen (`MASTERPLAN.md:13-19`) und Benchmarkzitat "UL Fold7 Adreno 830 Steel Nomad Light: 2048 Punkte, 66 % Stabilität" (`MASTERPLAN.md:11`, Fremdbenchmark). `FOLD7_GRAPHICS_TARGET.md:9-11`: die 58 FPS/97 % GPU/42 °C im KI-Bild sind "erfundene Bildelemente".
- **Desktop-Software-Rendering (Linux, Mesa llvmpipe, 1280×800, 60 Soldaten):** p50/p95 Framezeit Niedrig 63,9/73,7 ms, Ausgewogen 298,1/356,0 ms, Hoch 540,0/842,5 ms; RSS 425/510/617 MiB; Draw Calls 70/139/246 (`docs/12_integration/DELIVERY.md:68-74`). Zweite Reihe: p50 73,0/334,5/523,3 ms, p95 90,4/564,0/600,5 ms, RSS 423/522/630 MiB (`docs/12_integration/GRAPHICS.md:17`). Dorf-Requisiten 960×540 llvmpipe: 43,429→42,608 / 46,608→42,622 / 46,064→36,557 ms, Draw Calls 130→31 / 169→49 / 125→36 (`docs/18_childhood/PERFORMANCE.md:38-44`). Soldaten-Viewport llvmpipe: Render-CPU 197,57 ms, GPU 207,65 ms, 109 Draw Calls (`art/soldiers/README.md:40`). Körperberechnung 4,49→39,66 µs/Actor (`docs/19_motion_combat/DELIVERY.md:184-187`). Alle ausdrücklich "keine Android-Bildrate".
- **Android-Emulator (x86_64, API 35, `model: sdk_gphone64_x86_64`, SwiftShader):** Laufzeitberichte melden "fps" 0,22–1,07 bei 1–2 Frames, z. B. 0,282 fps / 3543,957 ms, 861 Draw Calls (`docs/19_motion_combat/evidence/android/ci31/runtime-report.json:3289-3294`, gpu "SwiftShader"), 0,218 fps / 4581,444 ms (`docs/17_village_life/evidence/android-delivery/IMPERATHEN-e1b1c1fb-Emulatorpruefung.json:823-826`), 0,74 fps / 1350,485 ms (`docs/12_integration/evidence/motion/android-runtime.json:342-345`). Alle mit `"physical_device_test": false` (jeweils Z. 4). Audit: `"fps_measurement_available": false`, `"audio_and_thermal_behavior": "not_tested"` (`docs/10_development/evidence/android/independent_audit.json:132-138`).
- **APK-Größen (dokumentiert, Release-Assets nicht im Repo):** Referenzrelease 0.7.0/Code 7 `7c3194db`: 184.262.016 Bytes, "kein physischer Fold7-Nachweis" (`docs/23_aeterna/MASTERPLAN.md:4`). Ältere: 67.301.191 B (`ebefe0b7`, `docs/12_integration/DELIVERY.md:11`), 64.802.890 B (Z. 25), 58.597.583 B (`02c56681`, Z. 31), 28.698.864 B (`ebcf2d8`, Z. 50). Kein APK im Repo (`find -name '*.apk'` nicht in Extension-Histogramm).
- **Speicher-Rechenwert (keine Messung):** LOD0-Texturen RGBA8 ≈ 57 MiB, "keine Messung des Android-Grafikspeichers" (`docs/14_character_realism/DELIVERY.md:44`). Review-Budgets je Profil (process_pss_mib 384/512/768/1024 etc.) sind "QA review ceilings, not measured device capacity" (`quality_profiles.json` budget_policy).

## 10. Dokumentierte offene Defekte / Blocker (docs/23_aeterna, 23_handoff_sol, 27–33)

- `docs/23_aeterna/TRACKER.md:8` M1.1 Bewegungsgate BLOCKED "#48, letzter Lauf 101 Befunde"; `MASTERPLAN.md:5` "101 Blocker bei 61 vermessenen Assets … nicht erfolgreich als APK bestätigt".
- `TRACKER.md:11` M1.4 Build-8-Integrations-APK BLOCKED; `:16` M2.5 I-15 ff./Nebenmissionen BLOCKED (Original-DOCX fehlt); `:19` M3.3 Fold7 physische Abnahme BLOCKED; `:12` M2.1 "2 von 7 registrierten Truppenkörpern integriert".
- `docs/29_city_stand0/integration.json:3` `"status": "BLOCKED_NOT_RELEASED"`; Stages blocked 1/5/0/0/4 (camera, commerce_inventory, world_expansion, interiors_materials, presentation_delivery); alle 8 `release_gates` false; `missing_required_files` u. a. roman_city_test/collision.glb, forum_paving, city_manifest.json, roman_meshy_temple_0..3.glb, hero_meshy_italia_0..3.glb, tests/city_exploration_test.gd (letzteres weiterhin nicht vorhanden).
- `docs/29_city_stand0/README.md:3` "Status: NICHT FREIGEGEBEN".
- `docs/32_city_asset_integrations/REAL_CITY_PACK_STATUS.md:8-15` Stadt-GLBs fehlten, kleine Adaptertests bestanden "auf künstlichen Prüfknoten"; `:17-27` Tempel, Forum-GLBs, Crowd-Layout nicht abgenommen, Cinematic-Stadtszene aus KI-Konzept "besteht noch nicht als gemessene Android-Grafik"; `:74` "Ein komplett grüner Dummy-Adaptertest ist kein vollständiger Stadtnachweis."
- `docs/33_fold7_release/QA_CANDIDATE.md:103-108` fehlendes privates Tempelasset, CI-Renderer-Test "keine Adreno-830-Frametime-Messung"; `:116` Status sonst `QA_CANDIDATE_ONLY`.
- `docs/27_detail_review/INTEGRATION_INVENTORY.md:52-67` "Verbindliche noch offene Abnahmen" 1–5 (Vorher/Nachher-Bildpaar, einheitliche Wirtschaftsautorität/Save-Migration, Gesten = Sichtkontakt, realer Fold7-Test, unverbundene Blender-/Meshy-Assets); `:38-43` spätere Epochen-Architektur (amphitheatrum, Thermen) in 58-v.-Chr.-Handlung ungeprüft.
- `docs/28_city_sandbox/ORIGINAL_CHARACTERS_AND_CINEMATICS.md:4` Jugend-Rig: "Independent digit topology, full phoneme lip sync, visual combat/movement acceptance and physical Fold7 QA remain open"; `:6` "Adult identity morph/retargeting remains open".
- `coordination/consolidated_candidate_20261125.json` release_blockers: "Motion and equipment collision quality gate", "Physical Fold7 acceptance and original signing proof", "Exact-source visual acceptance incl. GL teardown", "Verify new combined code and standalone scene/menu reachability"; `ci_verified: false`; unresolved_divergent_paths u. a. world_model.gd, main.gd, game_world.gd.
- `coordination/youth_anatomical_repair_20261125.json` (betrifft HEAD-Commit): verification "CI and Fold7 visual acceptance required. Fused hilt removal and full garment correction not claimed before evidence."; `youth_motion_polish_20261125.json` limits "Real model export, regression and on-device visual review pending".
- `quality/motion_contract.json`: 4 offene Anforderungen (siehe Abschnitt 3); `quality/motion_reviews.json`: 0 Reviews.
- `docs/23_handoff_sol/UEBERGABE.md:100-122` (Stand 24.11.): I-15 nur konzipiert; I-16 ff. nicht begonnen; NQ-1-01…24 nicht umgesetzt; Pferde/Maultiere fehlen; Orte Nachbardorf/Marschlager fehlen; Finger-Rig, Schwert-/Axtkampf brauchen neue Rigs; Musik/Trailer "nur technisch geprüft … nicht durch Anhören"; Vorspann-Legion = 2D-Silhouetten. Hinweis: Die dortigen Punkte "Brückenschritte … noch im Code" und "Marcus fehlt in I-06, I-08, I-09" sind durch die heutigen Missions-JSON (bridges in I-02/I-08/I-10..I-14; `companions` in I-04/I-06/I-08/I-09) teilweise überholt – Wirksamkeit im Spiel hier nicht geprüft.
- `prototype/README.md:51` (0.3.0-village): "Bekannte Zeitabweichung dieser Prüffassung: Die Saisonanzeige der Weltkugel rechnet weiterhin ab 100 n. Chr." (README älter als Kampagnenstart 58 v. Chr.; Abweichung zwischen README und `campaign_arc.json` start_year_bce 58).

---

# Anhang: Rohtabellen
## A1. Blend files (cmd: find . -path ./.git -prune -o -iname "*.blend" -print0 | xargs -0 ls -l)

| Pfad | Bytes |
|---|---:|
| art/architecture/atrium_house.blend | 2214906 |
| art/frontier_people/archer.blend | 11332000 |
| art/frontier_people/civilian_man.blend | 11198529 |
| art/frontier_people/civilian_woman.blend | 11940470 |
| art/frontier_people/elder.blend | 12983176 |
| art/frontier_people/legionary.blend | 11305095 |
| art/frontier_people/officer.blend | 11340286 |
| art/frontier_people/spearman.blend | 11335557 |
| art/hero_quality/hero.blend | 12346050 |
| art/landscape/farmer.blend | 166128 |
| art/landscape/han_farmer.blend | 177426 |
| art/landscape/han_house.blend | 497415 |
| art/landscape/oak.blend | 1148961 |
| art/landscape/olive.blend | 994496 |
| art/landscape/pine.blend | 1046558 |
| art/landscape/wa_farmer.blend | 169347 |
| art/landscape/wa_house.blend | 515402 |
| art/landscape/wa_store.blend | 508336 |
| art/landscape/wood_longhouse.blend | 575676 |
| art/soldiers/archer.blend | 268275 |
| art/soldiers/heavy.blend | 278305 |
| art/soldiers/officer.blend | 284234 |
| art/soldiers/spear.blend | 275795 |
| art/source/frontier_hut.blend | 156379 |
| art/village/market.blend | 1212508 |
| art/village/palisade_bay.blend | 1129200 |
| art/village/palisade_silhouette.blend | 1119802 |
| art/village/storehouse.blend | 1191671 |
| art/village/timber_billet.blend | 1121555 |
| art/village/training_dummy.blend | 1147683 |
| art/village/workshop.blend | 1227585 |
| art/village_characters/villager.blend | 948088 |
| art/visual_benchmark/assets/architecture/frontier_house.blend | 4102438 |
| art/visual_benchmark/assets/architecture/house_foundation.blend | 3566148 |
| art/visual_benchmark/assets/architecture/village_well.blend | 3619168 |
| art/visual_benchmark/assets/characters/civilian_youth.blend | 2015316 |
| art/visual_benchmark/assets/characters/civilian_youth_lod1.blend | 2383268 |
| art/visual_benchmark/assets/characters/frontier_guard.blend | 2566380 |
| art/visual_benchmark/assets/characters/frontier_guard_lod1.blend | 2748406 |
| art/weapons/gladius_lod0.blend | 1777000 |
| art/weapons/scutum_lod0.blend | 3384972 |
| art/weapons/weapons_review.blend | 7183817 |
| art/world_expansion/architecture/basalt_road.blend | 3606182 |
| art/world_expansion/architecture/basilica.blend | 6060027 |
| art/world_expansion/architecture/insula.blend | 4604078 |
| art/world_expansion/architecture/taberna.blend | 4145145 |
| art/world_expansion/vegetation/cypress.blend | 6978737 |
| art/world_expansion/vegetation/field_elm.blend | 7240018 |
| art/world_expansion/vegetation/laurel.blend | 5779923 |
| tools/city_detail/architecture_source.blend | 5642087 |
| tools/city_detail/characters_review/hero_city_detail.blend | 1852598 |

Summe: 191412602 Bytes, Anzahl 51

## A2. Blender-Python (cmd: grep -rl --include="*.py" "import bpy" . --exclude-dir=.git)

| Skript | Zeilen | Docstring (1. Zeile) |
|---|---:|---|
| art/architecture/atrium_materials.py | 122 | Paint the domus material atlas and derive tangent-space relief from original height fields. |
| art/architecture/build_atrium.py | 319 | Build the original walk-in tetrastyle courtyard house for the Italia village. |
| art/landscape/build.py | 253 | Original Mediterranean tree generator for Blender 4.3.2. |
| art/landscape/build_settlements.py | 438 | Build three settlement studies and a field worker. Blender 4.3.2, original meshes. |
| art/landscape/render_cutouts.py | 115 | Render transparent cutouts from the actual deliverable GLBs, then assemble contact sheets. |
| art/landscape/render_meshy_views.py | 174 | Render seven native IMPERATHEN game characters as true-scale, four-view RGBA references. |
| art/soldiers/build.py | 683 | Build the original IMPERATHEN field company with Blender 4.3. |
| art/village/build_props.py | 317 | Original market, joinery shed, store, stake bay, practice target and timber billet. |
| art/village_characters/build.py | 226 | Bind the project's original civilian study to a tailored 15-joint rig. |
| art/village_characters/soldier_lods.py | 17 | Derive the two distant animated village NPC tiers from the authored company rigs. |
| tools/art/build_architecture.py | 683 | Original, dimensioned frontier architecture for IMPERATHEN's art benchmark. |
| tools/art/build_characters.py | 647 | Original static character art studies for IMPERATHEN. |
| tools/art/common.py | 212 | Shared, deterministic Blender authoring helpers for IMPERATHEN's mobile art kit. |
| tools/build_frontier_hut.py | 185 | Create IMPERATHEN's original prototype hut with Blender's bundled Python. |
| tools/build_roman_weapons.py | 292 | Original procedural Gladius + Scutum asset generator for IMPERATHEN. |
| tools/characters/build_frontier_people.py | 548 | Build the living frontier cast from the already vendored anatomical hero. |
| tools/characters/build_hand_grips.py | 141 | Build an original fist correction from the bundled CC0 anatomical finger rig. |
| tools/characters/build_hero.py | 1001 | Reproducible, offline anatomical hero build for IMPERATHEN. |
| tools/characters/build_meshy_equipment.py | 197 | Fit existing Meshy equipment to metre-sized, grip-centred mobile GLBs. |
| tools/characters/build_meshy_people.py | 651 | Build fitted, skinned mobile derivatives of the two usable Meshy people. |
| tools/characters/build_meshy_player.py | 213 | Fit the user's original Meshy youth to a metric, editable biped. |
| tools/characters/build_soldier_faces.py | 453 | Author twenty adult soldier identities from the vendored anatomical cast. |
| tools/characters/extend_youth_rig.py | 146 | Supplementary, deforming joints for the confirmed original Meshy youth. |
| tools/city_detail/architecture_generate.py | 441 | Build the original, metre-scaled IMPERATHEN city detail kit with Blender. |
| tools/city_detail/characters_blender.py | 229 | Inspect and render the real hero with the optional city material pass. |
| tools/city_detail/characters_package.py | 162 | Keep the editable Blender review and complete, lossless relative textures. |
| tools/roman_material_lab/apply_blender.py | 41 | Blender material helper. No files or existing materials overwritten. |
| tools/ui/render_legion_silhouettes.py | 100 | Render side-view march-cycle silhouettes of the project's own field company rigs. |
| tools/viva_roma/blender_prop_review.py | 154 | Render actual committed GLBs under a consistent photographic studio rig. |
| tools/world/build_meshy_temple.py | 43 | Recover the user's Meshy temple as a metric, editable mobile asset. |
| tools/world/build_roman_test_city.py | 912 | Original metric Roman architecture for IMPERATHEN. |
| tools/world/prepare_private_temple.py | 154 | Prepare a privately supplied Meshy temple for the RomanCity 44x46-m plot. |
| tools/world/render_viva_roma_fixture.py | 60 | Real 3D Blender A/B fixture: same geometry, lighting, exposure and camera. |
| tools/world/viva_roma_surfaces.py | 135 | Original periodic PBR surface baker for Roman city geometry, no external art. |
| tools/world_art/architecture.py | 766 | Original modular Roman-inspired architecture for IMPERATHEN. |
| tools/world_art/vegetation.py | 728 | Original, wind-ready vegetation for IMPERATHEN. Blender 4.3+, metres. |

## B1. GLB-Tabelle (eigenes Skript scripts/glbinfo.py: GLB-Header 12 B, chunk0-Länge @12, JSON ab Byte 20)

Referenzprüfung: "res" = exakter res://-Pfad in prototype/**/*.gd|.tscn|.tres|.json|.godot|.cfg (ohne .import); "base" = nur Dateiname; "NONE" = kein Treffer. Achtung: Format-String-Ladepfade (z.B. "frontier_%s_%d.glb", "portal_lod%d.glb", "terrain_%d.glb") werden so nicht erkannt -> manuell geprüft, siehe Abschnitt B2.

| Pfad | MB | Skin-Joints | Anim | Clips | Meshes | Mat | Img | Ref |
|---|---:|---|---:|---|---:|---:|---:|---|
| art/visual_benchmark/assets/architecture/frontier_house.glb | 6.03 | - | 0 | - | 9 | 9 | 15 | base |
| art/visual_benchmark/assets/architecture/house_foundation.glb | 2.15 | - | 0 | - | 4 | 4 | 6 | NONE |
| art/visual_benchmark/assets/architecture/village_well.glb | 4.11 | - | 0 | - | 10 | 10 | 15 | base |
| art/visual_benchmark/assets/characters/civilian_youth.glb | 2.46 | - | 0 | - | 4 | 4 | 9 | NONE |
| art/visual_benchmark/assets/characters/civilian_youth_lod1.glb | 2.09 | - | 0 | - | 4 | 4 | 9 | NONE |
| art/visual_benchmark/assets/characters/frontier_guard.glb | 1.84 | - | 0 | - | 7 | 7 | 6 | NONE |
| art/visual_benchmark/assets/characters/frontier_guard_lod1.glb | 1.49 | - | 0 | - | 7 | 7 | 6 | NONE |
| prototype/assets/architecture/atrium_house.glb | 2.87 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/architecture/frontier_house.glb | 6.03 | - | 0 | - | 9 | 9 | 15 | res |
| prototype/assets/architecture/village_well.glb | 4.11 | - | 0 | - | 10 | 10 | 15 | res |
| prototype/assets/characters/frontier_archer_0.glb | 3.44 | 17 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_archer_1.glb | 2.18 | 17 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_archer_2.glb | 0.66 | 17 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_archer_3.glb | 0.31 | 17 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_man_0.glb | 4.77 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_man_1.glb | 2.43 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_man_2.glb | 0.73 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_man_3.glb | 0.32 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_woman_0.glb | 5.06 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 16 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_woman_1.glb | 2.52 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 16 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_woman_2.glb | 0.76 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 16 | 5 | 9 | res |
| prototype/assets/characters/frontier_civilian_woman_3.glb | 0.33 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 16 | 5 | 9 | res |
| prototype/assets/characters/frontier_elder_0.glb | 4.24 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 6 | 9 | res |
| prototype/assets/characters/frontier_elder_1.glb | 2.28 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 6 | 9 | res |
| prototype/assets/characters/frontier_elder_2.glb | 0.69 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 6 | 9 | res |
| prototype/assets/characters/frontier_elder_3.glb | 0.31 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 6 | 9 | res |
| prototype/assets/characters/frontier_legionary_0.glb | 3.45 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_legionary_1.glb | 2.16 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_legionary_2.glb | 0.65 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_legionary_3.glb | 0.29 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_officer_0.glb | 3.48 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_officer_1.glb | 2.19 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_officer_2.glb | 0.65 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_officer_3.glb | 0.30 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_spearman_0.glb | 3.47 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_spearman_1.glb | 2.18 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_spearman_2.glb | 0.65 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/frontier_spearman_3.glb | 0.29 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 13 | 5 | 9 | res |
| prototype/assets/characters/hero_quality_0.glb | 8.83 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 5 | 9 | res |
| prototype/assets/characters/hero_quality_1.glb | 5.76 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 5 | 9 | res |
| prototype/assets/characters/hero_quality_2.glb | 2.89 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 5 | 9 | res |
| prototype/assets/characters/hero_quality_3.glb | 0.93 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 14 | 5 | 9 | res |
| prototype/assets/city_detail/architecture/city_architecture_library.glb | 11.98 | - | 0 | - | 21 | 11 | 21 | res |
| prototype/assets/frontier_hut.glb | 0.47 | - | 0 | - | 8 | 8 | 0 | res |
| prototype/assets/landscape/farmer.glb | 0.21 | 15 | 2 | idle, march | 1 | 1 | 2 | res |
| prototype/assets/landscape/han_farmer.glb | 0.26 | 15 | 2 | idle, march | 1 | 1 | 2 | res |
| prototype/assets/landscape/han_house.glb | 0.63 | - | 0 | - | 1 | 1 | 1 | res |
| prototype/assets/landscape/oak_far.glb | 0.36 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/oak_mid.glb | 0.73 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/oak_near.glb | 1.89 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/olive_far.glb | 0.37 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/olive_mid.glb | 0.70 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/olive_near.glb | 1.52 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/pine_far.glb | 0.36 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/pine_mid.glb | 0.68 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/pine_near.glb | 1.66 | - | 0 | - | 1 | 2 | 2 | res |
| prototype/assets/landscape/wa_farmer.glb | 0.22 | 15 | 2 | idle, march | 1 | 1 | 2 | res |
| prototype/assets/landscape/wa_house.glb | 0.66 | - | 0 | - | 1 | 1 | 1 | res |
| prototype/assets/landscape/wa_store.glb | 0.67 | - | 0 | - | 1 | 1 | 1 | res |
| prototype/assets/landscape/wood_longhouse.glb | 0.95 | - | 0 | - | 1 | 1 | 1 | res |
| prototype/assets/meshy/characters/unit_germanic_axe_fighter_0.glb | 3.90 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_germanic_axe_fighter_1.glb | 1.06 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_germanic_axe_fighter_2.glb | 0.47 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_germanic_axe_fighter_3.glb | 0.28 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_roman_signifer_0.glb | 4.42 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_roman_signifer_1.glb | 1.13 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_roman_signifer_2.glb | 0.52 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/characters/unit_roman_signifer_3.glb | 0.30 | 15 | 11 | idle, march, run, talk, work, gather, train, attack, block, hit, death | 1 | 1 | 3 | res |
| prototype/assets/meshy/equipment/gear_germanic_round_shield_0.glb | 0.19 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/meshy/equipment/gear_germanic_round_shield_1.glb | 0.07 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/meshy/equipment/gear_roman_signum_0.glb | 0.13 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/meshy/equipment/gear_roman_signum_1.glb | 0.05 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/meshy/equipment/weap_germanic_hand_axe_0.glb | 0.09 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/meshy/equipment/weap_germanic_hand_axe_1.glb | 0.04 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/soldiers/archer_far.glb | 0.02 | - | 0 | - | 1 | 1 | 2 | res |
| prototype/assets/soldiers/archer_mid.glb | 0.29 | 17 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/archer_near.glb | 0.37 | 17 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/heavy_far.glb | 0.02 | - | 0 | - | 1 | 1 | 2 | res |
| prototype/assets/soldiers/heavy_mid.glb | 0.33 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/heavy_near.glb | 0.42 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/officer_far.glb | 0.03 | - | 0 | - | 1 | 1 | 2 | res |
| prototype/assets/soldiers/officer_mid.glb | 0.34 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/officer_near.glb | 0.44 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/spear_far.glb | 0.02 | - | 0 | - | 1 | 1 | 2 | res |
| prototype/assets/soldiers/spear_mid.glb | 0.33 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/soldiers/spear_near.glb | 0.41 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village/market.glb | 0.23 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village/palisade_bay.glb | 0.03 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village/palisade_silhouette.glb | 0.01 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village/storehouse.glb | 0.18 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village/timber_billet.glb | 0.01 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village/training_dummy.glb | 0.07 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village/workshop.glb | 0.27 | - | 0 | - | 1 | 1 | 3 | res |
| prototype/assets/village_characters/heavy_2.glb | 0.23 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village_characters/heavy_3.glb | 0.18 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village_characters/officer_2.glb | 0.24 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village_characters/officer_3.glb | 0.19 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village_characters/spear_2.glb | 0.23 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village_characters/spear_3.glb | 0.18 | 15 | 11 | idle, march, run, attack, shoot, block, wall, testudo, hit, retreat, death | 1 | 1 | 2 | res |
| prototype/assets/village_characters/villager_0.glb | 3.58 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 10 | 10 | 9 | res |
| prototype/assets/village_characters/villager_1.glb | 2.74 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 10 | 10 | 9 | res |
| prototype/assets/village_characters/villager_2.glb | 2.28 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 10 | 10 | 9 | res |
| prototype/assets/village_characters/villager_3.glb | 2.03 | 15 | 11 | idle, march, run, gather, talk, train, work, block, attack, hit, death | 10 | 10 | 9 | res |
| prototype/assets/viva_country/hinterland.glb | 1.14 | - | 0 | - | 4 | 3 | 0 | base |
| prototype/assets/viva_country/scatter_library.glb | 0.13 | - | 0 | - | 8 | 3 | 0 | base |
| prototype/assets/viva_country/terrain_0.glb | 1.33 | - | 0 | - | 1 | 3 | 0 | NONE |
| prototype/assets/viva_country/terrain_1.glb | 1.33 | - | 0 | - | 1 | 3 | 0 | NONE |
| prototype/assets/viva_country/terrain_2.glb | 1.33 | - | 0 | - | 1 | 3 | 0 | NONE |
| prototype/assets/viva_roma/props/helmet_lod0.glb | 0.83 | - | 0 | - | 1 | 4 | 0 | base |
| prototype/assets/viva_roma/props/helmet_lod1.glb | 0.40 | - | 0 | - | 1 | 4 | 0 | NONE |
| prototype/assets/viva_roma/props/helmet_lod2.glb | 0.20 | - | 0 | - | 1 | 4 | 0 | NONE |
| prototype/assets/viva_roma/props/portal_lod0.glb | 0.61 | - | 0 | - | 1 | 5 | 0 | base |
| prototype/assets/viva_roma/props/portal_lod1.glb | 0.35 | - | 0 | - | 1 | 5 | 0 | NONE |
| prototype/assets/viva_roma/props/portal_lod2.glb | 0.26 | - | 0 | - | 1 | 5 | 0 | NONE |
| prototype/assets/weapons/gladius_lod0.glb | 1.31 | - | 0 | - | 1 | 3 | 6 | res |
| prototype/assets/weapons/scutum_lod0.glb | 2.23 | - | 0 | - | 1 | 4 | 9 | res |
| prototype/assets/world_expansion/architecture/basalt_road_lod0.glb | 1.62 | - | 0 | - | 1 | 2 | 3 | res |
| prototype/assets/world_expansion/architecture/basalt_road_lod1.glb | 0.65 | - | 0 | - | 1 | 2 | 3 | res |
| prototype/assets/world_expansion/architecture/basalt_road_lod2.glb | 0.59 | - | 0 | - | 1 | 2 | 3 | res |
| prototype/assets/world_expansion/architecture/basilica_lod0.glb | 10.08 | - | 0 | - | 1 | 7 | 12 | res |
| prototype/assets/world_expansion/architecture/basilica_lod1.glb | 2.65 | - | 0 | - | 1 | 6 | 12 | res |
| prototype/assets/world_expansion/architecture/basilica_lod2.glb | 1.95 | - | 0 | - | 1 | 3 | 9 | res |
| prototype/assets/world_expansion/architecture/insula_lod0.glb | 7.73 | - | 0 | - | 1 | 7 | 12 | res |
| prototype/assets/world_expansion/architecture/insula_lod1.glb | 2.85 | - | 0 | - | 1 | 6 | 12 | res |
| prototype/assets/world_expansion/architecture/insula_lod2.glb | 2.78 | - | 0 | - | 1 | 4 | 12 | res |
| prototype/assets/world_expansion/architecture/taberna_lod0.glb | 6.13 | - | 0 | - | 1 | 8 | 15 | res |
| prototype/assets/world_expansion/architecture/taberna_lod1.glb | 3.14 | - | 0 | - | 1 | 8 | 15 | res |
| prototype/assets/world_expansion/architecture/taberna_lod2.glb | 2.41 | - | 0 | - | 1 | 4 | 12 | res |
| prototype/assets/world_expansion/vegetation/cypress_lod0.glb | 8.98 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/cypress_lod1.glb | 2.56 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/cypress_lod2.glb | 0.65 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/field_elm_lod0.glb | 9.59 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/field_elm_lod1.glb | 2.65 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/field_elm_lod2.glb | 0.74 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/laurel_lod0.glb | 6.26 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/laurel_lod1.glb | 1.83 | - | 0 | - | 1 | 2 | 6 | res |
| prototype/assets/world_expansion/vegetation/laurel_lod2.glb | 0.49 | - | 0 | - | 1 | 2 | 6 | res |

### Gruppiert nach Verzeichnis

| Verzeichnis | Dateien | MB |
|---|---:|---:|
| art/visual_benchmark/assets/architecture | 3 | 12.3 |
| art/visual_benchmark/assets/characters | 4 | 7.9 |
| prototype/assets | 1 | 0.5 |
| prototype/assets/architecture | 3 | 13.0 |
| prototype/assets/characters | 32 | 69.2 |
| prototype/assets/city_detail/architecture | 1 | 12.0 |
| prototype/assets/landscape | 16 | 11.9 |
| prototype/assets/meshy/characters | 8 | 12.1 |
| prototype/assets/meshy/equipment | 6 | 0.6 |
| prototype/assets/soldiers | 12 | 3.0 |
| prototype/assets/village | 7 | 0.8 |
| prototype/assets/village_characters | 10 | 11.9 |
| prototype/assets/viva_country | 5 | 5.3 |
| prototype/assets/viva_roma/props | 6 | 2.6 |
| prototype/assets/weapons | 2 | 3.5 |
| prototype/assets/world_expansion/architecture | 12 | 42.6 |
| prototype/assets/world_expansion/vegetation | 9 | 33.7 |

Gesamt: 137 GLB, 0 glTF, 254605340 Bytes; skinned+animiert: 61; LFS-Stubs: 0

## C1. Bilder/Videos je Verzeichnis (cmd: find . -path ./.git -prune -o -type f \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.webp" -o -iname "*.webm" -o -iname "*.mp4" \) -print | dirname-Gruppierung)

| Anzahl | Verzeichnis | Typen |
|---:|---|---|
| 7 | ./art/architecture/review | png:6 jpg:1  |
| 8 | ./art/frontier_people/review | png:8  |
| 13 | ./art/frontier_people/runtime_review | png:13  |
| 1 | ./art/hero_quality/reference | png:1  |
| 6 | ./art/hero_quality/review | png:6  |
| 1 | ./art/hero_quality/source | png:1  |
| 1 | ./art/hero_quality/source/system_assets/eyebrows/eyebrow001 | png:1  |
| 1 | ./art/hero_quality/source/system_assets/eyelashes/eyelashes01 | png:1  |
| 2 | ./art/hero_quality/source/system_assets/hair/short02 | png:2  |
| 1 | ./art/hero_quality/source/system_assets/skins/young_caucasian_female | png:1  |
| 1 | ./art/hero_quality/source/system_assets/skins/young_caucasian_male | png:1  |
| 2 | ./art/landscape/meshy | png:2  |
| 22 | ./art/landscape/review | png:22  |
| 6 | ./art/portraits/source | png:6  |
| 2 | ./art/soldiers | png:1 webm:1  |
| 3 | ./art/visual_benchmark/assets/architecture | png:3  |
| 2 | ./art/visual_benchmark/assets/characters | png:2  |
| 7 | ./art/visual_benchmark/evidence | png:7  |
| 24 | ./art/visual_benchmark/materials | png:24  |
| 1 | ./art/weapons | png:1  |
| 4 | ./art/world_expansion/architecture | png:4  |
| 3 | ./art/world_expansion/vegetation/review | png:3  |
| 1 | ./docs/10_development/evidence | png:1  |
| 3 | ./docs/10_development/evidence/android | png:3  |
| 7 | ./docs/12_integration/evidence | webp:7  |
| 2 | ./docs/12_integration/evidence/motion | webp:2  |
| 4 | ./docs/12_integration/evidence/portraits | webp:4  |
| 14 | ./docs/13_visual_upgrade/evidence | jpg:3 webp:11  |
| 19 | ./docs/14_character_realism/evidence/after | mp4:1 png:18  |
| 3 | ./docs/14_character_realism/evidence/android | png:3  |
| 11 | ./docs/14_character_realism/evidence/before | png:11  |
| 2 | ./docs/16_opening/evidence/android-ci | png:2  |
| 8 | ./docs/16_opening/evidence/render | mp4:1 png:7  |
| 8 | ./docs/16_opening/evidence/sky-seam | mp4:1 png:7  |
| 14 | ./docs/17_village_life/evidence/android-delivery/screenshots | png:14  |
| 8 | ./docs/18_childhood/evidence/performance | png:8  |
| 4 | ./docs/18_childhood/evidence/ui/before-hint-review | png:4  |
| 3 | ./docs/18_childhood/evidence/ui/final-hint-review | png:3  |
| 6 | ./docs/18_childhood/evidence/world | png:6  |
| 17 | ./docs/19_motion_combat/evidence/android/ci31 | png:17  |
| 12 | ./docs/19_motion_combat/evidence/family_route/ci1/graphical | png:12  |
| 21 | ./docs/19_motion_combat/evidence/motion/after | mp4:1 png:20  |
| 6 | ./docs/19_motion_combat/evidence/motion/before | mp4:1 png:5  |
| 3 | ./docs/19_motion_combat/evidence/touch | png:3  |
| 10 | ./docs/20_meshy_assets/evidence/characters-viewport | png:10  |
| 10 | ./docs/20_meshy_assets/evidence/equipment | png:10  |
| 9 | ./docs/24_ap1_ap2/evidence/visual/after | png:9  |
| 9 | ./docs/24_ap1_ap2/evidence/visual/before | png:9  |
| 4 | ./docs/25_team_a/evidence/contact-grounding/geometry-final | png:4  |
| 3 | ./docs/25_team_b/evidence/ci/after | png:3  |
| 3 | ./docs/25_team_b/evidence/ci/before | png:3  |
| 3 | ./docs/26_city_support/evidence/architecture | png:3  |
| 4 | ./docs/26_city_support/evidence/market | png:4  |
| 1 | ./prototype/assets/atlas | jpg:1  |
| 36 | ./prototype/assets/characters | png:36  |
| 4 | ./prototype/assets/city_detail/characters | png:4  |
| 18 | ./prototype/assets/portraits | webp:18  |
| 21 | ./prototype/assets/roman_material_lab | png:21  |
| 1 | ./prototype/assets/terrain | png:1  |
| 1 | ./prototype/assets/ui | png:1  |
| 8 | ./prototype/assets/village | png:8  |
| 12 | ./prototype/assets/village_characters/faces | webp:12  |
| 15 | ./prototype/assets/weapons | png:15  |
| 4 | ./tools/city_detail/characters_review | png:4  |
| 13 | ./tools/city_detail/characters_review/textures | png:13  |

Summe: 483 Dateien (prototype 117, docs 231, art 118, tools 17)

## E1. Godot-Skripte prototype/scripts (cmd: find prototype/scripts -name "*.gd"; wc -l; erste ##/#-Kommentarzeile)

| Skript | Zeilen | Kopfkommentar |
|---|---:|---|
| prototype/scripts/battle/battle_model.gd | 655 | Offline, fixed-step rules for the stand-alone valley encounter. |
| prototype/scripts/battle/formation_slots.gd | 70 |  |
| prototype/scripts/battle/valley_field.gd | 82 | Connect arbitrary endpoints to the navigable grid with a collision-free segment. |
| prototype/scripts/cinematic/i00_stage.gd | 158 | Lightweight cinematic scaffold for I-00 "Drei Legionen im Regen". |
| prototype/scripts/cinematic/opening_checkpoint.gd | 52 | Presentation progress is separate from the authoritative campaign schema. |
| prototype/scripts/cinematic/opening_director.gd | 406 | Five authored shots in the production village. This camera owns no game state. |
| prototype/scripts/city_support_district.gd | 502 | Optional walkable market district. Existing campaign startup is unchanged. |
| prototype/scripts/city_support_panel.gd | 310 | Reusable market UI. The session owns every mutation and all persistence. |
| prototype/scripts/city_trade.gd | 526 | Optional city commerce. WorldModel alone commits transactions and saves. |
| prototype/scripts/city_trade_catalog.gd | 37 | Original game-balance catalog. Prices are not historical Roman price claims. |
| prototype/scripts/domain/combat_actions.gd | 358 | Timed, offline combat authority. The renderer cannot award a hit. |
| prototype/scripts/domain/npc_behavior.gd | 514 | Bounded, offline decisions for the existing residents. Intent is saved; physical |
| prototype/scripts/domain/npc_social_memory.gd | 156 | Bounded memories of observed, mutually chosen NPC conversations. These are |
| prototype/scripts/domain/unarmed_combat.gd | 58 | Compatibility entry point for the family chapter. |
| prototype/scripts/domain/village_economy.gd | 559 | Named people are assigned once. Production, food and repair share real elapsed time. |
| prototype/scripts/domain/world_model.gd | 1298 | Offline authority for the first playable chapter. Renderers only read `data`. |
| prototype/scripts/main.gd | 1120 |  |
| prototype/scripts/rendering/city_architecture_detail.gd | 146 | Reusable meshes for the separate city review. No campaign or economy authority. |
| prototype/scripts/rendering/city_character_detail.gd | 114 | Local surface detail for the optional city preview. Never modifies a shared |
| prototype/scripts/rendering/city_exploration_detail_adapter.gd | 153 | Optional local art details for the observed 5b5eecad RomanCity hierarchy. |
| prototype/scripts/rendering/city_surface_microdetail.gd | 189 | Procedural Roman facade and street microgeometry on existing *real* city meshes. |
| prototype/scripts/rendering/city_viva_closeup_props.gd | 112 | Visual attachment of existing VIVA ROMA glTF assets to existing RomanCity |
| prototype/scripts/rendering/fold7_quality_policy.gd | 81 | Sustained mobile graphics policy; only the 3D buffer changes. The selected |
| prototype/scripts/rendering/viva_roma_atmosphere.gd | 43 | One sun, shared scene state, no renderer migration and no camera changes. |
| prototype/scripts/roman_material_lab/material_preview.gd | 41 |  |
| prototype/scripts/story/campaign_bible.gd | 109 | Read-only access to the authored four-volume IMPERATHEN campaign order. |
| prototype/scripts/story/chapter_abilities.gd | 117 | Chapter I abilities: data-driven, stable IDs, persisted at village.abilities. |
| prototype/scripts/story/companion_follow.gd | 378 | Laufzeitnavigation für bestehende Figuren. Keine Spielstandsänderung und keine |
| prototype/scripts/story/companion_story.gd | 83 | Begleitregie aus Missionsmetadaten. Fortschritt und Beziehung stammen aus |
| prototype/scripts/story/frontier_chapter.gd | 306 | An original, finite chapter in the fictional Roman settlement. |
| prototype/scripts/story/life_path_policy.gd | 112 | Opt-in design component. Does not migrate or advance existing world saves. |
| prototype/scripts/story/mission_cinematics.gd | 60 | Real-time story transitions are presentation only. No animation can complete, |
| prototype/scripts/story/mission_data.gd | 78 | Loads authored mission steps from res://assets/story/missions/<band>/. |
| prototype/scripts/story/opening_story.gd | 202 | Authored presentation for the first day and the confirmed adult transition. |
| prototype/scripts/story/village_childhood.gd | 1057 | Authored childhood chapters. Only explicit, nearby actions advance their order. |
| prototype/scripts/story/village_life.gd | 890 | Shared village life: original fictional encounters in a documented historical era. |
| prototype/scripts/strategy/atlas_data.gd | 37 | Site coordinates anchor representative regions. Edges are fictional campaign routes. |
| prototype/scripts/strategy/atlas_screen.gd | 136 |  |
| prototype/scripts/strategy/globe.gd | 189 |  |
| prototype/scripts/strategy/origins.gd | 9 | Fictional protagonists use regional settings around AD 100. |
| prototype/scripts/strategy/season_campaign.gd | 366 | Seasonal board-game campaign. Its regional mandate is independent of the protagonist's rank. |
| prototype/scripts/tactics/valley_hud.gd | 158 |  |
| prototype/scripts/tactics/valley_landscape.gd | 133 | The mesh, command cursor and formation slots sample the same height function. |
| prototype/scripts/tactics/valley_scene.gd | 526 |  |
| prototype/scripts/tests/city_architecture_detail_test.gd | 78 | Exercises exported resources and real physics openings in an isolated scene. |
| prototype/scripts/ui/android_graphics_probe.gd | 198 | Runs only when a debug QA installation has an explicit app-private request. |
| prototype/scripts/ui/android_village_diagnostics.gd | 330 | Read-only geometry/state observer for debug Android runtime verification. |
| prototype/scripts/ui/combat_action.gd | 23 | Original vector glyphs. Text stays visible for readability and accessibility. |
| prototype/scripts/ui/figure_gallery.gd | 187 |  |
| prototype/scripts/ui/game_ui.gd | 2296 | UI owns presentation and preferences; the entry point owns world pause and commands. |
| prototype/scripts/ui/imperial_fx.gd | 408 | Cinematic set pieces shared by the title sequence and the main menu: marching |
| prototype/scripts/ui/imperial_intro.gd | 722 | Real-time title sequence in trailer rhythm. Drum hits reveal a marching legion |
| prototype/scripts/ui/opening_overlay.gd | 438 | Presentation only: the entry point owns the cinematic clock and game state. |
| prototype/scripts/ui/roman_style.gd | 355 | Roman presentation kit for the title screens: Cinzel lettering (SIL OFL 1.1), |
| prototype/scripts/ui/runtime_display.gd | 281 | Opt-in frame measurements for the same viewport used by the village and valley. |
| prototype/scripts/ui/safe_area.gd | 17 | Convert display pixels (including letterboxing) to the root viewport's UI units. |
| prototype/scripts/ui/touch_action.gd | 86 | Standard button styling and focus, with independent native multitouch capture. |
| prototype/scripts/ui/touch_look.gd | 67 | Claims only unhandled world contacts; an owned finger stays owned over HUD controls. |
| prototype/scripts/ui/touch_modal.gd | 10 | Native scroll/list input runs first; all unused contacts remain inside this overlay. |
| prototype/scripts/ui/touch_pad.gd | 115 | A fixed analog pad owns exactly one pointer, including drags outside its bounds. |
| prototype/scripts/ui/ui_smoke.gd | 393 | Headless behavior/layout checks. tools/check_prototype.py supplies disposable user data. |
| prototype/scripts/ui/village_life_panel.gd | 265 | View-only panels: every consequential choice returns through the WorldModel. |
| prototype/scripts/ui/village_navigation.gd | 56 | North-up local map of the real authored houses; target comes from quest state. |
| prototype/scripts/visuals/cc0_gestures.gd | 42 | CC0 Quaternius/KinemIQ v1.0, retargeted by cc0_retarget_review.gd. |
| prototype/scripts/visuals/character_motion.gd | 753 | Own deterministic poses on the imported skin. Directions are solved in the |
| prototype/scripts/visuals/facial_motion.gd | 123 | Drives authored glTF face shapes on individual instances. This is expressive |
| prototype/scripts/visuals/far_ranks.gd | 140 | Batched silhouettes. Transforms supplied to sync() are local to this node. |
| prototype/scripts/visuals/grove.gd | 133 | Renders tree placements in grove-local metres, batched by species and camera distance. |
| prototype/scripts/visuals/hand_pose.gd | 104 | A skin-space hand grip for the existing fifteen-bone actor. Adds one local |
| prototype/scripts/visuals/meshy_asset_registry.gd | 101 | Optional access to Meshy source assets. |
| prototype/scripts/visuals/meshy_character_registry.gd | 25 | Only fitted derivatives may replace a live actor; source GLBs never do. |
| prototype/scripts/visuals/meshy_equipment.gd | 101 | Existing Meshy props fitted to live hand bones. Presentation only: this |
| prototype/scripts/visuals/meshy_loadout_registry.gd | 90 | Read-only modular presentation loadouts for staged Meshy assets. |
| prototype/scripts/visuals/natural_motion.gd | 163 | Original baked animation alternative from the childhood branch. |
| prototype/scripts/visuals/practice_gear.gd | 54 | Original lightweight borrowed practice props. Cosmetic; no combat authority. |
| prototype/scripts/visuals/service_gear.gd | 73 | Project-authored GLB service equipment. Both source sockets must resolve |
| prototype/scripts/visuals/soldier_visual.gd | 197 | A presentation-only soldier. The simulation owns movement, timing and casualties. |
| prototype/scripts/visuals/village_person.gd | 855 | Presentation of a domain person. The enclosing body owns collision and movement. |
| prototype/scripts/visuals/youth_sole_contact.gd | 142 | Bounded CPU sample of the original skinned sandals. The physics body keeps |
| prototype/scripts/viva_country/country_field.gd | 208 | Imported geometry is the source of truth. Grass and gravel never affect physics. |
| prototype/scripts/viva_country/country_scenery.gd | 98 | Visual continuation of the authored walkable area. No new physics or save data. |
| prototype/scripts/viva_country/country_sun.gd | 65 | Local art-directed daylight. Azimuth measured clockwise from world north (-Z). |
| prototype/scripts/viva_country/country_walk.gd | 149 | Independent first-person walk: no campaign, save or economy state. |
| prototype/scripts/viva_roma/closeup_lab.gd | 246 | Actual 3D asset review. No campaign, commerce, or save game access. |
| prototype/scripts/world/camera_obstruction.gd | 132 | Resolve a camera volume before and after smoothing. Vector3.INF means no |
| prototype/scripts/world/city_ambient_people.gd | 238 | Presentation-only Roman pedestrians on the existing authored market road. |
| prototype/scripts/world/city_exploration.gd | 830 | Independent free walk: no mission, economy or campaign simulation is started. |
| prototype/scripts/world/city_exploration_crowd.gd | 134 | Stream authored city occupants from the optional private model pack. |
| prototype/scripts/world/city_exploration_state.gd | 84 | Independent test-walk positions, never campaign state. |
| prototype/scripts/world/city_market_bridge.gd | 156 | A single market session for the existing exploration player and camera. |
| prototype/scripts/world/city_market_signage.gd | 134 | Read-only sign layer for the six real stall meshes in the Roman test city. |
| prototype/scripts/world/city_training.gd | 82 | Isolated, age-appropriate practice inside the existing Roman city. |
| prototype/scripts/world/companion_world.gd | 94 | Bindet dieselben vorhandenen Figuren an die Begleitregie. Nur beobachtete |
| prototype/scripts/world/game_world.gd | 1768 | Presentation and input only. World-changing decisions are emitted to the model. |
| prototype/scripts/world/player_action_world.gd | 192 | Geometry adapter for the authoritative combat state. Never awards damage. |
| prototype/scripts/world/regional_terrain.gd | 215 | Bounded terrain residency. The authored village remains the only inhabited region. |
| prototype/scripts/world/regional_village.gd | 251 | Static settlement dressing never owns the player's building, labour or resource ledger. |
| prototype/scripts/world/village/frontier_dressing.gd | 180 | Original civic details around the existing authored architecture, in metres. |
| prototype/scripts/world/village/opening_props.gd | 193 | Original, metre-scale dressing derived only from the confirmed companion choice. |
| prototype/scripts/world/village/settlement_dressing.gd | 244 | Original reusable household and craft props. No ledger or story side effects. |
| prototype/scripts/world/village/village_layout.gd | 120 | Authored streets and households. Presentation never changes the resource ledger. |
| prototype/scripts/world/village/village_local_navigation.gd | 41 | Small, deterministic local pedestrian planner. Every candidate leg is swept |
| prototype/scripts/world/village/village_population.gd | 553 | Stable residents and authored routines. No production, recruitment or narrative rewards. |
| prototype/scripts/world/village/village_scenery.gd | 360 | Rendering and decorative geometry for the playable frontier. Metres, +Y up. |
| prototype/scripts/world/village_art.gd | 257 | Original primitive art for the frontier prototype. All dimensions are metres. |
| prototype/scripts/world/village_life_world.gd | 568 | Visible people, workplaces and raiders are projections of saved village state. |
| prototype/scripts/world/world_smoke.gd | 263 | Standalone world integration checks; does not replace domain or visual tests. |
| prototype/scripts/world/world_stream_plan.gd | 230 | Deterministic regional coordinates and bounded streaming demand. |
| prototype/scripts/world_expansion/asset_library.gd | 166 | Imported meshes are shared across spatial MultiMesh batches. |
| prototype/scripts/world_expansion/atlas.gd | 116 | A drawn route graph from authored metre coordinates; no geographical claims. |
| prototype/scripts/world_expansion/environment_controller.gd | 175 | Independent presentation clock. It never mutates the story or saved world time. |
| prototype/scripts/world_expansion/preview.gd | 534 | Asset workshop attached through a separate scene. It has no WorldModel or save access. |
| prototype/scripts/world_expansion/settlement_layout.gd | 225 | Deterministic layout descriptors. Counts describe repeated modules, never unique buildings. |
| prototype/scripts/world_expansion/settlement_view.gd | 218 | Streets use bounded spatial batches. Overview uses one far-LOD batch per asset. |

Summe: 114 Dateien, 31626 Zeilen

## F1. Godot-Tests prototype/tests/*.gd (cmd: grep -oE PASS/RESULT-Literale je Datei)

| Test | Zeilen | Marker (Literal im Quelltext; nicht ausgeführt) |
|---|---:|---|
| prototype/tests/action_adapter_test.gd | 200 | ACTION_ADAPTER_PASS;ACTION_ADAPTER_RESULT checks=%d failures=%d; |
| prototype/tests/action_world_test.gd | 197 | ACTION_WORLD_PASS;ACTION_WORLD_RESULT checks=%d failures=%d;IMPERATHEN_ACTION_REPORT;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/band1_alignment_test.gd | 223 | BAND1_ALIGNMENT_PASS;BAND1_ALIGNMENT_RESULT checks=%d failures=%d; |
| prototype/tests/band1_expansion_test.gd | 172 | BAND1_EXPANSION_PASS;BAND1_EXPANSION_RESULT checks=%d failures=%d; |
| prototype/tests/battle_rules_test.gd | 308 | BATTLE_RULES_RESULT checks=%d failures=%d; |
| prototype/tests/camera_collision_test.gd | 405 | CAMERA_COLLISION_PASS;CAMERA_COLLISION_RESULT checks=%d failures=%d;CAMERA_OVERLAP_SEALED_RESULT: sealed local search reports;IMPERATHEN_CAMERA_REVIEW_LABEL; |
| prototype/tests/campaign_bible_test.gd | 35 | CAMPAIGN_BIBLE_TEST checks=; |
| prototype/tests/cc0_retarget_review.gd | 253 | CC0_RETARGET_REVIEW; |
| prototype/tests/cc0_runtime_gestures_test.gd | 73 | CC0_GESTURES_PASS;CC0_GESTURES_RESULT; |
| prototype/tests/chapter_abilities_test.gd | 164 | CHAPTER_ABILITIES_PASS;CHAPTER_ABILITIES_RESULT checks=%d failures=%d; |
| prototype/tests/character_motion_review.gd | 181 | CHARACTER_MOTION_REVIEW_PASS;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/character_motion_test.gd | 356 | CHARACTER_MOTION_TEST;LIVE_CONTACT_RESULT; |
| prototype/tests/childhood_integration_test.gd | 289 | CHILDHOOD_INTEGRATION_PASS;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_ambient_people_test.gd | 131 | CITY_AMBIENT_PEOPLE_PASS;CITY_AMBIENT_PEOPLE_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_character_detail_test.gd | 148 | CITY_CHARACTER_DETAIL: %d checks, %d failures;CITY_TEXTURE_RESOURCE ; |
| prototype/tests/city_crowd_layout_test.gd | 82 | CITY_CROWD_LAYOUT_PASS;CITY_CROWD_LAYOUT_RESULT;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_exploration_detail_adapter_test.gd | 162 | CITY_EXPLORATION_DETAIL_ADAPTER_TEST: %d checks, %d failures; real;CITY_EXPLORATION_DETAIL_PASS; |
| prototype/tests/city_inventory_ui_test.gd | 104 | CITY_INVENTORY_UI_PASS;CITY_INVENTORY_UI_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_market_bridge_test.gd | 64 | CITY_MARKET_BRIDGE_PASS;CITY_MARKET_BRIDGE_RESULT checks=%d failures=%d; |
| prototype/tests/city_market_signage_test.gd | 121 | CITY_MARKET_SIGNAGE_PASS;CITY_MARKET_SIGNAGE_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_support_graphics_review.gd | 84 | CITY_SUPPORT_GRAPHICS_CAPTURE;CITY_SUPPORT_GRAPHICS_PASS;CITY_SUPPORT_GRAPHICS_RESULT checks=%d failures=%d; |
| prototype/tests/city_support_integration_test.gd | 213 | CITY_SUPPORT_PASS;CITY_SUPPORT_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_surface_microdetail_test.gd | 124 | CITY_SURFACE_MICRODETAIL_PASS;CITY_SURFACE_MICRODETAIL_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_trade_test.gd | 316 | CITY_TRADE_PASS;CITY_TRADE_RESULT checks=%d failures=%d; |
| prototype/tests/city_training_test.gd | 54 | CITY_TRAINING_PASS;CITY_TRAINING_RESULT;CITY_TRAINING_TEST_REQUIRES_ISOLATION;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/city_viva_props_test.gd | 101 | CITY_VIVA_PROPS_PASS;CITY_VIVA_PROPS_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/combat_actions_test.gd | 383 | COMBAT_ACTIONS_PASS;COMBAT_ACTIONS_RESULT checks=%d failures=%d; |
| prototype/tests/combat_model_integration_test.gd | 326 | COMBAT_MODEL_PASS;COMBAT_MODEL_RESULT checks=%d failures=%d; |
| prototype/tests/commerce_transaction_test.gd | 262 | COMMERCE_TRANSACTION_PASS;COMMERCE_TRANSACTION_RESULT checks=%d failures=%d; |
| prototype/tests/companion_follow_test.gd | 206 | COMPANION_FOLLOW_PASS;COMPANION_FOLLOW_RESULT checks=%d failures=%d; |
| prototype/tests/companion_navigation_test.gd | 316 | COMPANION_NAVIGATION_PASS; |
| prototype/tests/companion_review.gd | 300 | COMPANION_REVIEW_FAIL;COMPANION_REVIEW_FAIL:;COMPANION_REVIEW_PASS;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/companion_turn_motion_test.gd | 128 | COMPANION_TURN_MOTION_PASS; |
| prototype/tests/compile_hand_grips.gd | 24 | HAND_GRIPS_COMPILED; |
| prototype/tests/domain_test.gd | 467 | DOMAIN_TEST_FAIL;DOMAIN_TEST_PASS;DOMAIN_TEST_RESULT suites=%d checks=%d failures=; |
| prototype/tests/figure_gallery_test.gd | 140 | FIGURE_GALLERY_RESULT checks=%d failures=%d real_te;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/fold7_graphics_policy_test.gd | 106 | FOLD7_GRAPHICS_POLICY_PASS checks=%d; deterministic 3D p;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/frontier_graphics_review.gd | 106 | FRONTIER_GRAPHICS_REVIEW;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/frontier_integration_test.gd | 387 | FRONTIER_INTEGRATION_PASS;FRONTIER_INTEGRATION_RESULT checks=%d failures=%d;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/frontier_people_review.gd | 107 | FRONTIER_GRAPHICS_REVIEW;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/frontier_people_test.gd | 131 | FRONTIER_PEOPLE_PASS; |
| prototype/tests/frontier_population_test.gd | 113 | FRONTIER_POPULATION_OK: %d checks; |
| prototype/tests/frontier_story_test.gd | 285 | FRONTIER_STORY_PASS;FRONTIER_STORY_RESULT checks=%d failures=%d; |
| prototype/tests/hero_cloth_grounding_test.gd | 75 | HERO_CLOTH_GROUNDING_TEST; |
| prototype/tests/hero_face_test.gd | 112 | HERO_FACE_TEST; |
| prototype/tests/hero_graphics_review.gd | 207 | HERO_CAPTURE;HERO_GRAPHICS_REVIEW;IMPERATHEN_ISOLATED_TEST; |
| prototype/tests/i00_stage_test.gd | 54 | I00_STAGE_PASS;I00_STAGE_RESULT checks=%d failures=%d; |
| prototype/tests/integration_test.gd | 352 | IMPERATHEN_ISOLATED_TEST;INTEGRATION_PASS: %d checks; real scene, UI/wo; |
| prototype/tests/life_path_policy_test.gd | 70 | LIFE_PATH_POLICY_RESULT checks=%d failures=%d; |
| prototype/tests/live_graphics_review.gd | 231 | IMPERATHEN_ISOLATED_TEST;LIVE_GRAPHICS_REVIEW; |
| prototype/tests/locomotion_regression_test.gd | 213 | IMPERATHEN_ISOLATED_TEST;IMPERATHEN_LOCOMOTION_REPORT;LOCOMOTION_PASS;LOCOMOTION_RESULT checks=%d failures=%d; |
| prototype/tests/lod_contact_history_test.gd | 112 | LOD_CONTACT_HISTORY_FAIL;LOD_CONTACT_HISTORY_METRICS;LOD_CONTACT_HISTORY_PASS; |
| prototype/tests/menu_review.gd | 40 | MENU_REVIEW_DONE;MENU_REVIEW_SHOT; |
| prototype/tests/meshy_asset_registry_test.gd | 73 | MESHY_ASSET_REGISTRY; |
| prototype/tests/meshy_character_test.gd | 222 | MESHY_CHARACTERS_PASS; |
| prototype/tests/meshy_equipment_test.gd | 201 | MESHY_EQUIPMENT_PASS; |
| prototype/tests/mission_bridge_data_test.gd | 128 | MISSION_BRIDGE_DATA_PASS;MISSION_BRIDGE_DATA_RESULT checks=%d failures=%d; |
| prototype/tests/mission_cinematics_test.gd | 28 | MISSION_CINEMATICS_PASS; |
| prototype/tests/motion_equipment_measurement.gd | 188 | MOTION_EQUIPMENT_CLIP; |
| prototype/tests/motion_mesh_clearance.gd | 204 |  |
| prototype/tests/motion_mesh_clearance_test.gd | 130 | MOTION_CLEARANCE_TEST; |
| prototype/tests/motion_quality_measurement.gd | 622 | IMPERATHEN_MOTION_REPORT; |
| prototype/tests/motion_quality_review.gd | 599 | IMPERATHEN_ISOLATED_TEST;MOTION_CAPTURE_RECORDED; |
| prototype/tests/motion_skin_measurement.gd | 286 |  |
| prototype/tests/motion_skin_measurement_test.gd | 126 | MOTION_SKIN_BACKEND;MOTION_SKIN_CONTACT;MOTION_SKIN_TEST; |
| prototype/tests/motion_world_measurement.gd | 554 |  |
| prototype/tests/natural_motion_test.gd | 189 | NATURAL_MOTION_TEST; |
| prototype/tests/npc_behavior_test.gd | 231 | NPC behavior: %d checks passed;NPC behavior: %d failures / %d checks; |
| prototype/tests/npc_behavior_ui_test.gd | 81 | IMPERATHEN_ISOLATED_TEST;NPC_BEHAVIOR_UI_PASS;NPC_BEHAVIOR_UI_RESULT checks=%d failures=%d; |
| prototype/tests/npc_behavior_world_test.gd | 373 | NPC behavior world: %d checks passed;NPC behavior world: %d failures / %d checks; |
| prototype/tests/npc_physical_conversation_test.gd | 128 | IMPERATHEN_ISOLATED_TEST;NPC_PHYSICAL_CONVERSATION_PASS;NPC_PHYSICAL_CONVERSATION_RESULT checks=%d failures=%d; |
| prototype/tests/npc_social_memory_test.gd | 385 | NPC social memory: %d checks passed;NPC social memory: %d failures / %d checks; |
| prototype/tests/opening_director_test.gd | 116 | OPENING_DIRECTOR_TEST; |
| prototype/tests/opening_flow_test.gd | 204 | IMPERATHEN_ISOLATED_TEST;OPENING_FLOW_PASS;OPENING_FLOW_RESULT checks=%d failures=%d; |
| prototype/tests/opening_graphics_review.gd | 262 | IMPERATHEN_ISOLATED_TEST;OPENING_CAPTURE;OPENING_GRAPHICS_REVIEW; |
| prototype/tests/opening_overlay_test.gd | 135 | OPENING_OVERLAY_RESULT checks=%d failures=%d; headle; |
| prototype/tests/opening_props_test.gd | 100 | IMPERATHEN_ISOLATED_TEST;OPENING_PROPS_RESULT checks=%d failures=%d; |
| prototype/tests/opening_story_test.gd | 143 | OPENING_STORY_PASS;OPENING_STORY_RESULT checks=%d failures=%d; |
| prototype/tests/regional_terrain_test.gd | 316 | REGIONAL_TERRAIN_RESULT checks=%d failures=%d; |
| prototype/tests/regional_worker_test.gd | 73 | REGIONAL_WORKERS_PASS; |
| prototype/tests/roman_city_real_asset_test.gd | 103 | IMPERATHEN_ISOLATED_TEST;ROMAN_CITY_REAL_ASSET_PASS;ROMAN_CITY_REAL_ASSET_RESULT; |
| prototype/tests/roman_city_real_walk_test.gd | 141 | IMPERATHEN_ISOLATED_TEST;ROMAN_CITY_REAL_WALK_PASS;ROMAN_CITY_REAL_WALK_RESULT; |
| prototype/tests/roman_city_visual_capture.gd | 132 | IMPERATHEN_ISOLATED_TEST;ROMAN_CITY_VISUAL_PASS;ROMAN_CITY_VISUAL_RESULT; |
| prototype/tests/roman_menu_test.gd | 356 | IMPERATHEN_ISOLATED_TEST;ROMAN_MENU_PASS headless intro/menu/music wir; |
| prototype/tests/runtime_display_lifecycle_test.gd | 118 | IMPERATHEN_ISOLATED_TEST;RUNTIME_DISPLAY_LIFECYCLE_PASS checks=%d; headless lifecycle; |
| prototype/tests/season_campaign_test.gd | 196 | SEASON_CAMPAIGN_RESULT checks=%d failures=%d; |
| prototype/tests/service_gear_test.gd | 53 | SERVICE_GEAR_PASS; |
| prototype/tests/settlement_geometry_test.gd | 159 | SETTLEMENT_GEOMETRY_PASS;SETTLEMENT_GEOMETRY_RESULT checks=%d failures=%d; |
| prototype/tests/settlement_graphics_review.gd | 103 | IMPERATHEN_ISOLATED_TEST;SETTLEMENT_GRAPHICS_REVIEW;SETTLEMENT_REVIEW_CAPTURE;SETTLEMENT_REVIEW_STAGE floor_settled; |
| prototype/tests/soldier_gait_test.gd | 187 | FAR_MOTION_RESULT max_support_slip_m=;SOLDIER_GAIT_RESULT checks=%d failures=%d max_sta; |
| prototype/tests/soldier_visual_test.gd | 164 | FAIL ;Soldier visuals: ; |
| prototype/tests/team_b_integration_test.gd | 207 | IMPERATHEN_ISOLATED_TEST;TEAM_B_INTEGRATION_PASS;TEAM_B_INTEGRATION_RESULT checks=%d failures=%d; |
| prototype/tests/team_b_review.gd | 138 | IMPERATHEN_ISOLATED_TEST;LIVING_WORLD_REVIEW_FAIL;LIVING_WORLD_REVIEW_PASS; |
| prototype/tests/touch_controls_test.gd | 406 | IMPERATHEN_ISOLATED_TEST;TOUCH_CONTROLS_PASS;TOUCH_CONTROLS_RESULT checks=%d failures=%d; |
| prototype/tests/touch_hud_review.gd | 134 | IMPERATHEN_ISOLATED_TEST;TOUCH_HUD_REVIEW_FAIL;TOUCH_HUD_REVIEW_PASS; |
| prototype/tests/unarmed_combat_test.gd | 99 | UNARMED_COMBAT_PASS checks=%d failures=%d; |
| prototype/tests/village_childhood_test.gd | 525 | VILLAGE_CHILDHOOD_PASS;VILLAGE_CHILDHOOD_RESULT checks=%d failures=%d; |
| prototype/tests/village_collision_test.gd | 129 | VILLAGE_COLLISION_PASS;VILLAGE_COLLISION_RESULT checks=%d failures=%d; |
| prototype/tests/village_economy_test.gd | 317 | VILLAGE_ECONOMY_PASS;VILLAGE_ECONOMY_RESULT checks=%d failures=%d; |
| prototype/tests/village_experience_test.gd | 669 | IMPERATHEN_ISOLATED_TEST;VILLAGE_EXPERIENCE_PASS;VILLAGE_EXPERIENCE_RESULT checks=%d failures=%d capture; |
| prototype/tests/village_life_test.gd | 358 | VILLAGE_LIFE_PASS;VILLAGE_LIFE_RESULT checks=%d failures=%d; |
| prototype/tests/village_model_test.gd | 368 | VILLAGE_MODEL_PASS;VILLAGE_MODEL_RESULT checks=%d failures=%d; |
| prototype/tests/village_nameplates_test.gd | 107 | VILLAGE_NAMEPLATES_PASS;VILLAGE_NAMEPLATES_RESULT checks=%d failures=%d; |
| prototype/tests/village_panel_test.gd | 237 | IMPERATHEN_ISOLATED_TEST;VILLAGE_PANEL_PASS;VILLAGE_PANEL_RESULT checks=%d failures=%d; |
| prototype/tests/village_person_test.gd | 167 | VILLAGE_PERSON_TEST; |
| prototype/tests/village_render_probe.gd | 181 | IMPERATHEN_ISOLATED_TEST;VILLAGE_RENDER_PROBE_PASS; |
| prototype/tests/village_static_art_test.gd | 81 | VILLAGE_STATIC_ART_TEST; |
| prototype/tests/visual_benchmark.gd | 97 | IMPERATHEN_ISOLATED_TEST;VISUAL_BENCHMARK_PASS; |
| prototype/tests/viva_character_finish_test.gd | 34 | VIVA_CHARACTER_FINISH_PASS (zusammengesetzt, Z.33) |
| prototype/tests/viva_country_test.gd | 116 | VIVA_COUNTRY_PASS %d checks (zusammengesetzt, Z.115) |
| prototype/tests/viva_roma_atmosphere_test.gd | 27 | VIVA_ROMA_ATMOSPHERE_PASS (zusammengesetzt, Z.26) |
| prototype/tests/world_expansion_environment_test.gd | 502 | WORLD_EXPANSION_TESTS; |
| prototype/tests/world_stream_plan_test.gd | 124 | WORLD_STREAM_PLAN_RESULT checks=%d failures=%d; |
| prototype/tests/youth_mocap_test.gd | 128 | YOUTH_MOCAP_RUNTIME_PASS;YOUTH_MOCAP_RUNTIME_RESULT; |

Summe: 114 .gd in prototype/tests; zusätzlich prototype/scripts/tests/city_architecture_detail_test.gd

## G1. tests/ (cmd: ls tests; ast-Docstring + Zahl der test*-Funktionen bzw. test(-Aufrufe)

| Datei | Zeilen | Beschreibung / Anzahl |
|---|---:|---|
| tests/android_action_payload_test.py | 85 | Package regressions for combat scripts and the four native hand resources. / test-Funktionen: 4 |
| tests/android_band1_alignment_test.py | 51 | Static checks for the Band 1 screenplay alignment of the childhood chapter. / test-Funktionen: 3 |
| tests/android_campaign_bible_test.py | 29 | - / test-Funktionen: 1 |
| tests/android_chapter_abilities_test.py | 51 | - / test-Funktionen: 4 |
| tests/android_childhood_payload_test.py | 80 | Package regression: a successful older APK cannot certify the family chapter. / test-Funktionen: 4 |
| tests/android_companion_capture_test.py | 154 | Der Bildnachweis darf echte Quellenänderungen nicht als Import kaschieren. / test-Funktionen: 14 |
| tests/android_frontier_payload_test.py | 85 | Reject old or incomplete deliveries using synthetic APK-content fixtures. / test-Funktionen: 5 |
| tests/android_hero_payload_test.py | 100 | Exercise the character-content gate using deliberately synthetic ZIP fixtures. / test-Funktionen: 5 |
| tests/android_living_village_payload_test.py | 90 | Reject APKs that leave the new story, NPC or Meshy data in the source tree. / test-Funktionen: 6 |
| tests/android_meshy_asset_test.py | 216 | - / test-Funktionen: 6 |
| tests/android_mission_data_test.py | 46 | - / test-Funktionen: 3 |
| tests/android_motion_build_test.py | 80 | Check that the Android exporter cannot substitute unapproved source bytes. / test-Funktionen: 4 |
| tests/android_package_isolation_test.py | 51 | Checks that QA APK export cannot overwrite the installed IMPERATHEN app. / test-Funktionen: 3 |
| tests/android_runtime_smoke_test.py | 1189 | Synthetic log timing regressions; these are not Android device evidence. / test-Funktionen: 100 |
| tests/android_signing_identity_test.py | 267 | Regression checks for APK signer evidence; no private keys or network access. / test-Funktionen: 22 |
| tests/android_story_source_audit_test.py | 165 | Story-source audit contracts, using synthetic DOCX files only. / test-Funktionen: 12 |
| tests/android_village_payload_test.py | 85 | Reject partial or stale village exports using explicitly synthetic ZIPs. / test-Funktionen: 6 |
| tests/android_village_publication_test.py | 198 | Exercise draft recovery without contacting GitHub or publishing test files. / test-Funktionen: 10 |
| tests/core.test.mjs | 305 | node:test für preview/core/game.mjs (Web-Vorschau) / test(-Aufrufe: 18 |
| tests/meshy_gait_reach_test.py | 231 | Blender-free FK regression for the actual Meshy gait generator. / test-Funktionen: 5 |
| tests/motion_quality_gate_test.py | 274 | Negative acceptance tests: missing, stale, malformed and excessive motion evidence. / test-Funktionen: 16 |
| tests/native_entry_test.cpp | 42 | C++ assert-Test für game/Imperathen EntryProgress (Unreal-Gerüst), via tools/verify_unreal.py |
| tests/pipeline_test.py | 174 | Regression coverage for coordination, evidence freshness and command boundaries. / test-Funktionen: 14 |
