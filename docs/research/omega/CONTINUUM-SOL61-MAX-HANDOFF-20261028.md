# SOL 6.1 MAX – Continuum Entwicklungsübergabe, 28. Oktober 2026

**VERBINDLICHER AUFTRAG FÜR DEN NÄCHSTEN SOL-MAX-LAUF**

Lies vor jeder Aktion diesen vollständigen Handoff und die aktuellen GitHub-Diffs. Entwickle ausschließlich Device AI Continuum in Ullmann27/device-ai, Zielpaket at.heinz.deviceai.continuumlocal. Nicht die historische at.heinz.deviceai.mentorpaired und nicht eine Demo-App. Überprüfe den aktuellen HEAD, lege deinen eigenen isolierten Branch und später einen Draft-PR an; ändere, merge oder resette weder main noch bestehende Branches. Dokumentiere fehlerhafte Tests, echte Kotlin/Node/Android-Checks und blockierte Gerätefunktionen ausdrücklich. Arbeite mehrere aufeinanderfolgende große Meilensteine ab, bis der aktuelle sichere Auftrag abgeschlossen ist. Keine kostenpflichtigen KI-APIs/Cloud-CI, kein Zugang zu privaten Besitzerdateien, kein Passwort/Token/P12 im Chat, Code, Git oder Log. Keine unautorisierte APK-Installation oder Modellgewichts-/Lernspeicheränderung.

## Git-Stand und sichere Rebasierung

- **main, zuletzt geprüft:** 190f5eabaa60fc59292b2b80d3086aafd7743a80. Dort liegt noch die alte App-Konfiguration: at.heinz.deviceai, versionCode 3 (0.3.0). Nicht als neue Fold7-App ausliefern.
- **Neue App / PR #209:** 2b678da95a076ffc8dcf16a624eddc66934abbbd, beruht auf #205, #204, #203.
- **Bridges #210/#211** wurden ursprünglich von der alten main-Historie abgezweigt; ein History-Merge wäre falsch.
- **Konsolidierung PR #212:** c802e6f6657c7f4143cd79b0d2c9981a58343e14, Branch research/continuum-local-integrated-gate-20261028. Sechs getestete Dateien aus #211 wurden als identische Git-Blobs in den realen Haupt-App-Branch übernommen, ohne alte Historie. PR #212 hat 30/30 Node-Tests, bleibt Draft.
- **Aktuelle Sol-Handoff-Basis:** research/continuum-sol-max-handoff-readiness-20261028 auf PR #212. Den aktuellen HEAD vor Beginn erneut lesen. Zwei neue Node-Dateien führen fünf separate Release-/Import-/Lern-/Coding-/Belegprüfungen durch, zusätzliche 15/15 Tests. Zusammen mit #212 **45/45 Node-Tests PASS**, keine Fold7- oder Android-Buildtests.
- **Divergenz:** PR #208 und #209 divergieren bei f39fbe31a677d8c5020df11805b901abc4cedb73. Insbesondere Änderungen in LocalModelCatalog.kt und der Android-Buildroute sind nicht blind zusammenzuführen. PR #206 enthält auch einen **nicht angewandten** Stream-Patch und außerhalb der regulären Android-Testquellen liegende Testmethoden.

## Bereits heute bearbeitet; nie doppelt neu bauen

1. **PR #203:** Autorisiertes Gedächtnis und Hybrid-Retrieval in vorhandenen Antwort-/Reasoning-Pfad eingebunden; gezielte Kotlin-/JUnit-/Node-Evidence. Keine nachgewiesene allgemeine Steigerung der Gemma-Inferenzqualität.
2. **PR #204:** Offline-Runner für vollständige Gradle/JUnit/Lint-Regression mit Quell-SHA, externem Evidence-Verzeichnis, Fail-closed-Bericht. Der **heutige** finale HEAD ist nicht vollständig unter Android-Gradle getestet.
3. **PR #205:** Konflikterkennung für deutsche Verneinung in Forschungsquellen; begrenzte Quelltests, kein Fold7-Runtime-Befund.
4. **PR #206/#207/#208:** getrennte Import-Stream-Kandidaten, eingefrorenes Transferexperiment, fünf deterministische Prüfer; keine fünf neuen generativen Sprachmodelle.
5. **PR #209:** korrigiert irrtümlichen Pfad zur alten Mentor-Pair-App im Fold7-Update-Gate und eine Shell-Fehlerkaskade, die fehlgeschlagene Vollregression verdecken konnte. Sichtbare SWB1- und Forschungsfreigabestände; 19/19 eigentliche Node/Bash-Tests, keine native APK-Prüfung.
6. **PR #210/#211:** Free-only Fable/Astra und eigener Loopback-Coding-Adapter, 22/22 Node-Testfälle mit echtem Dateizugriff und echten Testläufen, jedoch simulierten Modellantworten.
7. **PR #212:** identische Bridges ohne Änderung an App oder CCC1 in Haupt-App-Kandidat konsolidiert; 30/30 Node-Tests.
8. **Neue Sol-Readiness:** 15 zusätzlich ausgeführte Node-Tests. Erster Lauf 14/15 wegen falsch formulierter Testbehauptung, korrigierter Lauf 15/15. Protokoll mit erster Fehlspur außerhalb Git, Gesamtkombination 45/45.

## Sechs offene technische Blocker

**B1 – Zielvariant beim Android-Test:** scripts/continuum-fusion-full-regression.sh führt Gradle mit -PdeviceAiMentorPreview=true aus, ohne -PcontinuumLocalStandalone=true. Damit ist eine grüne Mentor-Preview-JUnit-Suite KEINE geprüfte eigenständige at.heinz.deviceai.continuumlocal-Releasevariante. Gradle verlangt für continuumLocalStandalone eine eigene P12; die gehört niemals in gewöhnliche Unit-Tests. Erstelle einen nachweislich unsignierten/niemals installierbaren Standalone-QA-Testmodus oder einen gleichwertigen sicher geprüften Variantenweg. Bestehende Signierregeln nicht umgehen. Der neue Readiness-Prüfer markiert dies explizit als LEGACY_MENTOR_PREVIEW_TEST_VARIANT und STANDALONE_UNSIGNED_QA_VARIANT_NOT_VERIFIED.

**B2 – Gemma SAF/Lifecycle:** Im echten AgentActivity.kt werden Modellmetadaten bereits in onSaveInstanceState restauriert – nicht erneut implementieren. importModel(uri) öffnet SAF-Stream, LocalModelCatalog.import kopiert/verifiziert SHA-256 und inspiziert Modellfähigkeit; erst dann localRuntime.load. Die bisherige 120-s-Lifecycle-Meldung ist kein Beweis für eine kaputte Datei. onDestroy() ruft localAgentExecutor.shutdownNow(), obwohl Import auf demselben Executor laufen kann. Dies ist ein **reales Unterbrechungsrisiko**, aber NICHT als Fold7-Ursache bewiesen. Erst sauber instrumentieren, dann gegebenenfalls lifecycle-sicher reparieren.

**B3 – Native Gemma-Lücke:** Modell gemma4-e4b-it, ehemals vom Besitzer beobachtete Originalgröße 3.659.530.240 Bytes und SHA-256 0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0. Dies ist kein Beleg einer tatsächlich generierten Textantwort. Diagnosestufen in Reihenfolge: SAF_OPENED, COPY_COMPLETED, SHA256_VERIFIED, INSPECT_OK, RUNTIME_READY, FIRST_NATIVE_RESPONSE, COLD_REOPEN_READY. Ohne Fold7-Termux-/Gerätekontrolle als DEVICE_BLOCKED ausweisen. Kein Ersatzdownload, GGUF/Qwen-Ersatz oder model-external-Aufruf.

**B4 – Lernkontinuität:** Historisch ca. 331.570 SWB1-Updates und 538 Quellen in mentorpaired gegen ca. 4.000 SWB1-Updates und 532 Quellen in continuumlocal. Diese SWB1-Updates sind KEINE Gemma-4-Gewichtstrainingsevents. Die zwei Android-Sandboxes sind privat und getrennt. Bestehendes continuum-learning-parity-audit.mjs verwenden, keinen parallelen Checkpoint-Store schaffen; getrennte Hash-/Format-/Holdout-Provenienz prüfen und nie ohne explizite Besitzerfreigabe kopieren/löschen/überschreiben.

**B5 – Coding-Agent noch nicht real:** Die Fable-/Astra-Experiential-Free-Route und eigene Loopback-Bridges liefen nur gegen simulierte Antworten. Kein echtes Gratis-Kontingent, Fable-/Astra-/Gemma-Coding-Modell, Cloudfreiheit des Loopback-Servers oder private Git-Zugriff verifiziert. Nur öffentlich-synthetischer Versuch mit echten Toolaufrufen, Test und Patchvorschlag; kostenpflichtige Fallbacks und unzulässige Pfade immer verweigern. Erst nach unabhängigem Nachweis und expliziter Owner-Freigabe minimal privater Repo-Zugriff auf eigenem Branch und Draft-PR.

**B6 – Kein auslieferbarer Android-Stand:** Voller Gradle-/Lint-/JUnit-Lauf für diesen exakten Integrations-HEAD fehlt. JDK17/Android SDK36/Gradle8.11.1/native AAPT2 müssen auf einem tatsächlich geeigneten lokalen Host geprüft werden. Originaler Besitzer-P12/Signatur/VersionCode 27+ und Fold7-App-Lifecycle nicht verfügbar oder nicht verifiziert. Keine neue APK erzeugt, signiert oder installiert.

## Deine mindestens fünf größeren Arbeitspakete (in genau dieser Priorisierung)

M1: **Echte standalone QA-Variante.** B1 auf realem Gradle-Kotlin-DSL validieren und unsignierten Testmodus ergänzen. Separate JUnit-/Lint-/Quellnachweise, negative Paketidentitäts-, Signer- und Abbruchtests. Ohne JDK/SDK klarer Teststatus BLOCKED_TOOLCHAIN statt Behauptung eines Android-Builds.

M2: **Gemma 4 native Importspur.** Import-Lifecycle-Pfad read-only vollständig nachvollziehen, präzise Phasen mit Bytezahl, monotoner Zeit und kontrolliertem Abbruch integrieren, keine private URI/Bytes aufzeichnen. Unit-/Integrationstests inkl. Activity-Neuerstellung und künstlichem 3,66GB-Leseverhalten ohne echte Gigabyte-Kopie. Native Antwort nur mit realem Gerät behaupten.

M3: **Lernen und Langzeitgedächtnis.** Vorhandene SWB1-/CRE1-/Archivkomponenten mit vorhandenen Vergleichs- und Holdout-Gates verbinden. Alte/neue App isolieren; keine unbewiesene Gewichtsübernahme. Echte Benchmark- und Cold-Restart-Evidence.

M4: **Autonome Coding-Brücke kontrolliert anbinden.** PR #212s Free-only-/Loopback-Code zuerst nur an bestehendes CCC1-Review/IndependentVerifier anbinden, kein automatisches Schreiben oder Main-Merge. Nach echtem Modellaufruf Synth-Test mit read_file, Testausführung und minimalem Patch; danach Besitzerreview für private Rechte. Keine bezahlten Fallbacks.

M5: **Full release check und unabhängige Rückprüfung.** Divergierende #208-Änderungen einzeln gegen Haupt-App-Kandidat prüfen, anschließend komplette Regression und Signer-/Release-Gates auf exaktem neuen HEAD. Nur nach tatsächlicher Besitzerfreigabe der original signierten App: Draft-PR, kein Auto-Install.

M6 optional: **Qualitätsmessung statt Zählerfetisch.** Eingefrorenen deutschen lokalen Retrieval-/Coding-Benchmark mit Negationen, Widersprüchen, Ablehnung und Vergessen auf echten Modellen messen, inklusive Regressionen und Side Effects.

## Prüfungsbefehle, unverändert wiederholen

node --test scripts/continuum-coding-bridge.test.mjs scripts/continuum-selfhosted-coding.test.mjs scripts/continuum-integration-source-gate.test.mjs scripts/continuum-sol-max-readiness.test.mjs

node scripts/continuum-integration-source-gate.mjs .

node scripts/continuum-sol-max-readiness.mjs --audit-source .

Der letzte Befehl **muss aktuell mit exit 69 blockieren**, solange der korrekte Standalone-QA-Nachweis fehlt. Das ist erwartetes Fail-closed-Verhalten, kein bestandenes Release.

## Sol Abschluss- und Rücksetzpflicht

Erstelle einen präzisen Bericht IMPLEMENTIERT / TESTED / BLOCKED_DEVICE / OPEN, mit exact SHA, real gelaufenen Befehlen, ursprünglichen Fehlversuchen, Ergebnissen und noch offenen Punkten. Nur ein neuer isolierter Draft-PR, nicht mergen; bei fehlender Fähigkeit zur Änderung vollständige Übergabe ohne Erfolgsbehauptung. Rücksetzweg: neuen Draft schließen, keine produktiven Bestandteile und keine Besitzerdateien ändern.

**Hinweis:** Dieses Dokument ist die übergabefertige Aufgabenbasis. Sol 6.1 kann durch Chattext nicht automatisch als eigenes Modell gestartet oder im Hintergrund aufgerufen werden.


## Aktualisierung nach Handoff-Erstellung – M1-Implementierung als überprüfbarer Kandidat

Noch im selben isolierten Draft-Branch wurde B1 nun **im Quellcode vorbereitet**, nicht mehr nur dokumentiert:

- app/build.gradle.kts: neue opt-in Eigenschaft continuumLocalStandaloneQa; für QA gilt die Standalone-Haupt-App-Basiskennung, aber Debug hat absichtlich den anderen Paketnamen at.heinz.deviceai.continuumlocal.qa. Originaler Continuum- und alter Mentor-Signer werden im QA-Modus durch require verboten, und die originale Standalone-Signierung bleibt unverändert. QA und echte Standalone-Freigabe sind gegenseitig ausgeschlossen.
- Das Gradle-QA-Gate erlaubt ausschließlich testDebugUnitTest, lintDebug und compileDebugKotlin als Startaufträge. Assembly/Bundle/Release dürfen nicht als unsignierte Besitzer-APK ausgegeben werden.
- scripts/continuum-fusion-full-regression.sh setzt nun zusätzlich -PcontinuumLocalStandaloneQa=true und -PcontinuumStandaloneVersionCode=27; die bisherige Mentor-Feature-Flag bleibt nur für die vorhandene Funktionsauswahl. Der Runner ruft zudem die fünf neuen Node-Schutztestdateien auf. Diese Testvariante ist getrennt von dem original signierten Fold7-Releasemodus.
- scripts/continuum-integration-source-gate.mjs akzeptiert die historisch geprüfte reine Standalone- und die neue ausdrücklich QA-gebundene Gradle-Bedingung, ohne einen falschen Paketnamen zuzulassen.
- Neu: scripts/continuum-standalone-qa-variant.test.mjs mit sechs prüfenden Source-Contracts. 6/6 mit **synthetischen Gradle-Quellfragmenten** bestanden. Gegen die tatsächlich von GitHub gelesenen Gradle-/Release-Dateien wurden acht separate direkte Quellbedingungen erfolgreich geprüft. Bestehende 45/45 Node-Tests nach Gate-Anpassung erneut bestanden. Ein gemeinsamer lokaler **51/51**-Lauf enthielt für die sechs neuen Source-Contracts ausschließlich simulierte Gradle-Dateien – **kein** echter Android-Gradle-Test.

**NEUER STATUS VON M1:** SOURCE_IMPLEMENTED, SOURCE_CHECKED, GRADLE_COMPILE_NOT_RUN, ANDROID_JUNIT_NOT_RUN, FOLD7_NOT_RUN, RELEASE_BLOCKED. Sol muss diesen konkreten Kotlin-DSL-/AGP-Kandidaten in der wirklichen JDK17/SDK36/Gradle8.11.1-Umgebung kompilieren, testDebugUnitTest und lintDebug ausführen, mögliche Konfigurationsfehler beheben und das echte Ergebnis protokollieren. Bis dahin weder PR mergen noch ein Owner-Release auslösen. Die neuen Skripte allein belegen keine vollumfänglich geprüfte APK.


## NEUE PRIORITÄT AUF AUSDRÜCKLICHEN BESITZERWUNSCH – EINE EINZIGE APP

Der Besitzer berichtet, dass derzeit **vier** Continuum-bezogene Android-Installationen auf dem Fold7 vorhanden sind; er will langfristig **nur die Haupt-App** \`at.heinz.deviceai.continuumlocal\`. Er empfindet die aktuelle App als passiv (kein sichtbares Lernen, keine nachweisbare Interaktion). Dies ersetzt die bisherige Priorität, weitere isolierte Neben-Apps zu erstellen: **Keine fünfte Demo-/Preview-/QA-Installation.** QA nur als niemals zu installierende Test-Konfiguration. Alte Installationen NICHT löschen oder App-Daten entfernen, bis alle sicher verfügbaren Exporte und Besitzerfakten kontrolliert übernommen sind.

Auf dem von PR #213 abgezweigten Branch \`research/continuum-sol-preflight-and-native-evidence-20261028\` wurden nun folgende Arbeiten zusätzlich durchgeführt:
- Native Gemma-Importphasen als begrenzter Kotlin-Tracker mit progress callbacks in LocalModelCatalog und UI-Fehleranzeige; neun Kotlin-Testmethoden hinzugefügt (separate Kotlin-/JVM-Probe erfolgreich, Android-JUnit fehlt).
- Bisheriger SWB1-Alt/Neu-Holdout-Vergleich verlangt identische SHA-256 des eingefrorenen Benchmarks; drei neue Node-Testfälle.
- Read-only CCC1-Übergangsprüfer mit 21 negativen/positiven Node-Testfällen (synthetischer Patch niemals automatisch schreiben).
- Exact source SHA / protected branch / JDK17 / SDK36 / Gradle8.11.1 / AAPT2 QA-Host-Preflight mit 15 Node-Testfällen; ursprünglicher Fehler im Clean-Checkout-Test wurde repariert.
- \`AgentActivity.showLearningCenter()\` zeigt nun als ersten Eintrag \`EINE HAUPT-APP · LERNSTAND UND DATENÜBERNAHME\`, mit echten SWB1-/Experience-/Quellen-/Forschungs-/Gemma-Zuständen und expliziten vorhandenen Owner-Import-, Trainingsmonitor-, Offline-Lern-, Modell- und Gemma-Direkt-Testaktionen.
- \`ContinuumKnowledgeArchive.importZip\` wurde von direkter Einzelübernahme auf vollständige, begrenzte Validierung ALLER Einträge vor der ersten Schreiboperation geändert; neue Tests für beschädigte/unsichere spätere ZIP-Einträge und beschädigte bestehende Kollisionsdatei. **Diese neuen Kotlin-Archivtests noch nicht auf Android/Gradle ausgeführt.**
- Die Archiv-UI zählt ausdrücklich echte neue Quellen (Differenz vorher/nachher) statt bloß gelesene ZIP-Dateien.
- Bisheriger SHA-gebundener Regression-Runner wird um SWB1-Parität, CCC1-Review und Host-Preflight-Tests erweitert.

Die komplette Datenübernahme-Architektur und die Schritt-für-Schritt-Anleitung stehen in \`docs/research/omega/CONTINUUM-ONE-APP-MIGRATION-20261028.md\`. Bereits existierende, nie unnötig neu bauen: SWB1-Export 32.960 Bytes, ownerbestätigter Gewichtsimport mit Holdout, wissensarchiv-v1 ZIP-Import/Export. **Fehlt weiterhin:** geprüfter Transfer aller persönlichen CRE1-/OwnerFact-/Experience-/Widerrufs-Speicher und die vier echten APK-Identitäten/Versionen. Keine Daten aus privater Sandbox ohne Export, Backup und Besitzerfreigabe behaupten. Es ist weder eine neue APK gebaut noch eine auf dem Fold7 installierte App verändert worden.

Unverändert obligatorisch für Sol: zuerst neue Kotlin-/Gradle-/JUnit-Änderungen tatsächlich kompilieren, dann Import-SAF/Modellreplikation und ein echter nativer Gemma-4-Direkt-Test auf Besitzergerät, unabhängige SWB1-Holdouts, CCC1 nur nach separat erteilter Repo-Berechtigung und ausschließlich Draft-PR. Nicht nach kosmetischen Zählern behaupten, die App lerne. Release erst nach Original-P12 und SHA-gepinnter Full Regression. Bei fehlendem Gerätezugriff ehrlich BLOCKED_DEVICE melden.
