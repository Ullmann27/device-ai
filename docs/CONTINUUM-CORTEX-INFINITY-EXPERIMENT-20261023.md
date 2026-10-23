# EXP-CORTEX-INFINITY – überprüfter Forschungsstand, 23.10.2026

## Verbindliche Einordnung

Dieses Paket entstand nach dem Architekturvorschlag von Argon 4, wurde aber an die tatsächlich vorhandenen Continuum-Module angepasst. Es ist **keine** produktive Android-Chat-Engine, kein garantierter Wahrheitsbeweis, keine auf dem Fold7 nachgewiesene Funktion und keine automatische Selbstverbesserung.

Quelle: PR #94, Commit 0499e2a0. Ergänzungen und unabhängige Sicherheitskorrekturen im Draft-PR #95. Main und Besitzergewichte bleiben unangetastet.

## Implementierte Forschungsbausteine

1. EphemeralFastWeightCore – 16×16 RAM-Gewichte, echter analytischer Gradient für L = ½‖W·x_prev – x_now‖² + L2-Regularisierung. Die ursprüngliche Argon-Formel W·x_prev + x_now erzeugte aus Nullinitialisierung einen verschwindenden Fehler; diese Fassung lernt messbar. Eingaben sind endlich und normalisiert, Gewichtsnorm begrenzt auf 2,5, Sitzung rücksetzbar. Dies beweist numerische Anpassung, **nicht** semantisches Verständnis oder selbständige Pronomenauflösung. Der explizite Gesprächsfokus kommt von einem typisierten Eingabefeld; Pronomen nutzen nur diesen Fokus.

2. EvidenceBoundedThoughtSearch – typisierte, endliche Mehr-Hop-Suche mit maximal drei Kanten, nachvollziehbarer Kettenherkunft und Enthaltung bei widersprüchlichen Pfaden. Drei deklarierte Relationstypen: LOCATED_IN, CITY und NOMINAL_GRID_FREQUENCY. Der Hash bindet an übergebene Bytes; das Bool-Flag ownerReviewed wird **nicht** kryptografisch authentisiert, und keine externe Faktengültigkeit wird behauptet. Der Algorithmus führt eine symbolische Graphsuche aus, kein trainiertes, semantisch validiertes Latent Reasoning.

3. WakeSleepShadowHabitCore – ausschließlich RAM-Habit-Cache, keine produktive Gewichtsspeicherung oder OS-Worker. Nur mehrstufige Traces können vorgeschlagen werden. Übernahme erfordert **für jedes** vorgeschlagene Habit einen eigenständigen positiven Probe-Fall, mindestens einen negativen Fall, eindeutige Abfragen und unveränderte Resultate gegenüber dem frischen Planner. Leere, doppelte, unvollständige oder widersprüchliche Holdouts bewirken Fail-Closed. Keine automatische Generierung/Validierung echter kontrafaktischer Trainingsbeispiele. Der Cache ist nicht dauerhaft und wird bei neuer Instanz verworfen.

4. ContinuumCortexResearchPipeline – typisierte, von der Anwendung ausdrücklich aufgerufene WAKE-/SHADOW-Verbindung. Die aufrufende Schicht liefert einen endlichen 16-D-Vektor, eine geprüfte Wissensgraphstruktur, ein explizites Subjekt und einen passenden Relationstyp. Die Pipeline gibt Status, betroffene Quell-IDs, Anzahl Kanten, Norm und sorgfältig als nicht fachlich zertifiziert gekennzeichneten Antworttext zurück. Sie startet weder CCC1 noch das Sprachmodell und überschreibt nicht den Hauptchat. Ohne Bezug oder bei fehlendem Beleg: Enthaltung.

## Lokal ausgeführte Validierung

Im isolierten Kotlin/JVM-Verzeichnis kompiliert mit kotlinc-jvm 1.9.0 / JDK 21:
- \`ContinuumCortexShadow.kt\`
- \`ContinuumCortexShadowChecks.kt\`
- \`ContinuumCortexResearchPipeline.kt\`
- \`ContinuumCortexPipelineChecks.kt\`

Zwei ausführbare Assertion-Suiten bestanden:
- \`ContinuumCortexShadowChecks\`: Gradientenbeginn, 10.000 Updates bei normbegrenzten Gewichten, NaN-Ablehnung und Reset, drei nachvollziehbare Z77→Werkhalle-Nord→Graz→50Hz-Kanten, Widerspruchs-Abstention, veränderte Hashes, leere/fehlgeschlagene und unvollständige Holdouts sowie bewusst begrenzte Cache-Freigabe.
- \`ContinuumCortexPipelineChecks\`: Themenfokus über mehrere Nachrichten, Pronomen-Rückbezug auf **explizit festgelegte** Entität, die typisierte Drei-Hop-Abfrage, korrekte Enthaltung bei ununterstützten Fragen, länger bestehender Fokus und schattenhaften Cache.

Zusätzlich ist der vorhandene Node-Symlink-Pfad-Policy-Test lokal mit 2/2 bestanden. Die neue Android-JUnit-Testintegration ist **noch nicht** in der vollständigen Gradle-Umgebung gelaufen.

## Unverzichtbare, noch offene Integrationsarbeit

- Keine ungeprüfte Verbindung zur \`AgentActivity.kt\`; zuerst den realen Chat-/Memory-API-Pfad und das UI-Opt-in überprüfen. Ein Chattext darf keine privilegierte Toolfreigabe auslösen.
- SWB1→16-D-Repräsentation ist noch nicht als verlässlicher, getesteter Adapter im Hauptchat angebunden.
- Lokale KG-Kanten müssen aus nachvollziehbaren, tatsächlich bestätigten Quellverweisen und einem authentisierten Ownersiegel oder bestehenden Freigabespeicher entstehen. Ein bloßes \`ownerReviewed=true\` ist kein Beleg.
- Der passende Relationstyp ist in diesem Experiment **manuell** typisiert. Weder robuste deutsche Intent-Erkennung noch allgemeine Entitätsauflösung sind nachgewiesen.
- LM1/GGUF dürfen nicht an dieser Forschung vorbei ungeprüfte Gewissheit generieren. Die Forschung wird erst nach Rückfalltest und Besitzerfreigabe bewusst aktivierbar.
- Sleep-Wirkung ist ein RAM-Shortcut-Cache, nicht die behauptete sichere selbständige neuronale Gewichts-Evolution. Für tatsächliche Trainingsgewichts-Promotion: separate Checkpoint-Signaturen, disjunkte Holdouts, negative Gegenbeispiele, Stromausfallwiederherstellung und anwendungsweite Regressionstests nötig.
- Kein realer Android-SDK-/Gradle-/Lint-Build, keine Termux-/Shizuku-Geräteprüfung und kein neu signiertes Owner-APK. Version 0.3.17/code18 ist nicht fertiggestellt. Der alte APK-Signaturschlüssel darf nicht ersetzt werden.

## Wissenschaftliche Einschränkung

Numerisches Test-Time-Training ist weder automatisch ein Gesprächsgedächtnis noch ein Universal-Reasoner. Eine erfolgreiche toy-Drei-Hop-Graphabfrage mit manuell getypten Relationen ist ein Test der endlichen Logik, nicht der Qualität eines allgemeinen Weltmodells. Ohne belastbare Negativtests können selbst fehlerfreie Trainingsläufe falsch sein. Kennzahlen und mobile Latenzen sind bisher nicht gemessen.
