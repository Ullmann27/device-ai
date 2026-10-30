# GPT-6 · Continuum Lokal P0 Update-Härtung · 30.10.2026

**Status:** WIP / UNVERIFIED. Kein Android-Build, keine Installation, keine Owner-Freigabe.
**Parent:** `feature/continuum-p0-inapp-owner-updater-20261029` / PR #236, Start-SHA `6bc54545adcef83e801003f5559d377f3a20d9ba`.
**Branch:** `chatgpt/continuum-p0-updater-hardening-20261030` (stacked Draft-PR gegen Parent; `main` bleibt unverändert).
**Experimente:** EXP-00 (on-device Build), EXP-08 (gesteuerter Update-/Installationskanal), EXP-09 (Build- und Signier-Rezept).
**Quellen vor Änderung gelesen:** AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, docs/CONTINUUM-EXPERIMENTE.json, Issue #41, PR #236 und konkrete Kotlin-/JUnit-Quellen.

## Nachgewiesene Ausgangslage
- Original-Update-Bildschirm und Update-Pipeline sind als **Draft-Quelle**, nicht als verifizierte Besitzer-APK vorhanden.
- GitHub Actions Run 40882827732 (`pull_request`, 29.10.2026) endete `failure` nach wenigen Sekunden; Job meldet **0 ausführbare Steps**. Kein Nachweis eines Kotlin-/SDK-Compilerfehlers oder einer gebauten APK; Artefaktliste leer.
- Die Termux-Buildroute `scripts/continuum-fold7-standalone-apk.sh build 29` setzt auf dem Fold7 die vorhandenen Android-/Gradle-Toolchains und **die originale P12** voraus. Der Schlüssel darf nie in GitHub Actions, Chat oder Repository gelangen.
- Bekannte Code-Lücken: voreiliger `Check.Current` bei altem Manifest vor vollständiger Release-Prüfung; wiederverwendete feste `PendingIntent`-Kennung 7155 für Installations-Sessions; asynchroner Download-Fortschritt erfasst veränderliche Zahl.

## Scope dieses Branches
1. Release-Metadaten **vor** `Current` vollständig validieren; ungültige oder nicht genehmigte Metadaten nicht als genehmigten aktuellen Stand darstellen.
2. Sitzungsgebundene `PendingIntent`-Kennung und reproduzierbar richtige Fortschrittsmeldungen.
3. JUnit-Negativtests. Keine Modifikation von Paket-ID, Original-Signer, Manifest-Host, OwnerGate, ToolGate, CCC1, CognitiveGenome oder M6/M8-Forschungszweigen.

## Abnahme und Grenzen
- Für eine Quellabnahme: `gradle --no-daemon -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=29 :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin` auf dem gestapelten Branch mit funktionierender SDK-/Maven-Umgebung.
- Für echten Update-Erfolg: Originalsignatur und Paketkennung prüfen, Owner bestätigt Installation, **zweites** In-App-Update, danach Versions-/Signatur-/Datenpersistenz-Abgleich auf Fold7.
- Android-/CI-/Gerätetests sind in dieser Connector-Sitzung **NOT_RUN**, solange kein tatsächlicher Lauf nachgewiesen ist. Keine Behauptung `REAL_APK_BUILT`.
- Rückfallplan: diesen isolierten Draft-PR nicht mergen. PR #236 sowie `main` bleiben unverändert.

## Fortsetzung
Nach Code-Commit: SHA-Readback für jede Datei, CI-Anlauf falls ausführbar; unabhängig davon Buildlog und echte Root-Cause beschaffen. Danach erst EWM-v2-Sealed-Holdout (M6) und M8 Modellbenchmark getrennt bearbeiten.


## Umsetzung dieser Sitzung (auf GitHub gepusht, unabhängig von Android-Build)
- `092ced91ff7be950ed33262dd86b83378cc0fd23`: Autorisierungs-, URL- und Metadatenprüfung findet vor dem Resultat `OLD_OR_EQUAL_VERSION` statt.
- `4da5c6567a4cec595fd393c7324251dd24f733cf`: `Check.Current` nur nach erfolgreicher Policy-Prüfung; `PackageInstaller`-Session-ID statt fester PendingIntent-Kennung; Fortschritts-Snapshot vor UI-Dispatch.
- `74250c6d9141c1f17248b6bcee36810240ed17d7`: JSON-Version und Bytegröße als exakte Ganzzahlen; VersionName muss zu VersionCode passen.
- `e375d784a425289f82fb4d89757e2d6e33c42eb2`, `94440238cd5a4cab41b07d87be71345c44252a7f`: insgesamt 3 neue JUnit-Testmethoden (altes Manifest/ungenehmigtes Manifest/falscher Host/Signer, Dezimalwerte, Versionskonsistenz).
- `d6f6cc915840067fb9928abea19907adb5db3783`: bestehende direkte Policy-Verträge für bereits alte Versionsnummern 27/28 nach statischem Review erhalten; ein neu angebotenes Release unter Code 29 wird dennoch zurückgewiesen.

**Unabhängige Build-/JUnit-Ausführung:** `NOT_RUN` in dieser Sitzung. Die GitHub-Connector-Schnittstelle kann die Quell-Dateien ändern, aber keine Android-Gradle-Toolchain auf dem Fold7 ausführen. Änderungen deshalb weiter `WIP/UNVERIFIED`, kein Besitzer-Release.

## Nächste unabhängige Prüfung
1. Auf demselben Branch die 5 Kotlin-Dateien per `git show` und PR-Diff gegen den Parent vergleichen; Compile-, Test- und Lint-Ausführung auf einem realen Android-SDK-Host oder vorhandenen Fold7-Termux durchführen.
2. Wenn Test- und Compile-Exitcodes 0: Test gegen schädliche/ungenehmigte `latest.json`, tatsächlich übertragene APK und Android-Installer-Status. Bestehende manuelle Test- und Datenbestände vor jedem Update sichern.
3. Bereits vorhandene `scripts/continuum-fold7-standalone-apk.sh build 29` nicht durch eine PC-Pflicht ersetzen. Ein originalsignierter Download oder ein Update ist **nicht** durch diesen PR entstanden.
4. Für M6 separat den fremd formulierten Holdout vor der Auswertung versiegeln; für M8 erst unabhängigen echten Modellbenchmark, dann isolierte Android-Ausführung. Keine unzulässigen Änderungen an der lokalen Modell- oder Repair-Autorität.

## Nachtrag: Tatsächliche alternative Render-Buildstrecke (29.10.2026, unabhängig geprüft)
- Service `continuum-p0-updater-qa-20261029` (`srv-dbhk0r1srm7s738208n0`) auf **separatem Basisbranch von PR #236**, Deployment `dep-dbhklqs9v7es73b725r0`, Commit `6bc54545adcef83e801003f5559d377f3a20d9ba`, Render-Status `live`.
- Buildlogs: `BUILD SUCCESSFUL in 3m 45s` (QA-Tests/Kompilierung), `BUILD SUCCESSFUL in 1m 6s` (separate Preview-APK), `BUILD SUCCESSFUL in 1m 40s` (unsigniertes Owner-Release).
- Separates Debug-QA-Artefakt mit SHA-256 `8fa6a007fba53ca76f7650e40b67e49a8fe496526df9870b321d8a59dec78370`: **andere Paketidentität**, kein Continuum-Update.
- Unsigniertes `Continuum Lokal 0.3.28 / versionCode 29` mit SHA-256 `af444010a254219e9fa430300fb63db1fe15acc2a0315d19511c6d023cb5523a`: **nicht installierbar** ohne originale Fold7-P12.
- **Wichtig:** Diese positiven Ergebnisse gelten nur für `6bc54545...`, **nicht** für unseren neuen PR #257. Der aktuelle PR benötigt eigene Kotlin-JUnit-/Lint-/APK-Buildbelege. GitHub Actions war hier also kein zwingender Blocker; Render konnte die Basisroute bereits bauen.
- Kein neues Render-Deployment ausgelöst und kein altes veröffentlichtes Update-Manifest freigegeben.
