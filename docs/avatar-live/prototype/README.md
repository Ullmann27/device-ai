# Jarvis-Avatar live – Zwischenstand (abgebrochen wegen Nutzungslimit, 03.11.2026 14:41 Europe/Vienna)

## Gefunden (nur gelesen, nichts im Repo geändert)
- App: Android nativ, Kotlin, Repo Ullmann27/device-ai (`/workspace/device-ai`).
- Kugel (Orb): `ui/ContinuumHologramRenderer.kt` (Glas-Kugel, Plexus-Netz, ∞-Kern, 5 Orbits, Zustandseffekte),
  `ui/ContinuumHologramPlexus.kt` (128+48 Knoten, Halo, 420 Staubpartikel, 8 Lichtbögen), `ui/ContinuumOrbitalRing.kt`,
  `ContinuumHologramView.kt` (Phase = delta*(.28+energy*.72), Zustands-Blend 1-exp(-7·dt)), `HologramAudio.kt` (Hüllkurve 35 ms Attack / 150 ms Release),
  `TtsHologramSignal.kt` (PCM-RMS). Referenzbild: `docs/design_targets/continuum-2026-10-22/reference-phone.png`.
- Firmenlogo: `app/src/main/res/drawable/device_ai_holo_logo.xml` (nicht verändert).
- Avatar-Vertrag (einzige Schnittstelle): PR #373, `docs/CONTINUUM_AVATAR_CONTRACT.md`, `avatar/AvatarContract.kt`, `avatar/AvatarDirector.kt`
  (AvatarSink, AvatarSinkRegistry, AvatarEvent, AvatarPoseReducer, 15 OVR-Viseme, Blick −1..1, +x rechts aus Betrachtersicht, +y oben).
- Stimme: `cognitive/JarvisVoiceProfile.kt` (Android-TTS, pitch 0.86, rate 0.93, ausdrücklich keine Schauspieler-Imitation).

## In diesem Ordner
- `face-geometry.js` – Gesichts-Tiefenfeld (65×74, cm) + Feature-Loops (Lippen, Augen, Brauen, Oval, Nase), abgeleitet aus MediaPipe canonical_face_model.obj (Apache-2.0).
- `tools/export_face.py`, `tools/canonical_face_model.obj` – Generator dafür.
- `vendor/mediapipe/` – lokal gebündelter MediaPipe Face Detector 0.10.14 (Bundle, WASM, blaze_face_short_range.tflite, LICENSE), damit Kamera-Tracking ohne Netzwerk läuft.

## Noch NICHT gebaut (offen)
- `avatar.js`/`index.html` Web-Renderer (Hologramm-Linien, Kugel als Gehirn, Aufwachen, Kamera-Opt-in, Augenverfolgung).
- 2D-Ebenen (Gesicht, Augen, Mund, Orb-Gehirn, Glow) als PNG mit Alpha + Anker-JSON.
- GLB mit 15 OVR-Visem-Blendshapes und Augen-Bones.
- Kotlin-AvatarSink/View im Worktree (Queue in onAvatarEvent, AvatarPoseReducer auf eigenem Thread 30–60 fps, Orb aus ContinuumHologramRenderer).
- Screenshots/Previews, Gradle-Tests.
Geplante Architektur siehe TEST_RESULTS.md im Worktree-Branch.
