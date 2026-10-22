# Continuum – Anschluss Opus PR57 an Android-Builder-UI

Stand: 22.10.2026 · EXP-00, EXP-08, EXP-12 · **SOURCE_INTEGRATED / REVIEW_ONLY**

## Baseline

Reviewbranch `chatgpt/continuum-opus-android-coding-ui-20261022` startet exakt bei Opus PR #57 HEAD `1d92db03b87558c0b330dc584fd1f4c025a93c82`, der seinerseits auf PR #53 `8897fd6630800b297fbda8f8638c7b615cf2b172` basiert. Kein Merge nach `main`; keine Änderungen am bestehenden Render-Releasebranch, Paketnamen, Signierschlüssel, SWB1/CDX1-Gewichten oder privaten App-Daten.

## Neu implementierter Durchstich

- Vorhandene `ContinuumMobileBuilderActivity` bleibt dieselbe Werkzeug-Unterseite. Sie erhält drei explizite Eingaben für den **begrenzten** vorhandenen `code-test`-Auftrag: Modulname unter `.github/continuum/`, exportierter Funktionsname und kurze JSON-Vertragstests. Nichts wird automatisch gestartet.
- Nach Validierung über das **bestehende** `ContinuumMobileCodingJob` ist ein gesonderter Dialog mit tatsächlichem Rechen-/Review-Hinweis erforderlich. Nur nach dessen positiver Bestätigung geht die fest erlaubte Node-`code-test`-Argumentliste als `RUN_COMMAND` an Termux.
- Der bestehende Builder-Dispatcher hat unverändert die Preview-/Termux-Berechtigungskontrolle, einen einzelnen ausstehenden Auftrag, einmalige mutable explizite PendingIntent-Rückmeldung und unveränderliche Callback-URI-Identität. Der Coding-Request nutzt dieselbe UUIDv4 als Job- und Callback-ID.
- Der bereits existierende nicht-exportierte `ContinuumMobileBuildResultReceiver` prüft bei `CODE_TEST` mit `StrictJson`/ `ContinuumMobileCodingJob.evaluate` die echte Termux-Rückgabe samt Exitcode, Fehlercode, Job-ID, vollständigem Commit, drei unabhängigen Testphasen und Verifikations-/Patchhash. Er speichert **nur die kurze geprüfte Erklärung**, niemals vollständige stdout-Ausgabe oder Quelldaten. Alte `DOCTOR`/`BUILD`/`STATUS`-Policy bleibt getrennt.
- Die UI hat **keine** automatische Patchanwendung, Mergefunktion, Installation oder Änderung des Owner-Checkouts. `code-cancel` ist weiterhin über die vorhandene Termux-CLI verfügbar. Die **Android-Cancel-Schaltfläche** ist bewusst noch offen: Ohne unabhängigen Callback/Job-Status dürfte sie eine laufende Abschlussquittung nicht überschreiben. Eine zweite parallele Android-Ausführung wird abgewiesen.
- Neue Node-Source-Wiring-Regressionsdatei `.github/continuum/mobile-coding-android-wiring.test.mjs` mit vier Gruppen prüft diese Grenzen.

## Nachweise, die wirklich vorliegen

- PR #57 aus Opus: 832/832 Node-Tests, sechs davon mit realem `kotlinc`; zusätzliche fünf Kotlin-JUnit-Tests grün nach dessen dokumentiertem Bericht. Dies sind **Baseline-Belege von Opus**, nicht neue Testausführungen dieser UI-Änderung.
- In diesem UI-Reviewbranch: beide Kotlin-Quellen und alle betreffenden Policies aus GitHub rückgelesen; **15 statische Source-/Identitätsprüfungen in einer isolierten Toolausführung bestanden**. Kein echter Android-Gradle-Build oder Kotlin-Compiler-Test der neuen Activity/Receiver-Dateien für diesen Branch ausgeführt.
- Folgende Reproduktion ist nach Buildtoolchain-Bereitstellung notwendig:
  `node --test .github/continuum/mobile-coding-android-wiring.test.mjs .github/continuum/mobile-builder-android-wiring.test.mjs .github/continuum/mobile-builder-result-wiring.test.mjs`
  `node --test scripts/continuum-mobile-coding.test.mjs`
  `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiMentorPreview=true` **ausschließlich im korrekt signierten, isolierten Review-Buildkontext, mit unveränderter vorhandener Signatur**.
- Status danach erst nach einem echten Samsung-Android-16-Termux-Auftrag und erfolgreicher Callbackprüfung auf `END_TO_END_VERIFIED` erhöhen. Quellintegration ist noch kein Android-Gerätebeleg. **Keine APK wurde hier neu erzeugt oder installiert.**

## Nächster Schritt und Rollback

1. Reviewdiff dieses Branches prüfen und Android-Compiler-/Node-Regressionslauf ausführen; bekannte Source-Contract-Tests nicht abändern, um Fehler zu verbergen.
2. Mit Samsung-Termux am sauberen Checkout und dem gewünschten Quellcommit die `code-test`-Kette real prüfen. Dann Besitzerfreigabe für Preview-APK und Android-Rückkanal-Test.
3. Danach Android-Cancel-UI nur mit separatem PendingIntent und race-sicherer Job-ID/Quittungsführung ergänzen; ebenso mögliche Chat-Eingabe nur durch den vorhandenen Owner-Backlog ohne untrusted Shell.
4. Rollback: Diesen Reviewbranch nicht übernehmen oder die zwei Activity/Receiver-Patches und die Test-/Dokumentationsdateien revertieren. Kein Datenbank-/Checkpoint-/Paket-/Signer-Rollback erforderlich.
