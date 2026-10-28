# Fold7: Continuum-Haupt-App aus PR #205 prüfen und sicher aktualisieren

Datum: 28.10.2026 · Offizielle private Quelle: `Ullmann27/device-ai` · Arbeitszweig: `chatgpt/continuum-german-research-contradiction-20261028` (Draft-PR #205, auf #204 und #203). Dies ist **die bestehende Haupt-App** `at.heinz.deviceai.mentorpaired`, keine Demo und keine parallel zu installierende Vorschau.

**Stand: Code und lokaler QA-Einstieg sind gepusht; vollständiger Android-Build, Gerät und Originalsignierung NICHT ausgeführt.** Der 9/9-Testnachweis des Shell-Aufrufwegs ist in [`fold7-gate-node-9pass.md`](../evidence/continuum-german-research-20261028/fold7-gate-node-9pass.md). Ein neuer Websuch-Qualitätsgewinn ist nicht unabhängig gemessen.

## A – Einmalige private Quelle aus leerem Termux-Startverzeichnis öffnen

Der Screenshot zeigte die Eingabeaufforderung `~ $`. Dort ist `scripts/...` noch nicht vorhanden; **nicht** zuerst einen Build-Befehl eingeben. Eine bereits vorhandene, möglicherweise geänderte `~/device-ai`-Arbeitskopie darf nicht überschrieben werden. Deshalb wird ein **separater** Checkout verwendet:

```bash
pkg install -y git gh nodejs-lts
gh auth status --hostname github.com >/dev/null 2>&1 ||
  GH_BROWSER=termux-open-url gh auth login --hostname github.com --git-protocol https --web
gh auth setup-git --hostname github.com
target="$HOME/device-ai-fold7-candidate"
if [ ! -e "$target" ]; then
  GIT_TERMINAL_PROMPT=0 git clone --single-branch     --branch chatgpt/continuum-german-research-contradiction-20261028     https://github.com/Ullmann27/device-ai.git "$target" || exit 1
fi
cd "$target" || exit 1
git remote get-url origin
git status --short
git rev-parse HEAD
```

GitHub-Anmeldung erfolgt nur im eigenen Termux/Browser. Keine Zugangstokens, P12 oder Passwörter hier im Chat eingeben. Wenn `target` bereits existiert, wird es nicht gelöscht, zurückgesetzt oder automatisch aktualisiert. Das vorhandene GitHub-Rezept `scripts/continuum-github-https.sh` kann anschließend die korrekte Remote-/Credential-Anbindung prüfen.

## B – Sicherer Doctor und echtes Kotlin-/Haupt-App-QA

Nach einem erfolgreichen Checkout:

```bash
cd "$HOME/device-ai-fold7-candidate" || exit 1
head="$(git rev-parse HEAD)" || exit 1
node --test scripts/continuum-fold7-owner-update-gate.test.mjs || exit 1
bash scripts/continuum-fold7-owner-update-gate.sh doctor "$head"
```

Doctor prüft die tatsächliche **JDK17**, **Gradle8.11.1**, **Android SDK36**, **natives ARM64-AAPT2**, **apksigner** und Node-LTS-Versionen. Fehlt etwas, endet er **BLOCKED**, ohne einen Build zu behaupten. Eine leere Termux-Installation besitzt diese vollständige Toolchain **nicht automatisch**; der Download/Einstieg in private Buildwerkzeuge ist ein separater, zu prüfender Schritt. Der Shell-/Node-Startcheck ersetzt **keine** Android-Kompilierung.

Nur wenn `CONTINUUM_LOCAL_DOCTOR=READY` gemeldet wird:

```bash
cd "$HOME/device-ai-fold7-candidate" || exit 1
head="$(git rev-parse HEAD)" || exit 1
export CONTINUUM_OWNER_NEXT_VERSION_CODE=26
bash scripts/continuum-fold7-owner-update-gate.sh qa "$head"
```

Den Versionscode 26 nur benutzen, **wenn** die aktuell installierte Besitzer-App eine niedrigere `versionCode` hat; sonst den nächsten höheren Wert auswählen. QA führt die **vollständige ungefilterte** JUnit-Suite mit echten Original-XML-Protokollen, konfiguriertes Lint und den echten Haupt-App-Gradle-Build aus. Die isolierte QA-APK ist nur als Quell-/Buildnachweis nutzbar; sie ist mit einem **anderen Zertifikat signiert** und **darf NICHT installiert** werden. Kein Downgrade, keine Deinstallation, keine Datenverlust-Umgehung.

## C – Ausschließlich nach bestandener QA: originale installierbare Haupt-App

Die installierte `at.heinz.deviceai.mentorpaired` braucht zum Update denselben privaten Original-PKCS12-Schlüssel mit Zertifikats-SHA256:

```text
2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc
```

Der öffentliche Fingerabdruck enthält **nicht** den privaten Schlüssel. Ist die P12 nicht sicher lokal verfügbar, bleibt die Besitzer-APK **BLOCKED**; keinen Ersatzkey erzeugen und die installierte App niemals zum Austausch deinstallieren.

Nach unabhängiger Prüfung der QA-Rohberichte, des tatsächlich installierten Versionscodes und der vorhandenen *Original-P12* **nur privat auf dem Gerät**:

```bash
cd "$HOME/device-ai-fold7-candidate" || exit 1
head="$(git rev-parse HEAD)" || exit 1
export CONTINUUM_OWNER_LOCAL_APPROVED_SHA="$head"
export CONTINUUM_OWNER_NEXT_VERSION_CODE=26
export CONTINUUM_MENTOR_SIGNING_FILE="$HOME/.config/device-ai/mentor-preview-signing.p12"
read -r -s -p 'Original-P12-Kennwort: ' CONTINUUM_MENTOR_SIGNING_PASSWORD
printf '\n'
export CONTINUUM_MENTOR_SIGNING_PASSWORD
bash scripts/continuum-fold7-owner-update-gate.sh owner "$head"
unset CONTINUUM_MENTOR_SIGNING_PASSWORD
```

P12-Pfad und Versionscode müssen den **wirklichen lokalen Verhältnissen** entsprechen; Code26 ist kein pauschaler Freigabewert. Die vorhandene Besitzer-Buildstrecke `continuum-local-owner-apk.sh build` führt Android-Tests/Lint/Assembly durch und prüft danach Original-Zertifikat, Paketname, Versionscode, SHA-256. Ergebnis bleibt als Datei außerhalb des Quellbaums; **keine automatische Installation**. Nach erfolgreicher echter Signierung darf der Besitzer das Android-Systemupdate über die normale Installationsbestätigung durchführen.

## D – Erst auf der installierten Version: Suchqualität messen

Eine realistische Vorher-/Nachher-Messung benutzt dieselben acht vorher festgelegten deutschsprachigen Recherchefragen, denselben lokalen Gemma-Modell-Hash, gleiche Netzberechtigungen und eine gesicherte Quellenliste. Insbesondere: positive und negierte Aussagen zu kabellosem Laden, Updates, Akkudauer und Funktionen; mindestens zwei unabhängige Domänen, eine bewusst unbeantwortbare Frage sowie Quellen mit falscher oder widersprüchlicher Aussage. Pro Frage dokumentieren: Quellen-URLs, tatsächlichen Quelleninhalt/Zeitpunkt, widersprüchliche Belegkette, korrekte Abstinenz, echte Fehlentscheidungen, lokale Latenz, RAM, Temperatur und Anzahl Netzaufrufe. Der Experimenteinstieg darf ohne erteilte Web-Freigabe nicht recherchieren; keine automatischen Memory- oder Modellgewichtsänderungen. Erst ein belastbarer Baseline/Kandidat-Vergleich erlaubt die Aussage **spürbar besser**.

## Technische Rückfallgrenzen

PR #205 bleibt Draft auf #204 und #203; `main` unberührt. Dieser dokumentierte Prüfweg ist **kein Beleg eines bereits durchgeführten Fold7-Builds**, keiner realen Gemma-Ausführung und keiner fertigen Besitzer-APK. Der vorangehende lokale Testlauf des neuen Shell-Orchestrators bestand 9/9 Node-Tests und Bash-Syntax, nicht die Android-Regression.
