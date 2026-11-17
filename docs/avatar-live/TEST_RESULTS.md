# Jarvis-Avatar live – TEST_RESULTS (ehrlich, Stand 03.11.2026 14:41 Europe/Vienna)

Basis: PR #373 (`26162442`). Arbeit wegen Nutzungslimit abgebrochen.

## Getestet / erledigt
- Nichts am App-Code geändert. Kein Kotlin geschrieben, daher keine Gradle-/JUnit-Läufe in diesem Branch.
- Geprüft (nur gelesen): Vertrag PR #373 (AvatarSink, AvatarSinkRegistry, AvatarEvent inkl. AudioLevelSource, AvatarPoseReducer: Glättung 0,4, Totzone 0,025, Faktor 1,5).
- Umgebung vorhanden: JDK 17 (`/workspace/env.sh`), Android-SDK `/opt/android-sdk`; frühere Läufe im Hauptrepo `BUILD SUCCESSFUL` (`/workspace/junit-m2.log`) – nicht für diesen Branch.
- Erzeugt außerhalb des Repos: `/workspace/jarvis-grafik/avatar-live/face-geometry.js` (aus MediaPipe canonical face model), lokal gebündelter MediaPipe Face Detector.

## Nicht getestet / nicht gebaut
- Web-Prototyp, Screenshots, Aufwach-Video, 2D-Ebenen + Anker-JSON, GLB (15 OVR-Viseme, Augen-Bones), Kotlin-Renderer, Gerätetest Fold7.

## Geplante Architektur (für die Fortsetzung)
1. `JarvisAvatarHub : AvatarSink` – in `Application.onCreate` vor AgentActivity in `AvatarSinkRegistry.sink` setzen; `onAvatarEvent` legt nur in `ConcurrentLinkedQueue`.
2. Eigener Render-Thread (30–60 fps): Queue leeren → `AvatarPoseReducer.apply`, `pose()` als volatilen Snapshot veröffentlichen.
3. `JarvisAvatarView`: Ebenen Gesicht/Augen/Mund/Glow + Gehirn-Orb über den bestehenden `ContinuumHologramRenderer` (unverändert) am Gehirn-Anker; Blick x·25° / y·15°, Lider (Aufwachen, Blinzeln 2–5 s), Mund: Öffnung = jawOpen, Form = viseme.
4. Kamera-Blickverfolgung nur mit eigenem Opt-in (Vertrag §7), Frontkamera, lokal, nichts speichern.
