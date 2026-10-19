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
Auf Node 22 in einer separaten lokalen Testumgebung wurden **9/9** spezialisierte Node-Tests ausgeführt: Parserblockade bei beliebigen Shellbefehlen, Geräteerkennung, fehlende Tools, State außerhalb des Checkouts, fehlende SDK-Kette, schmutziger Git-Checkout, kaputter Gradle-Lauf, ungültige Signatur und positive **synthetische** Belegkette. Keine dieser Simulationen ist ein echter ARM64-/Gradle-/Samsung-Build.

## Wichtigste nächste Arbeit
1. Toolchain-Kompatibilität **am realen Samsung**, inklusive nativer ARM64-AAPT2-Binärdatei mit passender API-36-Unterstützung nachweisen.
2. Ein signiertes Hello-World-Miniprojekt **auf demselben Telefon** bauen und installieren; danach vollständiges Continuum-APK bauen.
3. Den bestehenden `ContinuumWorkerChatRouter` mit einem minimal berechtigten, explizit freigegebenen Android↔Termux-Jobsystem verbinden und Android-Berechtigungs-/Restart-Negativtests schreiben.
4. M4-Zweit-Worktree-Verifikation und Paket-/Signer-Identitätskontrollen integrieren. Erst danach autonomen Zyklus aktivieren.

Ein Git-Worktree ist keine Betriebssystem-Sandbox. Unbekannter aus dem Internet bezogener Code darf nicht ohne getrennte OS-Isolation in Termux gebaut werden.
