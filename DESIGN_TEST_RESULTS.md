# Design-System – Testergebnisse (4.11.2026)
- `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.ui.JarvisShellLayoutTest` (inkl. compileDebugKotlin): **5/5 grün**, 0 Fehler.
- Geprüft: Form-Faktor-Erkennung Phone 412×915 / Fold7 884×904 / Tablet 1280×800, alle Slots im Fenster, keine Überlappung, Spaltenzahl bei Schrift 1.5×, Glow-Stufen geordnet.
- Nicht ausgeführt (sparsam): Voll-Suite, Lint, Screenshot-Rendering.

## Nachtrag Motion (4.11.2026)
- `--tests JarvisMotionTest --tests JarvisShellLayoutTest`: **7/7 + 5/5 grün**.
- Geprüft: Start-Versatz/Alpha/Skalierung, Landung im Ruhezustand, Rand-Aufleuchten nach der Landung, monotones Alpha, Staffelung, ANIMATOR_DURATION_SCALE 0 und 2, Easing-Endpunkte.

## Prüfbericht-Auflagen und VIDEO-SPEC (4.11.2026)
- `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.ui.Jarvis*'` (inkl. compileDebugKotlin, also auch JarvisShellView): **JarvisMotionTest 12/12, JarvisShellLayoutTest 7/7 grün**.
- Vorschau: `scripts/jarvis-design-preview.sh` (Java2D, Platzhalter-Inhalte). Ergebnis: `docs/design-screenshots/phone-hochformat-412x915.png`, `fold7-offen-884x760.png`, `tablet-quer-1280x800.png`, `phone-einflug-250ms.png` (Einflug-Zwischenstand), daneben ref2/ref4/ref5. Annäherung; kein Gerät/Emulator.

## Review 1 (4.11.2026)
- `--tests 'at.heinz.deviceai.ui.Jarvis*'` (inkl. compileDebugKotlin mit JarvisGlassRenderer/AGSL): **JarvisMotionTest 12/12, JarvisShellLayoutTest 8/8 grün**.
- Vorschau neu gerendert (`docs/design-screenshots/`). Das ist der Java2D-**Fallback-Pfad**: Blur und AGSL-Glow sind darin NICHT sichtbar und nur angenähert. Frame-Zeit auf dem Gerät nicht gemessen (kein Fold7).

## Kontrollgang 2, Abschluss (4.11.2026)
- `--tests 'at.heinz.deviceai.ui.Jarvis*'`: **JarvisMotionTest 13/13, JarvisShellLayoutTest 8/8 grün** (neu: Tiefen-Logik).
- Vorschau neu gerendert (deutsche Texte, Fallback-Pfad).
