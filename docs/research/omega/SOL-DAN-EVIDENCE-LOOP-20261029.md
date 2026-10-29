# Continuum DAN — begrenzter Anschlusslauf

WIP / UNVERIFIED. Basis: PR237 `f54327b906a67d4f40862b5dd25a6b72e6015bc7`, geprüfter Code `771529a3c2602db02f5255a748d16b2ce9205957`. Eigener Branch `sol/continuum-dan-evidence-loop-20261029`.

Kleinster Prüfgegenstand: Der normale modellgestützte Web-Tool-Pfad verwendet bereits ToolGate und DeepResearchEngine, übergibt sein Dossier aber bislang nicht an den vorhandenen Quellen-Lernstoffeingang. Diesen Übergang kontrolliert verbinden, ohne automatische Fakten-/Gewichtspromotion; reale negative Autorisierungs-/Fehlerprüfungen. Zweiter Gegenstand: vorhandene CCC1-Erfahrungs- und unabhängige Verifierkette auf tatsächlich wiederverwendbare, gültige Reparaturbelege prüfen. Kein zweiter Worker.

EXP02/05/06/11/12. PR134/179/198/224 geprüft; Opus-Forschung/versiegelte Tests bleiben unverändert. PR236 bleibt separat. Kein APK-Build, Modell-Download, Signiererzugriff, Main-Merge oder private Datenexporte. Noch kein neuer Geräte-/Gemma-/autonomer Reparaturnachweis. Die Resultate für diesen Branch sind unten getrennt dokumentiert.

## Implementierter, aber noch nicht voll verifizierter Stand

- Der normale Chat-Werkzeugpfad leitet genehmigte, abgeschlossene Web-Dossiers erst nach einer erfolgreichen Modellrunde an das **bestehende** Quellenprüffenster weiter. `ApprovedWebResearchToolRunner.publishForReview` prüft den aktuellen Einmalzugriff, einen persistenten `SUCCEEDED`-ToolGate-Beleg, Toolname, Argument-Fingerprint sowie Länge und SHA-256 des tatsächlichen Ausgabetextes. Fehlende oder unpassende Belege unterbinden die Übergabe.
- Die übernommenen Inhalte bleiben ausschließlich **Kandidaten für eine getrennte Benutzerprüfung**. `WebLearningJournal.approve` wird nicht automatisch ausgeführt. Weder Eigentümerfakten noch neuronale Gewichte werden von diesem Webpfad automatisch verändert.
- Bei zwei separat freigegebenen Chat-Webaufrufen bevorzugt die Übergabe nun das neueste Dossier mit tatsächlich geprüften Lernkandidaten; eine spätere leere Recherche verdeckt frühere brauchbare Belege nicht.
- CCC1s `repair-ledger.mjs` wertet positive Reparaturerfahrung nur aus einem zur bestätigten Gewinner-Kandidatur passenden `VERIFIED`-Versuch mit SHA-256 aus. `repair-strategies.mjs` blockiert das Wiederverwenden unbeglaubigter, bloß behaupteter Patch-Erfahrung. Unabhängige Verifizierung bleibt zwingend.
- Sicherheitsregressionen wurden in `AlwaysOnWebConsentTest.kt` und `repair-ledger-independent.test.mjs` im Quelltext ergänzt. Ihre reine Existenz beweist noch keinen bestandenen Testlauf.

## Prüfstatus 29. Oktober 2026

Die GitHub-Actions-Workflowdatei `.github/workflows/continuum-dan-pr238-qa.yml` wurde hinzugefügt. Die Läufe [40897134373](https://github.com/Ullmann27/device-ai/actions/runs/40897134373), [40897299844](https://github.com/Ullmann27/device-ai/actions/runs/40897299844) und [40897508127](https://github.com/Ullmann27/device-ai/actions/runs/40897508127) sind **FAILED vor Ausführung irgendeines Schritts** (GitHub-Jobs meldeten leere `steps` und `runner_id=0`). Das ist **kein** Beleg für einen Kotlin- oder Node-Testfehler; die genaue Infrastrukturursache ist bisher nicht gesichert. Tests für PR238 dürfen deshalb nur als `NOT_RUN` gelten.

Die aus PR237 dokumentierten 1.234 Android-JUnit-Tests und 226 Node-Tests betreffen den dort geprüften vorherigen Code-Stand, **nicht** die neuen Änderungen dieses PRs.

Reproduzierbare Folgeprüfung im isolierten Branch auf einem tatsächlich verfügbaren und passenden Host:

```bash
git status --short
git rev-parse HEAD
node --test --test-concurrency=2 .github/continuum/repair-ledger-independent.test.mjs .github/continuum/repair-cycle-safety.test.mjs
node --test --test-concurrency=2 .github/continuum/*.test.mjs
gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug --stacktrace
```

Vor einer Freigabe sind außerdem der vollständige JUnit-/Node-Testbericht, Android-Compilergebnis, CCC1-Regressionen sowie eine tatsächliche Geräteprüfung des **neuen** Review-Übergangs nötig. GitHub-Actions darf bei fehlender Runner-Ausführung nicht als PASS gewertet werden. Keine Besitzerfreigabe, APK, Installation, Signatur, automatisierte Wissenspromotion oder Fusion mit `main` erfolgt.
