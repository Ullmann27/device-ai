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

## Fortsetzung: ausgeführter Lernstandsvergleich und sichere APK-Grenze

### Ausführbare read-only Vergleichslogik
`scripts/continuum-learning-parity-audit.mjs` akzeptiert zwei ausdrücklich zugeordnete, optional unvollständige JSON-Messprotokolle und vergleicht ausschließlich berichtete Zähler und Prüfsummen. Unbekannte Werte bleiben `null`. Gleiche SHA-256-Herkunft beweist weder den historischen Importprozess noch vollständige Migration; deshalb bleiben `verifiedModelWeightMigration`, `verifiedFullMigration` und `verifiedGemmaInference` immer `false`. Ein Holdout-Vergleich erfolgt nur bei ausdrücklich übereinstimmender Benchmark-ID und identischen Fallzahlen; die unabhängige Echtheit der Eingaben ist damit noch nicht bewiesen. Externe APIs und Datenänderungen existieren nicht.

`scripts/continuum-learning-parity-audit.test.mjs` enthält 5 kontrollierte Node-Tests, die am 28.10.2026 in einer isolierten Node-22-Laufzeit wirklich bestanden wurden (5/5).

### Reale Fehlerkorrektur am Fold7-Release-Gate
`scripts/continuum-fold7-owner-update-gate.sh` adressierte bislang das historische `mentorpaired`-Paket und seine Signierumgebung. Der isolierte Fix verwendet nur den vorhandenen `continuum-fold7-standalone-apk.sh`-Pfad für `at.heinz.deviceai.continuumlocal`. Er verlangt exakten SHA/sauberen Entwicklungbranch/offiziellen Origin; prüft Paketidentität, nur originale lokale P12 mit gepinntem Zertifikat und v27+; verzichtet auf automatische Installation, Pull, Secret-Upload und Main-Änderungen. QA verwendet keine Signier-Secrets. Jede Regression erhält ein neues getrenntes privates Evidence-Verzeichnis.

Wichtiger nachgewiesener Shell-Fehler: Eine fehlgeschlagene Vollregression in einer `( ... ) || blocked`-Gruppe konnte durch einen danach erfolgreichen Node-Test überdeckt werden. Beide Teilbefehle haben jetzt eigene `|| exit 69`-Schranken. Die Tests prüfen explizit diese Fehlerverdeckung und verweigern dann die APK-Freigabe.

`scripts/continuum-fold7-owner-update-gate.test.mjs` enthält 14 in isolierten Git-Fixtures ausgeführte Node-Tests, zuletzt **14/14 PASS** (nicht Signierung auf einem echten Telefon). Zusammen mit den fünf Vergleichstests **19/19 PASS**, ohne Android-SDK oder Fold7. Shell `bash -n` PASS. Das ist `UNIT_TESTED` für die Node-/Bash-Gates, nicht `ANDROID_BUILT` oder `DEVICE_VERIFIED`.

### Exakte verifizierte Git-Blobs (von GitHub zurücklesbar)
- Release-Gate: `73e122b6aaac4210664668426233d5787dfa7991`.
- Release-Gate-Tests: `181c473a20606039599eb881255bad7b73c60dbb`.
- Lernvergleich: `6dd6384763b628919ffd3d6d057feb59a593ac38`.
- Lernvergleich-Tests: `ffb3f07a072454b60945b1ad3540daa091446280`.

Keine Besitzer-Signatur, keine App-Daten, kein Modell und kein Produktionsbranch wurden verändert. Das veraltete `scripts/continuum-fold7-update.sh` ist als gesonderter, fest an PR205 gebundener Pfad nicht der aktuelle sichere Freigabeeinstieg. Für neue APKs ist bis zur späteren Endfreigabe ausschließlich das korrigierte SHA-gepinnte Gate als Kandidat vorgesehen; es wurde hier nicht auf Fold7 ausgeführt.

## Externe Gegenprüfung Gemma 4 E4B / LiteRT-LM (28.10.2026)
Offizielle Google-AI-Edge-Dokumentation: https://developers.google.com/edge/litert-lm/models/gemma-4 und https://github.com/google-ai-edge/LiteRT-LM . Dort sind Gemma 4 E4B im LiteRT-LM-Modellkatalog, Android-CPU-Ausführung und eine Modellgröße von rund 3,65 GB dokumentiert. Als Referenz für ein **anderes Gerät** (Samsung S26 Ultra) gibt Google etwa 3.283 MB Peak-CPU-Memory an. Der reale Fold7-RAM-Bedarf, der Picker-Fehler und die native Ausführung sind damit **nicht** verifiziert.

Die lokale Implementierung `LocalModelCatalog.import()` kopiert die Stream-Bytes, prüft den veröffentlichten SHA-256 und führt erst danach `NativeLiteRtModelInspector.inspect()` aus. `AgentActivity.importModel()` lädt anschließend asynchron über die lokale Runtime. Die derzeit vorhandene Lifecycle-Timeout-Meldung nach 120 Sekunden ist keine gesicherte Ursachenanalyse eines gescheiterten 3,65-GB-Imports. Vor jeder Reparatur benötigt die echte Geräteprüfung die zeitlich getrennten Phasen: SAF-URI/Streamöffnung, tatsächlich kopierte Bytezahl, vollständige Hashprüfung, Inspektionsstatus, Runtime-Load, erste Inferenz und kontrollierter Neustart. Keine Neuladung/Neuspeicherung auf Verdacht. Auch LiteRT-LM `0.18.0` im Android-Gradle-Katalog ist Quellenstand, nicht bereits bewiesene E4B-Lauffähigkeit auf Fold7.
