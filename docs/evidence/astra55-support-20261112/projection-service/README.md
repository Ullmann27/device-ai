# Projection-Service · Startregression

Rootfix: `d97fb979ff16d74f51f977812174f1a2080cb6c2`.
Unverändert lokal übernommen als `acc9e8b79da6869555989953d77dbd32c6bac971`.
Geprüfter Test-/Quellcommit: `16eef753e73dc8c15a9784b016459f0221234eef`.

## Aktueller Root-Abgleich

Nach der Live-Reparatur `97bb5767c7` wurde derselbe unveränderte Sechser-Test gegen
Root `b2f051900427aa6671e87fa1a41089229591d760` erneut ausgeführt: **Compile PASS, 6/6 PASS**.
Zusätzlich zur ursprünglichen Quellliste kompiliert der Runner jetzt exakt `cognitive/LiveComputerActions.kt`
und `AndroidLiveNodePort.kt`. Damit stammen die neue `LiveScrollEvidence` und der geänderte Node-Port
aus den eingefrorenen Rootquellen. Sechs Quell-/Testdateien stimmen vor/nach dem Lauf mit diesem Commit überein.
Während des Laufes blieben Root-HEAD und alle sechs geprüften Dateien unverändert.
Die spätere Bindung gegen Root `47d275c6d52eebf52a2d5983656a6e128b7dd708` bestätigt dieselben sechs Dateihashes;
dieser Folgecommit änderte ausschließlich Dokumentation und andere Belege. Keine Assertion oder Produktfunktion geändert.

Belege: `root-b2f0519/`; `source-binding.json` bindet dort auch die frisch erzeugten Klassen
für LiveScrollEvidence, Node-Port, ObservationBus und Service. Alte Grün-/Rotbelege bleiben unverändert.
Dies bestätigt die konkrete Service-Verbindung zum neuen ObservationBus unter denselben sechs Servicefällen;
die vollständige Scroll-Laufzeitprüfung ist nicht Gegenstand dieses Laufes. Die unten genannten Host-/Gerätegrenzen gelten weiter.

## Ergebnis

| Servicequelle | Compile | Robolectric 35 |
|---|---|---|
| Vor dem Fix, `9c79a2ae5e20930c55976abe8d0391d8c0232570` | PASS | 6 FAIL / 6 |
| Mit unverändertem Rootfix | PASS | 6 PASS / 6 |

Fünf Ausgangsfehler: `stopSelf()` ohne vorheriges `startForeground()`.
Sechster Ausgangsfehler: Promotion-Exception endet mit `stopSelf()` statt `stopSelf(startId)`.
Keine zusätzliche Produktionsänderung für diese geprüften Abläufe erforderlich.

## Prüfumfang

`ContinuumLiveProjectionServiceUiTest` prüft den echten `onStartCommand`:

- Veraltete Sitzungsnummer und null Intent: Promotion vor Stop, kein Aufruf von `getMediaProjection`.
- Abbruch zwischen `startProjectionCapture` und Zustellung des Start-Intents: gleicher sicherer Ablauf.
- Ersetzte Capture-Sitzung: alter Start wird abgebrochen; die neue logische Capture-Bindung bleibt bestehen.
- Entzogener Observation-Zugang vor Zustellung: Abbruch nach Promotion, keine Projection-Erzeugung.
- Synthetisch abgewiesene Promotion: Capture-Bindung endet, `stopForeground(REMOVE)` und `stopSelf(startId)` folgen.

Jeder normale Abbruch prüft außerdem die echte erzeugte Notification-Aktion:
Service-PendingIntent, immutable, explizite Service-Komponente, Stop-Action und genaue angeforderte Capture-Sitzung.
Damit wird die Sitzung vor ihrer Zuweisung an das Servicefeld korrekt im Stop-Intent gebunden.

Die wartenden Starts entstehen durch den vorhandenen `AndroidLiveComputer.startProjectionCapture`.
Der Test entnimmt dessen tatsächliches Start-Intent aus Robolectric und liefert es nach Abbruch/Sessionersatz an den Service.
Es gibt keine nachgebaute Sessionverwaltung.

## Umgebung und Quellbindung

Direkter Kotlin-2.4.0-Compiler, JUnit 4.13.2, Robolectric 4.14.1, instrumentiertes Android 15 / SDK 35.
512 MB Heap, ein aktiver Prozessor, kein Gradle und kein Download.
Zusätzlich neu kompiliert: Service, `AndroidLiveComputer`, `ContinuumScreenObservationService` einschließlich
`ScreenObservationBus` und die Testklasse. Alle vier Dateien werden vor dem Lauf eingefroren.

Vorhandene Appkompilate und R-Klassen dienen ausschließlich als Umgebung/Friend-Modul.
Sie werden ebenfalls eingefroren und über Verzeichnishashes gebunden; ihr ursprünglicher vollständiger
Quellstand wird hier nicht als neu gebaut oder verifiziert ausgegeben.
Neu kompilierte Klassen stehen zuerst im Test-Classpath.
`source-binding.json` gleicht alle acht Quellverwendungen beider Läufe gegen ihre Git-Commits ab: vollständig identisch.
Die jeweiligen Manifeste enthalten Vor-/Nach-Hashes und die verwendeten Artefakthashes.

## Reproduktion

`python docs/evidence/astra55-support-20261112/projection-service/run.py --help`

Erforderliche Argumente: `--repo`, `--cache` (Gradle `modules-2/files-2.1`), `--app-build`
(bestehendes App-Buildverzeichnis), `--android-jar` (nicht instrumentierte Compile-API), `--out`.
Für die Rotkontrolle zusätzlich `--service-ref 9c79a2ae5e20930c55976abe8d0391d8c0232570`.
Der Runner nutzt nur vorhandene Abhängigkeiten und verlangt einen neuen Ausgabeordner.
Sein Java-Tempverzeichnis liegt innerhalb des Ausgabeordners; ausreichend freien Plattenplatz wählen.
Ein fehlgeschlagener Compiler-/JUnit-Lauf liefert einen Fehler-Exitcode.

## Grenzen und Einrichtungsbefunde

Die OS-Ebene ist durch Robolectric-Shadows ersetzt. Die Prüfungen belegen Aufrufreihenfolge,
Sessionprüfungen, Notification-Bindung und Cleanup des echten Appcodes.
Sie bilden den Android-Systemprozess und dessen `fgRequired`-/Crash-Nachrichten nicht nach.
Der Exceptionfall belegt einen abgefangenen Fehler samt Cleanup, **keine erfolgreiche Promotion**
und keine Gerätegarantie für abgewiesene FGS-Berechtigungen.
Der Sessionersatztest belegt die logische Bindung, nicht die Zustellreihenfolge des echten ActivityManager.

Zwei frühere Einrichtungshindernisse bleiben dokumentiert: ein instrumentiertes SDK-JAR als Compile-API
erzeugte fehlende Robolectric-Synthetikmethoden; danach scheiterte das Entpacken der nativen Runtime
am vollen gemeinsamen `/tmp`. Nicht instrumentierte Compile-API und eigenes Scratch-Tempverzeichnis
behoben diese Umgebungsfehler. Keine Appassertion wurde dafür abgeschwächt.

Bestehender Workflow-Nachweis 72/72 unverändert. Gradle/GPU/APK: NOT_RUN. Fold7: NOT_RUN.
LOCAL_ONLY / NOT_PUSHED. Registerbezug: EXP-07; vorhandene Negativtests, kein neues Feature.
Integrator: Hauptagent der gemeinsamen Astra55-Linie. Integration: Test-/Evidence-Commits übernehmen;
den schon vorhandenen Rootfix nicht erneut anwenden. Nächste Abnahme: gebündelter Android-Hostlauf und Fold7.
Rollback dieses Folgeauftrags: nur neue Testklasse und diesen Evidence-Ordner entfernen; keine Datenmigration.
