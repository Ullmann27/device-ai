# Continuum Lokal – Einmaliger Bootstrap, danach One-Tap-Updates (30.10.2026)

Status: **WIP / UNVERIFIED**, isolierter Branch auf PR #257, Parent SHA `f665f571bad2bd844b74af01fc213753d96ef042`. `main` bleibt unverändert.

## Nach Projekt- und Quellenabgleich bestätigte Besitzeranforderung
- Vorhandene `at.heinz.deviceai.continuumlocal` auf dem Fold7 behalten und **einmal** manuell mit ursprünglicher P12 und Passwort über die vorhandene Fold7-Termux-Toolchain aktualisieren.
- **Diese Bootstrap-APK muss den Update-Button enthalten.** Keine zweite parallel installierte Test-App, kein Neustart der Signieridentität, keine Deinstallation/Datenlöschung.
- **Danach nur noch in der App "Updates laden" antippen**, ohne manuelle Git-Befehle, Termux-Builds oder Passworteingaben. Der offizielle Android-Installationsdialog ist unabhängig davon notwendig.
- Das Originalzertifikat bleibt identisch. Jedes künftige APK benötigt weiterhin eine Signatur mit **demselben** Schlüssel. Der Update-Button kann signierte Releases nicht selbst erzeugen. Deshalb ist neben dem Client ein dauerhaft automatisierter, besonders geschützter Release-Produzent/Publisher erforderlich.
- Keine kostenpflichtigen externen KI-APIs, GitHub-Actions- oder Render-Zwang; bestehender lokaler Android-Builder ist primär, Render ein nachgewiesener optionaler Ausweichweg.

## Ist-Belege
- Nutzerbericht Fold7: Continuum Lokal `0.3.25` / versionCode 26 wurde originalsigniert gebaut und installiert, P12-Fingerprint im Repository-Builder fest hinterlegt.
- Der bestehende PR #236 besitzt `ContinuumOwnerUpdateActivity`, `ContinuumOwnerUpdater`, einen manifestgebundenen HTTPS-Verteilungskanal, SHA-256/PackageInstaller-Prüfungen; der Folge-PR #257 härtet diese Prüfungen.
- Render hat am 29.10.2026 eine **andere** Basis-SHA `6bc54545...` erfolgreich getestet und eine **unsignierte** `0.3.28`-APK gebaut; das ist keine installationstaugliche neue Owner-APK.
- Der Release-Kanal `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json` führt bislang `available:false`; kein signiertes Release wurde publiziert.

## Ziel-Architektur
**Einmal** überprüfter Originalsignatur-Bootstrap auf dem Fold7 → App-Menü / Systemstatus / ein einziger Knopf "Updates prüfen und laden" → unveränderliches HTTPS-Manifest → Paket-/Zertifikat-/SHA-/VersionCode-Prüfung → `PackageInstaller` + Android-Systemfreigabe → Datenbestand unverändert.

**Spätere Releases:** Freigegebener Code/Tests → ein gesonderter, lokal autorisierter Owner-Signierworker auf dem Fold7 mit geschützter, einmalig bereitgestellter Signieridentität → signierte APK + Hash + Release-Manifest → verifizierte HTTPS-Veröffentlichung. Die Planung des dauerhaften Signierworkers darf keine unverschlüsselte Kennwortdatei oder unkontrolliert signierbaren Modelltext erlauben; bis zum unabhängigen Geräte-/Schlüsselsicherheitsaudit **keine** automatische Signierung aktivieren.

## Diese Entwicklungsrunde
1. Nur eine sichtbare Hauptaktion für Prüfung und Download; kein zweiter In-App-Bestätigungsklick, Android-Installationszustimmung bleibt.
2. Reproduzierbare UI-/Quelltext-Negativtests, die garantieren, dass der Knopf in der Produkt-APK vorhanden und nicht nur im Preview verbaut ist.
3. Veröffentlichungskanal und automatischen Signierer getrennt und ehrlich ausweisen, bis alle Abnahmegates tatsächlich bestanden wurden.

## Zulässige Abnahme
- Git-HEAD und `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json`, Issue #41 gegenlesen; EXP-00/08/09.
- Android: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug` auf Originalquelle, dann `lintRelease`, `assembleRelease` mit Original P12 ausschließlich Termux Fold7; SHA, Paket `at.heinz.deviceai.continuumlocal`, originalen Zertifikatsfingerprint und inkrementellen versionCode prüfen.
- Auf dem Fold7 einmal manueller Installations- und Button-Test; danach eine **zweite**, neu freigegebene versionCode-erhöhte signierte APK per Manifest einspielen, ohne Termux/Downloads für den Gerätebenutzer.
- Ohne zweiten Nachweis: `END_TO_END_VERIFIED=false`. Keine Secret-Exporte, kein Auto-Merge, kein APK-Release ohne Besitzerfreigabe.

## Rückfall
Draft-PR nicht mergen; die zuvor installierte App nicht deinstallieren; Original P12 offline sichern. Keine Veränderungen an OwnerGate, ToolGate, CCC1, CognitiveGenome, EWM2, Gedächtnis oder Modellgewichten.

## Ausführliche Übergabe: real eingecheckte Implementierung

- `b9d3a3e6cb9180402357213281ed48b2f7bb5bb8`: Aktivität hat nur noch **eine** Update-Aktion, die nach erfolgreicher Policy-Prüfung den verifizierten Download unmittelbar auslöst. Android PackageInstaller verlangt weiterhin den systemeigenen Nutzerentscheid.
- `95336bddc75aacce915212db4d8b83458ac4282d`: Systemstatus-Menü in der Haupt-App zeigt ausdrücklich `UPDATES PRÜFEN UND LADEN`.
- `ecbf53965b49bf20a55e1b17312c731fe603ddc9` und `03829f3285d058bf573c1073a394186c6b9257dd`: Node-Quellvertrag: genau eine Schaltfläche, Manifest/Signatur/PackageInstaller/Activity im Produktionsmanifest sowie Systemstatus-Navigation.
- `b0ec92fe2e76e2d733aea73d41038e11200895ff`, `bd1945e5e969834d143b3a8c7fa85b80a3d4147d`: Original-Fold7-Build prüft die Update-/Release-Quellverträge **vor** dem nächsten Passwort-Prompt.
- `58802bbb1b47b570adb53021f4ef54483b62195d`, `806ca4bbb6bb37caadd7f50f2601770473f9b429`: zwei nachweislich veraltete statische Testannahmen an die reale Gradle-Paketvariante mit `continuumLocalRenderUnsigned` angepasst, ohne alternative App-ID zu akzeptieren.
- `21cb908e38107c94fc321b8bcf0863a20c9fdcf1`: Quell-Datei `scripts/continuum-owner-release-stage.mjs` validiert eine **bereits** originalsignierte APK, lokale SHA-gebundene Build-Quittung und das bestehende Release-Manifest; kopiert ausschließlich nach privatem Fold7-Staging, keine Verteilung.
- `56053a5cd00ef174c291613da3070d1ea695efb2`: Negativtests für fehlende/ungültige Signier-Staging-Eingaben.

**Ausgeführte Connector-Prüfung:** 20/20 Codevertrags-Bedingungen anhand tatsächlicher GitHub-Datei-Readbacks bestanden (reine Quellprüfung, **kein** Kotlin- oder Node-Lauf). Die zwei korrigierten historischen Testdateien und der neue Staging-Code müssen zusätzlich tatsächlich mit Node getestet werden.

## Veröffentlichung und Signierung – ausdrücklich getrennt

**Client:** Einmals installierte Bootstrap-APK braucht dann weder Git, noch Termux, noch Download-Dateiverwaltung zum **Herunterladen und Installationsanstoßen** späterer *bereits freigegebener* Updates.

**Producer:** Jede neue APK muss weiterhin mit dem ursprünglichen P12 signiert und unter der gepinnten HTTPS-Adresse mit gültigem `latest.json` und dem tatsächlichen hashidentifizierten APK-Binary veröffentlicht werden. Kein Software-Button kann die kryptographische Signierpflicht aushebeln.

Das vorhandene Render-Static-Site `continuum-owner-v23-release-preflight` (Service-ID `srv-dbf1tr2vcj2c73ehpfog`) hat nach live Connector-Readback `autoDeploy=no`, Quellbranch `release/continuum-owner-v23-candidate-20261025`, PublishPath `owner-v23-release-preflight`. Aktuell gibt es **keinen** geprüften originalsignierten Continuum-Lokal-Release. Die Bereitstellung des Bundles braucht noch den festen, überprüften Upload-/Deploy-Schritt; dieser darf nur nach gültiger Signaturfreigabe erfolgen.

**Unbeaufsichtigtes späteres Signieren ohne Passwortprompt:** noch nicht gelöst. Der Originalsignierer ist nach dem aktuell geprüften Buildskript lokal und interaktiv. Ein dauerhaft automatisch verwendbarer Signierer muss die P12 im Fold7 belassen, die Entsperrberechtigung geschützt halten, autorisierte Commits/Bauketten strikt binden, Modelltext als nicht vertrauenswürdig behandeln, begrenzte Signieraufträge verwenden und unabhängig auf dem Fold7 getestet werden. Eine Klartext-Passwortdatei oder Verlagerung des P12 auf GitHub/Render wurde **nicht** eingeführt. Bis ein solcher Worker existiert, ist die Aussage "niemals wieder manuell signieren" **nicht nachgewiesen**.

## Ausstehende entscheidende Gates

1. `node --test scripts/continuum-one-tap-updater-contract.test.mjs scripts/continuum-owner-release-stage.test.mjs scripts/continuum-fold7-standalone-apk.test.mjs scripts/continuum-one-app-contract.test.mjs` im realen Repository.
2. Gradle-JUnit, Android-Lint, Kotlin/Java-Release-Kompilierung des aktuellen neuen Branch-HEAD; kein Testgrün der alten Render-Basis-SHA wiederverwenden.
3. Einmaliger Fold7-Termux-Bootstrap der v29 oder höheren Version mit identischem Original-P12-Passwort; originale installierte v26 nicht deinstallieren. Start im Systemstatus, neue Aktion aufrufen.
4. **Neues** signiertes Release v30+ bauen und die beiden Dateien `continuum-local/latest.json` und `continuum-local/Continuum-Lokal-<SHA256>.apk` auf dem vorhandenen HTTPS-Host **tatsächlich** publizieren.
5. Ein Tap in der App, APK-/Paket-/Versions-/Fingerprint-Verifikation, Android-Installer-Dialog bestätigen, Relaunch und unabhängige Prüfung der Version sowie unveränderter Daten/Modelle.
6. Geschützten lokalen automatisierten Signierer und Publisher getrennt abnehmen, bevor wir Zukunfts-Updates als **passwortlos** bezeichnen.

**Status heute:** `SOURCE_PATCHED` / `STATIC_SOURCE_READBACK_PASS`; `NODE=NOT_RUN`; `ANDROID_BUILD=NOT_RUN`; `ORIGINAL_SIGNED_APK=NOT_RUN`; `FOLD7=NOT_RUN`; `SECOND_UPDATE=NOT_RUN`. Keine Änderung an `main`, keine Eigentümerdaten übertragen, kein Cloud-P12.
