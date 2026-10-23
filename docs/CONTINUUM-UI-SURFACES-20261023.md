# Gemeinsame Continuum-Bedienoberfläche – Wiederaufnahme

Branch: `astra/continuum-live-hologram-20261023`, Draft-PR #91.
Ausgangspunkt dieses Abschnitts: `1f21ffdb` (gesicherter Hologramm-Sprint).
Aktuellen SHA vor Fortsetzung lesen, keine fremden Worktrees ändern.

## Gemeinsame Bausteine

- `ui/ContinuumControls.kt`: native Ripple-, Pressed-, Fokus-, Hover-, Selected-
  und Disabled-Zustände; lesbare Textfarben und Button-Semantik. Kein eigener
  Animationstimer, keine Änderung eines Aktionshandlers oder einer Freigabe.
- `ui/ContinuumGridPolicy.kt` und `ContinuumBentoCockpit`: Raster nach gemessener
  Inhaltsbreite und Schriftgröße; vorhandene Views/Callbacks bleiben beim
  Umbruch erhalten. Größere Status-/Aktionstexte, zugängliche Überschriften.
- Gemeinsame Action-/Chip-/Karten-Fabriken in `AgentActivity`: gleiche
  Oberflächenfamilie in Chat, Lernen, Forschung, Speicher, Entwicklung und
  Diagnose. Chat-Plus/Mikrofon haben 48-dp-Ziele; Textaktionen dürfen wachsen.
- Startkarten und echte Navigationsziele erhalten Touch-/Fokusfeedback.
  Navigation meldet den tatsächlichen Selected-Zustand an Android.
- `res/values/styles.xml` + Dialog-Surface/-Textfarben: gemeinsamer nativer
  Dialogstil für bestehende Detail-/Bestätigungsfenster. Inhalte, Abbrechen,
  Freigaben und System-Permissiondialoge werden nicht umprogrammiert.

Bezug: Präsentation bestehender EXP-00/04/05/08/11; EXP-12-Daten-/Identitäts-
grenzen bleiben unverändert. LM1/NF/CCC1, Gewichte, Coding-Protokolle und
Paket-/Versions-/Signierparameter sind nicht Teil dieses Abschnitts.

## Reproduzierbare Prüfung

```sh
gradle -PdeviceAiFeaturePreview=true -PcontinuumUiWidgetTests=true \
  :app:testDebugUnitTest --tests 'at.heinz.deviceai.ui.ContinuumControlsTest' \
  --tests 'at.heinz.deviceai.ui.ContinuumGridPolicyTest'
```

Die zusätzlichen Widget-Prüfungen sind ein expliziter Desktop-QA-Pfad unter
`app/src/uiTest/`. Robolectric 4.14.1 führt Android-Widgets und native
Skia-Textmessung auf API 28 aus. SDK-JARs kommen ausschließlich über Gradles
Repository-/Proxy-/Cacheweg. Die normalen Android-/Termux-Builds und die
vorhandenen Tests behalten ihre bisherigen Abhängigkeiten. Keine bisherige
Prüfung wurde abgeschaltet. Das ist kein Samsung- oder TalkBack-Gerätenachweis.

Der zunächst verwendete Legacy-Grafikmodus lieferte keine echte Textmessung;
der Umbruchtest schlug deshalb fehl. Native Grafik bleibt für diese Prüfung
verbindlich. Die unveränderte Behauptung „alle Tests bestanden“ wäre falsch
gewesen; zunächst wurden SDK-Transport und Grafikbackend korrigiert.

Erster abgeschlossener Abschnitt: 31 vorhandene Node-UI-Verträge und alle
4 neuen Tests (3 native Widget-Prüfungen plus Raster-Policy) bestanden.
Android-Kotlin-Kompilierung erfolgreich. Belege: `docs/evidence/ui-surfaces-20261023/`. Keine APK gebaut/installiert.

## Abgeschlossener Abschlussabschnitt

Erster UI-Checkpoint: `9e14ebf4b031824569551c669897ab8171fd2ed2`, veröffentlicht.
Dieser Folgecommit enthält die final geprüften Ergänzungen und Nachweise.

- `ContinuumSystemGaugesView`: Systemringe wechseln bei enger Fläche/großer
  Schrift in zwei beziehungsweise eine Spalte. Beschriftungen wachsen korrekt;
  unbekannte Telemetrie bleibt „—“. Gradienten/Typeface werden wiederverwendet.
- `ContinuumBentoCockpit.Glass`: Drawable-Alpha wird beim Zeichnen tatsächlich
  beachtet; abgerundeter Outline für native Schatten; wiederverwendete Rechtecke.
- Native Tests prüfen zusätzlich Alpha, sichtbare Buttonzustände, Dialog-
  Abbrechen/Bestätigung und Panels bei 320/720 dp und 130 % Systemschrift.
- Final: **50 gezielte Android-/JVM-Tests, 0 Fehler, 0 übersprungen**;
  **31 Node-UI-Vertragstests bestanden**; Android-Kotlin-Kompilierung erfolgreich;
  konfigurierter Lint ohne Befunde. Bestehender Lint prüft nur `NewApi`.
  Testzahlen früherer Checkpoints überschneiden sich und werden nicht addiert.

```sh
gradle -PdeviceAiFeaturePreview=true -PcontinuumUiWidgetTests=true \
  -PcontinuumUiEvidenceDir=/absolute/path/to/ui-evidence \
  :app:testDebugUnitTest --tests 'at.heinz.deviceai.ui.*' \
  --tests 'at.heinz.deviceai.HologramAudioTest' \
  --tests 'at.heinz.deviceai.TtsHologramSignalTest' \
  --tests 'at.heinz.deviceai.ContinuumWorkAreaNavigationSpecTest' \
  :app:lintDebug --console=plain
node --test .github/continuum/aaa-cockpit-and-effects.test.mjs \
  .github/continuum/android-holographic-home.test.mjs \
  .github/continuum/fold-responsive-reflow.test.mjs \
  .github/continuum/learning-wave-animation.test.mjs \
  .github/continuum/app-navigation-wiring.test.mjs
```

Toolchain: JDK 17, Gradle 8.11.1, Android SDK 36. Warnung: Kotlin 2.5 wird
mindestens Gradle 8.14.4 benötigen; keine Toolchainmigration in diesem UI-Sprint.
Nachweise und Test-Summen: `docs/evidence/ui-surfaces-20261023/`.

[Schmale Fläche, 130 % Schrift](evidence/ui-surfaces-20261023/panel-320dp-font130.png) ·
[Breite Fläche, 130 % Schrift](evidence/ui-surfaces-20261023/panel-720dp-font130.png).
Diese Bilder zeigen echte native Widgets im Desktop-Test, **keinen vollständigen
App-Screenshot und keine Geräteabnahme**. Die Testaktionen führen keine realen
Forschungs-/Löschaufträge aus. Unbekannte Werte bleiben ausdrücklich unbekannt.

## Offene Abnahme / nächster konkreter Schritt

Vor Fortsetzung `git fetch origin`, `git status --short` und `git rev-parse HEAD`;
Draft-PR #91 und diese Datei lesen. Bei paralleler Arbeit eigenen Worktree
vom veröffentlichten PR-HEAD verwenden. Obigen gezielten Testbefehl ausführen.

Nächster fachlicher Schritt ist die kontrollierte gemeinsame UI-/KI-Integration
und Geräteabnahme: echte Dialoge, Tastatur, 130 % Schrift, Fold-Wechsel,
Back-Navigation, TalkBack, Hintergrund/Wiederaufnahme und Stromsparmodus.
Nicht jeder Detaildialog ist einzeln abgenommen. Keine gemessenen Geräte-FPS,
GPU- oder Akkuverbesserungen behauptet. Keine neue APK gebaut oder installiert.
Die spätere Besitzer-APK benötigt das unveränderte Paket
`at.heinz.deviceai.mentorpaired`, ursprüngliche Signatur und Versionscode >17.

Rollback: UI-/Ressourcen-/QA-Commits gezielt revertieren; keine Datenmigration.
Die Nutzungskontingente sind hier nicht verifiziert auslesbar. Auf die gemeldete
20-%-Restgrenze hin wurde der Umfang geschlossen, getestet und veröffentlicht.
