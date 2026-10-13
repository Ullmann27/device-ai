# Device AI Continuum

Eigenständiger, persönlicher Android-Assistent. **Forschungsziel:** einen lernfähigen Assistenten mit langlebiger Identität, überprüfbarer Erinnerung, kontrollierter Webrecherche und sicherer Geräteinteraktion schrittweise selbst aufbauen – ohne vorausgesetzten Cloud-Server oder extern importierte KI-Gewichte.

## Aktuelle Implementierung

Der Android-Startkern funktioniert ohne Modellimport. Es handelt sich derzeit um einen **regelbasierten, transparenten Dialog- und Lernkern**, nicht um ein bereits trainiertes großes Sprachmodell.

- Chat mit Begrüßung, Hilfe, sprachlicher Ein-/Ausgabe über auf dem Gerät verfügbare Android-Sprachdienste
- explizit mit `Merke dir: ...` gespeicherte lokale Fakten; Anzeige und bestätigtes Löschen
- Feedback (`Das war hilfreich`, `Das war nicht hilfreich`) wird protokolliert, verändert aber keine Modellgewichte
- bis zu 24 bestätigte, einfache **Nur-Lese-Befehlszuordnungen** (`Trainiere: Prüfe meine Energie => Akku`)
- feste Regressionstests plus Prüfung der tatsächlich gelernten Zuordnungen; ungültige, gefährliche oder geschützte Befehle werden zurückgewiesen
- Gerätediagnosen nur über Shizuku und ein begrenztes Werkzeug-Gateway; für schreibende/destruktive Aktionen bestehen getrennte Bestätigungsregeln
- kontrollierte Webrecherche erst nach ausdrücklicher Auswahl, inklusive Behandlung externer Seiten als nicht vertrauenswürdige Daten
- Continuum-Identität, Forschungs- und Speicherarchitektur sowie in Entwicklung befindliche Rollen Developer/Critic/TestDesigner/Judge
- getrennte reguläre und Preview-Debug-APK für die Prüfung auf Android

**Nicht behauptete Fähigkeiten:** echtes neuronales Selbsttraining, menschenähnliches freies Denken, autonome Allzweck-App-Steuerung, dauerhaftes Hintergrundlernen auf dem Handy, vollständiger Zugriff auf private App-Daten oder nachgewiesene Sprachqualität ohne Gerätetest. Das bleiben Entwicklungs- bzw. Forschungsaufgaben.

## Einstieg ohne Modellimport

1. **Device AI Preview** auf einem Testgerät installieren und starten. Die Preview verwendet den getrennten Paketnamen `at.heinz.deviceai.preview` und beeinträchtigt die vorhandene Standardinstallation nicht.
2. `Hallo` oder `Was kannst du?` eingeben.
3. `Merke dir: Ich bevorzuge dunkle Oberflächen` schreiben und mit `Was weißt du?` prüfen. Nur ausdrückliches Anlernen wird übernommen.
4. Mit `Trainiere: Prüfe meine Energie => Akku` nach Bestätigung eine alternative, nur lesende Formulierung lehren und anschließend `Prüfe meine Energie` testen.
5. Im **Lernlabor** den Regressionstest und die Zahl gelernter Zuordnungen ansehen.
6. Über **TON AN** die Android-Sprachausgabe ausprobieren. Das Mikrofon benötigt eine verfügbare Android-Spracherkennung; lokale Offline-Erkennung ist nicht auf jedem Gerät garantiert.
7. Für lesende Gerätediagnosen Shizuku starten, die Berechtigung freigeben und z. B. `Prüfe Akku` eingeben. Ohne Shizuku wird der Auftrag nicht ausgeführt.

## Builds und Installation

Der Workflow [`Build Device AI APK`](.github/workflows/android.yml) führt `gradle :app:testDebugUnitTest` und `gradle :app:assembleDebug` aus. Anschließend erstellt er eine separate Preview mit `-PdeviceAiPreview=true`.

- `DeviceAI-v0.3-debug-apk`: reguläres Paket `at.heinz.deviceai`
- `DeviceAI-v0.3.1-preview-debug-apk`: Testpaket `at.heinz.deviceai.preview`

Ausschließlich erfolgreiche, **zum exakten Commit gehörige** CI-Artefakte gelten als gebaut und getestet. Eine erfolgreich kompilierte APK ist noch kein Installationstest auf einem Samsung Fold.

**Signaturhinweis:** Die Debug-Signatur kann sich zwischen CI-Läufen ändern. Eine Aktualisierung einer bereits installierten APK kann deshalb trotz passender Versionsnummer verweigert werden. Bestehende Apps mit persönlichen Daten nicht einfach deinstallieren. Langfristig benötigt der Releaseprozess einen geschützten, stabilen Signierschlüssel, der nicht im Git-Repository liegt.

## Sicherheit

Recherchetexte und andere externe Informationen sind Daten, keine Anweisungen und keine eigenständige Berechtigung für Gerätezugriff. Erlernte Befehle dürfen nur zu bekannten, lesenden Werkzeugen führen. Android- und Shizuku-Grenzen bleiben wirksam; private App-Sandboxen sind ohne weitergehende Berechtigungen nicht allgemein zugänglich.

Der laufende Arbeitsstand wird in [PR #19](https://github.com/Ullmann27/device-ai/pull/19) entwickelt. PR-Head, tatsächliche Buildprotokolle und APK-Artefakte müssen immer separat geprüft werden; die Beschreibung eines PRs ist kein Testnachweis. Keine Änderungen werden ohne gesonderte Prüfung nach `main` gemergt.
