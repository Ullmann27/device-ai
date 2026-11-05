# JARVIS Visual – Astra-Fortsetzung, 05.11.2026

## Eigene Host-Belege

Ausgangsquelle: `b294fc9139f1a458fccf815bb931eb61b2f7b0e8`.
`a15624bed87470260adef810ce1610b986f9f02d` ergänzt nur die Übergabe.
JDK17 / SDK36 / Gradle8.11.1 / Kotlin2.4.0 / Robolectric, kein Gerät.

| Lauf | Tests | PASS | FAIL | SKIP | Exit |
|---|---:|---:|---:|---:|---:|
| baseline-visual | 5 | 5 | 0 | 0 | 0 |
| frame-clock-red | 7 | 5 | 2 | 0 | 1 |
| frame-clock-green | 7 | 7 | 0 | 0 | 0 |

Die Läufe überlappen; Zahlen werden nicht addiert. Die vier Bildschirmtests
zeichnen echte AgentActivity-Canvas-Aufnahmen mit einem 2D-Ersatzkopf, keine
Filament-GPU-Pixel. Die fünfte Prüfung ist die bestehende Avatar-Bewegungszeitreihe.

### Reproduzierter Frameclock-Fehler

`frameLoggingHonoursReducedMotion` und
`frameLoggingDoesNotKeepUnfocusedShellDrawing` erwarten nach dem Abklingen keine
weiteren Draws. Tatsächlicher RED: `expected 22, was 37` in beiden Fällen. Die
Debug-Diagnose startet bislang einen zusätzlichen Choreographer-Callback, der
unabhängig von den Animation-/Fokusgrenzen immer `invalidate()` aufruft.

Der Patch entfernt diese aktive Diagnoseuhr. Framezeit und Zeichenkosten werden
im ohnehin ausgeführten `onDraw` beobachtet. Kein zweiter Animationstakt;
Fokusverlust, Stromsparmodus und reduzierte Animation behalten ihre Wirkung.
Die vorhandenen fünf Lifecyclefälle und die zwei neuen Gegenproben bestehen.
Die `FrameSample`-Werte sind UI-Zeichnungen, keine Filament-/GPU-FPS.

Rohlogs, XML und ausführbare Befehle stehen je Lauf im Unterordner.
`frame-clock-red-green.patch` bindet die Änderung an die Ausgangsquelle.
Weitere Bot-Läufe und eine kombinierte Endabnahme stehen noch aus.

## Gerät und Release

Fold7, reale Kamera, echte TTS, Geräte-FPS, APK und Installation: `NOT_RUN`.
PR #565 bleibt Draft/offen; kein Main-Merge, keine Paket-/Signatur-/Versions-
änderung und keine Veröffentlichung einer App.
