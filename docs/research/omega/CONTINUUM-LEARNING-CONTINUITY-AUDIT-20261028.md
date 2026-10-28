# Continuum-Lernkontinuität: Alt-/Neu-App, Stand 2026-10-28

## Nachprüfbare Beobachtung und Grenzen
Der Besitzer berichtet für die ältere Installation ungefähr 331.000 Gewichtsupdates, für die neue Continuum-Lokal-Haupt-App nur etwa 3.000–4.000; außerdem weniger Lernereignisse und sichtbare Quellenaktivität. Die Zahlen sind Besitzerbeobachtungen, **keine aus dem Gerät ausgelesenen Auditwerte**. Im GitHub-Connector besteht kein Zugriff auf Android-Privatspeicher oder laufende Jobs.

App-Identitäten: `at.heinz.deviceai.mentorpaired` (alt) und `at.heinz.deviceai.continuumlocal` (aktuelle Haupt-App, v0.3.25 / versionCode 26). Android trennt deren private Daten. Ein funktionierender Quellimport umfasst nicht automatisch Modellgewichte, Experience-Ledger, JobScheduler-Registrierung, Forschungsthemen, Freigabepräferenzen oder CDX-/CRE1-Checkpoints.

## Durch Quellcode belegte Bedeutung der Metriken
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt`: `status().modelUpdates` stammt aus `TrainableSubwordEncoder.updates` (lokales SWB1-Kleinmodell). Das ist **kein** Training des LiteRT-LM-Gemma-4-Modells. Aktiver Encoder unter `learning-core-v2/subword-v1.bin`, gesonderter Last-known-good-Stand unter `subword-v1.lkg`.
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt`: `experiences-v2.bin` enthält höchstens 256 aktuelle Records; diese Zahl ist keine lebenslange Anzahl aller Trainingsschritte.
- `app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt`: Startseite zählt heute registrierte Experience-Zeitstempel; das Wissensarchiv wird separat über `ContinuumKnowledgeArchive.stats()` gelesen.
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt`: `enabled`, `attempts`, `sources` und `accepted` sind eigene App-Präferenzen; eine registrierte JobScheduler-Anforderung beweist keine abgeschlossene Ausführung. Das Offline-Lernen ist separat periodisch geplant.
- `ContinuumLearningCore.importSnapshot()` prüft fremde Gewichte gegen lokale Intent-/Semantik-Holdouts und persönlichen Replay-Schutz; ein verworfener Snapshot darf **nicht** durch blindes Kopieren des Dateisystems erzwungen werden.

Die ältere Übergabe `docs/handoff/CONTINUUM-ASTRA-MAX-GESAMTUEBERGABE-20261028.md` dokumentiert die Übernahme von 532 Archivquellen und einen abgelehnten SWB1-Import. Es liegt **kein Nachweis einer vollständigen sicheren Datenmigration** vor.

## Tatsächlich implementiert in diesem Branch
`AgentActivity.continuumDashboardText()` zeigt jetzt die tatsächliche `BuildConfig.APPLICATION_ID`, erklärt die App-Sandbox, unterscheidet SWB1-Updates explizit von Gemma-4-Gewichten und stellt Archivquellen, Experience-Kapazität, Forschungsfreigabe, Android-Jobregistrierung sowie Versuch-/Quellen-/Akzeptanzzähler nebeneinander. `learningCenterStatus()` benennt SWB1-Encoder-Updates eindeutig. Es wird kein neuer Speicher, Import, Cloud-Dienst oder autonomes Gewichtsupdate erzeugt.

Tests: `scripts/continuum-learning-parity-readout.test.mjs` enthält fünf Node-Quellcodevertragsprüfungen. Zusätzlich wurden acht Bedingungen direkt auf den zurückgelesenen GitHub-Blobs geprüft: 8/8 PASS (SOURCE/STATIC). **Nicht** ausgeführt: Android-Gradle, Android-JUnit auf vollständigem Checkout, Live-Fold7, native Gemma-Inferenz oder echter Export-/Importversuch. Daher derzeit **STATIC_VERIFIED**, nicht **ANDROID_BUILT** oder **DEVICE_VERIFIED**.

## Folgearbeiten, in sicherer Reihenfolge
1. Auf beiden installierten Apps den Lernkernstatus mitsamt App-ID, SWB1-Modellhash, SWB1-Updates, Parameterzahl, Holdouts und letzten Audits erfassen. Öffentliche Forschung: Freigabe, registrierter Job, Zähler, letzte Fehlermeldung. Keine nachträglichen Zahlen erfinden.
2. Vor jeglichem Migrationsversuch zwei getrennte, überprüfte Sicherungen der alten Daten und originalen Continuum-Lokal-P12 nachweisen; die alte App nicht deinstallieren. Besitzerbestätigung und Dateihashes festhalten.
3. Quelle für jede zu übertragende Datenklasse einzeln identifizieren: Archiv, Besitzerfakten, revoked Records, Experience-Ledger, Subword-Gewichte, SWB2, CRE1, CDX1, Dialoghistorie, Jobstatus. Fehlende Export-APIs nicht durch Zugriff auf fremde App-Sandboxes ersetzen.
4. SWB1-Snapshot nur über vorhandenen schützenden Importpfad unter DEV-/frozen-Holdout kontrolliert prüfen. Bei Regression alte Gewichte unverändert bewahren; nicht automatisch akzeptieren.
5. Für Lernfortschritts-Claims unabhängige unbekannte Aufgaben, Vorher-/Nachher-Auswertung und reale Geräteleistungsgrenzen verwenden; Updatezähler allein sind kein Intelligenzbeleg.
6. PR #205 und PR #206 sind divergiert (Merge-Base `f39fbe31a677d8c5020df11805b901abc4cedb73`). `LocalModelCatalog.kt` ist an beiden aktuellen HEADs identisch (Blob `94995cfccedb73ef20d39748567e16f402a79c10`), kein doppelter Import-Patch.

## Release-Grenze
`scripts/continuum-fold7-owner-update-gate.sh` benutzt derzeit den historischen Besitzer-Pfad, der `at.heinz.deviceai.mentorpaired` baut, **nicht** die neue lokale Haupt-App. Diesen Gate-Einstieg nicht für die nächste Continuum-Lokal-APK verwenden. Die vorhandene separate `scripts/continuum-fold7-standalone-apk.sh` adressiert `at.heinz.deviceai.continuumlocal`, die vorhandene lokale P12 und Versionscode 27. Benutzung erst nach vollständigen Tests, verifiziertem Signer und ausdrücklicher Besitzerfreigabe; keine automatische Installation.

Dieser Branch basiert auf PR #205, ersetzt keine PR-#207/#208-Komponenten, enthält keinen Merge und verändert kein Main/Production.
