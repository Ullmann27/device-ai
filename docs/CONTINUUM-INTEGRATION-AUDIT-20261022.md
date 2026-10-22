# Continuum – Integrationsprüfung aus der Vogelperspektive

**Zeitpunkt:** 22.10.2026. **Referenz:** signiertes Release v0.3.12, Git-Commit `619ebeb2831c8184a91e3c08e165ed4f4794f969`. **Art:** Quellcode-/PR-/Releaseprotokoll-Audit, **kein** vollständiger Geräte- oder Buildtest. Dieser Dokumentationszweig basiert exakt auf dem Release-Commit; keine Gewichte oder produktiven Konfigurationen geändert.

## 1. Entscheidende Bestandsprüfung

GitHub: `Ullmann27/device-ai` ist privat und zugänglich. Beim ersten Audit wurden **53 offene Pull Requests** gelistet; darunter historische/durch Zwischenzweige bereits überholte Arbeit. Der Basisbranch `main` und die signierte APK-Entwicklungslinie sind stark divergiert: `main...chatgpt/continuum-navigation-signed-delivery-20261022` meldete **1616 voraus / 24 zurück**, Status `diverged`. Daher `main` **nicht** als automatische APK-Quellreferenz nehmen und nicht blind zusammenführen.

| Bereich | Tatsächlich im Release-Commit 619ebeb | Weitere Entwicklung / fehlende Verbindung | Risikobewertung |
| --- | --- | --- | --- |
| Signierte Android-APK | [PR #65](https://github.com/Ullmann27/device-ai/pull/65) dokumentiert 0.3.12 / Code 15, `at.heinz.deviceai.mentorpaired`, Render-Gradle/JUnit/Lint/Signatur- und SHA-Prüfungen. | Kein hier neu ausgeführter Samsung-Gerätetest; kein neuer Build. | Daten/Signatur müssen vor Update erneut kontrolliert werden. |
| Navigation und Fold | `ContinuumWorkAreaNavigationSpec.kt` auf Release SHA `c154f06a567c964797beccf00c7659196b624f3e`, fünf Arbeitsbereiche. [PR #64](https://github.com/Ullmann27/device-ai/pull/64) → [#65](https://github.com/Ullmann27/device-ai/pull/65). | Native Bildschirm-, Tastatur-, große Schrift- und Fold-Tests auf physischem Gerät fehlen hier. | Vermeidung unbenutzbarer Dialoge und verdeckter Buttons. |
| SWB1/CDX1 | `ContinuumLearningCore.kt` auf Release Blob `a17b9d4721f7fc485346acb1af1b54b3dc485692`; `ContinuumAtomicPairedBundleStore.kt` Blob `809effe5631ea82628173225e48af0baa0955951`. | Private tatsächlich trainierte Gewichte / Konversationsverhalten sind nicht per GitHub belegbar. | Training/Replay/Last-Known-Good vor jedem Upgrade sichern. |
| Chat-Gedächtnis | `ContinuumMemoryDialogue.kt` Release Blob `509058f8d2e3e8235a2c76c9271db6b872fcba03`. | QF1 [#66](https://github.com/Ullmann27/device-ai/pull/66), QF2 [#67](https://github.com/Ullmann27/device-ai/pull/67) noch **nicht** ausgeliefert; reproduziertes Fehlzitat-Problem besteht in 0.3.12 weiter. | Vermeidung sachfremder Wikipedia-Belege und fälschlicher Sicherheit. |
| Android-Termux-Builder | Android Builder Activity Release Blob `f638907b721c689cd9cb26865696726a5f2a0f14`; `scripts/continuum-mobile-builder.mjs` Blob `d10149a2729cf75c5730163c0cacf49cbc10dd46`. | CLI kennt auf Release **nur `doctor/build/status`**. `scripts/continuum-mobile-coding.mjs` und `ContinuumMobileCodingJob.kt` sind auf Release nicht vorhanden. | Das Coding-Agent-UI aus [#58](https://github.com/Ullmann27/device-ai/pull/58) darf **nicht ohne** den Worker aus [#57](https://github.com/Ullmann27/device-ai/pull/57) übernommen werden. |
| Coding-Agent | In getrenntem [PR #57](https://github.com/Ullmann27/device-ai/pull/57): geprüfte Receipts, kanonischer Teamleader, echte begrenzte Testläufe (laut PR-Protokoll). [#58](https://github.com/Ullmann27/device-ai/pull/58) fügt Android-Eingaben, bestätigten Start und Receipt-Verifikation hinzu. | Beide sind auf anderer Stack-Basis als 0.3.12; direkte gemeinsame Integrations-, Android-/Termux-Gerätetests offen. | Kein behaupteter fertiger autonomer Handy-Programmieragent; keine Code-Versperrung oder automatisches Merge. |
| CDX1-Intelligenztest | Release enthält CDX1 und Gewichts-/Checkpointverwaltung. | `ContinuumIntelligenceOnDevice.kt` auf Release **nicht vorhanden**; [#59](https://github.com/Ullmann27/device-ai/pull/59) implementiert 20 positive + fünf unbeantwortbare Proben separat, ohne Produktions-Promotion. | Trainingsverlust/Generationsqualität getrennt auswerten. |
| CSX1-Forschungsmodell | Nicht die aktive Chat-Kernarchitektur der ausgelieferten Version. | [#62](https://github.com/Ullmann27/device-ai/pull/62): V8/I1 und kalibrierte Enthaltung nur Forschung. [#68](https://github.com/Ullmann27/device-ai/pull/68): Modell-SHA-Prüfung und Anschlussreparatur im Experiment-Runner. | Ergebnisse der synthetischen Benchmarks nicht als freies Sprachverständnis verkaufen. |

**Direkt überprüfte Abwesenheiten:** Am gepinnten Release-Commit lieferten `scripts/continuum-mobile-coding.mjs`, `app/src/main/java/at/heinz/deviceai/ContinuumMobileCodingJob.kt` und `app/src/main/java/at/heinz/deviceai/learning/ContinuumIntelligenceOnDevice.kt` GitHub `404 NOT_FOUND`. Das belegt nur das **Fehlen an diesen erwarteten Pfaden im Release**, nicht dass weltweit kein entsprechender Algorithmus existiert.

## 2. Gefundene konkrete Reparaturen (nicht in der APK)

**QF2 / PR #67:** QF1 prüfte Quellenanker vor allem bei genau einem langen Fachbegriff. QF2 verlangt in unbestätigten Quellen mehrere technische Bezeichnungen im selben Beleg und exakte gemischte Buchstaben/Ziffern-Kennungen. Bei der zusätzlichen Probe wurde ein falscher Nichttreffer entdeckt: `Wie funktioniert Fehlerrückführung?` wurde wegen `funktioniert` abgewiesen. Source-spezifische Frageoperatoren werden jetzt aus den zu prüfenden Fachankern entfernt; Besitzerfakten-/Gewichts-Ranking bleibt unverändert. JS und Kotlin entsprechend aktualisiert. Fünf lokale JS-Regressionsprüfungen auf einem aus den Änderungen aufgebauten Dateisatz liefen durch; vollständige Node-Suite, aktueller Android-Gradle-Build und Samsung fehlen.

**I1 / PR #68:** Der auf einem bereits vorhandenen Branch entwickelte checkpointgebundene `ContinuumCertifiedAbstention.answer(model,evidence,question,certificate)`-Pfad entfernte die alte `answer(prediction,certificate)`-Signatur. Der `certify`-Runner rief jedoch noch die alte Signatur auf; er wurde auf den modellgebundenen Aufruf umgestellt. Kotlin-Neukompilierung und erneute Benchmark-Messung sind noch offen. `Certificate` bleibt außerhalb einer streng kontrollierten Kalibrierpipeline konstruierbar: keine kryptographische Kalibrier-Attestierung behaupten.

## 3. Integrationsreihenfolge ohne Blind-Merge

1. **Stabilen Ausgangspunkt bewahren:** verifizierte 0.3.12 als Vergleich; vor Integration Release-SHA, vorhandene Signatur-Zertifikatsidentität und private SWB1/CDX1-Daten separat dokumentieren. Keine Deinstallation.
2. **Chat-Quellen:** QF1 → QF2 in einem vom Release abgeleiteten, vollständig editierbaren Integrationsbranch. Regressionen zu langen/kurzen Fachbegriffen, Mischfragen, unbestätigten Quellen, Besitzerfakten und Mehrwortfragen. Dabei kein automatischer Rückschluss auf Wahrheit.
3. **Echter Intelligenzprüfstand:** PR #59 nur lesend gegen die vorhandenen trainierten SWB1/CDX1-Paargewichte integrieren; Exaktheit und Anzahl leerer/falscher Antworten messen, kein verdecktes Training, keine falschen Erfolge bei fehlendem Checkpoint.
4. **Coding-Agent getrennt:** PR #57 → #58 als **eine** Ende-zu-Ende-Kette gegen den tatsächlichen Release-Quellstand übertragen. Besonders überprüfen: Termux `RUN_COMMAND`, `code-test/code-cancel`-CLI, einmalige UUID/Callback-Bindung, striktes Receipt-Format, Owner-Bestätigung, Storno-/Timeout-/Neustartverhalten, Git-Worktree-Isolation, begrenzte Ressourcen. Vor Erprobung keine Behauptung, dass das Gerät bereits eigenständig Apps programmieren kann.
5. **CSX1 zunächst im Schatten:** PR #62 → #68 separat testen; echte Kotlin-Compilation, Benchmark-Reproduktion, Hashbindung bei Inferenz, unabhängige neue Holdouts. CSX1 nicht stillschweigend zum produktiven Chat machen.
6. **Erst nach geprüften Kombinationen:** vollständig neue Version und Signatur-/Rollback-Prüfung, danach explizite Owner-Installation; keine sofortige APK erforderlich.

**Konfliktpunkte:** Android `AgentActivity.kt`, `ContinuumTrainingMonitorActivity.kt`, `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileBuildResultReceiver.kt`, `ContinuumLearningCore.kt` und die Node-Builder-/Teamleader-Routen können in verschiedenen Stacks dieselben Funktionsbereiche verändern. Diese Dateien im Integrationszweig jeweils gegen den Ausgangs-SHA vergleichen, semantisch zusammenführen und vollständig testen, nie blind überschreiben.

## 4. Verbindliche Freigabegates

- **Quelltreue:** jeder Patch rückverfolgbar auf Quellbranch/Commit, alle beteiligten Versionen dokumentiert; kein `main`-Merge als Abkürzung. Keine Geheimnisse in Diff/Logs.
- **Node:** `node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs` (vollständiger Checkout, Compiler-abhängige Tests dürfen nicht stillschweigend übersprungen werden).
- **JVM/Kotlin:** alle neuen und alten JUnit-Tests, inklusive JS/Kotlin-Paaritätsfälle, Modellversion-/CRC-/Checkpoint-Wiederherstellung, Stornierung und Offline-Verhalten.
- **Android:** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiMentorPreview=true` im korrekten vollständigen SDK. Resultate und exakte Build-Quelle dokumentieren.
- **Releaseidentität:** beim Folgerelease Versionscode >15, gleiches Paket `at.heinz.deviceai.mentorpaired` und derselbe **bereits bekannte persistente Signing-Zertifikatsfingerprint** `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`; `apksigner`, `aapt`, SHA-256 prüfen. Zertifikatsmaterial **niemals** im Repo.
- **Samsung:** bestehende APK **überinstallieren**, nie löschen; Sprach-/Fold-/Tastatur-/Dialogtest, Gedächtnis/Quellenvergleich, vorhandene SWB1-/CDX1-Hash-/Checkpoint- und Archivkontrolle vor/nach Installation; echte lokale Termux-Codingrunde nur nach Besitzerbestätigung.
- **Dokumentationsstatus:** `SOURCE_INTEGRATED`, `JVM_TESTED`, `APK_BUILT`, `DEVICE_VERIFIED` strikt getrennt; fehlende Stufen sichtbar belassen.

## 5. Tool-/Evidenzgrenzen

Render wurde in dieser Runde **nicht** ausgelesen oder deployed: Die verbundene Render-Aktion meldete `no workspace selected` und verlangt ausdrückliche Auswahl durch den Besitzer. Auf keinen der potentiell mehreren Workspaces wurde geraten. GitHub meldete am PR-#67-Commit keine GitHub-Workflow-Runs/Statuschecks; das ist **kein** Beweis, dass der Code fehlgeschlagen oder bestanden ist.

Die hier dokumentierten Programmänderungen sind in separaten Draft-PRs gehalten. Ein React/Android-Layout, ein Funktionsname, eine korrekte Signatur oder ein PR-Text allein beweist keine ausführbare Verbindung. Bestehende Lerngewichte, Owner-Daten, `main`, Render-Konfiguration und die veröffentlichte APK wurden in dieser Audit-Runde nicht mutiert.


## 6. Vollständiges GitHub-Bestandssicherungsinventar (22.10.2026, ca. 16:31 MESZ)

Dieses Inventar wurde unmittelbar aus der GitHub-Branchsuche **über beide Cursor-Seiten** und der Pull-Request-REST-Auflistung gewonnen. Es zählt **114 Branches**, **67 PRs** und hält auch unscheinbare Backup-/Experiments-/Agenten-Zustandszweige fest. Es ist ein **Referenzindex, kein Mergeauftrag**. Die Branch-Namen und PR-Status sind Momentaufnahmen; vor Änderungen aktuelle Remote-HEADs erneut abfragen. Die tatsächliche gerade laufende Claude-Sitzung kann aus GitHub nicht direkt ausgelesen werden.

### 6.1 Neuester Opus-Fortschritt seit PR #71

- [PR #72](https://github.com/Ullmann27/device-ai/pull/72): `opus/continuum-i2-selective-risk-20261022`, HEAD `1c72f8a28bd2a33f6aeb069fc1e4856d1cb802d1`; 26 echte JUnit-Prüfungen laut Opus-Dokumentation. Antwortbedingtes Risiko mit Holm-Korrektur, aber alte 12/35-Paraphrasengrenze unverändert; keine APK-Promotion.
- [PR #73](https://github.com/Ullmann27/device-ai/pull/73): `opus/continuum-synapse-v2-20261022`, HEAD `c49c48eb0151047e1b290165ee32444a3a756d24`; realer JUnit-Nachvollzug, Finite-Difference-Gradientenprüfung, belegtes Scheitern der `one-hot`-HRR-„Inversion“, Kennungs-/Subjektverlust und 4 unzulässige Antworten bei 13 adversarialen Proof-Fällen. Neue TRAIN 780 / VALIDATION 322 / eingefrorene TEST 592; `cycle1-select-validation-OBSOLETE-pairing-bug.txt` ist ausdrücklich *veraltet* nach erkanntem Paarungsfehler. Keine unüberprüften Verbesserungen als bestätigt verkaufen.
- [PR #74](https://github.com/Ullmann27/device-ai/pull/74): `opus/continuum-premium-ui-20261022`, HEAD `3a45ed33b94ea5f8f0663530c1c9125bfb3d6e59`; Referenzbilder, Design-Tokens, lizenzierte Schriftdateien, Hologramm-Renderer, reiner Painter und JVM-Vorschau. **Noch offen:** Android-Painter, reale Bildschirm-/Karten-/Fold-Anbindung und Gradle-/Geräteprüfung. Nicht als fertig deklarieren. Der nächste Forschungsauftrag soll diese UI-Arbeit nicht unterbrechen oder überschreiben.

### 6.2 Schlüssellinien ohne Doppelentwicklungen

- Frühere kognitive Architektur und Zustand: PR #1–#19, #22–#28, einschließlich Feedback-Ledger, widerrufbare Besitzerkorrektur, epistemische Grenzen, planbare Forschungs-/Reparaturzyklen sowie `continuum-state` und `continuum-agent-state`. Prüfe vor Neuimplementierung existierende Rulebook-/World-Model-/Memory-/Reasoning-Funktionen.
- Öffentliche Recherche, Sicherheit, Lernkerne: PR #30–#38, #39–#48 (CRE1, neuronale Lernregeln, Archiv, Quellen, periodische lokale Worker, holografischer Ausgangsrenderer, DNA-Guardian, SWB1/CDX1-Paarbindung und atomare LKG-Checkpoints). Optionale Cloud-Mentor-Schnittstellen sind **keine** Pflicht für lokale KI.
- Coding- und Release-System: PR #49–#58, inklusive llama.cpp/GGUF-Forschung, kanonischem lokalen Teamleiter, isoliertem Reparaturloop, echten Node-/Kotlin-Prüfquittungen, Render- und APK-Auslieferung. Keine zweite Agentenpipeline erstellen. Besondere Abhängigkeit PR #53 → #57 → #58.
- Intelligenzstrang: PR #59–#62 → #68 → #72, trainierbarer CSX1-Extraktor, sichere Enthaltung, modellgebundene und bedingte Kalibrierung; Stand bei ungewohnten Satzformen weiterhin schwach. Neuer Zweig PR #71 → #73 für Synapse/Reasoner.
- Chat-Quellennachweise: PR #66 → #67 → #70; Vorzeichen-/Unicode-/Kurzankerprüfungen nicht ungeprüft mit allgemeinen Besitzerkorrekturen mischen.
- UI und Release: PR #63 → #64 → #65; neue separate Referenz-Bild/Design-Arbeit PR #74. Signierte v0.3.12 bleibt Vergleichsstand. Hauptbranch `main` ist **nicht** die aktuelle ausgelieferte Quelle.

### 6.3 Alle Branches (unabhängig davon, ob sie einen offenen PR haben)

**Geschützte historische Backups (27):**
- `backup/continuum-day-close-20261017-f04f7235`
- `backup/continuum-main-20261017-190f5eab`
- `backup/continuum-pr19-20261017-ec4b4cae`
- `backup/continuum-pr28-20261017-4c4b3fd8`
- `backup/continuum-pr28-autonomous-evidence-gate-20261018-2e06c432`
- `backup/continuum-pr28-cefr-prospective-benchmark-20261018-5944aec1`
- `backup/continuum-pr28-cefr-research-20261018-084c083f`
- `backup/continuum-pr28-cegis-handoff-20261018-762cb80a`
- `backup/continuum-pr28-evidence-ledger-cefr-shadow-20261018-7387ae9c`
- `backup/continuum-pr28-four-powers-cefr-20261018-b544bb57`
- `backup/continuum-pr28-m4-acceptance-20261017-e9150e5`
- `backup/continuum-pr28-m4-closed-loop-gate-20261017-f7308898`
- `backup/continuum-pr28-m4-junit-node22-20261017-70a9db83`
- `backup/continuum-pr28-m4-junit-single-read-20261018-33b9c4cb`
- `backup/continuum-pr28-m4-real-e2e-proof-20261017-87313052`
- `backup/continuum-pr28-m4-required-e2e-20261017-62fa6cf0`
- `backup/continuum-pr28-m4-verified-tests-20261017-b9e056cb`
- `backup/continuum-pr28-m4-worker-failclosed-20261017-67de0069`
- `backup/continuum-pr28-m4-xml-proof-hardening-20261018-c1249ae2`
- `backup/continuum-pr28-repair-critic-control-boundary-20261018-7c075c56`
- `backup/continuum-pr28-repair-recursion-report-20261017-eb089bb3`
- `backup/continuum-pr28-secured-20261017-0d62d0aa`
- `backup/continuum-pr28-worker-heartbeat-20261017-5d92eb11`
- `backup/mentor-apk-before-pr50-update-20261021`
- `backup/render-apk-before-astra-integration-20261021`
- `backup/render-mentor-before-pr42-delivery-20261020`
- `backup/render-mentor-service-before-pr42-20261020`

**Experiment und Integrations-Releases (6):**
- `artifacts/sol61-preview-20261019`
- `docs/device-ai-open-source-reference-pack`
- `experiment/continuum-synapse-zero-20261022`
- `handoff/sol61-continuum-20261019`
- `integration/continuum-astra-20261021`
- `review/render-first-portable-build-20261022`

**Astra/Render (17):**
- `astra/continuum-always-on-web-20261018`
- `astra/continuum-atomic-paired-checkpoints-20261021`
- `astra/continuum-blacklab-20261019`
- `astra/continuum-dna-guardian-20261018`
- `astra/continuum-dual-core-binding-20261021`
- `astra/continuum-experiment-charter-20261019`
- `astra/continuum-holographic-ui-20261019`
- `astra/continuum-integrated-apk-20261019`
- `astra/continuum-local-coding-milestone-20261021`
- `astra/continuum-offline-public-research-20261018`
- `astra/continuum-optional-groq-mentor-20261019`
- `astra/continuum-research-bridge-20261019`
- `astra/continuum-research-learning-gate-20261019`
- `astra/continuum-research-verification-20261021`
- `astra/continuum-samsung-research-hotfix-20261019`
- `astra/render-mentor-apk-builder-20261019`
- `astra/runner-isolation-diagnostic-20261019`

**Claude Opus (7):**
- `opus/continuum-app-navigation-20261022`
- `opus/continuum-app-navigation-wip-archive-20261022`
- `opus/continuum-csx1-abstention-20261022`
- `opus/continuum-i2-selective-risk-20261022`
- `opus/continuum-mobile-coding-e2e-20261022`
- `opus/continuum-premium-ui-20261022`
- `opus/continuum-synapse-v2-20261022`

**ChatGPT (15):**
- `chatgpt/continuum-apk-preview-20261021`
- `chatgpt/continuum-app-navigation-completion-20261022`
- `chatgpt/continuum-argon-qf2-source-proof-20261022`
- `chatgpt/continuum-bundled-quality-qf2-20261022`
- `chatgpt/continuum-csx1-research-gate-20261022`
- `chatgpt/continuum-i1-model-binding-20261022`
- `chatgpt/continuum-intelligence-lab-readonly-20261022`
- `chatgpt/continuum-llama-recovery-20261021`
- `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022`
- `chatgpt/continuum-mentor-update-20261021`
- `chatgpt/continuum-navigation-signed-delivery-20261022`
- `chatgpt/continuum-opus-android-coding-ui-20261022`
- `chatgpt/continuum-release-integration-audit-20261022`
- `chatgpt/continuum-render-delivery-audit-20261022`
- `chatgpt/continuum-residual-pointer-built-20261022`

**Sol (2):**
- `sol/continuum-isolated-research-20261021`
- `sol/continuum-quality-20261021`

**Copilot/Deep Research (23):**
- `copilot/add-benchmark-measurement-harness`
- `copilot/add-deterministic-jvm-unit-tests`
- `copilot/continuum-agent-repair-demo`
- `copilot/continuum-android-integration-20261015`
- `copilot/continuum-argon-source-review-20261015`
- `copilot/continuum-atlas-provenance-gate-20261020`
- `copilot/continuum-contrastive-recovery-20261015`
- `copilot/continuum-feedback-ledger-20261015`
- `copilot/continuum-owner-answer-corrections-20261015`
- `copilot/continuum-phase1-direct`
- `copilot/continuum-phase1-runtime`
- `copilot/continuum-phase2-adaptive`
- `copilot/continuum-phase2-intelligence-direct`
- `copilot/continuum-selective-correction-revocation-20261015`
- `copilot/copilotcontinuum-phase2-intelligence-direct`
- `copilot/deepresearch-p0-durable-agent-memory`
- `copilot/fix-64243701-1415878393-f443f816-2b3d-4c73-8097-d7df0cb722ca`
- `copilot/inspect-extend-compute-distributed-task-code`
- `copilot/main-task-review`
- `copilot/part3-memory-model-ecology`
- `copilot/part4-agent-research`
- `copilot/pr-12-working-first-vertical-slice`
- `deepresearch/p0-durable-agent-memory`

**Bestehender Agent und Arbeitszustände (14):**
- `continuum/argon-safety-review-20261017`
- `continuum/backlog-agent-v2-20261015`
- `continuum/contrastive-recovery-20261015`
- `continuum-agent/activate-hourly-developer-agent`
- `continuum-agent/demo/38696111909/fault-kt-historical-escaped-newline-48bd150`
- `continuum-agent/demo/38696111909/repair-kt-historical-escaped-newline-48bd150`
- `continuum-agent/repair-0bf848a1be-ad28fd1d`
- `continuum-agent/repair-9e1412ed3f-2f132248`
- `continuum-agent/repair-22582016d3-e2cd043b`
- `continuum-agent/repair-ec14c4b94e-a82e4caf`
- `continuum-agent-state`
- `continuum-state`
- `development/continuum-backlog-20261015`
- `work/continuum-phase1-direct`

**Main / historische Versionszweige (3):**
- `main`
- `v0.2-redesign`
- `v0.3-agent`

### 6.4 Alle Pull Requests, inklusive sehr kleiner/alter Entwicklungsarbeiten

- [#1](https://github.com/Ullmann27/device-ai/pull/1) | closed / merged | `v0.3-agent` → `main` | Device AI v0.3 – secure cloud agent, typed tools and Fold UI
- [#2](https://github.com/Ullmann27/device-ai/pull/2) | closed / merged | `copilot/main-task-review` → `main` | Device AI cognitive architecture: Parts 1–3
- [#3](https://github.com/Ullmann27/device-ai/pull/3) | open / draft | `copilot/part3-memory-model-ecology` → `main` | Part 3: memory, experience and model ecology
- [#4](https://github.com/Ullmann27/device-ai/pull/4) | closed / draft | `docs/device-ai-open-source-reference-pack` → `copilot/part3-memory-model-ecology` | Docs: Device AI open-source reference pack
- [#5](https://github.com/Ullmann27/device-ai/pull/5) | open / draft | `copilot/part4-agent-research` → `copilot/part3-memory-model-ecology` | Part 4: bounded agent, tools, skills and research
- [#6](https://github.com/Ullmann27/device-ai/pull/6) | open / draft | `copilot/deepresearch-p0-durable-agent-memory` → `copilot/part4-agent-research` | P0 Deep Research: durable checkpoints, replay guard and hybrid memory retrieval
- [#8](https://github.com/Ullmann27/device-ai/pull/8) | open / draft | `copilot/continuum-phase1-runtime` → `copilot/deepresearch-p0-durable-agent-memory` | Continuum Phase 1: persistent seed, context compiler and compute fabric
- [#9](https://github.com/Ullmann27/device-ai/pull/9) | open / draft | `work/continuum-phase1-direct` → `copilot/deepresearch-p0-durable-agent-memory` | Continuum Phase 1 direct implementation
- [#10](https://github.com/Ullmann27/device-ai/pull/10) | open / draft | `copilot/continuum-phase1-direct` → `copilot/deepresearch-p0-durable-agent-memory` | Continuum Phase 1 CI validation
- [#11](https://github.com/Ullmann27/device-ai/pull/11) | open / draft | `copilot/continuum-phase2-intelligence-direct` → `work/continuum-phase1-direct` | Continuum Phase 2: effective intelligence and proof-carrying learning
- [#12](https://github.com/Ullmann27/device-ai/pull/12) | open | `copilot/copilotcontinuum-phase2-intelligence-direct` → `copilot/continuum-phase2-intelligence-direct` | Route hard local-chat tasks through the bounded reasoning chain as an unverified advisory
- [#14](https://github.com/Ullmann27/device-ai/pull/14) | open / draft | `copilot/add-deterministic-jvm-unit-tests` → `work/continuum-phase1-direct` | Add deterministic Continuum persistence failure tests
- [#16](https://github.com/Ullmann27/device-ai/pull/16) | open / draft | `copilot/add-benchmark-measurement-harness` → `work/continuum-phase1-direct` | Add benchmark harness comparing single-pass baseline vs Continuum pipeline
- [#17](https://github.com/Ullmann27/device-ai/pull/17) | open / draft | `copilot/fix-64243701-1415878393-f443f816-2b3d-4c73-8097-d7df0cb722ca` → `work/continuum-phase1-direct` | Harden untrusted research evidence boundaries
- [#18](https://github.com/Ullmann27/device-ai/pull/18) | open / draft | `copilot/inspect-extend-compute-distributed-task-code` → `work/continuum-phase1-direct` | Harden compute task bounds and result verification
- [#19](https://github.com/Ullmann27/device-ai/pull/19) | open / draft | `copilot/pr-12-working-first-vertical-slice` → `copilot/copilotcontinuum-phase2-intelligence-direct` | Device AI Continuum: Learning Core, Cognitive Runtime und Offline-Teamleiter (Draft)
- [#21](https://github.com/Ullmann27/device-ai/pull/21) | open / draft | `continuum-agent/repair-22582016d3-e2cd043b` → `copilot/continuum-agent-repair-demo` | [Continuum agent] KOTLIN_TYPO_REFERENCE repairs the deliberately broken Android build (acceptance demo)
- [#22](https://github.com/Ullmann27/device-ai/pull/22) | open / draft | `continuum-agent/activate-hourly-developer-agent` → `main` | ci(agent): activate the hourly Continuum developer-agent check (owner decision)
- [#23](https://github.com/Ullmann27/device-ai/pull/23) | closed / merged / draft | `copilot/continuum-contrastive-recovery-20261015` → `copilot/pr-12-working-first-vertical-slice` | Experimental cross-runtime contrastive retrieval and offline learning error recovery
- [#24](https://github.com/Ullmann27/device-ai/pull/24) | open / draft | `copilot/continuum-android-integration-20261015` → `copilot/continuum-contrastive-recovery-20261015` | Android integration: offline job observability, bounded contrastive probe and source retry repair
- [#25](https://github.com/Ullmann27/device-ai/pull/25) | open / draft | `copilot/continuum-feedback-ledger-20261015` → `copilot/pr-12-working-first-vertical-slice` | Continuum: persist explicit feedback in revocable experience ledger
- [#26](https://github.com/Ullmann27/device-ai/pull/26) | open / draft | `copilot/continuum-owner-answer-corrections-20261015` → `copilot/continuum-feedback-ledger-20261015` | Continuum: owner-corrected answers for exact questions with auditable revocation
- [#27](https://github.com/Ullmann27/device-ai/pull/27) | open / draft | `copilot/continuum-selective-correction-revocation-20261015` → `copilot/continuum-owner-answer-corrections-20261015` | Continuum: revoke one owner-confirmed answer correction without affecting other questions
- [#28](https://github.com/Ullmann27/device-ai/pull/28) | open / draft | `continuum/argon-safety-review-20261017` → `copilot/pr-12-working-first-vertical-slice` | Continuum: verifizierte Node-/Backend-Stabilisierung und lokale Modellschutzmaßnahmen
- [#30](https://github.com/Ullmann27/device-ai/pull/30) | open / draft | `astra/continuum-dna-guardian-20261018` → `continuum/argon-safety-review-20261017` | Continuum: signed DNA integrity guardian for autonomous Android teamleader
- [#31](https://github.com/Ullmann27/device-ai/pull/31) | open / draft | `astra/continuum-always-on-web-20261018` → `continuum/argon-safety-review-20261017` | Device AI: persistent owner-controlled ALWAYS_ON web consent and Android tool integration
- [#32](https://github.com/Ullmann27/device-ai/pull/32) | open / draft | `astra/continuum-offline-public-research-20261018` → `astra/continuum-always-on-web-20261018` | Continuum: no-paid-API persistent public research worker with bounded source memory
- [#33](https://github.com/Ullmann27/device-ai/pull/33) | open / draft | `astra/continuum-research-learning-gate-20261019` → `astra/continuum-offline-public-research-20261018` | Continuum: verified extractive CRE1 shadow learning and signed P-256 research packages
- [#34](https://github.com/Ullmann27/device-ai/pull/34) | open / draft | `astra/continuum-blacklab-20261019` → `astra/continuum-research-learning-gate-20261019` | Continuum PR34: CRE1 cached projection training, canary audits and bounded daemon shadow bridge
- [#35](https://github.com/Ullmann27/device-ai/pull/35) | open / draft | `astra/continuum-holographic-ui-20261019` → `astra/continuum-blacklab-20261019` | Continuum: animated native hologram with real Android voice and task states
- [#36](https://github.com/Ullmann27/device-ai/pull/36) | open / draft | `handoff/sol61-continuum-20261019` → `continuum/argon-safety-review-20261017` | SOL 6.1: vollständige Code- und Artefaktübergabe an Astra
- [#37](https://github.com/Ullmann27/device-ai/pull/37) | open / draft | `astra/continuum-integrated-apk-20261019` → `handoff/sol61-continuum-20261019` | Continuum integrated APK: latest Sol Android + safe research, CRE1 and DNA guardian
- [#38](https://github.com/Ullmann27/device-ai/pull/38) | open / draft | `astra/continuum-samsung-research-hotfix-20261019` → `astra/continuum-integrated-apk-20261019` | Samsung hotfix: prevent irrelevant research evidence and oversized follow-up aborts
- [#39](https://github.com/Ullmann27/device-ai/pull/39) | open / draft | `astra/continuum-optional-groq-mentor-20261019` → `astra/continuum-samsung-research-hotfix-20261019` | Continuum: opt-in free-tier Groq language mentor without exposing personal memory
- [#40](https://github.com/Ullmann27/device-ai/pull/40) | open / draft | `astra/render-mentor-apk-builder-20261019` → `astra/continuum-optional-groq-mentor-20261019` | Continuum Mentor pairing: owner-approved Groq Android connection and isolated APK
- [#42](https://github.com/Ullmann27/device-ai/pull/42) | open / draft | `astra/continuum-experiment-charter-20261019` → `backup/render-mentor-before-pr42-delivery-20261020` | Android-first Experimentvertrag + eigener lokaler APK-Build-Adapter (Draft)
- [#43](https://github.com/Ullmann27/device-ai/pull/43) | closed / merged | `sol/continuum-quality-20261021` → `astra/continuum-experiment-charter-20261019` | Continuum: überprüfbares Lernen, sichere Wiederaufnahme und Trainings-Monitor
- [#44](https://github.com/Ullmann27/device-ai/pull/44) | open / draft | `astra/continuum-dual-core-binding-20261021` → `astra/continuum-experiment-charter-20261019` | Device AI Continuum: SWB1/CDX1 als versionsfeste lernende Zwei-Kerne-Pipeline
- [#45](https://github.com/Ullmann27/device-ai/pull/45) | open / draft | `astra/continuum-atomic-paired-checkpoints-20261021` → `astra/continuum-dual-core-binding-20261021` | Continuum CDX1: atomarer Paar-Checkpoint mit geprüftem Last-Known-Good
- [#46](https://github.com/Ullmann27/device-ai/pull/46) | open / draft | `astra/continuum-research-verification-20261021` → `astra/continuum-experiment-charter-20261019` | Verify public-source failure recovery and Android worker safety
- [#47](https://github.com/Ullmann27/device-ai/pull/47) | closed / merged / draft | `astra/continuum-atomic-paired-checkpoints-20261021` → `integration/continuum-astra-20261021` | Integration: SWB1/CDX1-Paar-Checkpoints in Astra-Forschungsreparaturen
- [#48](https://github.com/Ullmann27/device-ai/pull/48) | open / draft | `integration/continuum-astra-20261021` → `astra/continuum-experiment-charter-20261019` | Continuum 0.3.9 integrated: public research safety + dual-core CDX1 + atomic checkpoints
- [#49](https://github.com/Ullmann27/device-ai/pull/49) | open / draft | `astra/continuum-local-coding-milestone-20261021` → `integration/continuum-astra-20261021` | Local coding: scoped numeric llama.cpp candidates, canonical repair and independent verification
- [#50](https://github.com/Ullmann27/device-ai/pull/50) | open / draft | `chatgpt/continuum-llama-recovery-20261021` → `sol/continuum-isolated-research-20261021` | Continuum recovery: llama.cpp schema fix, durable Teamleader CLI and Linux isolation proof
- [#51](https://github.com/Ullmann27/device-ai/pull/51) | open / draft | `chatgpt/continuum-apk-preview-20261021` → `chatgpt/continuum-llama-recovery-20261021` | Continuum: separate PR50 Android preview APK build and verification delivery
- [#52](https://github.com/Ullmann27/device-ai/pull/52) | open / draft | `astra/render-mentor-apk-builder-20261019` → `backup/mentor-apk-before-pr50-update-20261021` | Continuum 0.3.11 signed in-place Mentor Pair update — owner-safe review
- [#53](https://github.com/Ullmann27/device-ai/pull/53) | open / draft | `sol/continuum-isolated-research-20261021` → `astra/continuum-local-coding-milestone-20261021` | Continuum: isolated repair execution, persistent worker and measured local GGUF research
- [#55](https://github.com/Ullmann27/device-ai/pull/55) | open / draft | `chatgpt/continuum-render-delivery-audit-20261022` → `astra/render-mentor-apk-builder-20261019` | Continuum: Render APK-Auslieferung unabhängig und datenverlustfrei prüfen
- [#56](https://github.com/Ullmann27/device-ai/pull/56) | open / draft | `review/render-first-portable-build-20261022` → `astra/render-mentor-apk-builder-20261019` | Continuum: Render-first build entrypoint without GitHub Actions
- [#57](https://github.com/Ullmann27/device-ai/pull/57) | open / draft | `opus/continuum-mobile-coding-e2e-20261022` → `sol/continuum-isolated-research-20261021` | Mobile coding job: Termux channel → canonical Teamleader → verifiable receipt (EXP-00/M4.2, draft)
- [#58](https://github.com/Ullmann27/device-ai/pull/58) | open / draft | `chatgpt/continuum-opus-android-coding-ui-20261022` → `opus/continuum-mobile-coding-e2e-20261022` | Continuum M4: bestehenden Opus-Coding-Job in Android Builder anzeigen und sicher auslösen
- [#59](https://github.com/Ullmann27/device-ai/pull/59) | open / draft | `chatgpt/continuum-intelligence-lab-readonly-20261022` → `astra/render-mentor-apk-builder-20261019` | Continuum EXP-04/05: CDX1-Intelligenz-Labor mit 20+5 echten Antwortprüfungen (read-only)
- [#60](https://github.com/Ullmann27/device-ai/pull/60) | open / draft | `chatgpt/continuum-csx1-research-gate-20261022` → `chatgpt/continuum-intelligence-lab-readonly-20261022` | CSX1 Forschung: Argon-Span-Pointer kritisch geprüft und sichere Opus-Integrationsgates dokumentiert
- [#61](https://github.com/Ullmann27/device-ai/pull/61) | open / draft | `chatgpt/continuum-residual-pointer-built-20261022` → `chatgpt/continuum-csx1-research-gate-20261022` | CSX1: selbst implementierter trainierbarer Residual-Pointer mit JVM-Gegenprobe und Offline-Selbstlern-Gate
- [#62](https://github.com/Ullmann27/device-ai/pull/62) | open / draft | `opus/continuum-csx1-abstention-20261022` → `chatgpt/continuum-residual-pointer-built-20261022` | CSX1 + milestone I1: certified honest abstention (draft, research shadow)
- [#63](https://github.com/Ullmann27/device-ai/pull/63) | closed / merged / draft | `opus/continuum-app-navigation-20261022` → `astra/render-mentor-apk-builder-20261019` | App navigation: work areas always one tap away (draft)
- [#64](https://github.com/Ullmann27/device-ai/pull/64) | open / draft | `chatgpt/continuum-app-navigation-completion-20261022` → `opus/continuum-app-navigation-20261022` | Continuum UI: Opus-Navigation nach Nutzungslimit vervollständigt (mobile + Fold/Desktop)
- [#65](https://github.com/Ullmann27/device-ai/pull/65) | open / draft | `chatgpt/continuum-navigation-signed-delivery-20261022` → `chatgpt/continuum-app-navigation-completion-20261022` | APK 0.3.12 (15): signierte datenbewahrende Continuum-Navigation über vorhandenen Render-Build
- [#66](https://github.com/Ullmann27/device-ai/pull/66) | open / draft | `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022` → `chatgpt/continuum-navigation-signed-delivery-20261022` | QF1 · Continuum Chat-Gedächtnis: falsche Wikipedia-Zitate bei unbekannten Fachbegriffen vermeiden
- [#67](https://github.com/Ullmann27/device-ai/pull/67) | open / draft | `chatgpt/continuum-bundled-quality-qf2-20261022` → `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022` | QF2 · Mehrteilige Fachfragen und exakte Modellkennungen im Chat-Quellennachweis
- [#68](https://github.com/Ullmann27/device-ai/pull/68) | open / draft | `chatgpt/continuum-i1-model-binding-20261022` → `opus/continuum-csx1-abstention-20261022` | I1 · CSX1-Zertifikatsbindung und reparierter Experiment-Runner (Forschung)
- [#69](https://github.com/Ullmann27/device-ai/pull/69) | open / draft | `chatgpt/continuum-release-integration-audit-20261022` → `chatgpt/continuum-navigation-signed-delivery-20261022` | Continuum Release-Inventur: reale 0.3.12-Anschlüsse, Abhängigkeiten und Sicherheitsgates
- [#70](https://github.com/Ullmann27/device-ai/pull/70) | open / draft | `chatgpt/continuum-argon-qf2-source-proof-20261022` → `chatgpt/continuum-bundled-quality-qf2-20261022` | Argon-Review · QF2+ Quellenbelege: Node-Syntaxfix, Kurzanker und Deutsch-Komposita
- [#71](https://github.com/Ullmann27/device-ai/pull/71) | open / draft | `experiment/continuum-synapse-zero-20261022` → `chatgpt/continuum-navigation-signed-delivery-20261022` | EXP-SYNAPSE-ZERO · Eigenes 2-Hop-Shadow-Reasoning und 4096-Parameter-Rollenlernen
- [#72](https://github.com/Ullmann27/device-ai/pull/72) | open / draft | `opus/continuum-i2-selective-risk-20261022` → `chatgpt/continuum-i1-model-binding-20261022` | I2 · Bedingte Antwortqualität P(falsch | beantwortet) zertifizieren (Forschung, Draft)
- [#73](https://github.com/Ullmann27/device-ai/pull/73) | open / draft | `opus/continuum-synapse-v2-20261022` → `experiment/continuum-synapse-zero-20261022` | EXP-SYNAPSE-ZERO v2: measured review, new frozen benchmark and A0–A4 ablations (draft, research shadow)
- [#74](https://github.com/Ullmann27/device-ai/pull/74) | open / draft | `opus/continuum-premium-ui-20261022` → `astra/render-mentor-apk-builder-20261019` | Continuum Premium-UI nach Referenzbildern (Phone + Fold) mit echten Daten (Draft)

### 6.5 Nächste experimentelle Hauptaufgabe: ein lokaler Sprachkern + überprüfter Partnerdialog mit Coding-Agent

**Nicht** erneut UX/UI verändern. Den aktuellen Status von PR #72/#73/#74 erneut abrufen, das vollständige Inventar 6.3–6.4 prüfen und für jeden Zweig `reuse / candidate / superseded but preserve / blocked` begründen, ohne irgendetwas zu löschen. Darauf eine Entwicklungsaufgabe mit zwei **getrennten** Komponenten aufbauen:

(1) Einen selbst trainierbaren lokalen Sprachmodellkern im Forschungsmodus entwickeln: Tokenizer (Byte/Subword), explizite endliche Kontextrepräsentation, autoregressive Decoderschritte, eigenständige Backpropagation/Optimizer, sichere inhaltsadressierte Checkpoints, realistische CPU/ARM64-Budgets. CDX1 nicht übergehen; zuerst dessen Lernfähigkeit / Generation mit einer neuen Baseline gegen eine kleinere eigene Transformer- oder State-Space-Variante vergleichen. Training auf quellrechtlich nutzbaren, datenschutzgerecht gefilterten Korpora; weder die bisherigen 86/149 Quellen noch vier DEV-Beispiele reichen für eine belastbare allgemeine Sprachkompetenz. Nutze vorhandene SWB1/CDX1/CRE1/CSX1-Schnittstellen nur nach Verifikation; keine unbelegten Trainings-/Denkfähigkeiten behaupten.

(2) Einen kontrollierten **Reasoner↔Coding-Agent**-Dialog über den **existierenden** Teamleader / Termux / Worktree-Worker aufbauen. Typisierte, begrenzte Nachrichten `GOAL, HYPOTHESIS, PLAN, TEST_REQUEST, RECEIPT, CRITIQUE, REVISE, ABSTAIN`; jede Runde bindet Repo-Commit, Capability, Zeitbudget, Sicherheitsgrenze und Proof-/Testreceipt. Reasoner kann Hypothesen und Testpläne formulieren; Coding-Agent kann in der Sandbox arbeiten, muss unabhängig verifizierte Quittungen liefern; Reasoner darf niemals Quittungen fälschen und der Worker darf nicht ungeprüft auf `main` schreiben. Beschränkte Zyklen, Backpressure, Crash-/Replay-Wiederaufnahme, owner-gated Promotion und die Fähigkeit, bei eindeutiger Erfolglosigkeit abzubrechen.

Die Forschungsfragen sollen **riskant, aber falsifizierbar** sein: Kann ein lernfähiger Sprache-zu-Handlung-Kern eine unbekannte Anforderung in eine typisierte Plan-/Testfolge übersetzen? Verbessert eine begrenzte selbstkritische Schleife die Erfolgsquote gegenüber einem Single-Pass-Baseline-Runner? Helfen strukturierte Scratchpads, Relationsgraphen oder iterative Gedächtnisupdates tatsächlich bei neuen, unabhängigen Aufgaben? Zeige echte `TRAIN/VALIDATION/FROZEN_TEST`-Trennung, unkonditionierte Fehlbehauptungen, selektive Präzision, Coverage, Token-/RAM-/CPU-Zeit und vollständige Korrektheit des Code-Patches. Echte Autonomie heißt ein kontrollierter, reproduzierbarer Arbeitsprozess, **nicht** behauptetes Bewusstsein oder grenzenlose Selbstverbesserung. Keine API-Bezahlpflicht, kein GitHub-Actions-Zwang, kein selbsttätiger Produktivmerge, keine Migration von Gerätegewichten.

**Handoff-Gate:** jede stabile Etappe auf editierbarem separatem Branch committen, Dokument mit neuem SHA, realem Compile-/Teststatus und offenen Fehlern ergänzen. Vor Release vollständige Daten- und Signatursicherung, Android-Tests und Besitzerentscheidung.
