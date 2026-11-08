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

## Gepushte Implementierung / belastbare Grenzen
- Produktivpfad: `OwnerAppActionFlow(requireExactInstalledCandidate)` beendet fuzzy implizite Lookups **vor** einer Auswahl; explizite Suche darf weiterhin eine bestätigungspflichtige Wahl anbieten.
- `lookupFinishedWithoutInteraction` wird ausschließlich beim Ende der reinen LOOKUP-Phase gesetzt. `AgentActivity` erlaubt nur dann LLM-Fallback oder die Nachrichtenklärung. Nach `Step.Choose`, `choose(null)` und `finishLaunch` wird die tatsächliche Aktion/Absage gemeldet.
- Tests: `OwnerAppActionFlowTest` – fuzzy Implicit (Whats/Urlaub), fuzzy Explicit (Cancel/Launch), Legacy-Guard nach Choice; bestehender exact-Implicit-Flow verwendet nun denselben strengen Konstruktor. Alter `shouldClarifyMessage`-Aufruf von drei auf vier Argumente korrigiert.
- Gepushte produktive Code-Commits: `31a39852516506de529f0f63805c1687e48bc0ce`, `7a014c1245f4ab359d21c368374fcc73228164dd`. Tests: `d6de443b7c278652f1a38c22221da7381d9ea84b`, `14dc3294c5f06ccf07e3df0e325ef53aa33a454d`.

## Tatsächliche Prüfung
- **SOURCE_CONTRACT_TEXT_CHECK**: 10/10 String-/Reihenfolgeprüfungen auf den echten über GitHub gefetchten Dateien **PASS**. Kein Ersatz für Kotlin-Ausführung, JUnit oder Android.
- JUnit/Kotlin-Kompilation: **NOT_RUN**. Native Android SDK/Gradle nicht in der vorhandenen lokalen Shell; Internet-DNS-Zugriff auf GitHub dort nicht verfügbar.
- Node-Cockpit-/Voice-Verträge: **NOT_RUN** in diesem Zweig. Die unabhängigen PRs #627 und #629 melden jeweils initiale lokale Node-Fehler im alten same-session-Testvertrag; diese Ergebnisse nicht als Erfolg von #628 übertragen.
- Render-Service `srv-dbnual1srm7s73afh69g` ist auf `chatgpt/code50-recall-qa-fix-20261108` (nicht #628) konfiguriert. Letztes Ereignis: `pipeline_minutes_exhausted` am 08.11.2026 02:58:29 UTC; vor Checkout/Gradle. Kein Deploy ausgelöst.
- Fold7, Akku/Thermik, 10 Voice-Sätze, 15-s-Avatar-Proof, APK/Originalsignatur: **NOT_RUN / NOT_BUILT**.

## Abnahme auf dem unveränderten geprüften SHA
```bash
node --test .github/continuum/owner-cockpit-voice-original-orb.test.mjs
gradle --no-daemon --max-workers=2 --console=plain \
  -PdeviceAiMentorPreview=true \
  -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=50 \
  :app:testDebugUnitTest --tests at.heinz.deviceai.cognitive.OwnerAppActionFlowTest
gradle --no-daemon --max-workers=2 --console=plain \
  -PdeviceAiMentorPreview=true \
  -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=50 \
  :app:testDebugUnitTest :app:lintDebug
```
Erst reproduzierbar grüner exakter Quellenstand; anschließend Hardware- und Publisher-Gates. Kein GitHub-Actions-Auslöser (AGENTS.md).

## Parallelzweige
- #627: eigener Abnahme-/Routing-Zweig; ändert auch `OwnerAppActionFlowTest` und zwei Node/UI-Verträge. Bei Integration Konflikte auf Testebene fachlich vereinigen, **nicht** blind überschreiben.
- #629: separate Node-/Gradle-Quellenabnahme (Handoff). Seine Testbefunde bleiben unabhängig.
- PR #603 ist die Integrationsbasis; **kein Merge nach main**. Auch diesen Draft-PR erst nach tatsächlicher QA integrieren.
