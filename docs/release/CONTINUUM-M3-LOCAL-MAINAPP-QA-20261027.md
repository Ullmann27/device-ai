# Continuum M3 – native Haupt-App unabhängig prüfen, auch ohne Original-P12

**Stand 27.10.2026:** Die von Sol geprüfte gemeinsame Haupt-App-Integration ist auf PR #196, Original-HEAD `126309f9a7069c7e7b5bea3e2082462cab4f9f9c`. Dieser M3-Zweig erweitert diesen HEAD ausschließlich um einen **strikt nicht installierbaren QA-Buildweg für dieselbe Haupt-App**. Die Originalsignierung, `main`, Besitzerfakten, Gemma-Gewichte und bestehende Releases bleiben unverändert.

## Konkreter Fortschritt

Der bisherige Haupt-App-Builder `scripts/continuum-local-owner-apk.sh` verlangt die Original-P12 schon vor Gradle. Damit kann ohne Originalschlüssel nicht einmal ein vollständiges APK-Prüfartefakt entstehen, obwohl Sol die Quellen mit JDK17/Gradle8.11.1/SDK36 und 1.094 Android-JUnit-Tests erfolgreich getestet hat. Neue sichere Lösung:

- `app/build.gradle.kts`: Opt-in-Property `-PcontinuumOwnerLocalQa=true` ermöglicht exakt **dasselbe Haupt-App-Paket** `at.heinz.deviceai.mentorpaired`, Versionscode ab 26, ohne originale Signatur. Die Android-Debug-Signatur ist ausdrücklich **nicht** zum Besitzer-Update geeignet. Die Gradle-Konfiguration verweigert QA in Kombination mit Eigentümerfreigabe oder Eigentümer-Schlüsseldaten; die originale Signaturkonfiguration bleibt unangetastet.
- `scripts/continuum-local-mainapp-qa.sh`: Einmalig eingerichtetes Fold7/Termux ARM64 oder eigener Linux-Rechner baut die **Haupt-App, keine Demo-App**. Nutzt unverändert den verifizierten lokalen Build-Doctor, dieselbe SDK-/JDK-Umgebung, native Termux-`aapt2`, Termux-AIDL-Fallback, tatsächliche `:app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin :app:assembleDebug`. Kein Render, GitHub Actions, Modell-Download oder Anbieter-API.
- Das Skript bindet den geprüften sauberen Git-HEAD, Version 26+ und SHA-256 der APK, liest Paketidentität sowie Zertifikatsfingerabdruck **aus der erzeugten APK** und verlangt, dass der Fingerabdruck **vom bisherigen Besitzerzertifikat abweicht**. Es veröffentlicht ausschließlich eine ausdrücklich **DO-NOT-INSTALL** beschriftete QA-Datei samt lokalem Build-Protokoll und Prüfbelegen; kein automatisches Installieren, kein Dateneingriff, keine falsche Releasefreigabe.
- `scripts/continuum-local-mainapp-qa.test.mjs`: 17 Regressionstests für CLI-Syntax, Negativfälle, Signaturisolierung, Paket- und Versionsgleichheit, Buildaufgaben, ARM64-Kompilierung und Prüfnachweise.

## Ausführung auf vorbereitetem Gerät / Linux-PC

Zuerst über Sols vorhandenen, geprüften HTTPS-Helfer den privaten Quellstand abrufen. GitHub-Login und Quellcodeprüfung sind einmalige Voraussetzungen, nicht für den eigentlichen Offline-Build.

**Fold7-Hinweis bei steckengebliebener Login-Rückfrage:** `gh auth login` kann bei „Authenticate Git with your GitHub credentials? Yes“ auf eine Terminalantwort warten. In Termux zuerst mit **CTRL+C** abbrechen und anschließend für die einmalige Browser-Geräteanmeldung `GH_PROMPT_DISABLED=1 GH_BROWSER=termux-open-url timeout --foreground 300 gh auth login --hostname github.com --git-protocol https --web` verwenden. Die Anzeige des Gerätecodes ist **kein Zugangstoken**; dennoch nur im eigenen Browser eingeben, niemals hier teilen. Danach `bash scripts/continuum-github-https.sh setup` erneut ausführen. Ob dieser Weg auf dem Besitzer-Fold7 funktioniert, ist ausdrücklich **NOT_RUN**.

```bash
cd ~/device-ai
bash scripts/continuum-github-https.sh setup
git fetch origin chatgpt/continuum-mainapp-m3-after-sol-20261027
git switch --track origin/chatgpt/continuum-mainapp-m3-after-sol-20261027
bash scripts/continuum-local-owner-apk.sh doctor
```

Der **nicht-signierende, nicht-installierbare** Haupt-App-Qualitätsbuild benötigt **keine Original-P12**:

```bash
cd ~/device-ai
unset CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD
export CONTINUUM_LOCAL_QA_APPROVED_SHA="$(git rev-parse HEAD)"
export CONTINUUM_OWNER_NEXT_VERSION_CODE=26
node --test scripts/continuum-local-mainapp-qa.test.mjs
bash scripts/continuum-local-mainapp-qa.sh --offline
```

Wenn Gradle-Abhängigkeiten erstmals fehlen: einmal ohne `--offline` ausführen. Diese erstmalige Cache-Befüllung ist **nicht** an CI- oder Render-Minuten gebunden.

Die QA-Datei liegt danach ausschließlich unter `$HOME/.local/state/device-ai-continuum/mainapp-qa/`. **Nicht installieren.** Eine mit Android-Debugzertifikat signierte APK könnte die installierte Eigentümer-App nicht aktualisieren. Eine Deinstallation würde deren private Daten gefährden und ist ausdrücklich nicht Teil dieses Plans.

### Erst danach – echtes Besitzerupdate

Für die Eigentümer-APK ist weiterhin zwingend erforderlich:

1. Die tatsächliche **private Original-P12** muss dem Besitzer auf seinem Gerät/PC lokal und sicher zugänglich sein. Der bekannte Zertifikats-Fingerabdruck `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc` enthält **keinen privaten Schlüssel**. Der Render-Signierdienst besitzt die Secrets; die aktuellen Verbindungen erlauben keinen sicheren Export. Niemals einen Ersatzschlüssel als identischen Besitzer-Schlüssel behaupten.
2. `CONTINUUM_OWNER_LOCAL_APPROVED_SHA` muss exakt den unabhängig geprüften Quellstand freigeben. Bestehender `continuum-local-owner-apk.sh build --offline` verlangt originalen Schlüssel, passenden SHA, Unit-Tests, Lint und prüft tatsächliche APK-Signatur, Paket und Version. Kein automatisches Release.
3. Die tatsächlich mit dem Originalschlüssel signierte `at.heinz.deviceai.mentorpaired` 0.3.25/code26 kann danach als reguläres Update auf dem Fold7 geprüft werden; keine Deinstallation, kein Modell-Neudownload.

## Tatsächlich ausgeführte Prüfungen

- Die originale M3-Bash-Datei wurde exakt byteweise aus dem GitHub-Commit rekonstruiert. `git hash-object` ergab **`f2a28cb7376f304329896167bf1910c488f83a71`**, identisch zum GitHub-Blob.
- `bash -n` des exakten Skripts: **PASS**.
- Zehn isolierte, tatsächlich ausgeführte CLI-/Bash-Negativfälle mit Node22: **10/10 PASS**, darunter 5 ungültige Befehle, beide Schlüssel-Sperren, fehlender Checkout, Byteidentität und Bash-Syntax.
- Statische GitHub-Quellprüfung (Gradle/Builder/Node-Test): **18/18 PASS**.
- Für diesen neuen M3-HEAD: **Gradle/JUnit/Lint/APK-Assembly NOT_RUN**. Die von Sol bei PR196 geprüften 1.094 Android-Tests beziehen sich auf den älteren HEAD ohne diese zusätzliche Gradle-QA-Konfiguration. APK und echte Gemma-Geräteinferenz bleiben ausdrücklich **NOT_RUN**.

**Weder ein neues Besitzerupdate noch ein installierbares QA-Artefakt ist durch diese Quellenänderung bereits entstanden.** Der nächste messbare Teilmeilenstein ist eine lokal auf dem Fold7 oder Linux-PC tatsächlich durchgelaufene `:app:assembleDebug` mit unabhängig geprüftem Paket- und Signaturbericht, **ohne** private Besitzer-P12 und **ohne** Cloud-Buildzeit. Danach Besitzer-Schlüssel klären und reguläre Aktualisierung prüfen.

## Architekturgrenzen und offene Zweige

PR #196 und seine von Sol gesicherten Quelltexte bleiben als gemeinsamer Unterbau erhalten. PR #193 (Gemma-Reopen), #194 (SWB1-Belegkorrektur) und selektive #189-CCC1-Reparaturfixes sind bereits im Unterbau enthalten. Das unfertige #189-UI und unbewiesene CDX1-Promotion verbleiben isoliert. Kein Force-Push, kein Merge nach `main`, kein Modellwechsel, keine bestätigte Besitzererinnerung verändert. Alles bleibt Git-revisionsfähig und vollständig lesbar.
