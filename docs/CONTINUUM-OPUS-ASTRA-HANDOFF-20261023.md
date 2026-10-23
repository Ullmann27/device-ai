# Continuum — umsetzungsfertige Übergabe an Opus (CCC1) und Astra (Android UI)

Stand: 23.10.2026, ausschließlich Device AI Continuum. Kein Main-Merge, keine Signierung, keine API-Kosten.
Der integrierte Ausgangsstand ist **PR #94** (`0499e2a0d95187edcb3f0b8736339154bc563edb`). Der isolierte Forschungszweig ist **PR #95** (`integration/continuum-verifier-first-spine-20261023`); die Fold-Reflow-Reparatur liegt getrennt in **PR #96** (`integration/continuum-composer-reflow-fix-20261023`, basierend auf PR #95).

## 1. Opus: CCC1 fertigstellen, nicht ein zweites System bauen

**Quell-API:**
- `.github/continuum/ccc1-cli.mjs`: `compile --project <repo> --goal <text>`, `submit --project <repo> --state <dir> --goal <text>`, `status --state <dir> --format json`, `run-once --project <repo> --state <dir>`, `loop --project <repo> --state <dir> --cycles <n>`. Kein CLI-Kommando namens `cycle`, keine Flags `--state-dir` oder `--trigger` in dieser CLI.
- `ccc1-worker.mjs` exportiert `submitGoal`, `compileGoal`, `listTasks`, `runCcc1Cycle`; Statusserialisierung ist `statusJson(...)` in der CLI (`schema: continuum-ccc1-status`, Version 1).
- `ccc1-verifier.mjs` ist die unabhängige Reparatur-Autorität: echte Patchbytes, SHA-256, Auftrag/Commit-Bindung, drei Läufe und ausgeführte Tests; **Modell-Byte-Scores sind keine Autorität**.
- `ccc1-experience.mjs` führt geprüfte Ergebnisse im Journal; daraus **nicht direkt Trainingsgewichte überschreiben**.
- `ccc1-path-policy.mjs` schützt den State-Pfad gegen repo-interne Symlink-Umleitung; die Canonicalisierung ist vor Ausführung zu wiederholen und darf nicht TOCTOU-/Kernel-Sandbox-Beweise ersetzen.

**Bestehender Android↔Termux-Weg zur Wiederverwendung statt Neuentwicklung:**
- `ContinuumMobileBuilderActivity.kt`: explizit bestätigte Termux-RUN_COMMAND-Aufträge.
- `ContinuumMobileBuildPlan.kt`: feste, erlaubte Auftragsprofile.
- `ContinuumMobileCodingJob.kt`: gebundene Coding-Job-ID und validierte Rückmeldungen.
- `ContinuumMobileBuildResultReceiver.kt`: Android-PendingIntent-Callback und begrenzter Receipt-Speicher.
- `ToolExecutionReceiptStore.kt`: bereits persistente Werkzeugbelege, keine zweite Receipt-Instanz anlegen.
- `ContinuumDeviceTeamleaderScheduler`: Android-seitiger Planer; **nicht** als Node-CCC1-Wirkbetrieb ausgeben.

**Präzise noch offene Arbeiten für Opus:**
1. Vor jeder Anbindung tatsächliche Besitzerfreigabe, Repository-Pfad, State-Außenpfad, Termux-Berechtigung, aktiven Task und Journalbindung prüfen. UI darf freie Textnachricht niemals als Shell-Argument ausführen.
2. Erstmals eine ECHTE bestätigte `submit`→`status`→`run-once`-Kette über den bestehenden Termux-Kanal nachweisen, inklusive Beschränkung auf Git-Worktree, korrektes Fehler-/Abbruchverhalten und read-only Status.
3. Android-/Node-Vertragstests für `CCC1_STATE_INSIDE_SOURCE`, unbekannte Outcomes, beschädigtes Journal, doppelte Task-ID, kein Besitzer-Okay, verlorenen Callback, Unterbrechung, Timeout, Prozess-Neustart und Nicht-Merge auf `main`.
4. Testergebnis und Verifier-Journal nachvollziehbar im Entwicklungszentrum anzeigen, ohne zu behaupten, Patch sei installiert. Die bestehende Reparaturpipeline bleibt allein für produktive Codeänderungen zuständig.
5. Keine automatische Aktivierung eines Sprachmodells/CSX3, keine Verwendung von Argons nicht existierendem `cycle`-Aufruf.

## 2. Astra: Chat-Zustand bei Fold7-Reflow prüfen

**PR #96** enthält einen fokussierten Fix in `AgentActivity.kt`: Inhalt, Selektion (Start+Ende), Keyboard-IME-Status und EditText-Fokus werden über `setContentView(buildUi())` gesichert und wiederhergestellt; gepostete IME-Aktion ist an die neu erzeugte EditText-Instanz gebunden.
`ComposerReflowDraft.kt` besitzt rein-Kotlin lokale Prüfungen, Ergebnis PASS.
**Noch prüfen:** Android-Gradle/JVM/Instrumentierung, 1–4 Zeilen Eingabe mit Cursor mitten im Text, markiertem Text, Emulator/Fold7 geöffnet+geschlossen, IME offen, IME geschlossen, Hintergrund/Resume, 100 Reflow-Zyklen ohne Draft-Verlust, TalkBack. Keine Navigationsregressionen.

## 3. Was die Forschung aus Argons Vorschlägen tatsächlich geschaffen hat

**PR #95** ist vom Produktivpfad getrennt:
- `ContinuumCognitiveSpine.kt`: nur Byte-gepinnte Zitate und bewusste Enthaltung; nicht Wahrheit.
- `ResidualSpanResearchAdapter.kt`: Adapter zur realen `ContinuumResidualSpanCore.predict(evidence, question)`, mit 1024-Byte-Evidenz- und 256-Byte-Frage-Limit sowie strengem Span-Check. Kein Hauptchat-Switch.
- `ContinuumCortexShadow.kt`, `ContinuumCortexResearchPipeline.kt`: RAM-Fast-Weight-Gradient, Typ-Graph bis drei Kanten, Holdout-gesteuerter nicht persistenter Habit-Cache, **neuer expliziter Session-Reset**. Kein autonomes freies Denken behaupten.
- `Cdx1PatchShadowStudy.kt`: experimentelle Byte-Likelihood, nur Analyse; keine Freigabe für CCC1.
- `scripts/continuum-reviewed-context-shadow.mjs`: Hash-/Review-/Lexikon-gebundener UTF-8-Kontextvorschlag ohne GGUF-/Chat-/Toolaufruf.

**Echte lokale Belege:** Kotlin/JVM 1.9.0/JDK 21 Cortex-Checks (zwei Programme) PASS; ComposerReflowDraft-Checks PASS; CCC1-Pfad-Policy Node-Tests 2/2 PASS. Adapter-Syntax wurde separat gegen ein schnittstellengleiches Testmodell kompiliert, nicht gegen den vollständig ausgecheckten CSX3-Quellstand. Vollständige Android-/Node-Suiten **nicht** gelaufen.

## 4. Release-Gate (für alle verbindlich)

Vor APK 0.3.17/versionCode 18 vollständiges isoliertes Build-Checkout und:
1. Vollständige CCC1-/Node-Suiten mit tatsächlichem Exitcode und ausgeführten Assertions; keine `--skip`-Täuschung.
2. `:app:testDebugUnitTest`, `:app:lintDebug`, Android/UI-Tests, vorhandene lokal bestätigte Signier-/Release-Kette, echte ARM64-Realgeräteprüfung.
3. Gleicher Package-Name `at.heinz.deviceai.mentorpaired`, identische produktive Zertifikatidentität wie 0.3.16; dokumentierter Rollback inklusive Hash.
4. Benutzer-Daten, Memory/Checkpoints, Chat-Eingaben, UI, Termux-Lauf, CCC1-Hauptbranch-Schutz erhalten.
5. Keine Veröffentlichung, kein Main-Merge und keine automatische Signierung ohne diese Freigaben.

Opus/Astra sollen direkt auf den vorhandenen Modulen aufbauen, nicht parallele Quittungs- oder Worker-Systeme erschaffen. Die heutigen Forschungs-Patches bleiben sicher isoliert, bis echte End-to-End-Belege die Produktivreife zeigen.
