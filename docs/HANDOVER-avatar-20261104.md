# Handover Avatar (Kopf) – 04.11.2026, für ChatGPT Astra

## Stand
- Branch `jarvis-grafik/avatar-ref-match`, Draft-PR #482. Kopf-WIP @56fc93c57554fb801a39a00de33db98aff54236a
  (davor Merge #479 @69b76b2f → f322a72a). Tests avatar.* / ui.Jarvis* / *Slot*: 62/62 grün auf 56fc93c5.
- `avatar/ContinuumAvatarRenderer.kt` (prozeduraler Kopf, #479-Tokens Cyan #2FF9FE / Violett #914CEF / glowCore #ABF8FF):
  - `showOrbits = false`: 3 Orbit-Ringe aus (ref5 zeigt keine), Code bleibt für Video 1.
  - `ref5Hud = true`: HUD-Ring hinter dem Kopf, Tracking-Ecken, Licht-/Partikelhaar, Ruhe-Yaw 9°, Atmen (4,5 s).
  - Lebendig: Blinzeln 0,15 s, wake (Augenöffnen), Blick (gazeX/Y), Mund (jawOpen/Viseme), Schweben Muster C.
- `avatar/JarvisHoloAvatar.kt`: implementiert Kerns `JarvisAvatarRenderer` (#481 @17e1db2b).
- Vorschau: `tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HeadRef5Preview.kt`
  (Java2D, gegen app/build/tmp/kotlin-classes/debug + kotlin-stdlib kompilieren). Vergleich:
  /workspace/jarvis-design-referenz/kontrolle-3/avatar-kopf.png (links Vorschau, rechts ref5).
- Bild-Modus: NOCH NICHT gebaut. Entscheidung Runde 4: prozedurales Drahtgitter erreicht ref5 nicht →
  Bild-Layer aus generierten Kopfbildern.

## Abweichungen zu ref5 (prozeduraler Weg)
Gesicht noch Linienraster statt feiner konturfolgender Haarlinien, Wangen/Nase/Kinn zu flach; Haar fällt nach unten statt
nach links aufzufächern; Violett/Glühen und Schläfen-Glanzlicht zu schwach; Kopf etwas zu weit rechts/oben, Hals zu
schmal; Sockelring = alte Plattform; HUD-Ring mit zu wenig Segmenten/Ringen.

## Nächster Schritt
1. Quellbilder (Herkunft geklärt, selbst generiert, dürfen ins Repo): /workspace/jarvis-grafik/avatar-ref5/
   `jarvis-kopf-ref5_augen-zu.jpg`, `jarvis-kopf-ref5_augen-offen.jpg` (tatsächlich 864×1152, deckungsgleich bis auf
   JPEG-Rauschen). Als WebP q≈85, ~512×683 nach `res/drawable-nodpi/jarvis_head_ref5_closed|open.webp`.
2. ContinuumPainter hat keine Bild-API: `drawImage(image, src-rect, dst-rect, alpha)` mit Default no-op ergänzen,
   in AndroidContinuumPainter (Bitmap, einmal laden) und Java2DContinuumPainter implementieren.
3. Bild-Modus hinter Flag als Standard (prozedural bleibt Fallback): Augen weich maskiert zu↔offen überblenden
   (wake + Blinzeln 0,15 s), Mundbereich vertikal strecken aus jawOpen/visemeWeight + Speech-Glow (additiv, #479-Tokens),
   Atmen ~1 % Skalierung mit Hals-Anker, Schweben Muster C, Blick als Parallaxe weniger px + Iris-Glanzpunkt,
   HUD-Ring + Tracking-Ecken darüber (mehr Segmente/Ringe wie ref5), dezente Partikel. Keine Pro-Frame-Allokationen.
4. Mathematik #489 @e600add5 einbinden (LipSyncFaceSource, `applyTo(pose)` + `blinkAt`/`breath`). Dabei die eigene
   Glättung und das feste 4,6-s-Blinzeln im AvatarMorphMapper bzw. `eyeOpening()` abschalten, sonst doppelt geglättet.
5. Kern: `CockpitAnimationEvent.CockpitEntered` → `headIconBurst(seconds)` verdrahten (nicht das erste CommandFlyIn);
   `JarvisAvatarSlot.register(JarvisHoloAvatar())` in AgentActivity.

## Offen
- GLB-Herkunft (continuum-avatar.glb aus #478) ungeklärt → nicht einchecken/wiederverwenden.
- Regeln: kein Merge, kein Force-Push, nichts löschen, keine Referenzbilder/Videos/GLB in Git.
