# Command03: echte Computerbilder und native Sprachbewegung

## Start-Checkpoint – 16.11.2026

Status: **WIP / UNVERIFIED**. Dieser erste Checkpoint dokumentiert den beauftragten nächsten Anschluss, noch keinen Funktionsnachweis.

- Ausgangspunkt: `fbdb612f31158075bdb746d4cb267ba671179fa4`.
- Gemeinsamer Zielbranch: `integrate/continuum-command03-android-20261115`, PR #851.
- Arbeitsbranch: `feat/command03-live-frame-viseme-20261116`.
- Integrationsverantwortlicher: Root; drei abgestimmte Teilaufgaben für native Anbindung, HTML-Adapter und unabhängige Prüfung.

## Beauftragter sichtbarer Effekt

1. Der vorhandene native Computer-Bildstrom erscheint im projizierten Arbeitsfenster der freigegebenen Original-HTML-Oberfläche.
2. Die tatsächlichen Sprach-/Viseme-Daten bewegen den Mund des Originalavatars im vorhandenen Sprachablauf.

Vor Neuerstellung werden vorhandene Aufrufketten und öffentliche Szenen-APIs geprüft. Es entstehen kein weiterer Capture-Pfad, kein zweiter Orchestrator und keine Demo-Sprachbewegung. Die 35 Originalassets und der bereits getestete Eingabe-/PDF-/Klassikrückweg bleiben Gegenstand der bestehenden Quell- und Regressionstests.

## Abnahmekriterien

- Echte bestehende Produzenten erreichen über begrenzte native Transporte den HTML-Verbraucher.
- Verspätete Daten werden bei Sitzungswechsel, Beenden und Lifecycle-Wechseln verworfen; Screenshots werden nicht in Logs oder Einstellungen gespeichert.
- Fokusprüfungen belegen Transport, Sitzungsschutz und Lebenszyklus; bestehende HTML-Verträge bleiben gültig.
- Regulärer Kotlin-Build und gezielte Android-/DOM-Prüfungen auf einem exakt gesicherten SHA, anschließend normaler unsigned APK-Bau, soweit lokale Ressourcen ausreichen.
- Erfolgreiche Änderungen werden auf die genannte gemeinsame Integrationslinie zurückgeführt; keine isolierte Forschungsinsel.

## Nach Quellenprüfung festgelegte Anschlüsse

- Computer: `OwnerComputerSessionController.setFeed` → derselbe `AndroidLiveComputer.feed` → begrenzter HTML-Präsentationsadapter. Die vorhandene `LiveMonitorSubmission` und der bestehende Dispatch-Guard bleiben maßgeblich; das reine Versenden oder Decodieren eines Bildes erlaubt keine Aktion.
- Darstellung: exakte Sitzungs-/Generations-/Bildbestätigung aus der lokalen Seite, danach Android-`WebView.postVisualStateCallback` und erst das folgende sichtbare `onDraw`. Android dokumentiert den Callback als Zeichnungsbereitschaft; er ist selbst noch kein Draw: [offizieller WebView-Vertrag](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback)). Quellbindung und ursprüngliches Bildalter werden danach nochmals geprüft.
- Sprache: vorhandener `AvatarDirector` / `AvatarPoseReducer` → öffentliches `ContinuumAvatar.setSpeechFrame` und `clearSpeech`. Die originalen 15 Mundformen sind bereits kompatibel. Gemessene PCM-Hüllkurven, TTS-Wortbereiche und der bereits vorhandene Texttakt-Fallback bleiben unterscheidbar; Wortformen sind keine phonemgenaue Audioanalyse.
- Lifecycle: Der bereits erlaubte sichtbare Computerbetrieb im geteilten Bildschirm bleibt auch beim Fokuswechsel zur Ziel-App erhalten. Tatsächlich verborgene Fenster, `onStop`, Gesprächswechsel, Stopp und Schließen müssen Bilder/Renderbestätigungen entwerten. Sprachausgabe behält ihren eigenen Abbruchpfad.
- Betroffene Architekturverträge: EXP-07 (unabhängige Negativ-/Verifikationsprüfung), EXP-11 (bestehender Controller und bestehende Autorisierung). Kein neuer Experimentstatus und kein EXP-00-Gerätenachweis werden behauptet.

## Nachweisgrenzen

Zwischenstand vor dem ersten Android-Compile: Native Produzenten und HTML-Verbraucher sind im Arbeitszweig angeschlossen; die Kotlin-Kompilierung und Android-Ausführung sind noch **NOT_RUN**.

- Ausgangslauf: 16 bestehende DOM-Tests bestanden, 9 neue Fälle scheiterten konkret an den fehlenden Computer-/Speech-Einstiegspunkten.
- Nach Adapteränderung: 27/27 DOM-Tests bestanden, keine Skips. Enthalten ist die öffentliche API der unveränderten `avatar3d.js` im PNG-Fallback; Bilddecoder und Layout bleiben Test-Seams.
- Originalvertrag: 35 Assets / 15.864.219 Bytes unverändert; Syntax und Diff-Prüfung bestanden.
- Der finale zusätzliche Interop-Test muss mit `COMMAND03_REQUIRE_NATIVE_PACKET=1` das tatsächlich von der frischen Stage-UI-Prüfung erzeugte synthetische JSON-Paket lesen. Ohne Paket scheitert dieser Gate ausdrücklich. Allein handgeschriebene JS-Fixtures belegen keinen Kotlin-Wirevertrag.

PORTIERT: Quellanschlüsse im Arbeitszweig. VERIFIZIERT: bisher nur obige DOM-/Quellprüfungen. GERÄTEBESTÄTIGT: NOT_RUN. Dieser Zwischenstand ist kein fertiger Android-Nachweis.

Der vorherige Stand und seine 82 lokalen Fokusprüfungen sind in `COMMAND03-HTML-VALIDATION-HANDOFF.md` dokumentiert. Sie ersetzen keinen frischen Nachweis dieser neuen Datenpfade. Android-WebView/GPU, echte Sprachlatenz, Berechtigungsdialoge und Faltverhalten auf Hardware sind gesonderte Geräteprüfungen.

## Android-Zwischenstand am 16.11.2026

Geprüfter Quellstand: `32f6d04e88a9e7373147a83499ca8a984d10756c`. Produktionsunterbaum `app/src/main`: `f46c45771386422fa65776ff66a1609ab7f4c260`.

- Regulärer `:app:compileDebugKotlin`: **PASS_LOCAL**, 30,881 Sekunden. Tatsächliche Produktionsquellen, ursprüngliche Buildpfade, Gradle-Heap 768 MiB und Metaspace 256 MiB; keine Compilerfehler.
- Erster fokussierter Android-UI-Lauf: Kotlin-Testkompilierung abgeschlossen, anschließend Gradle-Daemon verschwunden. **Keine frischen abgeschlossenen JUnit-Ergebnisse.** Eine Speicherursache ist nicht nachgewiesen; dieser Lauf zählt nicht als Testnachweis.
- Zweiter Lauf mit denselben Quellen und regulär bereits kompilierten Klassen: **22 Tests, 19 bestanden, 3 fehlgeschlagen, keine Skips**. Offen sind das Präsentationsaktivitätsflag im sichtbaren Split-Screen sowie zwei Fälle, die noch keinen WebView-Visual-Callback einreihen. Die Ursache wird durch positive Vorbedingungen und konkrete Sichtbarkeits-/Fristdiagnosen geprüft; Produktionsgates und Assertions bleiben erhalten.
- Frisch aus dem zweiten Android-Lauf serialisiertes synthetisches Stage-Paket: SHA-256 `190f34b54949bc1707b35f1957cf8532567d3be1b7af449c08408ed135ce7657`. Damit besteht der verpflichtend aktivierte DOM-Interop-Lauf **28/28, ohne Skips**. Dies beweist den Kotlin-JSON-/JS-Vertrag, keine physische Geräteaufnahme.
- Die 35 Originalassets / 15.864.219 Bytes bleiben unverändert. Noch kein neuer APK-Bau für diese Medienanbindung und kein Gerätetest.

Der folgende Diagnose-Checkpoint ergänzt einen ausdrücklich als **Kompilierung ohne Testausführung** ausgewiesenen Modus `ui-compile`. Er führt die echte `compileDebugUnitTestKotlin`-Aufgabe mit denselben fokussierten physischen Testquellen und SHA-Prüfungen aus. Erst ein separater `ui-tests`-Lauf darf frische JUnit-Ergebnisse ausweisen. So müssen Compiler und Robolectric nicht im selben Gradle-Prozess resident bleiben. Die lokale QA-Allowlist erhält ausschließlich diese Compile-Aufgabe; APK-/Signaturgates bleiben unberührt.

PORTIERT: die beiden Produktionsanschlüsse. VERIFIZIERT: Produktionskompilierung, 28 DOM-/Interop-Fälle und 19 der 22 neuen Android-UI-Fälle. **Abnahme offen**, bis die drei Befunde behoben, Unit-/Klassikregressionen grün und der reguläre APK-Bau geprüft sind. GERÄTEBESTÄTIGT: **NOT_RUN**.

## Wiedereinstieg und Rückfall

### Tatsächlich erkannte Klassikregression

Auf `406997761ac42ec8e5f42a4097d7e2e5555b4333` bestanden anschließend alle **22 Medien-/Stage-UI-Tests**, **71 Unit-Tests** und **28 DOM-/Interop-Tests** mit frischem synthetischem Kotlin-Paket (SHA-256 `86aca35b3e7955ed9b8adb5f1b6fe7c656b06a5258b3c5854236d68fdda36f60`).

Die unveränderte klassische UI-Auswahl deckte danach eine reale Bindungsregression auf: Fünf `ContinuumAdaptiveAvatarNavigationUiTest`-Fälle erreichten ihre bisherigen Geometrie-/Montageprüfungen, scheiterten jedoch am erwarteten direkten `AvatarDirector.sink`. Der neue HTML-Fanout war auch im klassischen Design eingesetzt worden. Der minimale Produktionsfix erhält im klassischen Modus wieder den ursprünglichen direkten Sink und verwendet den zusätzlichen Sprachverteiler allein im HTML-Modus. Der initiale Director bekommt wie zuvor den ursprünglichen `JarvisAvatarSlot.fanOut`; die Designabfrage erfolgt erst an den vier echten UI-Attach-Stellen nach Context-Anbindung. Sämtliche fünf Identitätsassertionen bleiben unverändert.

Der betreffende Klassiklauf endete außerdem separat mit Testworker-Exit 137 nach unvollständiger Ausführung; es entstanden keine frischen JUnit-XML-Dateien. Eine OOM-Ursache ist nicht nachgewiesen. Er zählt weder als vollständiger 32er-Lauf noch als APK-Nachweis. Nach diesem Produktionsfix werden Compile, Medien-/Unit-/Klassiktests und Paketbau erneut an einen neuen exakten SHA gebunden.

### Präzisierter Testhost-Befund

Der Diagnose-Lauf auf `49f5d50f69e2afb6a340d91bd742aa6d9cc30202` ergab 22 Tests, 18 bestanden und 4 fehlgeschlagen. Die zusätzliche positive Kontrolle entlarvte dabei einen zuvor scheinbar grünen Negativfall: vor jeder Bildbestätigung war das Host-WebView-Fenster `GONE`, mit Größe 0×0 und ohne global sichtbare Fläche. Sitzung, Bildtoken, Seitenbereitschaft und Restfrist waren gültig. Der Split-Screen-Controller blieb aktiv; seine WebView wurde nicht pausiert.

Die Prüfung des tatsächlich verwendeten Robolectric-4.14.1-JARs zeigt, dass dessen `ShadowWebView`-Provider für `ViewDelegate.setFrame` nur `false` liefert. Der neue ausschließlich in `uiTest` liegende `Command03HostWebView` stellt deshalb den realen Android-`View.setFrame`-Aufruf wieder her. Die Fixtures führen außerdem die realen Fenster-/Layout-/Looper-Schritte aus. Größen-, Sichtbarkeits- und Draw-Getter werden nicht erfunden; die positiven Gate-Assertions bleiben erhalten. `CockpitHostFramePump` konsumiert nur vorhandene Invalidierungen mit Software-Canvas und ist kein GPU-Nachweis. Dieser Fixture-Fix wird erst durch den nächsten exakten Compile-/UI-Lauf verifiziert.

Der anschließende Lauf auf `2deeb075bf02bc8d6923b94aef032c7ef9d7949f` bestätigt echte 392×852-Bounds und global sichtbare Rechtecke, bleibt jedoch mit 18/22 bestanden am Fensterflag `GONE` hängen. Der lokale Robolectric-Bytecode erklärt auch diese zweite Ursache: `WindowSessionDelegate.getAddFlags` setzt `APP_VISIBLE` standardmäßig nur bei `GraphicsMode.NATIVE` und SDK ≥26. Die MediaStage-Klasse und allein die betroffene Split-Screen-Methode erhalten daher den bereits in klassischen Hosttests verwendeten NativeGraphics-Modus. Kein globaler Property-Schalter und keine künstlichen Sichtbarkeits-Getter werden gesetzt. Auch NativeGraphics ist hier ein Host-Testbackend, keine Geräte- oder WebView-GPU-Bestätigung. Der nächste Lauf muss diesen eng begrenzten Testkonfigurationsfix erst bestätigen.

Zuerst Arbeitsbranch, kanonischen Integrations-HEAD und diese Übergabe abgleichen. Dann native Produzenten und Adaptervertrag prüfen, frische Tests durchführen und Ergebnisse mit genauen SHAs ergänzen. Der vorhandene bestätigte Rückweg zur klassischen Oberfläche bleibt nutzbar. Keine Main-Integration, Originalsignierung, Installation oder produktive Veröffentlichung gehört zu diesem Entwicklungsschritt.
