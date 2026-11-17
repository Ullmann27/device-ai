# Regression-Verifier – erneute lokale Validierung (28.10.2026)

**Ausgeführt auf diesem isolierten x86_64-Arbeitscontainer, NICHT auf Fold7, Termux oder dem früheren Cloud-Workspace.**

Geprüfte tatsächlich auf GitHub gepushte Quell-Blobs (lokal per `git hash-object` mit GitHub `fetch_file.sha` abgeglichen):
- `scripts/continuum-fusion-full-regression.sh`: `5e4280ff34db5b84a85c464c67817046a17aab6d`
- `scripts/continuum-fusion-regression-report.mjs`: `1bfeecd6633906a6122d9a1909e5f418a9ec2383`
- `scripts/continuum-fusion-regression-report.test.mjs`: `5b196959a73e4a3cda25dac97b89fbd74e9f993b`

**Wirklich ausgeführte Prüfungen:**
```text
bash -n continuum-fusion-full-regression.sh        PASS (Exit 0)
node --test continuum-fusion-regression-report.test.mjs
TAP version 13
1..7
# tests 7
# suites 0
# pass 7
# fail 0
# cancelled 0
# skipped 0
# todo 0
```

Neuer Negativfall: bewusst abgeschnittenes XML wird unter `junit-xml/TEST-unit.xml` unverändert bewahrt, mit SHA-256 ins Manifest aufgenommen, und der Auswertungsstatus bleibt `FAIL`. Die ursprüngliche Fehlermeldung des Report-Parsers wird ebenfalls gespeichert.

Der lokale Runner erkennt jetzt außer der früheren Linux-`/workspace/continuum-tools`-Umgebung die bereits in `scripts/continuum-local-owner-apk.sh` dokumentierte Android-Termux-Struktur für JDK17, SDK36 und natives AAPT2. Er besteht weiterhin auf exakt Gradle 8.11.1, JDK17, sauberem Git-Checkout und einem übergebenen vollständigen Commit-SHA; Termux erhält nur den bereits vorhandenen `deviceAiMobileAidlFallback`-Schalter. Keine neuen Toolchains oder Netzzugriffe eingebaut.

**Unveränderte Nachweisgrenze:** Der komplette `:app:testDebugUnitTest`-Lauf, Gradle-Android-Kompilierung dieses zusätzlichen QA-Branches, echte Termux ARM64-Ausführung, Fold7 und Gemma-Inferenz wurden in diesem Container **NICHT** ausgeführt. Die 7 bestandenen Node-Prüfungen beweisen ausschließlich die JavaScript-Auswertung. Die 56 früheren Kotlin-/JUnit-Erfolge gehören zum geprüften Eltern-Branch #203, nicht zu einem neuen Geräte- oder vollständigen Regressionslauf.

Revisions-/Rückfallweg: PR #204 bleibt Draft, keine Änderungen an `main`, PR #203, bestehenden Modellgewichten, Besitzererinnerungen, Signierung oder installierter APK.