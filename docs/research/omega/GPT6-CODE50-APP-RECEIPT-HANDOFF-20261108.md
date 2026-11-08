# Code50 App-Receipt/Fallback Regression – GPT-6 Handoff
Stand: 08.11.2026. Basis: PR #603, 0c59dcc7b249d7eb6bfc9fb21d2306f814105ed6.

## Reproduzierbarer Quellfehler
Auf `Step.Done` prüft AgentActivity auch nach `Step.Choose`, `choose(null)` oder `finishLaunch()` noch `shouldFallbackToConversation`. Ein nicht exakter `Ich brauche Whats`-Treffer kann dadurch ein Ergebnis einer tatsächlich erfolgten oder abgebrochenen App-Aktion verschlucken.

## Reparaturziel
- Ungenaue, implizite App-Wünsche dürfen ohne exakte verifizierte Entdeckung keine Auswahl und keinen Launch anbieten.
- Konversationsfallback nur, wenn der Flow unmittelbar aus dem Lookup ohne Interaktion beendet wurde.
- Explizite, bewusst ausgewählte fuzzy App-Treffer behalten ihre echten Abbruch-/Tool-Receipts.
- Keine Änderung an ToolGate, Trust/Dispatch, Package, Signatur, Version oder main.
- Regressionen für fuzzy implicit / explicit, Auswahlabbruch, bestätigten Launch, exakte Treffer.
- Source-fremde Skripts, Experimente und Runtime-Architektur bleiben unangetastet.

## Experimentbezug
EXP-02 (testgesteuerter Bugfix), EXP-07 (adversarialer Holdout), EXP-12 (korrekte Gesprächskontinuität).

## Ausgangszustand
- PR #603 source mergeable, Source-Tests Code50 und Fold7: NOT_RUN.
- GitHub Actions laut AGENTS.md / CLAUDE.md nicht als QA auslösen.
- Render-Kontingent zuletzt erschöpft (Handoff PR #603).
- Host-JDK/SDK/Gradle-Test: NOT_RUN, Fold7: NOT_RUN, APK: NOT_BUILT.

## Eintritt für Integrator
1. Branch-HEAD mit PR #603 prüfen, fokussierte Kotlin-/OwnerApp-Tests auf exakter SHA laufen lassen.
2. `testDebugUnitTest lintDebug`, Node-Vertrag, Fold7-Funktions-/Voice-/Avatarabnahme.
3. Nur nach überprüften Gates in #603 integrieren; main und Originalpublisher nicht ändern.
4. Rollback: diesen isolierten PR nicht mergen bzw. nur seine Commits auf Integrationszweig rückgängig machen.
