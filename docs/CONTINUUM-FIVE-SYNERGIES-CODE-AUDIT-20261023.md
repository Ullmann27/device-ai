# Fünf vorgeschlagene Continuum-Synergien – Abgleich mit realem Code (23.10.2026)

Basis: PR #94 Commit `0499e2a0`; nachgelagerte Änderungen ausschließlich in `integration/continuum-verifier-first-spine-20261023`. Noch kein Release. Kein Merge nach `main`.

## 1. Parametrische Micro-App-Synthese – NICHT als APK umgesetzt

Der Bestand verfügt über `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileCodingJob.kt`, `ContinuumMobileBuildPlan.kt` und `scripts/continuum-mobile-builder.mjs` (Operations `doctor|build|status|code-test|code-cancel`). Der derzeitige Builder baut einen kontrollierten **bestehenden Device-AI-Checkout** zur isolierten Preview-APK. Er übersetzt keine freie Anfrage in ein neues, eigenständiges Android-Projekt.

Der vorgeschlagene `ContinuumMicroAppSynthesizer.kt` generiert nur Kotlin-Strings und einen Test, aber **kein Manifest, keine Android-Activity, keine Ressourcen, keine Gradle-Projektdateien und keine signierte APK**. Er ist kein echter Blueprint-to-APK-Compiler. Probleme: selbst bei frei gewählten Blueprint-Feldern müssen Namen/Typen und Literale gegen Code-Injection validiert werden; `Double` ist für Finanzbeträge unzuverlässig; ein hartcodiertes Monatslimit von 1500 EUR ist nicht nutzerkonfiguriert und Zeit-/Monatsfilter fehlen. Die vorgelegten Tests prüfen fast nur Dateiinhalte, nicht Kompilierung oder End-to-End-Bau. Kein unverifiziertes Produktions-Feature hinzugefügt.

## 2. CDX1 Patch-Anomalie-Sensor – NUR SCHATTENEXPERIMENT

`app/src/main/java/at/heinz/deviceai/learning/TinyContextDecoder.kt` ist eine experimentelle Byte-Softmax mit `CONTEXT=8`. Reale Methoden: `evaluate(DecoderCorpus)`, `nextTokenProbabilities(prompt)`, `train(...)`, `generate(...)`. Es gibt keine bestehende beweiskräftige Patch-Validierungs-API oder nachgewiesene `<1ms`-Laufzeit.

`app/src/test/java/at/heinz/deviceai/learning/Cdx1PatchShadowStudy.kt` und `Cdx1PatchShadowStudyTest.kt` wurden aufgenommen. Sie vergleichen nur manuell beschriftete, synthetische Varianten via `evaluate(...AUDIT)`; Ergebnisse sind Wahrscheinlichkeitshinweise und `canApprovePatch=false`. Keine Gewichte werden geändert. **Nicht mit dem offiziellen CCC1-Verifier verwechseln**.

Der bestehende `.github/continuum/ccc1-verifier.mjs` prüft reale Patchbytes, SHA-256, den Auftrag, drei feste Testläufe und unabhängige Belege. `repair-critic.mjs` führt strukturierte Sicherheitsregeln aus. Diese Instanzen behalten alleinige Gate-Autorität. Ein 8-Byte-Modell kann semantische Bugs oder ausgeglichene Kotlin-Klammern nicht garantieren; Quell-Hunks besitzen bewusst unbalancierte Klammern. Die vorgeschlagene Stack-Prüfung erkennt u. a. keine Triple-Quoted-Strings, Escapes, Blockkommentare oder Kotlin-Interpolation vollständig.

## 3. Shizuku Smoke-Test / Receipt Ledger – BESTAND ANSTATT DOPPELBILDUNG

`app/src/main/java/at/heinz/deviceai/ShizukuBridge.kt` führt bereits den Besitzerfreigabe-/Servicebindungs-Pfad. `ToolExecutionReceiptStore.kt` bietet persistierte Zustände `PROPOSED, APPROVED, STARTED, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, REJECTED`, ein Datenformat mit Integritätschecksumme und Testdatei `ToolExecutionReceiptStoreTest.kt`. Der vorgeschlagene neue `ShizukuPreviewSmokeGate` wäre ein konkurrierendes, ausschließlich RAM-basiertes Receipt-Register. Ein SHA-256-Chain-Hash allein ist keine digitale Signatur, keine nicht-fälschbare Werkzeugausführung und kein Attest einer echten Android-Installation. Nicht übernehmen.

Sicherer zukünftiger Pfad: isolierte Preview-Paketidentität und Installationsbesitzerfreigabe; fixierte Kommandos; checkbare Shizuku-Shell-Berechtigungen; Paket-Zertifikatsabgleich; `am start -W` plus abgestufte Crash-/Logcat-Analyse mit echter Zeitschranke; persistierte vorhandene Receipts und echte Gerätebelege. Kein automatischer Eingriff in `at.heinz.deviceai.mentorpaired`.

## 4. GGUF Kontextfilter – READ-ONLY IMPLEMENTIERT

Bestand: `scripts/continuum-gguf.mjs` prüft beschränkte Modellidentität; `scripts/continuum-local-context.mjs` ist ein separat beschränkter **Coding-Diagnose-Kontext**; `scripts/continuum-local-brain.mjs` liefert eine authentifizierte lokale Loopback-Schnittstelle. `.github/continuum/dialogue-retrieval.mjs` exportiert `sourceTermEvidence(query,text)`, einen lexikalischen Ankerfilter **ohne Wahrheitsgarantie**.

Neu: `scripts/continuum-reviewed-context-shadow.mjs` mit `.test.mjs`, read-only und ohne Modell-/Shell-Aufruf. Es fordert für *alle* Quellen eine Reviewed-Markierung, die nicht widerrufene Originalquelle, SHA-256-Pin und lexikalische Anker. Maximal drei Exzerpte mit insgesamt höchstens 600 **UTF-8-Bytes**, nicht nur Unicode-Zeichen. Es gibt nur den Status `UNTRUSTED_CONTEXT_CANDIDATES` bzw. `ABSTAIN_NO_REVIEWED_LEXICAL_MATCH`; niemals `verifiedFacts`, `autoApply`, Toolrecht oder automatische Hauptchatumschaltung.

**Tests wurden in GitHub als Quellcode bereitgestellt, aber nicht gegen den vollständigen Repo-Checkout ausgeführt.** Vor produktiver Nutzung: End-to-End-Adaption, echte Testfixtures aus QF2, Laufzeitbudget, Prompt-Injection-Abwehr sowie Herkunfts- und Nutzerfreigaben prüfen.

## 5. Automatische kontrafaktische Mutationen – NICHT AUTOMATISIEREN

`ContinuumCounterfactualCurriculum.kt` verlangt einen **ReviewedFact** einschließlich `replacement` und `reviewedNegativeEvidence`. Es erstellt `ContinuumResidualSpanCore.Example` für Spannenlernen, keine kompilierten Kotlin-Testfälle oder unabhängige Code-Spezifikationen. Automatischer Austausch von Kennungen/Grenzwerten kann syntaktisch gültigen Code fälschlich zum Fehler erklären oder einen logischen Fehler nicht erkennen. `isolated-profile-runner.mjs` ist ein fester OS-Isolations-Runner, keine Test-Orakelmaschine.

Erst separat erwägen: aus einer explizit menschlich bestätigten Verhaltensspezifikation mutationstaugliche Tests im neuen, gesperrten Worktree erstellen, als **zusätzliche** Befunde, ohne Baseline-Testüberschreibung, Main-Write, Produktionssignatur oder Freigabeautorität. Docker-/Kernel-Isolation, Test-Orakel und Sandbox müssen bewiesen sein.

## Priorität für die nächste Android-APK

1. Offene Freigabegates aus `docs/CONTINUUM-INTEGRATION-GATES-20261023.md` schließen.
2. Bestehende Chat-/Modellbedienung, Termux-/Shizuku-Quittungen, Besitzer-Daten, Fold-UI ohne Regressionen prüfen.
3. Neue Forschungsfunktionen nur mit falsifizierbaren Tests und expliziter Opt-in-Bedienung; niemals neue Moduldatei mit produktiver Funktion gleichsetzen.
4. Paket `at.heinz.deviceai.mentorpaired`, bestehende Signatur, neuer versionCode >17, altes Rollback-APK verifiziert archiviert, Geräte-Abnahme vor Release.

Noch **nicht** ausgeführt: gesamter Node/Android/Gradle/JVM-/Lint-Lauf, reale Docker-Isolation, signierter 0.3.17 Build oder Samsung Fold7-Abnahme.
