# Continuum MASTER – M4: Erste selbst erzeugte, separate Android-App

**Stand:** 23. Oktober 2026. **STATUS: SOURCE_INTEGRATED + MOCK_TESTED + Kotlin-Syntaxprüfung. Kein realer Android-APK-Build und kein Fold7-Gerätebeleg.**

## Was wirklich hinzugekommen ist

Der bestehende Android-/Termux-Builder kann jetzt eine **erste eigene Android-App aus einem geprüften, festen Rezept** erzeugen, überprüfen und über dieselbe native Termux-Toolchain bauen lassen. Das ist eine neue Projektart, nicht nur eine Reparatur von Continuum.

- Projekt: `~/continuum-projects/m4-notes`, Paket `at.heinz.continuum.m4notes`, Version 0.1.0 / code 1.
- Zwei echte Ansichten: **Notizen** und **Über**; Notizen liegen privat in `SharedPreferences`; `NotePolicy` normalisiert und begrenzt Eingabetext.
- Kein `INTERNET`, kein Paketmanagerzugriff und keine sonstigen Android-Berechtigungen in der erzeugten App.
- Gesicherte Quellrezeptur: neun feste Dateien mit SHA-256 im `continuum-source-manifest.json`. Quelländerungen, fehlende Dateien und Symlinks blockieren den Build.
- Keine Wiederverwendung, Änderung oder Überschreibung von `~/device-ai`, anderer Projektordner, Lerngewichte, Installer und Besitzer-APK.
- Build nur nach Freigabe, mit `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, offline, gedeckelter Laufzeit, über dem vorhandenen Termux-Doctor und dem gemeinsamen Build-Lease.
- Bei hypothetisch erfolgreichem Geräte-Build werden tatsächliche Gradle-Ergebnisse, APK-Signatur, exakter eigener Paketname, Version und SHA-256 geprüft; das Ergebnis bleibt privat und wird **nicht automatisch installiert**.
- Status unterscheidet `PROJECT_CREATED_NOT_BUILT`, `SOURCE_VERIFIED` und `EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED`. Android akzeptiert sie nur mit überprüfter Operation, Exit-Code und den erforderlichen SHA-256-Werten.
- In der vorhandenen Android-Builder-Activity drei neue Kategoriebuttons: **M4 · Eigene Notiz-App erzeugen**, **M4 · App-Quellen prüfen**, **M4 · Eigene Notiz-App bauen**. Erzeugen und Bauen erfordern Besitzerbestätigung.

## Nicht erreichen / epistemische Grenze

Diese M4-Stufe ist ein **festes geprüfte Projektvorlage**, noch **kein** frei formulierter App-Generator mit einem selbstständig generierenden Coding-LLM. Sie beweist noch nicht, dass CCC1 beliebige Apps entwerfen kann oder dass Termux auf dem Samsung das Projekt tatsächlich bauen kann.

Die Termux-/Android-Grenze ist nicht mit einer Betriebssystem-Sandbox gleichzusetzen. Die fest vorgegebene harmlose Quelle plus Ressourcengrenzen minimieren den Testumfang. Für freie Modellcode-Ausführung muss eine wirksame Dateisystem-/Netzwerk-/Prozess-Isolation nachgewiesen werden.

## Nachweis

- Exakt getestete neue `scripts/continuum-owned-app.mjs`: Git-Blob `33202f6fcc69f3028bf94ce7c045bda053f3efe2`.
- Exakt getestete `scripts/continuum-owned-app.test.mjs`: Git-Blob `451cea142247e826a3f0e79921f3860fdfcd8187`.
- Lokal mit Node 22: **20/20 Tests, 0 Fehler**. Darunter echte Quellgenerierung auf isoliertem temporärem Dateisystem, SHA-/Symlink- und Überschreibungsschutz sowie simulierte (NICHT echte) Gradle-/Signatur-/Paket-/APK-Rückläufe.
- Mit Kotlin-Compiler und **lokalen Android-API-Stubs** kompilierten `MainActivity.kt` und `NotePolicy.kt` syntaktisch; `NotePolicy.kt` zusätzlich alleine mit `kotlinc`. **Das ist kein Android-SDK-Compile und kein APK-Build.**
- Zusätzlich **21/21 statische, direkt am GitHub-Quellzweig geprüfte Integrationsbedingungen**: gleiche Git-Blobs wie lokal getestet; unveränderte Besitzer-Paket-ID/Code19; Button-/Termux-/Receipt-Verkabelung; Sicherungsgrenzen; keine Installation.
- Hinzugefügt: `app/src/test/java/at/heinz/deviceai/ContinuumOwnedAppContractTest.kt` mit negativen Android-/Receipt-Fällen; **Kotlin-JUnit dieser neuen Datei am kombinierten Branch noch nicht ausgeführt**.
- Die komplette `0.3.18`-Android-/Node-/Backend-Testkette muss auf **diesem** Branch neu grün sein; ein Erfolg auf Opus oder Signierbasis zählt nicht automatisch.

## Direkte, noch nicht auf dem Gerät ausgeführte Testreihenfolge

```sh
cd ~/device-ai
git status --short
node --version
node --test scripts/continuum-owned-app.test.mjs
node scripts/continuum-owned-app.mjs create
node scripts/continuum-owned-app.mjs verify
node scripts/continuum-owned-app.mjs build
```

Beim ersten Aufruf dürfen keine existierenden Benutzerdateien überschrieben werden. `build` meldet fehlende SDK-/Signer-/Toolchain-Fähigkeiten als `BLOCKED_MISSING_TOOLCHAIN` und niemals als Erfolg. Bei erfolgreicher Quelle muss die erzeugte APK eine andere Paketkennung als die Continuum-Haupt-App tragen.

## Noch vor Android-Abnahme

1. Gesamter Node-/Backend-/Kotlin-Testlauf, Android Gradle/Lint auf dem aktuellen exakten M4-Commit.
2. Prüfungen von `ContinuumMobileBuildResultReceiver` mit echtem Termux-Rückkanal, vor allem `stdout_original_length` und Beendigung/Storno.
3. Die M4-Buttons müssen zuerst durch eine geprüfte neue Besitzer-APK auf das Fold7 gelangen. **Nicht** aus diesem Branch direkt ungeprüft signieren.
4. Separaten App-Quellordner erzeugen, vorhandenes Projekt nicht überschreiben, `SOURCE_VERIFIED` erreichen.
5. Erste echte Gradle- und Signer-Quittung aus Termux nachweisen, **keine** APK automatisch installieren.
6. Installation der getrennten M4-Notiz-App erst nach Eigentümerentscheidung; zwei Ansichten, Speicherung, App-Neustart und Deinstallation ohne Datenverlust der Continuum-Haupt-App prüfen.

## Nächster technischer Ausbau

Erst nach Device-Verifikation dieser begrenzten Vorlage: Versionierte, **typisierte** Projektpläne (Screens, Permissions, Akzeptanztests), Projekt-Build und unabhängige QA durch die bestehende CCC1-Pipeline. Nur der Kandidatengenerator darf später durch ein lokales Sprachmodell ergänzt werden. Keine zweite Shell-, Backlog- oder Privilegierungsarchitektur.

**Rollback:** ausschließlich diesen isolierten Branch beziehungsweise neue M4-Dateien und deren UI-Adapter verwerfen; die bisherige Besitzer-App 0.3.17, die 0.3.18-Reviewbasis, alle Termux-Bestandsaufträge und Gewichte bleiben unangetastet.
