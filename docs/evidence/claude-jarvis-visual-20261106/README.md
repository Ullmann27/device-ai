# Bewegungsnachweis 06.11.2026: Deck-Räumung, App-Szene, Kopfzeile

`jarvis-cockpit-app-szene-host.mp4` (20 s, 303 Bilder, 15 fps) und `keyframes-app-szene.jpg` stammen aus der **echten
`AgentActivity`** (Robolectric-Canvas, Fold7 innen 884 × 760 dp, `ContinuumVisualEvidenceUiTest.fold7InnerMotionClip`).
**HOST** · Kopfbereich = 2D-Vorschaukopf, **nicht Filament-3D** · **REAL_DEVICE_NOT_RUN**.

| Zeit | Inhalt | Quelle |
|---|---|---|
| 0–9,5 s | Ruhe, Zuhören, Denken, Sprechen (Director-Wortbereiche), Ruhe | echte App, Signale geskriptet |
| 10,3 s | Systemstatus-Kachel → Arbeitsfenster mit echter Statusabfrage; **Kacheln und untere Reihen räumen das Deck** | echte App (Commit `c9b0df3e`) |
| 13,9 s | App-Auftrag `launch_app` für ein installiertes Paket: echtes PackageManager-Label/-Symbol, Fenster fliegt vom Gesicht in die freie Arbeitsfläche, Deck räumt | **SIMULATED_TOOL_INPUT**: Paket ist eine Robolectric-Fixture („Notizen Beispiel“), die Szene wird mit der Argumentform des ToolGate-Runners aufgerufen; es läuft kein echtes Werkzeug, keine App öffnet sich (Commit `aec4dca8`) |
| 15,4 s | JARVIS spricht während der Arbeit (Director-Wortbereiche) | geskriptet, kein Audio |
| 17,1 s | „Das Werkzeug hat geantwortet“, Fenster faltet zurück, Blick und Deck kehren zurück | echte App |

Kopfzeile mit Atemraum (Commit `2fb4456d`) ist in allen Bildern sichtbar. Nicht belegt: Filament-Kopf, Bildrate/Wärme auf dem Fold7,
echter Werkzeuglauf mit realer App.
