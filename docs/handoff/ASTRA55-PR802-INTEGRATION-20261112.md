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
Startverweigerung, Ressourcenwechsel und Entzug werden im getrennten Folgecommit
an derselben vorhandenen Service-Kette geprüft und abgesichert.

PORTIERT: genannte Live-/Workflow-Reparaturen. VERIFIZIERT: die 36 JVM-Prüfungen.
GERÄTEBESTÄTIGT: nein. Der Integrator übernimmt die getrennten Commits und prüft
anschließend den gemeinsamen Android-Stand. Rücknahme jeweils durch Rücknahme des
zusammengehörigen Folgecommits; Main, Paket, Signatur und Originalassets bleiben erhalten.
