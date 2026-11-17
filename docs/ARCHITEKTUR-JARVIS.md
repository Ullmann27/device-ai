# Jarvis-Architektur (eine Seite, Besitzer der Schnittstellen: Jarvis Kern)

**Regel:** Die Schnittstellen ändert nur Jarvis Kern. Wer eine neue braucht, fragt Kern.
**Auslöser** (wann etwas startet) gehören allein Kern: die Zeitachse im `CockpitAnimationPresenter`, getrieben von echten Ereignissen.
**Aussehen und Bewegung** gehören dem Besitzer des jeweiligen Stücks. Kern baut außer Bloom, Glas und Tiefe kein Aussehen.
**Übergabeformat:** „fertig: X auf Branch Y @sha, Einbau über Schnittstelle Z“.
**Grundregel:** Kein Code wird gelöscht. Was ersetzt ist, wird abgeklemmt und bleibt im Repo. Kein Force-Push, keine Branches löschen.

## Komponenten
| Baustein | Datei | Aufgabe |
|---|---|---|
| Ereignisbus | `cockpit/CockpitAnimationEvent.kt` (`CockpitAnimationBus`) | typisierte Ereignisse, Renderer-Fehler bleiben isoliert |
| Auslöser | `cockpit/CockpitAnimationTriggers.kt` | TurnLifecycle-, ToolGate-, ChiefWorkerPool-, Bereichs- und Alarm-Signale → Ereignisse |
| Verträge | `cockpit/CockpitContracts.kt`, `motion/MotionContracts.kt` | Schnittstellen für die Bots (unten) |
| Zeitachse/Presenter | `ui/CockpitAnimationPresenter.kt` | spielt zuerst den Hook (`CockpitTransitions.hook`), sonst einen minimalen, austauschbaren Standard |
| Shell (#479 @35a68f49) | `ui/JarvisShell*`, `JarvisDesign/Neon/Motion/Tiles`, `JarvisGlassRenderer` | Geometrie, Glas, Bewegung; `JarvisShellView` ist die unterste Ebene in `ContinuumHomeView` |
| Inhalte | `ui/JarvisShellContent.kt`, `ui/ContinuumCockpitPresentation.kt` | Werte für Slots und Kacheln (fehlt etwas: „nicht verfügbar“) |
| Landscape-Cockpit | `ui/ContinuumLandscapeCockpit*.kt` | ref1/ref3-Komposition (Spaltenaufteilung als Kern-Konstante) |

## Ereignisse (`CockpitAnimationEvent`)
CommandFlyIn, ActionStarted, ProcessTimeline, TurnEnded (TurnLifecycle/ToolGate/Pool) · AccountCardFlyIn, DocumentQuestion (Quelle fehlt) · DocumentLearned, MemoryStored(count), RecallHits (`MEMORY_TURN`) · CalculationShown (Muster E) · AreaChanged(`NAV_TURN`, TransitionPattern A_DEPTH/B_FLY_IN/C_WARP) · AlarmRaised/AlarmCleared(`ALARM_TURN`, AlarmKind) · Extension(kind).
Sprechen und Lippensync laufen nicht über den Bus, sondern über den Avatar-Vertrag (AvatarDirector → `JarvisAvatarSlot.fanOut`).

## Schnittstellen für die Bots (wer implementiert was)
| Schnittstelle | Implementiert | Kern liefert |
|---|---|---|
| `JarvisAvatarRenderer` (attach/onAvatarState/onAnimationEvent/detach, optional onSlotChanged, onPresentation(AvatarPresentation HEAD/FULL_BODY_ORB), onAvatarEvent) + `JarvisAvatarSlot.register` | **Jarvis Avatar** (Kopf 1/10/11, Datei-Icons aus dem Kopf in Szene 2) | EIN Slot. Ohne Renderer ein leerer Platzhalter „Jarvis Avatar – Renderer nicht angedockt“. Unser eigener Kopf/Orb (ContinuumHologramView) ist in den Cockpit-Modi abgeklemmt, nicht gelöscht |
| `CalculationEventSource` → `CalculationShown`, `CalcStep`, `ChartKind` | **Jarvis Gedächtnis** (Orb, Datenflug E, sichtbares Rechnen, Widgets-Einflug) | Auslöser, minimale Karte als Standard |
| `JarvisNavigator`, `CockpitActionHandler`/`ActionAvailability`, `triggers.areaChanged()` | **Jarvis Integration** (Rail ref5, B-Bewegung, Szene A, Kameradrift, Aktionsleiste, Szene 3b) | AreaChanged → Hook, sonst playEntrance()/minimale Tiefe |
| `PillActionGate` (AnswerPill/AnswerPillKind), `AccountEventSource` | **Jarvis Sicherheit** (Umschlag F, Pills G, Laser D mit Funken) | nur ToolGate plus ausdrückliches Owner-Ja, nie Auto-Ausführung |
| AlarmRaised/Cleared, C_WARP, AvatarPresentation | **Jarvis Lückenfüller** (Video 3) | Signale: Thermal-Status (API 29+), onTrimMemory, Worker-Warteschlange voll. Hub-droppedEventCount folgt mit #371 |
| `Easing`, `SpringCurve`, `Stagger`, `MotionTrack`, `MotionTimeline` (nur Interface), `FaceWeightsSource` (21 Blendshapes, #478-Reihenfolge), `VisemeHintSource`, `MotionLibrary` | **Jarvis Mathematik** (pure Kotlin, auf JarvisMotion) | Fallback auf JarvisMotion. `AudioLevelSource`-Produktion = TtsHologramSignal (0..1, Stille = 0, Uhr elapsedRealtime) |
| Bloom, Glas, Tiefenschärfe, Zeitachse, Szene 1 (Auslöser) | **Jarvis Kern** | — |

Fold7 offen: breite beschriftete Rail in beiden Ausrichtungen. Die Bottom-Bar gibt es nur auf Cover-Display und Phone (Chef-Entscheidung; Umsetzung Integration auf #483).

## Datenquellen
TurnLifecycle (Phase), ChiefWorkerPool.snapshot()/capacity (nur lesend, unveränderliche Kopie, siehe docs/CHIEF-PATTERN.md), ToolGate-Runner (Tool-Schritte), ContinuumDashboardCollector (CPU, RAM, Speicher, Akku, Akku-Temperatur), ContinuumMemoryStatsReader (#371, gedrosselt alle 15 s, nie pro Frame), TtsHologramSignal (Audiopegel).

**Memory-Events:** Die vereinbarte Signatur lautet `ContinuumMemoryEvents.forApp(appDirectory).addListener(ContinuumMemoryEventListener): AutoCloseable` mit DocumentLearned/MemoryStored/RecallHits. Der Listener läuft außerhalb des Main-Threads. `MemoryEventMapper` bündelt deshalb atomar und spült höchstens alle 100 ms auf dem Main-Thread. Nach `detach()` werden Nachzügler ignoriert.
Geliefert ist es auf #371 @de333693, aber **auf diesem Branch noch nicht produktiv**: #371 baut auf dem LEX4-Ledger, die Basis (ChatGPT) auf dem segmentierten LEX3. Cherry-Pick oder Merge kollidieren in ContinuumLearningExperience und ContinuumThinkingMemory, die Zusammenführung gehört Gedächtnis/Integration. Danach ist `MemoryEventSources.production` die Bridge aus `MemoryEventSourceTest.asSource()`.
PDF: Im Repo gibt es keinen PDF-Import, daher bleibt DocumentQuestion „Quelle fehlt“.

## Quelle fehlt
DocumentQuestion (kein PDF-Import) · AccountCardFlyIn (kein Gmail-Pfad, wartet auf `AccountEventSource`) · Memory-Events produktiv (Ledger-Linien) · Kacheln Recherche/Lernen (kein Live-Zähler) · Anrufen-Pill (kein Tool) · Mikrofonpegel (nur TTS-Pegel).

## Lücken an #479-Schnittstellen (jetzt an Avatar/Integration)
Kein Hit-Test und keine Accessibility pro Slot oder Kachel (Kern spiegelt die Geometrie von drawRail/drawBottomNav) · Platzhalter werden auch ohne Inhalt gezeichnet · `onDraw` erzeugt jeden Frame das Shell-Layout · keine Spaltenaufteilung für das ref1/ref3-Cockpit · Shell-Stage ist opt-in (`jarvis-shell`), solange Touch und Accessibility fehlen.

## Branches und PRs
#481 `design/jarvis-cockpit-home-20261104` (Kern, Entwurf) · Basis `fix/continuum-device-experience-resume-20261104` (ChatGPT, #477) · #479 `grok/jarvis-design-system` @35a68f49 · #371 `continuum/m2-cognitive-memory` · #483 (Integration) · `jarvis-grafik/avatar-ref-match` (Avatar, JarvisSequence).
