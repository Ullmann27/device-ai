# Code60 · gemeinsame APK-Lieferlinie · 16.11.2026

Status: **PORTIERT / TEILWEISE VERIFIZIERT / NICHT GERÄTEBESTÄTIGT**.

## Quelle und Auftrag

Der Besitzer verlangt die APK mit allen Neuerungen. Diese Lieferung führt die drei aktuellen Code60-Linien in den bestehenden kanonischen Branch `integrate/continuum-command03-android-20261115` (PR851) zusammen. Integrationsverantwortlich für diesen Checkpoint: Astra, APK-Lieferung 16.11.2026. Keine Änderung an main, Paket, Originalzertifikat oder geschützter Umgebung.

- Kanonische Quelle / einfacher Besitzer-Build: `e7178c48c9d37200eb6ea1ab8db2d706b89ced14`.
- Full-QA-Reparaturen PR857: `41b764c214e41e610641af3bc74bc57d778c6e99`.
- Native HTML-Bedienung PR854: `485b42593384cef23cd5869a0b1857971a810741`.
- Alle drei werden Eltern des Integrationscommits. Der bestehende einfache Workflow wird unverändert übernommen.

## Integrierter Effekt

Original-HTML, echte Computerbilder und Avatar-Sprachbewegung bleiben erhalten. Hinzu kommen die vollständigen PR854-Anschlüsse: scoped Eingabe/Diktat, Erhalt aktiver Computer-Sitzungen, getrennte Haupt-/Helferansichten mit Entwurfswiederherstellung, sichtbarer Fokus, Mikrofon-Langdruck, Quellenkarten und PDF-Entwurfsaktionen. View-/Sitzungs-/Revisionsprüfungen gegen veraltete Rückrufe bleiben aktiv.

Die automatisch zusammengeführte AgentActivity entspricht PR854 plus genau den zwei PR857-Korrekturen zum Löschen einer veralteten Gedächtnisszene beim Gesprächs-/Startseitenwechsel. Konflikte in klassischen UI-Fixtures wurden zugunsten der expliziten PR857-Präferenzeinstellung gelöst; Testassertionen wurden nicht entfernt.

Zwei zusätzliche, durch den tatsächlichen Emulatorlauf belegte Probe-Korrekturen:

1. `ContinuumNativeHologramProbe.copyStage` übergibt neben den neun Float-Werten auch das vorhandene Boolean `cameraFlightEnabled` an die aktuelle Kotlin-copy-Signatur. Ursache des früheren Abbruchs: `NoSuchMethodException`.
2. Der Offline-Web-Probe kehrt über den tatsächlichen zugänglichen Navigationsnamen `Gespräch` zurück. Die finale XML des früheren Laufs enthält genau diesen klickbaren Eintrag. Validator und synthetische Testdaten verwenden denselben Namen.

## Frische lokale Nachweise vor dem Integrationscommit

- DOM: **45 PASS, 0 FAIL, 0 SKIP** mit `node --test tools/command03-native-qa/native-bridge-behavior.test.mjs`; jsdom über `COMMAND03_DOM_MODULE`. Der optionale frische Kotlin-Interop-Paketlauf ist darin nicht enthalten.
- Originalquelle: `node tools/command03-native-qa/check-visual-source.mjs` **PASS**; 35 Originalassets, 15.864.219 Bytes unverändert.
- Offline-Web-Prüflogik: `python -B -m unittest discover -s tools/code55-apk -p test_native_web_offline.py -v` **24 PASS**.
- Diffprüfung der aktiven Code-/Workflowpfade bestanden. Historische importierte Fehlbelege bleiben unverändert.
- Android-Kompilierung, volle Tests und neue APK auf dieser kombinierten Quelle: **AUSSTEHEND** zum Zeitpunkt dieses Quell-Checkpoints.

## Frühere externe Nachweise – keine Abnahme dieser kombinierten Quelle

Auf `41b764c214e41e610641af3bc74bc57d778c6e99` bestand Kontextlauf [43918655114](https://github.com/Ullmann27/device-ai/actions/runs/43918655114). Im vollständigen Lauf [43918655087](https://github.com/Ullmann27/device-ai/actions/runs/43918655087) bestanden unsigned ARM64, Node/Backend, Renderer und Lint. Android: **5.914 Fälle, 11 FAIL, 2 SKIP**; Native-Runtime ebenfalls fehlgeschlagen. Diese elf klassischen Layout-/Draw-/Dialogfälle sind hier nicht als repariert ausgewiesen. Die beiden oben korrigierten nativen Probe-Pfade benötigen einen echten neuen Emulatorlauf; der separate Accessibility-Snapshot-Abbruch im Routine-Probe ist noch offen.

## Konkreter Lieferblocker und nächster Schritt

Der bestehende einfache Workflow baut Code60/0.3.59, ARM64, Paket `at.heinz.deviceai.continuumlocal`; die signierte Datei ist nur ein Besitzer-Testrelease, keine vollständige Geräteabnahme. Bei Push startet dieser bestehende APK-Bau. Ergebnis, Quell-SHA und tatsächliche APK anschließend zurücklesen.

Der Signierjob [133412667511](https://github.com/Ullmann27/device-ai/actions/runs/43894363899/job/133412667511) wurde bereits vor seinem ersten Schritt abgewiesen: `Branch "integrate/continuum-command03-android-20261115" is not allowed to deploy to continuum-original-signing due to environment protection rules.`

Ein berechtigter Besitzer/Administrator muss den exakten kanonischen Branch in der bestehenden geschützten Umgebung erlauben und erforderliche Review-Freigaben beibehalten. Erst danach den konkreten fehlgeschlagenen Signierjob wiederholen. Keine Umgehung über einen anderen Zweig, kein Export der Originalschlüssel, kein Austausch durch einen Debug-Schlüssel. Erwartetes Originalzertifikat SHA256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

**Rollback:** Bei nachgewiesener Regression einen regulären Revert auf der kanonischen Linie erstellen; letzter vorheriger kanonischer Stand ist `e7178c48c9d37200eb6ea1ab8db2d706b89ced14`. Keine Zweige löschen oder per Force-Push zurücksetzen. Der publizierte Code59 bleibt unabhängig bestehen.
