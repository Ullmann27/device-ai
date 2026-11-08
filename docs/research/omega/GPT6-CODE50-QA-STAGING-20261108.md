# Code50 QA Staging — 2026-11-08

Integration base: PR #603 HEAD `0c59dcc7b249d7eb6bfc9fb21d2306f814105ed6`.
Source branch: #628 through `b445b509e79a65f908f95b51d37409cc840ac5d6`.
Incoming planned: non-conflicting quality fixes and tests from #627 at `d6b3f8ce9bb44aab1af667020a24ff9ad5809cc0`.
#629: QA-only, not automatically cherry-picked.

**No main changes, no signing or release.** This branch combines candidate fixes only; Gradle/Node/Fold7 validation remains mandatory.

## Order
1. Carry unique production fixes: ProactiveSideQuestAndCountdownEngine, HollywoodFuiStageOverlay.
2. Carry updated contract tests for true same-session context and Fold7 Shell.
3. Carry Robolectric OwnerAppActionWorkSceneUiTest holdouts.
4. Preserve the richer #628 OwnerAppActionFlowTest instead of overwriting it with #627 one-line-only version.
5. Validate source and run available tests; report exact SHA and outputs.
6. Only then consider integrating into PR #603 (not main).

Risk: PR #628 stale pronoun state after rejected lookup must be cleared; fixed at `b445b509` and keep it in staging. Review all pending threads independently.

STATUS: WIP / UNVERIFIED.

## Integrierte und kontrollierte Quellen

- **Basis:** #603 `0c59dcc7b249d7eb6bfc9fb21d2306f814105ed6`. Staging ab #628 `b445b509e79a65f908f95b51d37409cc840ac5d6`.
- **Aus #627** `d6b3f8ce9bb44aab1af667020a24ff9ad5809cc0` exakt byteidentisch übernommen: `continuum-local-dialogue-quality-wiring.test.mjs`, `fold7-portrait-reference.test.mjs`, `owner-cockpit-voice-original-orb.test.mjs`, `ProactiveSideQuestAndCountdownEngine.kt`, `HollywoodFuiStageOverlay.kt`. Jeder GitHub-Blob-SHA in Quelle und Staging verglichen: **5/5 identisch**.
- **Aus #627, anschließend präzisiert:** `OwnerAppActionWorkSceneUiTest.kt` einschließlich fuzzy Need und Cancellation; `lookupGeneration` prüft tatsächlichen neuen Lookup-Turn, zusätzlicher Test `rejectedFuzzyNeedClearsPriorVerifiedPronounCandidate` für Alt-Kontext.
- **Bewusst NICHT überschrieben:** `OwnerAppActionFlowTest.kt` aus #628 enthält mehr Holdouts als die überlappende Ein-Zeilen-Korrektur aus #627. Die vierargumentige Signatur ist bereits korrekt.
- **Aus #628:** echte App-Action-Receipts, fuzzy exact-only Gate, lookup-only Fallback und nach zurückgewiesener verifizierter Discovery gelöschter stale Pronoun-Cache. ToolGate/Signing/Package/Version unverändert.
- **Zusätzliche Handoffs:** `ASTRA-CODE50-ACCEPTANCE-HANDOFF.md`, `GPT6-CODE50-APP-RECEIPT-HANDOFF-20261108.md`.

## Qualitätsstand und tatsächliche Prüfungen

- Verifizierte Quelltext-Invarianten des Staging-Branches: 19/20 im ersten Durchlauf; eine reine Prüfsuche hatte den `\\(`-Escape im Node-Fold-Vertrag falsch geschrieben. Die originale Assert-Zeile wurde danach direkt aus der GitHub-Datei gelesen und korrekt verifiziert: **20/20 SOURCE_ASSERTIONS PASS**. Dies ist **kein ausführbarer Node-/Gradle-Test**.
- **Unabhängige QA des Ausgangszweigs #627, ausdrücklich nicht #630:** dessen Handoff nennt einen echten lokalen Node-Durchlauf **5 PASS**, danach einen echten Gradle-Compilelauf mit **3 Kotlin-Diagnosen**; die zwei Quellfixe sind in diesem Staging enthalten, aber kein erneuter Gradle-Compile-PASS liegt vor.
- GitHub-Review von #630: Approved/keine Findings, **keine** funktionale Validierung. Reviewer-Schlussfolgerung ist keine Build-Abnahme.
- Hier verfügbare Shell hat Node 22 und Kotlin 1.9, aber **keinen Checkout** des aktuellen GitHub-Repositorys und **kein Android-SDK**; Git-Remote-Auflösung scheitert an DNS. Daher offizielle exakte Node-, Android- und Lint-Tests auf Staging **NOT_RUN**. Eine synthetische Quellenprüfung wird nicht als solche ausgegeben.
- Render-QA-Historie zuletzt `pipeline_minutes_exhausted` vor Checkout; nicht für #630 geschaltet, kein Deploy ausgelöst.
- Fold7: 10 Hands-Free-Turns, 15-s Avatar-Live-Proof, Thermik/CPU/FUI und originale Code50-APK **NOT_RUN / NOT_BUILT**.
- Keine GitHub Actions zum QA-Ausweichen angelegt oder getriggert; siehe `AGENTS.md` und `CLAUDE.md`.

## Ausführungsgates (exakt auf festgepinnter Staging-SHA)

```bash
node --test .github/continuum/owner-cockpit-voice-original-orb.test.mjs
node --test .github/continuum/continuum-local-dialogue-quality-wiring.test.mjs .github/continuum/fold7-portrait-reference.test.mjs
```

Fokussiertes Android/JUnit-Runtime-Gate: `OwnerAppActionFlowTest`, `OwnerAppLookupIntentTest`, `ProactiveSideQuestAndCountdownEngineTest`, `Airbus2100CockpitAndCountdownTest`, `Argon4CockpitAndBudgetRegressionTest` und `OwnerAppActionWorkSceneUiTest` im **vorgesehenen** Robolectric-`uiTest`-Quellsatz. Für `--tests` die Repo-QA-Guard-Regeln beachten und den bereits in #627 dokumentierten separaten Init-Script-Filter verwenden, nicht den Projektguard deaktivieren.

Danach: kompletter `:app:testDebugUnitTest :app:lintDebug`, Node/Backend-Full, echtes Fold7 (Voice/Avatar/FUI/Thermal), Package/versionCode+Originalsigner+SHA256 und Besitzer-Installation. Kein Merge nach `main` oder Release ohne bestandene Gates.

## Rollback
Staging-PR #630 geschlossen lassen oder nicht mergen; produktive Basis #603 bleibt unverändert. Parallel-PRs #627/#628/#629 nie per Force-Push/Reset überschreiben.
