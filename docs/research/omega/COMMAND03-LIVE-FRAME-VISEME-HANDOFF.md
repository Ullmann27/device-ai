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

## Wiedereinstieg und Rückfall

Zuerst Arbeitsbranch, kanonischen Integrations-HEAD und diese Übergabe abgleichen. Dann native Produzenten und Adaptervertrag prüfen, frische Tests durchführen und Ergebnisse mit genauen SHAs ergänzen. Der vorhandene bestätigte Rückweg zur klassischen Oberfläche bleibt nutzbar. Keine Main-Integration, Originalsignierung, Installation oder produktive Veröffentlichung gehört zu diesem Entwicklungsschritt.
