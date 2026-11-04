# HANDOVER Jarvis Lückenfüller → ChatGPT Astra (04.11.2026, 09:53 MEZ)

Branch `jarvis-luecke/video3-alarm-warp-ganzkoerper`, Draft-PR #487 gegen `grok/jarvis-design-system` (#479). Nicht mergen.
Gemergt: #479 @69b76b2f (ref5-Tokens, docs/JARVIS-REF5-FARBEN.md) und Mathematik `jarvis-mathematik/motion-math` @e600add5 (PR #489).

## Stand
- `app/src/main/java/at/heinz/deviceai/ui/video3/`:
  - `Video3Contracts.kt`: Adapter mit denselben Namen wie Kerns Verträge (#481): AlarmKind inkl. SECURITY_FINDING, AlarmInfo, ActionOption, CalmNoticeInfo. Slots: HeadDrawer (Avatar), OrbDrawer (Gedächtnis), AlarmBaseEffects (JarvisSequenceEffects), PillPresenter (Sicherheit G), FullBodyFigure/BodyAnchors.
  - `Video3AlarmScene.kt`: die große Alarm-Stufe, nur bei SECURITY_FINDING. Zoom, Shake, Glow ×3, Wellen und Lichtbahnen. Die Kaskade zeigt Ziffern nur aus Event-Werten.
  - `Video3CalmNotice.kt` + `Video3Pills.kt`: die ruhige Stufe für normale Fehler. Kopf sichtbar, Meldung aus dem Event, Pillen nur mit options; disabled-Optionen sind gedimmt und zeigen ihren Grund.
  - `Video3WarpScene.kt` (C_WARP): Flash, dann Burst, dann linearer Tunnel.
  - `JarvisUserCard.kt`: ref5-Karte „User Recognized“ mit Adaptern OwnerRecognitionState/OwnerRecognition/OwnerRecognitionSource/UserCardRenderer/JarvisUserCardSlot. Vier Zeilen ohne Quelle zeigen immer „—“.
  - `Video3Curves.kt`: **im letzten WIP-Commit auf Mathematik (`at.heinz.deviceai.motion.MotionPresets`) umgestellt, aber NICHT kompiliert und NICHT getestet.**
- Test `app/src/test/java/at/heinz/deviceai/ui/video3/Video3ScenesTest.kt`: 9/9 grün auf SHA 36207100 (kotlinc + JUnit 4 direkt, nicht Gradle). Für spätere SHAs nicht geprüft.
- Status-Literal-Scan (m5-integration-repair, `tools/status-literal-scan/scan.mjs`): 0 Treffer auf 36207100.
- Vorschau: `tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/video3/Video3Preview.kt`. Vergleichsbild (nicht im Git): `/workspace/jarvis-design-referenz/kontrolle-3/lueckenfueller-user-recognized.png`.

## Offen
1. Kompilieren und testen nach dem Mathematik-Tausch (Video3Curves, Warp nutzt `MotionPresets.warpScale`, max 5,2).
2. Ganzkörper-Szene fehlt komplett: Figur (Vitruv-Pose) hinter `AvatarPresentation.FULL_BODY_ORB` mit Ankern für Hände und Brust, Kopf über Avatars `ContinuumAvatarRenderer.draw()` (avatar-ref-match @f322a72a), Kugel über Gedächtnis' `ContinuumHologramRenderer` (Adapter `OrbDrawer`), Magenta-Ringe, Puls `MotionPresets.orbPulse`, radiales Cockpit mit Lasern (`Video3Curves.laser`), Donut und Linie nur mit echten Werten (`Video3CockpitData`). Hinweis für die Vorschau: Avatars Renderer kompiliert nur zusammen mit Avatars JarvisNeon/JarvisMotion (`bloomSegments` fehlt auf #479).
3. User-Recognized-Karte: Die Schrift wirkt größer als in ref5, obwohl sie mit den kleinsten Tokens gesetzt ist (pill/caption/status). Ob ein kleinerer Token nötig ist, entscheidet Welten-Optik. Kern-Verträge (OwnerRecognition, JarvisUserCardSlot) liegen auf #481 noch nicht vor, auf Kern-Typen umstellen, sobald sie gepusht sind. Maße im Raster: links 0,157, oben 0,123, Breite 0,149, Höhe 0,194 der Cockpit-Fläche (ref5 x 283–476, y 180–359 px; 124 × 115 dp).
4. Vergleichsbilder `kontrolle-3/lueckenfueller-alarm|warp|ganzkoerper.png` gegen Einzelbilder aus Video 3 fehlen noch. Vorschau-PNGs für Alarm, Calm und Warp liegen nur ungeprüft in /tmp.
5. §12: DEVICE_FILE-Szene (Ordner-/Datei-Karten fächern auf) und WEB-Szene (Browser-Karte mit echter Seite, Suchtreffer fliegen ein) auf Kerns ActionScene-Vertrag/ActionSceneRegistry. Nur echte Inhalte mit Berechtigung, Pillen über PillActionGate.
6. Abstimmung mit Sicherheit: JarvisSequenceEffects.drawAlarm (0/1-Kaskade, rote Vignette) überschneidet sich mit der Alarm-Szene. `questionPills` hat weder Hit-Test noch disabled-Zustand.
7. App-Overlay und Einbau (CockpitTransitionHook) sind nicht mein Stück, sondern Kerns bzw. Sicherheits.

## Nächster Schritt
Kompilieren (`/workspace/avatar-build/kotlinc` mit der Dateiliste aus `scripts/jarvis-design-preview.sh` + `ui/video3/*.kt` + `motion/*.kt`), Video3ScenesTest laufen lassen, danach die Ganzkörper-Szene (Punkt 2).

## Teststand Kurz-Sprint 10:05 (Code-Stand 731f7194, dieser Commit ändert nur diese Notiz)
- Kompiliert (JVM, kotlinc 2.4.0): ui/ContinuumPainter, ContinuumFonts, ContinuumTheme, JarvisDesign, JarvisNeon, JarvisMotion, motion/* (ohne AvatarPoseBridge, Android-/Avatar-Import) und ui/video3/*. Keine Fehler.
- `Video3ScenesTest` (JUnit 4 direkt, nicht Gradle): **9/9 grün** nach dem Mathematik-Tausch. Ein Code-Fix war nicht nötig.
- Status-Literal-Scan (m5-integration-repair scan.mjs): 0 Treffer.
- Gradle `:app:testDebugUnitTest` nicht gelaufen. Für die User-Card gibt es keinen eigenen Test.

## Endspurt 10:11 – zwei ref5-Kästchen
- `JarvisUserCard.kt` („User Recognized“, links oben neben dem Kopf). Layout in ref5-Pixeln der Karte (193 × 179 px), englische Labels wie ref5. Titel und grünes Häkchen nur bei RECOGNIZED, Untertitel = method, sonst „—“. Presence/Attention/Gaze/Emotion immer „—“; der Presence-Punkt ist gedämpft, nie grün. Raster (Kern): links 0,157, oben 0,123, Breite 0,149, Höhe 0,194 der Cockpit-Fläche.
- `JarvisGreetingCard.kt` (JARVIS-Grußkarte unter dem Kopf). Eingabe `JarvisGreeting(hourOfDay, statusText)`. Der Gruß kommt aus der echten Uhrzeit, ohne Uhrzeit steht „—“. Dahinter steht nur ein neutrales Angebot, kein „fully synced“. Ein statusText erscheint nur, wenn eine echte Quelle ihn liefert. Gemessen in ref5: x 290–565, y 390–555 px. Anteile der Display-Fläche (x 80–1375, y 67–986): links 0,162, oben 0,351, Breite 0,212, Höhe 0,180.
- Tests (JUnit 4 direkt, kotlinc 2.4.0, nicht Gradle): `Video3ScenesTest` 9/9 + `JarvisCardsTest` 2/2 = **11/11 grün** auf dem Code dieses Commits. Status-Literal-Scan: 0 Treffer.
- Vergleichsbilder (nicht im Git): `kontrolle-3/lueckenfueller-user-recognized.png` (UNKNOWN | RECOGNIZED | ref5) und `kontrolle-3/lueckenfueller-jarvis-gruss.png` (Vorschau | ref5).
- Ehrlicher Abstand: Die Schrift ist etwas größer und weniger fett als in ref5 (kleinste #479-Tokens caption/status). „JARVIS“ ist in ref5 fetter und größer. Der violette Verlauf hinter der Grußkarte kommt vom Hintergrund (Welten-Optik/Kern), nicht von der Karte.
