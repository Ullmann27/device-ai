# Übergabe Jarvis Mathematik – 04.11.2026 (für ChatGPT Astra)

**Branch:** `jarvis-mathematik/motion-math` · **Draft-PR:** #489 gegen `grok/jarvis-design-system` (#479) · nicht gemergt.
**Stand vor dieser Übergabe:** `1983d2f7a9cc027e6f825844100ab927f1271505` (MotionLibrary-Bindungen), davor `e600add52c7b9a36d760cd504c7e323ece97d3b6` (Tests).

## Inhalt (Paket `at.heinz.deviceai.motion`, reines Kotlin, ohne Android-Imports)
- Verträge: unverändert die von Kern aus `motion/MotionContracts.kt` (Easing, SpringCurve, Stagger, MotionTimeline, FaceWeightsSource, AudioLevelSource, VisemeHintSource).
- `Easings`: linear, easeInCubic, easeOutCubic, easeInOutCubic, easeOutQuint, easeInExpo, easeInOutSine, easeOutBack, cubicBezier(x1,y1,x2,y2).
- `DampedSpring` (SpringCurve), analytisch für unter-, kritisch und überdämpft: `DampedSpring(ω₀, ζ)`, `fromStiffness(k, c)`, `forSettle(sec, ζ)`; `valueAt`/`valueAtMs`, `settleSec`, `peakValue`/`peakSec`, `asEasing()`. Ab settleSec ist der Wert exakt 1.
- `Staggers`: centerFirst (symmetrische Ringe), centerFirstSequential, linear; Standard 0,08 s.
- `TrackTimeline` (implementiert das Interface MotionTimeline): `evaluate(tMs, out)`, allokationsfrei und zustandslos. Fabriken in `MotionTimelines`: staggered, sequence, of, sceneChange (A), panelsFlyIn (B), envelope (F), pills (G), widgets.
- `MotionPresets` nach VIDEO-SPEC:
  - A: SCENE_MS 800, SCENE_OLD_SCALE 3,2, expo-in
  - B: FLY_IN_MS 600, FLY_IN_FROM_SCALE 0,8, panelStagger
  - C: idleFloat 4 s, ±2,5 px
  - D: LASER_MS 300
  - F: ENVELOPE_IN_MS 500, ENVELOPE_FLAP_MS 300
  - G: pillSpring
  - widgetSpring/videoSpring: 0,5 s, ca. 4,6 % Overshoot
  - H: shake(t, seed, axis) 2 px, ALARM_GLOW_FACTOR 3
  - dollyScale 1,00→1,03
  - Video 3: warpScale, FACE_ZOOM_MS 500 mit faceZoomEasing, orbPulse
- `JarvisMotion` verweist auf diese zentrale Tabelle, neu dort: `Easing.easeInCubic`, `widgetSpring`, `spring(k, c)`. Das bisherige Verhalten bleibt gleich.
- `LipSyncFaceSource` (FaceWeightsSource), 21 Kanäle = `AvatarGlbContract.TARGETS` (aus der GLB von #478 ausgelesen): SIL, PP, FF, TH, DD, KK, CH, SS, NN, RR, AA, E, I, O, U, BlinkLeft, BlinkRight, BrowUp, BrowDown, Smile, JawOpen.
  - Mund: RMS-Hüllkurve mit Attack 30 ms und Release 120 ms; unter Pegel 0,025 sind alle Mundkanäle exakt 0.
  - Viseme nur aus TTS-Phonemen (VisemeHintSource), 70 ms Überblendung. JawOpen hat dieselben Öffnungsanteile wie AvatarMorphMapper.
  - Blinzeln 0,15 s alle 3–6 s, deterministisch per Seed; Atmen als Sinus.
  - Läuft die Zeit rückwärts, wird die Glättung zurückgesetzt. Brücke zu Avatar: `applyTo(pose: AvatarPose)`.
- `MotionLibrary` (von Kern freigegeben): easing = flyInEasing, spring = widgetSpring, stagger = panelStagger, faceWeights = LipSyncFaceSource(audioLevel, visemeHint). audioLevel und visemeHint werden pro Frame live gelesen. Ohne Kerns echte Quelle ist der Pegel 0 und der Mund bleibt zu; visemeHint ist null.

## Tests (ehrlich, je SHA)
- `e600add5`: `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.motion.*' --tests 'at.heinz.deviceai.ui.JarvisMotionTest'` → 34 grün, 0 rot. Breiter mit ui.Jarvis*, ui.Cockpit*, cockpit.*, avatar3d.* → 70 grün, 0 rot.
- `1983d2f7`: `motion.*` → 22 grün, 0 rot.
- Diese Übergabe-Datei ändert keinen Code; dafür wurden keine neuen Tests gelaufen. Keine Volltests der App, keine APK.

## Offene Punkte
1. Kern: echte AudioLevelSource in `AgentActivity.subscribeRealSignals()` an `MotionLibrary.audioLevel` hängen; Merge von #489 in #481 entscheidet Kern.
2. Avatar (`jarvis-grafik/avatar-ref-match`): AvatarMorphMapper glättet selbst und blinzelt fest alle 4,6 s. Entweder `weightsAt` direkt nutzen oder `applyTo` + `blinkAt` und die eigene Glättung und das eigene Blinzeln abschalten, sonst wird doppelt geglättet.
3. Geschätzte Werte (die Spec nennt keine Zahl), gegen video1.mp4 bei 3–5 s und 13–16 s abgleichen:
   - Pillen-Feder 0,45 s, ζ 0,8
   - Widget-Feder ζ 0,7
   - Shake-Takt 40 ms
   - Orb-Puls 2 s ±8 %
   - Dolly-Strecke 8 s
   - Warp 5,2
4. Laser D: `JarvisMotion.laser` bleibt linear; der Lückenfüller kann `Easings.easeOutCubic` nutzen.

## Sprint 10:05 – Merge-Prüfung (09:58)
- `git merge-tree` von HEAD gegen #481 (`design/jarvis-cockpit-home-20261104` @5a1076ca) und #482 (`jarvis-grafik/avatar-ref-match` @2e49ea74): beide konfliktfrei.
- API-Hinweis: AvatarMorphMapper hat auf #481/#482 jetzt eine eigene 70-ms-Visem-Überblendung und ein festes Blinzeln (alle 4,6 s, ca. 0,15 s). Wenn die Gewichte aus `LipSyncFaceSource` über `applyTo(pose)` in den Mapper laufen, wird doppelt überblendet und doppelt geblinzelt. Lösung bei Avatar: entweder `weightsAt` direkt an den Renderer geben oder im Mapper die eigene Überblendung und das eigene Blinzeln abschalten, wenn die Mathematik-Quelle aktiv ist. Die JawOpen-Logik des Mappers ist unverändert, damit stimmt der Kopplungstest weiter.
- Für den Merge-Stand wurden keine Tests gelaufen (nur Textmerge geprüft).
