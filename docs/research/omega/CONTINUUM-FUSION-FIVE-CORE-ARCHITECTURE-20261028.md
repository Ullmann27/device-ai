# Continuum Fusion – fünf Kerne, ein autoritativer Pfad (28.10.2026)

## Nachweisgrenze und Basis

Offizielles privates Repository `Ullmann27/device-ai`, isolierter Draft-PR #203 auf PR #198 → #197 → #196. Ausgangs-HEAD #198: `34acc378ac77e915b8d322bf02e46af77288bcf0`. Experimente EXP-00/02/04/05/06/07/09/11, verbindlicher Vertrag #41. Die 15 Quellen sind bereits vollständig in `docs/research/CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md` registriert. **Keine Fremdquellen oder Modellgewichte wurden in #203 importiert.** Upstream-SHAs, Dritt-Lizenzen, ARM64-Binärverträglichkeit und Leistungsdaten sind daher noch kein Übernahmebeleg.

## A – Modell / Kognition

Autorität: `app/src/main/java/at/heinz/deviceai/cognitive/CognitiveDecisionBridge.kt`, `app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt`, `ContinuumLearningCore.kt`, bestehender Gemma/LiteRT-LM-Pfad. CRE1 kann nur mit `Cre1PromotionGate.readQualified` in das bestehende, kandidatenidentische Reranking eintreten; die Brücke prüft Modellvorschläge gegen die Beleganker. R-02 (Jandal) und R-14 (LiteRT-LM) bleiben **AUDIT_REQUIRED**: keine zweite Engine, keine neue Modellpromotion.

## B – ein Gedächtnis / zwei klar getrennte Sichten

Autoritatives Produktivgedächtnis: `learning/ContinuumLearningCore.kt` → `learning/ContinuumMemoryDialogue.kt` (bestätigte, nicht widerrufene Ledger-Einträge). `replyWithEvidenceInConversation()` liest unter `CORE_LOCK` einen Snapshot, selektiert SWB1-Belege, lässt CRE1 nur eine exakte Permutation dieser Kandidaten versuchen und rekonstruiert die gewählten IDs aus dem **gleichen** Snapshot. Der vorhandene `cognitive/HybridMemoryRetriever.kt` ist ein *weiterer Ranking-Algorithmus*, nicht die Quelle neuer Besitzerfakten. `MemoryEcology.kt` / `ProofCarryingMemory.kt` bleiben die vorhandenen Forschungs-/Provenienzverträge.

**Neuer vertikaler Forschungsanschluss:** `cognitive/AuthorizedConversationMemoryShadow.kt` wird nach der autoritativen Dialogauswahl in `ContinuumCognitiveRuntime.reply()` aufgerufen. Eingang ausschließlich `turn.memoryIds`, `evidenceHits` und bereits lokal berechnete CRE1-Scores **desselben Zuges**. Die Beobachtung akzeptiert nur identische ID-Reihenfolge, maximal drei Belege, maximal 4096 Zeichen je Beleg, zwei explizite Belegarten und vollständige CRE1-Score-Identität. Fehlen ausgewählte Belege nach der bisherigen Wiederauflösung (z. B. bei Rechtschreibkorrektur), wird **nicht** heimlich ergänzt: Status `REJECTED_EVIDENCE_IDENTITY`.

Die nur für die Dauer des Funktionsaufrufs erzeugten `MemoryRecord`-Objekte erhalten `DEVICE_LOCAL_ONLY`, `UNVERIFIED` und keine erfundenen Quellenreferenzen oder Aktualitätszeitstempel. Das bedeutet **nicht**, dass zuvor vom Besitzer bestätigte Fakten zurückgestuft werden: Die autoritative Ledger-Klasse wird nicht verändert. Der bestehende `HybridMemoryRetriever.retrieve()` vergleicht rein lexikalisch und mit dem bereits berechneten lokalen CRE1-Score; kein neuer Encoder, keine externe Callback-Injektion, kein Datenbankzugriff, keine Modellanfrage. Ausgabe: nur Status, Zähler und boolesche Rangindikatoren; **keine Inhalte, URLs, Fragen oder IDs**. Antwort, Prompt, CRE1-Promotion und Owner-Gates bleiben unverändert. Ausnahmefehler des Shadows sind `EVALUATION_FAILED` und blockieren die ursprüngliche Antwort nicht.

R-03 (LocalChat) liefert die Evaluationsidee, R-04 (Mem0) Faktenpflege als späteres Research-Gate, R-05 (A-MEM) nur hypothetische Graphkanten, R-06 (sqlite-vec) nur optionalen lokalen Index; **keine dieser externen Implementierungen ist importiert**. R-07 und R-13 (LongMemEval-V2/LongMemEval) sind über `MemoryRetrievalResearchGate` und `MemoryResearchShadowComparator` als synthetische DEV/frozen-HOLDOUT-Methodik berücksichtigt. Der neue Test `AuthorizedConversationMemoryShadowTest` ruft beide existierenden Gates auf; reale Besitzerbelege dürfen nicht ungeprüft als goldene Holdout-Daten verwendet werden.

**Bewusste Grenze:** Die neue Beobachtung bewertet Rangänderungen *innerhalb bereits ausgewählter* Belege. Sie kann dadurch noch keine Recall-Verbesserung für bisher **nicht** gefundene Erinnerungen beweisen. Erst eine unabhängig freigegebene Evaluation mit vollständig autorisiertem Korpus und gefrorenem Holdout kann eine Erweiterung der produktiven Auswahl rechtfertigen.

## C – Skills / Handlungen

Autorität: `app/src/main/java/at/heinz/deviceai/ToolRegistry.kt` und `DeviceToolExecutor.kt` mit bestehendem ToolGate/Owner-Approval-Pfad. R-01 (Google Gallery) und R-02 (Jandal) sind ausschließlich Inspiration für zukünftige versionierte deklarative Manifeste; **PLANNED**. Eine Skill-Beschreibung darf niemals Shell-/Netzwerk-/Dateirechte oder Besitzerzustimmung erzeugen. Vor Aktivierung: ToolGate-Negativfälle für fehlende/widerrufene Rechte, unbekannte Tools, Parameter, Injection und unzulässige Datenabflüsse. Der M4-Memory-Shadow benötigt keine Skills und greift ToolGate nicht an.

## D – CCC1 / lokaler Entwicklerzyklus

Autorität: bestehender CCC1-/CEGIS-Repair-Journal- und Teamleiterpfad, `scripts/continuum-teamleader-termux.sh` mit `.github/continuum/teamleader-cli.mjs`. R-08 (mini-SWE-agent): kleine verifizierte Schritte; R-09 (Aider): begrenzte Repo-Map; R-10 (OpenHands): Workspace-Isolation, ohne Cloud-/Containerpflicht. **PLANNED** bis echte Reproducer, frischer Verifier, Worktree-Gates, Patch-Hash und Besitzerreview nachgewiesen sind. Kein zweiter Scheduler und keine Änderungen an `main`.

## E – Evaluation / lokale Android-Abnahme

Baseline = unveränderte SWB1-/CRE1-Antwort und aktueller `HybridMemoryRetriever`. `MemoryRetrievalResearchGate` liefert Top-1/Abstinenz, Hit@K, MRR und Laufzeit. `MemoryResearchShadowComparator` trennt DEV und fünf Fähigkeiten im eingefrorenen Holdout, fordert Einzelprobe-Nichtverschlechterung und kann nur `eligibleForOwnerReview` melden, **keine Aktivierung**. Fehlende Vergleichsdaten = **NOT_RUN**, nicht „besser“.

R-11 (Termux ARM64 Build Tools) und R-12 (ARM64 Android SDK Tools) sind Kandidaten für die bestehende `scripts/continuum-local-owner-apk.sh` / `scripts/continuum-local-mainapp-qa.sh`-Toolchain. Aktueller Gradle-Code: SDK36, JDK17-Owner-Buildvertrag, Gradle8.11.1 laut bestehender Buildübergabe; Paket `at.heinz.deviceai.mentorpaired` bei explizitem Owner/QA-Flag, Code26 = 0.3.25. **Keine lokale ARM64- oder Android-Abnahme in PR #203**; Original-P12 weiterhin privat und nicht vorhanden. R-15 (r/LocalLLaMA, r/termux, Google AI Developer Forum) bleibt unverbindlicher Rechercheeingang, kein Code.

## Abnahme und Reihenfolge

1. **SOURCE_INTEGRATED / RESEARCH_SHADOW**: begrenzter Observer im echten `ContinuumCognitiveRuntime.reply()`-Aufrufpfad, Kotlin-Negativtests und Runtime-Revocationstest eingecheckt. Die gezielten JS-Quellstrukturprüfungen sind **keine Kotlin-JUnit-Ausführung**.
2. **NOT_RUN / UNAVAILABLE in dieser Umgebung**: `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.AuthorizedConversationMemoryShadowTest' --tests 'at.heinz.deviceai.learning.ContinuumCognitiveRuntimeTest'`; anschließend `:app:compileDebugKotlin :app:lintDebug`, volle Android-Regressionssuite und unveränderte SWB1-/CRE1-Antwortvergleichsdaten. Auf einem autorisierten lokalen Checkout mit JDK17/SDK36 ausführen, keine ungeprüfte GitHub-Actions- oder Render-Kostenpflicht.
3. Nur bei real grünem ersten Schritt: Skill-Manifest (R-01/02, Issue #200), dann CCC1-Repro-/Verifier-Verbesserung (R-08/09/10, Issue #201), dann native ARM64-Toolchain-Abnahme (R-11/12, Issue #202) und echte Fold7-Messungen.
4. Qualitätsgewinn bei Antworten, Recall, Latenz, RAM, Energie oder Thermik: **NICHT NACHGEWIESEN**. Neue lokale Diagnostik ist keine bessere allgemeine Intelligenz.

**Rollback:** Draft-PR #203 ungemergt lassen oder einzelne Commits gezielt zurücknehmen; kein Reset von #198/#197/#196, kein Force-Push, keine Datenmigration, keine App-Installation.
