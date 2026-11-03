# Continuum Ω — Gehirn- und Forschungsinventur

Gelesener Quellstand: PR #390 `b4f61ae9765e26dd401db928b76fa9f233c0742a`. Unabhängiger Read-only-Auditor: echte Sitzung `/root/research_inventory`; keine Experimente dieses Audits neu ausgeführt. Zahlen unten sind geprüfte historische Artefakte, keine neuen Fold7-Messungen. Aktuelle Änderungen/Tests stehen in ASTRA-OMEGA-HANDOFF-20261103.md und den Lane-Berichten.

## Tatsächliche Hauptpipeline

`AgentActivity.runLocal` verwendet den vorhandenen `AiContextCompiler`, Gesprächsverlauf, `LocalFollowUpTaskFrame`, `OwnerStatementTimeline`, `LocalPromptFrame`, `BoundedAgentOrchestrator`, lokalen Modelladapter und `ToolGate`. Compiler und Agent rufen den bestehenden `HybridMemoryRetriever` auf. Der Modellwechsel während eines Turns wird geprüft, Freigaben und Bildschirm-Grant-Epoch werden je Fortsetzung erneut kontrolliert. Kein neuer Gehirn- oder Sprachkern erforderlich.

- `AgentActivity.kt` um 6518: begrenzter Orchestrator und echte Modellfortsetzung; um 6604: zeitliche Besitzerprojektion ausschließlich aus flüchtigen Besitzerturns; um 6644: atomarer Pflichtkontext statt abgeschnittener Negation; um 8628: Retentionpolicy.
- `AgentActivity.kt` um 3137 → `CognitiveDecisionBridge` → `ContinuumCognitiveRuntime` → `ContinuumLearningCore.replyWithEvidenceInConversation` → `ContinuumMemoryDialogue`: bestehende Gedächtnis-/SWB1-Antwortschicht.
- `AgentActivity.kt` um 3194/3196 → `ContinuumBrainBridge` und `ContinuumTemporalMemorySpecialist` über `ChatSpecialistChain`; EWM/Temporal benötigen vorhandenen Owner-Schalter, Standard OFF, SHADOW erhält Baseline.
- Hauptchat, Teamstatus und Coding-Entwurf sind verschieden: der Code40-Statuspfad führt einen echten read-only ChiefWorkerPool-Job aus; er startet keine externen Coding-Modelle. Coding-Entwurf ist kein ausgeführter Test.

## Neuronale Komponenten: aktueller Nachweis statt historischer Behauptung

| Komponente | Verbindung | Nachweis und Grenze |
|---|---|---|
| SWB1 | TrainableSubwordEncoder in MemoryDialogue; EWM verwendet Ranker | Letztes substantielles 33-Trial-Experiment: Semantik 26/60, drei Sidecars 26/60, 26/60, 27/60; Dialog 3/6 unverändert; REJECT_NO_STABLE_FINAL_GAIN. Produktiver Forschungs-Snapshot blieb erhalten. Keine neu gemessene Modellqualität. |
| CRE1 | Runtime lädt Checkpoint und prüft Promotionmanifest; qualifiziertes Reranking permutiert nur bereits SWB1-autorisierte Kandidaten | Dokumentierter 32-Schritt-Lauf: Loss 0.804442→0.801303. Das ist kein belegter Antwortqualitätsgewinn. Aktiver Fold7-Checkpoint und Promotionstatus nicht beobachtet. |
| EWM2 / Temporal | Reale LearningCore-/Specialist-Verbindungen, TemporalRoleEvidenceLedger lookup | Neuer 40-Fälle-DE-Test: 34 korrekt, 5 Enthaltungen, 1 falsch. A8 „Ich habe gekündigt“ lässt alten Arbeitgeber aktuell. Ältere 273/273 sind in der Grammatik erzeugt; 18/20+2-Enthaltungen wurde nachoptimiert. |
| CDX1 | LearningCore-Labor und paired decoder status/train; nicht normaler Gemma-Generator | 27 Ablationsvarianten: 0/20 belegte Antworten, 0/5 Enthaltungen. Oracle: 47/47 trainierte Antworten, 0/29 unbekannte Ortsnamen. Begrenzte Experimente, kein allgemeiner Unmöglichkeitsbeweis. |
| CDX2 | tools/continuum-span-research, kein Android-Aufruf | Vorhandener 11-Gewichte-Span/NOANSWER-Klassifikator + zwei trainierte Adaptermerkmale. Neuester Adapter E55/55, altes C55/55 in beiden Seeds; E-Baseline RAW45/55, 10 Verbesserungen/0 Regressionen. RULE/VERIFIED bereits55/55: kein zusätzlicher geprüfter Antwortgewinn. SHADOW_PASS_NOT_PROMOTED. |
| ANSK | Host-Lab über vorhandene Repair-Worktrees und Verifier | 82/82 Vertragstests mit echtem Git, simuliertem Gradle; kein gemessener Transfergewinn. Historischer Samsung-Bericht 632 Tests/Phase bleibt Besitzerangabe ohne hier vorliegendes Originalrezept. Kein Kotlin-Chat-Caller. |
| NEURON | Activity → NeuralDialogue/TransferExperiment/GeneralizationComparison, DemoSandbox | Reales kleines Klassifikatorlabor (32 Eingänge/8 Hidden/1 Ausgang) mit vordefinierten Textantworten. Drei Demo-Erhalt-/Abbruchtests dokumentiert. Keine freie Sprachgenerierung oder neue Generalisierungsabnahme. |

SWB1-Rohdaten liegen auf weiterhin vorhandenem Zweig `sol/continuum-autonomy-contrastive-20261027`, HEAD `056b9e1f…`, Pfade `docs/evidence/sol-autonomy-20261027/final/final-report.json`, `final/decision.json`, `feature-collision-analysis.json`; nicht stillschweigend als Teil des aktuellen Quellbaums ausgeben. CRE1: `docs/evidence/sol-0320-20261024/auto-dialogue-probe.txt`. CDX1: `docs/evidence/sol314-20261101/CDX1-ABLATION.md` und `docs/evidence/fable51-genesis-20261101/cdx1-oracle-bound.json`. CDX2: `docs/evidence/synapse-iii-20261102/m2-adapter/`; alle fünf dort gebundenen Quellhashes passen zum #390-Stand.

Die versuchte vollständige 13-Gewichte-CDX2-Neutrainierung wurde verworfen: altes C zeigte RAW zehn unbelegte Positive und VERIFIED den falschen Fall C027. Alte gute Gewichte bleiben erhalten. Die Seeds ergaben identische Gewichte wegen identischer aktiver Merkmale; das sind keine unabhängigen Aufgabendistributionen.

## Gedächtnis: vorhanden, verbunden oder nur experimentell

- `FileLearningExperienceStore` begrenzt #390 auf 256 Einträge. Geschützte Besitzer-/Widerrufseinträge können nicht verdrängt werden; Overflow wirft Fehler. Die Memory-Lane entwickelt die begrenzte LEX3-Migration unter demselben Vertrag. Keine zweite Faktendatenbank.
- SHA-gebundener Temporal-View-Cache ist bereits implementiert. Historischer Hostvergleich: warmer p95 30.056ms → 0.612–3.411ms bei 819/819 korrekten Ergebnissen. Kein neuer Cache erforderlich, keine Fold7-Energiebehauptung.
- `SignedOwnerCorrectionJournal` verwendet bereits `ProofCarryingMemoryMutation` und `MemoryMutationLedger`; LearningCore hat opt-in Signerpfad. Normale Activity-Konstruktion übergibt keinen Signer: Verbindung vorhanden, produktive signierte Mutationen nicht belegt.
- `ContinuumMemoryLayers` wird durch `ChiefBrainWiring` und `ContinuumThinkingMemory` verwendet; keine produktive Activity-Konstruktion dieser Wrapper gefunden. `ContinuumSkillLibrary` ohne externen Main-Caller gefunden. Nicht als aktive Fähigkeiten zählen.
- `MemoryGraph`, `TemporalNeuralShadow`, `ReviewedEvidenceGraph` haben Quellen/Tests ohne gefundenen externen Main-Konstruktor; `AdaptiveIdentity` ohne gefundenen aktiven Update-Caller. Keine Integration nur zur Vergrößerung der Komponentenliste.
- `ExperienceCompiler` hat einen echten Activity-Review-Aufruf, erzeugt jedoch Review-Kandidaten und keine selbst freigegebenen Fähigkeiten.
- Zeitliche Korrekturen und Widerruf dürfen bei Recovery, Transfer und Cache-Rebuild niemals durch einen älteren Backupstand überschrieben werden.

## Evidenzbindung und Recherche

Echte gebundene Ortsantworten laufen über `CertifiedLocationReflex` → `EvidenceBoundNaturalLocationProbe` + Verifier + Rollenadapter; auch MemoryDialogue verwendet diesen Pfad. `Cdx1EvidenceSlotBinder` dagegen wird nur durch Tests aufgerufen. Er bindet ein exaktes Zitat an einen überprüften Quellslot; Hashes beweisen weder Wahrheit noch richtige Rolleninterpretation.

`DeepResearchEngine`, erlaubte Websessions, Search/Read, Consent OFF/ASK/ALWAYS_ON und Quelltexte als unbestätigte Daten sind vorhanden. Im runLocal-Pfad sind Webaufrufe begrenzt, Netzwerk-Widerrufe werden erneut geprüft. Live-Web-Recherche, unabhängige Chief-Research-Worker und RIS-End-to-End-Abnahme wurden in dieser Sitzung nicht ausgeführt. Die bestehende Funktion darf nicht mit einem unbegrenzten autonomen Webforscher gleichgesetzt werden.

## Chief / CCC1 / autonome Entwicklung

#384 Staging, #385 TaskGraph, #386 ProofGatedStrategyLedger, #387 Alpha-Squad bilden einen separaten Draft-Stack. #390 enthält diesen nicht als Ganzes. TaskGraph plant Abhängigkeiten, führt keine Werkzeuge aus; unterschiedliche Capabilities beweisen keine personell unabhängige QA. Alpha-Squad benennt vier virtuelle Phasen auf einem tatsächlichen CCC1-Zyklus, keine vier unabhängigen Modelle.

Die vorhandene Android-Entwicklungskette ist `ContinuumCodingTask.Authorization` → bestehende TypedCodingBridge → feste Termux-Brücke → coding-orchestrator → Kandidatenprovider → kanonischer Repair-/Verifierpfad. Importiertes READY-Gemma in der App stellt nicht automatisch einen Termux-Modellserver bereit. Der jetzige Meilenstein repariert genau diesen bestehenden Belegvertrag, ohne einen freien Shell- oder Scheduler-Nebenpfad zu schaffen.

## Avatar / Cockpit

#338 liefert ein optionales Offline-Avatar-Labor. #350 hält gepinnte Upstreamquellen/Provenienz, aber keine nachgewiesenen GLB-/Morph-Rig-Assets. #373 stellt den Event-/TTS-Vertrag. #378 enthält laut aktuellem HEAD nur Arbeitsvorbereitung. Zusätzlich wurde aktueller Design-Draft #391 entdeckt; eigene Designassets werden nicht überschrieben. Die Android-Lane baut einen nativen prozeduralen Kopf als auswählbare Darstellung des existierenden Hologramms, mit echten Director-/TTS-Ereignissen. Orb bleibt Default; kein Kamera-Tracking, kein zweiter Zustandskern.

## Messmodell und nächste zwei Forschungsschritte

`evaluation-v1.json` ordnet alle 15 gewünschten Leistungsdimensionen den vorhandenen Tests und ausdrücklich fehlenden Messungen zu. Der bestehende 81-Fälle-M3-Test bleibt unverändert und ist Entwicklungs-/Regressionsmaterial. Der neue Vergleich verwirft geänderte Fälle/Erwartungen, harmful-Ausgaben und jede Fallregression, auch bei höherer Gesamtpunktzahl. Er authentifiziert weder Lauf noch Gutachter und ersetzt keine unabhängigen Rohlogs.

1. **Zeitliche Kündigung/Korrektur:** bekannte A8-Klasse durch separate Entwicklungsregression reparieren; ursprüngliche 40 Fälle unverändert wiederholen. Vor weiterem Tuning 100–200 neue deutsche Fälle unabhängig einfrieren, unerkannte Frageformen getrennt von gerechtfertigter Enthaltung zählen. Gleicher LearningCore-/Specialist-Pfad, keine Gewichte/Ownerdaten ändern.
2. **CDX2 natürliche Generalisierung:** bestehendes 11+2-Modell, RULE und Scorer einfrieren; unabhängig formulierte neue Namen, Rollentausch, Verneinungen, Widerrufe, gemischte Relationen, Unicode und beschädigte Quellen prüfen. RAW/RULE/VERIFIED, Belegtupel und Enthaltungen getrennt. C027 bleibt sichtbare Verifier-Regressionskontrolle. Nur SHADOW, keine Android-Promotion ohne belegten Vorteil.

A/B/C/D/E und Synapse-II-Kontrollen sind verbraucht. Sovereign-Ergebnis hatte 72 Zeilen, aber 54 eindeutige Aufgaben (36→54 korrekt, 18 Verbesserungen). Neuer Rollentauschtest zeigte 0/12 exakte SWB1-Kollisionen; historische spezielle Kollisionen dürfen nicht zu „alle Rollen vertauschen kollidiert“ verallgemeinert werden. Der deutsche 40-Fälle-Test zählt teils unerkannte Fragen als Enthaltung; das ist kein offizieller LongMemEval-Score.

Originalquellen im Audit read-only geprüft: [Pointer-Generator Networks, ACL2017](https://aclanthology.org/P17-1099/), [LongMemEval](https://github.com/xiaowu0162/LongMemEval) (500 Fragen/fünf Fähigkeiten, bereinigter Release September2025). Kein externer Code oder Datensatz importiert. Echte Gemma-/Fold7-/Energievergleichsläufe fehlen weiterhin.
