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
