# Code 50 (0.3.49 / versionCode 50) — Besitzer-Release-Runbook

> **Status: VORSCHLAG · WIP / UNVERIFIED · reine Dokumentation.**
> Es wurde nichts gebaut, signiert, veröffentlicht, deployt oder installiert. Code 50 hat **keinen** SHA-256 und **keinen** Download-Link.
> Dieses Dokument ändert keine Skripte, löst keinen Workflow, keinen Render-Deploy und keine Signierung aus und enthält keine Geheimnisse.
> Ausführen darf die hier beschriebenen Schritte nur der Besitzer oder ein vom Besitzer ausdrücklich beauftragter Prozess auf dem geschützten Host.

Entstanden am 08.11.2026 im Code-50-Multi-Agenten-Lauf (Agent 3 „Release und Originalsignatur-Pfad“, Lead Claude Sonnet 5.5) aus der Code-Baseline `cc8ee5fe2dffd98f41c505c01b5d9ffdae4d8cf9` (Code-tragender Commit `8bc695fd0d0fcc10ef0aa88684da8b57acc928f4`). Die Staging-Spitze trägt seither nur Doku-Commits des Leads; vor jeder Freigabe die exakte Spitze neu per `git ls-remote` lesen.

Statuskürzel: **[V]** selbst gelesen/abgefragt · **[A]** abgeleitet · **[U]** unverifiziert/nicht prüfbar.

## 0. Ablauf in einem Blick

```
Entscheidungen (B1–B3) → Freigaben (B4–B11) → optional Vorab-QA → EIN Publisher-Lauf (Weg R)
→ unabhängiger Readback + Zertifikatsprüfung → Installation durch den Besitzer → Fold7-Abnahme
→ erst danach eine Veröffentlichungsmitteilung
```

`SIGNED_STAGED_FOR_PINNED_HOST` (Ende des Publisher-Skripts) bedeutet nur „lokal gestaged“, **nicht** „veröffentlicht“ [V].

## 1. Ausgangslage (Belege)

| Gegenstand | Stand | Beleg | Status |
| --- | --- | --- | --- |
| Letzter Release | Code 48 / 0.3.47: GitHub-Release `continuum-local-code48-c9fffb7dda9c173b6c5ccd4104672b09fd94de15`, veröffentlicht 2026-11-07T11:31:31Z, aktuell `releases/latest`; APK `Continuum-Lokal-527753ade5ad5d932af6c42eb4bf374e8ad467f58fee7d5a16127f6730f4f520.apk`, 271.100.761 Bytes | `gh api repos/Ullmann27/device-ai/releases/latest` (GET, nur Metadaten) | [V]; Signatur der APK [U] (nicht geladen) |
| Render-Update-Katalog | `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json` — der einzige GET am 08.11.2026 scheiterte mit `curl: (6) Could not resolve host` (DNS-Sperre der Prüf-Sandbox). Welcher Code dort live ist, ist **unbekannt** | Befehl + Fehlerausgabe | [U] |
| Signierweg Code 48 | GitHub Actions: Workflow `continuum-code48-sign-retry.yml` auf Zweig `codex/continuum-code48-github-owner-20261107` (Lauf 2026-11-07T11:23:22Z, `success`), Environment `continuum-original-signing`; Release-Autor und Asset-Uploader `github-actions[bot]`. **Nicht** das Render-Skript (laut Code-48-Handoff scheiterte der Render-Deploy an `pipeline_minutes_exhausted`) | `gh api …/actions/runs?created=…`, `gh api …/releases/tags/<tag>` (GET) | [V] (Lauf, Uploader); Render-Aussage [U] (Handoff-Zitat) |
| Render-Publisher | Dienst `continuum-owner-v23-release-preflight` (`srv-dbf1tr2vcj2c73ehpfog`), statische Site, AutoDeploy aus, Buildkommando `bash scripts/render-continuum-m8-owner-release.sh`, Publishpfad `continuum-m8-owner-release`. Das Wurzel-`render.yaml` definiert nur das Backend, **nicht** den Publisher | `docs/research/omega/CONTINUUM-V36-OWNER-UPDATE-HANDOFF.md:12`; `render.yaml` | [U] (nicht live geprüft) |
| Code 50 | weder als signierte APK gebaut noch signiert noch veröffentlicht | Lead-Vorgabe 08.11.2026 | [V] |
| Diagnose-Artefakt | `Code50-UNSIGNED-NOT-INSTALLABLE` (149.031.186 Bytes) aus GitHub-Lauf 42478725998 (Head `8bc695fd…`), nicht abgelaufen, **läuft 2026-11-22T12:39:58Z ab**. Nicht installierbar, **keine Abnahme** | `gh api repos/Ullmann27/device-ai/actions/runs/42478725998/artifacts` (GET) | [V] |
| Original-Signer | SHA-256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`, Paket `at.heinz.deviceai.continuumlocal` | `scripts/continuum-owner-catalog-contract.mjs:12-14`; Publisher-Skript | [V] |
| Code-Baumhashes (Baseline `cc8ee5f`) | `app`=`b440b7e9cfb35ec73ff44cbd920a7c49d2292a1b`, `backend`=`f4bc00c1ba8afb0f115b26450757fd322843fd0c`, `scripts`=`01e9af0088fc2ab967830c2a17ed03cf9005311f`, `.github`=`e85d53ff42ecb1d1a631e6ac5c3643b88e591d84`, `gradle`=`4167f4ea961fcf6b4ae64c287eee7d16241e967e` | `git rev-parse cc8ee5f:<verz>` | [V] |

## 2. Ist Code 50 ein gültiges Upgrade? (Besitzerbestätigung am Fold7)

Code 50 liegt **strikt über** dem letzten veröffentlichten Stand (50 > 48) und über der historisch gemeldeten Installation 0.3.28 / versionCode 29 (50 > 29). Gleiches Paket und gleiche Originalsignatur vorausgesetzt, ist es ein gültiges **In-place-Update**, App-Daten bleiben erhalten [A, aus der Update-Policy abgeleitet]. Code 49 wurde nie veröffentlicht; die Lücke 48 → 50 ist unschädlich, weil nur `versionCode > installiert` verlangt wird [V].

| Installierter Stand am Fold7 | Update auf Code 50 | Grund |
| --- | --- | --- |
| Paket `at.heinz.deviceai.continuumlocal`, versionCode < 50, Signer = Original | **gültig** | strikt höherer Code, gleiche Signatur |
| versionCode = 50 | abgelehnt | `versionCode <= installiert` → `OLD_OR_EQUAL_VERSION` (`ContinuumSignedUpdatePolicy.kt:81`) [V] |
| versionCode > 50 (z. B. QA-/Testbuild) | abgelehnt (Downgrade) | dieselbe Regel [V] |
| anderer Signer (Debug, QA, Preview) | abgelehnt (App) bzw. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (Android) | Signatur muss dem Original entsprechen (`ContinuumOwnerUpdater.kt:288-309`) [V]/[A] |
| anderes Paket (z. B. `.qa`-Suffix, Mentor Pair) | kein Update, nur Parallelinstallation | Paketname wird geprüft [V] |

**Der Besitzer bestätigt vor dem Lauf schriftlich** (nichts davon kann ein Agent ersetzen): installierte `versionName` / `versionCode` und möglichst der installierte Signer. Die historische Angabe 0.3.28 / code29 ist [U], ob heute Code 48 oder etwas anderes installiert ist, ist unbekannt.

```bash
# am Fold7 (App-Info zeigt versionName 0.3.<Code-1>) oder per ADB:
adb shell dumpsys package at.heinz.deviceai.continuumlocal | grep -E "versionCode=|versionName="
# installierten Signer prüfen (Prüfordner außerhalb des Repos):
adb shell pm path at.heinz.deviceai.continuumlocal
adb pull <base.apk-Pfad aus der Ausgabe> installed-base.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs installed-base.apk   # Signer #1 SHA-256 muss 6354e91d…ee23 sein
```

**Nie deinstallieren, um ein Update zu erzwingen** (Datenverlust: Gedächtnis, Modelle, Einstellungen).

## 3. Kanal-Entscheidung des Besitzers (Voraussetzung für alles Weitere)

Es gibt zwei belegte Wege. Welcher für Code 50 zulässig ist, entscheidet **nur der Besitzer** (im Lead-Handoff als Besitzerentscheidung C geführt; diese Fassung liegt erst auf Staging, nicht auf dem Stand `cc8ee5f`).

| | **Weg R — Render-Publisher** | **Weg G — GitHub-Environment (Präzedenz Code 48)** |
| --- | --- | --- |
| Beleg | `scripts/render-continuum-m8-owner-release.sh:15-18` → `render-continuum-owner-continuous-release.sh` [V] | `scripts/continuum-code48-github-signer.sh` und `.github/workflows/continuum-code48-sign-retry.yml` auf Commit `ec26df10c22edd47d966138683bae08f9722d3a0` des Zweigs `codex/continuum-code48-github-owner-20261107` [V] |
| Schlüssel | Original-P12 nur im Publisher-Dienst (Render-Env) | Original-P12/Passwort als **GitHub-Environment-Secrets** (`CONTINUUM_LOCAL_SIGNING_P12_BASE64`, `CONTINUUM_LOCAL_SIGNING_PASSWORD`; nur die Namen aus der Workflow-Datei gesehen, Werte nie gelesen) [V] |
| Regelkonformität | entspricht den Besitzerregeln (P12 nur auf dem originalen Host) | **Spannung** zu „P12/Passwort nie auf GitHub“ (CLAUDE.md/AGENTS.md) und zur P0-Regel „GitHub Actions keine APK-/CI-Abnahme“ → ausdrückliche Besitzerentscheidung nötig |
| Erreicht den In-App-Knopf? | ja, auch bei installiertem Code ≤ 48 (gepinnter Render-Host) | nein bei Code ≤ 48 (kennt nur Render); erst eine installierte Code-50-App fragt beide Kanäle ab — davor **manuelle In-place-Installation** der GitHub-APK (`ContinuumOwnerUpdater.kt:41-90`; `ContinuumGitHubSignedUpdatePolicy.kt` fehlt im Code-48-Quellstand `c9fffb7d`) [V] |
| Stand der Werkzeuge | Skripte vorhanden und getestet (Node-Verträge) | Signierer ist für Code 48 **hartkodiert** (SHA, QA-Lauf, Workflow-SHA, Version, Artefakt-/Receipt-Namen: `…signer.sh:8-13,39,45,74`) und passt nicht zu den Namen des Code-50-QA-Workflows (`android-host` statt `android-unsigned`, `Code50-UNSIGNED-NOT-INSTALLABLE`, `versionCode=50` statt `version_code=…`: `continuum-code50-github-qa.yml:17,60,117-126`) → **neuer, geprüfter Code-50-Signierer nötig** (neue Infrastruktur, hier NICHT enthalten) [V] |
| Schutz | alle Gates im Skript | Environment hat nur eine Branch-Regel (`codex/continuum-code48-github-owner-20261107`), **keine Pflicht-Reviewer, keinen Wartetimer** (`gh api repos/Ullmann27/device-ai/environments/continuum-original-signing`, GET); jeder mit Push-Recht auf diesen Zweig kann Workflow-Code mit Schlüsselzugriff ändern [V]/[A] |
| Kapazität | Render-Pipeline-Minuten laut Docs erschöpft [U]; ein voller Lauf ≈ 36 min (Code-44-Läufe: 21:56:14 → 22:32:19 UTC, Gradle 29m13s) [V] | GitHub-Actions-Laufzeit; Verfügbarkeit laut P0-Hinweis unzuverlässig [U] |

Empfehlung [A]: **Weg R** ist der dokumentierte, regelkonforme Weg und Grundlage dieses Runbooks. Weg G nur nach ausdrücklicher Besitzerentscheidung, mit Pflicht-Reviewer (Besitzer) am Environment und einem neu geschriebenen, geprüften Signierer.

## 4. Checkliste der vom Besitzer zu liefernden Freigaben

- [ ] **B1 Kanal:** Weg R, Weg G oder beide; bei G ausdrückliche Zulassung der GitHub-Environment-Secrets und Entscheidung zu Pflicht-Reviewern (siehe §3).
- [ ] **B2 Freigegebener Quell-SHA** (40 Hex, vom Besitzer festgelegt, **nicht** automatisch `cc8ee5f` oder die Staging-Spitze). Gate-Diagnose lief auf `8bc695f`; `cc8ee5f` und spätere Staging-Spitzen unterscheiden sich davon laut Lead nur durch Doku — die Code-Baumhashes (§1) belegen das. Nach einem Flake-Fix (B7) entsteht ein neuer Code-Commit → neue SHA, neue QA.
- [ ] **B3 Freeze-Aufhebung:** `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER` = exakt B2. Setzt nur der Besitzer (`render-continuum-owner-continuous-release.sh:17-21`).
- [ ] **B4 Genehmigter SHA:** `CONTINUUM_RELEASE_APPROVED_SHA` = exakt B2 (gleiche Zeilen).
- [ ] **B5 Version:** `CONTINUUM_RELEASE_VERSION_CODE=50` → erzwungen `versionName=0.3.49` (`…release.sh:42-43`).
- [ ] **B6 Original-P12 + Passwort** liegen ausschließlich im geschützten Host (Fingerabdruck `6354e91d…ee23`, Alias `continuum-local`, PKCS12). Besitzer bestätigt das, ohne die Werte zu senden.
- [ ] **B7 `critical`-Flake** (`storage-agent`, Fixture `timeoutMs: 500`, `.github/continuum/storage-fabric.test.mjs:42`): Entscheidung A/B des Lead-Handoffs §11 getroffen und derselbe SHA stabil grün gesehen. Das QA-Skript hat **keinen Retry**: jede rote Gate bricht ab (`render-continuum-owner-continuous-qa.sh:250-253`).
- [ ] **B8 Kapazität:** Render-Pipeline-Minuten/Plan (nur Besitzer) bzw. Verfügbarkeit des gewählten Kanals.
- [ ] **B9 Fold7-Installationsstand** bestätigt (§2): Version < 50, Signer = Original.
- [ ] **B10 Release-Text:** keine Behauptung „Fold7 verifiziert“ ohne Gerätenachweis (`REAL_DEVICE_NOT_RUN` bleibt bis zur Abnahme); ab Code 50 sehen Geräte beide Kanäle (anders als der Code-48-Text „update button remains pinned to Render“).
- [ ] **B11 Fold7-Abnahme nach der Installation** (Updateknopf, Sprache, Orb, Thermik, Datenerhalt): getrennte Geräteprüfung, nie durch Emulator-/Robolectric-/Host-Ergebnisse ersetzt.

## 5. Host-Voraussetzungen

### 5.1 Weg R — Render-Publisher

- **Dienst:** `continuum-owner-v23-release-preflight`. Zustand (Branch, letzter Deploy, **nur die Namen** der Env-Variablen) vorab per Render-API (GET) lesen und festhalten [U].
- **Öffentliche Env-Felder:** `CONTINUUM_RELEASE_VERSION_CODE=50`, `CONTINUUM_RELEASE_APPROVED_SHA=<SHA>`, `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER=<SHA>`, `CONTINUUM_RELEASE_HOST=continuum-owner-v23-release-preflight.onrender.com`; `RENDER_SERVICE_NAME=continuum-owner-v23-release-preflight` (`…release.sh:22-34`) [V].
- **Private Env-Felder** (nur dort, nie anzeigen): `CONTINUUM_LOCAL_SIGNING_P12_BASE64`, `CONTINUUM_LOCAL_SIGNING_PASSWORD`; `CONTINUUM_MENTOR_SIGNING_*` müssen **leer** sein (`…release.sh:22-34`) [V].
- **Laufzeit:** Node-Major **24** (QA bricht sonst ab: `…qa.sh:108`; der Preflight akzeptiert zusätzlich 20/22, ein Preflight-PASS genügt also nicht, `continuum-release-host-preflight.mjs:68`), `python3`, `git`, `timeout`, `readlink`. Ausgehendes HTTPS zu `github.com` (JDK-Archiv, historischer Commit), `dl.google.com` (cmdline-tools, `platforms;android-36`, `build-tools;35.0.0`), `services.gradle.org` (Gradle 8.11.1). Die Toolchain holt das QA-Skript selbst: JDK `17.0.20.1+1` und cmdline-tools mit gepinntem SHA-256 (`…qa.sh:59-60,81`), Gradle-Prüfsumme vom selben Host (`…qa.sh:93-94`) [V].
- **Checkout:** detached HEAD auf exakt `<SHA>` (Render-Standard) oder lokaler Zweig `integration/continuum-all-systems-apk-qa-20261102`; **jeder andere benannte Zweig bricht ab** („Wrong source branch…“, `…qa.sh:28-33`). Sauber, keine ungetrackten Quellen unter `app/src scripts backend/src backend/test .github/continuum` (`…release.sh:35-43`) [V].
- **Historischer Commit:** `5270202e0699764b0eac5d4dd3e6a587b982ae05` per Fetch oder der gepinnte Blob-Snapshot (Tree `87e4c52821c4d5b5c61d4bab912b1dbe2aec1c25`), sonst Abbruch (`…qa.sh:115ff`) [V].
- **Last:** Gradle `--no-daemon --max-workers=2`, `-Xmx1600m`; JDK 17 → die zwei JDK-21-Tests (`LocalModelReloadLifecycleTest`) bleiben übersprungen, erwartet ist das offizielle Ergebnis 4032 / 0 / 20 [A, aus der Lead-Vorgabe].
- **Preflight** `continuum-release-host-preflight.mjs` ist ein rein lesender Vorab-Check (Node, JDK 17, SDK 36, Gradle 8.11.1, aapt2 35.0.0, sauberer Checkout, vertrauenswürdiger Origin, kein Zweig `main|master|prod|production|release`). Er liest aber `scripts/continuum-fusion-full-regression.sh` (Marker `-PcontinuumStandaloneVersionCode=27`) und `scripts/continuum-fold7-standalone-apk.sh`, **nicht** die Render-Skripte (`…preflight.mjs:43,57-61`) — er ist kein Beleg für den Code-50-Releasepfad [V].

### 5.2 Weg G — nur nach Besitzerentscheidung (siehe §3)

Environment `continuum-original-signing` (angelegt 2026-11-07T09:51:15Z), Secrets nur im Environment, Branch-Regel nur `codex/continuum-code48-github-owner-20261107` [V]. Ein Code-50-Signier-Workflow auf einem anderen Zweig erhielte die Secrets **nicht**, solange der Besitzer die Branch-Regel nicht ändert [A]. Runner `ubuntu-latest`, JDK 17 (Temurin), `build-tools;35.0.0` [V].

## 6. Befehlsreihenfolge (nur dokumentiert, NICHT ausgeführt)

### 6.1 Vorab, lesend (jede Maschine, keine Schlüssel)

```bash
SHA=<vom Besitzer freigegebener 40-stelliger Commit>
URL=https://github.com/Ullmann27/device-ai.git
git ls-remote "$URL" "refs/heads/<publisher-zweig>"            # muss exakt $SHA zeigen
gh api "repos/Ullmann27/device-ai/compare/c9fffb7dda9c173b6c5ccd4104672b09fd94de15...$SHA" --jq '{status,ahead_by,behind_by}'
                                                               # erwartet: ahead, behind_by 0 (Code-48-Quelle ist Vorfahr)
for d in app backend scripts .github gradle; do printf '%s ' "$d"; git rev-parse "$SHA:$d"; done
                                                               # mit §1 vergleichen: gleich = kein Code-Unterschied zur QA-Baseline
gh api repos/Ullmann27/device-ai/releases/latest --jq '.tag_name'   # Stand vor dem Lauf festhalten
```

### 6.2 Optionaler lokaler Host-Check (nicht aussagekräftig für den Render-Pfad)

```bash
git checkout --detach "$SHA"
node scripts/continuum-release-host-preflight.mjs --audit "$PWD" "$SHA"   # Exit 0 = bereit, Exit 69 = blockiert
```

### 6.3 Weg R — Render-Publisher (Besitzer oder ausdrücklich beauftragter Prozess)

Muster: `docs/evidence/code48-final-qa-20261107/release-preflight/code48-original-publisher-plan.json` (Status dort `PREPARED_NOT_EXECUTED`; der Plan nennt die SHA `5d91442e…`, veröffentlicht wurde `c9fffb7d…` — für Code 50 die SHA aus B2 verwenden) [V].

1. Zustand per Render-API **lesen** (Branch, Deploy-Status, Env-Namen).
2. Öffentliche Env-Felder **einzeln** setzen (Merge-Update je Schlüssel): `CONTINUUM_RELEASE_VERSION_CODE=50`, `CONTINUUM_RELEASE_APPROVED_SHA=$SHA`, `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER=$SHA`. **Niemals die gesamte Env-Liste ersetzen** (würde private Signierwerte löschen). Private Werte nicht anfassen.
3. Publisher-Zweig nur per **Fast-Forward** auf `$SHA` mit frisch gelesenem Expected-SHA (kein Force-Push; `main` unberührt).
4. **Genau ein** manueller Deploy; AutoDeploy bleibt aus. Bei Fehler **kein** zweiter Deploy ohne neue Besitzerfreigabe.
5. Der Render-Build führt aus (nicht von Hand):

```
bash scripts/render-continuum-m8-owner-release.sh          # Code >= 39 → exec (…m8-owner-release.sh:15-18)
 └ scripts/render-continuum-owner-continuous-release.sh
   1. Gates: exakter SHA ×2, Host, Dienstname, Legacy-Variablen leer, P12/Passwort gesetzt, sauberer Checkout, Code 39..9999   (Z. 17-43)
   2. Live-Katalog lesen, 12 Felder prüfen, Code > live-Code                                                                (Z. 45-65)
   3. QA OHNE Signiermaterial (CONTINUUM_OWNER_RELEASE_QA=1): Node-special, critical, full-node, Backend, Android
      compile/test/lint, Pflicht-Suiten, unsignierter Release+Bundle; jede rote Gate = Abbruch                              (Z. 67-73; qa.sh)
   4. P12 privat dekodieren; keytool: Alias continuum-local, PrivateKeyEntry, SHA-256 == 6354e91d…ee23                         (Z. 92-102)
   5. gradle … -PcontinuumLocalStandalone=true -PcontinuumStandaloneVersionCode=50 :app:lintRelease :app:assembleRelease
   6. apksigner verify --verbose --print-certs (v2 gültig, Signer-SHA), aapt2 dump badging (Paket/Code/Name), Größe, SHA-256    (Z. 121-130)
   7. Staging continuum-m8-owner-release/continuum-local/Continuum-Lokal-<sha256>.apk + latest.json
      + node scripts/continuum-owner-catalog-contract.mjs                                                                    (bis Z. 178)
   8. BUILD-INFO.txt  status=SIGNED_STAGED_FOR_PINNED_HOST   ← NICHT veröffentlicht                                           (Z. 180-185)
```

6. **Unabhängiger Readback** durch eine zweite Instanz (nicht der Publisher), nachdem der Dienst die Bytes tatsächlich ausliefert:

```bash
node scripts/continuum-owner-render-readback.mjs --manifest-only   # nur latest.json
node scripts/continuum-owner-render-readback.mjs                   # inkl. APK-Hash (Status APK_HASH_VERIFIED_CERT_NOT_VERIFIED: Zertifikat NICHT geprüft!)
```

7. Zertifikat, Paket und Version selbst prüfen (§8). Erst danach „veröffentlicht“ melden.
8. Installation: **Besitzer** am Fold7 über „Updates prüfen und laden“ (Android fragt per `PackageInstaller` den Besitzer; `ContinuumOwnerUpdater.kt:310-340`), danach B11.

### 6.4 Weg G — nur nach Besitzerentscheidung (Vorschlag, nichts davon existiert für Code 50)

1. Quell-QA auf exakt `$SHA` mit Receipt und unsigniertem Artefakt. Der vorhandene Workflow `continuum-code50-github-qa.yml` ist Diagnose; ihn als Freigabegrundlage zu nutzen widerspricht der P0-Regel und ist Besitzerentscheidung.
2. **Neuer** Code-50-Signierer mit mindestens den Prüfungen des Code-48-Signierers (`…signer.sh:21-47` QA-Identität/Artefakt/Receipt/unsignierter Zustand, `:49-81` P12-Fingerabdruck, zipalign, `apksigner sign`, `verify`, Badging, Größe, `:83-105` Draft, Readback, Veröffentlichung) — mit Code-50-Werten und ohne Hartkodierung an mehreren Stellen.
3. Release: Tag `continuum-local-code50-<SHA40>`, **genau ein** APK-Asset `Continuum-Lokal-<sha256>.apk` (Asset-`digest` = `sha256:<sha256>`), nicht Draft/Pre-Release (`ContinuumGitHubReleaseCatalog.kt:16-17,25-28,49,54`). Das Code-48-Skript veröffentlicht mit `--latest=false` (`…signer.sh:94,104`), die App liest aber `releases/latest` — **nach der Veröffentlichung `releases/latest` per GET prüfen** [V]/[U].
4. Readback und §8.

## 7. Was das Publisher-Skript selbst erzwingt — und was nicht

**Erzwungen** (`scripts/render-continuum-owner-continuous-release.sh`, Exit 69 bei Verstoß) [V]:

| Prüfung | Zeilen |
| --- | --- |
| Freeze-Aufhebung **und** Approved-SHA gleich dem exakten HEAD | 17-21 |
| Gepinnter Host und Dienstname; Legacy-Mentor-Variablen leer; P12 + Passwort gesetzt | 22-34 |
| Sauberer Checkout; `39 ≤ Code ≤ 9999`; `versionName = 0.3.(Code-1)` | 35-43 |
| Live-Katalog streng (12 Felder, `OWNER_RELEASE`, `approved`, Paket, Signer); **Code strikt größer als der live veröffentlichte Code** | 45-65 |
| Volle QA ohne Signiermaterial, HEAD unverändert | 67-73 |
| P12: Alias `continuum-local`, `PrivateKeyEntry`, Fingerabdruck `6354e91d…ee23` | 92-102 |
| `apksigner`: v2 gültig + Signer-SHA; `aapt2`: Paket/Code/Name; Größe 1 MB…2 GB; SHA-256 | 121-130 |
| Katalogvertrag gegen den Android-Parser (`verifyOwnerCatalog`); `latest.json` `wx` (nie überschreiben) | bis 178 |

Die QA selbst erzwingt zusätzlich u. a. Node 24 (`…qa.sh:108`), Dienstidentität `owner_qa=1` nur auf dem Publisher mit Approved-SHA (`:16-21`), **kein** Signiermaterial in der QA (`:50`), unsignierte Release-APK mit fehlschlagendem `apksigner verify` (`:243-247,260-263`) und `gateFailures > 0` ⇒ Abbruch (`:250-253`).

**NICHT automatisiert** [V]/[A]:

- Vergleich mit der auf dem Fold7 **installierten** Version (das Skript vergleicht nur mit dem Live-Katalog).
- Prüfung, ob der SHA ein genehmigter PR-/Branch-Stand ist (nur Gleichheit mit den Env-Werten).
- Setzen der Env-Felder, Fast-Forward des Publisher-Zweigs, Deploy, Pipeline-Minuten.
- **Veröffentlichung** und Readback inklusive Zertifikat (`continuum-owner-render-readback.mjs:110` meldet `CERT_NOT_VERIFIED`).
- GitHub-Release (nicht Teil des Render-Skripts).
- Installation und Fold7-Abnahme; Widerruf/Rollback; Schlüsselverwaltung.

## 8. Nachweisbefehle nach der Signierung (unabhängig vom Publisher; nichts davon wurde ausgeführt)

In einem Prüfordner **außerhalb des Repos**, APK nur aus der offiziellen Quelle:

```bash
APK=Continuum-Lokal-<sha256>.apk
BT="$ANDROID_HOME/build-tools/35.0.0"
sha256sum "$APK"                  # == <sha256> im Dateinamen == latest.json.apkSha256 == GitHub-digest
stat -c %s "$APK"                 # == latest.json.byteLength; 1.000.000 <= n <= 2.000.000.000
"$BT/apksigner" verify --verbose --print-certs "$APK"
   # erwartet: "Verified using v2 scheme (APK Signature Scheme v2): true"
   #           "Signer #1 certificate SHA-256 digest: 6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
"$BT/aapt2" dump badging "$APK" | head -n 1     # (alternativ aapt dump badging)
   # erwartet: package: name='at.heinz.deviceai.continuumlocal' versionCode='50' versionName='0.3.49'
```

Manifest (Weg R):

```bash
curl -fsS https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json -o latest.json
node scripts/continuum-owner-catalog-contract.mjs latest.json   # Exit 0 + CONTINUUM_OWNER_CATALOG_ANDROID_COMPATIBLE …
jq -e --arg sha "$SHA" '.schema==1 and .channel=="OWNER_RELEASE" and .approved==true
  and .packageName=="at.heinz.deviceai.continuumlocal"
  and .signerSha256=="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
  and .versionCode==50 and .versionName=="0.3.49" and .sourceCommit==$sha' latest.json
```

GitHub-Release (Weg G):

```bash
gh api repos/Ullmann27/device-ai/releases/latest --jq '{tag:.tag_name,draft,prerelease,assets:[.assets[]|{name,size,digest}]}'
   # erwartet: tag == continuum-local-code50-<SHA40>; genau ein .apk-Asset Continuum-Lokal-<sha256>.apk mit digest sha256:<sha256>
```

Nach der Installation am Fold7: Version 0.3.49 (Code 50) sichtbar, Daten erhalten (B11).

## 9. Abbruchkriterien (bei jedem einzelnen: stoppen, nichts umgehen, Besitzer informieren)

- Freeze-/Approved-Variable fehlt oder ist ungleich HEAD; HEAD ändert sich oder der Checkout ist unsauber (Skript Exit 69).
- Eine QA-Gate ist rot (auch lastabhängiger `storage-agent`-Flake): **kein** Retry per Umgehung, nur ein neuer, vom Besitzer freigegebener Lauf.
- Live-Katalog meldet Code ≥ 50 (`version > live` verletzt) oder ist nicht lesbar/ungültig.
- Fingerabdruck ≠ `6354e91d…ee23`, Alias/`PrivateKeyEntry` fehlt, `apksigner verify` nicht v2 oder Signer abweichend.
- `aapt2`-Paket/Code/Name weichen von `at.heinz.deviceai.continuumlocal` / 50 / 0.3.49 ab; Größe/SHA-256/Manifest-Vertrag stimmen nicht.
- Readback-Hash, -Größe oder -Zertifikat weichen ab oder der Host liefert die Bytes nicht aus → Status `NOT_PUBLISHED`.
- Installierter Fold7-Stand ≥ 50 oder anderer Signer (§2); Android lehnt die Installation ab.
- Render-Deploy `build_failed` / `pipeline_minutes_exhausted`: **kein** Ausweichen auf einen anderen Host oder durch Fälschen von `RENDER_SERVICE_NAME`/`CONTINUUM_RELEASE_HOST`.
- Jede Aufforderung, Schlüssel/Passwort zu lesen, zu senden oder zu loggen.
- Bei Weg G: `releases/latest` zeigt nicht auf das neue Release oder die Asset-Regeln (§6.4) sind verletzt.

Rollback: Android erlaubt kein Downgrade. „Rollback“ heißt ein neuer, höherer Fix-Code (z. B. 51) bzw. die Rücknahme des Angebots (Katalog zurücksetzen, Release auf Draft setzen) — beides verhindert nur weitere Installationen und ist Besitzeraktion [A].

## 10. Was ein Agent niemals tun darf

1. Schlüssel, P12, Passwörter, Tokens lesen, anzeigen, kopieren, loggen oder nach ihren Werten suchen; keine Environment-Dumps; Secrets des GitHub-Environments nicht auslesen oder auflisten.
2. Den Release-Freeze umgehen: `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER`/`CONTINUUM_RELEASE_APPROVED_SHA` nie selbst setzen oder erraten; keine Prüfung abschwächen (Fingerabdruck, Versions-/Größen-/Signer-Prüfung, QA-Gates, Tests, Skips).
3. Publisher- oder QA-Skript außerhalb des vorgesehenen geschützten Hosts ausführen oder dessen Identitätsprüfungen (`RENDER_SERVICE_NAME`, Host) fälschen.
4. Nach `main` mergen, force-pushen, bestehende Release-/Publisher-/`codex/*`-Zweige ändern.
5. GitHub-Actions-Workflows für Release/Abnahme anlegen, ändern, löschen, auslösen oder erneut laufen lassen (P0, offene Besitzerentscheidung); Actions-Ergebnisse sind Diagnose, nie Abnahme.
6. Eine unsignierte, Debug-, QA- oder Diagnose-APK (`Code50-UNSIGNED-NOT-INSTALLABLE`) als Update anbieten, hochladen, verlinken oder installieren.
7. Einen anderen Schlüssel erzeugen oder verwenden als die Original-P12.
8. Deinstallieren oder Daten/Gewichte löschen, um ein Update zu erzwingen; Installation ohne Besitzer am Gerät auslösen.
9. Erfolg behaupten ohne Nachweis: „veröffentlicht“ erst nach unabhängigem Readback (Hash, Größe, Signatur, Paket/Version); „Fold7 verifiziert“ nur nach echter Geräteprüfung; `SIGNED_STAGED_FOR_PINNED_HOST` ist nicht „veröffentlicht“.
10. Render-Ressourcen ohne ausdrücklichen Besitzerauftrag verändern (Env-Liste nie komplett ersetzen, Dienste nicht löschen/neu anlegen, Pläne/Abrechnung nicht ändern); mehr als einen Deploy pro Freigabe auslösen.
11. Eine Fremd-/Cloud-Buildumgebung als Abhängigkeit einführen (Android-First).

## 11. Offene Punkte / NICHT_AUSGEFÜHRT

- Render-Live-Katalog, Render-API-Zustand und Pipeline-Minuten: nicht lesbar (DNS-Sperre, kein Token) → [U].
- Signatur/Zertifikat der Code-48-APK: nicht geladen, nicht geprüft → [U].
- Kein Build, kein Gradle, kein `apksigner`/`aapt2`, kein Publisher-/QA-Skript, keine Signierung, kein Workflow, kein Deploy, keine Installation.
- Fold7: installierte Version/Signatur und alle Geräteprüfungen: NICHT_AUSGEFÜHRT.
- Existenz und Aktualität der GitHub-Environment-Secrets: nicht geprüft (bewusst nicht aufgelistet).
- `continuum-owner-update-manifest.mjs`, `continuum-owner-release-stage.mjs` und `continuum-fold7-standalone-apk.sh` (Termux-/Fold7-Lokalroute) wurden für dieses Runbook nicht ausgewertet.

## 12. Quellenverzeichnis (Stand `cc8ee5f`, sofern nicht anders angegeben)

- `scripts/render-continuum-m8-owner-release.sh:15-18`; `scripts/render-continuum-owner-continuous-release.sh:17-185` (Zeilen siehe §7).
- `scripts/render-continuum-owner-continuous-qa.sh:12-33,50,59-60,81,93-94,108,115,147,171,209,243-263`.
- `scripts/continuum-release-host-preflight.mjs:13,36,43,57-61,68,101-113`; `scripts/continuum-owner-render-readback.mjs:110,115-124`; `scripts/continuum-owner-catalog-contract.mjs:12-14` (Funktion `verifyOwnerCatalog`); `scripts/continuum-owner-continuous-release.test.mjs` (4 Verträge, Negativtest „ohne Freeze-Aufhebung Exit 69“).
- Updater: `ContinuumSignedUpdatePolicy.kt:44-87`; `ContinuumGitHubSignedUpdatePolicy.kt:26,36-47`; `ContinuumGitHubReleaseCatalog.kt:16-17,25-28,49,54`; `ContinuumOwnerReleaseSelector.kt:13-22`; `ContinuumOwnerUpdater.kt:41-90,282-340` (alle unter `app/src/main/java/at/heinz/deviceai/intelligence/`).
- Code-48-Signierweg (Zweig `codex/continuum-code48-github-owner-20261107`, Commit `ec26df10c22edd47d966138683bae08f9722d3a0`): `scripts/continuum-code48-github-signer.sh:7-13,15,21-47,49-81,83-105`; `.github/workflows/continuum-code48-sign-retry.yml`.
- `.github/workflows/continuum-code50-github-qa.yml:17,60,117-126`.
- `docs/release/CONTINUUM-CODE44-BUILD-HANDOFF-20261105.md` (Laufdauern), `docs/research/omega/CONTINUUM-V36-OWNER-UPDATE-HANDOFF.md:12,54,58`, `docs/research/omega/CODE48-RELEASE-COMPLETION-HANDOFF.md:134-136`, `docs/evidence/code48-final-qa-20261107/release-preflight/code48-original-publisher-plan.json`, `docs/release/CONTINUUM-OWNER-UPDATE-CHANNEL.md` (historisch, Code 38 — nicht mehr maßgeblich).
- GitHub-GETs (08.11.2026): `releases/latest`, `releases/tags/continuum-local-code48-…`, `actions/runs/42478725998[/artifacts]`, `actions/runs?created=2026-11-07…`, `environments/continuum-original-signing[/deployment-branch-policies]`.
