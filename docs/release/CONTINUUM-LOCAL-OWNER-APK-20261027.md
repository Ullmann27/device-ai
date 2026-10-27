# Continuum – wiederholbar lokal eine echte Haupt-App-APK bauen (ohne CI- oder Render-Build-Minuten)

**Status 27.10.2026:** Der Quellstand von PR #193 (Gemma-Wiederherstellung) und PR #194 (Belegzuordnung) wurde auf einem isolierten Haupt-App-Build-Zweig zusammengeführt. Der Script-Buildweg wurde erstellt; ein kompletter Android-Build oder eine neue signierte APK **wurde noch nicht ausgeführt**. Render hat keine Pipeline-Minuten; GitHub Actions ist ebenfalls erschöpft und wird **nicht mehr benutzt**.

## Nachhaltige Lösung: vorhandene lokale Android-Hardware

Aktueller Abschlusszweig: `sol/continuum-local-owner-integration-20261027` / PR #196, auf PR #195 aufgebaut. Gemma-Reopen und Belegzuordnung sind bereits enthalten. Die endgültigen Prüfresultate und SHAs stehen in `docs/research/omega/SOL-LOCAL-OWNER-HANDOFF-20261027.md`.

### Privaten GitHub-Zugang einmalig auf HTTPS umstellen

Im vorhandenen Checkout ausführen:

```sh
bash scripts/continuum-github-https.sh doctor
bash scripts/continuum-github-https.sh setup
git fetch origin sol/continuum-local-owner-integration-20261027
git switch sol/continuum-local-owner-integration-20261027
git pull --ff-only
```

Der Helfer verwendet ausschließlich die bereits authentifizierte `gh`-Anmeldung. Er prüft den privaten HTTPS-Lesezugriff, richtet `gh auth git-credential` ein und ersetzt nur den bekannten offiziellen SSH-Remote. Bei fehlender Anmeldung bleibt der Remote unverändert. Eine hängende Browseranmeldung kann im privaten Termux-Terminal mit `timeout --foreground 300 gh auth login --hostname github.com --git-protocol https --web` zeitlich begrenzt neu begonnen werden. Die Geräteanmeldung muss am Gerät abgeschlossen werden. Keine Tokens in URLs, Chat, Git-Konfiguration oder Build-Logs kopieren; `gh` verwaltet die Zugangsdaten in seinem lokalen privaten Speicher.

Der Builder verwendet jetzt das vollständige `JAVA_HOME` samt `javac` und `keytool` konsistent, akzeptiert Node-LTS 20/22/24 und prüft, ob das native `aapt2` tatsächlich startet. SDK/Gradle/Original-P12 bleiben lokale Voraussetzungen. Nachweise desselben Quellstands mit verschiedenen Versionscodes erhalten eigene Dateinamen.

Der vorhandene `scripts/continuum-mobile-builder.mjs build` ist für eine **andere Preview-App** ausgelegt und kann die installierte Eigentümer-App nicht aktualisieren. Das neue `scripts/continuum-local-owner-apk.sh` baut dagegen mit der bestehenden Gradle-Anwendung `at.heinz.deviceai.mentorpaired`. Nach einmaliger Einrichtung sind keine externen Build-Dienste, Cloud-Konten, Pipelines oder Download-Aktionen im Skript notwendig.

Kompatible Wege:
- **Direkt auf dem Fold7 mit Termux/ARM64:** vorhandenes Termux, Node-LTS 20/22/24, vollständiges JDK17, Gradle 8.11.1, Android SDK 36, ARM64-`aapt2` und `apksigner`; vorhandener `-PdeviceAiMobileAidlFallback=true` Android-Quellpfad wird benutzt. Keine Emulator- oder zweite App erforderlich.
- **Auf einem eigenen Linux-x64-Rechner:** dieselben Android/Gradle/JDK-Werkzeuge, offizieller Android SDK-36-Compiler und Build-Tools 35.0.0. Nach lokalem Build APK via USB/Datei auf Fold7 bringen.
- **Offline-Betrieb:** wenn SDK, JDK, Gradle, Maven-/Android-Artefakte bereits lokal vorliegen: `build --offline`. Die erstmalige Installation und das Auffüllen des Gradle-Caches benötigen eventuell einmalig Netzwerkzugang. Dies hat **keine monatliche Build-Minutengrenze**, benötigt aber Speicher, CPU, RAM und ggf. Strom.

### Einmaliger Sicherheitsblocker: ORIGINAL-Schlüssel

Android kann die bestehende installierte APK nur **mit identischem Paketnamen UND demselben privaten Signierzertifikat** aktualisieren. Der bekannte Zertifikats-Fingerabdruck lautet:
`2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.

Die lokale Umgebung muss deshalb eine **autorisierte lokale Kopie** des bisherigen `deviceai-mentor`-PKCS#12-Keystores haben. Der bisherige Render-Signierdienst besitzt seinen eigenen geheimen Schlüssel; der Quellcode oder die APK enthält den privaten Schlüssel **nicht**. Es gibt keinen sicheren Trick, ihn aus dem APK-Zertifikat zu rekonstruieren. Der Besitzer überträgt die Original-P12 **einmalig außerhalb dieses Chats und außerhalb von GitHub** an den eigenen PC oder Termux, idealerweise in einem privaten App-Verzeichnis. Niemals die P12, Base64-Inhalte oder das Kennwort im Chat, im Repository, in einem GitHub Issue oder in einem öffentlichen Link ablegen. Das lokale Skript lehnt ein Zertifikat mit anderem Fingerabdruck ausdrücklich ab.

### Bedienung auf vorbereitetem Termux / Linux

Im privaten und autorisierten Checkout dieses speziellen Zweigs:

```sh
cd ~/device-ai
git fetch origin chatgpt/continuum-owner-current-qa-apk-20261027
git switch chatgpt/continuum-owner-current-qa-apk-20261027
git pull --ff-only origin chatgpt/continuum-owner-current-qa-apk-20261027
bash scripts/continuum-local-owner-apk.sh doctor
```

Nur wenn **DOCTOR=READY** gemeldet wird, sämtliche Voraussetzungen erfüllt und die **Original-P12 bereits auf diesem Gerät** sicher hinterlegt ist:

```sh
export CONTINUUM_MENTOR_SIGNING_FILE="$HOME/.config/device-ai/mentor-preview-signing.p12"
read -r -s -p 'Lokales P12-Kennwort: ' CONTINUUM_MENTOR_SIGNING_PASSWORD
printf '\n'
export CONTINUUM_MENTOR_SIGNING_PASSWORD
export CONTINUUM_OWNER_LOCAL_APPROVED_SHA="$(git rev-parse HEAD)"
export CONTINUUM_OWNER_NEXT_VERSION_CODE=26
bash scripts/continuum-local-owner-apk.sh build --offline
unset CONTINUUM_MENTOR_SIGNING_PASSWORD
```

Falls ausschließlich die Gradle-Abhängigkeiten noch nicht im Cache sind, **beim ersten Mal** ohne `--offline` bauen. Danach funktioniert `--offline`, sofern alle Abhängigkeiten vorhanden sind. Die einzelne Build-Aktion benötigt **keinen neuen Modell-Download**.

**Jedes weitere Haupt-App-Update:** Git-Stand zuerst sauber und geprüft übernehmen, `CONTINUUM_OWNER_LOCAL_APPROVED_SHA` neu festlegen und den Versionscode auf **27, 28, 29 ...** erhöhen. Niemals kleiner als die Version auf dem Smartphone bauen. Pro Source SHA und installiertem Update die tatsächlichen Testergebnisse protokollieren.

### Sicherheits- und Qualitätsnachweise

Das Skript scheitert ohne:
1. Vollständigen, sauberen Git-Checkout und explizit bestätigten Git HEAD;
2. vollständiges JDK 17, Gradle 8.11.1, Node-LTS 20/22/24, Android SDK 36 sowie plattformspezifisches echtes `aapt2` und `apksigner`;
3. bereits lokal vorhandenen Original-P12-Datei und passendem Kennwort;
4. vor dem Build mit `keytool` übereinstimmendem Original-Signer-SHA;
5. erfolgreichen echten `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`;
6. unverändertem Git HEAD/Checkout nach dem Build;
7. nach dem Build verifiziertem Zertifikat, exakt `at.heinz.deviceai.mentorpaired`, korrektem VersionCode/VersionName und SHA-256-Dateihash.

Eine Datei wird **nur nach diesen Prüfungen** unter `$HOME/.local/state/device-ai-continuum/owner-apk/` gespeichert. Es gibt **keine automatische Installation**. Bei Fehlern wird keine fertige APK freigegeben. App-Daten und bereits installiertes Modell bleiben durch den Build unangetastet; bei einer späteren Installation ist ein Backup dennoch empfehlenswert.

### Aktueller Stand und reale Einschränkung

Die aktuell publizierte Eigentümer-APK 0.3.24/code25 wurde von einem älteren Commit erzeugt. Der neue lokale Builder verwendet, nach erfolgreichen Prüfungen, automatisch zunächst **0.3.25/code26**. Er kombiniert die Arbeit zu Gemma-Reopen und SWB1-Belegwechsel; er wurde noch **nicht vollständig mit Android SDK/Gradle ausgeführt**, weil in der Werkzeugumgebung dieses Chats weder Android SDK, Gradle noch Zugriff auf den privaten Besitzerkeystore vorhanden sind.

Dies ist ein unabhängiger, wiederholbarer lokaler Workflow, kein kostenloser Cloud-Build. **Der einmalige lokale Werkzeug- und Signierschlüsseltransfer ist unvermeidbar.** Kein neues Modell, keine externe KI-API, keine GitHub Actions, keine Render-Pipeline, kein Merge nach `main` und keine Neben-App.
