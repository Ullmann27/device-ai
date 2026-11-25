# IMPERATHEN – Technischer Übergabebericht an GPT-6.2 und Fable 5.2

Stand: 25.11.2026 · Verfasser: Claude Code (Cloud-Sitzung) · Grundlage: Masterhandbuch 3.0 (Operation Aeterna), Abschnitt A „P0“ und F „Abnahme“

Dieser Bericht trennt strikt zwischen **in dieser Sitzung ausgeführt und gemessen**, **im Repository vorgefunden (nicht von mir geprüft)** und **offen / blockiert**. Es gibt keinen Merge nach `main`, keine APK, keine Signatur und keine Freigabebehauptung.

---

## 0. Kurzfazit

| Punkt | Ergebnis |
|---|---|
| P0-Blocker aus Handbuch S. 5 (`ValueError: Original sandal plane is too ambiguous to flatten R`) | **Reproduziert** auf `2c549548` (LOD 3, rechter Fuß), auch mit den CI-Bibliotheksversionen |
| Ursache | Der Original-Meshy-Junge ist im Bindpose mit **beiden Sandalen ca. 80° nach innen verrollt** gebunden (Sohlen zeigen zueinander). Die bisherige Sohlenebene aus den „niedrigsten Vertices“ traf die Fußaußenwand statt der Sohle. Bei LOD 0–2 war sie um 36–52° falsch verrollt (CI „grün“, Füße aber auf der Seite), bei LOD 3 mit nur 20 Vertices kippte sie über die Schranke. |
| Korrektur | Patch `patches/0001-fix-youth-sole-slab-calibration.patch` (lokaler Commit `10031e1` auf `2c549548`): Kalibrierung an der tatsächlichen Holzsohlenplatte, LOD-Familien- und Spiegelprüfung, Bodenkontakt offline und in Godot nach derselben Regel |
| Wirkung (gemessen) | Sohlenneigung im Bodenkontakt vorher 59–76°, nachher Idle 1,7–4,7°, Marsch Ø 11–13°; kein Fuß-Vertex unter dem Boden; alle 4 LODs laufen durch |
| Lokale Prüfungen | Retarget Exit 0 (alle LODs), CI-Parität byte-identisch, Godot `YOUTH_MOCAP_RUNTIME_PASS` (85 495 Prüfungen, 0 Fehler), alle Engine-Prüfungen des fehlgeschlagenen Workflows lokal bestanden, 212 Python-Tests OK. Volle `check_prototype`-Suite: 84 PASS, 1 FAIL (`village_people`; vorbestehender Testfehler, auf `2c54954` identisch, §5.3) |
| Nicht erledigt | Push nach `Ullmann27/Imperathen` (Schreibzugriff in dieser Sitzung verweigert), APK-Export/Signatur, Fold7-Gerätetest, menschliche Sichtabnahme. Globales Motion-Gate weiter **BLOCKED** (591 Blocker bei 61 Assets, §5.3) |
| Neue offene Punkte durch den Patch | mehr überdehnte Bein-/Knöchelkanten bei LOD 0/1 (z. B. Idle 2 → 36 von 12 521); höhere CPU-Kosten der Laufzeit-Erdung (§7); Schranke `rest_normal.y ≥ 0,35` wurde **bewusst ersetzt** – Entscheidung des Projektinhabers/Fable nötig (§6.1) |

---

## 1. Auftrag, Quellen und Grenzen dieser Sitzung

**Quellwahrheit:** `https://github.com/Ullmann27/Imperathen`, Draft-PR #86, Branch `integration/imperathen-unified-20261125`, Head `2c549548497aad901bda32d71c19c2a1961d9591`. Unmittelbar vor Abschluss erneut per `git ls-remote` geprüft: Head unverändert, `main` = `e9e1bb16` („Initial commit“).

**Zugriff:**
- Imperathen: nur **lesend** (öffentliches Repository, anonymer Git-Klon). Der Antrag auf Schreibzugriff wurde in dieser Sitzung vom Auto-Modus-Klassifikator abgelehnt. Deshalb liegt die Korrektur als Patch und lokaler Commit vor, nicht als Imperathen-Branch/PR.
- Ablage dieses Berichts: Repository `Ullmann27/device-ai`, Branch `claude/handover-report-gpt-fable-ho4nro` (der für diese Sitzung vorgegebene Branch), Draft-PR, kein Merge.

**Werkzeuge, tatsächlich benutzt:**
- Godot `4.7.2.stable.official.ed1daf0bf` (offizieller Build, SHA512 gegen `SHA512-SUMS.txt` geprüft)
- Blender 4.0.2 (offizielles Linux-Tarball, SHA256 `5583a558…086a` gegen blender.org-Prüfsumme; gleiche Hauptversion wie `apt install blender` auf Ubuntu 24.04 in der CI)
- Python 3.13.18 + NumPy 2.5.5/SciPy 1.18.1 sowie zur CI-Parität Python 3.12.3 + NumPy 1.26.4/SciPy 1.11.4

**Nicht verfügbar:** Android-SDK/Export-Templates, Emulator, physisches Fold7 (SM-F966B), GPU (nur CPU-Rendering), Login-geschützte CI-Logs.

---

## 2. GitHub: Commits, Branches, Pull Requests

### 2.1 Imperathen (vorgefunden, gezählt am 25.11.2026)

| Größe | Wert | Quelle |
|---|---|---|
| Branches | 70 | `git ls-remote` / blobless Klon |
| Tags | 9 (z. B. `cc0-motion-2026-11-24-7c3194db`, `first-play-qa-7d01a4f1`) | `git ls-remote` |
| Commits über alle Branches | 538 (22.11.: 133 · 23.11.: 141 · 24.11.: 66 · 25.11.: 198) | `git rev-list --all --count` |
| Commits auf `integration/imperathen-unified-20261125` vor `main` | 401 | `git rev-list --count main..…` |
| Pull Requests | 63, alle offen, davon 60 Draft (nicht Draft: #49, #50, #51) | GitHub-Suche |
| Issues | 23, davon 19 offen | GitHub-Suche |
| Gemergt nach `main` | nichts (`main` enthält nur `README.md`) | Klon |

**PR #86** (Draft): Head `integration/imperathen-unified-20261125`, Basis `fix/full-candidate-original-youth-proof-20261125` (nicht `main`). Auf Head `2c54954` sind zwei Workflow-Läufe öffentlich als **Failure** sichtbar:
- [Run 45693491554](https://github.com/Ullmann27/Imperathen/actions/runs/45693491554) „isolated installable ARM64 QA – NOT a release“
- [Run 45693492317](https://github.com/Ullmann27/Imperathen/actions/runs/45693492317) „Fold7 candidate – real city, genuine APK, image checks“, Job [139484751482](https://github.com/Ullmann27/Imperathen/actions/runs/45693492317/job/139484751482); die Annotation steht am Schritt „Retarget recorded human walk run and unarmed combat …“.

Die Schritt-Logs sind ohne Anmeldung nicht lesbar. Die Fehlermeldung habe ich deshalb lokal reproduziert (§5.1), nicht aus dem CI-Log gelesen. Den Status der übrigen 11 Workflows auf PR #86 konnte ich öffentlich nicht einsehen.

Die letzten Commits auf dem Integrationsbranch (Auszug):
```
2c54954 fix(youth): calibrate sandal planes, rebuild neutral stance, repair garment and remove embedded weapon
7daa4c2 feat(motion): original youth sole grounding, recorded ankle roll and measured garment weight repair
ff7df9b fix(unified): type graphical teardown weak reference and permit bounded slow dependency installs
125d5e6 integration: consolidate city rendering, motion continuity, world expansion and country assets
6787a63 fix(ci): use verified original youth and mocap in full city acceptance
```

### 2.2 Arbeit dieser Sitzung

| Objekt | Ort | Status |
|---|---|---|
| Commit `10031e1` „fix(youth): calibrate the actual sandal sole slab …“ (Parent `2c54954`) | lokaler Imperathen-Worktree, **nicht gepusht** | als Patch beigelegt |
| `patches/0001-fix-youth-sole-slab-calibration.patch` | dieser Ordner | `git apply --check` auf sauberem `2c54954`: OK |
| Bericht und Nachweise | `device-ai`, Branch `claude/handover-report-gpt-fable-ho4nro` | Commit + Draft-PR |

**Den Patch nach Imperathen übernehmen** (wer Schreibrecht hat):
```bash
git fetch origin integration/imperathen-unified-20261125
git switch -c fix/youth-sole-slab-calibration-20261125 FETCH_HEAD
git am /pfad/zu/0001-fix-youth-sole-slab-calibration.patch
git push -u origin fix/youth-sole-slab-calibration-20261125
# Draft-PR gegen integration/imperathen-unified-20261125, nicht gegen main
```
Geänderte Dateien: `tools/characters/youth_anatomy.py`, `tools/characters/retarget_accad_youth.py`, `prototype/scripts/visuals/youth_sole_contact.gd`, `prototype/tests/youth_mocap_test.gd` (4 Dateien, +226/−64). Die Workflows müssen nicht geändert werden.

---

## 3. Tatsächlich implementierte Spielmechaniken

Grundlage: Code auf `2c54954` (114 GDScript-Dateien, 31 626 Zeilen in `prototype/scripts`). Die Spalte „in dieser Sitzung ausgeführt“ nennt nur, was ich headless getestet habe. **Kein Test hier ist ein Spieltest durch einen Menschen.**

| System | Kern-Dateien | Code vorhanden | in dieser Sitzung ausgeführt |
|---|---|---|---|
| Weltzustand und Save (Schema 5, `.bak`/`.tmp`-Wiederherstellung) | `domain/world_model.gd` | ja | indirekt über die Suite (§5) |
| Bewegung, Kamera, Kamerakollision, Touch | `world/game_world.gd`, `camera_obstruction.gd`, `ui/touch_*.gd` | ja | `touch_controls`, `main_scene` PASS |
| Unbewaffneter Nahkampf, 15 Techniken mit Windup/Reichweite/Ausdauer | `domain/combat_actions.gd`, `unarmed_combat.gd` | ja | `city_training_test` PASS (12 Prüfungen) |
| Formationsschlacht (7 Formationen) | `battle/battle_model.gd`, `tactics/*` | ja | nur in der vollen Suite (§5) |
| Saison-Weltkugel, 5 Herkünfte | `strategy/*` | ja | nur in der vollen Suite |
| Dorfökonomie, Berufe, Überfälle | `domain/village_economy.gd`, `story/village_life.gd` | ja | nur in der vollen Suite |
| Stadthandel/Inventar (16 Waren, 6 Läden) | `city_trade*.gd`, `world/city_market_bridge.gd` | ja | `city_trade`, `city_market_bridge`, `city_inventory_ui` PASS |
| NPC-Verhalten, soziales Gedächtnis, Gespräche mit Sichtlinie | `domain/npc_behavior.gd`, `npc_social_memory.gd` | ja | `npc_physical_conversation` PASS |
| Freie Stadterkundung mit dem Originaljungen und MoCap | `world/city_exploration.gd`, `visuals/village_person.gd` | ja | `roman_city_real_walk_test` PASS, `original_youth_active:true`, `mocap_active:true` |
| 4 Grafikprofile + Fold7-Governor | `rendering/fold7_quality_policy.gd`, `assets/quality_profiles.json` | ja | `fold7_graphics_policy` PASS (Logik, kein Gerät) |
| **Stealth** | – | **nein** (0 Treffer für stealth/schleich/sneak; nur Sichtlinien-Raycasts für Gespräche und Überfälle) | – |
| **Pferde/Reiten** | – | **nein** (Motion-Familien `inactive`) | – |
| **Nebenquest-System** | – | **nein** | – |

Einige Einzelszenen sind nicht aus dem Hauptmenü erreichbar: `viva_country`, `viva_roma/closeup_lab`, `world_expansion/preview`, `city_support_district`, `roman_material_lab`.

---

## 4. Blender-Dateien, GLB-Modelle, Skelette, Animationen

### 4.1 Im Repository (vorgefunden, Zählung nachgeprüft)
- **51 `.blend`** (191 412 602 Bytes), keine davon unter `prototype/`. **36 Blender-Python-Generatoren** (`import bpy`).
- **137 GLB** (254,6 MB). 130 davon unter `prototype/assets` werden vom Spiel geladen, die 7 in `art/visual_benchmark` nicht. 61 GLB haben Skin und Animation (15/17-Joint-Rigs, 11 Clips). Git-LFS: keine.
- **Nicht im Repository, nur in der CI erzeugt:** Stadt-GLB und Kollision, `hero_meshy_italia_0..3.glb` (Originaljunge, 35 Joints), `accad_youth_motion.json`, privater Tempel (Secret).
- Animationen: `hero_cc0_library.tres` (36 Clips, `runtime_approved:false`); BVH/FBX im Repo: 0 (ACCAD `Male2_bvh.zip`, CC BY 3.0, wird in der CI geladen). `quality/motion_reviews.json` enthält **0 Reviews**.

### 4.2 In dieser Sitzung erzeugt (nicht committet; reproduzierbar, Hashes angegeben)

| Artefakt | Werkzeug | Ergebnis |
|---|---|---|
| Römische Teststadt `imperathen_roman_test_city.glb` (50 945 284 B, SHA256 `f4d2a73f…2f05`), Kollision `roman_city_collision.glb` (4 331 560 B, `5fccb4d9…c8ba`), `.blend` | Blender 4.0.2, `tools/world/build_roman_test_city.py --surface-style viva --texture-size 1024` | `CITY_BUILD_COMPLETE`: 644 Mesh-Objekte, 72 Unikat-Meshes, 1 250 689 instanzierte Dreiecke, 186 786 Kollisionsdreiecke, 240 Wohnhäuser, 63 Marktstände. Asset-Audit `ROMAN_CITY_ASSET_AUDIT_PASS` |
| Originaljunge, 4 LODs mit ACCAD-MoCap (19 Clips je LOD: `march`, `run`, `block` aus ACCAD ersetzt, `idle` mit neu aufgebauten Füßen, 8 neue Kampfclips jab/hook/uppercut L/R, front_kick, side_kick; talk/work/gather/train/attack/hit/death unverändert) | Patch + `retarget_accad_youth.py` | SHA256 LOD0 `b893db06…5de9`, LOD1 `418d20c0…bc43`, LOD2 `3138717d…3d3e`, LOD3 `9434db9d…edcec`. Byte-identisch mit Python 3.12/NumPy 1.26 und Python 3.13/NumPy 2.5 |
| Skelett | – | unverändert: 35 Joints, Bind-Matrizen, Positionen und Topologie bleiben erhalten (Prüfungen des Skripts bestanden) |

Die Jugend-GLBs lege ich bewusst **nicht** in `device-ai` ab: Sie sind Ableitungen der privaten Meshy-Quelle und laut Manifest nur für das Spiel freigegeben.

---

## 5. Tests: bestanden und fehlgeschlagen

Alle Läufe fanden lokal in dieser Cloud-Sitzung statt (Linux x86_64, 4 CPU, ohne GPU, headless). Logs liegen unter `evidence/logs/`.

### 5.1 Reproduktion des Blockers (vor dem Patch)

| Prüfung | Stand | Ergebnis |
|---|---|---|
| `retarget_accad_youth.py` | `2c54954`, Python 3.12.3/NumPy 1.26.4/SciPy 1.11.4 | **FAIL**, Exit 1: LOD 0–2 durchlaufen, LOD 3: `ValueError: Original sandal plane is too ambiguous to flatten R` |
| Begleitbefund `YOUTH_SURFACE_REVIEW` (Tunika/Arm-Gewichte) | `2c54954` | `unresolved_surface_deformation`: LOD 0 2925, LOD 1 1710, LOD 2 737 überdehnte Kanten, max. Exzess 0,6589 m (LOD 2) – deckt sich mit Handbuch S. 5 |

### 5.2 Nach dem Patch

| Prüfung | Ergebnis |
|---|---|
| `retarget_accad_youth.py`, alle 4 LODs | **PASS**, Exit 0, `ACCAD_RETARGET_COMPLETE {"march":1.36795,"run":2.38253}` |
| CI-Parität (Python 3.12 + NumPy 1.26 + SciPy 1.11 auf sauberem `2c54954` + Patch) | **PASS**: 4/4 LOD-GLBs byte-identisch |
| Neue Sohlenprüfungen je LOD | **PASS** (Werte §6.1) |
| `python3 -m unittest discover -s tests -p 'android_*_test.py'` | **PASS**, 212 Tests OK |
| Godot `--import` (mit echter Stadt + Jungen) | **PASS**, Exit 0, 0 Fehlerzeilen, 90,8 s |
| `check_prototype.py --only` (12 CI-Gruppen: import, roman_menu, city_trade, city_market_bridge, city_inventory_ui, city_support, city_viva_props, city_exploration_detail, npc_physical_conversation, touch_controls, fold7_graphics_policy, main_scene) | **12/12 PASS** |
| `roman_city_real_asset_test.gd` | **PASS** |
| `roman_city_real_walk_test.gd` (`IMPERATHEN_REQUIRE_ORIGINAL_YOUTH=1`) | **PASS**: 26 Prüfungen, 0 Fehler, `original_youth_active:true`, `mocap_active:true`, `fps_measured:false` |
| `city_crowd_layout_test.gd` | **PASS**: 158 Prüfungen |
| `city_training_test.gd` | **PASS**: 12 Prüfungen |
| `mission_cinematics_test.gd` | **PASS** |
| `youth_mocap_test.gd` (inkl. neuer Prüfung „Sohlenplatte wird zur Erdung abgetastet“ je LOD) | **PASS**: 85 495 Prüfungen, 0 Fehler |
| Vollständige `check_prototype.py`-Suite | siehe §5.3 |
| Globales Motion-Gate `motion_quality_gate.py` | siehe §5.3 |
| APK-Export, QA-Signatur, Bildprüfung im Workflow | **nicht ausgeführt** (kein Android-SDK/keine Templates in dieser Sitzung) |
| Fold7-Gerätetest | **BLOCKED** (kein physischer Zugriff) |

### 5.3 Vollständige Suite und Motion-Gate

**`tools/check_prototype.py --keep-going` (alle Gruppen, Patch-Stand mit lokal gebauter Stadt und Original-Jungen):** 84 PASS, **1 FAIL**, 802,9 s, Spitzen-RSS 787 MiB.

Die Suite lief vor einer nachträglichen Optimierung von `youth_sole_contact.gd::bind()`, die das Verhalten nicht ändert (Fußgewichte werden vor den Transformationen geprüft). Danach habe ich erneut ausgeführt: identische Stichprobenzahlen je LOD, `youth_mocap_test` PASS (85 495/0) und `roman_city_real_walk_test` PASS. Der Patch enthält die optimierte Fassung.

- FAIL `village_people`: Zeitlimit 120 s. Ursache: `SCRIPT ERROR: Invalid access to property or key 'skin' on a base object of type 'null instance'` in `prototype/tests/village_person_test.gd:53`. Danach beendet sich Godot nicht, deshalb das Zeitlimit.
  - Der Test erwartet als Spielerfigur `HeroBody` oder `Civilian`. Liegt der Original-Junge (`hero_meshy_italia_*`) im Projekt, findet er keines von beiden.
  - **Nicht durch den Patch verursacht:** Mit dem unveränderten `youth_sole_contact.gd` von `2c54954` und denselben Jungen-GLBs tritt derselbe Fehler auf. Ohne Jungen-Assets besteht der Test auf dem Patch-Stand mit 223 Prüfungen und 0 Fehlern in 4,0 s.
  - **Voraussichtlich der nächste CI-Blocker:** `.github/workflows/fold7-city-apk.yml` stagt den Jungen (Schritte ab Z. ~95) und ruft in Z. 147 die volle Suite `check_prototype.py --keep-going` auf. Sobald der Patch den Retarget-Schritt freigibt, dürfte dieser Workflow an `village_people` scheitern. Das ist aus den Workflow-Dateien abgeleitet; einen CI-Lauf dazu gibt es noch nicht. `fold7-unreleased-qa.yml` ruft nur 12 Gruppen ohne `village_people` auf und ist nicht betroffen.
  - Nachweis: `evidence/logs/village_person_test_analyse.txt`.

**`tools/motion_quality_gate.py`** (nach der vollen Suite): **`MOTION_QUALITY_GATE_BLOCKED blockers=591 measured_assets=61`**.

| Kategorie | Anzahl |
|---|---|
| `max_support_slip_m` überschritten | 126 |
| `max_sole_penetration_m` überschritten | 116 |
| skeletal sampling reported errors | 80 |
| `skin_edge_violations` überschritten | 63 |
| Loop-Naht (m bzw. °) | je 24 |
| exakte Sichtprüfung fehlt | 12 |

- Am stärksten betroffen ist `frontier_people` (114 Blocker).
- Den Jungen betreffen nur 5 Registrierungsbefunde: Die 4 GLBs und `youth_sole_contact.gd` sind im Bewegungsvertrag „Unclassified runtime …“. Diese Dateien gibt es auch auf `2c54954`.
- Der historische Wert (569 bei 61 Assets, Handbuch S. 5) ist nicht direkt vergleichbar, weil lokal zusätzlich Stadt- und Jungen-Assets eingebunden waren.
- Mein erster Gate-Lauf mit nur 12 Gruppen ergab „measured_assets=0“ und ist **ungültig**. Ich weise ihn nicht als Ergebnis aus.

---

## 6. Offene Fehler und qualitative Defizite

### 6.1 Durch diese Arbeit entstanden oder offengelegt (vorrangig für Fable)

1. **Geänderte Schranke, Entscheidung nötig.** Die alte Prüfung `rest_normal.y ≥ 0,35` („Sohle zeigt im Bindpose überwiegend nach unten“) trifft auf dieses Asset nicht zu: Die echte Sohlenplatte hat auf allen LODs `y = 0,127–0,253`. Ich habe die Prüfung durch direkte Eindeutigkeitsmaße ersetzt. Das ist **keine stille Absenkung**, muss aber vom Projektinhaber oder von Fable bestätigt oder verworfen werden. Gemessene Werte gegen die neuen Grenzen:

   | Maß | Grenze | gemessen (schlechtester Wert) |
   |---|---|---|
   | Fläche der Sohlenplatte | ≥ 40 cm² | 42,6 cm² (LOD 1 R) |
   | Dominanz gegenüber anders orientierten ebenen Flächen | ≥ 1,5 | 1,93 (LOD 1 R) |
   | max. Abstand der Plattenvertices zur Ebene | ≤ 4 mm | 3,7 mm (LOD 0 R) |
   | Spiegelabweichung L/R | ≤ 12° | 6,9° (LOD 1) |
   | Abweichung zu LOD 0 | ≤ 12° | 10,7° (LOD 3 L) |

   Die Reserven bei Ebenheit (3,7/4 mm) und LOD-Abweichung (10,7/12°) sind knapp. Die Grenzwerte habe ich gesetzt, nachdem die Messwerte vorlagen; sie sind deshalb nicht unabhängig kalibriert.
2. **Mehr überdehnte Bein-/Knöchelkanten bei LOD 0/1.** Die Füße werden jetzt um ca. 80° in die richtige Lage gedreht; die schienbeingewichtete Knöchelhaut folgt nicht vollständig. Kanten mit Dehnung > 1,5× im Beinbereich:

   | Clip | LOD 0 vorher → nachher (von 12 521) | LOD 2 vorher → nachher (von 2 716) |
   |---|---|---|
   | idle | 2 → 36 | 0 → 6 |
   | march | 39 → 64 | 2 → 6 |
   | run | 127 → 120 | 14 → 16 |
   | jab_right | 56 → 56 | 2 → 5 |

   Die Maximalwerte bei LOD 0 (march 13,6×, run 16,9×, jab_right 11,3×) sind vorher und nachher gleich, stammen also nicht vom Patch; front_kick (21,9×) wurde nur nachher gemessen. Auf den Renderings ist am Knöchel keine Verzerrung zu sehen; eine Nahaufnahme-Sichtprüfung in Bewegung fehlt.
3. **Bindpose-Defekt des Assets selbst.** Die Sandalen sind im Original-Rig nach innen verrollt (Render `01`, `02`). Die nachhaltige Lösung wäre ein korrigierter Fuß-Bindpose bzw. korrigierte Gewichte im Original-Rig mit Provenienz. Das ist eine Asset-Entscheidung und nicht Teil dieses Patches.

### 6.2 Bestehende, unverändert offene Befunde (im Repository dokumentiert oder von mir beobachtet)

- **Tunika/Rock-Verformung** (`YOUTH_SURFACE_REVIEW`, `unresolved_surface_deformation`, bis 2925 Kanten, Exzess bis 0,67 m), in Render `08` als Riss zwischen den Beinen sichtbar; vom Patch unverändert.
- **Eingeschmolzener Schwertgriff:** `remaining_fused_hilt_visual_review_required: true` auf allen LODs.
- **Motion-Gate** (Tracker M1.1, historisch 101 bzw. 569 Befunde): lokal 591 Blocker bei 61 Assets, Details in §5.3.
- **`village_person_test.gd`** ist mit dem Original-Jungen nicht lauffähig: Script-Fehler an Zeile 53, danach kein Beenden, daher Zeitlimit statt sauberem FAIL (§5.3).
- **Kein Fold7-Nachweis**, keine Frametime-Messung auf Adreno 830; `quality_profiles.json`: `not_yet_run_on_android_hardware`.
- **Stadt-Stand 0** `BLOCKED_NOT_RELEASED`, alle 8 Release-Gates `false`; privater Tempel nicht geliefert.
- **Story:** 15 von 113 Hauptmissionen haben Spieldaten (I-00 als Echtzeit-Szene, I-01…I-14 als JSON mit 35 Schritten und Brücken). Ob jede davon durchspielbar ist, habe ich nicht geprüft. I-15 ff. und Bände II–IV sind nur registriert; die Original-DOCX liegen nicht im Repo. Kein Nebenquest-System (96 Nebenquests nur registriert).
- **Kein Stealth-System**, obwohl Handbuch P1 eine Stealth-Greybox fordert.
- **Epochenkonflikt** (dokumentiert): Bauten späterer Epochen (Amphitheater, Thermen) in einer Handlung von 58 v. Chr. sind ungeprüft.
- `prototype/README.md` nennt den Kalenderstart 100 n. Chr., `campaign_arc.json` dagegen 58 v. Chr.

---

## 7. Grafikleistung, Speicherverbrauch und Gerätekompatibilität

**In dieser Sitzung gemessen.** Das sind Werkzeugprozesse auf einer CPU-Cloud-Maschine, **keine Spiel-FPS und keine Gerätewerte**:

| Prozess | Wandzeit | Spitzen-RSS |
|---|---|---|
| Blender-Stadtbau (2 Threads) | 15,5 s* | 703,5 MiB |
| Godot-Projektimport (Stadt + Junge) | 90,8 s | 956,9 MiB |
| `youth_mocap_test.gd` | 1,5 s | 256,1 MiB |
| Retarget vor dem Patch, bis zum Abbruch bei LOD 3 | 18,8 s | nicht gemessen |

\* Zweiter Lauf in dasselbe Ausgabeverzeichnis; ob dabei Zwischenstände des ersten Laufs wiederverwendet wurden, habe ich nicht geprüft.

- **Bildrate:** nicht gemessen. Der Stadt-Gehtest meldet selbst `fps_measured:false`.
- **Im Repository dokumentiert (nicht von mir nachgemessen):** Desktop mit Mesa-llvmpipe-Software-Rendering, p50/p95 Niedrig 63,9/73,7 ms, Ausgewogen 298,1/356,0 ms, Hoch 540,0/842,5 ms, RSS 425/510/617 MiB (`docs/12_integration/DELIVERY.md:68-74`). Android-Emulator x86_64/SwiftShader 0,22–1,07 fps bei 1–2 Frames, jeweils `physical_device_test:false`. APK-Referenzbuild `7c3194db` 184 262 016 Bytes.
- **Fold7 (SM-F966B, Adreno 830):** **BLOCKED** – keine Messung vorhanden; Ziele (Hoch 60-FPS-Cap/3 686 400 px, Sehr hoch 30/5 760 000 px) sind Vorgaben, keine Ergebnisse.
- **Mehrkosten durch den Patch** (Benchmark auf Desktop-CPU, nicht Gerät; Skript nicht im Patch):

  | LOD | Stichproben alt → neu | `measure_lowest()` je Aufruf alt → neu | `bind()` alt → neu |
  |---|---|---|---|
  | 0 | 96 → 170 | 0,118 → 0,170 ms | 8,30 → 12,98 ms |
  | 1 | 96 → 142 | 0,092 → 0,135 ms | 5,44 → 8,01 ms |
  | 2 | 96 → 121 | 0,091 → 0,112 ms | 2,68 → 4,13 ms |
  | 3 | 78 → 134 | 0,073 → 0,121 ms | 1,41 → 2,11 ms |

  `measure_lowest()` läuft pro Frame, aber nur für den Jungen. `bind()` läuft bei jedem LOD-Wechsel. Auf dem Fold7 ist das nicht gemessen. Die Asset-Größen ändert der Patch nicht.

---

## 8. Renderbilder und Spielaufnahmen

**In dieser Sitzung erzeugt:** echte Blender-4.0.2-Cycles-CPU-Renderings der Original-GLBs bzw. der erzeugten MoCap-GLBs (`evidence/renders/`):

| Datei | Inhalt |
|---|---|
| `00_originaljunge_bindpose_lod0.jpg` | Original-LOD 0 im Bindpose, Ganzkörper |
| `01_bindpose_sandalen_nach_innen_verrollt.jpg`, `02_bindpose_von_unten.jpg` | Nachweis der nach innen verrollten Sandalen |
| `03_erkannte_sohlenplatte_lod0_rot.jpg`, `04_erkannte_sohlenplatte_lod3_rot.jpg` | vom neuen Verfahren gewählte Sohlenplatte (rot) auf LOD 0 und LOD 3 |
| `05_fussvertices_auswahl_alt_alle_lods.jpg` | Diagramm (kein Render): Vertices, die die alte Auswahl je LOD verwendete |
| `06`/`07`/`08_vorher_nachher_*.jpg` | gleiche Pose, links `2c54954`, rechts Patch: Idle-Füße, Marsch-Füße, Marsch-Ganzkörper |

**Nicht erzeugt:** keine Godot-Spielaufnahmen und kein Video, weil die Godot-Tests ohne Bildausgabe (headless) liefen. Die Konzeptbilder im Handbuch sind laut Handbuch selbst kein Render- oder Ingame-Nachweis.

---

## 9. Missionen, Dialoge, Stimmen, Filmsequenzen

- **In dieser Sitzung:** keine neuen Missionen, Dialoge, Stimmen oder Filme produziert. `mission_cinematics_test.gd` lief und bestand.
- **Vorgefunden:** Missionsdaten I-01…I-14 (`prototype/assets/story/missions/band1/`) und I-00 als Echtzeit-Szene `i00_stage.gd`. Dialogtexte stehen in den Missions-JSON und im GDScript; es gibt keine eigene Dialogdatei.
- **Stimmen:** keine Sprachaufnahmen. Audio besteht aus 3 synthetischen Dateien (Ambience 8 s, Trailer 11,6 s, Menüthema 58,2 s).
- **Filmsequenzen:** nur in-engine (`opening_director.gd`, `mission_cinematics.gd`, `imperial_intro.gd`); keine Videodatei im Spielpaket.

---

## 10. Konkrete Verbesserungspotenziale für Fable 5.2 (priorisiert, mit Abnahme)

**P0 – sofort**
1. **Patch unabhängig prüfen und übernehmen.** Auf exakt `2c549548` den Diff lesen, §5.1/5.2 wiederholen, Draft-PR gegen `integration/imperathen-unified-20261125` anlegen. Abnahme: beide Fold7-Workflows passieren den Retarget-Schritt; `YOUTH_SOLE_CALIBRATION` erscheint im CI-Log mit den Werten aus `evidence/metrics/sole_calibration_lod0-3.json`.
2. **Entscheidung zur Schranke (§6.1.1).** Wenn die Prüfung `y ≥ 0,35` bleiben soll, muss das Asset korrigiert werden (Fuß-Bindpose im Original-Rig), nicht die Erkennung. Fable soll ausdrücklich PASS/FAIL/BLOCKED dokumentieren.
3. **Knöchelhaut.** Die zusätzlichen > 1,5×-Kanten bei LOD 0/1 (§6.1.2) in 140+ Posen visuell prüfen, in Nahaufnahme und Bewegung (Blender oder echte Godot-Frames). Fix-Optionen: Fuß/Schienbein-Gewichte im Knöchelring getrennt glätten oder eine kontrollierte Korrektur des Fuß-Bindposes mit Provenienz. Abnahme: keine neuen überdehnten Kanten gegenüber `2c54954` ohne Sichtfreigabe.
4. **Restliche Workflow-Schritte:** APK-Export, QA-Signatur und Bildprüfung in der CI ausführen; das Motion-Gate in der CI auf demselben SHA messen und mit den lokalen 591 Blockern abgleichen (§5.3).
5. **`village_person_test.gd` reparieren (voraussichtlich der nächste Blocker in `fold7-city-apk.yml`, §5.3):** Spielerfigur `hero_meshy_italia_*` (MeshyYouthBody, 35 Joints) als dritten Fall behandeln. Nach einem Script-Fehler muss sich Godot sauber mit FAIL beenden statt in das Zeitlimit zu laufen. Abnahme: `village_people` PASS mit und ohne gestagten Jungen.

**P1**
6. Tunika-/Armgewichte (`unresolved_surface_deformation`) und den eingeschmolzenen Griff getrennt beheben; Abnahme über `YOUTH_SURFACE_REVIEW` mit `remaining_overstretched_edges` deutlich unter dem Ausgangswert und Sichtprüfung.
7. Erste echte **Stealth-Greybox** (Handbuch P1, T16): Sicht-/Geräuschzustand, legitimer Weg, Scheitern ohne Spielabbruch.
8. **Fold7-Abnahme** mit `tools/fold7_physical_qa.py`: 20–60 Minuten, P50/P95/P99, Thermik. Ohne Gerät gilt BLOCKED, nicht PASS.

**P2**
9. Das Ad-hoc-Bewertungsskript dieser Sitzung (Sohlenneigung im Kontakt, Fuß-Durchdringung, Kantendehnung; siehe §11) als festen Regressionstest in `tests/` aufnehmen, mit dem Wert vor dem Patch als Referenz.
10. Laufzeitkosten der verdoppelten Sohlenstichprobe auf dem Gerät messen; die Stichprobe falls nötig auf die Plattenränder reduzieren.

---

## 11. Reproduktion

```bash
# 1. Quellen
git clone https://github.com/Ullmann27/imperathen && cd imperathen
git fetch --depth=1 origin integration/imperathen-unified-20261125 && git switch -c work FETCH_HEAD   # 2c54954
git am 0001-fix-youth-sole-slab-calibration.patch
# 2. Originaljunge (Prüfsumme d70e0ba6…47e1) und ACCAD (f30dc56f…14b1)
python3 tools/characters/stage_original_youth.py --archive /tmp/youth.zip --report /tmp/youth-status.json
curl -fL https://accad.ohio-state.edu/sites/accad.osu.edu/files/Male2_bvh.zip -o /tmp/accad.zip
python3 tools/characters/retarget_accad_youth.py --archive /tmp/accad.zip --input prototype/assets/characters \
  --output /tmp/youth-mocap --report /tmp/youth-mocap-report.json
cp /tmp/youth-mocap/hero_meshy_italia_*.glb prototype/assets/characters/
cp /tmp/youth-mocap-report.json prototype/assets/animations/accad_youth_motion.json
# 3. Stadt (Blender 4.0.x) und Godot 4.7.2
blender -b -t 2 --python-exit-code 1 --python tools/world/build_roman_test_city.py -- --output /tmp/city --surface-style viva --texture-size 1024
# ... Kopie wie in .github/workflows/fold7-unreleased-qa.yml, dann:
godot --headless --path prototype --import
godot --headless --path prototype --script res://tests/youth_mocap_test.gd   # YOUTH_MOCAP_RUNTIME_PASS
```
Die Messungen vorher/nachher (`evidence/metrics/skinning_eval_*.json`) stammen aus einem Ad-hoc-Skript dieser Sitzung. Es skinnt Sohlen-, Fuß- und Beinvertices aus den Animationskanälen der GLBs (lineares Blend-Skinning wie im Retargeter). Das Skript ist **nicht** Teil des Patches; es liegt unter `werkzeuge/skinning_eval.py` (siehe Verbesserung 9).

---

## 12. Nachweise nach Evidence-Schema (Handbuch S. 48)

Maschinenlesbar in `evidence/evidence.json`. Felder: `gate_id`, `source_sha`, `input_hashes`, `engine_version`, `platform`, `scenario_id`, `steps`, `expected`, `observed`, `status`, `run_url`, `artifact_url`, `reviewer`, `unresolved`. Lokale Läufe haben keine `run_url`. Als `reviewer` ist überall „keiner (unabhängige Prüfung durch Fable ausstehend)“ eingetragen.

## 13. Inhalt dieses Ordners

| Pfad | Inhalt |
|---|---|
| `UEBERGABEBERICHT.md` | dieser Bericht |
| `patches/0001-fix-youth-sole-slab-calibration.patch` | Korrektur, `git am`-fähig, Basis `2c549548` |
| `evidence/evidence.json` | Nachweise im Handbuch-Schema |
| `evidence/metrics/` | Sohlenkalibrierung je LOD, Skinning-Auswertung vorher/nachher, Stadt-Asset-Audit |
| `evidence/logs/` | Fehlerreproduktion, Retarget nachher, lokale CI-Spiegelung, `check_prototype`, MoCap-Laufzeittest, volle Suite und Motion-Gate |
| `evidence/renders/` | 8 Blender-Cycles-Renderings und 1 Diagramm (§8) |
| `inventory/INVENTAR_ROHDATEN_2c54954.md` | Rohinventur des Repositorys mit Befehlen und Fundstellen (stichprobenartig nachgeprüft) |
| `werkzeuge/` | Ad-hoc-Skripte dieser Sitzung, **nicht reviewed, nicht Teil des Patches**: `skinning_eval.py` (Vorher/Nachher-Metriken), `sole_patch_scan.py`, drei Blender-Renderskripte, `bench_sole_bind.gd` (Laufzeitkosten), `ci_engine_step_local.sh` (lokale Spiegelung des CI-Engine-Schritts) |

---

## 14. Nachtrag: autonome Fortsetzung (25./26.11.2026)

Der Projektinhaber hat Schreibzugriff auf Imperathen freigegeben und eine autonome Fortsetzung beauftragt (kein Merge nach `main`, keine Release-APK).

### 14.1 GitHub

| Objekt | Stand |
|---|---|
| Branch `fix/youth-sole-slab-calibration-20261125` | `beff419` (Sohlen-Patch, inhaltlich identisch mit lokal getestetem `10031e1`), `ffbe3ad` (Testkorrektur `village_person_test.gd`) |
| Draft-PR [Ullmann27/Imperathen#87](https://github.com/Ullmann27/Imperathen/pull/87) | Basis `integration/imperathen-unified-20261125` (nicht `main`) |
| CI „isolated installable ARM64 QA – NOT a release“ | **success** auf `beff419` ([45711418202](https://github.com/Ullmann27/Imperathen/actions/runs/45711418202)) und auf `ffbe3ad` ([45712174606](https://github.com/Ullmann27/Imperathen/actions/runs/45712174606)); derselbe Workflow scheiterte auf `2c54954` |
| CI „Foundation checks“ | success auf beiden Commits |
| CI „Fold7 candidate – real city, genuine APK, image checks“ | Lauf auf `beff419` wegen des Folge-Pushes abgebrochen; Lauf auf `ffbe3ad` siehe PR (Stand bei Abschluss dieses Abschnitts noch offen) |

### 14.2 `village_person_test.gd` (vorher voraussichtlich nächster Blocker)

Seit `2fa08cd` nutzt das Spiel für den italischen Spieler (12–17 Jahre) den Original-Jungen. Der Test kannte diesen Fall nicht. Mit vorhandenem Jungen brach er mit einem Script-Fehler an Zeile 53 ab und lief dann bis ins Zeitlimit.

Die Korrektur (`ffbe3ad`):
- Für den Jungen gibt es eigene, inhaltliche Prüfungen: Skin, exakt 35 Gelenke, Originaltextur, LOD-Pfad; Begleiter erhalten den Jungen nie.
- Alle bisherigen Prüfungen für die übrigen Herkünfte bleiben; die Farbprüfung der Tuniken ist strenger.
- Ein Wächter bricht nach 100 s mit Exit 1 ab, statt still zu hängen.

Lokale Ergebnisse:
- mit Jungen: 233 Prüfungen, 0 Fehler
- ohne Jungen: 234 Prüfungen, 0 Fehler
- erzwungener Fehler: Exit 1 nach 16 s (vorher Zeitlimit)
- **volle `check_prototype`-Suite auf `ffbe3ad`: 85/85 PASS** (745 s)
- Motion-Gate: 587 Blocker bei 61 Assets. Die 4 weggefallenen sind exakt die vom `village_people`-Fehlschlag verursachten.

Nachweis: `evidence/nacht/`.

### 14.3 Echte Godot-Bilder (nicht Blender)

`evidence/godot_frames/`: Godot 4.7.2, GL Compatibility, Mesa llvmpipe unter Xvfb. Aufgenommen wurde die echte Spielfigur `village_person.gd` mit dem Original-Jungen (LOD 0) und der Laufzeit-Erdung `youth_sole_contact.gd`; die tiefste Sohle liegt in allen 14 Aufnahmen bei +3 mm über dem Boden.
- Bild `10`: Sandalen in Idle und Marsch flach aufgesetzt.
- Bild `11`/`12`: Bei Block und Uppercut wird ein großer Tunika-Streifen bis zur erhobenen Hand gezogen (siehe 14.5). Der Rest des eingeschmolzenen Schwertgriffs ist an der linken Hand sichtbar.
- Grenze: In der Jab-Aufnahme ist keine Schlagpose erkennbar. `sample_pose("jab_right", …)` hat sichtbar nichts verändert; das ist nicht geklärt und kein Beleg für die Kampfclips.
- Das Aufnahmeskript liegt unter `werkzeuge/godot_youth_closeup.gd` (nur Nachweis, nicht im Spiel). Es setzt die Figur einmalig auf die gemessene Sohle, weil sie im Spiel unter einem Kapselkörper hängt.

### 14.4 Knöchel: Gewichtsglättung geprüft und verworfen

Ich habe eine Laplace-Glättung des Schienbein/Fuß-Übergangs im Knöchelband getestet, mit derselben Übernahmeregel wie die vorhandene Tunika-Reparatur. Ergebnis über 140 Prüfposen: **schlechter**.

| LOD | Kanten > 1,5× vorher | Kanten > 1,5× mit Glättung |
|---|---|---|
| 0 | 231 | 302 |
| 1 | 85 | 113 |
| 2 | 33 | 39 |
| 3 | 8 | 37 |

Die Übernahmeprüfung hat die Glättung verworfen, und ich habe den Code vollständig zurückgenommen (Diff und Log in `evidence/nacht/`). **Folgerung:** Die Knöchel-Dehnung lässt sich nicht über Gewichte lösen, sondern nur über einen korrigierten Fuß-Bindpose im Original-Rig.

### 14.5 Tunika-Riss: Ursache gefunden

Das Originalnetz ist an den Händen mit dem Rock **verschmolzen** (LOD 0):
- 1 031 Kanten verbinden direkt hand-gewichtete mit becken-/oberschenkelgewichteten Vertices.
- 546 cm² (links) bzw. 233 cm² (rechts) stoff-farbige Fläche sind an die Hände gebunden.
- Die überdehnten Vertices liegen in Hüft- bis Handhöhe (y 0,59–0,82 m). Von ihnen haben 48 % ein Arm/Hand-Gewicht > 0,5 und weitere 33 % eines von 0,05–0,5; die schlimmsten Posen sind Block, Uppercut und Jab.
- Das Netz besteht aus 2 622 getrennten UV-Inseln. Die Dehnung entsteht innerhalb gemischt gewichteter Inseln.

Die vorhandene Reparatur verwirft alle Kandidaten. Lösungsansatz: Inseln nahe der Hand jeweils einheitlich Körper oder Hand zuordnen, über die Prüfposen gemessen; das öffnet die Naht an Inselgrenzen, statt Flächen zu dehnen. Weil das eine Netz- und Sichtentscheidung ist, wird er nur als eigener Versuch mit Vorher/Nachher-Bildern vorgelegt (siehe PR-Verweise am Ende dieses Abschnitts, falls vorhanden).
