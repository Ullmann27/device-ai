# IMPERATHEN – Technischer Übergabebericht an GPT-6.2 und Fable 5.2

Stand: 25.11.2026 · Verfasser: Claude Code (Cloud-Sitzung) · Grundlage: Masterhandbuch 3.0 (Operation Aeterna), Abschnitt A „P0“ und F „Abnahme“

Dieser Bericht trennt strikt zwischen **in dieser Sitzung ausgeführt und gemessen**, **im Repository vorgefunden (nicht von mir geprüft)** und **offen / blockiert**. Es gibt keinen Merge nach `main`, keine APK, keine Signatur und keine Freigabebehauptung.

---

## Aktueller Stand (Nachtrag 26.11.2026, Details in §14)

| Objekt | Stand |
|---|---|
| Draft-PR [#87](https://github.com/Ullmann27/Imperathen/pull/87): Sohlenkalibrierung + `village_person_test` | gegen `integration/imperathen-unified-20261125`. CI: Workflow „isolated installable ARM64 QA“ **grün** (vorher rot). Im Fold7-Workflow sind 85/85 `check_prototype`-Gruppen und alle Stadt-/Jungen-Tests grün; rot ist nur das vorbestehende globale Motion-Gate (587/61) |
| Draft-PR [#88](https://github.com/Ullmann27/Imperathen/pull/88): verschmolzene Hände vom Rock trennen (setzt auf #87 auf) | überdehnte Tunika-Kanten LOD 0 2 925 → 152, Spitze 0,674 → 0,072 m; echte Godot-Bilder aller Schläge und Tritte vorher/nachher (`evidence/godot_frames/17_*`) |
| Draft-PR [#89](https://github.com/Ullmann27/Imperathen/pull/89): schwebende Militär-NPCs geerdet (setzt auf #87 auf) | CI wie #87: 85/85 Gruppen, Stadt-/Jungen-Tests grün, rot nur das Motion-Gate, jetzt **507** statt 587 (§14.13) |
| Draft-PR [#90](https://github.com/Ullmann27/Imperathen/pull/90): Todes-Clip der 54 Company-Exporte geerdet (setzt auf #89 auf) | Sohlen-Durchdringung 16–21 cm → 0 (Engine ≤ 0,28 cm), Gleiten 0,8–1,05 m → 0,3–3,9 cm. Gemeinsam mit #87 + #88 + #89 gemessen: Suite 85/85, **Motion-Gate 507 → 382**, kein neuer Befund (§14.14). CI bestätigt (382) |
| Draft-PR [#91](https://github.com/Ullmann27/Imperathen/pull/91): gerissene Rocknaht der Grenz-NPCs (setzt auf #90 auf) | 10 GLBs, Rockrisse → 0 ohne Durchdringung; mit #87–#90 gemessen: Suite 85/85, **Gate 382 → 372**, kein neuer Befund (§14.15). CI bestätigt (372) |
| Draft-PR [#92](https://github.com/Ullmann27/Imperathen/pull/92): Dorf-Hocke „gather“ geerdet (setzt auf #91 auf) | Dorf 3,15 cm → 0 im Boden, Gleiten 2,3 → 0 cm; mit #87–#91 gemessen: Suite 85/85, **Gate 372 → 359**, kein neuer Befund (§14.16). CI bestätigt (359) |
| Draft-PR [#93](https://github.com/Ullmann27/Imperathen/pull/93): Tod und Hocke des Helden geerdet (setzt auf #92 auf) | Engine Tod 0,75/2,13 → 0,05/1,35 cm (Durchdringung/Gleiten), Hocke 0,69 → 0,05 cm; Suite 85/85, **Gate 359 → 355**, kein neuer Befund (§14.17). CI bestätigt (355) |
| Draft-PR [#94](https://github.com/Ullmann27/Imperathen/pull/94): Nieten des Grenz-Kits auf ihren Platten (setzt auf #93 auf) | Schulter-Nieten heben bis 8 cm ab → 0 (an der Schulterplatte gebunden), Brust-Nieten schweben bis 4 cm vor der Platte → aufgesetzt; Suite 85/85, Gate 355 unverändert, jeder Messwert identisch (§14.19). CI bestätigt (355, Befundliste zeilengleich mit #93) |
| Reproduzierbarkeit | Mit Blender 4.3.2 (glTF-IO 4.3.47) entstehen Kompanie- und Dorf-GLBs bytegleich neu (Bogenschütze bis auf Float-Rauschen im `shoot`-Clip). Die Erdung aus #90/#92 ist im Build durchgängig nachgewiesen (§14.16) |
| Verworfen (gemessen schlechter oder ohne Nutzen) | Knöchel-Gewichtsglättung, farbbasierte Insel-Zuordnung, Entfernen deckungsgleicher Griffinseln, Bein-IK und Hüftschwenk im Todes-Clip, Helden-Rockregel und Tunika-Glättung für die Grenz-NPCs |
| Entscheidungen für den Projektinhaber | 1. Ersetzte Sohlen-Schranke (`rest_normal.y ≥ 0,35`) bestätigen. 2. Sichtabnahme #88, #89 und #90. 3. Künstler-Pass am Originalnetz für Fuß-Bindpose, Griffrest und saubere Hand/Rock-Trennung. 4. Registrierung des Jungen im `motion_contract.json`. 5. #90: Abschalten von Godots Keyframe-Optimierer für 54 Modelle (+3,5 % Importgröße) annehmen; Todes-Pose der Kompanie für das verbleibende Gleiten neu gestalten. 6. Tunika der Grenz-NPCs: Risse gegen häufigeres Durchscheinen des Körpers abwägen oder Künstler-Pass (§14.15) |

Die folgende Tabelle beschreibt den Stand vor der Nachtschicht. Der Hinweis dort, ein Push nach Imperathen sei nicht erledigt, ist überholt.

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
- Bild `11`/`12`: Beim Block wird ein großer Tunika-Streifen bis zur erhobenen Hand gezogen (siehe 14.5). Der Rest des eingeschmolzenen Schwertgriffs ist sichtbar; er sitzt an der rechten Hand der Figur, im Bild links.
- **Korrektur:** Die Kacheln „jab_right“ und „uppercut“ in Bild 11–14 zeigen wegen meines Aufnahmegerüsts nicht die Technik, sondern die Ausgangspose der Überblendung (siehe `evidence/godot_frames/HINWEIS_AUFNAHMEGERUEST.md`). Das ist kein Spielfehler. Korrekte Aufnahmen aller Schläge und Tritte liefert Bild `17`.
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

Die vorhandene Reparatur verwirft alle Kandidaten.

### 14.6 Tunika-Korrektur umgesetzt: Draft-PR [#88](https://github.com/Ullmann27/Imperathen/pull/88)

Branch `fix/youth-hand-tunic-separation-20261126`, Commit `6478fef`. Er setzt auf #87 auf, Basis ist dessen Branch. Neue Funktion `separate_hand_islands()` in `youth_skin_polish.py`, aufgerufen nach der Waffenentfernung. Jede armgewichtete UV-Insel im Rock-/Oberschenkelbereich wird einheitlich zugeordnet:
- **Hand**, wenn der Median-Abstand zur Unterarm/Hand/Finger-Kette unter 6 cm liegt;
- sonst **Körper**, über das vorhandene `redistribute()`.

Eine Körper-Insel, deren Nähte überwiegend an Handfläche grenzen, bleibt bei der Hand (gegen schwebende Fragmente). Übernommen wird nur, wenn Anzahl **und** Spitze der Überdehnung sinken.

| LOD | überdehnte Kanten | Spitze | Nahtöffnungen > 20 mm |
|---|---|---|---|
| 0 | 2 925 → 152 | 0,674 → 0,072 m | 11 |
| 1 | 1 710 → 98 | 0,689 → 0,079 m | 23 |
| 2 | 737 → 42 | 0,659 → 0,083 m | 5 |
| 3 | 140 → 8 | 0,503 → 0,106 m | 3 |

Prüfungen:
- Clips, Sohlenebenen und Waffenbericht sind identisch zu #87. Bewegung und Erdung bleiben also unverändert.
- CI-Parität: Python 3.12 / NumPy 1.26.4 / SciPy 1.11.4 → 4/4 GLBs byte-identisch.
- `youth_mocap_test` 85 495/0, `village_person_test` 233/0, `roman_city_real_walk_test` PASS.

Godot-Bilder:
- `evidence/godot_frames/13_*`: vorher/nachher LOD 0. Beim Block ist der Stoffstreifen weg, der Marsch-Saum ist sauber, Idle unverändert.
- `14_*`: alle 4 LODs nachher.
- **`17_*`** (korrigiertes Gerüst): Jab rechts/links, Haken, Uppercut rechts/links, Front- und Seitkick, Block, Idle. **Vorher (#87)** reißen bei jedem Schlag große Tunika-Flächen mit den Händen mit; **nachher (#88)** sind alle Posen sauber.

Der Weg dorthin, mit verworfenen Varianten (Werkzeug `werkzeuge/garment_islands_experiment.py`):

| Variante | Messung | Bild |
|---|---|---|
| Zuordnung nach Hautfarbe | 2 923 → 360 Kanten | **schlechter**: Oberschenkelhaut flog mit der Hand (`15_*`) |
| geometrisch, nur gemischte Inseln | 406 Kanten, Spitze 0,257 m | Streifen weg, aber Flocken und Fragmente |
| alle armgewichteten Inseln im Rockbereich | 152 Kanten, 0,072 m | ein schwebendes Fragment an der Hüfte |
| zusätzlich Nahtnachbar-Regel und 6 cm Radius | 152 Kanten, 0,072 m, Nahtöffnungen 36 | **Endstand**, Fragment weg |

Offen bei #88:
- menschliche Sichtabnahme, auch in Bewegung und in Nahaufnahme;
- CI-Lauf auf #88;
- eine volle Suite ohne parallele Eingriffe. Den ersten Lauf habe ich verworfen, weil ich währenddessen die LOD-0-Datei für einen anderen Versuch kurz ausgetauscht hatte; das Ergebnis der Wiederholung steht in 14.8.

### 14.7 Schwertgriff-Rest an der rechten Hand: Versuch ohne Nutzen

An der rechten Hand hängen goldbraune Fragmente des eingeschmolzenen Griffs. Die vorhandene Waffenentfernung erfasst sie nicht, weil sie Handgewicht > 0,65 und keine hautfarbene Ecke verlangt; die Reste liegen bei 0,4–0,6.

Versuch: Inseln entfernen, deren Vertices exakt auf den Vertices des Original-Schwertnetzes liegen (LOD 0: 99 Inseln, 137–172 Flächen; LOD 2/3: wegen Dezimierung keine). Ergebnis in Godot-Nahaufnahmen: Die sichtbaren Fragmente bleiben weitgehend (`16_*`). **Nicht übernommen.** Das braucht einen Künstler-Pass am Originalnetz.

### 14.8 CI und Motion-Gate auf #87

- `real-city-arm64-qa-apk` ist rot. Laut CI-Log bestanden im roten Schritt alle 85 `check_prototype`-Gruppen (0 FAIL), Stadt-Asset, Stadt-Gehtest (Original-Junge und MoCap aktiv), `YOUTH_MOCAP_RUNTIME_PASS` und Crowd.
- Gescheitert ist erst der letzte Befehl `motion_quality_gate.py`: `BLOCKED 587 / 61`, identisch zu lokal. Begründung als Kommentar am PR: <https://github.com/Ullmann27/Imperathen/pull/87#issuecomment-7176760097>.
- Das Gate braucht menschliche Sichtprüfungen und Korrekturen an fremden Bewegungsfamilien. Die 5 Jungen-Befunde sind Registrierungslücken in `motion_contract.json`; eine Registrierung würde neue Messungen und Sichtprüfungen verlangen und ist eine Entscheidung des Motion-Teams.
- `actual-city-visual-review` (echte Godot-Stadt-Framebuffer High/Very High mit Original-Jungen in der CI): **success**.

### 14.9 PR #88: CI, sauberer Suite-Lauf, Stadtbilder

**CI auf `6478fef`:**
- grün: `signed-independent-arm64-city`, `actual-city-visual-review`, `local-checks`;
- rot: `real-city-arm64-qa-apk`. Im Schritt sind alle 85 `check_prototype`-Gruppen bestanden, ebenso Stadt-Asset, Stadt-Gehtest, `YOUTH_MOCAP_RUNTIME_PASS` und Crowd. Gescheitert ist erst `motion_quality_gate.py` mit `BLOCKED 587/61`. Die Befundzeilen sind laut Log-Vergleich zeilengleich mit #87; #88 erzeugt also keinen neuen Befund. Kommentar: <https://github.com/Ullmann27/Imperathen/pull/88#issuecomment-7177188287>.

**Lokal ohne parallele Eingriffe:** volle `check_prototype`-Suite **85/85 PASS** (667 s, Spitzen-RSS 785 MiB). Motion-Gate 587/61 mit identischer Befundliste wie #87 (`evidence/nacht/*_PR88.txt`).

**Echte Godot-Stadtbilder** (`evidence/godot_frames/18_*`): Godot 4.7.2, GL Compatibility, llvmpipe unter Xvfb. Aufgenommen mit `roman_city_visual_capture.gd`, High und Very High, je Forum, Markt und Mauerwerk, Stadt aus Blender 4.0.2, Junge mit #87 + #88.
- Ergebnis: `ROMAN_CITY_VISUAL_PASS` für beide Profile, `original_youth_active:true`, `mocap_active:true`, Teardown `scene_freed:true`.
- Wanduhrzeit 18,8 s bzw. 11,6 s, Spitzen-RSS 1 257 bzw. 1 099 MiB (Werkzeugprozess, kein Gerätewert).
- Beobachtung: High und Very High unterscheiden sich im Compatibility-Renderer optisch kaum. Im Hintergrund steht ein Amphitheater vom Typ Kolosseum. Das ist der bereits dokumentierte Epochenkonflikt (Handlung 58 v. Chr.); das Kolosseum wurde erst 70–80 n. Chr. errichtet.

**Echte Schlag-/Tritt-Posen** (`17_*`, korrigiertes Aufnahmegerüst): Vorher (#87) reißen bei Jab, Haken und Uppercut große Tunika-Flächen mit den Händen mit; nachher (#88) sind alle Posen sauber. Kicks und Block sind ebenfalls sauber.

### 14.10 Empfehlungen an Fable 5.2 (aktualisiert)

1. #87, #88, #89 und #90 unabhängig prüfen. Insbesondere die Bilder 17, 19, 20 und 21 und eine eigene Sichtabnahme in Bewegung, auch in Nahaufnahme der Hand/Rock-Naht und eines Todes in der Formation. Danach über Merge in `integration/imperathen-unified-20261125` entscheiden lassen, nicht nach `main`. Reihenfolge: #87 → #89 → #90; #88 auf #87.
2. Projektinhaber-Entscheidung zur ersetzten Sohlen-Schranke einholen (#87).
3. Motion-Gate (587; mit #89 507, mit #90 382; §14.13/§14.14):
   - Die Familien `frontier_people`, `battle_rigs`, `meshy_gear`/`service_gear`, `village_navigation` und `raid_motion` haben echte Messbefunde (Fußgleiten, Durchdringung, Übergangssprünge). Sie gehören in eigene Fix-PRs.
   - 84 Befunde betreffen nicht klassifizierte Ressourcen und Skripte. Dafür braucht es eine Vertragsentscheidung, wie in der CI erzeugte Assets (Stadt, Junge) registriert werden, ohne in Workflows ohne Staging „missing“ zu melden.
   - Die 12 fehlenden menschlichen Sichtprüfungen können nur Menschen liefern.
4. Künstler-Pass am Originalnetz des Jungen: Fuß-Bindpose (Sandalen ca. 80° verrollt), eingeschmolzener Griffrest an der rechten Hand, saubere Trennung von Hand und Rock. Neues Original sichern und Laufzeitableitungen neu erzeugen.
5. Epochenkonflikt der Stadt (Kolosseum in 58 v. Chr.) mit dem Kanon klären.
6. Mein Aufnahmegerüst (`werkzeuge/godot_strikes_capture.gd`) als festen Bild-Regressionstest übernehmen, mit abgeschlossener Überblendung.
7. Todes-Pose der Kompanie (`art/soldiers/build.py`) neu gestalten, damit beide Sohlen beim Einknicken stehen bleiben, und mit Blender 4.3 neu aufbauen. Der Nachbearbeitungsschritt aus #90 bleibt dabei gültig und idempotent.
8. Prüfen, ob Godots Keyframe-Optimierer auch andere kontaktkritische Clips verfälscht (Werkzeug: `werkzeuge/zz_dump_track.gd`).

### 14.11 Schwebende Militär-NPCs geerdet: Draft-PR [#89](https://github.com/Ullmann27/Imperathen/pull/89)

**Befund aus dem Motion-Gate:** 80 × „No actual support-surface samples for foot L/R“ bei `frontier_people`. Ursache sind die militärischen Grenz-Exporte (Bogenschütze, Legionär, Offizier, Speerträger): Ihre Sohlen liegen im Bindpose und in allen Clips **3,41 cm** (LOD 0) bzw. **3,23 cm** (LOD 2) über dem Modell-Ursprung. Das stammt aus `fit_to_company()` in `build_frontier_people.py`, das den Heldenkörper auf das unveränderte Company-Skelett überträgt. Diese NPCs schwebten im Spiel sichtbar, und ihre Stand-/Gang-Clips waren für das Gate nicht messbar.

**Lösung:** `scripts/visuals/authored_ground.gd` misst die Bindpose-Sohlenhöhe je Modellpfad; Dorf-/Stadt-NPC und Schlachtsoldat senken die Militärmodelle um diesen Wert ab. Zivilisten und der Junge bleiben unberührt. Das Skript ist im Bewegungsvertrag klassifiziert.

**Ergebnis:**
- Sohle im Idle bei 0,0000 m statt +3,41 cm.
- Kontakt-Frames 60/60 statt 0/60.
- Zivilisten-Werte identisch.
- Suite 85/85.
- **Motion-Gate 587 → 507.**
- Bilder: `evidence/godot_frames/19_*` (Legionär vorher/nachher, Zivilist unverändert) und `20_*` (Todes-Clip).

**Ehrlich offen:**
- Im Todes-Clip steigt die maximale Fußdurchdringung von 13,9 auf 17,3 cm. Der Clip drückt schon vorher mitten im Fall die Zehen durch den Boden; die Endlage schwebt nicht mehr.
- Neu messbar ist das Gleiten beim Rückzug: 0,050 m (Grenze 0,015 m).
- Beides sind Authoring-Aufgaben an den Clips.
- Werkzeuge: `werkzeuge/frontier_contact_diag.gd`, `werkzeuge/godot_legionary_capture.gd`.

**Gate-Stand gesamt, wenn #87 + #88 + #89 zusammengeführt werden:** 507 Befunde. #88 ändert nichts am Gate, #89 ist unabhängig von #88. Die gemeinsame Messung aller drei ist inzwischen ausgeführt (§14.13).

### 14.12 Weitere Gate-Befunde analysiert, nicht geändert (Mess-Spezifikation)

1. **Loop-Nähte „retreat“** (34 Befunde `frontier_people`, 14 `legacy_people`; 0,72 m / 58°):
   - Der Clip selbst ist geschlossen: erster und letzter Keyframe sind bei allen 45 Kanälen identisch, geprüft in `frontier_legionary_0.glb` und `soldiers/heavy_near.glb`.
   - Der Sprung entsteht im Messpfad, der „retreat“ über `village_person` abspielt. Dort ist „retreat“ nicht in `LOOPS`, und Dorfbewohner nutzen den Clip im Spiel nicht; als Schleife läuft er nur in der Schlacht (`soldier_visual.gd`, `LOOPING`).
   - **Vorschlag an das Motion-Team:** „retreat“ für Personen nicht als Schleife messen oder nur über den Soldaten-Pfad messen.
2. **Übergangssprünge `village_navigation`** (26 Befunde; Position 0,187 m, Rotation 23,4°):
   - Laut Log (`companion_turn_motion.log`) liegt der schlimmste Fall bei Marcus (`villager_2.glb`) mit `run → run` ohne LOD-Wechsel, an `Foot_L`/`Foot_R`, bis 0,327 m pro Frame.
   - Mein Diagnoselauf (`werkzeuge/run_jump_diag.gd`: gerader Lauf, Lauf mit Drehung, Marsch → Lauf) zeigt nur 0,066–0,069 m zusätzliche Fußbewegung pro Frame, also normale Schwunggeschwindigkeit.
   - Der Sprung entsteht demnach im Missions-Navigationsfluss (Pause, Fortsetzen, Ankunft). Außerdem misst die Metrik Weltpositionen Frame-zu-Frame einschließlich der Körperbewegung; bei Laufgeschwindigkeit sind das bereits etwa 0,06–0,07 m pro Frame, gleich der Grenze von 0,06 m.
   - **Empfehlung:** Metrik relativ zur Wurzelbewegung definieren und den Navigationsfall gezielt nachstellen.
3. **Rückzugs-Gleiten** (neu messbar durch #89): 0,050 m (Grenze 0,015 m). Der Rückzug wird im Personen-Pfad mit Geschwindigkeit 0 abgespielt, obwohl er ein Gang-Clip ist; vermutlich derselbe Mess-Spezifikationspunkt wie unter 1.

### 14.13 CI von #89 und gemeinsame Messung #87 + #88 + #89

- **CI #89** (`e5bf8b9`, Lauf [45721459886](https://github.com/Ullmann27/Imperathen/actions/runs/45721459886)):
  - `headless-regressions`, `actual-city-visual-review` und `signed-independent-arm64-city` sind grün.
  - In `real-city-arm64-qa-apk` liefen alle 85 `check_prototype`-Gruppen mit `exit=0`, ebenso `ROMAN_CITY_REAL_ASSET_PASS`, `ROMAN_CITY_REAL_WALK_PASS`, `YOUTH_MOCAP_RUNTIME_PASS` und `CITY_CROWD_LAYOUT_PASS`.
  - Rot ist nur der letzte Befehl `motion_quality_gate.py` mit **507** Befunden (#87: 587). Das entspricht exakt der lokalen Messung.
  - Kein `SCRIPT ERROR`, kein `Traceback`. Erläuternder Kommentar auf #89 gepostet.
- **Gemeinsame lokale Messung** (lokaler Branch aus #87 + #89 + Cherry-Pick von #88, Godot 4.7.2): 85/85 Gruppen, Gate 507, Befundliste zeilengleich mit #89 allein.
  - Logs: `evidence/logs/check_prototype_combined_87_88_89.txt`, `motion_gate_combined_87_88_89.txt`.

### 14.14 Todes-Clip der Company-Riggs geerdet: Draft-PR [#90](https://github.com/Ullmann27/Imperathen/pull/90)

**Befund:**
- Die Durchdringungs- und Gleitbefunde von `frontier_people`, `legacy_people`, `battle_rigs` und `meshy_people` stammen fast vollständig aus dem Todes-Clip: Sohlen bis 21 cm im Boden, bis 1,05 m Gleiten.
- Ursache ist die feste Beckenabsenkung von 0,715 m in der gemeinsamen Todes-Pose (`art/soldiers/build.py`). Sie ignoriert die gehäuteten Füße. Alle 54 Company-Exporte übernehmen sie.
- Der Held hat dafür bereits eine Blender-Korrektur (`fit_death_ground_contact`). Team A hatte den Meshy-Teil als offen dokumentiert (`docs/25_team_a/CONTACT_GROUNDING_CONTINUATION.md`, Punkt 2).

**Lösung (2 Commits auf #89):**
1. `tools/characters/ground_death_clip.py` stellt das exportierte GLB auf der CPU in Pose (lineares Blend-Skinning, glTF-Interpolation) und ersetzt nur den Sampler `death/Pelvis/translation` (240 Hz).
   - Die tiefste Oberfläche bleibt auf dem Standboden.
   - Kontaktpunkte im 2-cm-Band bleiben so gut stehen, wie es eine starre Verschiebung erlaubt (kleinster umschließender Kreis).
   - Gliedmaßen, Netze und alle anderen Clips bleiben bytegleich (Zugriffsvergleich für alle 54 Dateien: `evidence/metrics/death_ground_accessor_comparison.json`).
   - Alle fünf Blender-Exportstellen rufen den Schritt auf. Blenders eigenes Python erzeugt damit ein bytegleiches GLB.
   - Ein Test sichert Marker, Kanal, Import-Einstellung und Grenzwerte.
2. Godots Keyframe-Optimierer (Standard beim Szenenimport) hatte 267 von 289 neuen Schlüsseln verworfen und das Becken zur Fallmitte um 1,3 cm abgesenkt (Nachweis: `werkzeuge/zz_dump_track.gd`). Die `.import`-Dateien der 54 Exporte schalten ihn deshalb ab. Der Import behält dann den 30-fps-Bake auf 0,3 mm genau.

**Ergebnis:**

| Messung | Wert |
|---|---|
| Offline, 54 GLBs (`evidence/metrics/death_ground_contact_54_glb.json`) | Durchdringung 0,163–0,214 m → 0; Gleiten Grenz-NPCs 0,8–1,3 cm, Meshy 0,3–1,0 cm, Kompanie 1,6–2,1 cm, Dorf 1,5–3,9 cm (vorher 0,79–1,05 m) |
| Engine, Todes-Clip | Durchdringung ≤ 0,28 cm; Gleiten Meshy ≤ 1,13 cm, Grenz-NPCs 25 von 28 ≤ 1,5 cm, Kompanie 1,6–2,0 cm, Dorf bis 3,8 cm |
| Volle Suite mit #87 + #88 + #89 | 85/85 in allen drei Läufen |
| Motion-Gate | 507 → 416 (Erdung) → **382** (Import ohne Optimierer); in beiden Schritten **kein neuer Befund**. Logs: `evidence/logs/motion_gate_combined_plus_death_*.txt`, Suite: `check_prototype_combined_plus_death_pr90.txt` |
| Nebenwirkungen | alle anderen Clip-Werte gleich oder besser, außer `villager_3` gather 2,22 → 2,24 cm (schon vorher über der Grenze); importierte Szenen +1,08 MB (+3,5 %) |
| Bilder | `evidence/godot_frames/21_godot_todesclip_geerdet_vorher_nachher.jpg`: vier Modelle bei 0/35/55/75/100 %. Vorher sind die Füße zur Fallmitte vom Boden abgeschnitten und rutschen nach vorn; nachher stehen sie und der Körper kippt um sie |

**Ehrlich offen:**
- Die liegende Endlage liegt jetzt etwa 0,9 m weiter hinten (der Körper kippt um die Füße statt die Füße unter ihm wegzuschieben). Das ist sichtbar, physikalisch plausibel, aber eine Verhaltensänderung, etwa in engen Formationen.
- Kompanie und Dorf überschreiten die 1,5-cm-Gleitgrenze weiterhin. Ursachen:
  - das gestaltete Einknicken des rechten Knies schiebt eine Sohle gegen die andere;
  - die tragende Sohle rollt über die Ferse.

  Geprüft und **verworfen**:
  - zweiknochige Bein-IK: Kniewinkel bis 39° geändert, Knie bis 2 cm im Boden;
  - Bein-IK nur am gebeugten Bein mit 10°-Deckel: Dorf bis 5,5 cm;
  - reiner Hüftschwenk: Dorf 6,4 cm.

  Richtig wäre eine neu gestaltete Todes-Pose in `art/soldiers/build.py` mit Neuaufbau unter Blender 4.3.
- Kein Neuaufbau mit Blender 4.3 (lokal nur 4.0.2; die committeten GLBs stammen aus glTF-IO v4.3.47).
- Keine Sichtabnahme durch Menschen, kein Fold7-Test.
- Werkzeuge: `werkzeuge/godot_death_capture.gd`, `compare_glb.py`, `hz_sweep.py`, `engine_emul.py`, `zz_dump_track.gd`.

### 14.15 Gerissene Rocknaht der Grenz-NPCs: Draft-PR [#91](https://github.com/Ullmann27/Imperathen/pull/91)

**Befund:**
- Die Hautkanten-Befunde von `frontier_people` (31 864 Verletzungen) stammen aus Rock und Tunika.
- Die Grenz-Exporte tragen noch den älteren Punkt-Klassifikator des Helden-Rocks: Benachbarte Vertices der Mittelnaht hängen vollständig an gegenüberliegenden Oberschenkeln.
- `build_hero.py` korrigiert das inzwischen beim Helden (`fit_skirt_weights`, `fit_tunic_joint_weights`). Die Grenz-Exporte wurden danach nie neu gebaut.

**Messung der Varianten** (Werkzeuge `werkzeuge/cloth_variants.py`, `tunic_variants.py`, `local_variants.py`):

| Variante | Risse | Durchdringung |
|---|---|---|
| Helden-Rockregel (0,45) | 0 | Beine bis **11 cm** durch den Saum (2 989 Stichproben); im Godot-Bild sichtbar → **verworfen** |
| Helden-Rockregel mit 0,7 / 0,9 | 0 | 5,6 / 1,1 cm → verworfen |
| Tunika-Glättung global / lokal | Tunika 0 | Körper öfter durch die Ärmel (Legionär 1 408 → 1 660–2 069, Zivilist 959 → 1 618–2 078 Stichproben > 1 cm) → **nicht übernommen**, Abwägung offen |
| **Rock: lokale Nahtglättung** (reißende Vertices + 1 Ring, 4 Durchgänge, Mischung 0,35) | **0** | **unverändert** → übernommen |

**Ergebnis #91:**
- Geändert sind 10 von 28 Exporten mit je 13–19 Rock-Vertices. Rockrisse 637 / 222 / 93 → 0 (`evidence/metrics/frontier_skirt_seam_28_glb.json`).
- Gemeinsam mit #87 + #88 + #89 + #90 gemessen:
  - Suite 85/85;
  - `skin_edge_violations` 31 864 → 23 336;
  - **Gate 382 → 372**: alle zehn `walk_run_stop`-Hautbefunde entfallen, kein Clip schlechter, kein neuer Befund.
  - Logs: `evidence/logs/*_combined_plus_pr90_pr91.txt`.
- Bilder: `evidence/godot_frames/22_godot_grenz_rocknaht_vorher_heldenregel_naht.jpg` (vorher / verworfene Helden-Regel / übernommen). Werkzeug: `werkzeuge/godot_cloth_capture.gd`.

**Offen:**
- Die Tunika-Risse bleiben; sie tragen die übrigen Hautbefunde.
- Vorbestehend und unverändert: Bei Zivilist und Altem (LOD 0) tritt der Körper bis 8,2 cm durch den Rock.
- Keine menschliche Sichtabnahme, kein Fold7-Test.

### 14.16 Dorf-Hocke „gather“ geerdet: Draft-PR [#92](https://github.com/Ullmann27/Imperathen/pull/92); Neuaufbau mit Blender 4.3.2

**Befund:** Die Hocke `gather` senkt das Becken fest um 0,11 m. Dadurch stecken die Sohlen der Dorfbewohner 3,15 cm im Boden (Gleiten 2,3 cm); bei Grenz-Zivilist und Altem sind es 0,64 cm.

**Lösung:**
- Das Werkzeug aus #90 nimmt `--clip`.
- Andere Clips als `death` schreibt es nur, wenn Durchdringung oder Gleiten um mehr als 1 mm besser wird und nichts schlechter.
- Verworfen hat das Werkzeug dabei selbst: die Grenz-Zivilistin (Gleiten 0,97 → 1,97 cm) und Meshy (bereits sauber).
- Geschrieben wurden 12 GLBs, jeweils nur `gather/Pelvis/translation` (`evidence/metrics/gather_ground_contact_24_glb.json`).

**Ergebnis mit #87–#91:**
- Suite 85/85.
- Engine-Werte Dorf-`gather`: 3,06 → 0 cm Durchdringung, 2,21 → 0 cm Gleiten.
- **Gate 372 → 359**, kein Clip schlechter, kein neuer Befund.
- Logs: `evidence/logs/*_plus_pr90_pr91_pr92.txt`.

**Neuaufbau mit Blender 4.3.2** (offizielles Archiv, SHA-256 geprüft; glTF-IO 4.3.47 = Generator der committeten GLBs):

| Prüfung | Ergebnis |
|---|---|
| `art/soldiers/build.py` auf `2c54954` | 10 von 12 GLBs bytegleich; `archer_near/mid` weichen nur in fünf `shoot`-Kanälen um ≤ 3·10⁻⁷ ab (Bogen-IK) |
| dasselbe auf #90 (Erdung im Build) | heavy/officer/spear bytegleich mit den committeten, geerdeten GLBs; beim Bogenschützen ist der Tod-Kanal identisch (Log: `evidence/logs/blender432_soldiers_rebuild_pr90.txt`) |
| `art/village_characters/build.py` auf `2c54954` | alle vier `villager_*.glb` bytegleich |
| dasselbe auf #92 | Tod-Kanal bytegleich; im Gather-Kanal 27 Werte mit ≤ 1,7·10⁻¹⁸ m Abweichung (NumPy 1.24.3 in Blender gegenüber lokal) |

**Folge für Fable:** Kompanie und Dorf lassen sich künftig verlässlich mit Blender 4.3.2 neu bauen. Damit sind Quellkorrekturen möglich, etwa die Angriffspose der Kompanie, deren linker Fuß im Battle-Pfad 16,8 cm über den Boden gleitet (`art/soldiers/build.py`, Pose `attack`: `Thigh_L −12°` ohne Kniebeugung). Diese Choreografie-Änderung habe ich bewusst **nicht** vorgenommen. Grenz-NPCs und Meshy habe ich nicht neu gebaut, weil laut Team A dabei neuere Quellen einfließen würden.

### 14.17 Tod und Hocke des Helden: Draft-PR [#93](https://github.com/Ullmann27/Imperathen/pull/93)

**Messbasis:** Erst habe ich zwei Varianten nur mit der Messgruppe `motion_measurement` verglichen:
- **Nur Optimierer aus:** Der Tod des Helden geht in der Engine von 0,75 → 0,08 cm Durchdringung; der Optimierer war hier also ebenfalls die Ursache. Das Gleiten bleibt bei 2,13 cm.
- **Zusätzlich Erdung (Tod und Hocke):** Tod 0,05 cm / 1,35 cm, Hocke 0,05 / 0,05 cm.

**#93 übernimmt Variante 2.** `build_hero.py` ruft den Schritt nach `pack_hero_glb` auf.

**Volle Suite mit #87–#92:** 85/85, **Gate 359 → 355**, kein Messwert schlechter, kein neuer Befund.

**Bilder:** `evidence/godot_frames/23_godot_held_tod_hocke_vorher_nachher.jpg`; die Unterschiede sind sichtbar klein, eine Regression ist nicht erkennbar. Werkzeug: `werkzeuge/godot_hero_death_capture.gd`.

**Vorbestehender Befund (nicht durch #93):** `tools/characters/validate_hero.py` lehnt `hero_quality_0.glb` schon auf der Basis ab (`external URI/path is forbidden`). Grund ist der Lieferschritt `pack_hero_glb`, der ein Bild als externe Datei auslagert. Validator und Packer widersprechen sich; `art/hero_quality/validation.json` (Stand 22.11.) ist damit veraltet. Beleg: `evidence/metrics/validate_hero_vorher_lod0_externe_uri.json`. Entscheidung des Projektinhabers nötig.

**Offen:** Die übrigen Gleit-Befunde des Helden stammen von prozeduralen Kicks zur Laufzeit (1,9–2,0 cm), nicht aus GLB-Clips.

**Gate-Verlauf des PR-Stapels** (jeweils lokal gemessen mit allen Vorgängern, Godot 4.7.2):

| Stand | Gate | CI |
|---|---|---|
| #87 | 587 | bestätigt |
| + #89 | 507 | bestätigt |
| + #90 | 382 | bestätigt |
| + #91 | 372 | bestätigt |
| + #92 | 359 | bestätigt |
| + #93 | 355 | bestätigt |
| + #94 | 355 | bestätigt |

### 14.18 Entscheidungsgrundlage Tunika der Grenz-NPCs (nicht umgesetzt)

`evidence/godot_frames/24_godot_grenz_tunika_abwaegung_original_vs_glaettung.jpg` zeigt in Godot 4.7.2 den Export (oben) neben einer **temporären** Variante mit lokaler Tunika-Glättung (unten; reißende Vertices + 1 Ring, 2 Durchgänge). Die Variante ist nicht committet.

- **Schussposition des Legionärs:** Die Tunika reißt an der Achsel in rote Splitter. Die Glättung verringert das nur leicht, während der Körper laut Messung öfter durch die Ärmel scheint (§14.15).
- **Vorbestehender Zusatzbefund in derselben Pose (Seitenansicht):** Einzelne Nieten des `FrontierRomanKit` schweben unter dem Arm frei in der Luft. Die Gewichtung des Kits braucht einen Künstler-Pass.
- **Empfehlung:** Tunika und Kit im Originalnetz mit sauberer Ärmel-/Achselgewichtung neu wichten (Künstler-Pass) statt weiter nachträglich zu glätten. Werkzeug: `werkzeuge/godot_tunic_tradeoff_capture.gd`.

### 14.19 Nieten des Grenz-Kits: Draft-PR [#94](https://github.com/Ullmann27/Imperathen/pull/94)

**Befund:** Die Nieten, die `build_frontier_people.py` dem Militär-Kit hinzufügt, sind alle starr an `Spine` gebunden und in fester Tiefe platziert.
- **Schulter-Nieten:** Sie sitzen auf den an `Arm` gebundenen Schulterplatten und bleiben bei gehobenem Arm bis zu 8 cm in der Luft stehen.
- **Brust-Nieten:** Sie schweben schon im Bindpose 0,8–4,0 cm vor der gewölbten Brustplatte.

Messung: `werkzeuge/rivet_eval.py`, `rivet_gap.py` (exakter Punkt-Dreieck-Abstand).

**Lösung `tools/characters/rebind_frontier_kit_rivets.py`:**
1. Umbinden an die Platte, auf der eine Niete sitzt, wenn das Abheben über alle Clips nicht wächst: 1,7–8,0 cm → 0.
2. Aufsetzen schwebender Nieten auf die Platte desselben Gelenks, 0,5 mm eingelassen.

Alle verschobenen Inseln liegen auf dem Nietenraster des Build-Skripts. Betroffen sind 16 Militär-Exporte (`evidence/metrics/frontier_kit_rivets_16_glb.json`).

**Ergebnis mit #87 … #93:**
- Suite 85/85.
- Gate 355 mit identischer Befundliste; jeder Messwert identisch, das Gate misst Nieten nicht.
- Bilder: `evidence/godot_frames/25_godot_grenz_kit_nieten_vorher_nachher.jpg`, Wirkung klein, aber sichtbar.

**Offen:** Die Quellkorrektur in `build_frontier_people.py` (Nieten auf die Plattenoberfläche, Schulter-Nieten an `Arm_*`).

### 14.20 Gleiten des Spielers (Junge) in der Missionsnavigation: Ursache gefunden, nicht geändert

**Befund:** Der größte einzelne Gleitwert des Gates (18,5 cm, `village_navigation/walk_run_stop`) gehört dem **Spieler**, also dem Jungen in LOD 3 (`hero_meshy_italia_3.glb`), beim ersten Marschschritt aus dem Stand.
- In Team As Basislinie (`docs/25_team_a/evidence/contact-grounding/baseline-engine-2616391.zip`, vor der Integration des Jungen) lag das Maximum bei 5,5 cm und betraf nicht den Spieler.
- Der Wert kam also mit dem Jungen als Spielerfigur. Ob 2c54954 selbst oder #87 ihn verursacht, lässt sich nicht trennen, weil 2c54954 LOD 3 gar nicht baut (P0).
- Die Codestelle gehört nicht zu #87.

**Messung Bild für Bild:** Werkzeug `werkzeuge/player_nav_slip_diag.gd` (dieselbe Fixture wie das Gate), Log `evidence/logs/player_nav_slip_diag.txt`.

| Phase | Gleiten des gestützten Fußes |
|---|---|
| Gleichmäßiger Marsch, mittlere Standphase | 0 cm |
| Abrollen am Ende jeder Standphase | 4–5,5 cm (Marsch und Lauf) |
| Erster Schritt aus dem Stand (Beschleunigung 0 → 1,45 m/s) | rechter Fuß gleitet während des Einblendens rund 10 cm, dann 8 cm Abrollen, zusammen **18,5 cm** |

**Ursache** in `prototype/scripts/visuals/character_motion.gd`, `plant_world_feet`, Zweig `preserve_authored_roll` (nur der Junge):
- Zeile 245/260: Ein Fuß gilt in der ganzen zentralen Standphase als gestützt (`u` 0,08 … stance − 0,05).
- Zeile 253: Die haltende Korrektur wird aber nur mit `envelope` eingeblendet (0,08 → 0,17) und wieder ausgeblendet (stance − 0,13 → stance − 0,05).
- Zeile 254: Die Korrektur ist auf 7,5 cm × Skalierung begrenzt (`limit_length`).

In den Ein-/Ausblendfenstern wird der Fuß also bewusst nicht gehalten (aufgenommenes Fersen-/Ballenabrollen), meldet sich aber als gestützt. Beim Anlaufen übersteigt die Abweichung zusätzlich die Begrenzung. Das Gate misst außerdem den Knöchel (`Foot_*`-Ursprung), nicht den Kontaktpunkt, und zählt das natürliche Vorrollen des Knöchels über den Ballen als Gleiten.

**Nicht geändert**, weil es eine Laufzeit-Entscheidung zur Bewegungsqualität des Spielers ist, die eine Sichtabnahme braucht. **Empfehlung an Fable/Motion-Team:**
1. `_world_supported` beim Jungen nur melden, solange `envelope` ≈ 1 ist, oder das Gate auf Kontaktpunkte statt Knöchel umstellen.
2. Den ersten Schritt aus dem Stand mit voller Haltekraft erfassen, also ohne Einblendung, wenn der Körper beschleunigt, und die Begrenzung von 7,5 cm am echten Bedarf prüfen.
3. Danach Sichtabnahme in Bewegung.

## 15. Gesamtbilanz der autonomen Nachtschicht (25./26.11.2026)

### 15.1 PR-Stapel und Merge-Reihenfolge (alle Draft, kein Merge nach `main`)

```
integration/imperathen-unified-20261125 (2c54954)
└─ #87  Sohlenkalibrierung Junge (P0) + village_person_test
   ├─ #88  Hände vom Rock des Jungen trennen
   └─ #89  schwebende Militär-NPCs geerdet
      └─ #90  Todes-Clip der 54 Company-Exporte + Keyframe-Optimierer aus
         └─ #91  Rocknaht der Grenz-NPCs
            └─ #92  Dorf-Hocke „gather“
               └─ #93  Tod und Hocke des Helden
                  └─ #94  Nieten des Grenz-Kits
```

Empfohlene Reihenfolge: #87 → #89 → #90 → #91 → #92 → #93 → #94. #88 ist unabhängig davon auf #87 einzuspielen; die gemeinsame Messung zeigt keine Wechselwirkung.

| Stand (jeweils mit allen Vorgängern) | `check_prototype` | Motion-Gate | CI |
|---|---|---|---|
| #87 | 85/85 | 587 | bestätigt |
| + #89 | 85/85 | 507 | bestätigt |
| + #90 | 85/85 | 382 | bestätigt |
| + #91 | 85/85 | 372 | bestätigt |
| + #92 | 85/85 | 359 | bestätigt |
| + #93 | 85/85 | 355 | bestätigt |
| + #94 | 85/85 | 355 (jeder Messwert identisch) | bestätigt (Befundliste zeilengleich mit #93) |

Das Gate bleibt rot. Die restlichen 355 Befunde verteilen sich so:
- 84 Vertragsregistrierungen (nicht klassifizierte Ressourcen und Skripte, u. a. der in der CI erzeugte Junge und die Stadt);
- fehlende menschliche Sichtprüfungen;
- Tunika der Grenz-NPCs (Abwägung, §14.18);
- Messspezifikation „retreat“ (Schleifennaht, §14.12);
- Laufzeit-Kicks des Helden;
- Angriffs- und Todes-Choreografie der Kompanie (§14.16);
- Navigation und Spieler-Abrollen (§14.20);
- Fernkolonnen-Shader (`rank_stride`, LOD-Sprung 13,8 cm);
- Ausrüstungsdurchdringung mit konservativen Obergrenzen.

### 15.2 Was nachgewiesen ist und was nicht

- **Nachgewiesen (lokal und CI):**
  - Alle PRs bestehen die volle Suite.
  - Jede Gate-Zahl stimmt zwischen lokaler Messung und CI überein.
  - Jede Asset-Änderung ist per Zugriffsvergleich auf die beabsichtigten Kanäle bzw. Attribute begrenzt.
  - Kompanie- und Dorf-GLBs sind mit Blender 4.3.2 reproduzierbar; die Erdungsschritte sind im Build durchgängig nachgewiesen.
- **Nur lokal/offline nachgewiesen:** Varianten-Messungen der verworfenen Ansätze.
- **Nicht nachgewiesen:** menschliche Sichtabnahme, Fold7-Gerät, APK/Signatur, Neuaufbau von Grenz-NPCs, Meshy und Held mit Blender.

### 15.3 Verworfen nach Messung (nicht in den PRs)

- Knöchel-Gewichtsglättung;
- farbbasierte Insel-Zuordnung;
- Entfernen deckungsgleicher Griffinseln;
- Bein-IK und Hüftschwenk im Todes-Clip;
- 60/120/180-Hz-Schlüsselraster (240 Hz gewählt);
- Helden-Rockregel und Tunika-Glättung für die Grenz-NPCs;
- Erdung von Gather bei Grenz-Zivilistin und Meshy (das Werkzeug hat sie selbst abgelehnt).

### 15.4 Entscheidungen für den Projektinhaber

1. Ersetzte Sohlen-Schranke des Jungen bestätigen (#87).
2. Sichtabnahme #88–#94 in Bewegung, insbesondere Tod in der Formation (Endlage etwa 0,9 m weiter hinten).
3. Keyframe-Optimierer für die 58 Modelle aus Kompanie und Held aus (+3,5 % Importgröße).
4. Tunika der Grenz-NPCs: Risse gegen häufigeres Durchscheinen des Körpers oder Künstler-Pass.
5. Widerspruch `validate_hero.py` gegen verlustfreie Helden-Lieferung (externe Bild-URI).
6. Choreografie: Angriff der Kompanie (Fuß gleitet 16,8 cm), Kniezug im Kompanie-Tod.
7. Laufzeit des Spielers: Stützmeldung und Haltekraft beim ersten Schritt (§14.20).
8. Registrierung des Jungen und der Stadt im Bewegungsvertrag.

### 15.5 Neue Werkzeuge in Imperathen (über die PRs)

| Werkzeug | Zweck |
|---|---|
| `tools/characters/ground_death_clip.py` | Becken-Translation eines Clips erden; `--clip`, idempotent |
| `tools/characters/smooth_frontier_skirt_seam.py` | reißende Rocknähte lokal glätten, mit Durchdringungsprüfung |
| `tools/characters/rebind_frontier_kit_rivets.py` | Kit-Nieten umbinden und aufsetzen |
| Tests | `tests/death_ground_contact_test.py`, `frontier_skirt_seam_test.py`, `frontier_kit_rivets_test.py` (NumPy-Teil wird übersprungen, wo NumPy fehlt) |

Alle drei Werkzeuge werden von den Build-Skripten nach dem Export aufgerufen.
