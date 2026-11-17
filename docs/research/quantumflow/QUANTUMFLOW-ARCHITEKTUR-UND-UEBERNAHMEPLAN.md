# QUANTUMFLOW — Technischer Open-Source-Integrationsplan
Datum: 02.11.2026 | Repository: Ullmann27/device-ai | Audit PR #350

Portierungsbefunde aus Review PR360: Der unveränderte MediaPipe-Snapshot
`FaceLandmarkerHelper.kt` benötigt vor einer Runtime-Übernahme Prüfungen für
fehlgeschlagene `detectForVideo`-Ergebnisse, null erfolgreiche Frames vor der
Durchschnittsdivision und `MediaMetadataRetriever.release()` in `finally`.
Diese Befunde sind Backlog für eine spätere geprüfte Portierung. Lizenzierte
Originalsnapshots und ihre Blob-Pins bleiben unverändert; LIVE_3D_NOT_IMPLEMENTED.

**Status: CODE / LICENSE SNAPSHOTS VERIFIED. LIVE_3D_NOT_IMPLEMENTED.** Dieses Dokument ist kein Release-Nachweis. Forschungs-Branch #350 stammt von PR #349 @ `93422eb6`. PR #349 hat danach das neue reale Statusband mit Commit `df3b6930` erhalten: die beiden Branches NICHT blind synchronisieren; UI-Event-Anbindung muss den neuesten PR349-Stand verwenden. Hauptrepository, bestehendes Gemma, TTS, ToolGate und SWB1/EWM2 bleiben unverändert.

## 1. Schon vorhandene Continuum-Technik – nicht neu erfinden

- PR #338 `experiment/continuum-avatar-lab-offline-20261101` bietet `ContinuumAvatarLabActivity.kt`: freiwilligen lokalen Porträtimport, 2D-Kopfneigung, Blinzeln, Augenleuchten, PCM-RMS-Mundbewegung und Lebenszykluskontrolle. Noch nicht in diesem Branch integriert.
- `TtsHologramSignal.kt` ist ein begrenzter, synchronisierter PCM-Amplituden-/Zeitverlauf und verfügt bereits über `activate/begin/append/anchor/level/stop`. Nutze ihn für sichtbare Kieferbewegung als ersten belegbaren Schritt, aber **RMS ist kein Phonem- oder Visem-Nachweis**.
- `ContinuumHologramView.kt`, `ContinuumJarvisMissionControlActivity.kt`, `ContinuumHologramRenderer.kt`, `ContinuumHologramPlexus.kt` und bestehende Tests stellen einen UI-/Render-Bestand bereit. Den Continuum-Orb nicht ersetzen, sondern Quantumflow als wählbaren Modus ergänzen.
- PR #349 `df3b6930`: reales Statusband für Arbeitsschritt, Modul und Laufzeit. Dieses Statusband als **einzige Event-Quelle** für die futuristische Kontrollraum-Inszenierung verwenden, keine neue künstliche Prozentquelle.

## 2. Gefundene Originalprojekte – Code schon gesichert

Im Manifest `UPSTREAM-PROVENANCE.json` stehen acht GitHub-Projekte mit vollständigen Upstream-SHAs, Lizenzen und 16 verifizierten Original-Blob-SHAs. Alle im Verzeichnis `docs/evidence/quantumflow-20261102/upstream/` gesicherten Dateien sind **Quellen für Evaluation**, kein automatisch laufender oder vollständiger Runtime-Import.

| Funktion | Vorlage | Verwendung | technischer Prüfpunkt |
|---|---|---|---|
| Echte mobile 3D-Szene, GLB/glTF, PBR, Licht | sceneview/sceneview (Apache-2.0) | native Kotlin + Filament Render-View | Versions-/AGP-/Compose-Interop, GLB blendshapes und Thermalprofil |
| Deutschsprachige visemähnliche Artikulation, Augen und Gesichtsanimation | met4citizen/TalkingHead (MIT) | schnelle isolierte Offline-WebView-PoC oder algorithmisches Referenzmodell | vorhandene Android-TTS liefert derzeit keine garantierten Wort-/Phonemtimestamps |
| Sprachsignal → Oculus-Visemes | met4citizen/HeadAudio (MIT) | optionale Offline-Web-AudioWorklet-Forschungsstrecke | Browser-AudioWorklet, modellabhängige Genauigkeit, Worklet-Verfügbarkeit in Android WebView |
| Blick, Kopf, ARKit-/VRM-Mimik | pixiv/three-vrm (MIT) | VRM-Rig-Beispiel und Blicklogik | Web-3D-Stack, kein nativer Kotlin-Renderer |
| 478 Face-Landmarks / 52 Blendshape-Werte / Head-Matrix | google-ai-edge/mediapipe-samples (Apache-2.0) | explizit kameragesteuertes Lokales Tracking | Face Landmarker **ist keine Besitzer-Identitätsprüfung**; sichtbare Kamerafreigabe, kein Speichern |
| Animiertes JARVIS-Dashboard | aligh993/JARVIS (MIT) | nur als HUD-/CSS-Animationsmuster; neu als eigene Android-Views/Lottie | HTML-Demo mit simulierten Werten: keine ungeprüften Prozent, kein remote CDN |
| Premium HUD-Animation, Rings, Karten, Dokumentenfluss | airbnb/lottie-android (Apache-2.0) | nativer Android UI-Layer | Animations-JSON-Lizenz gesondert prüfen; keine heimlichen Animationstrigger |
| Vorbereitete skalierbare Motion-Dateien | LottieFiles/dotlottie-android (MIT) | Alternative zu Lottie für State Machines | tatsächliche Plattformabhängigkeiten/JNI-Größe und Animation-Lizenz testen |

## 3. Empfohlene zweistufige Architektur

**Stufe A – schneller Offline-Demonstrator, ohne neue App:**
- Reuse Avatar Lab PR #338 nach Codeprüfung; optional in separater nicht-exportierter Android-Activity eine Offline-WebView-Probe für TalkingHead mit lokal gebündelter HTML/JS/GLB-Laufzeit; JavaScript-Bridge streng begrenzen, kein `addJavascriptInterface` mit Fremdseiten, keine externen URLs oder unsichtbare Kamera.
- GLB des Quantumflow-Kopfes **ist noch nicht vorhanden**. Das hochgeladene 2D-Gesicht liefert nur Stilvorlage; für echte 3D-Animation braucht es eigenes geometrisches Mesh, Rig und passende morph targets. Kein fremdes Demo-Gesicht ungeprüft lizenzieren/verteilen.
- Anfangs `TtsHologramSignal` für Mundöffnung und vorhandenes TTS für Sprache. Beweis im UI: `AUDIO_ENERGY_LIP_APPROXIMATION`, nicht exakte deutsche Visem-Synchronität.

**Stufe B – dauerhaft bevorzugter Android-Produktionsweg:**
- Kotlin/Java `QuantumflowAvatarRenderer` mit Filament/SceneView oder eigener leichter Filament-Schicht, vorher Benchmarks und vorhandenes App-Theme/Views prüfen.
- Eigene lokale `AvatarPose`-Werte: jawOpen, smile, cheekRaise, blinkLeft, blinkRight, gazeX/Y, headYaw/Pitch/Roll, speakEnergy, trackedConfidence. Alle Werte finite, begrenzt, nach Sitzung und Kamerastop auf neutral zurücksetzen.
- `TtsHologramSignal` im TTS-Synthese-/Playback-Callback einsetzen und nur zeitlich gepufferte Metadaten übergeben; nie Roh-PCM protokollieren.
- CameraX → MediaPipe FaceLandmarker → nur aktuelle Pose-/Blickdaten → Avatar. Kamera nur nach bewusstem Opt-in, sichtbarer Aufnahmeindikation und Pause/Schließen stop; keinerlei Gesichts-Embedding-Aufbewahrung erforderlich.
- Biometrisches Freischalten sensibler Aktionen ausschließlich über Androids systemseitige `BiometricPrompt`-Sicherheitsprüfung und bestehenden `ToolGate`; Personentracking ≠ Eigentümeridentität.
- Ein `AvatarWorkEvent`-Vertrag verbindet echte vorhandene `AgentActivity`-/Worker-/ToolGate-Ereignisse mit dem HUD; scharfe Typtrennung `REQUESTED`, `CONFIRMED`, `RUNNING`, `VERIFYING`, `SUCCEEDED`, `BLOCKED`, `FAILED`, `CANCELLED`. Nie `SUCCEEDED` vor Postcondition.
- Animations-Objekte (App-Karte, Dokument, Terminal, Memory-Ring) nur aus tatsächlich vorliegenden Auftrags-/Tool-/Beleg-Ereignissen ableiten. Sensible Dokumentinhalte im HUD maskieren. Sichtbarer Matrix-Hintergrund ist dekorativ und ausdrücklich kein Eingabelog/Quellnachweis.
- Laufzeit basiert auf monotonem Takt; Fortschritt nur `completedVerifiedSteps / knownPlannedSteps` wenn beide bekannt. Sonst `STEP X` und fortlaufende Uhr, keine frei erfundene Prozentzahl oder ETA.
- `Gedankenkommentare` sind kurze nutzerorientierte **beobachtbare Arbeitsschritte** (z. B. „Die App öffnet sich“, „Fenster überprüft“), keine angebliche Offenlegung interner Modellüberlegungen. Ein-/Ausschaltoption, Voice-Priorisierung und TTS-Unterbrechung sind erforderlich.

## 4. Konkrete Umsetzungspakete (je nach realem Build)

**QF01**: Ressourcenkatalog, Lizenzprüfung, Source-Hashprüfung, existierendes Avatar Lab und Statusband identifizieren (**dieser PR: Source-Audit abgeschlossen**).
**QF02**: Opt-in Avatar-Lab + eigene GLB-Stilfigur importieren (Geometrie/Rig/Blendshapes), echter Offline-Render auf Fold7 mit FPS/Frames/GL-Speicher/Temperatur.
**QF03**: TTS ↔ Visem-Abstraktion; PCM-RMS aus bestehendem Pfad; deutsche phonem-/audio-basierte Verfeinerung nur nach Synchronitäts-/Latenztests.
**QF04**: Augen, Wangen, Kopfrotation, Idle/Blink-Kurven und auf opt-in Frontkamera basiertes FaceLandmarker-Tracking; Stale-Frames verwerfen.
**QF05**: animierte HUD-Arbeitskarten, drehende Quellen, App-Start-Verifizierung, Terminal-Eventfeed, reale Zeit- und Phasenanzeige; keine erfundenen Prozentwerte, keine fake laufenden Spezialisten.
**QF06**: Abschlussprüfung vollständiger Gemini/Gemma→ToolGate→Android→Postcondition→HUD→Avatar→TTS Sequenz plus Negativtests, Render-Android-Gradle-/LiteRT-/Lint-Build und Fold7-Gerätetest.

## 5. Abnahmekriterien und bekannte Grenzen

- **In diesem Forschungszweig NICHT ausgeführt:** Android Gradle, Rendering, GLB-Import, Kamera, Audio-Sync, Lip-Sync, biometrisches Authentifizieren, FPS-/Thermalmessung, Fold7. Keine produktiven Änderungen.
- Browser-JS-Prototypen und 3D-Beispielmodelle sind nicht automatisch Android-Bibliotheken; Modell- und Asset-Lizenzen separat prüfen.
- Performance-Ziele sind nur Zielwerte, keine gemessenen Daten (z. B. 30 FPS bei gleichzeitiger Gemma-Inferenz als experimentelles Ziel, Strom-/Thermalgrenzen mit UI-Fallback).
- Nutzer darf Quantumflow deaktivieren und auf leichten vorhandenen Orb zurückwechseln. Keine permanenten GPU-Renderloops im Hintergrund.
- GitHub-Quellarchiv bleibt unter `docs/evidence/` getrennt vom App-Klassenpfad; keine geheimen Schlüssel, keine fremde unlizensierte Figur, keine Cloud-Abhängigkeit.
