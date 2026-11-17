# Jarvis-Cockpit (Querformat) und Animations-Ereignisse

Stand 04.11.2026, Zweig `design/jarvis-cockpit-home-20261104` (Draft-PR #481). Er baut auf ChatGPTs #477 auf (Basis `fix/continuum-device-experience-resume-20261104` @ d8fd7f7c) und auf dem Design-System #479 (Cherry-Pick von b085cf78).
UI-Stack: Android-Views mit dem plattformneutralen `ContinuumPainter`, kein Compose. Das passt zum bestehenden Repo.

## Layout: `ui/ContinuumLandscapeCockpitLayout.kt`
- Es greift bei Querformat ab 720 dp Breite und 480 dp Höhe, bei Schriftgröße ≤ 1,3 und ohne Kompaktmodus. In allen anderen Fällen bleibt das scrollende Referenzlayout von ChatGPT.
- Es baut auf `JarvisShellLayout` auf. Die Spaltenanteile stammen aus dessen FOLD_OPEN-Aufteilung (gilt auch für Tablets), die Leiste ist die bestehende Rail der Activity.
- Aufteilung:
  - **oben:** BRAND, die Tabs Gedächtnis, Agenten, Bots, System, Netz und Lernstand sowie Suche, Hinweise und Profil
  - **links:** `MODEL` (KI-Gehirn), `PROCESSES` (Aktuelle Prozesse), `LIVE_DIALOG`
  - **Mitte:** die Pills Avatar, Kugel und Kompakt sowie `HERO` = **Avatar-Slot** (`ContinuumLandscapeCockpitLayout.AVATAR_SLOT`). Dort liegt die bestehende `ContinuumHologramView`. Jarvis Avatar steuert den Kopf über `AvatarDirector` und `AvatarSinkRegistry` (#373), das Cockpit zeichnet in diesen Bereich nichts.
  - **rechts:** `SYSTEM_STATUS`, `WORKERS` (Aktive Agenten)
  - **unten:** `COMPOSER` und die Aktionsleiste
- Ein Bildschirm ohne Scrollen, alle Ziele mindestens 48 dp groß.

## Aktionsleiste: echte Pfade, nichts läuft von selbst
| Knopf | Slot | Pfad |
|---|---|---|
| Denken | LABEL_THINKING | Chat öffnen |
| Recherchieren | LABEL_ANALYSING | `showResearchModeDialog()` |
| Lernen | LABEL_LEARNING | `showLearningCenter()` |
| Entwickeln | LABEL_DEVELOPING | `showDevelopmentCenter()` |
| Ausführen | ACTION_TASK | `showCodingTaskDraftDialog()`: nur ein Entwurf, ausgeführt wird erst nach Bestätigung |
| Überprüfen | ACTION_VERIFY | `showDiagnosticsCenter()` |

Die Agenten-Karte öffnet `showChiefWorkerStatus()`, das ist ein echter, rein lesender ChiefWorkerPool-Auftrag.

## Datenquellen: echt oder „nicht verfügbar“
| Anzeige | Quelle |
|---|---|
| KI-Gehirn: Modell, Zustand, Antwortzeit, Kontext | `ContinuumDashboardCollector` (localRuntime, gemessene Antwortzeiten) |
| Auftragsphase (Annehmen → Arbeiten → Ergebnis) | `TurnLifecycle.phase` des aktuellen Turns |
| CPU (App), RAM, Speicher, Akku | Process-CPU-Zeit, ActivityManager.MemoryInfo, StatFs, BatteryManager |
| Akku-Temperatur | `ACTION_BATTERY_CHANGED` / `EXTRA_TEMPERATURE`. Eine CPU-Temperatur behauptet die App nicht, Android stellt sie Apps nicht bereit. |
| Aktive Agenten | `ChiefWorkerPool.snapshot()`, `capacity`, letzter `WorkerReport`. Der Balken zeigt die **verbrauchte Frist**, keinen Fortschritt, weil der Pool keinen Fortschritt meldet. |
| Aktuelle Prozesse | Schrittliste aus `CockpitAnimationTriggers` (echte Turn-, Werkzeug- und Worker-Ereignisse) |
| Live-Dialog | die letzten Nachrichten, die im Chat sichtbar sind |
| Gedächtniswerte | **offen**, kommt aus #371 (`ContinuumMemoryStatsReader`) |

Fehlt eine Quelle, zeigt die Karte „nicht verfügbar“, „noch nicht gestartet“ oder „Noch kein Auftrag“. Zahlen aus den Referenzbildern kopiert das Cockpit nie.

## Animations-Ereignisse: `cockpit/CockpitAnimationEvent.kt`
`CockpitAnimationBus` → `CockpitAnimationRenderer`. Die Ereignisse erzeugt nur `CockpitAnimationTriggers`, und das wird nur von echten Pfaden in `AgentActivity` aufgerufen:

| Ereignis | Auslöser |
|---|---|
| `CommandFlyIn` + `ActionStarted(THINK)` + `ProcessTimeline` | `TurnLifecycle.acknowledge()` im lokalen Turn |
| `ProcessTimeline` (Freigabe nötig, Blockiert …) | `ChiefTurnBridge`-Delegate, also `TurnLifecycle.milestoneOf(event.state)` |
| `ActionStarted(RESEARCH / TOOL)` + Schritt | Start im ToolGate-`ToolRunner` (`actualStart`). web_search und web_read zählen als RESEARCH. |
| Schritt fertig oder fehlgeschlagen | `finally` des ToolRunners (Fehlschlag, wenn eine Exception auftritt) |
| `ActionStarted(WORKER)`, `TurnEnded` | `ChiefWorkerPool.submit` angenommen bzw. `WorkerReport` |
| `TurnEnded` | `chiefTurn.deliver()` |
| `AccountCardFlyIn` (Gmail) | **Quelle fehlt:** In der App gibt es keinen Gmail-Konto-Pfad (`ContinuumReadinessGuide`: NOT_IMPLEMENTED). Die API `accountToolUsed()` ist getestet und wird nicht aufgerufen. Ein Werkzeugname allein löst sie nie aus. |
| `DocumentQuestion` (PDF) | **Quelle fehlt:** Es gibt keinen allgemeinen Pfad, der Dokumente oder PDFs aufnimmt. Die API `documentReceived()` ist getestet und wird nicht aufgerufen. |
| `Extension(kind)` | Erweiterungspunkt, z. B. `"math"` für Rechen-Visualisierung. Unbekannte Arten werden ignoriert. |

Sprechen und Lippensync laufen bewusst **nicht** über diesen Bus, sondern weiter über den Avatar-Vertrag.

### Visuals austauschen
- `CockpitTransitions.hook = CockpitTransitionHook { event, home -> … ; true }` ersetzt den Standard je Ereignis (für Welten-Optik und Jarvis Avatar). `home.viewFor(slot)` liefert die Karten-Views.
- Standard (`ui/CockpitAnimationPresenter.kt`), bewusst minimal:
  - Karten fliegen mit 280 ms ein, versetzt um je 45 ms
  - Aktionsknopf pulsiert und bleibt aktiv bis `TurnEnded`
  - Prozess-Karte wird 0,7 s hervorgehoben
  - Konto- und Dokumentkarte als vorübergehende Glaskarte, 6 s
- Nur ViewPropertyAnimator. Bei „Animationen entfernen“ und im Energiesparmodus werden die Werte trotzdem aktualisiert, aber ohne Bewegung.

## Tests
- `cockpit/CockpitAnimationTriggersTest` (7): rot → grün, Logs in `docs/evidence/jarvis-cockpit-20261104/`
- `ui/ContinuumCockpitPresentationTest` (5)
- `ui/ContinuumLandscapeCockpitLayoutTest` (4)
- Robolectric `ContinuumReferenceCockpitUiTest` (8, inkl. Fold-Querformat 840×649: echte Tipp-Ziele und Dialoge) und `ContinuumAdaptiveCockpitUiTest` (3)
- Vorschau `docs/evidence/jarvis-cockpit-20261104/fold-landscape-840x649-home.png`: Robolectric-Canvas, kein Filament und kein Gerät. In Robolectric fehlen Gerätewerte, deshalb steht dort „nicht verfügbar“.
