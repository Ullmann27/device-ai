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
- **Kontextfenster/Tokenbudget: Ursache gemessen und Budget-Fix umgesetzt (host-getestet, Gerät offen).** Hostmessung mit
  echtem `ToolRegistry`: 33 Werkzeugdeklarationen ≈ 7 043 Byte JSON ≈ 1 760–2 350 Token – etwa die Hälfte des
  4 096-Token-Fensters; das alte Zeichenbudget (15 500) passte nie. Neu: `LocalNativePromptBudget.promptByteBudget`
  (nativ gemessene Präfix-Token, 3 Byte/Token wie der Planer, ≥ 512 Antwort-Token, Rahmenreserve) über
  `LocalModelRuntime.tokenAwarePromptUtf8Bytes`; die Prompt-Rahmung nimmt das Minimum → optionale Historie wird zuerst
  gekürzt, Pflichtinhalt, der nicht passt, wird vor dem nativen Prefill ehrlich abgelehnt. Nicht geändert: Anzahl und Text
  der Werkzeugdeklarationen (würde Modellverhalten ändern; erst nach Gerätemessung des echten Präfix entscheiden).
- **KI-Team-Fehler: rückverfolgbar gemacht (host-getestet), Ursache auf dem Gerät offen.** Vorher speicherte der
  Worker-Pool nur den Ausnahmeklassennamen und das Team nur „FAILED“. Jetzt wandert ein fester Code mit
  (`ChiefFailureCode`; Diagnose z. B. `FAILED__LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL`), Freitext nie. Wahrscheinliche
  Ursache im Feld: dasselbe zu große Prompt-Budget (Team-Prompts ≤ 15 500 Byte, ≈ 5 k Token > 4 096) – auf dem Gerät
  am neuen Diagnosecode zu bestätigen.
- **NEURON-Herkunft: Anzeige ehrlich gemacht (host-getestet).** Ein „Verbesserung“-Zähler stieg, sobald im kleinen
  Startkern-Netz (Gruß/Frage-Klassifikator, nicht Gemma) der Trainingsverlust sank und Prüfsätze nicht schlechter
  wurden (`NeuralDialogue.practice`) – kein Qualitätsbeleg. Anzeige jetzt „übernommen (Verlust ↓, kein Qualitätsbeleg)“;
  Lernlogik unverändert. NEURON-Tests 19 Klassen · 63 Tests grün.
- „Was kannst du davon löschen?“: offen (Antwort braucht den gesicherten Löschweg für Einzelfakten; nicht erfunden).
- Volle Android-Suite, Lint, APK-Bau: in diesem Zweig noch nicht gelaufen.

## 3a. Durchgeführte Prüfungen (Host, 13.11.2026, Commit `03b80b202` + App-Ablauf-Fix)

| Prüfung | Ergebnis |
|---|---|
| Gezielte Gradle-Hostsuite (Live-Computer, Beobachtungsbus, Projektion, Controller-UI, Dokumentfluss, Cockpit/Filament-Hosttests, Routing, Kamerafahrt) | 85 Klassen · 529 Tests · 0 Fehler · 0 übersprungen |
| App-Ablauf (OwnerAppActionFlow, App-Suche, Bot-Routing) | 6 Klassen · 70 Tests · 0 Fehler |
| Tokenbudget + Prompt-Rahmung + Laufzeit + Dialog/Kontext + Dokumentfluss | 41 Klassen · 242 Tests · 0 Fehler |
| Chief/KI-Team + Bot-UI | 31 Klassen · 251 Tests · 2 rot – beide auch auf Basis `4d1c3fbfc` rot (Schreibschutz-Tests, Prozess läuft als uid 0); Team-Nachlauf mit neuem E2E-Test 30/30 |
| Node-Verträge der berührten Quellen | 291/291 |
| Node-Vollsuite | 1650: 1601 grün, 26 rot, 23 übersprungen – die 26 roten identisch auf Basis `4d1c3fbfc` (`JUNIT_FILE_IDENTITY_UNSUPPORTED_RUNTIME` in ccc1-redgreen-verifier, coding-orchestrator-failure-report, momega1-verifier-regressions) |

Hinweis: Ein früherer Lauf wurde nicht gewertet, weil während seiner Kompilierung kurz `git stash` lief.

### Unsignierte APK (nur Bau-/Packnachweis, nicht installierbar über die originalsignierte App, nicht verteilt)

| Feld | Wert |
|---|---|
| Quelle | `ce1ff8da4` (Zweig `claude/code56-blacksite-rebirth-20261113`) |
| Befehl | `:app:assembleRelease` mit `-PcontinuumStandaloneVersionCode=56 -PcontinuumLocalRenderUnsigned=true -PcontinuumOwnerArm64=true -PdeviceAiMentorPreview=true`, Signier-Umgebungsvariablen entfernt |
| Paket / Version | `at.heinz.deviceai.continuumlocal` · versionCode 56 · versionName 0.3.55 · min 26 / target 36 |
| Größe / SHA-256 | 103 787 127 Byte · `8009ab48a49f8049afe0427f36b3fb1349c50df35bf820f4b1d138b3f6dcf778` |
| Signatur | keine (`apksigner verify` → DOES NOT VERIFY, erwartet) |
| Inhalt geprüft | `cinema3d/*` (Workstation-GLB 645 592 B, Orb, live-monitor.filamat), arm64 Filament/gltfio/LiteRT-LM/OpenCV, neue Klassen im Dex |

Originalsignierung nur über den geschützten Signierweg nach SHA-genauer Besitzerfreigabe. Die frühere unsignierte
APK55 aus `472ef3fd6` (SHA-256 `bdb393a2…7d603`) ist überholt (Code55 wurde aus `889e06d47` signiert).

## 4. Nächster Gerätetest (Fold7, Split-Screen)

1. Continuum + Eigene Dateien nebeneinander, Bedienungshilfe aktiv. „Öffne Downloads.“ → Freigabe „Gesamter Bildschirm“.
2. Erwartung: Monitor zeigt die Dateien-App live; Klick auf „Downloads“; Ordnerüberschrift bestätigt; nach ≤ 8 s endet
   die Aufnahme (Statusleisten-Hinweis verschwindet).
3. Bei Fehlschlag: die zusätzliche Zeile „Bildkette: …“ abfotografieren – sie zeigt, an welcher Stufe die Kette abbricht.
