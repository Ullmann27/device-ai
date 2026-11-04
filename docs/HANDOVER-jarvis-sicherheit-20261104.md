# Handover Jarvis Sicherheit → ChatGPT Astra (2026-11-04, ~09:53 MEZ)

## Stand
- #485 `continuum/m5-safe-flows-fg-20261104` (Draft): F/G/D + Local Model Status; letzter Feature-Commit 1e845e8e (dieser Handover-Commit darauf).
- #372 `continuum/m5-integration-repair` (Draft) @ 59344fa46f6fda6a2c0d71ee628c1ce54dab2bc1: B5-Guard, 3900-Cap, Status-Literal-Scanner, Reviews.

## Erledigt
- F/G/D auf #479-Tokens @69b76b2f (`Styles.pill` #07204D/#0175AF, `Type.pill`, `Colors.glowCore`).
- Local Model Status (`ui/LocalModelStatusPresentation.kt`, `CockpitLocalModelCard`): ref5-Kachel, Type.heading/status/caption, englische Labels, Online/Offline-Pille nur aus `system.networkOnline`, fehlend = „—“. (HAUPTBOT a: umgesetzt; Kachelgröße = Kerns Raster.)
- Pillen-Vertauschung (HAUPTBOT b): Ursache war das Render-Fixture; Produktion mappt per Kind korrekt. Bewiesen durch `PillGateMappingTest`; nur STUDIE (web_search) ist aktiv.
- Umschlag im Kino-Look (HAUPTBOT c): #479-Bloom/Glas, Tiefe, ref5-Maßstab.
- PillActionGate/AccountEventSource (`cockpit/SafePillActionGate.kt`), B5-Guard, 3900-Cap, Status-Literal-Scan (0 Treffer), alles auf #372.
- Letzte Testläufe: betroffene Tests grün (4+8+8+2). Volle Suite @a993c352: 2291 Tests, 0 fehlgeschlagen, 19 übersprungen. Die Suite lief nicht erneut auf 1e845e8e.

## Offen
- Kern: State-Feld für `LocalModelResourceSnapshot` + Admission (RAM/Thermal/Load zeigen sonst „—“); Platzierung der Kachel im Raster.
- Lint NewApi `JarvisBloomPass.kt:37` (Welten-Optik/#479) und `AndroidOnDeviceSpeechInput.kt:31` (Basis).
- Gmail-Rot/Papier haben kein REF5-Token (Welten-Optik).
- §12 blockiert: kein ActionScene-Vertrag auf #481 (@80af1c15).
- Der Body von PR #485 ist eventuell nicht aktualisiert (das Update wurde unterbrochen).

## Manifest-Beobachtung (Heinz entscheidet, unverändert)
QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES, com.termux.permission.RUN_COMMAND.

## Dateien
- `cockpit/SafePillActionGate.kt`, `ui/JarvisSequenceEffects.kt` (F/G/D), `ui/LocalModelStatusPresentation.kt`, `ui/JarvisNeon.kt` (Merge)
- Tests: `SafeFlowsFgTest`, `JarvisSequenceFgdTest`, `LocalModelStatusPresentationTest`, `PillGateMappingTest`
- Reviews/Evidenz: `docs/reviews/` auf #372; Bilder: `/workspace/jarvis-design-referenz/kontrolle-3/sicherheit-{fgd,status}.png`
- 2026-11-04 09:57 Sprint: NewApi-Guards inline in JarvisBloomPass/AndroidOnDeviceSpeechInput (5e5545a90); lintDebug nicht verifiziert (Abbruch), Kern-RAM/Thermik-Teil übersprungen.
