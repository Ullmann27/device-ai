# FABLE_JARVIS_HANDOFF – Device AI Continuum / JARVIS Genesis (Fable 5.1, 04.11.2026)

Notsicherung nach Sitzungsabbruch (Budgetgrenze). Alles hier ist ehrlich als **fertig / teilweise / getestet / offen** markiert.
Nichts davon ist auf einem Fold7, in einem Emulator oder als APK geprüft.

## 1. Repository, Branches, PRs

| Branch | HEAD | PR | Basis | Inhalt |
|---|---|---|---|---|
| `research/continuum-fable51-jarvis-genesis-20261104` (Hauptbranch dieser Sitzung) | siehe `git log -1` | Draft-PR (siehe PR-Liste „Fable 5.1 JARVIS Genesis“) | `codex/astra-jarvis-takeover-20261104` (Astras PR #491 @ e101e393d) | Host-Check-Toolchain für Filament/androidx, Referenzbilder, diese Übergabe |
| `gamma/parameter-audit-20261104` | `db2c3ba99` | Draft-PR | Hauptbranch dieser Sitzung | analytischer LM1-Parameterzähler `tools/parameter-audit/` (Zwischenstand) |
| `alpha/avatar-hologram-20261104`, `beta/cognitive-avatar-bridge-20261104`, `qa/cognitive-memory-audit-20261104` | = `030217e5d` | – | Hauptbranch | **nur lokal angelegt, keine Änderungen** (Agenten wurden vor dem ersten Commit gestoppt); nicht gepusht |
| `research/continuum-fable51-genesis-20261101` | `702b57ae` | #334 (Draft) | PR-#333-Branch | Vorsitzung (Recherche-Dialog, Aktualitätsprüfung, RIS, E7/EWM2) – vollständig gesichert |

Ausgangspunkt für alle Avatar-/Dashboard-Arbeit ist **Astras Stand e101e393d (PR #491)**, nicht `main`. Parallel offene fremde PRs:
#495 (Avatar-Mund/Audio-Helfer), #499/#494 (Memory→Avatar), #492/#497 (Gewichte/Gates), #489 (Motion-Mathematik), #490 (CognitiveLoop).
Keiner davon darf überschrieben werden.

## 2. In dieser Sitzung erstellte/veränderte Dateien

| Datei | Status |
|---|---|
| `scripts/android-kotlin-compile-check.sh`, `scripts/android-kotlin-unit-check.sh` | **fertig, host-geprüft**: optionale `filament-android-1.56.0.jar`/`gltfio-android-1.56.0.jar` (classes.jar der AARs von Maven Central) und ein compile-only `androidx.annotation.RequiresApi`-Stub, damit `avatar3d/FilamentAvatarRenderer.kt`, `ui/JarvisGlassRenderer.kt`, `ui/JarvisBloomPass.kt` ohne Gradle kompilieren. Ergebnis auf bb6fa6593: `KOTLIN_COMPILE_CHECKED` (0 Fehler außerhalb LiteRT-LM). Der JUnit-Teil wurde durch den Abbruch unterbrochen → **nicht abgeschlossen**. |
| `docs/design-references/jarvis-genesis-20261104/ref-01…04` | verbindliche Referenzbilder (SHA-256 unten) |
| `tools/parameter-audit/lm1_param_count.py`, `README.md` (Branch gamma) | Zwischenstand, lauffähig |
| `FABLE_JARVIS_HANDOFF.md`, `NEXT_AGENT_INSTRUCTIONS.md` | diese Übergabe |

Keine Android-Quelldatei (Kotlin/Assets/Shader) wurde in dieser Sitzung verändert. Paket `at.heinz.deviceai.continuumlocal`, Signatur, versionCode, Update-Kanal: unverändert.

## 3. Verbindliche visuelle Referenzen (nicht neu interpretieren)

| Datei | SHA-256 | Zuordnung |
|---|---|---|
| `ref-01-head-frontal.jpeg` | 83e950e0…3fbbec16e | Hauptreferenz Kopf: frontale Gesichtsgeometrie, feine anatomische Lichtlinien, Violett/Magenta links → Cyan/Blau rechts, dunkle halbtransparente Haut, Fresnel-Rand, Partikel; elliptische Lichtbahnen hinter dem Kopf → `avatar3d/continuum-avatar.glb` (Materialien DeepIndigoSkin/CyanThreads/VioletThreads), `FilamentAvatarRenderer`, `ContinuumHologramView` |
| `ref-02-head-torso-threequarter.jpeg` | 84aeaebd…fbfc8264 | Dreiviertelansicht mit Schulter/Brustleuchten (Herz-Glühpunkt) → Framing/Kamera (`AvatarGlbContract`, `AvatarPortraitFramingTest`) |
| `ref-03-head-profile-eyes.jpg` | bf91dbf2…75563cd77 | Augen: kräftig cyan-leuchtende Iris mit Tiefe, Schläfen-Lichtknoten → Materialien CyanIris/NaturalSclera |
| `ref-04-dashboard-fold7.jpeg` | 452b841d…c33484036 | Gesamt-Dashboard Fold7 offen: Topbar, 11er-Navigation links, JARVIS-Dialogkarte, Gehirnkugel mit Podest, Statusmodule rechts, 10 Funktionskacheln (5×2), 4 Infopanels, Statusleiste → `ui/JarvisShellLayout/Renderer`, `JarvisCockpitGridContent`, `JarvisTiles`, `JarvisMenu`, `ContinuumBrainOrbRenderer`, `JarvisCockpitPanels` |

Astras eigene Referenzkopie: `docs/design-screenshots/jarvis-master-20261104.jpg` (1536×1024) + Komponentenmatrix `docs/research/omega/ASTRA-REFERENCE-PARITY-20261104.md`.

## 4. Tatsächlicher Stand der Komponenten (verifiziert am Code von e101e393d)

| Komponente | Stand | Beleg |
|---|---|---|
| 3D-Kopf (GLB) | **teilweise**: echtes animierbares Modell vorhanden (`app/src/main/assets/avatar3d/continuum-avatar.glb`, 10,2 MB, MakeHuman-CC0-Adaption; FaceMesh 12.980 Verts; 21 Morph-Targets SIL,PP,FF,TH,DD,KK,CH,SS,NN,RR,AA,E,I,O,U,BlinkLeft,BlinkRight,BrowUp,BrowDown,Smile,JawOpen; NeuralThreadsMesh 2 Primitives mit denselben 21 Targets; Augen/Iris/Pupillen/Zähne; Skin mit Nodes AvatarRoot/Neck/Head/EyeLeft/EyeRight/Jaw; keine Animationsclips). **Offen**: Referenzähnlichkeit des Gesichts, holografische Materialien (Skin ist OPAQUE, Linien nur als Threads-Mesh), Fresnel/Scanlines, Bloom, Partikel. NOTICE.json: `identityStatus = PARTIAL_REFERENCE_ADAPTATION_REQUIRES_OWNER_REVIEW`. | GLB-Header geparst (Python), NOTICE.json |
| Renderer | **teilweise**: `avatar3d/FilamentAvatarRenderer.kt` (Filament 1.56.0 + gltfio, 211 Zeilen), `AvatarGlbContract.kt`, `AvatarMorphMapper.kt`; Host-View `ContinuumHologramView.kt` mit 2D-Fallback `avatar/ContinuumAvatarRenderer.kt`. Kompiliert jetzt im Host-Check. **GPU-Pixel ungeprüft.** | compile-check bb6fa6593 |
| Animationscontroller | **teilweise**: `avatar/AvatarDirector.kt` + `AvatarContract.kt` (AvatarState IDLE, LISTENING, THINKING, RESEARCHING, LEARNING, WAITING_FOR_APPROVAL, SPEAKING, BLOCKED; AvatarPoseReducer; Blink; Viseme-Enum 15 Klassen; GazeTarget). **Offen**: ACKNOWLEDGING, SLEEP, Idle-Mikrobewegungen/Blickdrift als Spring-Interpolation (Motion-Bibliothek `motion/` vorhanden, PR #489). | Code gelesen |
| Lippensynchronisation | **teilweise**: TTS `UtteranceProgressListener.onRangeStart` → `AvatarDirector.wordRange` → `visemes(word)` → VisemeCue; Audio-Pegel → `audioLevel` (AgentActivity ~249, ~6553–6580). Deterministisch aus Wortgrenzen, keine Phonem-Zeitmarken der Engine. **Offen**: Koartikulation, Prüfung gegen echte Audioausgabe (nur Gerät). | AgentActivity.kt |
| Gehirnkugel | **teilweise**: `ui/ContinuumBrainOrbRenderer.kt` (3 geneigte Ringebenen mit Tiefensortierung, Netz, Gehirn, Staub, Podest mit 3 Stufen), `ContinuumBrainSceneView.kt`, `BrainOrbAnimationRenderer.kt`; Ereignisintensität aus `Signals(processing, …)`. Canvas, kein GPU. | Code gelesen |
| Dashboard | **teilweise (Astra)**: `JarvisShellLayout/Renderer/View`, `JarvisCockpitGrid(+Content)`, `JarvisTiles`, `JarvisMenu` (11 Navigationszeilen, 10 Kacheln, Compact/Medium/Expanded), `JarvisWindowProfile`; Referenz-Assets `app/src/main/assets/jarvis-reference/`. Tests `JarvisReferenceWindowLayoutTest`, `JarvisShellLayoutTest` u. a. (Astra-Angabe 167/167, in dieser Sitzung **nicht** nachgelaufen). JVM-Vorschau: `scripts/jarvis-design-preview.sh` (Java2D, `tools/continuum-jvm-preview/`). | Astra PR #491 |
| KI-Schnittstellen (nicht anfassen ohne Abstimmung) | vorhanden: `AvatarDirector.onRuntime(AvatarRuntimeSignals)` (AgentActivity ~6533), Dialog `sendCurrentInput` (~2415–2900) mit `CertifiedLocationReflex`, `FreshKnowledgeNeed`, `ContinuumLearningCore.replyFromMemory`, `LocalFirstPolicy`, Recherche `runResearch` (~5690), `LocalConversationContext`. Dokumentiert in `docs/research/omega/FABLE51-GENESIS-CONTINUATION.md` Abschnitt B. | Vorsitzung |

## 5. Tests

| Prüfung | Ergebnis |
|---|---|
| `scripts/android-kotlin-unit-check.sh` auf bb6fa6593, Compile-Teil | **bestanden** (`KOTLIN_COMPILE_CHECKED`, 162 erwartete LiteRT-LM-Auflösungsfehler, 0 sonstige) |
| JUnit-Teil derselben Prüfung | **abgebrochen** (Sitzungsende) – muss wiederholt werden; auf e101e393d meldete Astra 167/167 gezielte Tests |
| `tools/parameter-audit/lm1_param_count.py` | läuft; 512/5-Profil = 16.011.776 Parameter (nur definierte Zahl) |
| Gradle/APK/Lint/Robolectric/Emulator/Fold7/Filament-GPU | **nicht ausgeführt** (kein Android-SDK in dieser Umgebung; Google Maven blockiert, Maven Central erreichbar) |

Reproduktion Host-Check: `source /home/user/tools/env.sh && export JAVA_OPTS="-Xmx4g -Xss4m" LC_ALL=C.UTF-8 && scripts/android-kotlin-unit-check.sh <out>` (benötigt in `CONTINUUM_ANDROID_DEPS`: android-all-15.jar, okhttp/okio, shizuku-*, json.jar, optional filament-android-1.56.0.jar + gltfio-android-1.56.0.jar).

## 6. Bekannte Fehler / Konflikte / fehlende Abhängigkeiten

- Google Maven (`dl.google.com`) in der Sandbox gesperrt → kein Android-SDK/AGP; androidx-Artefakte nicht ladbar (daher Stub).
- Nur 2 CPUs / 7 GB RAM: Host-Checks müssen serialisiert werden (`flock /tmp/kotlin-check.lock`), zwei kotlinc parallel → OOM.
- Fremde Branches berühren dieselben Dateien (AgentActivity.kt, avatar/*, ui/Jarvis*): vor Merge `git merge-tree` gegen #495, #499, #494 prüfen.
- Parameterangabe „~3.778“ in keinem LM1-Profil auffindbar; „16M“ ist das Profil MinGRU 512/5 – ob ein trainierter Checkpoint existiert, ist **offen**.

## 7. Exakte nächste Schritte

Siehe `NEXT_AGENT_INSTRUCTIONS.md` (Prioritäten 1–6 mit Dateien, Methoden, Tests, Abnahme).
