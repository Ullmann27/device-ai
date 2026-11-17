# Continuum Master-Integration – geprüfter Reviewstand, 24.10.2026

## Geltungsbereich und Branch
- Ausschließlich `Ullmann27/device-ai`; keinerlei Änderungen an Lumo.
- Ausgangspunkt: Sol M5 über PR #110, Ausgangs-SHA `f382aeda2612723ae3eacdf0fe96e87fbf10e04c`.
- Aktueller Reviewbranch: `chatgpt/continuum-master-integration-v19-20261024` (HEAD immer neu prüfen).
- Relevante Experimente: EXP-00, EXP-02, EXP-05, EXP-08, EXP-09, EXP-12 und CTX3-Forschung (OFF/SHADOW).
- Kein Merge nach `main`, kein Force-Push, keine Signierumgebung/privaten Gewichte/Kundendaten berührt.

## Integration – nachweisbarer Zuwachs
1. Release-Kandidat `opus/continuum-0.3.18-candidate-20261023` auf `b842d471` geprüft. Seine nachfolgenden fünf Commits verändern neun Dateien plus ein Kandidatendokument; sieben der neun Funktions-/Testdateien sind in der M5-Quelle bereits bytegleich. Die zwei divergierenden Verdrahtungstests wurden im M5-Strang weiterentwickelt. Daher **kein blindes Rückkopieren, kein zweites konkurrierendes System**.
2. Die vorhandene Verbindung Sol M4 → Opus CCC1 → Sol M5 → PR #110 ist eine durchgehende Git-Abstammung; der neue Branch steht direkt auf dem aktuelleren Integrationszweig.
3. Der Besitzer-Chat versteht nun ausdrücklich `Worker Projektplan` und `Worker Projektplan: <App-Wunsch>`. Innerhalb des Besitzerpakets öffnet er das vorhandene M5-Labor und gibt optional maximal 400 Zeichen als **unbestätigten Entwurf** weiter; ein nicht autorisierter externer Preview-Pfad öffnet nur den bestehenden Builder. Keine Umwandlung beliebiger Chat-Nachrichten in ausführbare Befehle.
4. Kontrollzeichen/mehrzeilige adressierte Worker-Aufträge werden vor Normalisierung verworfen. Die bisherige `Worker: ...`-Entwicklungsauftragsbehandlung bleibt erhalten.
5. Das M5-Labor übernimmt die Chat-Vorgabe ausschließlich ins Eingabefeld, niemals als bestätigten Slot, Plan, Build oder Installation. Der bestehende Plan-SHA-/Revisions-/SavedState-/Freigabeschutz aus PR #110 bleibt erhalten.
6. Kotlin-Routerregressionen, neuer Robolectric-Fall und statische Android-Verdrahtungsregression für diese Grenze hinzugefügt.

## Wiederholte Integrationskontrollen
- Direkte GitHub-Leseprüfung der geänderten Originaldateien und des Android-Manifestes: 15/15 strukturelle Quellinvarianten erfüllt. Das ist **kein** Compiler- oder Gerätetest.
- Eigenständiges, rein lesendes `scripts/continuum-v19-pin-audit.mjs`: liest die echte 22-Dateien-Freigabeliste aus dem unveränderten bestehenden Signierskript, prüft die Git-Blob-Identität und verhindert Pfad-Duplikate, Traversal, Symlink-Ersatz und fehlende Dateien. Normaler Aufruf endet bei Abweichung mit Exitcode 2; `--report-only` druckt lediglich Diagnosen und gibt **keine** Besitzerfreigabe.
- Fünf echte Node-22-Tests von `scripts/continuum-v19-pin-audit.test.mjs` im isolierten temporären Git-Testverzeichnis ausgeführt: **5/5 bestanden**, null Fehltests. GitHub-Blobs der beiden Dateien anschließend unabhängig mit `git hash-object` auf Bytegleichheit zum getesteten Original überprüft.
- Der zusätzliche Review-GitHub-Workflow nutzt Node, Backend, Gradle/JUnit/Robolectric, Android-Lint, separate `at.heinz.deviceai.preview`-Debug-APK und Signatur-/Hashnachweis nur als **Reviewpfad**, niemals als Besitzer-APK oder Main-Integration.

## Tatsächliche Release-Blocker
- Alle 22 alten v19-Signer-Quellpins gegen den GitHub-HEAD nachgelesen: **11/22 bitgleich, 11/22 nicht bitgleich**. Abweichende Quellpfade:
  `AgentActivity.kt`, `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileBuildPlan.kt`,
  `ContinuumMobileResultPolicy.kt`, `ContinuumMobileBuildResultReceiver.kt`,
  `ContinuumWorkerChatRouter.kt`, `ContinuumOwnedAppContractTest.kt`,
  `ContinuumWorkerChatRouterTest.kt`, `continuum-owned-app.mjs`,
  `continuum-owned-app.test.mjs` und `BootstrapBrain.kt`.
- Die Abweichungen sind **nicht** automatisch Fehler. Sie sind ein verpflichtender Anlass für unabhängigen Quellreview, vollständige grüne Tests auf exakt einem eingefrorenen Release-SHA und eine bewusste Besitzer-Freigabe der aktualisierten Fingerabdrücke. **Keine automatische Änderung** an `APPROVED_RELEASE_SOURCES` vorgenommen.
- Echter GitHub-Actions-Prüfstart am eigenen Branch: Workflow `Continuum V19 Isolated Integration QA`, Runs [40061340541](https://github.com/Ullmann27/device-ai/actions/runs/40061340541) und [40061349892](https://github.com/Ullmann27/device-ai/actions/runs/40061349892) scheiterten in wenigen Sekunden vor beobachtbaren Runner-Schritten. Die GitHub-Checks melden zwei Annotations, deren Text über den verfügbaren Repository-Connector nicht abgerufen werden konnte. **Ursache unbekannt**; der Build wurde deshalb nicht fälschlich als getestet markiert.
- Der aktuelle lokale Analysecontainer hat Node22, Git und Kotlin 1.9, jedoch **kein Gradle 8.11.1 und kein Android SDK36**. Deshalb konnten die vollständigen neuen Kotlin/Android-Tests, Lint und APK auf diesem Quellstand hier nicht ausgeführt werden. Ältere Sol-Zahlen bleiben Baselinebelege, nicht Ergebnisse dieser Commits.
- **Update nach ausdrücklicher Besitzerbestätigung des Render-Workspace:** `My Workspace` mit ID `tea-d7qe6dosfn5c73e2rnag` ist verbunden. Signierdienst `srv-dbaue53bc2fs73crkop0` (`device-ai-mentor-apk-preview`) hat `autoDeploy=no`, Quelle `astra/render-mentor-apk-builder-20261019` und den letzten erfolgreichen Live-Deploy `dep-dbdnrivf3r2c73d9hqp0` am 23.10.2026, HEAD `285639ba94d75cc427cb17b7421f12f48296ec1f`. Keine Environment-Secrets gelesen, kein Deployment getriggert.
- **Update aus öffentlichem HTTPS-Abruf:** `https://device-ai-mentor-apk-preview.onrender.com/BUILD-INFO.txt` lieferte die Metadaten der signierten Altversion `0.3.17/code18`, Paket `at.heinz.deviceai.mentorpaired`, Commit `285639ba94d75cc427cb17b7421f12f48296ec1f`, APK-SHA-256 `0ef181f011990cae1ec7b0b71c09fddf7ab0824dd132e80a702e37363a72e998`, Status `SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED`. Render-Logs belegen `BUILD SUCCESSFUL`, v2-Signatur, genau einen Signierer und dieselbe Paketidentität. Die unveränderliche APK-Adresse reagiert bei öffentlicher Inhaltsabfrage mit `content_too_large`, der Downloadbytevergleich durch unser Werkzeug war nicht möglich. Der **v19-Rollback-Gate ist deshalb noch nicht vollständig erneut bestanden**.
- Keine signierte 0.3.18 Besitzer-APK erzeugt, keine installierbare In-Place-Update-Datei behauptet und kein Chrome-APK-Link erfunden.

## Offene Forschung
- CTX3 unabhängig getestete Sprachhypothese weiter FAIL; ausschließlich OFF/SHADOW. Keine neue Gewichts- oder Antwortpromotion.
- Bereits vorhandenes LiteRT-LM/quantisierte Modell-Anbindung und CRE1/SWB1 zuerst mit echter Android-Integration und Speichergrenze nachweisen. Keine neue externe Abhängigkeit und kein ungemessener Modellwechsel im Release.
- Für künftige externe Technik erst nach vollständiger Integrationsabnahme aktuelle offizielle Dokumentation, Lizenzen und ARM64/Samsung-Laufzeit messen.

## Präzise nächste Ausführung
1. GH-Actions-Runner-/Check-Annotationsursache aus Administrations-/Runneroberfläche ermitteln; Node/Backend und echtes Android-SDK/Robolectric/Lint auf diesem Review-SHA durchführen, sämtliche XML-/TAP-Belege archivieren.
2. Danach zweiten unabhängigen Diff-, Git-SHA- und Signierquellreview der elf geänderten Pins auf einem eingefrorenen Integrations-HEAD, **erst dann** die bisherige Release-Freigabeliste mit der ausdrücklichen Besitzerentscheidung aktualisieren.
3. Auf dem genehmigten vorhandenen privaten Render-Signierservice (nur nach bestätigtem Workspace) das bestehende v19-Skript und Rollback-Gate unverändert ausführen. 0.3.17-APK inklusive alten SHA/Zertifikat wirklich archivieren; signierte 0.3.18 über exakte SHAs und `aapt`/`apksigner` nachweisen.
4. Nur falls erfolgreich: konkrete HTTPS-APK-Adresse aus veröffentlichtem BUILD-INFO erhalten, direkte Chrome-Erreichbarkeit und heruntergeladenen SHA-256 unabhängig prüfen. Gerätetest Fold7 (Installation als Update ohne Datenausfall, M5-Chat/Termux/CCC1/Forschungszyklus) separat dokumentieren.
5. Bei jedem nicht bestandenem Gate bewusst stoppen, weitere Reparatur nur auf einem neuen isolierten Commit. Keine Änderung an `main` und kein stiller Wechsel des Besitzerzertifikats.

## Rollback
Der Reviewbranch ist ein Nachfolger von PR #110. Für die Implementierung einzelne neue Commits revertieren (kein Force-Push/Reset) oder unveränderten Ausgangs-HEAD `f382aeda...` erneut auschecken. Keine Datenmigration oder Gewichtsänderung fand statt.

## Signier-Einstieg kontrolliert an v19 angeschlossen (nach Workspacebestätigung)

Der Render-Dienst zeigt als BuildCommand weiterhin `bash scripts/render-mentor-preview.sh`. Auf der bisherigen live Signierquelle führt dessen erster ausführbarer Aufruf **unbedingt** `render-mentor-owner-update-v18.sh` aus. Ein bloßes Deployment wäre deshalb kein v19-Build.

Auf **diesem isolierten Reviewbranch** wurde der vorhandene Einstieg so erweitert, dass ohne zusätzliche Variable weiterhin ausschließlich v18 gestartet wird. Nur wenn `CONTINUUM_OWNER_RELEASE_VERSION=19`, `CONTINUUM_RELEASE_SHA=<40-hex HEAD>` und ein gesondert ausdrücklich gesetzter `CONTINUUM_OWNER_V19_APPROVED_SHA` alle exakt übereinstimmen, kann er an das schon bestehende unveränderte `render-mentor-owner-update-v19.sh` übergeben. Fremde Versionswerte und fehlende Pins enden mit Exit 69. Es wurden weder diese Variablen auf Render gesetzt noch der private Signierbranch verändert. Der v19-Skripteigene 22-Pin-Gate, Rollback, Signatur- und SDK-Prüfungen bleiben zusätzlich unverändert.

`scripts/render-mentor-preview-dispatch.test.mjs` ergänzt vier Regressionen zu Default v18, v19 ohne Pin, falschem Pin und bestätigtem Pin/ungültigem Release. Ein **lokaler ausführbarer Bash-Fixture-Test der erreichbaren Entrypoint-Verzweigung**, mit Stub-Skripten statt echten APK-Builds, bestand fünf Cases: Default v18, fehlende v19-Freigabe, falsche SHA, korrekte SHA und ungültige Versionsauswahl. Das ist kein Lauf der kompletten GitHub-Repo-Testdatei, kein v19-Signer-Probelauf und keine vollständige Android-SDK-Regression.

**Weitere Sperre bleibt:** 11/22 bisherige Owner-Fingerabdrücke abweichend; volle unabhängige SDK-JUnit/Robolectric/Node/Backend-/Lint-Prüfungen auf neuestem HEAD nicht erfolgt; GitHub Actions-Läufe enden mit `failure` bevor Logs/Runner-Steps verfügbar werden. Eine tatsächliche Signierfreigabe muss getrennt erfolgen. v19-Download ausdrücklich **nicht** veröffentlicht.
