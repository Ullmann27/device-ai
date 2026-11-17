# Code55 · Agent C (Live-Computer) · Übergabe 12.11.2026

Zweig `claude/code55-agent-c-live-computer-20261112` (Basis `d46c286de`). Ausführlich: `docs/research/omega/LIVE-COMPUTER-AUDIT-20261112.md`.
PORTIERT = Code da · VERIFIZIERT = Hosttest grün · GERÄTEBESTÄTIGT = Fold7-Beleg. **Fold7/Emulator: NOT_RUN.**

## Befunde (Datei:Zeile)
- Klickkandidat verlangt genau ein Fenster: `AndroidNativeClick.kt:28,46` → im Fold7-Split-Screen immer `AMBIGUOUS_OR_OVERLAID_WINDOWS`.
- Fensterfreigabe verlangt aktives+fokussiertes Ziel: `ContinuumScreenObservationService.kt:124`; Lesen nutzt `rootInActiveWindow` `:36` → im Split-Screen ggf. Continuum selbst.
- `chief/DeviceControl.kt:8-18` inaktiv; `AccessibilityControlPort` ohne Implementierung (`:66`); Router meldet nur `DISPATCHED` ohne Folgezustand (`:248-255`); Koordinaten-Taps blind (`:204-214`).
- Accessibility-Konfiguration ohne `canPerformGestures`/`canTakeScreenshot`: `res/xml/continuum_screen_observation.xml:3-7`.
- Kein MediaProjection/VirtualDisplay/FGS im Code oder Manifest (`AndroidManifest.xml:4-19`); `take_screenshot` ist Shizuku-`screencap` in eine Datei (`DeviceToolExecutor.kt:353-355`).
- Kein Senden-Tool registriert (`ToolRegistry.kt:69-103`) – Versand bleibt unmöglich.
- Verdrahtet: `launch_app`/`read_app_screen` über ToolGate (`DeviceToolExecutor.kt:111-116`, `AndroidDeviceAppActions.kt:40-74`).

## Implementiert (VERIFIZIERT nur Host)
- `cognitive/LiveComputerFrames.kt`: Latest-Frame-Holder (≤ 2 lebende Frames, eine Leihe), Bindung Task/Sitzung/Ziel/Fenster/Bildzeit,
  Spiegelschutz, Secure/unbestätigte/fremde Quelle abgewiesen, App-Wechsel verwirft alte Frames, Zustände LIVE/PAUSIERT/VERDECKT/BEENDET.
  **Schnittstelle für Agent E:** `LiveMonitorFeed<F>` (`status(now)`, `acquire()`→`LiveFrameLease` mit `close()` als Release-Callback, `version()`).
- `cognitive/LiveComputerActions.kt`: `ActionEffectVerifier` (angenommen ≠ Erfolg), `LiveActionJournal` (keine Doppelaktion, nach UNKLAR nie
  dieselbe Aktion, sonst nur mit frischerer Beobachtung), `VerifiedNodeAction` (Scroll/Text/Senden mit Ergebnis gefunden/angenommen/beobachtet),
  `LiveTargetBounds` an Beobachtung gebunden, `LiveActionEvent` = Adapterstelle für Agent Ds `CockpitWorkEvent` (nicht doppelt gebaut),
  `SendActionGuard` + `SendApproval` (nur aus ToolGate-Quittung DESTRUCTIVE/APPROVED/DISPATCH_COMMITTED, exakte Argumente, einmalig; `SEND_TOOLS` leer).
- `VerifiedNodeClick.kt:32-33`: Klickkandidat weist Senden/Posten/Liken/Anrufen ab.
- Tests: `LiveComputerFramesTest` (8), `LiveComputerActionsTest` (10); Nachbarn siehe Commit-Text.

## Fold7 – realistisch / nicht möglich (API-Lage, NOT_RUN)
- Möglich: Split-Screen Continuum + Ziel-App, MediaProjection „einzelne App“ (Android 14+) → nur Ziel-App-Pixel, Paket per Accessibility gebunden.
- Möglich für Einzelschritte: `takeScreenshotOfWindow(windowId)` (API 34) – braucht `canTakeScreenshot`, Ratenlimit.
- Nicht möglich: fremde App im Vollbild-Cockpit zeigen+bedienen; Ganzbild-Capture neben Continuum (Spiegelschleife, wird abgewiesen);
  FLAG_SECURE-Inhalte (bewusst keine Umgehung); VirtualDisplay als VM für fremde Apps.

## Nächste Schritte
1. Reine `WindowSafetyPolicy` (ein Ziel-Fenster; eigene Fenster/IME nicht überlappend erlaubt) + Robolectric-Test; Wurzel per `windows` statt `rootInActiveWindow`.
2. `AndroidNodeActionPort` am `ScreenObservationBus` analog `clickCandidate` (`ContinuumScreenObservationService.kt:158-205`): `ACTION_SCROLL_*`, `ACTION_SET_TEXT`.
3. `ContinuumScreenCaptureService` (FGS `mediaProjection`, Start nur per Besitzer-Geste, `registerCallback` vor `createVirtualDisplay`, ImageReader ≤ 1280 px, `maxImages=3`) → `LatestLiveFrameHolder`.
4. Agent E bindet `LiveMonitorFeed` an den Filament-Monitor; Agent D mappt `LiveActionEvent` → `CockpitWorkEvent`.
5. Fold7-Belege: WhatsApp-Entwurf an Testkontakt ohne Versand, Stop, Rechteentzug, App-Wechsel, Rotation.
