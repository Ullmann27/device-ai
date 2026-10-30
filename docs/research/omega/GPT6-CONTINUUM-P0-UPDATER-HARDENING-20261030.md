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
