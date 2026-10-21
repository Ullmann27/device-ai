# Device AI Continuum – SWB1/CDX1: überprüfbare Zwei-Kerne-Kopplung
Stand: 21.10.2026; isolierter Entwicklungszweig, kein Merge nach main.

## Tatsächliche Kerne
SWB1 ist ein eigener trainierbarer Subword-Encoder mit 4.113 Parametern, eigenständigen Gewichtssnapshots und Holdout-Gates. CDX1 ist ein getrennter, evidenz-konditionierter Byte-Decoder mit 7.746 Parametern, eigenem Paararchiv, eigener Forschungsausgabe und eigenem Checkpoint. Er ist kein produktives allgemeines Chat-Sprachmodell. CDX1 verwendet 16 SWB1-Merkmale je Beleg/Frage als Konditionierung.

## Konsistenzfehler und Reparatur
Bislang konnte SWB1 nach dem CDX1-Training weitere Updates erhalten; die CDX1-Testgenerierung nutzte dann veränderte SWB1-Merkmale. Das ist ein nicht versionierter Trainings-/Inferenz-Drift.

Neu angenommene CDX1-Paartrainingsrunden schreiben neben dem unveränderten CDX1-Checkpoint ein checksummengesichertes Binding namens context-decoder-paired.swb1-binding. Es enthält den SHA-256-Digest des zugehörigen CDX1-Checkpoints und die exakt zur Konditionierung verwendeten SWB1-Snapshotbytes. Spätere CDX1-Lern- und Testläufe verwenden dieselbe validierte SWB1-Fassung. SWB1 selbst kann parallel zu anderen Zeiten weitertrainiert werden, ohne CDX1 unbemerkt neu zu konditionieren. Eine absichtliche gemeinsame Versionmigration benötigt eigenständige Tests und wird nicht improvisiert.

Das Binding wird mit temporärer Datei, fsync und atomarer Ersetzung gespeichert. Bei beschädigtem Binding, geändertem CDX1-Hash oder ungültigem SWB1-Snapshot bleiben CDX1-Generierung und weitere Paartrainingsversuche gesperrt, statt mit falschen Parametern fortzufahren. Bei Crash zwischen Decoder-Checkpoint und Binding ist die Verknüpfung nicht bestätigbar. Kein Rückschreiben nach main, keine Änderung am Produktivchat, kein Löschen privater Lerndaten.

## Kompatibilität und Anzeige
Alt-Checkpoints ohne Binding bleiben als LEGACY_UNBOUND experimentell nutzbar, werden aber explizit als nicht rückwirkend verifiziert markiert. Nach einer weiteren akzeptierten Trainingsrunde erhalten sie eine neue Bindung ab diesem Stand. PINNED_CURRENT bedeutet gleicher SWB1-Stand wie jetzt aktiv. PINNED_PREVIOUS bedeutet bestätigte ältere Fassung, während der aktive SWB1 weiterlernte. CORRUPT bedeutet gesperrte CDX1-Ausgabe. Die Lernansicht zeigt diese Zustände auf Deutsch.

## Tests und ausstehende Abnahme
Die neuen Kotlin-JVM-Regressionen prüfen CDX1-Inferenzkonstanz vor und nach geänderten SWB1-Gewichten, fortgesetztes separates Training, schadhaftes oder manipuliertes Binding, Nichtänderung vorhandener Decoder-Gewichte bei Ablehnung und altes Binding-freies Verhalten.

Das ist noch kein Nachweis für freie Sprache, Generalisierung auf unbekannte Alltagsfragen oder einen positiven Holdout-Lerngewinn. Vor Freigabe braucht der exakte neue Commit eine vollständige Gradle-/JUnit-/Lint-/APK-Prüfung und danach Samsung-Android-16-/Termux-Laufzeittests. Keine automatischen Installationen und keine externe kostenpflichtige KI-API.
