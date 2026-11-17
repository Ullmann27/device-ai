# Android-Capture-Prüfung – 12.11.2026

## Ergebnis und Basis

Geprüfte Basis: lokaler Commit `9918891a6da1d74275b087a04f4aa86c79074d46`,
laut Integrationszuordnung derselbe Tree wie PR #799 auf
`5f87bdc610944b93d3173a83c5beeea315abeb66`.

Das Cockpit zeigt eine an Aufgabe, Sitzung und Paket gebundene Android-App.
Es stellt keinen unabhängigen vollständigen Android-Desktop bereit. Der
MediaProjection-Weg fordert zwar den Standardbildschirm an, veröffentlicht
anschließend aber nur einen geprüften Ausschnitt der Ziel-App. Ein anderer
Bildschirmmodus, Launcher oder Paketwechsel wird nicht automatisch zur neuen
Bildquelle. Design, Szene, Originalavatar und Orb sind unverändert.

## Tatsächliche Aufrufkette

| Gegenstand | Quellbefund | Konsequenz |
| --- | --- | --- |
| Zustimmung | `AndroidLiveComputer.beginSession` verlangt Besitzerbestätigung, API 34+, verbundenen Accessibility-Dienst und `CAN_TAKE_SCREENSHOT`. `startProjectionCapture` verlangt zusätzlich `RESULT_OK` und einen gestarteten Vordergrunddienst. | Eine allgemeine Bildschirmfreigabe ersetzt weder die Aufgabenbindung noch die Accessibility-Freigabe. Auch Projection benötigt die Screenshot-Fähigkeit für den Schutzfenstercheck. |
| Android-Dialog | `createProjectionConsentIntent` verwendet `MediaProjectionConfig.createConfigForDefaultDisplay()`. | Angefordert wird das Standarddisplay; das ist die Quelle des späteren Ausschnitts. |
| Fenster-Screenshot | `captureWindow` verwendet `takeScreenshotOfWindow(before.window.id)`. Revision, Epoch, Fensteridentität und Aufnahmetimestamp werden vor Veröffentlichung erneut geprüft. | Ein bestimmtes App-Fenster, kein kompletter Display-Screenshot. Dieser Weg veröffentlicht das ganze Fenster mit Viewport 0..1; er hat keinen Projection-Rechteckzuschnitt. |
| Projection-Zuschnitt | `ContinuumLiveProjectionService.ticket` verlangt genau ein Ziel-APPLICATION-Fenster sowie genau ein eigenes APPLICATION-Fenster. `ProjectionCropGeometry.crop` verlangt vollständige Displayabmessungen und nicht überlappende Fenster. | Geeignet für nebeneinander sichtbare Fenster. Ein vom OS geliefertes Einzel-App-Format oder ein überlappendes Vollbild wird abgewiesen. |
| Tastatur und Systemleisten | `LiveWindowSafety.select` erlaubt nur Tastatur bzw. passive schmale Systemleisten als tolerierte Überlagerungen. Der Projection-Zuschnitt zieht diese Flächen ab und wählt das größte verbleibende Rechteck. `permitsNode` sperrt überdeckte Bedienelemente. | Projection zeigt keine Tastatur-/Systemleistenpixel. Fenster-Screenshots werden hiervon getrennt behandelt; überdeckte Knoten werden trotzdem nicht bedient. |
| Launcher/Home | Es gibt hier keinen Launcher-Sonderfall. Ein explizit gebundenes Launcherpaket müsste vom OS als eindeutiges APPLICATION-Fenster geliefert werden. `NodeActionKind` enthält CLICK, SCROLL_FORWARD/BACKWARD, SET_TEXT und SEND. | Kein automatischer Home-/Systemdesktop-Modus und kein globaler Home-/Back-Befehl in diesem Live-Node-Vertrag. Andere Projektwerkzeuge wurden damit nicht pauschal ausgeschlossen. |
| Appwechsel | `AndroidLiveComputer.perform` und `click` prüfen `request.packageName == binding.targetPackage`. Frame- und Sessionvertrag prüfen dieselbe Quellbindung. | Ein Wechsel zu einem anderen Paket gibt diesem keine Eingabeberechtigung. |
| Hintergrund | Der bestehende `OwnerComputerSessionController` wählt Projection nur in Multiwindow. `AgentActivity.onPause` stoppt außerhalb Multiwindow; `onStop` stoppt außer beim ausstehenden Consent. | Die aktuelle Aktivitätsintegration ist ein sichtbarer App-Arbeitsablauf, kein unbeaufsichtigt im Hintergrund weiterlaufender Android-Desktop. Activity wurde hier nicht verändert. |
| Rotation/Größenänderung | Projection-Resize und `onConfigurationChanged` pausieren, entwerten Schutz-/Geometriedaten und ersetzen die Reader-Surface. `ticket` verwirft alte oder nicht zum Display passende Abmessungen. | Kein Weiterverwenden des alten Ausschnitts während einer Größenänderung. Tatsächliche Fold-/Rotationsaufnahme bleibt geräteoffen. |
| Freigabeentzug | `MediaProjection.Callback.onStop`, Dienstzerstörung, Taskentfernung und Accessibility-Interrupt/Destroy beenden die gebundene Sitzung. Close-Pfade geben VirtualDisplay, Reader und Projection frei. | Späte Bilder dürfen keine beendete Sitzung wiederbeleben. Ein alter Stop-Intent darf eine neuere Sitzung nicht beenden. |

## HardwareBuffer-Transport

`ContinuumLiveProjectionService.createReader` nutzt einen RGBA-Hardwarebuffer,
sRGB-Dataspace, GPU-Sampling und `maxImages=3` für gleichzeitig erworbene
Readerbilder. Maximal eine
GPU-Kopie läuft gleichzeitig. `AndroidGpuFrameCopier` kopiert den geprüften
Ausschnitt über `RenderNode`/`HardwareBufferRenderer` in einen eigenen
unveränderlichen sRGB-Ausgabepuffer. Das Eingabebild bleibt bis zum Fence-Ende
geleast. Der Pfad enthält keine Videoenkodierung oder CPU-Pixelrücklesung;
wegen der GPU-Kopie ist er kein durchgehend kopierfreier Transport.

Der vorhandene `CockpitLiveTexture`-Verbraucher importiert diesen Puffer in
Filaments externen Stream. Er begrenzt native Pufferreferenzen und blendet bei
nicht mehr passendem Live-Status aus. Die reale Fence-/GPU-Interoperabilität
und die Bildschirmrate wurden in dieser Prüfung nicht auf einem Gerät gemessen.

## Behobener Frischefehler

Vorher setzte der Projection-Dienst nach `acquireLatestImage()` einen lokalen
`System.nanoTime()`-Marker. Die GPU-Zustellung war auf 750 ms begrenzt;
`captureAgeMillis` blieb beim Offer jedoch null Millisekunden. Der Frameholder
altert seit Empfang plus diesem Feld. Daher verlängerte die zugelassene
GPU-Zustellzeit die scheinbare Bildfrische.

Der enge Fix in `AndroidLiveComputer.offerProjection` übernimmt die Zeit seit
diesem **lokalen Abholmarker** in `captureAgeMillis`, reduziert vorhandenes
Alter nicht und weist ungültige bzw. in der Zukunft liegende Marker zurück.
Er verwendet weder `Image.timestamp` noch eine angenommene OEM-Zeitbasis.

Repro: Ein um 700 ms verzögertes Offer und weitere 900 ms Haltezeit ergeben
mindestens 1.600 ms Alter. Bei der bestehenden 1.500-ms-Grenze muss der Status
PAUSED sein. Vor dem Fix scheitert die Altersprüfung; danach besteht sie.

**Verbleibende Grenze:** Das Alter eines Bildes vor `acquireLatestImage()`
wird weiterhin nicht ermittelt. Die öffentliche `Image.getTimestamp()`-API
beschreibt eine producerabhängige Zeitbasis, keine pauschale Gleichheit mit
`System.nanoTime()`. Der AOSP-HWC-Pfad verwendet zwar `systemTime()` beim
Queueing, doch dieser einzelne Implementierungspfad ist kein Vertrag für
alle GPU-/OEM-Pfade. Ein Warteschlangenstau vor dem Handler bleibt deshalb
eine konkret benannte Prüflücke. Der Fix beansprucht nur den tatsächlich
gemessenen Abhol-zu-Offer-Zeitraum.

## Ausgeführte Prüfung

67 Tests mit frisch kompilierten Capture-Produktionsquellen, Kotlin 2.4,
JUnit 4.13.2 und Robolectric SDK 28/35. Übrige Abhängigkeitsklassen und
Ressourcen stammen aus einem eingefrorenen Integrations-JAR. Die Ausführung
war über die gemeinsame Build-Sperre serialisiert. Kein vollständiger
Gradle-/APK-Build, keine reale OS-Freigabe, keine Geräte-/GPU-Abnahme.

| Testklasse | Gegenstand |
| --- | --- |
| `LiveComputerFramesTest` | Quelle/Sitzung, Stale-Grenze, Pausieren, Freigabe und begrenzte Pufferhaltung |
| `LiveWindowCaptureTest` | Aufnahmezeit, Epoch/Revision, Fensterwechsel und späte Zustellung |
| `LiveWindowSafetyTest` | eindeutiges Zielfenster, Spiegelung, Overlay, Tastatur und Systemdialog |
| `ProjectionCropGeometryTest` | Split-Screen-Zuschnitt, Vollbild/Überlappung, Einzel-App-/Rotationsabmessungen und Viewport |
| `ContinuumLiveProjectionServiceUiTest` | Vordergrunddienststart, Session-Replay, Resizefehler, Stop, neu: Zustellalter und direkte Freigabeunterbrechung |
| `AndroidWindowCaptureTest` | tatsächlicher Screenshot-Callback, altes Bild/alte Sitzung, Stop und Pufferfreigabe |
| `ScreenObservationRevisionTest` | Ereignisrevisionen, unklare Fenster, unterbrochene Reads und Dienst-Epoch |

Rot: 67 Tests, genau drei erwartete Fehlschläge auf unverändertem
Produktionscode: verlorenes Zustellalter, veraltetes Offer und Zukunftsmarker.
Grün: 67/67, Quellhashes während des Laufs unverändert. Neue direkte
Accessibility-Interrupt- und MediaProjection-onStop-Checks bestätigen
Sessionende und Pufferfreigabe bereits im roten Lauf.

Die bestehenden Geometrie-Repros bestätigen außerdem:

- Display 2184×1968, rechtes Appfenster x=1096..2184, Puffer 1920×1730:
  akzeptierter Zuschnitt x=964..1920, y=0..1730.
- Nur 1088×1968 erfasste Appabmessung, vertauschte Rotationsmaße oder
  überlappendes eigenes Fenster: kein gültiger Zuschnitt.
- Tastatur ab Display-y=1300 und Statusleiste bis y=50: Zuschnitt bleibt
  innerhalb des dazwischenliegenden sichtbaren Bereichs.

Belege: `docs/evidence/android-capture-audit-20261112/validation.json`,
`red.txt`, `green.txt` und `red-source.patch`.

Regulärer Wiederholungspfad mit vollständig bereitgestellter Projekttoolchain
(dieser vollständige Gradle-Lauf wurde hier nicht ausgeführt):

```sh
./gradlew :app:testDebugUnitTest -PcontinuumUiWidgetTests=true \
  --tests '*LiveComputerFramesTest' --tests '*LiveWindowCaptureTest' \
  --tests '*LiveWindowSafetyTest' --tests '*ProjectionCropGeometryTest' \
  --tests '*ContinuumLiveProjectionServiceUiTest' \
  --tests '*AndroidWindowCaptureTest' --tests '*ScreenObservationRevisionTest'
```

## Integrationsgrenze

Kanonischer Zielpfad bleibt die bestehende Code55-Integration / PR #799.
Dieser Patch ändert nur den Projection-Offer-Eingang und passende Tests.
EXP-06/11: Quellbeleg und bestehender Aktionsvertrag bleiben erhalten;
EXP-12: ausschließlich derselbe Sitzungs- und Stop-Vertrag. Kein neues
Rechte-, Transport-, Modell- oder Orchestrierungssystem. Status:
`SOURCE_INTEGRATED` / `MOCK_TESTED`, Gerätebestätigung offen.
Rollback: diesen kleinen Commit revertieren. Kein eigener Push durch diesen
Capture-Agenten; Veröffentlichung führt der Integrationsverantwortliche aus.

## Offizielle API-Quellen

- [MediaProjectionConfig](https://developer.android.com/reference/android/media/projection/MediaProjectionConfig): Standarddisplay-Auswahl, API 34.
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshotOfWindow(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)): bestimmtes Accessibility-Fenster und Screenshot-Capability.
- [MediaProjection](https://developer.android.com/media/grow/media-projection): OS-Consent, Vordergrunddienst, Callback und Größenänderungen.
- [Image.getTimestamp](https://developer.android.com/reference/android/media/Image#getTimestamp()): Nanosekunden mit quellabhängiger Zeitbasis.
- [AOSP VirtualDisplaySurface](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/surfaceflinger/DisplayHardware/VirtualDisplaySurface.cpp): geprüfter Blob `384f7b22c7bf1a3ead4382829845d41c8a05b16d`; kein zusätzlicher öffentlicher Zeitbasisvertrag.
