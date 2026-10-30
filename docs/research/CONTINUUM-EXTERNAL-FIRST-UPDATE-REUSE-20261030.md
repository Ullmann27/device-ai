# Continuum – EXTERNAL-FIRST / REUSE-BEFORE-BUILD
Datum 2026-10-30 · Status: recherchiert; tatsächliche Integration/Android-Test getrennt ausweisen

## Bindende Owner-Auftragspolitik
Vor jeder Eigenentwicklung funktional überlappender Module zuerst aktiv verfügbare etablierte Lösungen prüfen. Die Besitzererlaubnis umfasst gezielte Open-Source-Übernahmen nach Prüfung, **keinen** Blankoscheck für sicherheitskritische externe Binärdateien, heimliche Cloud-Datenübertragung oder neue Berechtigungen ohne Kontrolle.

**Qualitätsgates:** (1) reales Funktionsdefizit/Benchmark und Ist-Codevergleich, (2) vorgelagerte Quellidentität, aktive Wartung, Build-/Abhängigkeitsinventar, (3) Lizenz und NOTICE/Attribution, (4) echtes Android/Termux/ARM64-Profil, (5) Import/Adapter ohne zweiten OwnerGate/ToolGate/CCC1-Kern, (6) Lizenz- und Sicherheitsprüfung, (7) unabhängige Negativ-/Holdouttests, (8) belegter Effizienzgewinn gegen Baseline, (9) saubere Deaktivierung/Rollback, (10) Branch/Draft-PR vor Integration und niemals Eigenmacht nach `main`. Ohne Tests: `AUDIT_REQUIRED`, nicht `INTEGRATED`.

## Bestand (verifiziert im privaten Repository auf PR #260)
- `ContinuumOwnerUpdater.kt` bietet bereits HTTPS-Manifest, freigegebene APK-URL, Byte-Limit, SHA-256, Signer-/Package-/Versionprüfung und PackageInstaller. Ein zweiter generischer Updater bringt keine offensichtliche Verbesserung.
- `scripts/continuum-owner-release-stage.mjs`: lokale signierte APK im privaten Staging nach Quittungsprüfung, aber noch kein automatischer Publisher. Original-P12 darf nicht in GitHub/Render gelangen.
- `scripts/continuum-fold7-standalone-apk.sh`: Einmal-Bootstrap und wiederholte Builds in Termux mit Originalschlüssel und Passwort. Kein verifizierter unbeaufsichtigter Signierer.
- `app/build.gradle.kts`: compileSdk=36, targetSdk=36, minSdk=26. `ContinuumOwnerUpdateReceiver.kt` implementiert bereits Android-Installer-`STATUS_PENDING_USER_ACTION` als Rückfallpfad.

## Upstream-Audit 2026-10-30

| Kandidat | Echte Fähigkeit | Nachweis/Lizenz | Ist-Fit | Entscheidung |
|---|---|---|---|---|
| Android `PackageInstaller.SessionParams.setRequireUserAction` | Bei Android 12+ selbstupdate unter OS-Bedingungen eventuell ohne **zweiten** Android-Dialog | Android SDK API31; dokumentiert in `developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams`. `UPDATE_PACKAGES_WITHOUT_USER_ACTION` Normal-Permission | **direkt** nutzbar, kein Fremdcode oder neuer Build-/Netzwerk-Stack | **ZIEL: zuerst kleiner abgesicherter Adapter (opt-in per bewusstem Update-Klick), OS kann Dialog erzwingen** |
| [rclone](https://github.com/rclone/rclone) | fertiges S3/WebDAV/SFTP-/Objekt-Publishing, Hashprüfung, Wiederaufnahme, Sync/Copy | [MIT-Lizenz](https://github.com/rclone/rclone/blob/master/COPYING), [Dokumentation](https://rclone.org/docs/), Termux-Paket unter `termux/termux-packages/packages/rclone` nachgewiesen | Potenziell **sehr hoher** Entwicklungsersparniswert im separaten signierten Release-Publisher; bestehender fest gepinnter Render Static-Site-Host ist aber **nicht** nachgewiesen als rclone-Schreibziel | **ADAPTER_CANDIDATE**, erst nach Ziel-Backend- und Credential-/Host-Vertrag. Weder binary noch Cloud-Zugang heimlich hinzufügen |
| [jenly1314/AppUpdater](https://github.com/jenly1314/AppUpdater) | Update-Download mit Fortschritt, Retry, optional Benachrichtigungen; getrenntes app-updater Modul | MIT; Release v2.2.0 vom 04.08.2026 laut upstream README | Würde unsere SHA-256-/Originalsignatur-/Sitzungskontrolle größtenteils nur duplizieren; upstream primär MD5/Versioncode-Cache; zusätzliche Download-Abhängigkeit | **NICHT_IMPORTIEREN**, solange keine unabhängige demonstrierte Beschleunigung oder bessere Tests |
| [javiersantos/AppUpdater](https://github.com/javiersantos/AppUpdater) | Update-Check für Play/GitHub/eigenen JSON-Server | Apache-2.0 laut Verzeichnis, letztes dort genanntes Release 2.7 ca. 8 Jahre alt; Upstream-README | Mehrfach-Marketplace-Navigation statt strikt gepinntem Manifest, JitPack-Abhängigkeit; kein Release-Signer | **NICHT_IMPORTIEREN** |
| [Obtainium](https://github.com/ImranR98/Obtainium) | Standalone-Update-App mit vielen Quellen | GPL-3.0; F-Droid- und GitHub-Upstream | Zweite App statt eingebettetem Continuum-Knopf, Lizenzkomplexität, eigener Quellen-/Installer-Stack | **EXTERNES REFERENZPROJEKT**, keine Codeübernahme ohne gesonderte GPL-Entscheidung |
| [APKUpdater](https://github.com/rumboalla/apkupdater) | Standalone-Android-Updates über alternative Stores | GPL-3.0 upstream | Gleicher Konflikt mit eigenem Originalzertifikat-/Manifestvertrag | **EXTERNES REFERENZPROJEKT**, keine Übernahme |
| [AOSP apksig / apksigner](https://android.googlesource.com/platform/tools/apksig) | echte APK v2/v3-Signaturprüfung und -Erzeugung | Offizielles Android SDK, vollständiger bereits installierter `apksigner`-Pfad | Schon im Termux-Builder vorhanden | **REUSE_EXISTING**, nicht erneut implementieren |

## Rechtliche und technische Grenzen
GPL-3.0 nicht kommentarlos in die Haupt-App kopieren. Selbst MIT/Apache-2.0 verlangt korrekte Notice/Lizenzbehandlung. `rclone`-Integration nur als externe, offiziell installierte und versioniert geprüfte Toolchain-Komponente, bis eine Runtime-/Transportmessung besteht. Vermeide eine neue Speicher-/Agentenarchitektur.

Android-Quelle: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#L (Verweis auf `setRequireUserAction`; präzise URL https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams ).
Offizielle Bedingungen: App zielt auf mindestens API 35 unter Android 17, Update einer **selbstinstallierten App** bzw. Eigentümer-/Installer-Beziehung, erklärte Normal-Permission `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `USER_ACTION_NOT_REQUIRED`; Android kann dennoch `STATUS_PENDING_USER_ACTION` zurückgeben. Ein Nutzer-Tap innerhalb Continuum bleibt explizit verpflichtend. Kein automatischer Hintergrund-Installationsversuch.

## Nächste Schritte
1. Nur in isoliertem Draft-PR die offizielle PackageInstaller-Funktion als kleinste Wiederverwendung integrieren; bestehenden Pfad für nötige Android-Dialoge erhalten.
2. Statische Quellprüfung, vollständige echte Gradle-/JUnit-Abnahme und Fold7-Realtest trennen. Ohne Runtime-Belege kein `END_TO_END_VERIFIED`.
3. Publishing-Backend später gegen Render Static Site und rclone-Remotes tatsächlich prüfen; erst dann rclone als optionalen lokalen Publisher einbauen.
4. Für jedes weitere Kandidatenpaket dieselben Gates erneut durchlaufen, nicht die Entscheidung pauschal übertragen.
