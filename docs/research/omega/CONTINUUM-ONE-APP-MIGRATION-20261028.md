# Device AI Continuum — EIN-APP-KONSOLIDIERUNG UND DATENUMZUG

Datum: 28. Oktober 2026. Produkt: **eine** Haupt-App at.heinz.deviceai.continuumlocal auf Fold7. Kein neues App-Paket installieren, keine alte App ohne kontrollierten Datenvergleich löschen. Dieses Dokument gehört zum aktuellen eigenständigen Research-Draft auf PR #213.

## Warum sich die bisherige Haupt-App leer anfühlt

Die alte und neue App haben **verschiedene Android-App-IDs** und daher getrennte private Sandboxes. Ein SWB1-Zähler von ca. 331.570 im alten mentorpaired gegen etwa 4.000 in continuumlocal bedeutet weder 331.570 Gemma-Parameter-Trainingsvorgänge noch automatisch einen höheren Erkenntnisstand. Das Wissensarchiv, bestätigte Besitzerfakten, SWB1-Gewichte, CRE1-Experimente, Gemma-4-Originalmodell, Forschungsjobs und das optionale Coding/CCC1-System sind **verschiedene** Ressourcen.

Die neue App hat im bestehenden \`ContinuumLearningCore\` bereits den Trainingsmonitor, Trainings-Audits, Modellevaluierung, offline öffentliche Übungspakete und eine manuell freizugebende Forschungsplanung. Solange Gemma 4 E4B nicht als native Inferenz nachgewiesen ist, kann sie nicht als zuverlässiger lokaler Chat-Mitarbeiter erscheinen. Öffentliche Forschung in der neuen App wurde zuvor als AUS berichtet; kein automatisches Einschalten ohne Benutzerfreigabe.

## Echte bereits existierende Transferformate

| Ressourcenklasse | Bereits vorhandene App-Quellen | Korrekte sichere Übernahme |
|---|---|---|
| SWB1 Subword-Encoder | Lernzentrum: SWB1 EIGENE GEWICHTE EXPORTIEREN, 32.960-Byte-Modellsnapshot; GEWICHTE IMPORTIEREN über Android SAF | Alte App muss den Export im installierten Build tatsächlich anbieten. Neue App importiert mit \`ContinuumLearningCore.importSnapshot\` nach Modellformat- und Holdout-Vergleich sowie Schutz persönlicher Intent-Lektionen. Ein nur höherer Updatezähler ist **kein** automatischer Übernahmegrund. |
| Öffentliche Forschungsquellen | Lernzentrum: WISSENSARCHIV BACKUP/WIEDERHERSTELLEN | ZIP aus der alten App über Android-Dateidialog speichern; in der neuen App ZIP importieren. Kein Auslesen anderer privater Sandboxes. |
| Modell Gemma 4 E4B | Originale LiteRT-LM \`.litertlm\`-Datei, zuvor 3.659.530.240 Bytes mit publiziertem SHA; Quellquelle muss vorhanden sein | Wenn Originaldatei im gemeinsamen Gerätespeicher noch verfügbar ist, in neuer App prüfen/auswählen. Nicht über GitHub hochladen, keine unnötige doppelte Kopie herunterladen, kein GGUF/Qwen-Ersatz. Bereits privat importierte Modellbytes der alten App sind nicht direkt zugänglich. |
| Bestätigte Besitzerfakten, CRE1, Langzeitkontext, Experience-Protokolle | \`ContinuumLearningCore\`, \`LearningExperienceStore\` und weitere private Stores | **NICHT** in SWB1-ZIP/Modellgewichtssnapshot enthalten; bestehender vollständiger Exportpfad für alle diese Daten ist nicht nachgewiesen. Format, Eigentümerfreigabe, Schema-Versionen, Revocations und Widerrufe sind zuerst zu analysieren. Nie heimlich aus der alten App extrahieren oder persönliche Fakten durch unbestätigte Quellen ersetzen. |
| Forschungsfreigaben und Android-Jobs | Separate SharedPreferences und JobScheduler je App-ID | Benutzer kontrolliert gewünschte Forschungsberechtigungen in der neuen Haupt-App selbst; niemals automatisch aus altem Paket übertragen oder Cloud-Verbindungen einschalten. |
| Entwickler-Code und Coding-Worker | Private GitHub-Branches/PRs #203–#213, CCC1, Bridge | Code direkt im **einen** Haupt-App-Quellzweig integrieren, ohne separate installierbare Demos. Externe Modelle optional, gebührenpflichtige Fallbacks gesperrt. |

## Was in diesem zusätzlichen Research-Branch implementiert ist

1. Im vorhandenen \`AgentActivity.showLearningCenter()\` steht **EINE HAUPT-APP · LERNSTAND UND DATENÜBERNAHME** an erster Stelle, ohne eigene APK/Launcher-Anwendung.
2. Ein zentraler Dialog zeigt **aktuelle** SWB1-Updates, Experience-Zahl, Quellenanzahl, öffentlichen Forschungsstatus/Android-Job und nachweisbar geladenen Gemma-Status.
3. Nur nach Besitzeraktion werden vorhandene sichere Funktionen aufgerufen: SWB1-Import, Archivimport/-export, Gewichts-Export, Trainingsmonitor, isolierter Gemma-Direkt-Test, Modellverwaltung, Holdout-/Audit-Ansicht, Offline-Lernpaket und Forschungsverwalter.
4. Der Archivimport nennt die **wirklich neu übernommenen Quellen** (Differenz vorher/nachher), nicht irreführend die Gesamtzahl aller ZIP-Einträge.
5. \`ContinuumKnowledgeArchive.importZip\` validiert nun **alle** ZIP-Einträge vor dem ersten Schreibvorgang. Unzulässige spätere Dateien, beschädigte Datensätze, unerlaubte Domains, alte Kollisionsdateien, auffällige Inhalte und ein >64 MiB expandierter Datensatz werden vor dem Commit abgewiesen. Zwei zusätzliche Tests für defekte/unsichere späte Einträge und ein Test für eine beschädigte bestehende Kollisionsdatei wurden ergänzt. Diese neuen Android-/Kotlin-Testmethoden müssen noch auf echter Gradle/JVM-Testumgebung ausgeführt werden. Ein späterer physischer Schreibfehler bleibt theoretisch als Teilimport möglich; bestehende Quellen werden nie gelöscht.

## Ohne weitere App-Installation: Besitzer-geführter Transfer

- **Sicherung:** In der alten tatsächlich installierten App nach \`Lernzentrum → SWB1 · EIGENE GEWICHTE EXPORTIEREN\` und \`Wissensarchiv → BACKUP EXPORTIEREN\` suchen. Wenn die alte installierte Version diese Funktionen nicht zeigt, keine erfundenen Menüpunkte behaupten: Screenshots und App-Version für sicheren alternativen Export ermitteln. Es dürfen keine privaten Android-Sandbox-Grenzen umgangen werden.
- **Prüfung:** Wenn die zwei Dateien vorhanden sind, Dateigröße, SHA-256, ZIP-Entries und Schema kontrollieren. Der Nutzer kann Dateien zur Prüfung bewusst bereitstellen, private Erinnerungen dürfen nicht ohne gesonderte Zustimmung cloudseitig analysiert werden. Die Dateien niemals ungefragt in GitHub committen.
- **Übernahme:** In der **neuen** Haupt-App \`Lernzentrum → EINE HAUPT-APP\` öffnen, erst Wissensarchiv-ZIP, dann SWB1-Backup prüfen und importieren. Erfolg und neue echte Quellenanzahl kontrollieren. Abgelehnte Gewichte sind ein Schutz, kein Fehler, der mit Force überschrieben wird.
- **Modell und Denken:** Original-Gemma-Datei wiederverwenden, Modell laden, direkten Werkzeug-freien nativen Test ausführen und erst danach Chat/Reasoning-Sitzungen prüfen.
- **Lernen sichtbar:** Forschungsstatus, Android-Job, letzten Trainings-Audit, den SWB1-Frozen-Holdout und tatsächlich akzeptierte Stufen protokollieren. No-op/abgelehnte Trainingsrunden als solche ausgeben.
- **Endkontrolle:** Alte und neue Archive, Besitzerfakten, SWB1-Hashes/Updates, Gemma-Antwort, Erinnerungs-Widerrufe und den original signierten Release durch Besitzerfreigabe vergleichen.
- **Erst dann** kann der Besitzer selbst entscheiden, welche der vier alten Installationen deinstalliert werden sollen. Keine automatische Deinstallation, kein Datenlöschen.

## Nicht gelöste Blocker – darf Sol 6.1 Max nicht übersehen

- Kein direkter Zugriff auf die vier tatsächlich installierten APKs, ihre jeweiligen App-IDs, Originalsignaturen, den Fold7-Speicher oder die privaten Lerngewichte. Weder Menge noch Kompatibilität der realen exportierten Daten bislang geprüft.
- Bestehende alte Installationsversion könnte die oben genannten Exportfunktionen nicht enthalten; kein Update/Neusignieren dieser App ohne Besitzer-Abgleich.
- Gradle/JDK17/SDK36/AAPT2 und Original-P12 des Fold7 stehen hier nicht zur Verfügung; keine native Android-Build- oder Installationsbestätigung.
- Komplette CRE1/OwnerFact/Agent-Experience-Migration und spätere sichere Entfernung der alten Apps erfordern ein getrenntes, schema-strenges Verfahren; nicht automatisch aus Archiv/SWB1 ableiten.
- \`ContinuumKnowledgeArchive.importZip\` hat neue Quellcodeänderungen, die erst durch echte Android/JUnit-Regression gegen bestehenden Archiv-Testbestand validiert werden müssen; bis dahin nicht produktiv mergen.
- \`showOneAppMigrationCenter\` ist in GitHub-Quellen implementiert, nicht auf dem Fold7 verfügbar, bis eine echte originale signierte neue Haupt-App freigegeben und gebaut ist.

## Sol 6.1 Max – Auftrag ohne weitere Rückfragen

1. Hole den aktuellen HEAD dieses neuen Draft-PRs, vergleiche gegen #213, prüfe alle geänderten Kotlin-Dateien mit Gradle-Kotlin-DSL, echten JUnit-Tests und Lint.
2. Bei echten Fehlern im Archivvorlauf, Import-Lifecycle oder Dialog UI: repariere im eigenen isolierten Branch, teste reproduzierbar, und lege neuen editierbaren Draft-PR vor. Nicht in main, nicht in die alte installierte App schreiben.
3. Entwickle als nächsten **echten** Migration-Meilenstein eine geprüfte, explizite Export-/Importspur für Besitzerfakten/Experience-Widerrufe, kompatibel zum bereits installierten Alt-Build. Falls alte APK Export nicht zulässt, belege die Blockade und frage ausschließlich gezielt nach bereitstellbaren Sicherungen.
4. Verbinde Gemma-4-Direkt-Test, Trainingsmonitor, Forschungsjobs und nachgewiesene Trainingserfolge mit dem zentralen Haupt-App-Dashboard. Keine kosmetischen Zähler oder behauptete Inferenz.
5. Kontrolliere Release-Signatur, Paketnamen und Versionscode, sichere bestehende Installationen und Lernstände; APK nur für \`at.heinz.deviceai.continuumlocal\`, kein fünftes Testpaket.

**Sicherheit:** Protokolliere nur nicht geheime Code-SHA, Quellzahlen, Zeit/Phasen und Testresultate. Persönliche Texte, private Checkpoints, OAuth, P12, Modellbytes und Geheimnisse niemals in GitHub oder Logs.


## Neueste UI- und Testkorrekturen im selben PR

Die Modellverwaltung zeigt jetzt bei laufendem Import die tatsächlich erreichte Importphase, \`copiedBytes\` in MiB und monoton gemessene Sekunden. Der Callback aktualisiert die Oberfläche nur bei Phasenwechsel oder pro 64-MiB-Kopierfortschritt; keine Dauerflut von UI-Events. Der starre 120-Sekunden-Importtimeout wurde in einen Fortschritts-Watchdog verwandelt: sichtbarer Fortschritt startet die Prüfperiode neu, fehlender Fortschritt wird als **Warnung** protokolliert statt den Import sofort als fehlgeschlagen auszuweisen. Dies beseitigt noch nicht das mögliche \`AgentActivity.onDestroy → localAgentExecutor.shutdownNow()\`-Risiko; für einen lebenszyklusfesten Großimport sind echte Android-Reproduktions- und Reparaturtests nötig.

Die Haupt-App-Umzugszentrale bietet außerdem **explizit einen isolierten nativen Gemma-Direkttest** ohne Gerätewerkzeuge. Dessen Testantwort muss tatsächlich vom laufenden LiteRT-Modell kommen; die UI darf aus Modell-Hash, SWB1-Zähler oder Quellenzahl keine native Sprachgenerierung ableiten.

\`scripts/continuum-one-app-contract.test.mjs\` enthält nun 19 automatisch auszuführende Node-Quellverträge. Die Live-GitHub-Quelltexte wurden zusätzlich unabhängig gegen 25 direkt formulierte statische Quellbedingungen geprüft (25/25 PASS). Die vollständige **81/81 Node-Kernregression** wurde danach lokal nochmals auf den bereits verfügbaren isolierten Modulen ausgeführt (0 Fehlversuche); sie ersetzt weder die neuen 19 One-App-Node-Vertragstests noch die aktuellen Android-Gradle/JUnit-Tests.

**Achtung:** Sol darf für die reale Archivmigration weder den bisherigen 81er-Kernlauf noch die statische Live-Prüfung als bestandene Kotlin-JUnit-Regressionssuite ausgeben. Neue Kotlin-Tests: 3 Tests (später defekte ZIP-Quelle, spätere unsichere ZIP-Quelle, beschädigte bestehende Kollision). Zusätzlich existieren die 7 ursprünglichen Archivtests; Vollprüfung ist auf JDK17/SDK36/Gradle8.11.1 auszuführen, vor APK-Merge verpflichtend.
