# Continuum – Systemanschlüsse und autonome Dialogforschung
Datum: 24.10.2026 · Repo: `Ullmann27/device-ai` · Branch: `chatgpt/continuum-autonomous-dialogue-research-20261024`

## Nachgewiesener aktueller Mangel
Die Besitzer-APK ist nicht automatisch ein gemeinsamer produktiver Sprachkern, obwohl die Quellbasis viele einzelne KI-Bausteine enthält. Bestätigt durch Quellinspektion:

1. `AgentActivity.runContinuumDialogue` führt die bestätigte SWB1/CRE1-Suche mit `CognitiveDecisionBridge(..., null)` aus. Die optionale direkte Reasoning-Adapter-Verbindung ist dadurch in genau diesem Aufruf aus. Ein bereites importiertes LiteRT-LM-Modell kann stattdessen über `LocalFirstPolicy.afterMemory` auf noch unbeantwortete Fragen reagieren; dies ist ein separater Pfad.
2. Das proprietäre LM1 unter `learning/lm/` besitzt Tokenizer, Transformer/minGRU, Checkpoints und `LmRuntime.generate`, ist aber **nicht** in den Hauptchat verdrahtet. Laut unabhängig dokumentiertem LM1-Test: auf frischem Text schwache Generierung, keine belegte Anweisungsverarbeitung. Es darf nicht als allgemeine KI umetikettiert werden.
3. Die GGUF-/Kontextskripte existieren in `scripts/`, sind keine eingebettete Android-GGUF-Inferenz; die vorhandene Android-Importoberfläche benötigt eine `.litertlm`-Datei.
4. `NeuralPracticeScheduler` wiederholt Eigentümer-lektorierte Beispiele nur bis zur Stagnation. `ContinuumAutonomousScheduler` plant endliche Offlinejobs und optional eine CDX1-Forschungsstufe. Bisher gab es **keinen eigenen, automatisch synthetisierten CRE1-Multiturn-Korpus pro Runde**.
5. Der Android-Teamleiterstatus `READY` meldet ausdrücklich nur Prüfung von Backlog und Lernkern; keinen abgeschlossenen Android-Build oder eine kontinuierlich ausführende Kodier-KI.
6. **Bestätigter Dashboard-Zählerfehler**: `ContinuumDashboardCollector` verwendete für `sources` ausschließlich `WebLearningJournal.notes().size`; die mehrhundert Einträge aus `ContinuumKnowledgeArchive` wurden beim Start-Dashboard nicht mitgezählt. Im Draft umgestellt auf die tatsächlichen archivierten Quellen mit 30-Sekunden-Cache. Das verändert keine Quelldaten.

Die folgenden PRs besitzen getrennte, teils divergierende Commits und dürfen nicht blind gemergt werden: #75 (LM1), #82 (NFC1), #92 (Opus), #93 (GGUF), #99 (CCC1), #112 (Sol), #113 (Hologramm). Die aktuelle Signierbasis ist Nachfahre von Sol #112 und Kontext #113. Vor neuen Integrationen pro Datei Quell-/Blob-Identität vergleichen.

## Jetzt im isolierten Branch implementiert – ausdrücklich KEIN Release

- `ContinuumAutoDialogueCurriculum`: erzeugt deterministisch fiktive Gespräche zweier Sprecher mit referierenden Anschlussfragen. 16 train / 8 neu auditierte / 8 fest gehaltene Beispiele je Runde; Roboteridentitäten disjunkt. Keine Nutzer-Chats und kein Internet als Trainingswahrheit.
- `ContinuumAutoDialogueLab`: ausschließlich CRE1-Forschung. Kein Chat-, SWB1-, CDX1-, Owner-Fakten- oder Signatur-Promotionpfad. Verbraucht maximal 64 getrennte Stufen. Ein Vorab-Cursor verhindert unkontrolliertes Wiederholen nach Prozessabbruch; bei Unterbrechung wird die betreffende Stufe konservativ nicht erneut trainiert.
- `ContinuumAutoDialoguePolicy`: ausdrücklicher Besitzer-Opt-in; Start nur mit mindestens 3,5 Sekunden verbleibendem bestehenden Offlinejob-Budget. Das OS darf Arbeit verschieben. Android-App-Menü: **Lernen → Experimentell → AUTO-DIALOGE · HINTERGRUNDFORSCHUNG EIN/AUS**.
- `ContinuumAutonomousScheduler`: CRE1-Aufgabe läuft nur als begrenzter Teilschritt des existierenden täglichen Offlinejobs, nicht als zweiter Endlosservice.
- `ContinuumAutoDialogueCurriculumTest`: Split-, Audit- und Bereichstests; Tests wurden als Quellcode geschrieben, aber in diesem Chat **nicht mit Gradle/JVM ausgeführt**.
- `scripts/continuum-module-wiring-audit.mjs`: reproduzierbare statische Bestandsaufnahme. Nicht als Inferenz-, Hardware- oder APK-Test interpretieren.
- `ContinuumDashboardCollector.kt` und `.github/continuum/continuum-dashboard-archive-source.test.mjs`: korrigierte Quelle der Startseiten-Zählung und statischer Regressionstest.

## Mindestnachweise vor irgendeinem signierten Update

1. `node scripts/continuum-module-wiring-audit.mjs`, mit allen gemeldeten Lücken im Protokoll.
2. `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.learning.ContinuumAutoDialogueCurriculumTest` und bestehende Kotlin-Tests auf echtem SDK/Gradle-Setup ausführen, Abbrüche = gesperrt.
3. Vollständige Android-Code-/Ressourcen-/Lint-Tests und Fold7-UI-Test mit wirklichem installiertem Modell; vor allem wiederholtes Hintergrundstarten, Akku- und Stopp-Szenarien.
4. Experimentelles CRE1-Multiturn-Training nur mit explizitem Besitzer-Opt-in und unabhängiger Holdoutverbesserung; bei fehlendem Leistungsgewinn Ergebnis `REJECTED`.
5. Aktives, zuvor signiertes App-Paket und Besitzer-Zertifikat verifizieren; privat gesicherte Erinnerungen und Gewichte nicht ersetzen.
6. Unbeantwortete Alltagsdialoge unter festgehaltener deutscher Testliste gegen Quell- und Modellantworten vergleichen. Erfolgreiche Gradienten sind **kein** Beweis für Gesprächskompetenz.

## Weitere konkrete Integrationsarbeiten

- Eine versionierte `ModuleConnectionManifest`-Prüfung, die vor jedem Owner-Release je Modul `SOURCE_PRESENT`, `COMPILED`, `WIRED`, `EXERCISED`, `DEVICE_VERIFIED` ausweist, einschließlich exakt benannter Checkpoint- und Modellidentität.
- Fortschrittliches kontrolliertes Dialogtraining mit Korrekturen, Mehrdeutigkeit, Negation und mehrzügigen Bezügen; neue unabhängige Testsets bei jeder Curriculumversion.
- LM1/CRE1/CDX1 nur nach nachgewiesener Generalisierung in abgegrenzten Bereichen einsetzen. Ein lokales LiteRT-Modell darf optionaler Lehrer/Verbalizer bleiben, nie ungeprüfte Quelle für persönliche Fakten.
- Geräteinternes Trainingslog: letzte echte Runde, Datenprovenienz, Modellhash vorher/nachher, Audit vorher/nachher und Ablehnungsgrund. Kein Fake-Success.
- Separater kontinuierlicher Termux/PC-Worker für Coding-Aufgaben mit Isolation und Besitzerprüfung; Android `JobScheduler` garantiert keine exakten Intervalle und ersetzt keinen solchen Worker.

## Status
Alle Änderungen sind bislang nur im oben genannten Prüfbranch committed. **Kein Merge, keine signierte APK und kein erfolgreicher Build oder Fold7-Lauf wurden in diesem Chat behauptet oder ausgeführt.**
