# Continuum MASTER – M4: Erste selbst erzeugte, separate Android-App

**Stand:** 23. Oktober 2026. **STATUS: SOURCE_INTEGRATED + MOCK_TESTED + Kotlin-Syntaxprüfung. Kein realer Android-APK-Build und kein Fold7-Gerätebeleg.**

## Was wirklich hinzugekommen ist

Der bestehende Android-/Termux-Builder kann jetzt eine **erste eigene Android-App aus einem geprüften, festen Rezept** erzeugen, überprüfen und über dieselbe native Termux-Toolchain bauen lassen. Das ist eine neue Projektart, nicht nur eine Reparatur von Continuum.

- Projekt: `~/continuum-projects/m4-notes`, Paket `at.heinz.continuum.m4notes`, Version 0.1.0 / code 1.
- Zwei echte Ansichten: **Notizen** und **Über**; Notizen liegen privat in `SharedPreferences`; `NotePolicy` normalisiert und begrenzt Eingabetext.
- Kein `INTERNET`, kein Paketmanagerzugriff und keine sonstigen Android-Berechtigungen in der erzeugten App.
- Gesicherte Quellrezeptur: neun feste Dateien mit SHA-256 im `continuum-source-manifest.json`. Quelländerungen, fehlende Dateien und Symlinks blockieren den Build.
- Keine Wiederverwendung, Änderung oder Überschreibung von `~/device-ai`, anderer Projektordner, Lerngewichte, Installer und Besitzer-APK.
- Build nur nach Freigabe, mit `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, offline, Kotlin-Compiler im selben Prozess, gedeckelter Laufzeit, über dem vorhandenen Termux-Doctor und dem gemeinsamen Build-Lease.
- Bei tatsächlich erfolgreichem Geräte-Build werden tatsächliche Gradle-Ergebnisse, APK-Signatur, exakter eigener Paketname, Version und SHA-256 geprüft; das Ergebnis bleibt privat und wird **nicht automatisch installiert**.
- Status unterscheidet `PROJECT_CREATED_NOT_BUILT`, `SOURCE_VERIFIED` und `EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED`. Android akzeptiert sie nur mit überprüfter Operation, Exit-Code und den erforderlichen SHA-256-Werten.
- In der vorhandenen Android-Builder-Activity drei neue Kategoriebuttons: **M4 · Eigene Notiz-App erzeugen**, **M4 · App-Quellen prüfen**, **M4 · Eigene Notiz-App bauen**. Erzeugen und Bauen erfordern Besitzerbestätigung.

## Nicht erreichen / epistemische Grenze

Diese M4-Stufe ist ein **festes geprüfte Projektvorlage**, noch **kein** frei formulierter App-Generator mit einem selbstständig generierenden Coding-LLM. Sie beweist noch nicht, dass CCC1 beliebige Apps entwerfen kann oder dass Termux auf dem Samsung das Projekt tatsächlich bauen kann.

Die Termux-/Android-Grenze ist nicht mit einer Betriebssystem-Sandbox gleichzusetzen. Die fest vorgegebene harmlose Quelle plus Ressourcengrenzen minimieren den Testumfang. Für freie Modellcode-Ausführung muss eine wirksame Dateisystem-/Netzwerk-/Prozess-Isolation nachgewiesen werden.

## Nachweis

- Exakt getestete neue `scripts/continuum-owned-app.mjs`: Git-Blob `03894c0dfe696e1963a22dd962bbe4047f94a320`.
- Exakt getestete `scripts/continuum-owned-app.test.mjs`: Git-Blob `eddabe2a51be6ef761ede0d53c4eade4544054c6`.
- Lokal mit Node 22: **23/23 Tests, 0 Fehler**. Darunter echte Quellgenerierung auf isoliertem temporärem Dateisystem, SHA-/Symlink- und Überschreibungsschutz sowie simulierte (NICHT echte) Gradle-/Signatur-/Paket-/APK-Rückläufe.
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

## Sicherheitsnachtrag

Vor PR-Abschluss wurden drei weitere Quell-/Statusordner-Symlink-Angriffsfälle nach einem echten Befund hinzugefügt. Der letzte Quellstand und seine Tests sind byteidentisch zwischen lokalem Node-22-Prüflauf und GitHub. Abschluss: 23/23, 0 fehlgeschlagen, 0 übersprungen. Die Eigentümer-App wurde nicht gebaut oder installiert.

## GPT-6 Nachprüfung · Termux-Buildwerkzeug (23.10.2026)

Bei der erneuten Übernahme wurde ein realer Integrationsfehler entdeckt: `continuum-owned-app.mjs` verlangte zusätzlich zu `aapt2` ein Legacy-`aapt`-Programm, obwohl der vorhandene autorisierte `doctor()` nur `aapt2` als Voraussetzung bestätigt. Dies konnte einen betriebsbereiten Termux-Build fälschlich als `BLOCKED_MISSING_TOOLCHAIN` ablehnen. M4 verwendet jetzt dasselbe von `doctor()` verifizierte native `aapt2 dump badging` wie der Haupt-Builder. Der vorhandene M4-Test-Dummy wurde entsprechend auf `aapt2` korrigiert. Die beiden oben referenzierten Git-Blobs wurden exakt lokal mit Node 22 ausgeführt: **23 von 23 Tests bestanden, 0 Fehler, 0 Übersprungene**. Dies ersetzt keine echte ARM64-/Android-SDK-Buildabnahme.

Zusätzlich wurde die Besitzer-Release-Prüfung `scripts/render-mentor-owner-update-v19.sh` für den aktuellen isolierten M4-/Trainingsquellstand um 10 weitere geprüfte Hash-Gates ergänzt (insgesamt 19), unter anderem Android-Kategorie-Buttons, Termux-Routing, Ergebnisvalidierung, Modelltrainingsstudio und M4-Skripte. Dadurch darf ein künftiger Render-Release nur den exakt freigegebenen Gesamtstand verwenden; weder `main` noch Render wurde aktualisiert. Die neue Trainingsoberfläche bleibt ein separates Modul innerhalb des M4-Branches und ist keine Voraussetzung für den Projektbuild.

## M4-Qualitätsrunde · Notiztext und Android-Ansichten (23.10.2026)

Zusätzliche Geräte-Logik: Ein ungespeicherter Notizentwurf bleibt beim Wechsel der zwei Bildschirme erhalten und wird in `onSaveInstanceState` für eine Android-Neuerstellung der Activity gesichert. Speichern in `SharedPreferences` bleibt ausdrücklich erst beim Druck auf **Lokal speichern** erlaubt. Ein fehlgeschlagener `commit()` behauptet keinen Erfolg und lässt den Entwurf bestehen. Der neue Source-Blob ist `03894c0dfe696e1963a22dd962bbe4047f94a320`; Tests `eddabe2a51be6ef761ede0d53c4eade4544054c6`. **Node 22: 23/23 grün, 0 übersprungen; genau diese Blobs auf GitHub.** Die daraus erzeugten aktuellen Kotlin-Klassen `MainActivity.kt` + `NotePolicy.kt` kompilierten syntaktisch mit lokalem `kotlinc` und expliziten Android-API-Stubs (Exit 0). Dies ist weiterhin **kein offizieller Android-SDK-Build** und keine Geräteabnahme.

Parallel liegt das separat anwählbare **Continuum Trainingsstudio** in der Besitzer-UI mit **18 Themenrubriken, 256 Übungen und 600 Eingabeschritten**; darin österreichische Alltagssprache, Grammatik und Tippfehler. Diese Vorlagen verbessern Modellgewichte **nicht automatisch**. Die 30-Sekunden-Automatisierung bleibt ein gesonderter Implementierungs- und Validierungsauftrag, damit keine bloße Fragenwiederholung als nachgewiesener Lernfortschritt ausgegeben wird.

## M4-Sicherheitsprüfung – Gradle-Ausgabe (23. Oktober 2026)

Nach Weiterentwicklung hinzugefügt: `outputWithinProject()` prüft die tatsächlich existierenden Verzeichnisse `app/`, `app/build/`, `app/build/outputs/`, `app/build/outputs/apk/` und `app/build/outputs/apk/debug/` mit `lstatSync`. Ein symbolischer Link oder Nicht-Verzeichnis verhindert den Aufruf von Gradle (Status `BLOCKED_UNSAFE_BUILD_OUTPUT`). Eine erneute Prüfung nach Gradle verhindert, dass eine umgeleitete Ausgabe als APK-Beleg übernommen wird.

Dazu drei neue Negativtests: Symlink an `app/build`, Symlink in `app/build/outputs/apk` sowie manipulierte Ausgabe während eines simulierten Gradle-Durchgangs. Sie sind in `scripts/continuum-owned-app.test.mjs` als Quelltests versioniert. **Diese drei neuen Node-Tests wurden in dieser Weiterentwicklung noch nicht real ausgeführt.** Die letzte tatsächliche Node-Ausführung vor dieser Änderung war 23/23 grün. Aus den 23 + 3 wird deshalb **nicht** fälschlich ein bestätigtes 26/26 gemacht.

Quell-Blobs: Generator `d8fef48aeadafc24e469d33abe0ef6da321bb332`; Tests `a65e558b2c08f32bca4bb8d87073d451a9f0c067`. Acht konkrete Integrationsaussagen wurden gegen den GitHub-Quellstand geprüft (Guard vor/nach Build, echte `aapt2`-Anbindung, Ausschluss automatischer Installation und exakte Release-Hashes): **8/8 statisch erfüllt**. Das gesamte Besitzer-Release-Gate besitzt 22 attestierte GitHub-Dateien; der GitHub-Gesamtbaum stimmt **22/22** mit den hinterlegten Hashes überein.

Zusätzlicher unabhängiger Kotlin-Check: das bereitgestellte, aus demselben Notiz-Rezept erzeugte Projekt wurde lokal mit `kotlinc` kompiliert, einschließlich `MainActivity.kt`, `NotePolicy.kt` und `NotePolicyTest.kt`, unter explizit **nachgebauten Android-API- und JUnit-Teststubs** (Exit-Code 0, Compiler-Ausgabejar entstanden). Dieser Check bestätigt Syntax/Typverträglichkeit gegen die Stubs, aber **keine APK-Erzeugung**, keinen offiziellen Android-SDK-/Lint-Lauf und keinen Test auf dem Fold7.

**Freigabe weiterhin gesperrt:** neuer Node-Gesamttestlauf und Gradle-/Lint-/SDK-Signatur- und Geräte-Test müssen auf genau diesem aktuellen Review-Commit grün werden. Der PR bleibt Draft. Keine Branch-Merges und keine Änderungen am bestehenden privaten Signierdienst.
