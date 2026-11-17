# Semantischer Vergleich: alte Kernpfade

**Aktuelle Basis:** `3ff83387af64bf9f99f4440f657f3d27937ca777`, Baum `5206abd91b3740838661aa0e27e6702b3891591b`, PR591.
**Verfahren:** Originalblobs und damalige Aufrufer gegen aktuelle Produktionsquellen gelesen; keine Builds, Tests oder Quellenänderungen. Kein Leistungs- oder Gerätebeweis. Kurz-SHAs in der Tabelle werden unten und in den JSON-Belegen vollständig aufgelöst.

## Ergebnis und Reihenfolge

Die 13 zugewiesenen fehlenden Dateipfade bedeuten keine 13 fehlenden App-Funktionen. Sechs Phase‑1-Grundlagen haben aktuelle Nachfolger. Drei begrenzte App-Deltas sind konkret nachweisbar: M4-Fehlertexte, ein Startknopf für bestehende lokale Vertragsprüfungen und eine breitere sprachliche Selbstprüfungsroute. Bei RAID und Compute fehlen zusätzliche alte Primitive; ihre damalige App-Verdrahtung ist jedoch nicht belegt. Der adaptive Analysator und die Kotlin-Dual-Control-Policy waren ebenfalls keine angeschlossenen neuen App-Ausführungspfade.

**Empfohlene nächste Prüfung:** erst die zwei kleinen Diagnose-/Routinglücken, dann den Auditknopf mit seinem tatsächlichen Skript und Callback. Größere Forschungsbausteine benötigen getrennte Integrationsarbeit; kein alter Gesamtzweig sollte hierfür übernommen werden.

## 13 zugewiesene Pfade

Alle Pfade sind relativ zu `app/src/main/java/at/heinz/deviceai/`.

| Ursprung / SHA | Datei und tatsächliche Funktion | Aktueller Ersatz / tatsächlicher Aufrufer | Befund und fehlender Anschluss |
|---|---|---|---|
| #310 / `ccbf0431d4` (auch #309) | `ContinuumTermuxFailureDiagnosis.kt`: feste, begrenzte Fehlertexte für fehlendes M4-Projektskript oder ungültigen Git-Checkout. | Aktueller `ContinuumMobileBuildResultReceiver` zeigt für M4 nur generische Policy-Texte. `ContinuumCcc1Bridge.diagnose` betrifft CCC1 und ein anderes Skript. | **Kleine echte Lücke.** Alter Receiver rief den Helfer nur bei EXECUTION_FAILED auf. An aktuellen M4-Fehlerzweig anbinden; keine Rohlogs speichern/anzeigen. |
| #233 / `316c55543f` | `ContinuumSelfInspectionQuery.kt`: begrenzter deutscher Intent-Matcher für Untersuchung eigener Projekt-/App-Schwächen. Alte Activity zeigte ehrliche Laufzeitgrenzen und Statuswege. | `AgentActivity.isContinuumSelfCheck/showContinuumSelfCheck` sind angeschlossen, akzeptieren fünf feste Formulierungen und lesen echte Lernkern-/Journalzustände. | **Teilweise vorhanden.** Breite Formulierungen und damalige Schwächen-Zusammenfassung fehlen. Bestehenden Statuspfad gezielt erweitern; der Matcher ist keine Codeanalyse und keine Änderungsfreigabe. |
| #208 / `cc6c629500` | `ContinuumMicroAgentBridge.kt`: Termux-Aufruf von `scripts/continuum-microagents.mjs --run all`; strikte Auswertung von fünf deterministischen Offline-Vertragsprüfungen. | Kein aktueller Symbol-/Skript-/Callback-Treffer. Chief-Bots sind eine andere Funktion. Alter Builder hatte Knopf+Bestätigung, alter Receiver `MICRO_AGENT_AUDIT`-Routing. | **Echter fehlender Audit-Aufruf.** Skript, genehmigten Dispatch, aktuelle Callback-Provenienz und Ergebnistext zusammen portieren. Kein Nachweis laufender Modelle, autonomer Reparatur oder Gerätetests. |
| Branch / `27f1861eab` | `ContinuumDualControlPolicy.kt`: reine DENY/REVIEW/ALLOW-Tabelle mit Owner-/Sitzungs-/OS-Gates und wiederverwendbarer Zustimmung. | Entscheidungstabelle existiert in `.github/continuum/mcp-adapter.mjs`; Android nutzt den angeschlossenen `AgentToolLayer.ToolGate` mit Bestätigungen, Budget und Journal. | **Unverbundener Android-Prototyp; JS-Spiegelung vorhanden.** Keine Policy-/Profilreferenz in den beiden damals geänderten Activities. Für Android fehlen vertrauenswürdiger Grant-/Profilzustand und Abgleich mit dem tatsächlichen ToolGate. |
| Phase‑2 / `3d7608023b` | `cognitive/TaskComplexityAnalyzer.kt`: heuristische gewichtete Bewertung, Gründe und Budgetkappen. | `ContinuumTaskClassifier` → `ReasoningStrategySelector` → `ChatReasoningRouter` ist über Activity angeschlossen; begrenzte zusätzliche Modellaufrufe. | **Kernzweck vorhanden.** Zusätzliche Bewertungsdimensionen/Begründungen sind ein unverbundenes Experiment; keine Referenz im damals mitgeänderten Deliberation-Code. Kein belegter Qualitätsgewinn. |
| #8 / `9eaa591239` | `continuum/AiContextCompiler.kt`: priorisierte, gefilterte Kontextsektionen unter Bytebudget. | `cognitive/AiContextCompiler` wird in Activity verwendet; verbindliche semantische Einheiten und `requireCompleteIn` statt alter Präfixkürzung. | **Ersetzt.** Alten Compiler nicht zurückbringen; er kürzt auch verpflichtende Abschnitte. |
| #8 / `9eaa591239` | `continuum/ContinuumLocalContextAdapter.kt`: Featureflag entscheidet zwischen Compiler und altem Fallback. | Aktueller tatsächlicher lokaler AgentModel-Pfad in Activity kompiliert den Kontext direkt. | **Ersetzt.** Kein fehlender Laufzeitadapter. Die aktuelle Forschungsklasse `ContinuumLocalAgentModel` wurde nicht als Produktionsaufrufer gewertet. |
| #8 / `9eaa591239` | `continuum/ContinuumSeed.kt`: signierter Identitäts-/Governance-/Zustandsvertrag. | `cognitive/ContinuumSeed`, `FileContinuumSeedStore` und `ContinuumRuntimeStore`; tatsächliche Activity-Initialisierung und Wiederherstellung. | **Ersetzt, anderes Format.** Keine zweite Identität oder alte Datenmigration ohne eigenen Vertrag einführen. |
| #8 / `9eaa591239` | `continuum/ContinuumObjectStore.kt`: unveränderliche Inhalte, Hashprüfung, Quota, sichere Dateischreibpfade. | `cognitive/ContinuumState.kt` + `ContinuumRuntimeStore`; zusätzlich tatsächliche ChiefBotProfileStore-Nutzung. Neue IDs binden Typ und Inhalt, Zustände Generation und Vorgänger. | **Ersetzt, inkompatible IDs.** Kein fehlendes App-Gedächtnis allein aus diesem Pfad. |
| #8 / `9eaa591239` | `continuum/Deliberation.kt`: sieben Strategien, Kandidat/Kritiker/Prüfer/Judge, Grenzen und Abbruch. | `cognitive/Deliberation`, `ContinuumIntelligence`, `ChatReasoningRouter`; Router wird in Activity erstellt. | **Kernzweck ersetzt.** Alte Promptkürzung und separaten Executor nicht als Upgrade übernehmen. |
| #8 / `9eaa591239` | `continuum/BenchmarkHarness.kt`: zehn Kategorien, extern gelieferte Messwerte und Modellkontinuitätsvergleich. | `cognitive/ContinuumBenchmark.kt` enthält dieselben Kategorien, Harness und `ModelMortalityExperiment`. Kein aktueller Activity-Aufrufer gefunden. | **Als Messinfrastruktur weitgehend ersetzt.** Auch der alte PR verband keinen Benchmarklauf mit der App. Keine gemessene Modellqualität aus den Klassen ableiten. |
| #8 / `9eaa591239` | `continuum/CognitiveRaid.kt`: Wiederherstellungsplan pro objectId+tier, erforderliche Objekte und Verifikationsliste. | `cognitive/CognitiveRaid` bewertet nur pro tier; kein Produktionsaufrufer des Planners gefunden. Reale lokale Wiederherstellung liegt im `ContinuumRuntimeStore`. | **Teilweise ersetzt.** Alte objektgenaue Prüfung ist eine echte zusätzliche Logik. Für Nutzen fehlen Store-Adapter und Wiederherstellungsaufrufer; damaliger PR8 verband nur den Kontextcompiler. |
| #8 / `9eaa591239` | `continuum/ComputeFabric.kt`: signierte Registry plus begrenzter lokaler Executor mit Queue, Deadline, Abbruch, Widerruf und Ergebnisbindung. | `cognitive/ComputeFabric` bietet Registry, Auswahl und Ergebnisprüfung. `VirtualModelFabricSnapshotExporter` nutzt Auswahl als ausdrücklich nicht ausführbare Planung; kein Verbraucher von `LocalComputeWorker` gefunden. | **Teilweise ersetzt.** Alter Executor ist ein zusätzlicher unverbundener Baustein. Er braucht echte Worker, Lifecycle und aktuelle Ergebnisverträge. Kein Nachweis zusätzlicher Rechenleistung. |

## Bereits geprüfte größere Kandidaten

Diese Zeilen ordnen vorhandene Reviews ein; keine erneute ausführliche Prüfung oder Testausführung. Aktuelle Abwesenheit ist im Inventar bestätigt. Die Folgeversionen #326/#387 müssen vor späterer Übernahme separat verglichen werden.

| Kandidat / SHA | Tatsächlicher Beitrag | Vorhandenes / noch fehlender Anschluss | Priorität |
|---|---|---|---|
| #316 / `08a7f13ce9` | `DevelopmentSourceStreamImport`: begrenzter, disk-basierter UTF‑8-Import mit Hash, Abbruch und atomischem Abschluss. | Die Datei fehlt. Kein SAF-/App-/Modellaufrufer im geprüften Beitrag; Quota und Crash-/Abbruchpfad müssen ins heutige Produkt eingebunden werden. | Sinnvolle Grundlage für große Quelltexte; gemeinsam mit Index und Folgefix prüfen. |
| #318 / `bc75bae656`; Folge #326 / `e193411d37` | `DevelopmentSourceChunkIndex`: begrenzte Codepunkt-Chunks, Offset-/Zeilen-/Hash-Metadaten und geprüfter Rücklesepfad. | Index und Import fehlen aktuell. Kein Retrieval-/Ranking-/Modellanschluss. #326 ist laut Inventar ein Provenienz-Folgefix; dessen Inhalt wurde hier nicht neu bewertet. | Nach Importvertrag und Datenschutzgrenzen; nicht rohen #316-Stand bevorzugen. |
| #385 / `8f64178818`; Folge #387 / `eed77e56b5` | `ChiefTaskGraph`: begrenzter DAG, Abhängigkeiten, Dateikonflikte und separate Verifikationsaufgaben. | Aktuelle Chief-Ausführung existiert, dieser Planer fehlt. Er braucht echten Pool-/Worktree-/Lifecycle-Anschluss und unabhängig vertrauenswürdige Prüfergebnisse; der Planer führt selbst nichts aus. | Mittlere Integrationsgröße. Folge #387 vor Port prüfen. |
| #386 / `ee9b2dfde7` | `ChiefProofGatedStrategyLedger`: eng gebundene Strategiedaten aus unabhängigen RED/GREEN-Belegen. | Ledger fehlt. Fehlender Vertrauensadapter für echte CCC1-/Host-Belege und Persistenz; ohne Prüfer keine akzeptierte Lernerkenntnis. | Nach echten Belegen und Chief-Anschluss. GREEN ist kein autonomer Patchbeweis. |
| #490 / `8d7b560ede` | `cognitive/loop/*`: Verträge, Engine, Cockpit-Bridge und Gate-Ports. | Eigener Handoff nennt fehlenden lokalen Reasoning-Adapter, Planner, Activity- und Memory-Verdrahtung. Heutiger Chief-/Agent-Loop ist vorhanden. | Architekturarbeit; kein zweiter konkurrierender Laufkreis als Einzelmerge. |

## Belege und Grenzen

- `blobs.json`: alle 13 Originalpfade, Git-Blob-IDs, SHA256, vollständige Herkunfts-Heads und lokal gelesene Originale.
- `historical-callers.json`: acht damalige Caller-Dateien, Original-Blob-IDs und konkrete Symboltreffer bzw. Nulltreffer.
- `current-evidence.json`: aktuelle Blob-IDs und symbolbezogene Aufruferstellen, geprüft auf exakt 3ff.
- Aktueller vorhandener Vorreview: `docs/research/omega/CODE48-CONNECTED-CINEMA-20261107.md`, Abschnitt Branch search.
- Quelltextprüfung belegt Existenz, Anschluss und erkennbare Verträge; keine neue Laufzeit-, Qualitäts-, Akku- oder Fold7-Messung.

### Vollständige Herkunfts-Heads

| Bezug | SHA |
|---|---|
| #8 | `9eaa591239b54dc486ecc9d94457621963e34a91` |
| Phase‑2 | `3d7608023b3f7ebbdf09ecb29aef48816685b594` |
| Dual-Control | `27f1861eab2f0fed3d251bdc0ef4e37cfa35b2ae` |
| #208 | `cc6c6295002cce1fa46e10c28f4e7436dd7fa967` |
| #233 | `316c55543f2e1e84a52c5ace437c572791e1936d` |
| #310 | `ccbf0431d446789234fe8d5f0583cf9b8d40b8ba` |
| #316 | `08a7f13ce9dbb2180e244789917d64e7b28ce49f` |
| #318 | `bc75bae6562fc05b5ff0abcf0f3154ef099f21d1` |
| #326 | `e193411d37339b4c91a4e24ef341b03794a0aa7f` |
| #385 | `8f64178818399c0d5412bbe20bab6b178b98c037` |
| #386 | `ee9b2dfde74499a499f86ba3e07c19f48b89c446` |
| #387 | `eed77e56b526d199141b658a4bdd38acd2d896e0` |
| #490 | `8d7b560ede4623c110f94138da183189991ed4a5` |
