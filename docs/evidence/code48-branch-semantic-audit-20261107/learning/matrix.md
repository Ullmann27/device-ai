# Read-only Branchmatrix: Lernen, Gedächtnis und lokale Modelle

**Vergleichsquelle:** `3ff83387af64bf9f99f4440f657f3d27937ca777` · Tree `5206abd91b3740838661aa0e27e6702b3891591b` · `candidate` sauber.
**Umfang:** 19 fehlende Pfade, 24 unterschiedliche Pfad/Blob-Varianten, 18 aus historischen Commits gelesene Caller-Dateien. Keine Quell-/Gitref-Änderung, kein Testlauf, kein Training, kein Provider- oder Geräte-Datenzugriff. Die JSON-Matrix enthält alle Quellvarianten und Ref-Zuordnungen.

## Ergebnis

- Vorhandener funktionaler Ersatz: Gemma-Importkonstanten vollständig; zeitliches Owner-Gedächtnis für die ausdrücklich dokumentierte Grammatik durch einen wesentlich neueren aktiven Pfad.
- Begrenzte sinnvolle Restkandidaten: normaler Modell-Weblauf → vorhandener Lernstoffdialog; Tutor-inaktiv-Hinweis; präzise Erläuterung der Importvorschau.
- Teilersatz mit konkretem Rest: ältere englische und medienlose Web-Verbote fehlen in der aktuellen zentralen Regexliste. Nur Quellbefund, kein neuer Test; die heutige Originalturn-/Widerrufsgrenze darf nicht zurückgesetzt werden.
- Größere eigenständige Fähigkeit: exakte Frage→besitzerkorrigierte Antwort inklusive Revision/Widerruf; erfordert heutiges Daten-/Recoverydesign und ist kein kleiner Dateianschluss.
- Zusätzliche Diagnostik: Claim-Audit und expliziter CDX1-Shadowbefehl. Beide ändern weder Fakten noch Modellgewichte; keine automatische Antwortfreigabe.
- Zehn Pfade sind weiterhin Forschungs-/Shadow-Bausteine. Ihre bloße Integration liefert keine neue verifizierte Trainings- oder Gedächtniskompetenz und spart nicht automatisch Last.

**Gerätefehler:** Der aktuelle Trainingsfehler am Telefon bleibt ohne tatsächlichen Monitorcode und Phase unbekannt. Kein Fund dieser Matrix wird als Ursache oder Reparatur ausgegeben.

## Übersicht

| Pfad | Quelle | Funktion und aktueller Vergleich | Einstufung / nötiger Anschluss |
|---|---|---|---|
| `cognitive/Argon4SystemShadow.kt` | [PR242](https://github.com/Ullmann27/device-ai/pull/242) · `7924ba079396` | Zählt flüchtige Gesprächsbezüge sowie aus höchstens drei autorisierten Treffern ableitbare Relationen/Widersprüche; keine Antwortänderung.<br>AuthorizedConversationMemoryShadow ist tatsächlich in ContinuumCognitiveRuntime angeschlossen, vergleicht aber lexikalisches/CRE1-Ranking. Er ersetzt nicht die ARGON-Relationszählung. Ein fehlender ARGON-Bericht ist kein fehlender Hauptchat oder Trainingspfad. | **Forschung/Shadow**. Für reine Diagnostik DialogStateKernel + JustifiedRelationStore und die alten beobachtenden Hooks an die heutige Session-/Widerrufslogik anpassen. Für nutzbare Antworten wäre eine zusätzliche überprüfte Produktentscheidung nötig. |
| `cognitive/AuthorizedWebLearningReviewInbox.kt` | [PR239](https://github.com/Ullmann27/device-ai/pull/239) · `30fbef96182e` | Bewahrt bis zu acht Quellenkandidaten aus einer autorisierten Modell-Webrecherche nur im RAM; prüft Berechtigung bei Publish, Listenabruf und Review erneut.<br>Echte Anschlusslücke: normaler Modell-Toolpfad liest runner.dossier derzeit nur für die Read-Animation. Lernstoffdialog liest ausschließlich FileResearchDossierStore. Explizite Web-Recherche und WebLearningJournal sind bereits vorhanden. | **Begrenzter Produktkandidat**. Vorhandenes runner.dossier nach erfolgreichem session.checkAuthorized in die flüchtige Inbox übernehmen; vorhandenen Lernstoffdialog anbinden; Widerruf/STOP/Background/Sessionwechsel auch vor finalem Speichern und Coach-Übergabe prüfen. Kein zweiter dauerhafter Store. |
| `cognitive/ConversationResearchIntent.kt` | [PR310](https://github.com/Ullmann27/device-ai/pull/310) · `ccbf0431d446` | Remove-only Web-Veto für deutsche/englische Verbote; PR310 korrigiert den alten >2048-Zeichen-Fail-open-Fall zu >65536 fail-closed.<br>NetworkTurnConstraint ersetzt das Konzept und ist an Eingabe und Originalturn-Toolgrenze angeschlossen. Aktuell >8192 fail-closed, zusätzlich lokale Owner-Intents und Revoke. Die aktuelle Regexliste enthält jedoch weder englische Formen wie do not browse the web/without internet noch medienloses bitte nicht recherchieren. | **Teilersatz, Verbotsformen fehlen**. Falls weiterbearbeitet: ausschließlich fehlende explizite Verbotsformen in die aktuelle zentrale Policy übernehmen und ihre bestehenden Grenzen erhalten. Keine parallele zweite Klasse und kein Rücksetzen der Tool-/Consent-Grenzen. |
| `cognitive/LocalModelClaimAudit.kt` | [PR245](https://github.com/Ullmann27/device-ai/pull/245) · `29c1e1e1bef2` | Prüft eine fertige Modellantwort lexikalisch gegen exakt ihren Retrieval-Kontext: unbelegte Details, Satzpolarität, Rollenbindung, veraltete Werte, neue Aktionen und Konflikte. Liefert ausschließlich TraceStage.<br>Kein gleichwertiger generativer Claim-Audit im aktuellen Traceenum. LocalDialogueQualityGate ist aktiv, prüft aber Form, Wiederholungen, Kontextleugnung und interne Platzhalter; bestätigt ausdrücklich keine Wahrheit. | **Diagnosekandidat**. Ewm1ClaimCheck/Ewm1Lexicon bereits vorhanden. Vier erlaubte TraceStage-Werte plus ein metadata-only Hook nach erfolgreicher Antwort mit demselben LocalGroundedRecall; keine automatische Antwortunterdrückung oder Faktpromotion. |
| `cognitive/MemoryQuestionResolver.kt` | [PR245](https://github.com/Ullmann27/device-ai/pull/245) · `29c1e1e1bef2` | Ersetzt eine enge Form wie der Bruder von Anna durch genau einen aus aktiven Fakten ableitbaren Geschwisterkandidaten; sonst null.<br>Die alte reale Integration benutzt lediglich resolve(...) != null als SHADOW-Logsignal. Sie fragt den EWM-Brain nicht mit dem umgeschriebenen Text ab. Aktuelles EWM besitzt bereits belegbasierte Personen-/Relationsverknüpfungen, aber keinen identischen Resolver. | **Forschung/Shadow**. Als Diagnose nur JustifiedRelationStore und aktuellen aktiven Fakt-Snapshot anschließen. Produktiver Nutzen würde einen neuen beweisgebundenen Fragenpfad mit Mehrdeutigkeits-, Widerrufs- und Rollenprüfung verlangen. |
| `cognitive/MemoryTimeline.kt` | [PR245](https://github.com/Ullmann27/device-ai/pull/245) · `29c1e1e1bef2` | Heuristischer Vergleich bis zu 200 aktiver Fakten über Namen, Vierzeichenstämme und Zeit-/Korrekturmarker; erkennt Widerspruch oder veraltetes erstes Zitat.<br>Tatsächlich angeschlossene ContinuumTemporalMemory + Specialist leisten für Wohnort, Arbeit, Alter und Namensrollen mehr: zwei Zeitachsen, Modalität, Tombstones, Widerrufs-Nachprüfung, explizite Konflikte. OwnerStatementTimeline ergänzt nur flüchtige Mengenangaben im Gespräch. Der alte allgemeine Stammvergleich ist nicht identisch. | **Vorhandener Ersatz**. Kein Anschluss nötig, um die bereits vorhandene zeitliche Owner-Erinnerung zu nutzen. Außerhalb der aktuellen Grammatik müsste jede neue Fähigkeit separat begründet werden; den alten heuristischen Shadow nicht als breiten Ersatz einsetzen. |
| `learning/ContinuumAnswerCorrection.kt` | [PR27](https://github.com/Ullmann27/device-ai/pull/27) · `c3b77c0093cf` | Besitzer bestätigt eine Antwort für exakt normalisierte Frage; SHA-256-Frageschlüssel, neueste bestätigte Revision, selektiver oder gesamter Widerruf ohne neuronales Training.<br>Echte andere Fähigkeit: heutige Owner-Faktkorrekturen, Signaturjournal und Workcard-Korrektur ersetzen kein Frage→Antwort-Override. Aktuelles LearningExperienceType besitzt ANSWER_CORRECTION nicht; im produktiven Baum keine gleichnamige Lookup-/Widerrufsfunktion. | **Größerer Produktkandidat**. Benötigt bewusstes Schema/Codec-/LEX3-/Recovery-/Exportdesign, bestätigendes UI, atomare Revision/Widerruf und heutige Session-/Attributgrenzen. Nicht nur die 27-Zeilen-Hilfsklasse kopieren und keinesfalls alten Core/Store überschreiben. |
| `learning/ContinuumCdx1ChatShadow.kt` | [sol/continuum-experiment-bridge-20261027](https://github.com/Ullmann27/device-ai/blob/fe7292685d4f4105d59eed297079484df2fc157d/app/src/main/java/at/heinz/deviceai/learning/ContinuumCdx1ChatShadow.kt) · `fe7292685d4f` | Expliziter Forschungsbefehl führt einen vorhandenen CDX1-Paardecoder auf einem autorisierten Treffer desselben SWB1-Turns aus; prüft Checkpointhash vorher/nachher und zeigt unbestätigte Ausgabe.<br>CDX1-Paarlabor, lesender Status und trainPairedExperiment existieren. Der zusätzliche Chatbefehl und same-turn Shadow-Report fehlen; keine fehlende Freigabe für allgemeine CDX-Chatantworten. | **Diagnosekandidat**. Nur optionalen Diagnosebefehl an den bestehenden Turn und Decoderstatus/infer-Callback binden. Vorhandene Autorisierungs-/Checkpoint-/Bytegrenzen übernehmen; Ausgabe isoliert anzeigen. |
| `learning/NeuralTutorBatchHint.kt` | [PR239](https://github.com/Ullmann27/device-ai/pull/239) · `30fbef96182e` | Erkennt außerhalb des Tutors einen explizit mit Frage:/Gruß: markierten Paste mit mindestens zwei deutschen Zitaten und zeigt nur einen Aktivierungshinweis.<br>Aktiver NeuralLearningCoach unterstützt bereits bis zu zwölf getrennte Beispiele und einzeln bestätigte Trainingsschritte. Der Hinweis bei inaktivem Coach fehlt; das ist UX/Fehlleitung, kein fehlender Trainer. | **Begrenzter Produktkandidat**. Ein enges frühes remove-only Routing vor allgemeinem Chat/Device-Routing; ausschließlich Hinweis zum bestehenden Neuron Tutor. Keine automatische Aktivierung oder Übernahme. |
| `learning/PersonalLearningPreviewCopy.kt` | [PR311](https://github.com/Ullmann27/device-ai/pull/311) · `6a153f1334d3` | Präzisiert den read-only Importvorschau-Zähler: Forschungs-/Trainingsdatensätze sind im Backup enthalten, werden nicht als Owner-Fakten importiert; kein vollständiger Restore.<br>Aktueller Dialog hat dieselbe previewDocument-Funktion und reviewOnlyCount, zeigt aber lediglich Weitere protokollierte Datensätze. Export erklärt Teile des Umfangs, die Importvorschau enthält diese ausführliche Erklärung nicht. | **Begrenzter Produktkandidat**. Kleinen Copy-Helper an bestehende Vorschauzeile hängen; kein Parser-, Import-, Recovery- oder Datenformatwechsel. |
| `learning/interactive/InteractiveDialogueContext.kt` | [PR174](https://github.com/Ullmann27/device-ai/pull/174) · `fe838399ce31` | Opt-in strukturierter Gesprächspuffer mit quellgebundener Entity-Registry, Revision-Hash, Fokus, negativen Bezügen, Konflikthinweis und bis zu 24 erhaltenen Anker-/Recent-Turns.<br>Aktuelle LocalConversationContext/PromptHistory halten sessiongebundene Owner-Turns, Vollständigkeit, unbestätigte Modelltexte und typisierten Recherchekontext. CTX3-SHADOW ist bereits im Core angeschlossen. Kein identischer strukturierter 24-Turn-Ankerpuffer. | **Forschung/Shadow**. Vor Nutzung echte quellautorisierte Registry/Revision liefern und heutige Kontext-, Negations-, Größen- und Invalidierungsregeln integrieren. Anschließen verändert Kontextauswahl und benötigt eigenen Qualitätsnachweis. |
| `learning/interactive/InteractiveResearchRouting.kt` | [PR174](https://github.com/Ullmann27/device-ai/pull/174) · `fe838399ce31` | Reine Planinspektion: bei unklarer Entity Rückfrage; sonst vorhandenes ArchiveAnswerPolicy.select auf unveränderter Frage; danach Modellbedarf/fehlende Konfiguration melden.<br>Aktuelle echte Chatkette hat bereits Archiv-/Gedächtnis-/Modell-Gates. Dieser Adapter ist ein Forschungs-Wrapper um dieselbe Archivpolicy und startet selbst keinen Modellaufruf. | **Forschung/Shadow**. Nur zusammen mit InteractiveDialogueContext und passender Registry sinnvoll; keine eigenständige Produktlücke. Hauptchat nicht auf den alten Wrapper umleiten. |
| `learning/synapse/ContinuumSynapseZeroShadow.kt` | [PR71](https://github.com/Ullmann27/device-ai/pull/71) · `2b58ed17861d` | Deterministischer Forschungsreader für explizite Roboter-/Modul-IDs: Standort, Modul, Kühlmittel/Schutzgas; direkte oder zweistufige Ketten mit Textspanne/Quellhash.<br>Aktuelle CertifiedLocationReflex/Span-Verifier liefern bereits echte beleggebundene Ortsantworten, aber nicht dasselbe technische Roboterschema. Original erklärt sich als isolierter read-only Reasoner ohne SWB/CDX/Owner-Dateien. | **Forschung/Shadow**. Als Forschungsartefakt bewahren. Ein produktiver technischer Spezialist benötigte belegte Eingabeform, Qualitätsgrenze, aktuellen Autorisierungskontext und Aufrufvertrag. |
| `learning/synapse/SynapseContrastiveRoleProbe.kt` | [PR71](https://github.com/Ullmann27/device-ai/pull/71) · `2b58ed17861d` | 4096-Parameter-Zwei-Kandidaten-Rangexperiment mit getrennten Embeddings, Rollenbindung und analytischem InfoNCE-SGD.<br>Kein Teil des realen SWB1/CDX1-Schedulerpfads. CRE1-Reranking ist bereits separat mit PromotionGate an autorisierte Kandidaten angeschlossen. | **Forschung/Shadow**. Nur Forschung. Vor irgendeiner Nutzung wären Trainingsartefakte, sichere Persistenz/Versionierung und unabhängige Transfer-/Abstentionsmessung nötig; kein kleiner fehlender Caller. |
| `learning/synapse/SynapseV2Reasoner.kt` | [PR73](https://github.com/Ullmann27/device-ai/pull/73) · `79a3079efb1d` | In-memory Lernreader mit HRR/BAG, Rollen-/Entity-Bindung, optionalen zwei Hops, Abstention und strukturellen Belegen an exakten UTF-16-Spannen.<br>Original ausdrücklich JVM-Forschung ohne Chat- oder Owner-Datenanschluss. Aktuelle Quellen enthalten echte Span-/CRE1-Pfade; das v2-Modell ist nicht deren fehlender Speicher- oder Schedulerbaustein. | **Forschung/Shadow**. Forschung erhalten, keine automatische Aktivierung. Produktnutzung bräuchte Daten-/Checkpoint-/Budget-/Qualitätsvertrag, aktuelle Evidenzadmission und unabhängigen Transfernachweis. |
| `localmodel/LocalModelImportPreset.kt` | [PR149](https://github.com/Ullmann27/device-ai/pull/149) · `febffb293f12` | Drei öffentliche Gemma-Katalogkonstanten für Model-ID, Version und SHA-256 als vorausgefülltes Importformular.<br>Vollständig abgedeckt: ContinuumSelectedModels.LOCAL_ID/LOCAL_VERSION/LOCAL_SHA256 sind bytegleich zu den alten drei Werten. AgentActivity befüllt damit weiterhin den echten Importdialog; zusätzlich Statusprüfung über ID+Hash. | **Vorhandener Ersatz**. Kein Anschluss nötig. Alte Doppelkonstanten nicht wieder einführen. |
| `research/argon4/ComputeAllocationPolicy.kt` | [PR242](https://github.com/Ullmann27/device-ai/pull/242) · `7924ba079396` | Value-of-computation-Prototyp: wählt anhand Evidenz/Unsicherheit/Freshness/Modell/Web/Budget und Beta-Priors eine vorgeschlagene Aktion; modellierte Zeit-/Energiekosten.<br>Aktueller ChatReasoningRouter ist tatsächlich angebunden, begrenzt auf vier Zusatzaufrufe/20 s und prüft vollständige Eingabe-/Kontextbudgets. Alte ARGON-Integration berechnet nur argonShadowChoice, beeinflusst keinen Aufruf und keine Deadline. | **Forschung/Shadow**. Produktiver Lastnutzen braucht reale Signale, gemessene Kosten/kalibrierte Ergebnisse und bewusstes Routingdesign. Bloßes Anschließen der alten SHADOW-Variable spart keinen Modellaufruf. |
| `research/argon4/DialogStateKernel.kt` | [PR245](https://github.com/Ullmann27/device-ai/pull/245) · `29c1e1e1bef2` | Flüchtiger Entity-/Pronomen-/Zeitfensterkern: Erwähnungen, Korrekturausschlüsse, offene Fragen, Mehrdeutigkeitsrückfrage; spätere Variante hält ausdrücklich rollenbenannte Personen bis 32 Turns.<br>Aktuelle LocalConversationContext, OwnerStatementTimeline und Follow-up-Policies decken Sitzungskontext, Korrekturen und begrenzte Größen ab. Kein identischer langer Pronomen-/Zeitfensterkernel. Historische Nutzung über Argon4ConversationShadow bleibt beobachtend. | **Forschung/Shadow**. Für Beobachtung den vorhandenen Session-Lebenszyklus nutzen; für Produktionsumschreibungen wäre neue überprüfte Semantik nötig. Keine alten Modellantwort-Erwähnungen als Owner-Fakten oder Freigaben übernehmen. |
| `research/argon4/JustifiedRelationStore.kt` | [PR245](https://github.com/Ullmann27/device-ai/pull/245) · `29c1e1e1bef2` | Begründungsgraph mit minimalen Mengen von Basisfakt-IDs, widerrufbaren Ableitungen, Widersprüchen und engem Familien-/Ortsregelwerk; abgeleitete Aussagen immer DERIVED_UNCONFIRMED.<br>Aktuelle zeitliche Owner-Erinnerung und EWM-Belegverknüpfungen sind aktiv; kein identischer allgemeiner widerrufbarer Kinship-Graph. ReviewedEvidenceGraph existiert für Ortsrelationen, hat im aktuellen Produktionsbaum aber keinen externen Caller und ist daher kein belegter Produktersatz. | **Forschung/Shadow**. Nur aus aktuellen autorisierten Fakt-Snapshots projizieren; keine zweite kanonische Datenbank. Für Antworten wären sichere Subjekt-/Rollenadmission, Widerrufsnachprüfung, klare Ableitungskennzeichnung und harte Arbeitsbudgets nötig. |

## Nachweise und Grenzen je Datei

### Argon4SystemShadow

- Ursprungsdatei: [7924ba079396](https://github.com/Ullmann27/device-ai/blob/7924ba0793963dd84eef61517359a743071829bf/app/src/main/java/at/heinz/deviceai/cognitive/Argon4SystemShadow.kt), Blob `99b96fe2663bec3d0831bfe5c4c207d1bd4a0ead`; 1 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:cognitive/LocalConversationContext.kt:10,46-56`; `0e10beac455ca4418d513aa1a7ad57ee3db23452:learning/ContinuumCognitiveRuntime.kt:139-142`.
- Aktuelle Quelle: `learning/ContinuumCognitiveRuntime.kt:119-133`; `cognitive/AuthorizedConversationMemoryShadow.kt:7-11,39-123`.
- Grenze: Die historische Brücke lässt Umschreibungen ausdrücklich intern; standardmäßig SHADOW, Sessionreset nach 64 Turns. Kein belegter Effekt auf den gemeldeten Trainingsfehler.

### AuthorizedWebLearningReviewInbox

- Ursprungsdatei: [30fbef96182e](https://github.com/Ullmann27/device-ai/blob/30fbef96182ed83ee55a1e6ec732d1c533df4a51/app/src/main/java/at/heinz/deviceai/cognitive/AuthorizedWebLearningReviewInbox.kt), Blob `2461af7c7c4e54246729662e494e4eb4beba34f5`; 1 geprüfte Varianten.
- Historischer Caller: `30fbef96182ed83ee55a1e6ec732d1c533df4a51:AgentActivity.kt:321,530,5177-5217,5244-5268,5825-5827`.
- Aktuelle Quelle: `ApprovedWebResearchToolRunner.kt:8-33`; `AgentActivity.kt:8231-8249,8297-8318,9009-9021`.
- Grenze: Alt bevorzugt Live-Kandidaten und fällt bei leerer Liste auf das gespeicherte Dossier zurück; keine Listenunion. Eine gespeicherte Quellnotiz ist weder Owner-Fakt noch Gewichtsupdate.

### ConversationResearchIntent

- Ursprungsdatei: [ccbf0431d446](https://github.com/Ullmann27/device-ai/blob/ccbf0431d446789234fe8d5f0583cf9b8d40b8ba/app/src/main/java/at/heinz/deviceai/cognitive/ConversationResearchIntent.kt), Blob `08d2ce3ff886fa7fa9e9c9ea1c6a1ecdb24157ec`; 2 geprüfte Varianten.
- Historischer Caller: `ccbf0431d446789234fe8d5f0583cf9b8d40b8ba:AgentActivity.kt:2681-2684`.
- Aktuelle Quelle: `cognitive/NetworkTurnConstraint.kt:16-70`; `cognitive/ResearchNegationSignals.kt:5-30`; `AgentActivity.kt:5109-5115,8998-9007`.
- Grenze: Statischer Quellvergleich; keine neue Ausführung/Regression in diesem Audit. ResearchNegationSignals verarbeitet Polarität von Quellenbehauptungen und ersetzt diese Eigentümeranweisung nicht.

### LocalModelClaimAudit

- Ursprungsdatei: [29c1e1e1bef2](https://github.com/Ullmann27/device-ai/blob/29c1e1e1bef289cddf1e0c7854f2511923b93c0f/app/src/main/java/at/heinz/deviceai/cognitive/LocalModelClaimAudit.kt), Blob `ef43e9d429ea76ed09293d1388f36d7d7ed8560f`; 2 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:AgentActivity.kt:6341-6344`; `0e10beac455ca4418d513aa1a7ad57ee3db23452:cognitive/CognitiveTurnTrace.kt:13-16`.
- Aktuelle Quelle: `cognitive/CognitiveTurnTrace.kt:7-15`; `cognitive/LocalDialogueQualityGate.kt:5-10,31-104`; `AgentActivity.kt:9565`.
- Grenze: PR244 enthält die kleinere Vorstufe, PR245 die erweiterten Checks. Ein PASS beweist kein Entailment. Zusätzliche Laufzeit- und Fehlalarmwirkung müsste separat gemessen werden; keine Trainingsreparatur.

### MemoryQuestionResolver

- Ursprungsdatei: [29c1e1e1bef2](https://github.com/Ullmann27/device-ai/blob/29c1e1e1bef289cddf1e0c7854f2511923b93c0f/app/src/main/java/at/heinz/deviceai/cognitive/MemoryQuestionResolver.kt), Blob `972f3ffcab835e01f36a83f30888fb7d742b4382`; 1 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:learning/ContinuumBrain.kt:194-204`.
- Aktuelle Quelle: `learning/ContinuumBrain.kt:182-201`; `intelligence/ewm/Ewm1EvidenceWorkspace.kt:73,206,228,269`; `learning/ContinuumLearningCore.kt:725-759`.
- Grenze: Keine nachgewiesene fehlende sichtbare Antwortfähigkeit aus dem Dateinamen ableitbar. Der Resolver kennt Geschwisterrelationen, nicht zuverlässig das Geschlecht des gefundenen Gegenübers.

### MemoryTimeline

- Ursprungsdatei: [29c1e1e1bef2](https://github.com/Ullmann27/device-ai/blob/29c1e1e1bef289cddf1e0c7854f2511923b93c0f/app/src/main/java/at/heinz/deviceai/cognitive/MemoryTimeline.kt), Blob `03ff0e4fa9f276bfd0fde1a32807b53195cb783c`; 1 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:learning/ContinuumBrain.kt:194-204`.
- Aktuelle Quelle: `learning/ContinuumTemporalMemory.kt:13-30`; `learning/ContinuumTemporalMemorySpecialist.kt:5-62`; `AgentActivity.kt:5797,9414-9420`; `cognitive/OwnerStatementTimeline.kt:3-21`.
- Grenze: Alter Caller protokolliert nur SHADOW-Verdikt und verändert keine Antwort. Aktueller Specialist bleibt OFF/SHADOW-neutral und ersetzt nur im ausdrücklich aktivierten ON-Modus.

### ContinuumAnswerCorrection

- Ursprungsdatei: [c3b77c0093cf](https://github.com/Ullmann27/device-ai/blob/c3b77c0093cfddbc7193f805730dd6ba2eae7823/app/src/main/java/at/heinz/deviceai/learning/ContinuumAnswerCorrection.kt), Blob `44064174df8eb570df14f138d0b3a780a882860e`; 2 geprüfte Varianten.
- Historischer Caller: `4678a98f62e9b339cc723497e58839e6216b1c8f:AgentActivity.kt:938-940,2171-2247`; `4678a98f62e9b339cc723497e58839e6216b1c8f:learning/ContinuumLearningCore.kt:420-460`; `4678a98f62e9b339cc723497e58839e6216b1c8f:learning/ContinuumMemoryDialogue.kt:42-43`; `4678a98f62e9b339cc723497e58839e6216b1c8f:learning/ContinuumLearningExperience.kt:150-184`.
- Aktuelle Quelle: `learning/ContinuumLearningExperience.kt:15-21,102-123`; `learning/ContinuumLearningCore.kt:697-731`; `AgentActivity.kt:2096-2121,7984`.
- Grenze: Historischer Lookup übergeht beim Treffer normales Ranking. Dialog-/Core-/Store-/UI-Änderungen sind wesentlich; aktuelle Owner-Fakt-Schutzmaßnahmen müssen erhalten bleiben. Kein Gewichtslernen und keine belegte Gerätefehlerursache.

### ContinuumCdx1ChatShadow

- Ursprungsdatei: [fe7292685d4f](https://github.com/Ullmann27/device-ai/blob/fe7292685d4f4105d59eed297079484df2fc157d/app/src/main/java/at/heinz/deviceai/learning/ContinuumCdx1ChatShadow.kt), Blob `a05ff9d1667f95775c673411246a14358b881c50`; 1 geprüfte Varianten.
- Historischer Caller: `fe7292685d4f4105d59eed297079484df2fc157d:AgentActivity.kt:2304-2307,2758-2762`.
- Aktuelle Quelle: `learning/ContinuumDecoderBridge.kt:5-14,123-145,231-246`; `learning/ContinuumCognitiveRuntime.kt:90-137`.
- Grenze: Kein Trainingsaufruf in diesem Adapter. 160 UTF-8-Byte-Evidenz/Frage, SHADOW, kein Weiterreichen an Gemma oder Gerätewerkzeuge, keine Chatpromotion.

### NeuralTutorBatchHint

- Ursprungsdatei: [30fbef96182e](https://github.com/Ullmann27/device-ai/blob/30fbef96182ed83ee55a1e6ec732d1c533df4a51/app/src/main/java/at/heinz/deviceai/learning/NeuralTutorBatchHint.kt), Blob `0b9bff3cae34dfbb9812d6c92df97b5d90f0006a`; 1 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:AgentActivity.kt:2608-2620`.
- Aktuelle Quelle: `learning/NeuralLearningCoach.kt:23-33,36-90`; `AgentActivity.kt:5203-5207,5348-5351`; `cognitive/OwnerChatIntentRouter.kt:18-30`.
- Grenze: Alte Integration verhindert, dass Trainingsbeispiele als Geräteanfrage behandelt werden. Erfasst keine beliebigen Batchformate und repariert keinen Scheduler-/Speicherfehler.

### PersonalLearningPreviewCopy

- Ursprungsdatei: [6a153f1334d3](https://github.com/Ullmann27/device-ai/blob/6a153f1334d3762eaf2c103a3f5f1b637fe55325/app/src/main/java/at/heinz/deviceai/learning/PersonalLearningPreviewCopy.kt), Blob `f5acc1c98044c0d4b9596cff817ce30184429d19`; 1 geprüfte Varianten.
- Historischer Caller: `6a153f1334d3762eaf2c103a3f5f1b637fe55325:AgentActivity.kt:3529-3532`.
- Aktuelle Quelle: `AgentActivity.kt:6387-6428`; `learning/PersonalLearningTransfer.kt`.
- Grenze: Reine Darstellungsverbesserung; belegt keinen Datenverlust und führt keine Wiederherstellung aus.

### InteractiveDialogueContext

- Ursprungsdatei: [fe838399ce31](https://github.com/Ullmann27/device-ai/blob/fe838399ce31f266594d331886f1b2fab0ea3925/app/src/main/java/at/heinz/deviceai/learning/interactive/InteractiveDialogueContext.kt), Blob `db42c1c72f39cc3b7b130ea0aa10fa2e75bde611`; 1 geprüfte Varianten.
- Historischer Caller: `Ursprungsblob db42c1c72f39cc3b7b130ea0aa10fa2e75bde611:10-17,30-50,53-98`.
- Aktuelle Quelle: `cognitive/LocalConversationContext.kt:21-48,65-72,99-125,149-175`; `learning/ContinuumLearningCore.kt:911-915,931,960`; `learning/context/Ctx3Context.kt`.
- Grenze: Original bezeichnet sich ausdrücklich als Forschungsadapter ohne Produktionscaller. Ctx3Entity/ClaimGraph sind Voraussetzungen; keine Fakten- oder Freigabeautorität. Größerer Kontext ist kein Qualitätsbeweis.

### InteractiveResearchRouting

- Ursprungsdatei: [fe838399ce31](https://github.com/Ullmann27/device-ai/blob/fe838399ce31f266594d331886f1b2fab0ea3925/app/src/main/java/at/heinz/deviceai/learning/interactive/InteractiveResearchRouting.kt), Blob `4048215360cee68e5661fbce30aa7f1b7298858b`; 1 geprüfte Varianten.
- Historischer Caller: `Ursprungsblob 4048215360cee68e5661fbce30aa7f1b7298858b:7-23`.
- Aktuelle Quelle: `learning/ContinuumArchiveAnswerPolicy.kt`; `cognitive/CognitiveDecisionBridge.kt`; `AgentActivity.kt:5520-5526,5734-5830`.
- Grenze: höchstens 20 Archivtreffer; Archivbeobachtung bleibt ungeprüfte öffentliche Quelle. Keine Autorisierung, kein Training, kein Speicherzugriff im Wrapper.

### ContinuumSynapseZeroShadow

- Ursprungsdatei: [2b58ed17861d](https://github.com/Ullmann27/device-ai/blob/2b58ed17861de986d97c756be4205993e1233f32/app/src/main/java/at/heinz/deviceai/learning/synapse/ContinuumSynapseZeroShadow.kt), Blob `f17a590b3e3f520d623d6a3fd09a1dc5b3cea715`; 1 geprüfte Varianten.
- Historischer Caller: `Ursprungsblob f17a590b3e3f520d623d6a3fd09a1dc5b3cea715:7-28,79-129`; `docs/research/omega/CONTINUUM-INFRASTRUCTURE-AUDIT-20261025.md:59`.
- Aktuelle Quelle: `cognitive/CertifiedLocationReflex.kt:8-39,58-109`; `AgentActivity.kt:5455-5464`; `learning/ContinuumMemoryDialogue.kt:102`.
- Grenze: Synthetisches engeres Schema ist keine allgemeine Denk-/Trainingsfähigkeit. Keine im Audit ausgeführte Messung.

### SynapseContrastiveRoleProbe

- Ursprungsdatei: [2b58ed17861d](https://github.com/Ullmann27/device-ai/blob/2b58ed17861de986d97c756be4205993e1233f32/app/src/main/java/at/heinz/deviceai/learning/synapse/SynapseContrastiveRoleProbe.kt), Blob `7664baf884a1ed78845b59de43c070201f61a87d`; 1 geprüfte Varianten.
- Historischer Caller: `Ursprungsblob 7664baf884a1ed78845b59de43c070201f61a87d:9-18,33-75`; `docs/research/omega/CONTINUUM-INFRASTRUCTURE-AUDIT-20261025.md:59`.
- Aktuelle Quelle: `learning/ContinuumCognitiveRuntime.kt:93-115`; `learning/Cre1PromotionGate.kt`.
- Grenze: Die eigene Netzwerkbewertung kann ohne unabhängigen Beleg keine Antwort autorisieren. In diesem Audit ausdrücklich kein Training.

### SynapseV2Reasoner

- Ursprungsdatei: [79a3079efb1d](https://github.com/Ullmann27/device-ai/blob/79a3079efb1de80e0fd648e2192882bc3d4c1ed4/app/src/main/java/at/heinz/deviceai/learning/synapse/SynapseV2Reasoner.kt), Blob `a0a41763697fb2ff6b24b293669aa99880545012`; 1 geprüfte Varianten.
- Historischer Caller: `Ursprungsblob a0a41763697fb2ff6b24b293669aa99880545012:11-26,385-426,487`; `docs/research/omega/CONTINUUM-INFRASTRUCTURE-AUDIT-20261025.md:60`.
- Aktuelle Quelle: `learning/span/EvidenceBoundNaturalLocationProbe.kt`; `learning/ContinuumCognitiveRuntime.kt:93-115`.
- Grenze: Vorhandener Audit dokumentiert einen negativen Transferbefund; hier kein neuer Benchmark und keine Qualitätsbehauptung. Einen hohen Modellscoresatz nicht mit Belegwahrheit verwechseln.

### LocalModelImportPreset

- Ursprungsdatei: [febffb293f12](https://github.com/Ullmann27/device-ai/blob/febffb293f12702df5714ee20c8fb66471b493b9/app/src/main/java/at/heinz/deviceai/localmodel/LocalModelImportPreset.kt), Blob `7db4cfa23e33684593bb1f725fc97b9651f9b06b`; 1 geprüfte Varianten.
- Historischer Caller: `febffb293f12702df5714ee20c8fb66471b493b9:AgentActivity.kt:6255-6258`.
- Aktuelle Quelle: `ContinuumSelectedModels.kt:10-30`; `AgentActivity.kt:10746-10748,11035`.
- Grenze: Katalogmetadaten beweisen weder geladenes Modell noch Antwortqualität; keine privaten Modellbytes wurden gelesen.

### ComputeAllocationPolicy

- Ursprungsdatei: [7924ba079396](https://github.com/Ullmann27/device-ai/blob/7924ba0793963dd84eef61517359a743071829bf/app/src/main/java/at/heinz/deviceai/research/argon4/ComputeAllocationPolicy.kt), Blob `4628ba00cb98e4c6abdc3e637837dc4ca1fc8aa0`; 1 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:cognitive/ChatReasoningRouter.kt:31-33,43-62`.
- Aktuelle Quelle: `cognitive/ChatReasoningRouter.kt:11-25,37-87`; `AgentActivity.kt:9345`.
- Grenze: Historischer Caller setzt coverage=0, claimFlags=0, unresolvedReference=false und internetAllowed=false; keine vorhandene adaptive thermische Lösung. Zeit-/Joulewerte sind Annahmen des Prototyps, keine Fold-Messung.

### DialogStateKernel

- Ursprungsdatei: [29c1e1e1bef2](https://github.com/Ullmann27/device-ai/blob/29c1e1e1bef289cddf1e0c7854f2511923b93c0f/app/src/main/java/at/heinz/deviceai/research/argon4/DialogStateKernel.kt), Blob `85c816a7579af910f66d1c00cd5f0a51ecd55060`; 2 geprüfte Varianten.
- Historischer Caller: `0e10beac455ca4418d513aa1a7ad57ee3db23452:cognitive/LocalConversationContext.kt:10-11,46-56`; `Ursprungsblob 99b96fe2663bec3d0831bfe5c4c207d1bd4a0ead:Argon4ConversationShadow`.
- Aktuelle Quelle: `cognitive/LocalConversationContext.kt:21-48,65-72,99-125`; `cognitive/LocalConversationFollowUpPolicy.kt`; `cognitive/OwnerStatementTimeline.kt:3-21`.
- Grenze: Prototyp kann Modellantwort-Erwähnungen als Diskurs aufnehmen, nicht als Wahrheit. Default maxEntities=12; heuristische Wort-/Gender-/Zeitregeln. Kein belegtes Allzweckgedächtnis.

### JustifiedRelationStore

- Ursprungsdatei: [29c1e1e1bef2](https://github.com/Ullmann27/device-ai/blob/29c1e1e1bef289cddf1e0c7854f2511923b93c0f/app/src/main/java/at/heinz/deviceai/research/argon4/JustifiedRelationStore.kt), Blob `fd5799baaa3c91c91fba02a069ca9b6568581489`; 2 geprüfte Varianten.
- Historischer Caller: `Ursprungsblob 99b96fe2663bec3d0831bfe5c4c207d1bd4a0ead:Argon4EvidenceShadow`; `Ursprungsblob fd5799baaa3c91c91fba02a069ca9b6568581489:15,35,58-95,112-143`.
- Aktuelle Quelle: `learning/ContinuumLearningCore.kt:725-759`; `learning/ContinuumTemporalMemory.kt:13-30`; `research/neuro/ReviewedEvidenceGraph.kt:11,87`; `intelligence/ewm/Ewm1EvidenceWorkspace.kt:269`.
- Grenze: Alter Adapter begrenzt auf 3 Fakten/maxDerived32/depth2. Allgemeiner Store default2000/depth3 rechnet bei jedem assert neu; matches materialisiert Zwischenjoins ohne eigenes Arbeitslimit. Kein risikofreier Anschluss bei gemeldeter Hitze.

## Reproduzierbare Artefakte

- `matrix.json`: komplette strukturierte Matrix einschließlich aller Ursprungsrefs und Blobvarianten.
- `original-sources.json` und `originals/`: exakte per Git cat-file gelesene öffentliche Ursprungsblobs.
- `historical-callers.json`, `historical-callers/`, `historical-caller-excerpts.json`: ursprüngliche Aufrufstellen mit SHA/Blob/Pfad.
- `variants/`: Diffs aller fünf mehrfach vorhandenen Ursprungsdateien.
- `source-state.json`: SHA/Tree/Clean-Status und Ausführungsgrenzen.

Alle genannten Produktpfade sind relativ zu `app/src/main/java/at/heinz/deviceai/`. Die Quellen wurden statisch gelesen; vorhandene alte Benchmark-/Auditbeschreibungen sind als solche benannt und nicht als frische Testergebnisse dargestellt.
