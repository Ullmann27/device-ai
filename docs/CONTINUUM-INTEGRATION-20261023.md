# Continuum – isolierter Integrationskandidat vom 23.10.2026

## Zweck und Schutz
- Ausgangspunkt: AAA-UI-Zweig `opus/continuum-aaa-ui-20261022` @ `c717a28255f243d7da33b88a70e7fd4c1ee724ca`.
- Forschung importiert aus `opus/continuum-neural-frontier-lab-20261022` @ `14702b0da94f2dd901c1dbad63c382b616b2c66a`, der LM1/CCC1 aus PR #75 enthält.
- Ausschließlich separater Feature-Preview-Branch; weder Production/main noch Besitzergewichte, installiertes APK oder Daten ändern.
- Keine automatisierte Freischaltung experimenteller LM1-/NF-Gewichte oder Coding-Aktionen. Für Forschungsfunktionen ist eine gesonderte verifizierte Runtime- und UI-Verdrahtung nötig.
- PR #83 enthält eine ältere, teils überholte Fold-Rail-Änderung; nicht nochmals blind integrieren.

## Tatsächlich übernommene Dateien
- LM1: 5 Kotlin-Kerndateien für Tokenisierung, Korpus, Tensor, Modell und Checkpoints sowie `LmCoreTest.kt`, `LmBaselines.kt`, `LmExperiment.kt`.
- CCC1: Envelope, Gate, Interpreter, Journal, Orchestrator, Recovery, Verifier und Test sowie abhängige Reasoner-Dateien. Der neue Dead-Lease-Recovery-Mechanismus einschließlich Regressionstest stammt aus dem Forschungszweig.
- Forschungstools: rein lokaler assoziativer Kandidatenranker, begrenzte Behaviour-Probes, zustimmungsgebundenes Research-Radar, jeweils mit Tests.
- Native AAA-Benutzeroberfläche aus dem Ausgangszweig unangetastet.
- Separater Render-Builder: `scripts/render-continuum-integrated-preview-20261023.sh`. Pinnt den Branch-Namen, prüft vorhandene Test-/Signatur-/Paketgates und schreibt unveränderliche, SHA-256-benannte Artefakte nach `continuum-aaa-output`.

## Derzeitige Qualitätsgrenze (verbindlich)
- **SOURCE_STAGED**: ja, auf isoliertem Branch.
- **SOURCE_MERGE_REVIEW**: teilweise, bekannte gemeinsame Module ausgewählt; vollständige Gradle-/Node-Kompatibilität steht aus.
- **NODE_TESTED**: ja, laut Render-Log des festgehaltenen Commits (u. a. 887 Tests ohne Fehler; weitere Suites bestanden).
- **KOTLIN_COMPILED**: ja, Android-Kotlin- und Testquellen kompiliert auf Render.
- **APK_BUILT**: ja, Render-Deploy `dep-dbddtkgae00c73frkt90` erfolgreich (Commit `f0c8f6eadd3469fd5e04d77848b3d290428ce211`).
- **APK_SIGNED / PACKAGE_VERIFIED**: ja, Skript-Gate `apksigner` (v2) + `aapt`/Paketprüfung erfolgreich, andernfalls hätte der Render-Deploy abgebrochen.
- **DEVICE_VERIFIED / RELEASE_APPROVED**: nein; keine automatische Freigabe als signiertes Update der produktiven App.
- `LmStep7.kt` und `LmStep7Test.kt` aus dem unterbrochenen Cloud-Arbeitsverzeichnis **nicht in GitHub gefunden**: nicht stillschweigend als enthalten behaupten.
- NF-Experimente und Checkpoints bleiben bewusst in ihrem eigenen Forschungszweig. Keine vorzeitige Promotion nach Android.
- LM1-Finaltest aus PR #75 wurde bereits einmal ausgeführt; er darf **nicht wiederholt oder für weitere Modellauswahl benutzt** werden.

## Nächste technische Gate-Reihenfolge
1. Sicherstellen, dass die Quellen dieses Branches auf unveränderlichem Commit-SHA basieren.
2. `node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs`.
3. `npm --prefix backend ci --ignore-scripts --no-audit --no-fund` und Backendtests.
4. Mit JDK 17, Android SDK 36, Gradle 8.11.1: `gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiFeaturePreview=true --stacktrace`.
5. `apksigner verify --verbose --print-certs`, `aapt dump badging`, Paket muss `at.heinz.deviceai.featurepreview` sein (nicht `at.heinz.deviceai.mentorpaired`).
6. APK-Hash, Quelle-Commit, Paket, Signatur und Versionscode festhalten. Nur diese geprüfte APK auf separatem HTTPS-Render-Preview-Dienst zum Chrome-Download veröffentlichen.
7. Auf realem Smartphone/Fold Navigation, Neustart, Schriftgrößen und Messwerte prüfen; fehlende Daten nicht simulieren.

## Externe Build-Voraussetzungen
- GitHub Actions zeigte am 23.10. mehrfach Runner-ID 0 und null Jobschritte; dadurch gibt es keine verifizierte APK aus diesem Zweig.
- Historisch erfolgreicher Ausweichweg war eine **Render**-Build-/Chrome-Auslieferungspipeline. Die Render-Workspace-Auswahl und Service-Zuordnung müssen vor Änderungen bestätigt sein; keinesfalls den bisherigen signierten `mentorpaired`-Dienst ungeprüft auf diesen experimentellen Branch umstellen.
- Keine URL wird als APK-Download deklariert, bevor der tatsächliche Hash und die Datei am Ziel geprüft sind.

## Weiterarbeit ohne Code-Sperren
Der Branch bleibt vollständig lesbar. Alle Änderungen wurden einzeln gepusht. Keine fremden Worktrees, keine Force-Pushes, kein Main-Merge.
## Tatsächlicher Render-Build (23.10.2026)
- Dienst `srv-dbddtk8ae00c73frkrhg`, Deployment `dep-dbddtkgae00c73frkt90`, erfolgreich `live` am 23.10.2026 04:18:42 UTC.
- Exakter APK-Quellcommit **`f0c8f6eadd3469fd5e04d77848b3d290428ce211`**; spätere Dokumentationscommits gehören NICHT zu diesem APK-Build.
- Android Buildprotokoll: `BUILD SUCCESSFUL in 4m 20s`, 54 Gradle-Aufgaben ausgeführt. Haupt-Kotlin und JVM-Testquellen kompiliert, Unit-Tests, Android-Lint und `assembleDebug` ohne Buildfehler.
- Node-Teilprüfung: 887 Tests, 0 Fehler; weitere getrennte Suites (60, 26, 34, 30 Tests) ebenfalls 0 Fehler.
- Direktdownload: `https://device-ai-continuum-full-preview-20261023.onrender.com/DeviceAI-Continuum-Integrated-3ba5669900b917cc6fc4ac57ba3d7e541fd48524a3b7ba67d351fa87a9055201.apk`.
- Gemeldeter APK-SHA-256: `3ba5669900b917cc6fc4ac57ba3d7e541fd48524a3b7ba67d351fa87a9055201` (Render-Buildskript hat seine lokale Kopie verifiziert). Kein unabhängiger Abruf/Hashcheck der HTTPS-Datei innerhalb dieser Sitzung möglich; Render hat den Dienst live geschaltet.
- Es ist eine **separat installierbare APK mit `at.heinz.deviceai.featurepreview`**, kein signaturkompatibles Update für `at.heinz.deviceai.mentorpaired`.
- **Begrenzung:** LM1-Klassen und CCC1/Research-Radar-Module sind im Quell-/Buildstand vorhanden; ein nutzerseitig funktionierender LM1-Chat oder autonome Anwendung der Forschungsgewichte wurde auf echter Hardware nicht nachgewiesen. Unfertiges Step-7-Training ist nicht Bestandteil dieses Builds.
- Bestehendes Nutzergerät, private Gewichte und Haupt-APK wurden nicht verändert.
