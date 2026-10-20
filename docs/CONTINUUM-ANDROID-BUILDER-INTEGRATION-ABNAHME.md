# Device AI Continuum – Android-Hauptoberfläche ↔ Builder: Integrations- und Reparaturabnahme

Stand: 19. Oktober 2026 · Scope: ausschließlich Device AI Continuum · EXP-00 und EXP-08 · Draft PR #42

## Ziel und bewahrte Architektur

Die bestehende hochwertige `AgentActivity` mit Hologramm, Chat, Modellverwaltung, Gedächtnis, Shizuku und Entwicklungszentrum bleibt die **primäre Android-Oberfläche**. Der Continuum Android Builder ist ein untergeordnetes Werkzeug, keine alternative Haupt-App und kein neues unabhängiges Agentensystem.

- `AgentActivity` ist der **einzige LAUNCHER** im Manifest.
- Der Owner öffnet den Builder über die Kachel **BAUMEISTER · HANDY-BUILDER**, das bestehende Entwicklungszentrum, die Tablet-Seitenleiste oder den Befehl **Worker Builder**.
- Eine gestartete Builder-Unterseite besitzt **Zurück zu Device AI**, die nur `finish()` aufruft und den ursprünglichen Activity-Stack bewahrt.
- Auf `at.heinz.deviceai.preview` und `at.heinz.deviceai.mentorpaired` wird derselbe im APK enthaltene `ContinuumMobileBuilderActivity` genutzt; keine zweite APK ist zur internen Navigation nötig.
- Eine **separat installierte** reguläre App `at.heinz.deviceai` öffnet nur den exportierten Builder der explizit ausgewählten `at.heinz.deviceai.preview`. Fehlt dieser, erscheint eine verständliche Meldung; es erfolgt kein Fallback auf Shell, Internet oder App-Installation.
- Android teilt private Preferences zwischen unterschiedlichen Anwendungspaketen nicht. Daher zeigt das Entwicklungszentrum den letzten privaten Builder-Bericht nur innerhalb derselben Preview. In einer separaten regulären App wird bewusst **keine** paketübergreifende Einsicht behauptet.

## Audit und kontrollierte Bearbeitung

| Phase | Prüfauftrag | Nachweis / Abbruchkriterium |
| --- | --- | --- |
| A – Git/Vertrag | HEAD, AGENTS.md, EXP-Register und Issue #41 prüfen | Nur Featurebranch, PR als Draft, kein Main-Merge |
| B – Haupt-UI | Chat, Orb, Modell-, Lern-, Speicher-, Shizuku- und Entwicklungszugänge erhalten | Quelltext-Wiring und UI-Tests; kein zweiter Launcher |
| C – sichere Navigation | Integrierte Preview, Mentor-Preview, reguläre App, fehlendes Ziel | Kotlin-Route-JUnit, Source-Wiring, SecurityException-/ActivityNotFoundException-Verhalten |
| D – Android/Termux | Eigentümergenehmigte feste Node-/Git-/Gradle-Operationen, kein freier Shelltext | `mobile-builder-android-wiring`, `mobile-builder-result-wiring`, keine neue Termux-Berechtigung |
| E – Statuswahrheit | Aktive Toolchain von früheren Fehlerberichten unterscheiden; alle echten Statuswerte akzeptieren | Kotlin Policy-JUnit und Receiver-Wiring; `BLOCKED_SOURCE_CHANGED_DURING_BUILD` niemals zum Erfolg hochstufen |
| F – Android-Build | JDK17, Gradle8.11.1, ARM64 AAPT2, SDK36, Apksigner auf Samsung | 28 Builder-Node-Tests bisher am Gerät belegt; neue App-Integration noch nicht als gebaut ausgeben |
| G – APK-Abnahme | Android-App baut Preview nach Bestätigung, prüft Signatur und SHA, installiert nur mit Owner-Zustimmung | Echtes `EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED`, dann getrennte Installations-/Start-/Logcat-Prüfung |
| H – Agenten-Abnahme | Chat, lokales Modell, Backlog, Task/Review/Repair, Lernen und Berechtigungen | Pro Funktion realer Test; Modell fehlt => keine behauptete generative KI; kein automatischer Main-Push |

## Sichere, reproduzierbare Tests auf dem Samsung

Vor einem Build steht **jede Codeänderung isoliert** auf `astra/continuum-experiment-charter-20261019`. Keine unaufgeforderte Änderung an `main`.

```sh
cd "$HOME/device-ai"
git status -sb
GIT_SSH_COMMAND="ssh -i $HOME/.ssh/continuum_device_ai_readonly -o IdentitiesOnly=yes" git pull --ff-only
git rev-parse --short HEAD
node --test \
  .github/continuum/mobile-builder-android-wiring.test.mjs \
  .github/continuum/mobile-builder-existing-integration.test.mjs \
  .github/continuum/mobile-builder-result-wiring.test.mjs \
  .github/continuum/android-holographic-home.test.mjs \
  .github/continuum/android-cockpit-shortcuts.test.mjs \
  .github/continuum/android-integration-wiring.test.mjs \
  scripts/continuum-mobile-builder.test.mjs
node scripts/continuum-mobile-builder.mjs doctor
```

`git pull --ff-only` ist nur bei sicherem Git-Stand zulässig. Auf einen unerwarteten Branch, lokale Änderungen, rote Tests oder `doctor.status=UNAVAILABLE` folgt **kein automatischer Build**. Termux darf nicht durch `|| exit 1` die interaktive Sitzung schließen.

Kotlin-Unit-Tests in der Android-Termux-Umgebung (JDK17 explizit und SDK/AAPT2 lokal):

```sh
cd "$HOME/device-ai"
JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk" \
PATH="$PREFIX/lib/jvm/java-17-openjdk/bin:$PATH" \
ANDROID_HOME="$HOME/android-sdk" \
ANDROID_SDK_ROOT="$HOME/android-sdk" \
"$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle" \
  :app:testDebugUnitTest \
  --tests at.heinz.deviceai.ContinuumMobileBuilderRouteTest \
  --tests at.heinz.deviceai.ContinuumMobileResultPolicyTest \
  --tests at.heinz.deviceai.ContinuumMobileBuildPlanTest \
  --console=plain --no-daemon -Pkotlin.incremental=false
```

**Nicht voraussetzen**, dass Android-Unit-Tests die Laufzeit, Termux-Dienstrechte oder zwei installierte Anwendungspakete beweisen. Dafür sind Besitzer-gesteuerte APK-Gerätetests nötig.

## Geräte-Szenarien nach einem tatsächlich erfolgreichen neuen Preview-Build

1. **Launcher**: Nach expliziter Installation/Update genau ein Continuum-Preview-App-Icon, das die hochwertige AgentActivity öffnet; keine zweite Builder-Startbildschirm-App.
2. **Hologramm, Chat und Bereiche**: Ursprüngliche UI sichtbar; Chat/Modelle/Lernen/Gedächtnis/Shizuku unverändert navigierbar.
3. **Builder-Einstieg**: Haupt-Dashboard-Kachel, Entwicklungszentrum sowie `Worker Builder` öffnen denselben Builder; Zurück bringt den Nutzer zum ursprünglichen Bildschirm.
4. **Termux Doctor**: App → `Werkzeuge prüfen` → Ergebnis aktualisieren; echte READY-Meldung vom Gerät und nicht bloß ein gestarteter Intent.
5. **Status**: Historischer `BLOCKED_MISSING_TOOLCHAIN`-Bericht wird als früherer Fehler dargestellt; keine falsche Behauptung eines aktuellen Werkzeugfehlers oder einer erfolgreichen Installation.
6. **Build**: Nur nach bestätigtem Owner-Dialog; tatsächliche Unit-/Build-Exitcodes, Signatur und Hash. Kein eigenmächtiges APK-Installieren.
7. **Negativfälle**: Vorschau fehlt; Termux nicht freigegeben; Hintergrundjob noch offen; Build-Quellcode verändert; fehlende Signatur; unvollständiger Callback. Erwartung immer verständliche Fehlermeldung statt Erfolg.
8. **Getrennte Pakete**: `at.heinz.deviceai` öffnet den existierenden Preview-Builder über explizite Android-Komponente, kann aber private Preview-Jobberichte nicht selbst lesen.

## Fester Installationsordner nach signatur- und hashgeprüftem Build

**Owner-Workflow, eingeführt 20.10.2026:** Nach erfolgreichem `apksigner verify`, SHA-256-Prüfung und unverändertem Git-HEAD speichert der mobile Node-Builder **zuerst** das private, unveränderliche Artefakt im lokalen State und kopiert **danach** automatisch eine zusätzliche APK nach:

`Interner Speicher/Download/00-DEVICEAI-INSTALLATION/DeviceAI-Continuum-Preview-<HEAD7>-<SHA12>.apk`

Die Kopie wird erst nach einer erneuten SHA-256-Prüfung der temporären Download-Datei sichtbar gemacht. Vorhandene gleiche Kopien werden nur bei identischem Hash wiederverwendet, Konfliktdateien bleiben unangetastet. Wenn Termux keinen freigegebenen Download-Zugriff hat oder der Zielordner unsicher ist, bleibt die original verifizierte private APK erhalten und `ownerInstallCopy.status` lautet `UNAVAILABLE`. Bei Erfolg `COPIED`, bei anderen Plattformen `NOT_TERMUX`. Der bestehende Buildstatus bleibt `EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED`; die App darf aus der Kopie **keine** installierte oder produktionsreife App ableiten.

Die alte Handarbeit mit `cp`/Dateimanager-Suche entfällt **erst für Builds, die mit dieser neuen Builder-Version gestartet wurden**. Die bereits um 04:27 aus der zuvor installierten Preview erzeugte APK hatte diesen Automatismus noch nicht. Kein Download, kein automatischer Android Package Installer, keine ungefragte Aktualisierung. Die Nutzerin/der Nutzer öffnet die APK aus diesem festen Download-Unterordner und bestätigt Androids Installation selbst.

**Gerätenachweis für vorherige Preview, 20.10.2026:** Vollständiger Test- und Lint-Lauf, erfolgreiches Gradle-APK via Android-Builder-Button, SHA-Konsistenz, Preview-Paket-ID und `AgentActivity`-Launcher. Owner-Screenshot **04:49** zeigt die tatsächlich gestartete hochwertige Device-AI-Hauptoberfläche mit Hologramm, Chat und dem sichtbaren `Handy-Builder · Termux`-Button. **Noch offen:** den Button anwählen, Builder und Rücknavigation am Gerät prüfen, neuen Autokopiermechanismus auf Samsung bauen/testen, unabhängiger M4-/Langzeit-Agententest.

## Rücksetzen, offene Risiken und ehrliche Abnahme

- Änderungen ausschließlich im Draft-Branch. Ein Rollback erfolgt durch Revert der isolierten Commits; **kein** Main-Eingriff.
- Der Besitzer hat die neue Preview `9f77202` am **20.10.2026 um 04:49** auf dem Samsung sichtbar gestartet; der Screenshot zeigt die moderne Device-AI-`AgentActivity` mit Orb und Baumeister-Kachel. Ein späterer Build mit dem hier neu entwickelten **automatischen Download-Kopierer** muss separat gebaut, getestet und ausdrücklich installiert werden. Bereits installierte APKs ändern sich durch einen GitHub-Commit nicht.
- Bei einem nicht installierten passenden lokalen LLM wird keine autonome Codegenerierung als getestet ausgegeben.
- Der zuerst manuell gelungene Samsung-APK-Build belegt **nicht** den vollständigen Build über den Android-Button, keine APK-Gerätelaufzeit-Suite und keine kontinuierlich autonome Softwareentwicklung.
- Unabhängige M4-Gates, echtes UI-Gerätefeedback, Langzeitbetrieb und Produktionsfreigabe bleiben eigene Abnahmestufen. Testskips sind einzeln zu begründen und dürfen nicht stillschweigend als bestandene Tests gezählt werden.

**Erst nach grünen echten Node-, Kotlin-, Build-, Signatur- und Geräteprüfungen darf diese Integrationsstufe als technisch abgenommen bezeichnet werden.**
