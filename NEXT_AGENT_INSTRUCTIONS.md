# NEXT_AGENT_INSTRUCTIONS – JARVIS Genesis Fortsetzung (Stand 04.11.2026)

Sofort ausführbarer Auftrag für den nächsten Agenten. Lies zuerst `FABLE_JARVIS_HANDOFF.md` und
`docs/research/omega/ASTRA-REFERENCE-PARITY-20261104.md`. Die vier Bilder unter
`docs/design-references/jarvis-genesis-20261104/` sind **verbindliche Produktionsreferenzen**, keine Inspiration.

## Regeln (unverändert gültig)
- Arbeiten auf einem eigenen Branch ab `research/continuum-fable51-jarvis-genesis-20261104`; nie auf `main`; keine Force-Pushes, keine Resets, keine fremden Branches löschen.
- Paket `at.heinz.deviceai.continuumlocal`, Signatur, versionName 0.3.35 / versionCode 36, Besitzer-Erinnerungen, Modellgewichte, EWM2-Format: nicht verändern.
- Kopf niemals durch Drahtgittermaske, generischen Roboter oder leuchtende Kugel ersetzen.
- Status immer getrennt melden: Quellcode implementiert / Host-Test bestanden / Emulator / echtes Fold7 / produktiv. Keine Simulation von Ausführungen, keine Konzeptbilder als Implementierungsnachweis.
- Nach jedem Push: Branch, SHA, Verbesserung, Teststatus.
- Vor jedem Eingriff in `AgentActivity.kt`, `avatar/*`, `ui/Jarvis*`: `git fetch origin` und `git merge-tree` gegen die Branches von PR #495, #499, #494, #489 prüfen; keine Doppelimplementierung.

## Prioritätenreihenfolge (Gedächtnis-/Parameterforschung darf 1–4 nicht verdrängen)

### Prio 1 – Originalgetreuer 3D-Avatar (Referenz ref-01, ref-02, ref-03)
Dateien: `app/src/main/assets/avatar3d/continuum-avatar.glb` (+ `NOTICE.json`, `LICENSE.ASSETS.md`), `app/src/main/java/at/heinz/deviceai/avatar3d/{FilamentAvatarRenderer,AvatarGlbContract,AvatarMorphMapper}.kt`, `ContinuumHologramView.kt`.
1. Gesichtsgeometrie an ref-01 angleichen (Proportionen: schmale Nase, hohe Wangenknochen, definierte Kieferlinie, geschlossener neutraler Mund, Augenabstand). Werkzeugpfad ohne Blender: Python + `pygltflib`/`numpy` auf den vorhandenen FaceMesh-Positionen (12.980 Verts), Morph-Targets erhalten (21 Namen müssen exakt bleiben, `AvatarGlbContract` prüft sie).
2. Material-Upgrade: Skin von OPAQUE auf halbtransparentes dunkles Indigo (alphaMode BLEND, baseColor α≈0,35–0,5), Emissive für Lichtlinien, KHR_materials_emissive_strength beibehalten; Farbverlauf links Magenta/Violett (≈#B23CFF) → rechts Cyan (≈#19D3FF) über Vertex-Farben oder eine 2k-Gradient-Textur; `NeuralThreadsMesh` als Lichtlinien entlang Jochbein/Schläfe/Kiefer verdichten.
3. Filament-Seite: Fresnel-Rand (custom material oder Clearcoat + Emissive), Bloom über `View.setBloomOptions`, Scanlines/Partikel als separates additives Quad oder Punktwolke; Kamera-Framing nach `AvatarPortraitFramingTest`.
4. Abnahme: Host-Check (`scripts/android-kotlin-unit-check.sh`) grün; GLB-Vertragstest `tools/epsilon-avatar-contract/`; Screenshot vom Gerät/Emulator neben ref-01 legen (ohne Gerät ehrlich als „ungeprüft“ markieren).

### Prio 2 – Mimik, Augen, Sprachsynchronisation
Dateien: `avatar/AvatarDirector.kt`, `avatar/AvatarContract.kt`, `motion/*`, `avatar3d/AvatarMorphMapper.kt`, Hooks in `AgentActivity.kt` (~249 audioLevel, ~6533 onRuntime, ~6543–6580 TTS-Listener, ~2790 gaze).
1. Neue Zustände `ACKNOWLEDGING` (kurzes Nicken + Augenbrauen) und `SLEEP` (Lider 80 %, Lichtlinien gedimmt) in `AvatarState` + `AvatarPoseReducer`; alle bestehenden Tests in `app/src/test/.../avatar/` müssen weiterlaufen.
2. Idle: Blickdrift (Sakkaden alle 2–5 s, Amplitude ≤ 6°), Mikro-Kopfbewegung via `DampedSpring`, Blinzeln 12–20/min mit Doppelblinzeln; beim Zuhören Kopf leicht zur Audioquelle, Augen auf Kamera.
3. Visemes: Koartikulation (Blend von aktuellem/nächstem Viseme, Attack 40 ms, Release 90 ms) in `AvatarMorphMapper`; Amplitude aus `audioLevel`; Wortgrenzen aus `onRangeStart` bleiben Quelle. Nichts an TTS-Engine-Auswahl ändern.
4. Tests: deterministische Unit-Tests (Zeitachse simulieren, keine echte Audio), Host-Check grün.

### Prio 3 – Gehirnkugel und räumliche Hologramme (ref-04, Mitte)
Dateien: `ui/ContinuumBrainOrbRenderer.kt`, `ui/ContinuumBrainSceneView.kt`, `ui/BrainOrbAnimationRenderer.kt`.
1. Visuell: Glas-Kugel (Fresnel-Rand cyan), Gehirn innen mit Violett→Cyan-Gradient und pulsierenden Synapsen, 3 geneigte Ringe (bereits tiefensortiert) mit Partikeln, Podest mit drei Stufen und Bodenreflexion.
2. Zustandskopplung: Intensität/Farbe aus `AvatarState` (THINKING = schneller Puls, LEARNING = Violett-Anteil hoch, BLOCKED = rot-oranger Rand); Signal-Quelle bleibt `AvatarDirector`/`Signals`.
3. Danach optional GPU-Pfad (Filament-Szene) – erst wenn Canvas-Version abgenommen.

### Prio 4 – Pixelgenaues Dashboard, Leuchteffekte, responsive Layouts (ref-04)
Dateien: `ui/JarvisShellLayout.kt`, `JarvisShellRenderer.kt`, `JarvisCockpitGrid(+Content).kt`, `JarvisTiles.kt`, `JarvisMenu.kt`, `JarvisGlassRenderer.kt`, `JarvisBloomPass.kt`, `JarvisWindowProfile.kt`, Assets `app/src/main/assets/jarvis-reference/`.
1. Pixelvergleich: JVM-Vorschau `scripts/jarvis-design-preview.sh` (Java2D) für 884×760 dp (Fold7 offen), 412×915 (Phone), 1280×800 (Tablet) rendern und gegen ref-04 overlayen; Abweichungen in Topbar/Navigation/Dialogkarte/Kachelraster (5×2)/Infopanels/Statusleiste auflisten und beheben.
2. Glow: Cyan-Außenglühen an Kacheln, Glas-Hintergrund mit 8–12 % Weiß, Bloom nur auf aktiven Elementen; Textkontrast ≥ 4,5:1 bewahren.
3. Tests: `JarvisReferenceWindowLayoutTest`, `JarvisShellLayoutTest`, Snapshot-Vergleich über `tools/capture/` (wenn Gradle verfügbar).

### Prio 5 – Verbindung mit KI-Schnittstellen
Nur bestehende Schnittstellen nutzen: `AvatarDirector.onRuntime(AvatarRuntimeSignals)`, `speechStarted/wordRange/audioLevel/speechEnded`, `ContinuumLearningCore.replyFromMemory`, `LocalFirstPolicy`, `FreshKnowledgeNeed`, `runResearch`. Ereignis → Zustand: Mikrofon aktiv → LISTENING; Reasoning läuft → THINKING; `runResearch` → RESEARCHING; Lernzyklus → LEARNING; Freigabe nötig → WAITING_FOR_APPROVAL; Policy-Block → BLOCKED. Kein neues Kernsystem, keine Parallel-Datenbank neben EWM2; OFF/ASK/ALWAYS_ON respektieren.

### Prio 6 – Integration, Fold7-Tests, APK-Release
1. Merge-Reihenfolge vorschlagen: #491 (Astra) → dieser Branch → Avatar-/Dashboard-Branches; nur mit Besitzerfreigabe.
2. Fold7-Abnahme: Innendisplay 2184×1968 px, Außendisplay, Continuity bei Faltwechsel, Avatar ≥ 30 fps, kein Jank bei TTS; Protokoll in `docs/evidence/`.
3. APK nur über den bestehenden Release-Pfad (Release-Vertrag prüfen, bevor versionCode erhöht wird). Ein grüner Host-Build ist kein installiertes Release.

## Offene Nebenaufgaben (nach Prio 1–4)
- Host-Check JUnit-Lauf auf dem aktuellen HEAD wiederholen und Ergebnis in `FABLE_JARVIS_HANDOFF.md` nachtragen.
- Branch `gamma/parameter-audit-20261104`: Checkpoint-Dateien gegen `lm1_param_count.py --json` abgleichen; Frage „existiert ein trainierter 16M-Checkpoint?“ mit Datei, Bytes, SHA beantworten.
- Cognitive-QA (Gesprächsfluss Gedächtnis→Avatar) erst nach stabiler Avatar-Zustandsmaschine.
