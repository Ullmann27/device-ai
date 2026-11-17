# Astra55 Live-Computer – laufender Checkpoint

Basis: Code55 `1b90611388cc2b1978ddfe32064a25dd3a08cee1`, PR #791.
Integration: Astra-Hauptzweig / PR #799. Experimente: EXP-06/08/11, bestehende Auftrags- und ToolGate-Grenzen bleiben erhalten.

## Status

PORTIERT: Opus C `0b4e56dae` und Audit `f04836790`.
Neue Android-Implementierung: WIP / UNVERIFIED bis unten konkrete Prüfbelege ergänzt sind.
GERÄTEBESTÄTIGT: NOT_RUN. Kein APK-, FPS-, Fold7- oder Temperaturerfolg behauptet.

## Echte Quelle und Anschluss

`AndroidLiveComputer.startWindowCapture(context, taskId, targetPackage, ownerConfirmed)`
startet eine ausdrücklich beauftragte Sitzung. `ownerConfirmed` kommt vom konkreten
Besitzerauftrag bzw. dessen vorhandener ToolGate-Freigabe, niemals aus Modelltext.
Zusätzlich muss Androids Bedienungshilfe vom Besitzer aktiviert sein und die
Screenshot-Capability gewähren. Widerruf beendet die Sitzung.

Android 14+ `AccessibilityService.takeScreenshotOfWindow(windowId, ...)` liefert
HardwareBuffer des exakt gebundenen Fremd-App-Fensters. Auswahl über `windows`,
nicht `rootInActiveWindow`: Im Split-Screen darf Continuum den Fokus besitzen.
Aufeinanderfolgende Screenshots werden mit 350 ms Abstand nach Abschluss angefragt;
dies ist eine begrenzte Anfragerate, keine gemessene Bildrate. Kein Videoencoder,
PNG, Base64, Screenshot-Archiv oder Modellaufruf pro Frame.

Der Monitor konsumiert `AndroidLiveComputer.feed`: `status`, `acquire`, `version`.
Payload: `AndroidLiveFrame.hardwareBuffer`, `.colorSpace`, `.width`, `.height`.
Lease nach GPU-Verbrauch schließen. Höchstens aktuelles Bild plus ein geleastes
Vorgängerbild. Maximal 4,5 Millionen Pixel pro Bild; volle Fold7-Fenster benötigen
mehr als die ursprüngliche 2,1-Megapixel-Grenze. Farbe/Aspect bleiben im Renderer.
Quelle/Window werden vor und nach dem asynchronen OS-Callback geprüft. Veraltete
Sitzungen, geschützte Bilder und Eigenfenster werden verworfen, nicht LIVE angezeigt.

## Eingaben

`observe(taskId)` / `perform(NodeActionRequest, LiveActionEventSink)` /
`click(taskId, ClickRequest, ownerConfirmed, onDispatched)` laufen auf einem Worker.
Beide Eingabe-APIs verlangen zusätzlich `permissionCheck: () -> Unit`; der
Default verweigert. Der konkrete Owner-Auftrag mit App, Suchtext/Entwurf,
Schritttypen, Ablaufzeit und Widerruf wird unmittelbar vor `performAction` geprüft.
Vor jeder Eingabe: Grant, Task, exaktes Window, Revision, Tree-Digest, eindeutiger
Node, sichtbare Bounds und IME/Overlay-Ausschluss. Danach neue Beobachtung und
konkrete Nachbedingung. Android-Akzeptanz ist kein Erfolg. Unklare Aktionen werden
im bestehenden C-Journal blockiert. Kein SEND-Tool registriert, kein Versand.

`LiveComputerStart.sessionId` ist die Aufnahmegeneration; `.observationEpoch` ist
die davon getrennte Accessibility-Freigabegeneration. Beide nicht gleichsetzen.
Activity-Lifecycle und Stop-Schaltfläche müssen `AndroidLiveComputer.stop()` rufen.
Root integriert Activity/ToolRegistry; Workflow-Agent liefert den Suchablauf,
Scene-Agent rendert das Hardware-Bild im Monitor.

## MediaProjection als flüssiger Pfad

`createProjectionConsentIntent(context)` fordert ab API 34 ausdrücklich
`MediaProjectionConfig.createConfigForDefaultDisplay()` an. Nach dem OS-Dialog:
`startProjectionCapture(context, taskId, targetPackage, resultCode, resultData, ownerConfirmed)`.
Die Antwort liefert eine neue `sessionId` und die aktuelle `observationEpoch`.
Emitter erst an diesen Start binden. Bei verweigertem OS-Dialog bleibt der
separat gestartete Window-Screenshot-Fallback möglich. Kein Token wird gespeichert
oder mehrfach verwendet.

`ContinuumLiveProjectionService` ist ein privater `mediaProjection`-Foreground-Service.
Er registriert Stop/Visibility/Resize vor dem einzigen VirtualDisplay und zeigt
eine dauerhafte Beenden-Aktion. Widerruf, Activity-Stop und Task-Remove beenden die
Quelle. Rotation ersetzt Surface/Reader über denselben VirtualDisplay.

`ImageReader(maxImages=3).acquireLatestImage()` liefert den Displaystream. Die
Annahmegrenze beträgt 33 ms; **30 FPS sind nicht gemessen**. Während einer GPU-Kopie
wird jeder weitere Frame verworfen. `HardwareBufferRenderer` kopiert ausschließlich
das geprüfte Fremd-App-Rechteck in einen eigenen Buffer. Ein Worker wartet den
GPU-Fence ab, bevor das ImageReader-Image freigegeben wird. Der Monitor bekommt
unveränderliche Pixel, keinen wiederbeschriebenen Producer-Buffer. Maximal eine
laufende Kopie plus zwei gehaltene Ausgabeframes. Bei einem Treiber-Fence-Hänger
bleibt genau eine Quelle gebunden; UI/Stop blockieren nicht, fehlende frische
Ausgabe wird PAUSIERT. GPU-/Lebensdauerabnahme auf Android bleibt offen.

Der Resize-Callback muss die gesamte Display-Ausdehnung bestätigen. Target- und
Eigenfenster müssen getrennt sichtbar sein und werden vor und nach der GPU-Kopie
verglichen. Crop-Kanten runden nach innen; eigene Pixel, IME und Systemleisten
bleiben außerhalb. Displaystream maximal 1920 Pixel an der langen Kante.
Ein periodischer exakter Window-Screenshot prüft zusätzlich Secure-Window-
Verweigerungen. Keine Bilder werden persistiert oder an Modelle/Netzwerk geschickt.

## Bewusste Plattformgrenzen

MediaProjection-Single-App-Capture verrät dem Aufrufer das ausgewählte Package
nicht. Die gerade fokussierte Accessibility-App beweist deshalb nicht, dass deren
Pixel aufgenommen werden. Deshalb wird der Default-Display-Vertrag angefordert
und seine Ausdehnung geprüft. Android erlaubt OEMs ein Override dieses Opt-outs.
Erkennbare Teilfenster-/Größenabweichungen bleiben gesperrt. Ein hypothetisches
OEM-Override mit identischer Voll-Display-Geometrie ist durch diese öffentlichen
APIs allein nicht separat nachweisbar; Samsung-Abnahme bleibt erforderlich.
Bei unklarer Quelle den exakten Window-Fallback nutzen. Ein normales
Handy benötigt ein tatsächlich sichtbares Fremd-App-Fenster; verborgenes beliebiges
App-Rendering innerhalb Continuum wird nicht versprochen. Screenshot-Freigaben und
FLAG_SECURE werden respektiert.

## Prüfen und Rücksetzen

Host: serialisiertes Gradle unter gemeinsamem `agent-gradle.lock`.
Fokus: LiveComputerFramesTest, LiveComputerActionsTest, LiveWindowSafetyTest,
LiveSearchClickTest, VerifiedNodeClickTest, ProjectionCropGeometryTest,
AndroidLiveNodePortTest sowie native Window/Observation-UI-Tests.
Nächster Gerätefall: Fold7 Split-Screen, WhatsApp öffnen, Suchfeld fokussieren,
Anna suchen, Stop / Accessibility-Widerruf / Rotation / fremdes Overlay.
Rückfall: Capture nicht starten bzw. stoppen; vorhandene Cockpit-/Toolpfade bleiben.

Offizielle API: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshotOfWindow(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)

MediaProjection: https://developer.android.com/media/grow/media-projection
OEM-Override: https://developer.android.com/guide/practices/device-compatibility-mode
GPU-Fence: https://developer.android.com/reference/android/graphics/HardwareBufferRenderer.RenderResult
