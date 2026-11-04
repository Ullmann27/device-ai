# Übergabe Lumo Welten-Optik → ChatGPT (Astra), 04.11.2026

## Stand
- Branch `grok/jarvis-design-system`, Draft-PR #479 (Basis: Branch von #477, enthält #481 @17e1db2b per Merge 9cd1c500).
- Letzter Inhaltscommit `9ca98840`: Tokens für Bloom, Staub und Cyan→Violett-Verläufe (Kachel, Pille, Chevron), NewApi-Fix in `JarvisBloomPass.kt` (SDK_INT ≥ 31 vor `record()`).
- Tests: `:app:testDebugUnitTest --tests 'at.heinz.deviceai.ui.Jarvis*'` auf 9ca98840: 25/25 grün (JDK 17: `JAVA_HOME=/opt/jdk/jdk-17.0.21+9`). Volle Suite nicht gelaufen.
- `lintDebug`: JarvisBloomPass sauber. Verbleibender NewApi-Fehler nur in `AndroidOnDeviceSpeechInput.kt:31` (nicht Teil dieses Stücks, Besitzer muss beheben).

## Relevante Dateien (app/src/main/java/at/heinz/deviceai/ui/)
- `JarvisDesign.kt`: Farben (per Pixel aus ref5), Typo-Skala, `Styles` (Kachel, Button, Pille), Glow-/Bloom-/Staub-Tokens.
- `JarvisNeon.kt`: Glas-Karte, `tile`, `button`, `pill`, `bloom`, aktiver Glow.
- `JarvisBloomPass.kt`, `JarvisGlassRenderer.kt`: Bloom (RenderEffect, API 31+), Backdrop-Blur (API 31+), AGSL (API 33+), Fallbacks.
- `JarvisMotion.kt`: Einflug, Staffelung, Szenenwechsel, Laser, Tiefenstufen und Drift.
- `JarvisShellLayout.kt`, `JarvisShellRenderer.kt`, `JarvisShellView.kt`: Layouts Phone, Fold7 (20/55/25), Tablet, einbaufertige View.
- Doku: `docs/DESIGN-SYSTEM.md`, `docs/JARVIS-REF5-FARBEN.md` (Messwerte mit Koordinaten), Messskript `tools/measure_ref5_colors.py`, `DESIGN_TEST_RESULTS.md`.
- Vergleichsbilder (außerhalb von Git): `/workspace/jarvis-design-referenz/kontrolle-3/welten-optik-*.png`.

## Offene Punkte (aus REVIEW-3, §11, §12 und eigener Kontrolle)
1. Bloom: Hof in der Vorschau schwächer als in ref5, innen heller machen (Kern/Hof-Verlauf nachmessen). `Glow.SUBTLE` geschätzt.
2. Staub: Mindestanzahl von 20 Partikeln entfernen und Dichte strikt nach Fläche setzen. Dichte beruht auf 2 gemessenen Partikeln, vorläufig.
3. Raster: sichtbarer als die gemessene Streuung in ref5, absenken.
4. Verläufe: Kachel- und Pillen-Verlauf umgesetzt. Chevron sitzt mittig statt auf Titelhöhe. Der aktive Button soll zur Mitte heller werden wie in ref5.
5. Glas: `backdropBlur = 24f` ist geschätzt, die Unschärfe ist in ref5 nicht messbar. Glas- und Kachel-Deckkraft 80 % festgelegt.
6. Lint: Fehler in JarvisBloomPass behoben (9ca98840). Offen bleibt nur `AndroidOnDeviceSpeechInput.kt:31` (fremde Datei).
7. Gerät: Blur (API 31), AGSL (API 33) und Fold7-Frame-Zeit (`frameLogging`, Log-Tag `JarvisFrames`) sind ungeprüft.
8. Echte 3D-Neigung und Tiefen-Blur sind auf einem Gerät nicht verifiziert. Tiefenwerte geschätzt.
9. Icons weichen von ref5 ab, Besitzer sind Kern bzw. Integration.
10. Referenz-PNGs ref2/ref4/ref5 liegen in `docs/design-screenshots/`. Nicht gelöscht, Entscheidung bei Heinz.

## Nächster Schritt
Punkte 1–4 schließen, jeweils mit Vergleichsbild in kontrolle-3. Danach baut Kern `JarvisShellView` über die #481-Verträge in `ContinuumHomeView` ein, dann die Geräte-Checks.

## Sprint 10:05 (04.11.2026)
- Erledigt: Bloom-Kern→Hof-Verlauf (glowCore innen → bloomHalo außen, Stopps geschätzt), Kachel-Chevron auf Titelhöhe, aktiver Button mit zur Mitte hellerem Verlauf (navSurface → aufgehelltes navSurfaceActive, Aufhellung geschätzt). Jarvis*-Unit-Tests grün.
- Offen: Stopps/Aufhellung in ref5 nachmessen, Vergleichsbild kontrolle-3 nicht erstellt, Staub/Raster, Gerätetest, Lint in AndroidOnDeviceSpeechInput.kt (fremd).

## Endspurt (04.11.2026, 10:12)
- API für Besitzer: `JarvisNeon.headerChips(p, l, t)` (JARVIS Mode + Local · Private · On-Device First), `JarvisNeon.pill(...)` als Chip-Stil, `JarvisNeon.dotIcon`; Karten nur über `JarvisNeon.glassCard(...)` zeichnen. Renderer-`topBar` platziert die Chips (52,7 % Breite), Marke links (17 %).
- Tests: Jarvis* 25/25 grün, 0 Fehler.
- Offen: Rand-Verlauf Cyan→Violett und Glow von glassCard nicht nach ref5 gemessen (weiterhin flacher Rand #329EC0); Bloom-Stopps und Button-Aufhellung weiter geschätzt; Füllung des Local-Chips (#021E24) nutzt noch pillSurface; JARVIS-Kronen-Icon fehlt; Vergleichsbild endspurt nicht erstellt.
