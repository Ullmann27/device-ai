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

## Nachweisgrenzen

PORTIERT: noch offen. VERIFIZIERT: noch offen. GERÄTEBESTÄTIGT: NOT_RUN.

Der vorherige Stand und seine 82 lokalen Fokusprüfungen sind in `COMMAND03-HTML-VALIDATION-HANDOFF.md` dokumentiert. Sie ersetzen keinen frischen Nachweis dieser neuen Datenpfade. Android-WebView/GPU, echte Sprachlatenz, Berechtigungsdialoge und Faltverhalten auf Hardware sind gesonderte Geräteprüfungen.

## Wiedereinstieg und Rückfall

Zuerst Arbeitsbranch, kanonischen Integrations-HEAD und diese Übergabe abgleichen. Dann native Produzenten und Adaptervertrag prüfen, frische Tests durchführen und Ergebnisse mit genauen SHAs ergänzen. Der vorhandene bestätigte Rückweg zur klassischen Oberfläche bleibt nutzbar. Keine Main-Integration, Originalsignierung, Installation oder produktive Veröffentlichung gehört zu diesem Entwicklungsschritt.
