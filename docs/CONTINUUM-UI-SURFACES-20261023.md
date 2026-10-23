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

## Offene Abnahme / nächster Schritt

Native Widget-Prüfungen abschließen, Zustandsscreenshots prüfen, dann gezielte
Android-JUnit- und Lint-Runde am finalen Code. Danach in der späteren gemeinsamen
APK echte Dialoge, Tastatur, 130 % Schrift, Fold-Wechsel, Back-Navigation,
TalkBack und Stromsparmodus prüfen. Nicht jeder Detaildialog ist hier einzeln
als fertige Geräteoberfläche abgenommen. Besondere Folgeprüfung: Systemanzeigen
auf schmalen Dialogflächen und bei großer Schrift.

Rollback: UI-/Ressourcen-/QA-Commits gezielt revertieren; keine Datenmigration.
Die Nutzungskontingente sind hier nicht verifiziert auslesbar.
