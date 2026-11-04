# Design-System – Testergebnisse (4.11.2026)
- `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.ui.JarvisShellLayoutTest` (inkl. compileDebugKotlin): **5/5 grün**, 0 Fehler.
- Geprüft: Form-Faktor-Erkennung Phone 412×915 / Fold7 884×904 / Tablet 1280×800, alle Slots im Fenster, keine Überlappung, Spaltenzahl bei Schrift 1.5×, Glow-Stufen geordnet.
- Nicht ausgeführt (sparsam): Voll-Suite, Lint, Screenshot-Rendering.

## Nachtrag Motion (4.11.2026)
- `--tests JarvisMotionTest --tests JarvisShellLayoutTest`: **7/7 + 5/5 grün**.
- Geprüft: Start-Versatz/Alpha/Skalierung, Landung im Ruhezustand, Rand-Aufleuchten nach der Landung, monotones Alpha, Staffelung, ANIMATOR_DURATION_SCALE 0 und 2, Easing-Endpunkte.
