# Android und Cockpit: technische Abschlussprüfung

## Ergebnis

**Der geprüfte Stand enthält ein echtes, appgebundenes Live-Cockpit. Ein vollständiger Android-Homebildschirm mit beliebiger selbstständiger Appbedienung ist noch nicht angeschlossen.** Die erteilte Aufnahmefreigabe allein erweitert den implementierten Funktionsumfang nicht.

Die drei vorhandenen Agenten prüften Aufnahme und Lebenszyklus, Monitor und Renderübergabe sowie Befehle und Android-Eingaben. Der Integrator verband ihre Korrekturen, prüfte die Activity-Anschlüsse und führte den gemeinsamen Kotlin-/Android-Hostlauf aus. Das zuvor akzeptierte Design und die Modellassets wurden nicht verändert.

Entwicklungsnachweis: [PR #803](https://github.com/Ullmann27/device-ai/pull/803) für den gemeinsamen Arbeitszweig von [PR #799](https://github.com/Ullmann27/device-ai/pull/799). Status: **PORTIERT; auf dem Host VERIFIZIERT; noch nicht GERÄTEBESTÄTIGT.**

Ausgangspunkt ist der öffentliche PR-799-Commit `5f87bdc610944b93d3173a83c5beeea315abeb66`. Sein Tree `cae47c3abaee1a378b4157f26abf7e6ec33df2c1` entspricht dem lokalen Ausgangsstand `9918891a6da1d74275b087a04f4aa86c79074d46`. Der gemeinsam geprüfte Produktstand ist `b7d4cbdda44a10ea94107e47eb0ef76f71e857a1`; danach ergänzte Dateien sind Prüfbelege und Dokumentation.

## Was tatsächlich verbunden ist

| Strecke | Geprüfter Umfang |
|---|---|
| Auftrag → App | Text und bewusst aktivierter Sprach-Gesprächsmodus erreichen den Computerparser. Die installierte Ziel-App wird ermittelt, bestätigt und über einen echten Launcher-Intent geöffnet. Ein neues Zielfenster wird nachgeprüft. |
| Android → Bild | Accessibility-Fensteraufnahme oder MediaProjection liefern HardwareBuffer. Projection fordert das Standarddisplay an und veröffentlicht anschließend nur den überprüften Ausschnitt der gebundenen Ziel-App. |
| Bild → 3D-Monitor | GPU-Kopie, Framehalter und Filament-Stream speisen die vorhandene Monitorfläche. Das Seitenverhältnis bleibt erhalten; Auflösung und Farbraum können angepasst werden. Es handelt sich um echte App-Inhalte, nicht um ein gerendertes Beispielbild. |
| Anzeige → Eingabebereitschaft | Ein frischer Capture allein genügt nicht mehr. Ein passender Frame muss erfolgreich an `render` und `endFrame` übergeben worden sein. Die erste Beobachtung wartet auf diesen Zustand. |
| Befehl → Android-Eingabe | Der Port führt echte Accessibility-Aktionen für Klick, Scrollen und Texteingabe aus. Vor der Aktion werden Sitzung, App, Fenster, Knoten, Freigabe und der tatsächlich eingereichte Monitorausschnitt geprüft. |
| Eingabe → Ergebnis | Neue Beobachtung und konkrete Nachbedingung müssen die Wirkung bestätigen. Texte werden mit dem angeforderten Inhalt verglichen; Scrollen benötigt ein passendes natives Bewegungsereignis. Unklare Aktionen werden nicht blind wiederholt. |
| Ende/Unterbrechung | Stop, Freigabeentzug, verdeckte Quelle und verlorene Darstellung entwerten die zugehörige Bereitschaft. Die vorhandenen Task- und Android-Freigaben wurden nicht umgangen. |

Die Verbindung läuft über `AgentActivity`, `OwnerComputerSessionController`, `AndroidLiveComputer`, `AndroidLiveNodePort`, `LiveComputerFrames`, `LiveMonitorSubmission`, `CockpitLiveTexture` und `FilamentAvatarRenderer`. Ausführliche Quellstellen stehen in den unten verlinkten Teilprüfungen.

## Vier konkrete Anschlussfehler behoben

1. **Frischer Capture ohne dargestellten Monitor:** Zuvor durfte eine Eingabe stattfinden, obwohl der Monitor den Frame noch nie importiert oder gerendert hatte. Jetzt verlangt die Eingabekette eine frische, passende Renderübergabe. Import allein und der Status „Live“ reichen nicht aus. Ein regulär übersprungener Renderframe erzeugt keine neue Bestätigung; ein zuvor gezeichnetes, weiterhin frisches Bild darf bestehen bleiben.
2. **Eingabe außerhalb des gezeigten Ausschnitts:** Eine schwebende Tastatur konnte mehrere sichtbare App-Bereiche hinterlassen. Der Monitor zeigte nur den größten Bereich, während ein anderer sichtbarer Bereich bedienbar blieb. Jetzt muss das vollständige Zielrechteck unmittelbar vor `performAction` innerhalb des eingereichten Monitorausschnitts liegen. Ein sichtbares Zentrum allein genügt nicht.
3. **Verlorenes Bildalter während der GPU-Zustellung:** Die seit `acquireLatestImage()` tatsächlich verstrichene lokale Zustellzeit wird in das Framealter übernommen. Vorhandenes Alter wird nicht verkürzt; zu alte oder ungültige Zustellungen werden zurückgewiesen. Wiederholtes Rendern macht alte Pixel nicht wieder frisch.
4. **Falsche Erwartung nach englischer Kontaktsuche:** Aus `Search...` wurde zuvor fälschlich ein deutsches Nachrichtenfeld abgeleitet. Der Ablauf prüft nun das tatsächlich beobachtete eindeutige deutsche oder englische Nachrichtenfeld. Empfänger- und Entwurfsprüfungen bleiben bestehen.

Zusätzlich wurde eine irreführende Rückfrage bei vorhandenem Entwurf korrigiert: Es wird zur Prüfung direkt in der App und anschließendem Neustart aufgefordert. Eine kurze Antwort konnte diesen Ablauf bislang nicht fortsetzen.

## Grenzen des aktuellen Funktionsumfangs

| Wunsch | Tatsächlicher Stand |
|---|---|
| Vollständiger Homebildschirm im Cockpit | Kein produktiv angeschlossener Home-/Zurück-/Recents-Weg und kein eigenständiger virtueller Android-Desktop. |
| Cockpit und Ziel-App gleichzeitig | Der aktuelle Controller ist für eine sichtbare Ziel-App und ein sichtbares Cockpit ausgelegt. Die Mehrfensteransicht wird nicht automatisch angeordnet. Beim Verbergen im Vollbild wird die aktive Bedienung beendet. |
| Beliebige Appwechsel innerhalb eines Auftrags | Ein Auftrag bleibt an ein Zielpaket gebunden. Der generische Appstart kann weitere installierte Apps öffnen, übernimmt diese aber nicht automatisch in einen freien Computer-Arbeitsplan. |
| Frei formulierte Arbeitsschritte | Der Computerparser kennt WhatsApp, Facebook, Dateien, Word und Excel sowie feste Ziele: öffnen, suchen, Downloads öffnen und WhatsApp-Entwurf vorbereiten. |
| Word-/Excel-Bearbeitung | Öffnen und gemeinsames Suchrezept vorhanden; freie Dokumentbearbeitung oder Tabellen-/Formelbearbeitung ist in dieser Bedienkette nicht implementiert. |
| Nachrichten selbst versenden | Der Ablauf bereitet und prüft den Entwurf. Ein registrierter, geprüfter Versandweg fehlt weiterhin. |
| Allgemeines Schlussfolgern über Bildschirmbilder | Die Computersteuerung verwendet Accessibility-Knoten und feste Abläufe. Ein frei planender visueller Agent ist hier nicht angeschlossen; die separate Gesprächsfunktion ist davon zu unterscheiden. |
| Manuelle Fernbedienung durch Antippen des 3D-Monitors | Kein Touch-Weiterleitungspfad. Die geprüften Eingaben werden durch den KI-Auftragsablauf ausgelöst. |

## Tatsächlich ausgeführte gemeinsame Prüfung

| Prüfschritt | Ergebnis |
|---|---|
| Frische, gezielte Kotlin-Kompilation | **52 Produktionsdateien erfolgreich kompiliert**, einschließlich Activity, Controller, Capture, Port und Renderer. |
| Gemeinsame reine JVM-Verträge | **98/98 bestanden**: App-Ablauf, App-Auftrag, Aufnahmezustimmung, Computerparser, Taskfreigabe, Monitor-Guard, Workflow und verifizierter Appstart. |
| Gemeinsame Android-Hostprüfungen | **42/42 bestanden**: nativer NodePort, Scrollen, Projection-Service und Monitor-Lebenszyklus; Robolectric SDK 28/35. |
| Quellenbindung | Alle für den Lauf erfassten Produktions- und Testquellen blieben unverändert. Die Hashes stehen im gemeinsamen Prüfbeleg. |

Damit sind **140 Tests im gemeinsamen Schlusslauf** bestanden. Die zusätzlichen Teilprüfungen und roten Gegenproben sind separat dokumentiert; ihre teilweise überlappenden Testzahlen werden nicht zu dieser Zahl addiert.

Die Kotlin-Kompilation verwendet unveränderte Klassen aus einem eingefrorenen Integrations-JAR für übrige Abhängigkeiten. Sie ist kein vollständiger Gradle-/APK-Build. Die Android-Hosttests verwenden kontrollierte Fenster-, Knoten- und Freigabedaten; sie beweisen keine Bedienung einer real installierten Fremd-App auf einem Fold7.

Die Renderbestätigung bedeutet erfolgreiche API-Einreichung. GPU-Abschluss, Android-Komposition und physische Anzeige müssen auf dem Gerät geprüft werden. Auch das Bildalter vor `acquireLatestImage()` ist nicht allgemein bestimmt: Die öffentliche Android-Dokumentation legt die Zeitbasis von `Image.timestamp` abhängig vom Erzeuger fest. Es wurde keine unbelegte Uhrenumrechnung eingebaut.

## Belege und Quellen

- [Gemeinsamer Prüfbeleg](../evidence/android-display-integration-20261112/validation.json), [JVM-Ergebnis](../evidence/android-display-integration-20261112/pure-tests.txt), [Android-Hostergebnis](../evidence/android-display-integration-20261112/native-tests.txt).
- [Befehls- und Eingabepfad](ASTRA55-COMPUTER-EXECUTION-AUDIT-20261112.md), einschließlich ursprünglicher Composer-Gegenprobe.
- [Captureprüfung](../research/omega/ASTRA55-ANDROID-CAPTURE-AUDIT-20261112.md), einschließlich Zustellalter und Freigabeentzug.
- [Monitorprüfung](../evidence/cockpit-live-monitor-audit-20261112/README.md), einschließlich Submission, Frische und Frame-Skipping.
- [Zielausschnittprüfung](ASTRA55-RENDERED-VIEWPORT-CHECK-20261112.md), einschließlich echter negativer Gegenprobe und erfolgreicher Texteingabe im zulässigen Ausschnitt.
- Offizielle Android-Quellen: [MediaProjection](https://developer.android.com/media/grow/media-projection), [DefaultDisplay-Konfiguration](https://developer.android.com/reference/android/media/projection/MediaProjectionConfig), [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [Image-Zeitstempel](https://developer.android.com/reference/android/media/Image).
- Offizielle Filament-Quelle: [Renderer und Frame-Pacing](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/Renderer.java).

Der nächste sachliche Gerätenachweis ist ein freigegebener Mehrfensterlauf mit sichtbarer Ziel-App und Cockpit: denselben Inhalt zeigen, Suche und Entwurf ausführen, die tatsächliche Wirkung auf beiden Ansichten vergleichen und anschließend Capture-/Darstellungsverlust prüfen. Der vollständige Home-/Appwechselpfad ist eine eigene noch fehlende Implementierung.
