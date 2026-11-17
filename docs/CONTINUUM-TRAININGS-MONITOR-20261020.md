# Continuum Trainings-Monitor – Android Preview v0.3.5

Stand 20.10.2026. Experimentbranch / Draft PR #42. Keine Änderung an main oder an vorhandenen SWB1-/CDX1-Gewichten.

## Bedienung

`KI personalisieren → Lernen → TRAININGS-MONITOR · LIVE` öffnet eine nicht exportierte, nur lesende Android-Ansicht mit nativer holografischer Canvas-Animation. Die Ansicht liest drei **unabhängige** lokale Statuskanäle: öffentliche Wikipedia-Recherche mit SWB1-Lernen, tägliches SWB1-Offline-Lernen und vom Besitzer gestartete CDX1-Paararchiv-Stufen.

- **TRAINIERT GERADE**: Ein Trainingskern hat den tatsächlichen Eintritt in die Gradientenberechnung protokolliert und der Eintrag ist noch aktuell.
- **FORSCHT / PRÜFT GERADE**: Ein Android-Lernauftrag führt andere Arbeiten wie Quellenabruf, Archivierung oder unabhängige Modellprüfungen durch.
- **TRAINING ABGESCHLOSSEN**: Letzter Abschluss der Kanäle ist höchstens eine Minute alt. Ergebnis (angenommen, verworfen, verschoben, fehlgeschlagen) steht separat in der Kanal-Kachel.
- **WARTET AUF NÄCHSTEN ZYKLUS**: Kein frisches aktives Signal. `JobScheduler` und die lokale Freigabe zeigen, ob der öffentliche Forschungsjob tatsächlich registriert ist. Das Planungsfenster aus letztem Start plus 15 Minuten ist nur eine Annäherung, kein exakter Ausführungstermin.
- **LAUFSTATUS UNGEKLÄRT**: Ein ohne Abschluss stehengebliebener Start ist älter als 45 Sekunden oder die letzte monotone Uhrzeit liegt nach einem Neustart in der Zukunft. So kann die Ansicht nach einem Prozessabbruch nicht dauerhaft ein Live-Training vortäuschen.

Der Monitor aktualisiert sich im sichtbaren Fenster einmal pro Sekunde. Nur die Kernanimation zeichnet laufend neue Frames; geschlossene Android-Ansichten erhalten keine eigenen UI-Timer.

## Laufzeitschutz

Persistiert werden nur feste Phasen und begrenzte Metadaten (öffentlicher Thementitel, Kanal, Modellkennzeichen, Start/Abschluss/Ergebnis). Es werden keine Trainingsdaten, Nutzerfakten, Rohprotokolle, API-Tokens oder frei ausführbaren Befehle gespeichert. Telemetrie-Fehler dürfen das Training nicht verändern. Jeder Kern benutzt bestehende Holdout-Gates und Checkpoint-Regeln; der Monitor aktiviert kein Modell und führt keine Installation aus.

Der SWB1-Kern bietet einen optionalen nicht-werfenden Beobachter für die tatsächlichen Gradienten-/Audit-Grenzen. Die öffentlichen und Offline-JobServices geben diese Ereignisse in ihren eigenen Kanal weiter. Beim CDX1-Labor erfolgt dieselbe Grenzmeldung über den bestehenden Paar-Decoder. Schon vor der neuen APK erfolgte Trainingsläufe sind über die bisherigen Learning-Core-/Forschungs-Audits einsehbar, aber nicht nachträglich mit erdachten Live-Phasen versehen.

## Build und Freigabe

Neues signiertes Mentor-Preview-Paket: applicationId `at.heinz.deviceai.mentorpaired`, Preview-versionCode 8 und versionName 0.3.5. Vorhandene Signatur muss unverändert bleiben. Keine Deinstallation: sonst gehen private App-Daten verloren. Ein fehlerhaft signiertes Paket ist abzulehnen.

Neu: `.github/continuum/training-monitor-wiring.test.mjs` in den Termux- und Render-Build-Vertrag aufgenommen. Quelltext-Wiring-Checks sind kein Ersatz für `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, APK-Signatur/Package-ID/SHA256 und Samsung-End-to-End-Abnahme.

Ohne abgeschlossenen, überprüften Build darf kein alter Render-APK-Link als neuer Monitor-Build bezeichnet werden.
