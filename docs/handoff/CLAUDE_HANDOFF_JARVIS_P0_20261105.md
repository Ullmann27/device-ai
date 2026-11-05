# Weiterbearbeitungsprotokoll – JARVIS-Cockpit & P0-Fixes (05.11.2026)

Branch `claude/continuum-jarvis-cockpit-z7bo60` · PR #565 (Draft, Basis `research/opus55-continuum-voice-hud-preflight-20261105`).
Letzter Code-Stand vor diesem Protokoll: `1de80794`. Kein Main-Merge, keine Signatur-/Paket-/Versionsänderung, keine
Veröffentlichung. **Alle Belege sind Host-Tests (JVM/Robolectric); auf dem Fold7 wurde nichts davon geprüft.**

## Erledigt in dieser Sitzung (neueste zuerst)

| SHA | Inhalt | Nachweis (Host) |
|---|---|---|
| `1de80794` | #567: Gedächtnisfragen bleiben lokal. `PublicKnowledgeTopicRouter` sendet keine Gesprächsbezüge („habe ich dir“, „du … hast“) mehr an die Websuche; `BootstrapBrain` wertet verneinte Verben („starte keine …“) nicht mehr als Geräteauftrag (Ausnahme entfällt bei sondern/stattdessen/außer/aber); Recall-Fragen lesen das geprüfte Ledger und zeigen nur passende Fakten oder ehrlich „nicht gefunden“. | RED `expected null, but was:<testwort habe dir gerade genannt>` → GREEN `OwnerRecallRoutingFold7Test` 3/3; mit Bootstrap/Router/Negation/Injection/OwnerFact 151/151 |
| `83876fec` | #569-Review: abgewiesener Zweitstart verdrängt die laufende Antwort nicht mehr aus dem Wärmewächter. | RED `[Cancelled]` → GREEN 4/4, `localmodel` 81/81; Gitar bestätigt |
| `d681f100` | #569: `LocalModelThermalGuard` vor der einen Prozess-Laufzeit (`LocalModelHost`): kein Start ab Android-`THERMAL_STATUS_SEVERE`, laufende Antwort stoppt einmal mit ehrlichem Grund, kein Retry/Nachstart. | RED 3 Fälle → GREEN 3/3, `localmodel` 80/80, Activity-Tests 79/79 |
| `8d5211d4` | `docs/research/omega/JARVIS_SCENE_MATRIX_20261105.md`: Quellen, Rechte, Adapter, Belegstufe je Szene. | – |
| `4c3019a0` | `JarvisActionScenes` (universeller Präsentationsadapter); alle START-Center öffnen das Arbeitsfenster aus der berührten Kachel bzw. neben dem Gesicht; Avatar blickt hin und zurück. | neuer UI-Test + 91 angrenzende Tests |
| `8fb1d87f` | #568-Review: `ctx-reset`-Marker, Neustart holt verworfenen Modellkontext nicht zurück. | RED → GREEN, 44/44 |
| `21d7fde4` | Host-Bewegungsclips (Cockpit, Original-GLB) + `tools/avatar-host-render/`. | als Host-Render gekennzeichnet |

## Offene Schritte in Besitzer-Reihenfolge

1. **#567 Rest (P0-12):** Allgemeine Wissensfrage bei **nicht geladenem** Modell liefert `ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER`
   („keine bestätigte Erinnerung …“). Erwartet: klarer Hinweis „lokales Modell nicht bereit“ statt Gedächtnisfehler.
   Ansatz: im Activity-Pfad `runContinuumDialogue` (Startkern ohne READY-Modell) bei NO_EVIDENCE + keiner Besitzer-Recall-Form
   den Modellstatus melden; RED zuerst. Fundstellen: `learning/ContinuumMemoryDialogue.kt:111,235`, `learning/ContinuumBrain.kt:256-308`.
2. **#569 Rest:** PowerManager-Headroom (API 30, `getThermalHeadroom`, rate-limitiert) als Vorwarnung; Umgang mit MODERATE
   (z. B. kleineres Ausgabebudget über `LocalModelGenerationPolicy`); prüfen, ob ein Ausweichpfad in derselben Anfrage nach einem
   Wärmestopp einen zweiten Lauf startet. Fold7-Messung nur abgekühlt und mit Besitzerzustimmung; keine Zahlen erfinden.
3. **#566 / #568 Fold7-Abnahme:** echte TTS-Folge, STOP, lange Antworten; Chat nach App-Ende/Prozesstod; Verlaufsverwaltung/Löschen-UI
   fehlt noch (`LocalChatTranscriptStore.delete` existiert, keine Oberfläche).
4. **Bots:** Fold7 zeigte „Lokales Modell nicht geladen“ bei Bot-Karten trotz früherer Gemma-Antworten – Status-Quelle prüfen
   (`ChiefBotProfileStore`, Bot-Karten, `localRuntime.state`). Danach einen kleinsten echten Auftragspfad mit Quittung.
5. **Werkzeugfenster:** `ToolGate`-Runner (`AgentActivity.createToolGate`, `cockpitTriggers.toolStarted/toolFinished`) →
   `JarvisActionScenes.forTool` → `JarvisWorkWindowHost.show(..., scene)` mit echtem Werkzeugergebnis, z. B. `get_storage_info`.
   Fehlschlag bernstein, Erfolg nur nach echter Rückmeldung; Gmail/PDF haben keine Quelle (siehe Szenenmatrix).
6. **Gates vor Code44:** volle Android-Suite, Node24, Backend, NewApi-Lint und signierter ARM64-Preflight auf exakt demselben SHA
   (nur über Render-QA-Host oder nachweislich funktionierenden lokalen Build, nicht über GitHub Actions); erst dann Besitzerfreigabe.

## Arbeitsregeln für die Fortsetzung
- Vor jedem Patch `git rev-parse HEAD`, `git status`, `git fetch` und Vergleich mit `origin/claude/continuum-jarvis-cockpit-z7bo60`.
- Testlauf im Container: `./gradlew --no-daemon -q [-PcontinuumUiWidgetTests=true] :app:testDebugUnitTest --tests '…'`
  (Google-Maven-Spiegel in `~/.gradle/init.d/`; die volle Suite kann den Worker beenden, deshalb gezielt testen).
  Robolectric hat hier nur API 28 – Android-Thermal-APIs (ab 29) daher nur als JVM-Test über `LocalModelThermalGuard.wrap`.
- Original-GLB (`c8b88385…`), `ContinuumHologramRenderer` (transparent), Paket `at.heinz.deviceai.continuumlocal`,
  Signatur und Updatekanal unverändert lassen.
