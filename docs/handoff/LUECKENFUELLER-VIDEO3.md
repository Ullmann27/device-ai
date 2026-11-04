# Übergabe-Notiz Jarvis Lückenfüller – Video 3 (Stand Abschluss 04.11.2026)

Branch `jarvis-luecke/video3-alarm-warp-ganzkoerper`, Basis #479 `grok/jarvis-design-system` @35a68f49. Paket `at.heinz.deviceai.ui.video3`.
Nur Aussehen + Bewegung. Reine Renderer `render(painter, width, height, seconds, …)`. Kein eigener Auslöser, kein Bus, keine Aktion.

## Status: WIP, ungeprüft
- Nicht kompiliert, keine Tests, keine Vorschau-Bilder (Abschluss um 09:30 vor dem ersten Kompilieren). **Teststand: nicht geprüft.**

## Fertig geschrieben (ungeprüft)
- `Video3Contracts.kt`: Adapter mit denselben Namen und Feldern wie Kerns Verträge auf #481 @17e1db2b: `Video3AlarmKind` (inkl. `SECURITY_FINDING`, `playsAlarmScene`), `Video3AlarmInfo` (+options), `Video3ActionOption`, `Video3CalmNoticeInfo`. Slots: `HeadDrawer` (Avatar), `OrbDrawer` (Gedächtnis), `AlarmBaseEffects` (JarvisSequenceEffects), `PillPresenter` (Sicherheit G), `FullBodyFigure` + `BodyAnchors` (Hände/Brust), `Video3CockpitData`, `Video3Options` (reduzierte Bewegung, ohne Glow).
- `Video3Curves.kt`: easeInExpo/easeOutCubic aus JarvisMotion (#479). Für Jarvis Mathematik als **MATHEMATIK-TAUSCH** markiert: `easeIn` (0,5 s), `shakeX/shakeY(seed, timeMs)` (deterministisch, 2 px), `orbPulse`, `Fallback : AlarmBaseEffects`.
- `Video3AlarmScene.kt` (die große Stufe, nur bei `SECURITY_FINDING`): Hintergrund ohne UI, Z-Zoom ins Gesicht mit Ease-in über 0,5 s, zackige Cyan-Wellen, Lichtbahnen, Glow ×3 und Shake über `AlarmBaseEffects`. In der Kaskade stehen lesbare Ziffern nur aus measured/threshold des Events, ohne diese Werte nur Lichtstriche. Panel „Sicherheitsbefund erkannt“ mit Quelle, Messwert/Schwelle oder „nicht verfügbar“ und dem Hinweis „Keine automatische Maßnahme“. Pillen nur, wenn das Event options mitbringt.
- `Video3CalmNotice.kt` (die ruhige Stufe für normale Fehler und Status): kein Shake, kein Zoom, kein Warp. Der Kopf bleibt sichtbar. Angezeigt werden die message des Events und die Quelle, Pillen nur bei options. `optionAt()` meldet die Auswahl nur zurück.
- `Video3Pills.kt`: Standard-`PillPresenter` (Muster G über JarvisMotion.Presets.pill + JarvisNeon). Deaktivierte Optionen erscheinen gedimmt mit disabledReason.
- `Video3WarpScene.kt` (C_WARP): Bloom-Flash, danach Burst von 0 auf 600 % (Muster A, easeInExpo 0,8 s), danach ein linearer Tunnel aus Dokumentkarten und Ordnern mit Streaks. Feste Anzahlen (20/44/72), Geometrie vorberechnet. Bei reduzierter Bewegung ein Standbild.

## Unfertig / offen
- Die Ganzkörper-Szene (`Video3FullBodyScene`, `VitruvianWireframeFigure` hinter FULL_BODY_ORB mit Ankern für Hände und Brust, Magenta-Ringe, Puls-Overlay, radiales Cockpit mit Lasern, Donut und Linie aus echten Werten) ist **nicht geschrieben**. Ebenso fehlt der Adapter `HologramOrbDrawer`, der den ContinuumHologramRenderer von Gedächtnis einbindet.
- JVM-Tests (Szene startet nur bei SECURITY_FINDING, reduzierte Bewegung ohne Shake/Zoom, ohne Glow, keine lesbaren Ziffern ohne Werte), Java2D-Vorschau (t=10.5/11.5/13/16/18.5 s, Fold7 quer) und Status-Literal-Scan (m5-integration-repair) sind nicht gelaufen.
- Es gibt noch Allokationen pro Frame innerhalb von JarvisNeon (Fill-Objekte). Die eigenen Geometrien sind vorab angelegt.
- App-Overlay und Einbau sind nicht mein Stück (Kern/Sicherheit).

## Andockpunkte für Kern (#481 @17e1db2b)
- `AlarmRaised(kind = SECURITY_FINDING)` → `CockpitTransitionHook` ruft `Video3AlarmScene.render(…, seconds since atMillis, info)` auf. Die Felder werden 1:1 gemappt. Andere Kinds spielen die Szene nicht (`render` gibt dann false zurück).
- `CalmNotice` → `Video3CalmNotice.render`. Die Auswahl geht über `optionAt()` → `PillActionGate.requestOption(option, turnId, onDecision)`.
- `AreaChanged(pattern = C_WARP)` → `Video3WarpScene.render` (nur nach echtem SECURITY_FINDING).
- `AvatarPresentation.FULL_BODY_ORB` → (offen) Ganzkörper-Szene.

## Look-Slots
- Kopf: Jarvis Avatar `ContinuumAvatarRenderer.draw(p, w, h, pose, seconds, motion)` (avatar-ref-match @e5b7a7c7) über `HeadDrawer`. Auf der Basis fehlt die Klasse, Default `HeadDrawer.NONE`.
- Kugel-Mesh: Jarvis Gedächtnis `ContinuumHologramRenderer` über `OrbDrawer`.
- Alarm-Basis: `JarvisSequenceEffects.glowBoost/shakeOffsetX/Y` (nur lesen) über `AlarmBaseEffects`.
- Pillen G: `JarvisSequenceEffects.questionPills` über `PillPresenter`.

## Abstimmung mit Jarvis Sicherheit
- JarvisSequenceEffects wurde nicht verändert. Es überschneidet sich mit drawAlarm() (rote Vignette und 0/1-Kaskade). Meine Kaskade zeigt nur Event-Werte, eine von beiden muss im Einbau weg.
- Frage: `questionPills` bietet weder Hit-Test noch Zustand „disabled + reason“. Wird eine Signaturerweiterung gewünscht?
