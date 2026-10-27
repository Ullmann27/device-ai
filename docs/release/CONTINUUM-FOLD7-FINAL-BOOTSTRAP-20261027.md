# Fold7: letzte Signierblockade und drei Schritte

Geprüfter Programmstand: PR #196, `17a0ce18674e45cf911d41521bd076cc9f9f76ce`. Diese Abschlussnotiz ändert keinen Programmcode. Beide lokalen Helfer erneut geprüft: 12/12 PASS. Keine Cloud-Builds, Secret-Abfragen, Ersatzsignaturen oder Installationen.

## Sichere Signiermetadaten

Render-Dienst: `device-ai-mentor-apk-preview`, `srv-dbaue53bc2fs73crkop0`, Typ `static_site`, AutoDeploy `no`. Aktuell beobachteter Branch: `integration/continuum-owner-v26-live-candidate-20261027`; Buildkommando: `bash scripts/render-mentor-owner-update-v25-live.sh`; veröffentlichter Ordner: `mentor-build-output`. Dienst unverändert.

Die historischen und aktuellen Signierskripte erwarten `CONTINUUM_MENTOR_SIGNING_P12_BASE64` und separat `CONTINUUM_MENTOR_SIGNING_PASSWORD`. Sie dekodieren die P12 im privaten Build-Arbeitsverzeichnis `.continuum-render-build/mentor-preview-signing.p12` mit Dateimodus 0600, außerhalb des veröffentlichten Ordners. Alias: `deviceai-mentor`. Vorgeschriebener Zertifikats-SHA256: `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.

Dies belegt den Bereitstellungsweg, nicht die aktuelle Verfügbarkeit der Secretwerte: Werte wurden nicht gelesen. Der verfügbare Render-Connector bietet keinen sicheren Datei-/Secret-Export oder Zugriff auf den abgeschlossenen Build-Arbeitsbereich. Eine Exportmöglichkeit im persönlichen Render-Konto wird hier nicht behauptet oder ausgeschlossen. Falls nur diese Secret-Kopie existiert, muss der autorisierte Dienstbesitzer sie außerhalb von Chat, Git und Logs über seinen eigenen sicheren lokalen Sicherungsweg bereitstellen. Keinen Build dafür starten, keine Secrets verändern und nichts in den öffentlichen Ausgabeordner kopieren.

Hier ist weder eine konfigurierte P12 noch eine Datei am üblichen privaten oder lokalen Build-Pfad vorhanden; auch kein Signierkennwort ist konfiguriert. Der Fold7-Dateispeicher ist nicht zugänglich. Eine bereits autorisierte Original-P12 auf dem Fold7/privaten Backup ist wiederverwendbar; der Builder prüft das Zertifikat vor und nach dem Build. Die APK allein enthält keinen rekonstruierbaren privaten Schlüssel.

## Höchstens drei Besitzer-Schritte

1. **Im privaten Termux-Terminal anmelden und den geprüften PR abrufen.** Eine noch hängende frühere Anmeldung zuerst mit Strg+C beenden. Im vorhandenen sauberen Checkout:

   ```bash
   cd ~/device-ai
   timeout --foreground 15 gh auth status --hostname github.com >/dev/null 2>&1 || timeout --foreground 300 gh auth login --hostname github.com --git-protocol https --web
   git -c 'url.https://github.com/Ullmann27/device-ai.git.insteadOf=https://github.com/Ullmann27/device-ai.git' -c credential.helper= -c 'credential.helper=!gh auth git-credential' fetch https://github.com/Ullmann27/device-ai.git sol/continuum-local-owner-integration-20261027
   git switch --detach 17a0ce18674e45cf911d41521bd076cc9f9f76ce
   ```

   Dieser feste Checkout bewahrt vorhandene Zweige; Git-Verlauf und Remote-Branch bleiben zugänglich. Der explizite HTTPS-Fetch benötigt weder SSH-Schlüssel noch den erst in PR #196 hinzugefügten Helfer. Bei fehlgeschlagener Anmeldung oder fehlgeschlagenem Fetch hier stoppen.

2. **HTTPS-Helfer und Doctor ausführen:**

   ```bash
   bash scripts/continuum-github-https.sh setup
   bash scripts/continuum-local-owner-apk.sh doctor
   ```

   Nur bei erfolgreichem HTTPS-Nachweis und `DOCTOR=READY` fortsetzen. Ein gemeldeter vorhandener Keystore ist noch kein Zertifikatsnachweis.

3. **Nur mit bereits privat bereitgestellter Original-P12 bauen.** Beispielpfad bei Bedarf auf die tatsächliche private Datei anpassen; nie eine Ersatz-P12 erzeugen:

   ```bash
   export CONTINUUM_MENTOR_SIGNING_FILE="$HOME/.config/device-ai/mentor-preview-signing.p12"
   read -r -s -p 'Original-P12-Kennwort: ' CONTINUUM_MENTOR_SIGNING_PASSWORD
   printf '\n'
   export CONTINUUM_MENTOR_SIGNING_PASSWORD
   export CONTINUUM_OWNER_LOCAL_APPROVED_SHA=17a0ce18674e45cf911d41521bd076cc9f9f76ce
   export CONTINUUM_OWNER_NEXT_VERSION_CODE=26
   bash scripts/continuum-local-owner-apk.sh build --offline
   unset CONTINUUM_MENTOR_SIGNING_PASSWORD
   ```

   Ziel: `at.heinz.deviceai.mentorpaired`, 0.3.25/code26; code26 muss größer als der installierte Versionscode sein. Bei unvollständigem Gradle-Cache darf derselbe lokale Build einmal ohne `--offline` laufen. Ergebnis erst nach erfolgreichen Tests/Lint, Originalsignatur-, Paket-, Versions- und Hash-Prüfung verwenden. Keine automatische Installation. Ohne Original-P12 endet der Ablauf hier **BLOCKED**.
