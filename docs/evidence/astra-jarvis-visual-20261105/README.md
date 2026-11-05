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

## Posenabhängige Kinn-Überlappung

Produktbasis `0bfa86db8b7364dc6129bd6b87e9f787f64e41b0`.
Der neue Test `turningChinNeverEntersTheDialogueCard` rechnet mit unabhängiger
Linear-Blend-Skinning-Geometrie der unveränderten GLB. RED: 7 Fälle, 6 PASS,
1 FAIL, 0 SKIP; bei 884×760 bis 67,062326 dp Eindringen in die Dialogkarte.

Ursache: bisherige Einpassung verwendet nur die Ruhe-Silhouette und überspringt
bei großen Bühnen die Dialog-Kollisionsprüfung vollständig. Jetzt umfasst die
Silhouette die geprüften Kopfposen und konservativ die Head/Neck/Rest-Mischungen.
Die Suche prüft beide zulässigen Richtungen vom bevorzugten Anker und maximiert
die Größe unter den Kollisionsgrenzen. Kronenrand, Rig, Materialien und GLB bleiben.

Die stärkere Anforderung verändert notwendigerweise frühere reine Größen- und
Ankererwartungen: 87,5 % bzw. unveränderte Tablet-Komposition dürfen kein Gesicht
über der Dialogkarte erzwingen. Zwei Zwischenläufe bleiben negativ archiviert
(7 Fälle: zunächst 5 PASS/2 FAIL, dann 4 PASS/3 FAIL). Diese alten Layoutannahmen
wurden ausdrücklich auf die nun geforderte kollisionsfreie Posenreichweite
angepasst; die neue geometrische Nichtüberlappungsprüfung bleibt unverändert.

Abschließende eigene `avatar3d.*`-Regression: **61 PASS, 0 FAIL, 0 SKIP**, Exit 0.
`framing-red-green.patch` enthält den vollständigen Delta. Das sind Host-
Geometrie- und Mappertests, weiterhin kein Filament-/Fold7-Abnahmeergebnis.
