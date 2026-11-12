# Code55: Voice-Reparatur und PDF-Hostbefund

Stand: 2026-11-12. Integration in `integrate/code55-apk-fable-20261112` nach Fable-Merge.

## Übernommen und repariert

Produkt- und Teständerungen von PR #793, Head `1735b66a3de444e5c41f9e065e5462268c4e10b3`, gezielt portiert. Keine CI-Dateien übernommen. Die neueren Computer-/Cockpit-Hooks aus #799 in `AgentActivity` bleiben erhalten.

- `LocalSpeechSession`: Zuhören erst nach dem echten Android-`ready`-Callback anzeigen; Startanforderung allein zeigt kein aktives Mikrofon.
- `ContinuousConversationMode`: begrenzte Stille-Wiederholungen; frischer Recognizer nach wiederholtem Fehler; Wiederaufnahme derselben aktiven Sitzung nach kurzem Hintergrundwechsel, maximal 15 Sekunden.
- `AgentActivity`: Mikrofon im Hintergrund vollständig schließen; Wiederaufnahme nur bei Vordergrund, Fensterfokus, freiem Eingabefeld, vorhandener Berechtigung/Spracherkennung und ohne laufende Antwort oder Freigabedialog. Wiederaufnahmeaufgaben bei Abbruch/Neustart entfernen.
- STOP-Reparatur aus anschließendem Activity-Review: `cancelLocalRequest()` schließt den Sprachscope jetzt sofort am Eintritt, vor allen frühen Rückgaben. Damit kann STOP während einer App-Suche oder eines anderen Sonderpfads keine ausstehende Vordergrund-Wiederaufnahme hinterlassen.
- Zusätzliche Reparatur gegenüber #793: `suspendForBackground()` markiert bereits `PAUSED` befindliche Sitzungen nicht erneut zur automatischen Wiederaufnahme. Dadurch bleiben bewusste Pausen und Fehlerpausen erhalten; wiederholtes `onPause` verlängert das ursprüngliche Zeitfenster nicht.

Exklusive bearbeitete Dateien:

- `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`
- `app/src/main/java/at/heinz/deviceai/cognitive/ContinuousConversationMode.kt`
- `app/src/main/java/at/heinz/deviceai/cognitive/LocalSpeechSession.kt`
- `app/src/test/java/at/heinz/deviceai/cognitive/ContinuousConversationModeTest.kt`
- `app/src/test/java/at/heinz/deviceai/cognitive/LocalSpeechSessionTest.kt`
- `app/src/uiTest/java/at/heinz/deviceai/ContinuumCockpitHandsFreeUiTest.kt`

## Verifikation

Direkte Kotlin/JUnit-Prüfung der tatsächlichen aktuellen Quelldateien, ohne Gradle und ohne Android-Mocks für die Zustandsmaschine: JDK 17, Kotlin 2.4.0, JUnit 4.13.2. Drei Klassen: `ContinuousConversationModeTest`, `ContinuousConversationRecoveryTest`, `LocalSpeechSessionTest`.

| Stand | Ergebnis |
| --- | --- |
| Neue Regressionstests vor PAUSED-Guard | 53 Tests, genau 3 Fehler |
| Nach PAUSED-Guard | 53 Tests, 0 Fehler |

Die drei zunächst roten Fälle:

- `ownerPauseSurvivesBackgroundWithoutAutomaticListening`
- `failurePauseAlsoNeedsAnOwnerTapAfterBackground`
- `repeatedBackgroundEventsDoNotExtendTheRecoveryWindow`

Belege: [RED-Ausgabe](evidence/code55-voice-20261112/red-junit.log), [GREEN-Ausgabe](evidence/code55-voice-20261112/green-junit.log), [RED-Quellhashes](evidence/code55-voice-20261112/red-receipt.json), [GREEN-Quellhashes](evidence/code55-voice-20261112/green-receipt.json). Beide Kompilierungen erfolgreich; Quellhashes während jedes Laufs unverändert. Zwischen RED und GREEN wurden zusätzlich nur kurze Kommentare angepasst. `git diff --check` erfolgreich.

Zusätzliche Activity-Regression `ContinuumCockpitHandsFreeUiTest.stopDuringAppLookupCancelsPendingForegroundRecovery`: setzt die konkrete Kombination `appLookupActive` + ausstehende Hintergrund-Recovery, betätigt den echten Senden/STOP-Button und prüft das Ende der Suche, `OFF`, gelöschte Recovery sowie ausbleibende Wiederaufnahme. Dieser neue Robolectric-Fall wurde vom Teilagenten noch nicht ausgeführt; er gehört zum gemeinsamen Gradle-Lauf. Der Hauptagent brach den vorherigen Lauf vor diesem Fix kontrolliert mit Exit 130 ab; daraus wird kein Testergebnis abgeleitet.

Der lesende Review bestätigte außerdem: ausgeblendeter Textchat allein beendet Hands-free bewusst nicht, weil #799 die Unterhaltung im sichtbaren START-Cockpit hält. Activity-Hintergrund und fehlender Fensterfokus verhindern Wiederaufnahme; bewusste Pause, Mikrofon-Langdruck, Sitzungs-/Botwechsel und Zerstörung der Activity entwerten sie.

Diese Prüfung belegt die Zustandsmaschine und den lokalen Speech-Adapter. Die vollständige Android-Kompilierung, kombinierte Gradle-Suite, APK und Geräteprüfung erfolgen durch den Integrationslauf des Hauptagenten. Es wird hier kein Geräte-/Mikrofonerfolg behauptet.

## PDF: Hostfehler unverändert, keine Abschwächung

`PdfIntakeNativeDecodeProbeUiTest.kt` wurde nicht geändert. Kein Skipflag gesetzt, keine Assertion entfernt. Bekannter [CI-Lauf 43177154472](https://github.com/Ullmann27/device-ai/actions/runs/43177154472), Quellstand `921cc382a1510ae5cf047aafa82900a945def731`: SDK-35-Probe scheitert mit `NoSuchMethodError: long java.io.FileDescriptor.getOwnerId$()` über `IoUtils.acquireRawFd` / `ParcelFileDescriptor.detachFd` / `PdfProcessor.create`.

Prüfung der tatsächlich gecachten Laufzeit:

- `robolectric:shadows-framework:4.14.1` enthält keinen PDF-Shadow; `ShadowParcelFileDescriptor` implementiert kein `detachFd`.
- Im verwendeten `android-all-instrumented-15-robolectric-12650502-i7.jar` liefert die instrumentierte Implementierung von `PdfDocumentProxy.createFromFd` null; `render` liefert false und `getPageText` null. Ein isolierter Ersatz für die fehlende `FileDescriptor`-Methode stellt deshalb noch kein echtes PDF-Decoding her.
- Die untersuchte native Robolectric-Laufzeit ist `nativeruntime-dist-compat:1.0.16`; im exportierten Symbolbestand war kein PDF-Entry-Point vorhanden.
- Auch der für diese Untersuchung gelesene vollständige offizielle Robolectric-Quellbaum enthielt keine PDF-Implementierung. Ein ungeprüftes Versionsupgrade ist damit keine belegte Reparatur.

Konsequenz: kein Skip, keine Attrappe und kein künstlicher Erfolgswert. Echte Rendering-/Textextraktionsabnahme benötigt die Android-PDF-Laufzeit auf API 35+ (Gerät/Emulator) oder eine vollständig implementierte kompatible Hostlaufzeit. Der tatsächliche Ausgang der unveränderten vollständigen Suite ist separat zu berichten.

## Übergabe

Voice-Dateien für den kombinierten Build freigegeben. Während des Builds erfolgen durch diesen Agenten keine Produkt-/Teständerungen, kein eigener Gradle-Lauf, kein Staging und kein Commit. Signatur und Paketkennung wurden nicht verändert.
