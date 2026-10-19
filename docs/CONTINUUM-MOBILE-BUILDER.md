# On-device Android Builder – überprüfte Strategie und erster Vertical Slice
Stand 2026-10-19 · Experiment **EXP-00**, Zielstand noch **nicht** am Samsung abgenommen.

## Recherchierte und lizenzierte Ausgangspunkte (nicht blind kopieren)
1. **Termux Studio** – https://github.com/poordevcode/termux-android-studio – Apache-2.0, ARM64-aapt2 und Gradle-Override `android.aapt2FromMavenOverride`. **Adoptieren als Konzept**, keine ungesicherten vorkompilierten Binaries und keine fremden Installer automatisch ausführen.
2. **Code on the Go** – https://github.com/appdevforall/CodeOnTheGo – GPL-3.0, weiterentwickelter AndroidIDE-Nachfolger. **Nur analysieren / getrenntes Werkzeug** bis Lizenz-, Quell- und Integrationsprüfung.
3. **AndroidIDE (archiviert)** – https://github.com/AndroidIDEOfficial/AndroidIDE – GPL-3.0; als historischer Anwendungsbeweis, nicht als ungewartete Produktionsabhängigkeit.
4. **Termux Android Build** – https://github.com/Xtratter/termux-android-build – GPL-3.0-or-later; JDK17, Gradle-Override und realer ARM64-Anwendungsbericht. **Nur Verfahren studieren**.
5. **Termux Android App Builder** – https://github.com/0-Hackibit/Termux-Android-App-Builder – minimaler aapt2/javac/dex/sign-Prototyp mit kleiner Prüfoberfläche. **Experimentiervorlage**, unabhängig implementieren.
6. **Android offizielle AAPT2-Dokumentation** – https://developer.android.com/tools/aapt2 – erklärt Resource-Compile und Link.
7. **Android APK-Signierung** – https://developer.android.com/tools/apksigner – die Signatur **nach** allen ZIP-/Alignment-Änderungen setzen, anschließend prüfen.
8. **Termux RUN_COMMAND Intent** – https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent – dokumentiert die permission-gesteuerte Android→Termux-Kommunikation; noch **nicht** integriert oder für unser Gerät abgenommen.
9. **Android PackageInstaller** – https://developer.android.com/reference/android/content/pm/PackageInstaller – offizieller autorisierter Installationsweg.

Keine Behauptung, dass ein externes Repo in allen Android-Versionen sauber funktioniert. Zu studierende Fremdprojekte nicht ungeprüft ins Hauptrepo importieren. Lizenzen und konkrete Versionsstände vor Codeübernahme nochmals kontrollieren.

## Warum diese Abkürzung sinnvoll ist
Die bestehende Android-App verwendet Kotlin, Gradle 8.11.1 und API 36. Statt einen kompletten Compiler selbst zu erfinden, nutzen wir vorhandene native Android-/Termux-Build-Werkzeuge über einen eigenen schmalen Adapter, der ein Quell-Commit und einen echten ausgeführten Build kontrolliert. Das spart das Neuschreiben von AAPT2, D8, Gradle und Signieralgorithmen. Der eigene KI-Programmagent bleibt davon unabhängig und kann später typed Patch-Kandidaten an die bestehende M4-Reparaturpipeline übergeben.

## Implementiert
`scripts/continuum-mobile-builder.mjs`:
- `doctor`: prüft auf Android/Termux ARM64, vorhandenes Git, JDK 17, Gradle 8.11.1, lauffähiges ARM64-AAPT2, `apksigner`, SDK 36 (`android.jar`). Fehlt etwas: `UNAVAILABLE`. Keine selbsttätigen Installationen/Downloads.
- `build`: nur aus sauberem existierenden Git-Checkout, nicht aus Shell-Text; feste Gradle-Aufträge `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug -PdeviceAiPreview=true`, eingeschränkte Workerzahl, 35-min-Limit.
- APK wird in externen Zustandsordner kopiert; die **kopierten Bytes** werden mit `apksigner verify --verbose --print-certs` geprüft und anschließend mit SHA-256 gehasht. Keine automatische APK-Installation, kein Git-Push und keine Änderung an produktiven Branches.
- `status`: letzten gespeicherten Prüfbericht anzeigen.
- Alle Berichte enthalten `productionReady:false`, `deviceInstallationVerified:false`, bis die reale unabhängige Geräteabnahme erfolgt. Die aktuellen Node-Tests simulieren Toolantworten.

## Termux-Testablauf (erst nach bewusster Einrichtung vertrauenswürdiger Tools)
Im vorhandenen vollständigen, sauberen Termux-Git-Checkout (SDK 36 im eigenen Termux-Zugriffsbereich):
```sh
node scripts/continuum-mobile-builder.mjs doctor
node scripts/continuum-mobile-builder.mjs build --project .
node scripts/continuum-mobile-builder.mjs status
```
Die Umgebung erwartet `ANDROID_HOME` oder `ANDROID_SDK_ROOT`. Bei Werkzeugen außerhalb des PATH sind `CONTINUUM_AAPT2_PATH` und `CONTINUUM_GRADLE_PATH` erlaubt. Die Scriptvorgabe verlangt **genau** Gradle 8.11.1, Java 17 und Android SDK 36, um unbemerkte Toolchain-Abweichungen zu vermeiden. Es ist kein Installationsskript für die Toolchain enthalten. Erst nach gesonderter Prüfung passende ARM64-Pakete installieren und Downloadquellen/Signaturen kontrollieren.

## Aktuell bestätigter Prüfstand
Eine frühere lokale Node-22-Vorimplementierung bestand **9/9 isolierte Tests**. Die jetzt **im GitHub-Branch eingecheckte Version** enthält eigene Regressionen in `scripts/continuum-mobile-builder.test.mjs`; deren reale Ausführung am eingecheckten Git-HEAD **steht noch aus**. Daher wird für diese Branchversion kein grüner Testlauf beansprucht. Die synthetischen Tests wären auch bei Erfolg kein echter ARM64-/Gradle-/Samsung-Build.

Abnahme auf einem vollständigen Checkout: `node --test scripts/continuum-mobile-builder.test.mjs` und danach `node scripts/continuum-mobile-builder.mjs doctor` auf dem Android-Smartphone.

## Wichtigste nächste Arbeit
1. Toolchain-Kompatibilität **am realen Samsung**, inklusive nativer ARM64-AAPT2-Binärdatei mit passender API-36-Unterstützung nachweisen.
2. Ein signiertes Hello-World-Miniprojekt **auf demselben Telefon** bauen und installieren; danach vollständiges Continuum-APK bauen.
3. Den bestehenden `ContinuumWorkerChatRouter` mit einem minimal berechtigten, explizit freigegebenen Android↔Termux-Jobsystem verbinden und Android-Berechtigungs-/Restart-Negativtests schreiben.
4. M4-Zweit-Worktree-Verifikation und Paket-/Signer-Identitätskontrollen integrieren. Erst danach autonomen Zyklus aktivieren.

Ein Git-Worktree ist keine Betriebssystem-Sandbox. Unbekannter aus dem Internet bezogener Code darf nicht ohne getrennte OS-Isolation in Termux gebaut werden.


## Android-UI-Bridge EXP-00 (19.10.2026)

Ein zusätzlicher *Preview-only* Launcher **Continuum Builder** wurde in PR #42 angeschlossen:

- `ContinuumMobileBuilderActivity.kt`: native Oberfläche mit Werkzeugprüfung, manuellem APK-Build (mit expliziter Bestätigung) und Statusanzeige. Der Launcher erscheint nur für `deviceAiPreview` bzw. `deviceAiMentorPreview` und wird im normalen Produktions-Build deaktiviert.
- `ContinuumMobileBuildPlan.kt`: exakt drei fixe Operationen `doctor`, `build --project /data/data/com.termux/files/home/device-ai`, `status`. Fester Node-Pfad `/data/data/com.termux/files/usr/bin/node`. Kein Chattext, keine beliebigen Shellargumente, keine automatische Installation.
- `AndroidManifest.xml`: optional vom Besitzer zu erteilende `com.termux.permission.RUN_COMMAND`. Explizite Zielkomponente `com.termux.app.RunCommandService`, offizieller Intent `com.termux.RUN_COMMAND`, Termux-Terminal zeigt die tatsächliche Ausgabe.
- Kotlin-Vertragstest und isoliert ausgeführter Kotlin-Smoke-Test (3 Operationspfade); zusätzliche Node-Quellverkabelungsregressionen (`mobile-builder-android-wiring.test.mjs`). Diese Prüfungen sind **kein** Android-Laufzeitnachweis.

**Verwendung erst nach echtem APK-Build:**
1. Vertrauenswürdige Termux-Installation, danach den Besitzer-Checkout unter `/data/data/com.termux/files/home/device-ai` bereitstellen; Node 20+, Git, JDK 17, Gradle 8.11.1, natives ARM64-AAPT2, SDK 36 und `apksigner` separat nachvollziehbar installieren.
2. In Termux `~/.termux/termux.properties` die Zeile `allow-external-apps=true` nach eigener Entscheidung setzen. Benutzerfreigabe `RUN_COMMAND` für Device AI Preview in Androids Berechtigungseinstellungen erlauben. Damit erhält Device AI die Möglichkeit, eigene Termux-Befehle zu starten; die App selbst beschränkt sich auf drei konstante Aufträge.
3. Neue Preview-APK mit aktualisiertem PR #42-Commit **tatsächlich** bauen und installieren. Auf dem Startbildschirm `Continuum Builder` öffnen und zunächst `Werkzeuge prüfen` ausführen. Termux muss die ausführliche `doctor`-Antwort anzeigen.
4. Bei `READY_FOR_EXPERIMENTAL_BUILD`: freiwillig `APK auf dem Handy bauen` bestätigen. Nach dem Build `Letzten Buildbericht anzeigen` prüfen und Hash, Signatur und echten Quellcommit protokollieren.
5. Ohne erfolgreiches Gerätetestprotokoll ist **weder** on-device APK-Erfolg **noch** eine erfolgreich installierte App nachgewiesen. Installation/PackageInstaller wird in der nächsten gesonderten Phase implementiert, nach expliziter Eigentümerfreigabe.

Bekannte offene Punkte: Komplett-APK-Kompilation des neuen GitHub-Heads, Android-16-/Termux-Service-Berechtigungen, Rückkanal für maschinenlesbare Statusmeldungen, Android-Doze-/Neustart-Recovery, echter Samsung-ARM64-AAPT2-Lauf, signierte Installation, Build-Isolierung von fremden Codeständen. Dieser UI-Bridge-Prototyp löst nur die feste, lokale **Auftragsauslösung**, nicht alle späteren EXP-00/EXP-08-Aufgaben.

Offizielle Kommunikationsreferenz: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent

## EXP-08: Termux-Rückkanal mit überprüfbarer Antwort (19.10.2026)

Die vorherige UI meldete lediglich, dass der Android-Service gestartet worden war. Nun wurden die offiziellen Termux-Extras aus dem Original-`TermuxConstants.java` geprüft und ein erster scoped Result-Callback implementiert:

- Eine **explizite, nicht exportierte BroadcastReceiver-Komponente** `ContinuumMobileBuildResultReceiver` (nur Preview-Varianten).
- Pro Besitzeraktion ein UUID-Auftrag mit nur drei festen Operationen, gespeichert in privaten Android-Preferences. Ein gleichzeitig aktiver Auftrag blockiert andere Ausführungen; nach einer Stunde kann der Besitzer fehlende Rückmeldungen ausdrücklich als ungeklärt markieren.
- `PendingIntent.getBroadcast` mit `FLAG_ONE_SHOT | FLAG_MUTABLE` und expliziter Zielkomponente. Mutabilität ist nötig, damit Termux das `result`-Bundle nachtragen kann; **keine impliziten Mutable-Intents**.
- `RUN_COMMAND_BACKGROUND=true` für stdout/stderr/Exitcode als separate Ergebnisse. Der Android-Benutzer bestätigt den kostspieligen APK-Build *vor* dieser Ausführung.
- Offizielle `com.termux.RUN_COMMAND_PENDING_INTENT`-Übergabe; Rückgabeschlüssel `result`, `stdout`, `stdout_original_length`, `exitCode`, `err`.
- Empfänger verwirft fremde/veraltete Auftrag-IDs, Operationen ohne Übereinstimmung, fehlenden Exitcode, fehlenden Termux-Erfolgsindikator und übergroße/gekürzte oder nicht JSON-dekodierbare Ausgaben.
- Nur echte kombinierte Indizien `exitCode=0`, `err=-1`, passender Build-Status sowie zwei formal gültige SHA-256-Werte führen zur **Meldung eines nicht unabhängig gerätegeprüften** APK-Kandidaten. Das ist kein Zeichen für erfolgreiche Installation.
- Der Callback speichert **nur** den kleinen Status und ggf. den APK-SHA, **keine Terminalausgabe**, Zugangsdaten oder gesamten Build-Logs.
- JVM-Negativtests in `ContinuumMobileResultPolicyTest.kt`, Node-Wiring-Sicherungen in `.github/continuum/mobile-builder-result-wiring.test.mjs`.

**Eigentümertest vor weiterer Freigabe:** die aktuelle APK mit diesen Komponenten wirklich bauen, auf einem Android-16-Gerät installieren, Termux >= 0.109, erlaubte RUN_COMMAND-Berechtigung und `allow-external-apps=true` einrichten, Doctor-/Fehler-/Build-Callbacks kontrollieren, Lifecycle/Reboot/Thermal und lange Build-Dauer erproben. Termux kann zurückmelden, aber wir können ohne ein echtes Smartphone-Protokoll keine End-to-End-Funktion behaupten.

**Wichtige Grenze:** Ein Android Result-Callback beweist nur, dass der lokale Dienst bestimmte Daten zurückgegeben hat. Der nächste Schritt muss den echten APK-Artefaktzugriff (z.B. klar begrenzter Storage-Provider), Paketkennung und erwartete Signing-Key-Identität, unabhängige Prüfroutinen und autorisierte Installation ergänzen.

Quellnachweise: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent ; https://raw.githubusercontent.com/termux/termux-app/master/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java ; https://developer.android.com/reference/android/app/PendingIntent
