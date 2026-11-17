# Device AI Continuum – Bedienung und Android-Geräteabnahme

Stand: 18. Oktober 2026 · Integrationszweig: `continuum/argon-safety-review-20261017` · PR #28 (Draft)

## Zweck

Die Oberfläche soll ohne technische Vorkenntnisse bedienbar sein, ohne vorhandene Sicherheitsgrenzen zu verschleiern. Die Designrichtung ist ein eigenständiges dunkles KI-Cockpit: kontrastreiche blau/cyanfarbene Oberflächen, abgestufte Karten, eine lokal mit Android Canvas gezeichnete isometrische 3D-Kerngrafik, klare Statusmeldungen und große primäre Aktionen. Device AI Continuum ist kein Lernspiel; andere Projekte gehören nicht in dieses Repository.

## Bedienungsmodell

1. **Chatten:** öffnet die Texteingabe. Die lokale Basis beantwortet nur unterstützte Befehle und belegte Erinnerungen. Freier Sprachdialog setzt ein geladenes kompatibles LiteRT-LM-Modell voraus.
2. **Recherchieren:** erfordert die Einwilligung vor Netzwerkanfragen; unbekannte oder leere Quellen werden niemals als gesichertes Wissen deklariert. Fehlen Quellen, nennt die Oberfläche eine nachvollziehbare nächste Aktion.
3. **Lernen:** nur bestätigte Eingaben und überprüfte Trainingskandidaten. Fehlgeschlagene Experimente dürfen gespeicherte Gewichte nicht überschreiben.
4. **KI-Modell:** zuerst Import, dann Laden. Die Modell-ID, Version und der vorab bekannte SHA-256-Prüfwert werden geprüft. Ungültige Eingaben bleiben im Formular zur Korrektur; keine stillen Downloads oder Cloud-Ersatz.
5. **Entwickeln:** erst Modell, Ziel und Quelldatei(en) bereitstellen. Der Geräteassistent erzeugt ausschließlich überprüfbare Vorschläge, keine automatischen Builds, Pushes, Patches oder Installationen. Fortschritt und Fehler sind deutsch erklärt.
6. **Gedächtnis und System:** bestätigte Fakten, Widerrufe, Berechtigungen und Shizuku sind getrennt erreichbar. Die lokale Basis darf nicht als geladenes Sprachmodell bezeichnet werden.

Statusfelder erklären sich per Tippen: **Gerät**, **Basis**, **Web**. Die Optionen „Web“ und „Gerät“ ändern keine Berechtigungen durch bloßes Öffnen der Erklärung.

## Geräte-Abnahmeliste für den nächsten APK-Build

- [ ] APK stammt vom **exakten** angegebenen Git-Commit; SHA-256 und Android-Paketname erfassen.
- [ ] Android-Build, Kotlin-/JVM-Unit-Tests, Node-/Backend-Regressionen und `:app:lintDebug` erfolgreich.
- [ ] Start bei leerem Modellkatalog: verständliches Cockpit, Status „Basis bereit“, kein fälschlich als aktiv beworbenes Sprachmodell.
- [ ] Hochformat, Querformat, kleine Android-Displays und größere Samsung-Displays: Kacheln, Statusfelder, Texte und Schaltflächen ohne Abschneiden erreichbar.
- [ ] Android-Schriftgröße 150 % und Display-Vergrößerung: Inhalte scrollbar, Schaltflächen bedienbar, kein verdecktes Texteingabefeld.
- [ ] TalkBack: Fokus auf alle Kacheln, verständliche Beschreibungen, dekorative 3D-Grafik übersprungen.
- [ ] „Chatten“ bringt die Tastatur und Texteingabe in den Vordergrund.
- [ ] Kein Modell: Entwicklungsassistent erklärt das Fehlen und öffnet die Modellverwaltung, ohne eine KI-Antwort zu erfinden.
- [ ] Kein Entwicklungsziel oder keine Quelldatei: die angezeigten Schritte benennen den tatsächlichen fehlenden Bestandteil.
- [ ] Modellimport mit ungültiger ID/Version/SHA-256: Fenster bleibt offen, Eingaben bleiben erhalten, kein Import.
- [ ] Modellimport mit gültigem **bekanntem** SHA-256: Datei wird vollständig auf Prüfsumme und unterstütztes Format geprüft; Laden und Entladen funktionieren.
- [ ] Wiederholtes Laden/Entladen, Abbruch, Bildschirmdrehung und Neustart: kein hängen gebliebener Prozess und kein Datenverlust.
- [ ] Shizuku fehlt: freizugebende Geräteaktion wird nicht ausgeführt; Nutzereingabe bleibt stehen und eine Anleitung erscheint.
- [ ] Internet: vor jeder neuen Suchfreigabe ist klar, dass die Anfrage übertragen wird.
- [ ] Recherche nach Solarzellen mit erreichbarer Wikipedia: begrenzter Textauszug, originale Quellen-URL, keine falsche Quellenidentität; großer HTML-Inhalt führt nicht mehr zwingend zum Abbruch dieser Quelle.
- [ ] Rechercheserver unerreichbar: klare Fehlermeldung ohne behauptete Quellen, kein automatischer Wissenseintrag.
- [ ] Neuronaler Trainingsfehler: Fehlerkategorie sichtbar, vorheriger gesicherter Lernstand unverändert.
- [ ] Entwicklungsentwurf: nur nach lokalem Modell + Ziel + Quelldateien. Export möglich; keine automatische Codeübernahme.
- [ ] Keine Änderungen in `main` ohne gesonderte technische Abnahme.

## Reproduzierbarer Build (vollständiger sauberer Git-Checkout)

Voraussetzungen: JDK **17**, Gradle **8.11.1**, Android SDK Platform **36**, Build-Tools **35.0.0**, Node 20/22, alle benötigten Gradle-Artefakte. Aus dem Root-Verzeichnis:

```bash
git status --porcelain
git rev-parse HEAD
bash scripts/local-m4-android.sh "$(git rev-parse HEAD)"
gradle :app:lintDebug :app:testDebugUnitTest --stacktrace
gradle :app:assembleDebug -PdeviceAiPreview=true --stacktrace
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Der M4-Nachweis alleine ersetzt **nicht** Lint, Installation, echte Nutzerführung, Modellimport oder vollständige Android-Geräteabnahme. Eine installierte ältere Preview kann eine abweichende Debug-Signatur haben; ein Signaturkonflikt darf nicht durch gefälschte Versionierung oder fremde Signierschlüssel „gelöst“ werden. Vor einer Deinstallation sind gegebenenfalls lokale Daten zu sichern.

## Nachweisgrenzen, Stand 18.10.2026

Die Quellcode- und Integrationsprüfungen des Designumbaus belegen **nicht**, dass die aktuelle APK bereits kompiliert wurde oder auf dem Gerät läuft. Die bisher verfügbaren APK-Artefakte stammen aus einem älteren Build (16.10.2026, Commit `004494068c9f3eda5965de6fdb8b7795dac1d279`) und enthalten den neuen UX-Umbau nicht. Neuere GitHub Actions wurden bereits vor Job-Schritten blockiert; ohne funktionierende Android-Toolchain oder einen tatsächlich ausgeführten Runner ist keine neu gebaute APK zu behaupten.
