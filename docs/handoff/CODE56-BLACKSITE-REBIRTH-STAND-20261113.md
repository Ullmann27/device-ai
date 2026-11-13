# Code56 · Operation Blacksite Rebirth – Arbeitsstand 13.11.2026

Zweig `claude/code56-blacksite-rebirth-20261113`, Basis `4d1c3fbfc` (= Code55-Integration `889e06d47` + Datei-Fix).
Kein Merge nach `main`, keine Signierung, keine APK-Veröffentlichung. Status je Punkt getrennt:
**IMPLEMENTIERT / HOST-GETESTET / GERÄTEBESTÄTIGT (Fold7)**. Nichts hier ist auf dem Fold7 bestätigt.

## 1. Feldfehler „Monitor schwarz · Verdeckt · Bildquelle nicht bestätigt“ (P2) – Ursache

Ablauf im Feld: Split-Screen Continuum + Eigene Dateien, „Öffne Downloads“, Bildschirmfreigabe erteilt →
„Keine Bildquelle verbunden“ → „Verdeckt · Bildquelle nicht bestätigt“ → Auftrag gestoppt → Projektion blieb aktiv.

Ursachenkette (statisch aus dem Code belegt; Gerätemessung steht aus):

1. `VirtualComputerMonitorView.updateStatus()` setzt bei jedem Statuswechsel `contentDescription`
   (`ui/VirtualComputerMonitorView.kt`), `CockpitLiveTexture.frameSubmitted()` wechselt pro Bild zwischen
   „Bild wird übernommen“ und „Live“.
2. Android meldet das als `TYPE_WINDOW_CONTENT_CHANGED` **aus Continuums eigenem Paket**
   (Dienstkonfiguration abonniert `typeWindowContentChanged`).
3. `ScreenObservationBus.contentChanged()` erhöhte dafür die Beobachtungsrevision, solange keine Lesung lief.
4. `ContinuumLiveProjectionService.onImage()` vergleicht die Revision vor und nach der GPU-Kopie
   (`before.revision != after.revision`) → Bild verworfen → `pauseProjection(SOURCE_UNVERIFIED)` →
   Status „Verdeckt · Bildquelle nicht bestätigt“ → Label ändert sich erneut → neues Ereignis → nächstes Bild verworfen.
   **Selbsterhaltende Schleife.**
5. Dieselbe Revision bindet Klicks (`LiveMonitorSubmission.checkActionSource`: `meta.observationRevision == source.revision`)
   → auch Eingaben waren dadurch blockiert.
6. Der Workflow wartete 5 s auf ein frisches Bild (`NO_FRESH_MONITOR_FRAME`), meldete nur „Auftrag angehalten“,
   und der Controller rief nach einem fehlgeschlagenen Auftrag **kein** `stop()` → MediaProjection blieb aktiv.

Split-Screen-Hinweis des Besitzers geprüft: `LiveWindowSafety.select` wählt das Zielfenster nach Paket/Typ/Lage,
**nicht** nach Fokus; Fokusverlust führt dort nicht zu „verdeckt“. Accessibility-Fensterbereiche sind von Android
bereits um überlagernde Fenster beschnitten.

## 2. Änderungen in diesem Zweig

| Bereich | Datei(en) | Inhalt | Status |
|---|---|---|---|
| Bildquelle | `cognitive/LiveObservationEventPolicy.kt`, `ContinuumScreenObservationService.kt` | Inhaltsereignisse aus Continuums eigenem Paket erhöhen die Live-Revision nicht mehr (Android validiert den Absender gegen die UID). Fenster­struktur-Ereignisse, fremde und unbekannte Absender sowie eine Lesung von Continuum selbst zählen weiter. | implementiert, host-getestet |
| Aufräumen | `OwnerComputerSessionController.kt` | Fehler/Pause/Rückfrage/Abbruch → sofort `stop()` (Projektion, Dienst, Freigabe). Erfolg → Ergebnis bleibt max. 8 s live, dann automatisches Ende. Ergebnisseite wird beim Verdecken geleert. | implementiert, host-getestet |
| Fehlermeldung | `cognitive/ComputerFailureExplanation.kt`, `OwnerComputerWorkflow.kt` | Bekannte Codes (z. B. `NO_FRESH_MONITOR_FRAME`) werden als Satz + „(Diagnose: CODE)“ genannt; freier Text nie. | implementiert, host-getestet |
| Diagnoseereignisse | `cognitive/LiveCaptureDiagnostics.kt` + Anschlüsse in Controller, `AndroidLiveComputer`, Projektionsdienst, `CockpitLiveTexture` | Zähler je Sitzung: Freigabe angefragt/erteilt/abgelehnt, Aufnahmedienst gestartet, Bildschirm sichtbar/verborgen, Bilder empfangen, Zielfenster bestätigt, an Monitor übergeben, im 3D-Monitor dargestellt, Eingaben, Wirkungen, Aufnahme beendet; Verwerfungsgründe. Bei Fehler/Pause als **eigene, nicht gesprochene** Zeile im Chat, nie im Modellkontext. | implementiert, Zähler host-getestet; Anschlüsse ohne Gerät ungetestet |
| Datei-Knopf | `AgentActivity.kt` | Tippen → Android-PDF-Auswahl (Fix `4d1c3fbfc`); langes Drücken → Entwicklungs-Quelldatei (hält Vertrag „App erstellen erreichbar“, der durch `4d1c3fbfc` allein rot war). | implementiert, UI-getestet (Robolectric) |
| Routing | `cognitive/OwnerDocumentIntent.kt` | „Suche mir eine PDF“, „Kannst du mir eine PDF suchen?“ → PDF-Auswahl; „… im Internet/online/über X“ unverändert. | implementiert, Unit + UI-Route getestet |
| Routing | `cognitive/OwnerChatIntentRouter.kt`, `OwnerCapabilityReply.kt`, `AgentActivity.kt` | „Was kannst du sonst noch?“ → Fähigkeiten; „Welches lokale KI-Modell verwendest du?“ → Antwort aus dem tatsächlich geladenen Modell (`localRuntime.loadedModel`), kein Web/Modellaufruf. | implementiert, host-getestet |
| Kamerafahrt (P1) | `avatar3d/CockpitCameraFlight.kt`, `FilamentAvatarRenderer.kt` | Bei `ACCEPTED`: Kamera startet hinter/über dem Monitor (150°, −11°), Quaternion-Slerp über die Seite, Smootherstep, endet **exakt** in der Ruhekamera (native Overlays wieder deckungsgleich). STOPPED/ERROR/Aufräumen → sofort Ruhe; reduzierte Bewegung → keine Fahrt. | Mathematik host-getestet; **kein Filament-Bild aufgenommen** |

## 3. Nicht erledigt / Blocker (ehrlich)

- **Fold7: NOT_RUN.** Keine Gerätebestätigung von Live-Bild, „Öffne Downloads“, „Öffne WhatsApp“, Texteingabe.
- **Filament-Aufnahmen / Clip: NOT_RUN.** In diesem Container gibt es keinen Emulator, keine System-Images, kein KVM.
- **GLB-Details (Lüftungsschlitze, Anschlüsse, VESA, angewinkelte Seitenmonitore): nicht umgesetzt.** Die Workstation-GLB
  wird über `tools/cockpit-workstation/build_unified_assets.py` gebaut, das exakt Blender 4.3 verlangt
  (`assert bpy.app.version[:2] == (4, 3)`); hier ist nur Blender 4.0.2 vorhanden, und PyPI bietet für das hiesige
  Python 3.13 nur `bpy` 5.1–5.3. Zusätzlich sind die Seitenmonitore
  an native Android-Ausschnitte gebunden (`native_document_bounds`/`native_draft_bounds`, `native_plane_z = 0`);
  ein Anwinkeln verlangt eine Lösung für diese 2D-Ausschnitte. Vorhanden sind bereits: Rückplatte, Rückfase, Rahmen,
  Glas, Ständer + Fuß, Tastatur, Schreibtisch, Seitenmonitore (flach), Bunkerraum.
- **Gedächtnis „Polarlicht 482“ → „Schrauben“:** nicht reproduziert. Host-Probe (temporär, nicht eingecheckt) mit echtem
  `ContinuumLearningCore` + `ContinuumCognitiveRuntime` nach „Neustart“ (neue Instanzen, gleiches Verzeichnis):
  3 Speicherformen („heißt Polarlicht 482“, „„Polarlicht 482““, zwei Sätze) × 6 Fragen (Name, Farbe, Ort, Anzahl …)
  → immer `grounded=true` mit vollständigem Originalsatz inkl. „Polarlicht 482“. Der Lernkern verliert den Namen also
  nicht; der Feldfehler liegt in einem anderen Pfad (lokales Modell mit Gedächtniskontext oder Gesprächsweg).
  Für die Ursache wird der genaue Feld-Wortlaut (Speichersatz + Frage + Antwort) benötigt.
- **Kontextfenster/Tokenbudget**, **KI-Team-Fehler**, **NEURON-Herkunft**, „Was kannst du davon löschen?“: offen.
- Volle Android-Suite, Lint, APK-Bau: in diesem Zweig noch nicht gelaufen.

## 3a. Durchgeführte Prüfungen (Host, 13.11.2026, Commit `03b80b202` + App-Ablauf-Fix)

| Prüfung | Ergebnis |
|---|---|
| Gezielte Gradle-Hostsuite (Live-Computer, Beobachtungsbus, Projektion, Controller-UI, Dokumentfluss, Cockpit/Filament-Hosttests, Routing, Kamerafahrt) | 85 Klassen · 529 Tests · 0 Fehler · 0 übersprungen |
| App-Ablauf (OwnerAppActionFlow, App-Suche, Bot-Routing) | 6 Klassen · 70 Tests · 0 Fehler |
| Node-Verträge der berührten Quellen | 291/291 |
| Node-Vollsuite | 1650: 1601 grün, 26 rot, 23 übersprungen – die 26 roten identisch auf Basis `4d1c3fbfc` (`JUNIT_FILE_IDENTITY_UNSUPPORTED_RUNTIME` in ccc1-redgreen-verifier, coding-orchestrator-failure-report, momega1-verifier-regressions) |

Hinweis: Ein früherer Lauf wurde nicht gewertet, weil während seiner Kompilierung kurz `git stash` lief.

## 4. Nächster Gerätetest (Fold7, Split-Screen)

1. Continuum + Eigene Dateien nebeneinander, Bedienungshilfe aktiv. „Öffne Downloads.“ → Freigabe „Gesamter Bildschirm“.
2. Erwartung: Monitor zeigt die Dateien-App live; Klick auf „Downloads“; Ordnerüberschrift bestätigt; nach ≤ 8 s endet
   die Aufnahme (Statusleisten-Hinweis verschwindet).
3. Bei Fehlschlag: die zusätzliche Zeile „Bildkette: …“ abfotografieren – sie zeigt, an welcher Stufe die Kette abbricht.
