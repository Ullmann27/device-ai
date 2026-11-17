# Continuum 0.3.18 – GPT-6 Übernahme, Kategorie-Routing und Release-Prüfung (23.10.2026)

**Status: REVIEW CANDIDATE, NOT BUILT, NOT SIGNED, NOT DEVICE_VERIFIED.**
Die tatsächlich installierte Besitzer-App bleibt unverändert. Kein Merge nach main. Kein Signierschlüssel im Repository.

## Herkunft
- Haupt-Übernahme: Opus PR #99, Commit `b35fed4179964ce376d4a8236957cbc54f7025c7` mit vollständiger Übergabe `docs/CONTINUUM-OPUS-TO-GPT6-HANDOFF-20261023.md`.
- Eigener Arbeitszweig: `gpt6/continuum-0.3.18-release-preflight-20261023`, isoliert vom Opus-, main- und Signierzweig.
- Bereits signierte, dokumentierte Besitzer-App: `astra/render-mentor-apk-builder-20261019` @ `285639ba94d75cc427cb17b7421f12f48296ec1f`, v0.3.17 / code18.
- Der Opus-Zweig enthält mehrere ältere UI-Blobs als der signierte v0.3.17-Stand. Die neueren Hologramm-/Dashboard-/Fold-Dateien der signierten Quelle wurden ausdrücklich erhalten (keine UI-Herabstufung).
- Diese Übernahme beinhaltet auch die nachträglichen CCC1-/Issue-76-Änderungen, die in 0.3.17 noch fehlen; nur überprüfbare Forschungskerne, nicht allgemeiner KI-Chat.

## Navigation und Buttons
- `AgentActivity.navigateWorkArea`: Start → Dashboard; Chat → Eingabe; Lernen → Lernzentrum; Entwickeln → Entwicklungszentrum; Mehr → Kategorienauswahl.
- Fold-Rail: Start, Chat, Lernen, Entwickeln, Analysieren, Speicher, System, Mehr mit konkreten Click-Callbacks.
- Start-Kacheln: Quellen, Diagnosen, Profil, lokale Modelle, Internetstatus, Denken/Chat, Lernen, Analyse, Entwicklung, Gedächtnis, System und Messwerte führen zu den jeweils zugeordneten Besitzer-Bereichen. Das Hologramm ist dekorativ, kein Reparaturknopf.
- Neu: `CCC1 · REPARATUR-AGENT (TERMUX)` unmittelbar im Entwicklungszentrum; nutzt dieselbe bestehende geprüfte Builder-Activity, keinen zweiten Worker.
- Paket `at.heinz.deviceai.mentorpaired` öffnet Builder intern; Manifest erlaubt die Activity nur unter `mobileBuilderEnabled`. Der Termux-Checkout und die tatsächliche Berechtigung müssen auf dem Gerät geprüft werden.
- Neue statische Kontraktprüfungen: `scripts/continuum-ui-routing.test.mjs` (5 Bereiche); Release-Sicherheit `scripts/continuum-owner-v19-release.test.mjs` (4 Bereiche). Die neueren signierten Haupt-App-/Cockpit-Tests wurden erhalten; Versionsannahmen für den neuen Kandidaten aktualisiert. Neue Tests auf diesem Review-SHA bislang **nicht mit Node ausgeführt**.

## Neue Besitzer-App – genaues Releaseprofil
- `app/build.gradle.kts`: **nur** `deviceAiOwnerSignedUpdate` → `versionCode 19`, `versionName 0.3.18`; Paket über `deviceAiMentorPreview` weiterhin `at.heinz.deviceai.mentorpaired`, Signatur aus bestehendem persistenten Keystore.
- `scripts/render-mentor-owner-update-v19.sh`: aus dem funktionierenden v18-Prüfweg abgeleitet, festes signiertes Quellprofil und 9 Git-Blob-Attestierungen, überprüfter Commit erforderlich, alter Schlüssel zwingend.
- Rollback-Vorbedingung: **tatsächliche** signierte 0.3.17-APK von der bisherigen Release-URL laden, mit SHA-256, APK-Paket, code18 und dem selben Zertifikat überprüfen und sichern; bei jedem Problem ABBRUCH, keine neue Veröffentlichung.
- Zertifikat SHA256: `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.
- Das neue Skript ist noch nicht durch den autorisierten Render-Build gelaufen. Das bestehende `scripts/render-mentor-owner-update-v18.sh` und die veröffentlichte v0.3.17 bleiben unverändert.
- `CONTINUUM_RELEASE_SHA` muss auf den **späteren geprüften exakten signierten Integration-SHA** zeigen; der Bau ist gegen die vorhandene signierte Branch-Identität gefesselt. Keine direkte Ausführung auf diesem GPT-6-Branch ohne kontrollierte Integration.

## Statische Vorprüfung (am GitHub-Quellstand durchgeführt)
1. Neuer CCC1-Button ruft `openMobileBuilder()` auf.
2. Fünf Kategorien haben konkrete Handler.
3. Weitere Startseiten-Ziele haben konkrete Methoden.
4. Besitzerpaket öffnet den eigenen Builder.
5. Manifest aktiviert die Builder-Activity nur über die erlaubten Varianten.
6. Owner-Version ist 19 / 0.3.18.
7. 0.3.17-Backup ist im v19-Prüfskript vorgeschrieben.
8. Neun kritische Blob-Hashes sind fixiert.
9. Bestehender Signatur-Fingerprint ist fest codiert und validiert.
10. Bestehende Owner-UI-Prüfungen sind für v19 angepasst.
11. Historische v18-Prüfungen bleiben als Regression erhalten.

Diese Prüfungen inspizieren Quelltext. **Sie beweisen weder eine erfolgreiche Ausführung von Node/Kotlin/Gradle noch funktionsfähige Touch-Ereignisse auf Fold7.**

## Noch zwingend vor Build/Freigabe
1. Prüfen, dass kein anderer Agent dieselben Dateien in einem aktiven Worktree ändert. `git fetch`, `git status --short`, Basis und Ziel-SHA feststellen.
2. Exakten signierten UI-Stand und alle Opus-Änderungen in einem isolierten, konfliktgeprüften Integrationstree vereinen. Den bestehenden Signierzweig nicht blind überschreiben. Bei gemeinsamer Änderung, etwa `app/build.gradle.kts`, neue Version 19 **und** alte UI-/Signaturkonfiguration erhalten.
3. Auf genau dem resultierenden SHA alle Node-Tests inkl. `scripts/*.test.mjs`, Backend-Suite, `scripts/android-kotlin-unit-check.sh`, Gradle `:app:testDebugUnitTest :app:lintDebug`, Robolectric-/UI-Tests und Release-Gate ausführen. Testfehler reparieren und **wiederholen**.
4. Prüfen, dass die signierte 0.3.17 samt Fingerprint und SHA-256 unverändert abrufbar und als Rollback gespeichert ist. Bei fehlender Sicherung keine Auslieferung.
5. Mit ausdrücklicher Besitzerfreigabe den **bestehenden** vertrauenswürdigen Render-Signierdienst und dessen bestätigten Workspace verwenden. Nur gepinnten finalen SHA bauen, Schlüssel nicht anzeigen, prüfen: apksigner, aapt, SHA-256, Buildlogs.
6. Danach den Besitzer beim In-place-Update auf dem Fold7 führen: kein Deinstallieren. Prüfen: gespeicherte Daten, Start/Chat/Lernen/Entwicklung/Mehr, alle Kategoriebuttons, Fold/IME, CCC1-Kette + Termux-Checkout, Issue #76, Hologramm, Gedächtnis/Modelle und Fehleranzeigen. Ausgabe nur nach Erfolg `DEVICE_VERIFIED` nennen.

**Abschließende Entscheidung:** Noch keine APK; kein bestätigter Gradle- oder Fold7-Gesamttest auf diesem Review-Branch. Darum kein neues signiertes Artefakt behaupten. Der Stand ist ein überprüfbarer Vorschlag zur nächsten Freigabe.
