# SOL 6.1 MAX — CONTINUUM J.A.R.V.I.S. RESEARCH → RUNTIME (2026-10-27)

**Rolle:** Ausführender Entwicklungsleiter. **Ziel:** endlich ein großer, tatsächlich nachweisbarer Sprung bei den Fähigkeiten der bestehenden Android-KI, durch Verbindung bereits entwickelter Komponenten, NICHT durch mehr isolierte Demonstrationen. **APK-RELEASE AUSDRÜCKLICH VERSCHOBEN.**

## 0. Nicht verhandelbar: offenen Code erhalten

Repo `Ullmann27/device-ai`. Arbeite ausschließlich an Device AI Continuum, im bestehenden Hauptapp-Quellbaum, kein zweites Android-Projekt. Ohne Eigentümerfreigabe kein Release, keine APK, kein `main`/Production-Merge, keine Änderung offizieller SWB1-Gewichte, keine falschen Erfolgsaussagen. Erhalte Quellberechtigungen, normale UTF-8-Dateien, vollständige commits, offene Draft-PRs, Revertpfad, kein Force-Push/Löschen/Locking. Vor Ende jeder Ausführung auch bei Budget-Ende Quellen, Prüfbelege, genaue HEAD-SHA, Fehlstände und nächsten nativen Befehl vollständig nach GitHub pushen. Keine privaten Workspace-only Erfolge behaupten.

## 1. START: Den Quellen-Audit als technische Ausgangsbasis lesen

- Audit `docs/research/omega/CONTINUUM-JARVIS-RESEARCH-CONNECTION-AUDIT-20261027.md` lesen; alle Git-HEADs neu prüfen, falls sie seit dem Audit bewegt wurden.
- Aktuelle Ausgangsbasis: Draft PR #179 / `integration/continuum-autonomy-learning-20261027` HEAD `7ca4702d1012a5dceb6c2bb8efebe8c8534b0ff7`; PR #180 `chatgpt/continuum-checked-shell-bounds-20261027` getrennt und noch REVIEW ONLY. Ergänzende Forschungs-PRs #143/145/151/152/163/167/168/169/174/175/176/177/178 sowie fehlende ältere Branches Synapse v2 und CDXA1.
- Audit-Ergebnis: Sourcecode der meisten alten Studien ist **schon** in PR #179 vorhanden; bloße Existenz ist keine echte Aktivierung. Die 19 fehlenden Forschungsdateien aus PR #174 und 10 fehlenden Synapse-v2-Quellen/Testdateien sind KEINE automatisch mergefähigen Verbesserungen. PR #174 dialogische v1 scheiterte an 4 falschen Bezügen in 8 adversarialen Korrekturen. Synapse-v2 wurde als Shadow erforscht und darf nur mit neuen Sicherheits-/Generalisierungsbeweisen an den Chat.
- SWB1: 12 Runden / 2 akzeptiert; SHA256 `5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1`. SWB2-Benchmark 26/26/27 von 60 vs 26/60 Ausgangsmodell => REJECT. CDX1 in der dokumentierten Forschungsprüfung 0/36 exakte Antworten. CDXA1 im eigenen Resume UNTRAINED/NOT_RUN Quality, nur numerische 7/7; keine Gewichte. Kein künstliches „erlernt“ behaupten.

## 2. Priorität P0: Behebe die ECHTE Anschlusslücke des kognitiven Hauptchats

Prüfe die originale `AgentActivity.kt` (~ Zeilen 2490–2610): `CognitiveDecisionBridge(ContinuumCognitiveRuntime(...), null)` — die optionale Modell-Reasoning-Brücke bekommt ausdrücklich KEINEN Adapter. Die andere, separate `LocalFirstPolicy`/`LocalModelRuntime`-Route existiert bereits. Stelle in einem **vorher/nachher messbaren, reversiblen** Design sicher, dass beide Wege nicht gegeneinander arbeiten:

1. Baue eine einzige kompakte, klar getestete Orchestrierungs-Schnittstelle für Nutzerfrage, Gesprächssitzung, autorisierte Gedächtnisbelege, lokales Modell und erlaubte Toolaktionen. Bestehende `CognitiveDecisionBridge`, `LocalModelReasoningAdapter`, `LocalFirstPolicy`, `BoundedAgentOrchestrator`, `ToolGate` und ihre Stores wiederverwenden. KEINEN parallelen Chatkern, keine zweite Datenbank, keine neuen allmächtigen Tools bauen.
2. Persönliche bestätigte Besitzerfakten bleiben **wörtlich und quellgebunden**. Das vorhandene `preservesEvidenceAnchors` ist absichtlich restriktiv; nicht abschalten, um frei erfundene Paraphrasen als bestätigte Fakten anzuzeigen. Baue eine **separat gekennzeichnete generative Erklärung/Analyse** nur mit exakten Beleg-IDs und Zitaten; neue LLM-Aussagen sind Interpretation, niemals automatisch OWNER_CONFIRMED. Ohne sicheren Beleg: Unsicherheit / Rückfrage / freie Wissensantwort mit klarer Herkunft, statt fremde Besitzererinnerungen auszudenken.
3. Der lokale LLM-Lauf muss die **tatsächliche vorhandene LiteRT-LM Runtime** nutzen, sofern das Besitzergerät/Host-Modell verfügbar und durch echte Modell-ID/Hash nachgewiesen ist. Fehlt ein Modell im Testhost, dann `MODEL_NOT_READY / NOT_RUN` und separate deterministische Probe; niemals einen String-Mock als neuronalen Intelligenzerfolg zählen.
4. Sesssion-/CTX3-Daten bleiben gescopt und nachprüfbar; `Ctx3Shadow` ist nur beobachtend. Prüfe mehrdeutige Pronomina, Themenwechsel, negative Erwähnungen, falsche Memory-IDs, Widerruf nach fünf Dialogzügen und App-Neustart. Keine Rückgabe falscher Belege, auch nicht nach LLM-Ausgabe.

**Abnahme P0:** Mindestens drei vollständig nachgewiesene Dialogklassen mit Referenzbaseline: (A) bestätigte Besitzerinformation wortgetreu, (B) neue mehrschrittige Sachfrage mit lokalem generativem Modell (wenn echt verfügbar), (C) unklarer/nicht gedeckter persönlicher Fakt mit korrekter Rückfrage statt Halluzination. Sämtliche Tests mit exaktem Modell-/Quellhash und bekannten Latenzen; echte Modellinferenz separat von Mocks ausweisen.

## 3. Priorität P1: Der erste wirkliche mehrstufige J.A.R.V.I.S.-Agent

Die Hauptapp hat bereits **einen** deterministischen Werkzeugpfad: `ReadOnlyToolPlan` → `BoundedAgentOrchestrator` → `ToolGate` → echte `get_storage_info` Messung → wiederauffindbare Episode, mit `maxToolCalls=1`, `maxRetries=0`. Das ist gut, aber kein frei planender Assistent.

Baue darauf eine streng typisierte, begrenzte Mehrschritt-Planung mit 2 bis maximal 3 ausschließlich *bestehenden* READ_ONLY-Werkzeugen und *echter* Nachprüfung der Postconditions auf. Freie Modelltexte dürfen keine Toolrechte verleihen. Explizite Einmal-/Session-Freigabe, Risiko- und Tool-ID-Bindung, Deadline/Budget, Anti-Replay, Annahme ausschließlich frischer typisierter Outputs, keine positiven Lernerfahrungen für Timeout, Teiloutput, falsche Exitcodes, Abbruch oder zu spät eintreffende Antworten.

Implementiere einen echten E2E-Gegenversuch: Bitte um Statusprüfung → gespeicherte Fakten unterscheiden → begrenzten Plan erzeugen → erlaubte reale Messung(en) → Endergebnis und Beleg-Hash → korrigierbare Episode → Neustart → dieselbe Aufgabe besser ausführen. Bei nicht verfügbaren Werkzeugen klare Nichtausführung statt fingierter Resultate. Kein privilegiertes oder destruktives Tool in dieser Phase. PR #180 Binder/Callback-Sicherheitsänderungen vor jeder erweiterten Geräteaktion unabhängig prüfen.

**Abnahme P1:** zwei unterschiedliche echte, erlaubte Werkzeugaktionen sofern bestehende ToolContracts dies abdecken; sonst eine reale Aktion plus ein legitimer reiner lokaler Leseschritt, explizit so deklarieren. Mindestens ein nachgewiesener durch Erinnerung verbesserter späterer Lauf, kein Fake-Replay. 0 positive Lernerfahrungen aus abgelehnten, duplizierten oder mehrdeutigen Werkzeug-Ergebnissen.

## 4. Priorität P2: Forschungsbausteine gezielt retten, NICHT blind alles mergen

Führe die folgenden Kandidaten in **exakt dieser Reihenfolge** als A/B-Forschungsadapter mit echten Baselines und vorregistrierten neuen Fallgruppen durch:

- **CTX3**: derzeit `Ctx3Shadow.evaluate` bei Memory-Reply aktiv, aber read-only/kein Antwortreplacement. Teste scoped mehrturniges Referenzverstehen für neue Fragen. Nur eine nachweislich sicherere *Clarify/Scope* Entscheidung darf später mit dem Hauptchat verbunden werden; nie eine unbewiesene Faktzuweisung.
- **CRE1/COIN-WM / evidenzgebundener Belegleser**: CRE1-Rerank bereits gated, COIN-WM-Shadow vorhanden. Kandidaten nur auf **präziser Entitäten- und Relationsbindung** vergleichen (Graz vs nicht Graz, Besitzer-ID A vs B). Bewahre bewiesene Quellen-/Datenherkunft und Abstention. Synapse-v2 aus separatem Branch als möglicher zusätzlicher Span-Reader **nur nach** unabhängiger Messung nutzen; in diesem Audit fehlende 10 Kotlin-Dateien sind kein Grund für ungeprüfte Promotion.
- **CDX1 / CDXA1**: CDX1 bisher kein brauchbares Generierungsmodell. CDXA1 ist UNTRAINED: wenn weiterer Forschungswert, das bereits eingefrorene Protokoll ohne nachträgliche Holdout-Leckage nativ fertig trainieren und echte negative Gegenproben prüfen. Wenn kein messbarer Vorteil gegenüber dem produktiven Retrieval/LLM, nicht integrieren.
- **VirtualModelFabric**: vorhandener `VirtualModelFabricSnapshotExporter` deklariert `executable:false`. Kein Beweis laufender verteilten KI. Nutze optional echte signierte ComputeRegistry nur für gepinnte lokale Offline-Inferenz mit Besitzereinwilligung; erfinde keine Remote-GPU, Modellgewichte oder kostenlose Server.
- **PR #174 Interactive Dialogue**: der verworfene V1-Kandidat darf NICHT pauschal portiert werden. Der sicher isolierte Feedbackmechanismus aus PR175/176 bleibt; nur begrenzt getestete neue Referenzauflösung mit frisch versiegeltem Vergleich.

**Erfolg P2:** neue Aufgaben korrekt beantwortet ohne Verlust bei negierten, negativen, mehrdeutigen, multi-entity und Cross-Session-Kontrollen. Reduziert ein Kandidat false positives nicht, bleibt er SHADOW/REJECT. Die bereits offen gelesenen Synapse-592-, Dialog-504-, SWB2-60- und SWB1-52-Fälle sind NICHT mehr unberührte Holdouts.

## 5. Priorität P3: Erstmals echte lokale Code-Reparatur (nach dem Chat- und Agenten-Meilenstein)

Die vorhandenen CCC1/VH-SR-/Repair-/Model-Fabric-Module und lokalen Worker dürfen NICHT nur weitere Code-„Probeberichte“ erzeugen. Baue einen end-to-end Reparaturversuch mit einem **neuen, isolierten, tatsächlich fehlerhaften** Entwicklungsworktree:

1. Erkenne einen echten Rot-Test mit exakter Commit-, Dateipfad- und Testfallbindung.
2. Verwende einen tatsächlich verfügbaren lokalen generativen Coding-Provider; wenn nicht installiert, markiere MODEL_NOT_READY und trenne den deterministischen Referenzpatch vom Modellkandidaten.
3. Lasse Kandidatenquellcode nur in Sandbox/isoliertem Branch anwenden; untrusted Outputs niemals auf Host privilegiert ausführen.
4. Verlange CCC1 oder den bestehenden unabhängigen Reparaturprüfer: RED vor Kandidat, GREEN im Kandidat, frischer GREEN auf unverändertem geprüften Head, gleiche Testinventare, echte Logs/Hashes, Review-Gate.
5. Gib **nur** einen lesbaren Draft-PR/Patch für Besitzerfreigabe aus. Keine automatische Merge-/Release-/Trainingspromotion.

**Abnahme P3:** mindestens ein nachweislich von einem realen lokalen Modell generierter Patch, der die unabhängigen Gates besteht; ohne verfügbares Modell ist der Codepfad lediglich vorbereitet und der Forschungsfortschritt korrekt als NOT_RUN bezeichnet.

## 6. Qualitätsmessung und Abgabe: größere Erfolge statt mehr Dateizahlen

Vor jeder Änderung einen eingefrorenen Referenzstand und Testplan anlegen. Messe getrennt:

- mehrturniges Referenzverständnis inkl. Korrektur/Widerruf und neue unbekannte Entitäten;
- korrekte grounded Fakten, falsche Memory-IDs, Halluzinationen, unangemessenes Gewissheitsniveau;
- echte Task-Completion mit Toolbelegen, Fehler-/Timeout-Quote, keine unautorisierte Aktion, 0 falsche SUCCESS-Episoden;
- p50/p95 lokale Modell- und Toollatenz, CPU/RAM/Batterieschutz auf tatsächlich verfügbarer Hardware;
- Coding pass@1 getrennt nach vorgegebenem Operator, echtem neuronalen Patch, independente Verifikation;
- echte Vorteile eines neuen Kandidaten gegenüber unveränderter PR179-Baseline auf neuen nach Domäne/Quelle gesplitteten Aufgaben.

Ergebnisse gelten NUR mit exakten source-HEAD SHA, Checkpoint-/Modellhash, nativen Befehlen, Exitcodes, PASS/FAIL/SKIP/NOT_RUN, Rohprotokollen und unabhängiger Bewertungsinstanz. Keine künstliche 100%-Zahl durch triviale Mocktests. Keine Trainingsexperimente für live Besitzerfakten ohne Opt-in. Vorhandene SWB1-Gewichte und 2/12 offiziell unverändert.

## 7. Arbeitsteilung und harte Endbedingung

**Sol 6.1 MAX ist ausführender Integrator:** arbeite tatsächlich im eigenen neuen branch auf der geprüften Basis; nur kleine, rücknehmbare Commits; PR #179 und #180 nicht parallel überschreiben. **Astra hoher Denkmodus ist unabhängiger Adversarial-Reviewer** von falschen Entitäten, Belegautorität, Prompt Injection, Replay, Modellgerüchten und Race Conditions. Niemals einen fehlenden externen Review als erfolgt behaupten.

Direkter erster Befehl: `git status`, exact HEAD, dann `rg -n 'CognitiveDecisionBridge|LocalFirstPolicy|ReadOnlyToolPlan|Ctx3Shadow|experimentalPairedContinuation|VirtualModelFabricSnapshotExporter' app/src/main` und bestehende gezielte native Tests. Implementiere dann **den P0-Dialogadapter mit strengem Belegschutz** und prüfe drei reale Dialogpfade. DANACH P1 echten sicheren Mehrschrittplan. Nur dann P2 Forschungsanbindung oder P3 Coding. Keine neue APK in dieser Mission.

Die Übergabe muss offen bleiben: Branch/HEAD/Dateipfade, Tests, fehlende Android-/Fold7-/LLM-Gates, Eingriffsrisiken und unmittelbar ausführbarer Folgebefehl. Der beste sichtbare Fortschritt ist eine wirklich neue ausgeführte Fähigkeit, nicht eine weitere große PR-Statistik.
