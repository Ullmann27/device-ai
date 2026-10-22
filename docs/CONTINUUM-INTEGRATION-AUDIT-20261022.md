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
