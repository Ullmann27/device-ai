# Live-Computer – Audit und erster Durchstich (Code55, Agent C, 12.11.2026)

Basis: `claude/code55-cinematic-evolution-20261112` @ `d46c286de`, Zweig `claude/code55-agent-c-live-computer-20261112`.
Auftrag: `docs/design/continuum-live-computer-20261112/AUFTRAG-OPUS-5.5.md` (Commit `339be4a38`, nur gelesen).
Statusbegriffe: **PORTIERT** = Code vorhanden · **VERIFIZIERT** = Hosttest grün · **GERÄTEBESTÄTIGT** = auf Fold7 belegt.
Fold7/Emulator: **NOT_RUN** für alles in diesem Dokument. Gerätverhalten unten ist API-Dokumentation, kein Beleg.

## 1. Was existiert (Code-Belege)

| Baustein | Beleg | Stand |
|---|---|---|
| Accessibility-Beobachtung, nur Fensterereignisse | `ContinuumScreenObservationService.kt:14-33`, Manifest `AndroidManifest.xml:30-34` | PORTIERT, in Produktion verdrahtet |
| Konfiguration: `canRetrieveWindowContent`, `flagRetrieveInteractiveWindows`; **kein** `canPerformGestures`, **kein** `canTakeScreenshot` | `res/xml/continuum_screen_observation.xml:3-7` | Gesten-/Screenshot-API daher nicht nutzbar |
| Epoche/Revision/Fensterbindung, Lesesperre | `ContinuumScreenObservationService.kt:58-97`, `read()` `:206-223` | PORTIERT, uiTests `ScreenObservationRevisionTest` |
| `read_app_screen` (Text, Passwort/Eingabefelder geschwärzt) | `AndroidDeviceAppActions.kt:47-74`, `BoundedScreenReader.kt:17-62` | PORTIERT, über ToolGate (Risk.CONFIRM `ToolRegistry.kt:88`) |
| `launch_app` mit beobachtetem Vordergrund-Postcondition | `AndroidDeviceAppActions.kt:40-46`, `VerifiedForegroundLaunch.kt:27-58` | PORTIERT, verdrahtet `DeviceToolExecutor.kt:111-116` |
| Klickkandidat (Label → genau ein Knoten → neuer Zustand) | `VerifiedNodeClick.kt:24-55`, Android-Teil `AndroidNativeClick.kt:14-100`, Bus `ContinuumScreenObservationService.kt:158-205` | PORTIERT, **nicht** registriert/verdrahtet (nur uiTests) |
| `chief/DeviceControl.kt` (Tap/Swipe/Text/Back/Home) | `DeviceControl.kt:8-18` „inactive“, Router `:135-246` | inaktiv; `AccessibilityControlPort` hat **keine** Implementierung (Kommentar `:66` nennt eine nicht existierende Klasse) |
| `take_screenshot` / `record_screen` | `DeviceToolExecutor.kt:353-360` | Shizuku-Shell `screencap`/`screenrecord` in `/sdcard/Download` – Datei, kein Live-Frame |
| MediaProjection / VirtualDisplay / FGS | `grep` über `app/src`: kein Treffer; Manifest ohne `FOREGROUND_SERVICE*` | fehlt |
| Arbeitsfenster im Cockpit | `JarvisWorkWindowHost.show(body: View, …)` `JarvisWorkWindowHost.kt:150`, Szene `Kind.SCREEN` `JarvisActionScenes.kt:88` | nimmt beliebige View; Darstellung übernimmt Agent E (Filament-Monitor) |
| Blick auf Arbeitsfenster | `JarvisWindowGaze.kt:7-14`, `ToolWorkSceneLease.kt:7-69` | Präsentation, keine Ausführungsrechte |

## 2. Lücken (präzise)

1. **Einzelfenster-Annahme:** `AndroidNativeClick.kt:28,46` verlangt `windows.size==1`; im Fold7-Split-Screen (Continuum + Ziel-App) gibt es mindestens zwei Anwendungsfenster → jeder Klick endet in `AMBIGUOUS_OR_OVERLAID_WINDOWS`. Ebenso Systemfenster (Statusleiste, IME) – auf echten Geräten ist `size==1` vermutlich selten (NOT_RUN). Fehlt: Fensterpolitik „genau ein Ziel-App-Fenster, eigene Continuum-Fenster und IME erlaubt, sofern nicht überlappend; jedes fremde überlappende Fenster blockiert“. Die bestehende Prüfung bleibt bis dahin unverändert.
2. **`ignoreVerifiedUnrelatedWindow`** (`ContinuumScreenObservationService.kt:100-143`) verlangt, dass das Ziel `isActive && isFocused` ist (`:124`). Im Split-Screen ist nur eine Hälfte fokussiert; tippt der Besitzer in Continuum, ist WhatsApp nicht aktiv → Lesen/Klicken bricht ab.
3. **`read()`/`readScreen`** nutzen `rootInActiveWindow` (`:36`) – liefert im Split-Screen das fokussierte Fenster, ggf. Continuum selbst → `UNEXPECTED_SCREEN_PACKAGE`. Für Mehrfenster ist `windows`-basierte Wurzelwahl nötig.
4. **Kein Scroll/Text mit Wirkungsprüfung**: `DeviceControlRouter` meldet nur `DISPATCHED` (`DeviceControl.kt:248-255`), kein Folgezustand. Koordinaten-Taps (`:204-214`) sind blind und könnten einen Senden-Knopf treffen → für den Live-Computer **nicht** verwenden.
5. **Keine Bildquelle**: kein MediaProjection-FGS, kein `canTakeScreenshot`.
6. **Senden**: es gibt kein Senden-Tool in `ToolRegistry.kt:69-103`; `SafePillActionGate.kt:62-67` bestätigt „kein mail-send tool“. Gut so – Versand bleibt unmöglich.

## 3. Darstellungswege auf einem Gerät (Bewertung, alle NOT_RUN)

| Weg | Realistisch? | Grund |
|---|---|---|
| Fremde App **innerhalb** des Vollbild-Cockpits zeigen und bedienen | **nein** | Android rendert fremde Activities nicht in eine fremde View; VirtualDisplay ist keine VM für fremde Apps (Start fremder Activities auf eigenem VirtualDisplay ist nicht allgemein erlaubt). Ist Continuum im Vollbild, ist die Ziel-App unsichtbar → keine aktuellen Pixel. |
| MediaProjection **ganzer Bildschirm**, Continuum daneben sichtbar | nur mit Spiegelschleife | Bild enthält das eigene Cockpit → Bildschirm-im-Bildschirm. Wird von `LatestLiveFrameHolder` abgewiesen (`OWN_WINDOW_MIRROR`). |
| MediaProjection **einzelne App** (Android 14+ Auswahldialog) im Split-Screen | **ja, bester Kandidat** | Nur die gewählte App im Bild, kein Spiegel. Android nennt die gewählte App nicht → Paket muss per Accessibility-Fensterliste gebunden werden; sonst `SOURCE_UNVERIFIED`. Unsichtbar/minimiert → `onCapturedContentVisibilityChanged(false)` → VERDECKT. |
| `AccessibilityService.takeScreenshotOfWindow(windowId)` (API 34) | ja, für Beobachtungsschritte | Genau ein Fenster per ID, keine Einwilligung pro Sitzung, aber `canTakeScreenshot` nötig (Bedienungshilfe ggf. neu bestätigen) und Ratenlimit; geschützte Fenster → Fehler `SECURE_WINDOW` → VERDECKT. |
| Coverscreen (Fold7 zugeklappt) | ungeklärt | Zugeklappt ist der Hauptschirm aus; gleichzeitige Nutzung beider Displays durch Drittanbieter nicht belegt. |
| FLAG_SECURE-Apps (Banking, teils Messenger-Ansichten) | **nein, gewollt** | MediaProjection liefert Schwarz; keine Umgehung. |

## 4. Umgesetzt in diesem Durchstich

* `cognitive/LiveComputerFrames.kt` – **VERIFIZIERT (Host)**: `LiveFrameBinding` (Task/Sitzung/Ziel/Herkunft), `LatestLiveFrameHolder` (nur letztes Bild, ≤ 2 lebende Frames, eine Leihe, Freigabe erst nach Lease-Ende, Ratenlimit, Reihenfolge, Größenlimit), Spiegelschutz (`OWN_WINDOW_MIRROR` bei eigener Quelle bzw. Ganzbild mit sichtbarem Continuum), `SECURE_CONTENT`, `SOURCE_UNVERIFIED`, `WRONG_SOURCE`, App-Wechsel verwirft alte Frames sofort; Zustände LIVE / PAUSIERT / VERDECKT / BEENDET / WARTET. Schnittstelle `LiveMonitorFeed` für Agent E (status/acquire/version; Frame-Typ generisch, z. B. `HardwareBuffer`, Lease-`close()` als Release-Callback).
* `cognitive/LiveComputerActions.kt` – **VERIFIZIERT (Host)**: `ActionEffectVerifier` (angenommen ≠ erfolgreich), `LiveActionJournal` (keine Doppelaktion auf gleicher Beobachtung, nach UNKLAR nur mit frischerer Beobachtung, gleiche Aktion nach UNKLAR nie wieder), `VerifiedNodeAction` für Scroll/Text/Senden mit Ergebnis (Knoten gefunden, angenommen, Folgezustand beobachtet), `LiveTargetBounds` an Beobachtung gebunden, `LiveActionEvent` als Adapterstelle für Agent Ds `CockpitWorkEvent`, `SendActionGuard` + `SendApproval` (nur aus ToolGate-Quittung DISPATCH_COMMITTED/APPROVED/DESTRUCTIVE mit exakten Argumenten, einmalig; `SEND_TOOLS` leer ⇒ Versand in Produktion unmöglich).
* `VerifiedNodeClick.kt:32-33` – bestehender Klickkandidat weist Senden/Posten/Liken/Anrufen-Ziele ab.
* **Nicht umgesetzt:** MediaProjection-FGS, Android-Port für Scroll/Text am `ScreenObservationBus`, Mehrfenster-Politik. Gründe: Budget; Robolectric läuft hier mit SDK 28 (`app/build.gradle.kts:297`), API-34-Callbacks wären nicht hosttestbar.

## 5. Nächster Schritt (konkret)

1. Reine `WindowSafetyPolicy` (Fensterfakten → genau ein Ziel-Fenster, eigene/IME nicht überlappend erlaubt) + Robolectric-Test; dann `AndroidNodeActionPort` am Bus analog `clickCandidate` (`:158-205`), Wurzel per `windows` statt `rootInActiveWindow`.
2. `ContinuumScreenCaptureService` (`foregroundServiceType="mediaProjection"`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`), Start nur aus Besitzer-Geste, `startForeground` vor `getMediaProjection`, `registerCallback` vor `createVirtualDisplay`, `ImageReader` (lange Kante ≤ 1280, `maxImages=3`) → `LatestLiveFrameHolder`; `onStop`/Sichtbarkeit → `end()`/`pause()`.
3. Fold7-Belege: Split-Screen WhatsApp + Continuum, Einzel-App-Freigabe, Entwurf ohne Versand, Stop/Rechteentzug/Rotation.
