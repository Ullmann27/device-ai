# Continuum – Drei Kontrollgänge / CCC1-Shell-Vorbereitung (23.10.2026)

**Basis:** PR #94 0499e2a0; PR #95 Forschung; PR #96 separater Fold7-Reflow-Fix. Keine Main-Änderung und keine neue APK. Quelle: GitHub API und lokale isolierte Prüfungen.

## Kontrollgang 1 – GitHub / Workflow-Befunde

- Opus PR #92, Head 313e6ffce5afcb24ecd7c7345b988dd103a3c2a6, weiterhin Draft und unverändert. PR #94 bleibt ebenfalls Draft. PR #95 hat neue schreibfreie Preflight-Prüfungen; PR #96 bleibt unabhängig für Android-UI.
- GitHub-Läufe [Hourly Watch](https://github.com/Ullmann27/device-ai/actions/runs/39932918383) und [Zero-Cost Core](https://github.com/Ullmann27/device-ai/actions/runs/39927474100) vom 23.10.2026 endeten nach jeweils etwa vier Sekunden mit `failure`. In den abrufbaren Jobs sind **keine ausgeführten Step-Datensätze vorhanden**. Die eigentliche Ursache (Runner, Actions-Konto oder Workflow-Konfiguration) ist NICHT bekannt; kein Nachweis für CCC1-/Gradle-Codefehler. Run-Ansichten müssen zusätzlich geprüft werden.
- Die beiden `main`-Workflows verwenden **ältere, hartcodierte Refs**: `copilot/pr-12-working-first-vertical-slice` bzw. `a3bca34f4f2843875bd907954991ee9a5d9638ed`, nicht PR #94/#95. Keine aktiven CI-Belege auf dem Integrationskandidaten. Main-Workflow nicht ohne Besitzerfreigabe verändern.

## Kontrollgang 2 – Android / KI / Verdrahtung

- Bestehende Integrationen: `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileCodingJob.kt`, `ContinuumMobileBuildResultReceiver.kt`, `ContinuumMobileBuildPlan.kt`, `ToolExecutionReceiptStore.kt`, `ShizukuBridge.kt`. **Opus muss diese nutzen, statt redundante Shell- oder Quittungssysteme zu entwickeln.**
- `ContinuumResidualSpanCore.predict` ist Forschung, keine zertifizierte Wahrheitsprüfung; der Adapter in PR #95 bleibt hinter einem Forschungs-Gate.
- Experimenteller Kortex hält normbegrenzte Fast-Weights und einen begrenzten typisierten Wissensgraphen. Sitzungstrennung ist abgesichert; Produktivchat-Anbindung fehlt bewusst.
- PR #96 bewahrt Chat-Draft, Selektion, Fokus und IME bei Fold-Reflow. Echter Android-Gradle-/Fold7-/Tastaturtest fehlt weiterhin.
- Der komplette GitHub-Baum bestätigt: **direkte lokale Importziele** der beiden neuen Node-Modulpaare (CCC1-Preflight und GGUF-Kontextkandidaten) existieren. Noch kein Transitiv-/Gerätebeweis.

## Kontrollgang 3 – tatsächlich ergänzte Shell/CCC1-Vorprüfung

Neu: `.github/continuum/ccc1-local-preflight.mjs` und `.github/continuum/ccc1-local-preflight.test.mjs` auf PR #95.

Der Preflight führt keine Reparatur aus, nutzt **ausschließlich** read-only `git`-Kommandos (kein Shell-String), prüft vollständige benötigte CCC1-Module, Node >=20, Git-HEAD, sauberen Worktree, isolierten Branch und State-Pfad. Er **importiert** die bereits vorhandene `ensureCcc1StateOutsideSource`-Policy gegen Symlink-Umleitung. Geschützte Branches: main/master/production/prod/release inklusive Familien. Resultate sind nur `READY_TO_REQUEST_OWNER_REVIEW` oder `BLOCKED` mit Ursachen; niemals Freigabe- oder Erfolgsschwindel.

**Tatsächlich lokal:** Preflight 4/4 Tests PASS. Bestehende State-Pfad-Policy 2/2 Tests PASS. Echte temporäre lokale Git-Fixture: Forschung-Branch => READY; Wechsel zu main => BLOCKED. Kotlin/JVM Kortex-Prüfungen: beide Programme PASS, darunter 22 gezielte Sicherheitsassertions. Kein echter CCC1-Worker und kein vollständiger Android-Build ausgeführt.

### Ausführbare Anweisungen für Opus – im sauberen isolierten Checkout

```bash
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
STATE_ROOT="$HOME/.local/state/continuum-ccc1"
node .github/continuum/ccc1-local-preflight.mjs --project "$PROJECT_ROOT" --state "$STATE_ROOT"
node --test .github/continuum/ccc1-local-preflight.test.mjs .github/continuum/ccc1-path-policy.test.mjs
node --test .github/continuum/ccc1.test.mjs .github/continuum/ccc1-worker.test.mjs
node --test scripts/continuum-reviewed-context-shadow.test.mjs
```

**Nur bei READY** die Aufgabe separat und ausdrücklich bestätigen lassen. Der existierende CLI-Vertrag lautet `compile`, `submit`, `status`, `run-once`, `loop`; NICHT `cycle --state-dir --trigger`. Die Vorprüfung erteilt weder Shell-Ausführungsrechte noch einen Merge-Auftrag und ist kein OS-Sandbox-Beweis.

## Was noch fehlt

1. Opus: echter Besitzer-freigegebener Android↔Termux↔CCC1-Zyklus auf begrenztem Worktree, Journal/Verifier-Receipts, Recovery, Cancellation, OS-/Symlink-TOCTOU-Isolation.
2. Astra: Android SDK, `:app:testDebugUnitTest`, `:app:lintDebug`, Phone/Fold/IME-, Barrierefreiheit- und UI-Regressionstests; PR #96 einschließlich Benutzertexte erhalten.
3. Integration: kombinierter Node-/Android-Test auf eingefrorenem Quell-SHA; Owner-Key-/gleiches-Paket-Update versionCode >17, vorhandener Rollback mit Signaturprüfung und echte Fold7-Abnahme **vor** Veröffentlichung.

**Abschluss:** Die unabhängig ausführbaren statischen und gezielten lokalen Prüfungen dieses dreifachen Rundgangs sind erledigt. Größere Integrations- und Geräte-Gates sind nicht bewiesen. Daher ausdrücklich keine Behauptung, es gebe absolut keine weiteren Fehler.
