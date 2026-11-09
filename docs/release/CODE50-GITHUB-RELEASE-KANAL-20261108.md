# Code 50 — GitHub-Release-Kanal (Besitzerentscheidung C, 08.11.2026)

**Status:** Quellvorbereitung. **Nichts ist signiert, veröffentlicht oder installiert.** Es gibt weder eine Original-signierte APK 50 noch einen SHA-256 noch einen Download-Link.

## 1. Entscheidung und Grenzen

Der Besitzer hat für Code 50 (`0.3.49` / `versionCode 50`, `at.heinz.deviceai.continuumlocal`) den Kanal **GitHub statt Render** festgelegt („Über GitHub soll er es machen, wie vorher auch. Render haben wir ausgelassen.“). Das ersetzt für Code 50 allein den Render-Kanal der Entscheidung vom 31.10.2026. Die Ausnahme steht eng begrenzt in `AGENTS.md` und `CLAUDE.md`.

Die Kanalwahl ist **nicht**: eine erfolgte Signierung, die Aufhebung des Release-Freeze, eine Abnahme, unbegrenzte Schlüsselverwendung oder die Freigabe eines bestimmten Commits. Für die Signierung ist eine ausdrückliche, SHA-spezifische Besitzerfreigabe nötig (Abschnitt 4).

## 2. Was gebaut wurde

| Datei | Zweck |
|---|---|
| `.github/workflows/continuum-code50-github-qa.yml` | Nur `workflow_dispatch`, Pflichteingabe `expected_sha` (muss `GITHUB_SHA` entsprechen), Branch-Guard. Jobs `node-backend`, `android-host` (JUnit 4032 / 0 Fehler / 20 Skips hart geprüft, Lint, ungesigniertes Release), `android-lifecycle-jdk21` (gesondert, blockiert nichts). Kein Secret-Zugriff, keine Signierung. |
| `.github/workflows/continuum-code50-sign-release.yml` | Nur `workflow_dispatch`. Job `preflight` ohne Environment und ohne Geheimnisse, danach Job `release-original-signed` im Environment `continuum-original-signing`. |
| `scripts/continuum-code50-github-signer.sh` | Modi `preflight` und `sign`. Abgeleitet vom Code-48-Weg, aber mit Code-50-Jobnamen (`android-host`), Artefakt `Code50-UNSIGNED-NOT-INSTALLABLE` und neuem Receipt-Schema. |
| `.github/continuum/code50-github-release-contract.test.mjs` | Statischer Vertragstest (kein Push-Trigger, Geheimnisse nur im geschützten Job, keine Ausgabe von Schlüsselmaterial, Reihenfolge der Gates). |
| `.github/continuum/code50-github-signer-preflight.test.mjs` | Funktionale Tests der Preflight-Gates mit Fake-`gh` (7 Tests). |

## 3. Gates des Signierskripts (fail-closed, Exit 69)

1. **Environment-Schutz:** Das Environment muss einen Pflicht-Reviewer `Ullmann27` haben und **genau eine** Branch-Regel für `chatgpt/code50-qa-staging-20261108`. Sonst Abbruch, bevor ein Geheimnis freigegeben wird.
2. **Bindung:** `source_sha` = `owner_approved_sha` = `GITHUB_SHA` (Branch-Spitze), Event `workflow_dispatch`, richtiger Branch und Repository.
3. **QA-Lauf:** `head_sha` = Quell-Commit, Branch richtig, manuell gestartet, Workflowpfad richtig, abgeschlossen und erfolgreich; Jobs `node-backend` und `android-host` grün. Der JDK-21-Job wird nur ausgewiesen.
4. **Release:** Tag `continuum-local-code50-<40hex>` existiert noch nicht; Code 50 ist höher als das aktuelle GitHub-Latest.
5. **Ungesigniertes Artefakt:** SHA-256-Manifest, Receipt (`source_sha`, `qa_run_id`, `tests_total=4032`, `tests_failed=0`, `tests_errors=0`, `tests_skipped=20`, …), `aapt2` (Paket, Code 50, 0.3.49) und `apksigner` (muss unsigniert sein).
6. **Originalschlüssel:** nur über Umgebungsvariablen, nie ausgegeben. Der Fingerabdruck muss `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` sein.
7. **Signierung:** `zipalign`, `apksigner sign`, Prüfung (v2, Zertifikat, Paket, Größe), SHA-256.
8. **Veröffentlichung:** Entwurf anlegen, Readback prüfen, dann veröffentlichen als `Latest`. Danach öffentlicher Readback ohne Anmeldung genau wie im App-Katalog (`releases/latest`, genau eine APK `Continuum-Lokal-<sha256>.apk`, `digest`, Größe), Download über die öffentliche URL, SHA-256 und Zertifikat. Schlägt das nach der Veröffentlichung fehl, wird das Release wieder zum Entwurf gemacht.

## 4. Was nur der Besitzer tun kann

Das ist mit dem Repository-Token nicht möglich (HTTP 403 bei Environment-Secrets). Ich habe nichts davon geändert und keinen Schlüsselwert gelesen.

1. **Environment `continuum-original-signing` schützen** (GitHub → Settings → Environments):
   - *Required reviewers*: `Ullmann27` hinzufügen (Stand 08.11.2026: **keine** Reviewer eingetragen).
   - *Deployment branches*: auf **ausgewählte Branches** stellen und nur `chatgpt/code50-qa-staging-20261108` zulassen (Stand: nur der Code-48-Branch `codex/continuum-code48-github-owner-20261107`; er muss ersetzt werden, sonst bricht das Skript ab).
   - Prüfen, dass die Environment-Secrets `CONTINUUM_LOCAL_SIGNING_P12_BASE64` und `CONTINUUM_LOCAL_SIGNING_PASSWORD` die **Originalsignatur** enthalten (Werte nicht weitergeben). Ich kann ihre Existenz nicht prüfen.
2. **Explizite Freigabe für den Quell-SHA** (Eingabe `owner_approved_sha` beim Start des Signier-Workflows, plus die Reviewer-Genehmigung des Environments). Der Release-Freeze bleibt bis dahin bestehen.
3. **Installierte Version bestätigen** (zuletzt berichtet 0.3.28 / Code 29; Code 48 war die letzte Veröffentlichung). Code 50 braucht einen höheren Code bei gleicher Signatur.

## 5. Ablauf nach der Besitzerfreigabe

1. QA starten (Actions → „Continuum Code50 — GitHub source QA and unsigned APK“ → Branch `chatgpt/code50-qa-staging-20261108`, `expected_sha` = aktueller HEAD). Dauer ca. 35–60 Minuten.
2. Nur wenn **alle** Jobs grün sind: Signier-Workflow starten mit `source_sha`, `qa_run_id` (Nummer des QA-Laufs) und `owner_approved_sha` (gleicher SHA). Der Besitzer genehmigt im Environment.
3. Der Artefakt-Aufbewahrungszeitraum beträgt 14 Tage; danach muss die QA wiederholt werden.
4. Jeder weitere Commit auf dem Branch ändert den SHA und macht QA und Freigabe ungültig. Ergebnisse (Run-IDs, SHA-256, Link) gehören deshalb in PR-Kommentare, nicht in einen neuen Commit.

## 6. Update auf Geräten mit Code 48

Der eingebaute Update-Knopf des installierten Code 48 kennt nur den Render-Kanal. Code 48 aktualisiert daher **manuell in-place** über die veröffentlichte GitHub-APK (Download, Installationsfreigabe bestätigen; keine Deinstallation, gleiche Signatur, höherer Code). Ab installiertem Code 50 prüft der Update-Knopf zusätzlich den GitHub-Kanal. Eine fremd signierte APK wird vom System als Update abgelehnt.

## 7. Weiterhin offen / nicht ausgeführt

- QA auf dem exakten Kandidaten (`NOT_RUN`), Signierung (`NOT_RUN`), echter Download-Link (existiert nicht).
- Das Signierskript wurde nur statisch und im Preflight (Fake-`gh`) getestet. Zipalign/apksigner/Release-Schritte sind **bis zum ersten echten Lauf unverifiziert**.
- JDK-21-Lifecycle-Tests (2 Fälle): laufen nur im gesonderten Job. Fehlt ihr Ergebnis, steht im Release „nicht ausgeführt“.
- Echte Fold7-Prüfung: `REAL_DEVICE_NOT_RUN`.
- Hygiene-Hinweis: Aktionen im Workflow sind per Tag (`@v4`), nicht per Commit-SHA gepinnt.
