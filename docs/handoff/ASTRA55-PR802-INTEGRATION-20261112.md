# Code55: Übernahme der Supportprüfung #802

Integrationsbasis: `a99eac98cf`. Geprüfte Zulieferung:
`28aa599bb0e6c77ad8b594d7717003d243a312e8`, PR #802.
Ziel bleibt PR #799; Integrator Astra. Bestehende EXP-05/06/11/12.

## Abgleich aller drei Reparaturdateien

- Live: Fenster-ID-Vergleich und erschöpfungssichere Unsicherheitsmenge fehlten.
  Die beiden kleinen Reparaturen wurden unverändert auf die aktuelle Quelle übernommen.
- Workflow: Alle drei Revisions-/Schrittprüfungen fehlten. Der Originaldiff wurde
  übernommen und die neun Gegenproben in die regulären Tests gestellt.
- Szene: Die breite Mindesthöhe bei großer Schrift ist an den zuständigen
  3D-/Layoutagenten übergeben; hier wurden keine Renderingdateien verändert.

Der zusätzliche Bridge-Befund wurde behoben: Bewegungen verwenden eine eindeutig
codierte Identität aus Aufgabe, Sitzung und Schritt. Ein Folgeauftrag mit erneutem
Schritt `computer-1` verliert seinen Impuls dadurch nicht.

Eine weitere Gegenprobe zeigte die Grenze des kleinen Workflowdiffs: Eine verspätete
Beobachtung von A konnte noch den Cache des Adapters überschreiben, nachdem B bereits
beobachtet war. Der Adapter nimmt jetzt nur monotone Beobachtungen seines laufenden
Schritts mit passender Aufgabe und Epoche an. Fremde alte Ergebnisse pausieren den
Nachfolger nicht. Es wurde kein weiterer Ereignisbus angelegt.

## Gezielte Prüfung dieses ersten Folgecommits

36/36 Kotlin/JVM-Tests bestanden: LiveSupportBoundaryTest, WorkflowReviewTest,
CockpitWorkEventTest, LiveComputerActionsTest, VerifiedNodeClickTest.
Die geänderten vollständigen Produktionsquellen wurden mit Kotlin 2.4.0 neu
übersetzt, unveränderte Abhängigkeiten stammen aus dem tatsächlichen Integrationsbuild.
Compiler maximal 384 MiB, Test-JVM 256 MiB, jeweils ein Prozessor. Kein Gradle-Job,
keine Ersatzlogik, keine Android-/Fold7-Bestätigung.

## Scrollwirkung: angeschlossen und gezielt geprüft

- Der vorhandene Dienst hält für genau eine laufende Aktion die tatsächliche
  Accessibility-Knotenidentität. Aufgabe, Schritt, Fenster, Epoche, Capture-Sitzung,
  Ausgangsrevision und Versandzeit sind an die Beobachtung gebunden.
- Nur `TYPE_VIEW_SCROLLED` derselben nativen Quelle nach dem Versand kann eine
  Bewegung liefern. Der Nachher-Baum muss dieselbe Quelle eindeutig enthalten.
  Andere Badges, andere Container und bloße Revisionssprünge bestätigen nichts.
- Ab API 30 zählen echte Deltawerte. Bei fehlenden Deltas sowie unter API 28/29
  belegt erst eine Folge gültiger Offset-/Indexwerte die Bewegung. Unbekannte Werte,
  unveränderte Positionen und reine Änderungen der Elementanzahl zählen nicht.
- `TYPE_VIEW_SCROLLED` und `TYPE_VIEW_TEXT_CHANGED` sind im vorhandenen Dienst und
  seiner Ereignisanmeldung angeschlossen. Ende, Abbruch, neue Fenster oder Entzug
  lösen die temporäre Knotenreferenz wieder. Es entsteht keine zweite Warteschlange.
- Native Freigabe und LIVE-Prüfung unmittelbar vor Eingabe bleiben erhalten.

20/20 JVM-Prüfungen mit frisch übersetzten Produktionsquellen bestanden:
LiveScrollEvidenceTest, LiveComputerActionsTest und LiveSupportBoundaryTest.
49/49 direkte Robolectric-Prüfungen mit echten Dienst-/Portquellen bestanden:
AndroidLiveNodePortTest, AndroidLiveScrollTest, ScreenObservationRevisionTest und
NativeClickCandidateTest. Lauf ohne Gradle, Frameworkknoten synthetisch, kein Gerät.
Neue positive Gegenprobe: derselbe Container bewegt sich, genau ein nativer Scroll.
Neue negative Gegenproben: Badge, anderer Container, anderes Fenster, alte Meldung,
unveränderte Position und widerrufene Freigabe.

Android-Quellen: [AccessibilityRecord](https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord),
[AOSP UiAutomation](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/UiAutomation.java),
[AndroidX-Korrektur für gepoolte API-28/29-Deltas](https://android.googlesource.com/superprojects/androidx/+/eafc2e9724579e0700a67ff4a4b06565972e8be2).

## Fenster-Screenshot: Identität, Aufnahmezeit und Farbe

- Der Auftrag bindet Capture-Sitzung, Aufgabe, Fenster und Ausgangsrevision.
  Rückruf sowie asynchrones Konvertierungsergebnis müssen dieselbe Beobachtung
  noch vorfinden. Eine inzwischen neue Revision darf alte Pixel nicht umetikettieren.
- `ScreenshotResult.timestamp` bleibt der tatsächliche Aufnahmezeitpunkt.
  Das gemessene Alter beim Empfang/Kopieren wird im vorhandenen Frame-Holder zur
  weiteren Alterung addiert. Verspätete Bilder erhalten keine neue LIVE-Frist.
- Der gemeinsame GPU-Kopierer übernimmt zusätzlich Screenshot-HardwareBuffer.
  P3 bleibt zunächst als P3 gekennzeichnet und wird per HardwareBufferRenderer
  wirklich nach sRGB gerendert. Direkte sRGB-Screenshots benötigen diese Kopie nicht.
  Unbekannte Eingangsprofile werden verworfen, nicht als sRGB bezeichnet.
- ImageReader fordert RGBA_8888 und sRGB explizit an. Ein Producer darf diesen
  Default überschreiben; deshalb wird weiterhin der tatsächliche Bildfarbraum
  geprüft. Die bestehende Obergrenze einer laufenden GPU-Kopie bleibt erhalten.

26/26 JVM-Prüfungen bestanden: LiveWindowCaptureTest, LiveComputerFramesTest,
LiveComputerDispatchGuardTest. 7/7 direkte Robolectric-Prüfungen bestanden:
AndroidCaptureColorTest und AndroidWindowCaptureTest. Die native Gegenprobe ruft
den tatsächlichen Screenshot-Callback auf: ursprüngliche Revision/Zeit bleiben,
neue Revision wird verworfen, alte Sitzung kann die neue nicht pausieren und Stop
gibt den verspäteten Buffer frei. Vollständige geänderte Android-Quellen wurden
dabei gegen SDK 36 frisch übersetzt. Eine reale P3-GPU-/Fold7-Abnahme steht aus.

Referenzen: [ScreenshotResult](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.ScreenshotResult),
[ImageReader.Builder](https://developer.android.com/reference/android/media/ImageReader.Builder).

## Anschließende Service-Regressionsprüfung

Der neue Besitzerauftrag verlangt zusätzlich die Prüfung des Foreground-Service-
Absturzes. Der konkrete Geräte-Stacktrace liegt diesem Agenten nicht vor.
Der aktualisierte Supportbaum `30e9b246c961851220aab31821f89c16367e4967`
(GitHub-Baum von `c1d41bcdce73b42cbce3b77920ecd86360b379fe`) enthält inzwischen
einen konkreten FGS-Fix. Dieser vorhandene Service-Diff wurde übernommen:
Foreground-Promotion vor Prüfung eines inzwischen veralteten Starts;
Stop-PendingIntent schon mit der angeforderten Capture-Sitzung;
Aufräumen und `stopSelf(startId)` bei verweigerter Promotion.
Die sechs originalen ContinuumLiveProjectionServiceUiTest-Fälle wurden unverändert
übernommen und auf den aktuellen vollständigen Capture-/Servicequellen erneut
ausgeführt: **6/6 PASS**. Android-Systemprozess und Fold7 bleiben ungeprüft.

Der fehlende Zweizeiler in `AgentActivity.newSession` wurde dem Hauptintegrator
zur konfliktfreien Übernahme gemeldet: vorgemerkten Computerstart verwerfen,
bisherigen Controller vor dem neuen Chat stoppen. Dieser Agent ändert Activity nicht.

### Ergänzende konkrete Service-Kanten

- `startForegroundService` muss einen Komponentenstart bestätigen; null und
  Ausnahme enden die zugehörige Capture-Sitzung und melden keinen laufenden Start.
- Ein Stop-Intent einer alten Capture-Sitzung beendet keine neuere Service-Sitzung.
  Der passende Stop räumt Aufnahme und Foreground-Status sofort auf.
- Fehler beim Surface-Wechsel werden innerhalb der Resize-/Konfigurationscallbacks
  behandelt. Alter und neuer Reader werden auch bei einem fehlgeschlagenen Wechsel
  freigegeben; eine laufende GPU-Kopie behält ihre bestehende geschützte Lebensdauer.
- Ein einzelner Fehler bei `projection.stop`, Listenerentfernung oder Displayfreigabe
  überspringt die übrige Freigabe und `stopForeground` nicht mehr. Aufräumen bleibt
  wiederholbar. Ein abgewiesener GPU-Auftrag gibt sein Image frei und beendet Capture.

Die sechs ursprünglichen Tests plus sechs gezielte Ergänzungen bestehen: **12/12**.
Rotkontrolle mit denselben Tests und Service/Controller aus `6785ed6762`:
sechs Fehlermeldungen in fünf Testmethoden, einschließlich einer zusätzlichen
Cleanup-Ausnahme des Resize-Falls. Die originalen sechs FGS-Prüfungen bleiben grün.
Das belegt die ergänzenden Kanten ohne Behauptung eines historischen Geräte-Logs.
Quellhashes und Rohresultate: `docs/evidence/astra55-pr802-integration-20261112/fgs/`.

Scope-Freeze nach diesem Service-Folgecommit. Nur noch echte Integrationsregressionen;
Gesamtkompilation, Gesamtsuite und APK liegen beim Hauptintegrator.

PORTIERT: die genannten Live-/Workflow-/Capture-/Service-Reparaturen.
VERIFIZIERT: die oben einzeln angegebenen, teilweise überlappenden Prüfläufe.
GERÄTEBESTÄTIGT: nein. Der Integrator übernimmt die getrennten Commits und prüft
anschließend den gemeinsamen Android-Stand. Rücknahme jeweils durch Rücknahme des
zusammengehörigen Folgecommits; Main, Paket, Signatur und Originalassets bleiben erhalten.
