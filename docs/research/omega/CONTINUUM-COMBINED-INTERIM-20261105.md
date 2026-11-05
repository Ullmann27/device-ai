# C1-R3 integrierter Kandidat: lebendiger Originalkopf + Originalkugel + Neon-Oberfläche

**Status: SOURCE_STAGED / NEW_TESTS_NOT_RUN / APK_BLOCKED.**

## Quell- und Kompatibilitätsprüfung

Träger `f4abfe9c288981443e91b708ace72de0e72bdf9e` (#527 + #539 + #542 + aktueller GLB aus #535).
Avatar-Idle-Spender `1419e0f738accec7eb9c8916e481688125bbe37b` (#540). Neon-Spender `3b491eb96fafca2d7a25a6f41dabe9c35b144e5a` (#541).
Alle sechs Quellpfade vor dem Patch über aktuelle GitHub-Blobs abgeglichen. **Kein** pauschaler Import der divergierten Designbranch-Historie.

- **Avatar-Blinzeln:** Übernahme nur der `AvatarIdleMotion`-Formel mit deterministisch variierenden Blinkzeiten; identische alte Schnittstelle zu dem vorhandenen Morph-Mapper. Neues JUnit `AvatarNaturalBlinkTimingTest.kt`.
- **Neon-Kacheln:** `JarvisTiles` behält die #527-Basismenüs und die existierenden Zustands-IDs, bekommt die kontrollierte 14-Sekunden-Randlichtphase und zwei zusätzliche Konturstriche. `JarvisShellRenderer` reicht den vorhandenen `timeMs` durch, ohne eigene Uhr.
- **Sichtbarkeits-/Energieschutz:** `JarvisShellView` behält *ausdrücklich* die C1/PR #527-`compactPresentation`-Logik inklusive `layoutCompact`-Cache; nur Ambient-Freigabe, PowerManager-Schranke und gedrosselte `postInvalidateDelayed(66L)` nach dem Einstieg hinzugefügt. Keine 1:1-Ersetzung durch die ältere Design-Datei aus #535; dadurch würde das Compact-Lifecycle-Verhalten verloren gehen.
- **Gehirnkugel:** #539 Original-Renderer und #542 NaN-/Unendlichkeits-Werteabsicherung bleiben in der gemeinsamen C1-R3-Linie erhalten.
- **Besitzerkopf:** GLB SHA256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`, 12.038.416 Bytes; dazu Herkunftsnachweis, Laufzeitpin, MorphMapper, Filament-Framing bereits im Parent-Commit. Alle 21 Morph-Ziele erhalten.

## Testabgrenzung

Die isolierten Kotlin-Host-Smokes der Fach-PRs wurden erneut ausgeführt:
`AvatarIdleNaturalBlinkSmoke.jar`: 92.001 Abtastungen/460 s, 120 Blinzler, gültige Gewichte, erster Blink 4,525s.
`TileAmbientSmoke.jar`: 4 Ziel-Kacheltypen × 1.400 Zeitpunkte, Farbphase stetig und 14s-zyklisch.
`OrbSignalNumericSmoke.jar`: Ausgangs-NaN reproduziert, 343 normalisierte Zahlenfälle gültig.

**Wichtig:** Diese Smokes sind keine komplette Project-Gradle-/Android-Abnahme. Die neuen JUnit-Dateien und alle vollständigen Android-/Node-/Backend-/Lint-Gates sind auf diesem **neuen** zusammengeführten Baum NOCH NICHT AUSGEFÜHRT. Original-GLB, Filament-Materialien, Fold7-/GPU-/TTS-/Kamera-Verhalten sind noch nicht gerätegeprüft. Die älteren 3.032 Android-PASS von #527 und 184 Host-PASS von #535 gelten NICHT für den neuen Gesamtbaum.

## Weitere unverbundene Arbeiten

`JarvisEnergyFlows`/`JarvisHeadAura` des Designzweiges sowie dessen C1-besessene Änderungen in `ContinuumHologramView.kt`/`AgentActivity.kt` und die restlichen Menüs/Layouts benötigen einen separaten geprüften Integrationsschritt. Ebenso die neuesten Gehirn-/Bot-/Gewichts-Fachzweige; keine fremden Eigentümerdateien blind überschreiben.

Release ausschließlich nach vollständig bestandenen Gates und ausdrücklicher SHA-/Freeze-Freigabe: Paket `at.heinz.deviceai.continuumlocal`, Originalsigner `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, strikt steigendes versionCode und der vorhandene Render-Host. Kein `main`-Merge, keine App-Zweitkopie oder APK-Veröffentlichung.
