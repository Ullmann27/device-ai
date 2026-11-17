# Code55 – Übergabe an ChatGPT (Pro-Modus, Astra-Architektur) · 12.11.2026

Verfasser: Claude Opus 5.5 (Integrator). Grund: Budget des Besitzers fast aufgebraucht. Diese Datei ist der
**Einstiegspunkt**; Detailberichte der Agenten liegen in deren Zweigen unter `docs/handoff/CODE55-AGENT-*-UEBERGABE.md`.

## 0. Harte Regeln (unverändert gültig)
- Paket `at.heinz.deviceai.continuumlocal`, Originalsignatur, Originalavatar-GLB und Originalkugel **nicht ändern**.
- Kein Merge nach `main`, keine Signierung/Veröffentlichung ohne SHA-genaue Besitzerfreigabe, keine Force-Pushes.
- P12/Passwort nie lesen/drucken/kopieren. Keine Assertions entfernen/abschwächen. Keine erfundenen Ergebnisse.
- Status immer getrennt: **PORTIERT / HOST-VERIFIZIERT / GERÄTEBESTÄTIGT (Fold7)**. Hosttests ≠ Gerät.
- Gradle-QA (Host): `./gradlew --no-daemon -q -Pkotlin.compiler.execution.strategy=in-process -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests '<Muster>'`
  (gemeinsame Sperre `flock …/agent-gradle.lock`, nie mehrere schwere Läufe parallel). Version: `-PcontinuumStandaloneVersionCode=55` → 0.3.54.

## 1. Integrationslinie
- Zweig **`claude/code55-cinematic-evolution-20261112`**, Draft-PR **#791**, Basis Code54 `9713854bf` (`integrate/omega-apk54-20261111`).
- Letzter integrierter Stand bei Übergabe: siehe `git log` (≥ `af04d96ed`). Gitar: 7/7 Befunde geschlossen, freigegeben.
- **Volle Android-Suite auf Code55: NOT_RUN. APK55: nicht gebaut. Fold7: NOT_RUN.**

### In #791 enthalten (HOST-VERIFIZIERT, gezielte Klassen grün)
| Commit | Inhalt |
|---|---|
| `91fa82e4d` | #788 Diagnose/Cache-Vorschau/Negation übernommen |
| `24efd1d1f`, `d4e7c2452`, `1ecae3e33`, `e6fce9b87`, `caba9c83d` | Intelligence55: „Morgen wollen wir … starten“ = Gespräch; mehrteiliger Diagnoseauftrag lesend; „man/ihr + -t“-Befehle gesperrt, Fragen/Pläne/allgemeine Aussagen frei |
| `5770a1b34`, `a45009bd3` | Feld-P0: Akku-Satz landete in Web-Recherche (`PublicKnowledgeTopicRouter`) – behoben, beide Web-Eingänge getestet |
| `7e8c2b45e` | `get_battery_info` meldet Android-Thermalstatus oder „nicht verfügbar“ |
| `2b1dd0b13` | Dialekt-Ursache: Prompt verlangte „österreichisches Sprachgefühl“ → `OwnerLanguageStyle` (Standarddeutsch, „du“) |
| `2760bfde6`, `8aea6e852` | #785 Flugkontakt / #787 ThermalPhaseRecorder übernommen (**Rekorder noch nicht angeschlossen**) |
| `d46c286de`, `af04d96ed` | Kein rohes Markdown in Bubbles/Sprache; nativer Token-Überlauf (4220≥4096) ehrlich gemeldet, nicht im Verlauf gespeichert |
| `1c8e67421`, `b35a21195` | Vorher/Nachher-Hostbilder (Robolectric, **kein** Filament/GPU) |
| `339be4a38` | Besitzer-Designvorlage + Auftrag: `docs/design/continuum-live-computer-20261112/` |

### Offene Ursachen (nicht behoben)
1. **Token-Budget:** `LocalChatPromptBudget.FULL_CHARACTERS = 15_500` zählt System-Anweisung und Werkzeugdefinitionen
   (`LiteRtLmLocalModelRuntime.kt` ~Z.167, `tools = ToolRegistry.all()`) nicht mit → tokenbewusstes Budget nötig.
2. **Dialekt trotz Prompt** (Feld MATH-DE-01/02 betraf noch Code54-Prompt): Ausgabeprüfung auf Dialektmuster fehlt noch.
3. **Uhrzeit/Datum** gehen ans Modell statt an eine deterministische Geräteuhr-Antwort.
4. **„Gefundene Apps: WhatsApp … Welche davon?“** bei genau einem Treffer – Text unpassend.
5. **Grafik (#797):** Vorher/Nachher bei 2100 ms praktisch gleich – die Cinema55-Neugestaltung fehlt noch.

## 2. Agentenzweige (jeweils eigene Übergabedatei im Zweig)
| Agent | Zweig(e) | Auftrag |
|---|---|---|
| A | `claude/code55-agent-a-memory-reopen-20261112`, ggf. `…-agent-a-jarvis-motion-…` | Werkzeugkiste als Tatsachenaussage, Korrekturkette 12→17→21, Begrüßung nur bei neuer Sitzung (SAF-Picker erzeugt Activity-Neuerstellung!), kein stiller leerer Kontext; danach Jarvis-Bewegung an echte Ereignisse |
| B | `claude/code55-agent-b-pdf-dialog-20261112`, ggf. `…-agent-b-computer-intent-…` | PDF-Bezug (Name/Seiten/Seitentext/Turn-Revision) an Gespräch binden, alte Antwort nie für neue Frage; danach Computer-Auftragsparser |
| C | `claude/code55-agent-c-live-computer-20261112` | Live-Computer-Audit + Bausteine: Latest-Frame-Holder, Spiegel-Schleifenschutz, Wirkungsprüfung, MediaProjection-FGS-Gerüst, Accessibility-Aktionen ohne Senden |
| D | `claude/code55-agent-d-handsfree-20261112`, ggf. `…-agent-d-work-events-…` | Freisprech-Regression (Erkenner-Fehler/Ein-Aus-Schleifen); danach `cognitive/CockpitWorkEvent.kt` |
| E | `claude/code55-agent-e-3d-cockpit-20261112` | 3D-Cockpit: Kugel hinter Jarvis, Transparenz + Stirnlogo, 3D-Monitor mit Texturplatz, 8 Glasbuttons (Fold7 1×8, Phone 2×4) |

**Vorgehen beim Übernehmen:** Jeden Zweig einzeln prüfen (`git log`, Übergabedatei lesen), gezielte Tests laufen lassen,
dann per `git merge --no-ff` in `claude/code55-cinematic-evolution-20261112`. Mehrere Zweige ändern `AgentActivity.kt` →
Konflikte sorgfältig auflösen, danach Nachbartests. Nichts Rotes übernehmen.

## 3. Wie ich den virtuellen Computer weitergebaut hätte (Plan)
**Grundsatz:** drei getrennte, gekoppelte Teile – Bildquelle (A), Bedienung (B), Darstellung (C); verbunden über
`CockpitWorkEvent` (taskId, sessionId, Bildzeit). Kein zweiter Renderer, keine zweite Aufgabenverwaltung.

1. **Ereignisvertrag** (`CockpitWorkEvent`): TaskAccepted, GazeTarget, Click, Scroll, TypeText, Observation, Verified,
   AskOwner, Stop, Error. Gebundener Reducer: Ereignisse nach Stop verwerfen, alte taskIds ignorieren, keine Doppelaktion.
2. **Bildquelle (Fold7 zuerst):** MediaProjection mit Besitzerfreigabe, Foreground-Service
   (`foregroundServiceType="mediaProjection"`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`), `ImageReader` in begrenzter
   Auflösung → **nur neuester Frame** (`HardwareBuffer`/Surface, keine PNG/Base64-Kopien). Realistischer Darstellungsweg
   auf einem Gerät: **Fold7-Split-Screen** (Ziel-App + Continuum nebeneinander) oder Aufnahme mit Ausschnitt der Ziel-App;
   eigener Bildbereich wird ausgeschlossen (Spiegel-Schleife). FLAG_SECURE-Fenster: ehrlich „geschützt“ anzeigen.
   Normales Smartphone: App im Vordergrund, Continuum als kleine Überlagerung/Rückkehr – getrennt prüfen.
3. **Bedienung:** vorhandener `ContinuumScreenObservationService` (AccessibilityService): Knoten suchen → Aktion
   (Klick/Scroll/Text setzen) → **neue Beobachtung** → Wirkung bestätigt/unklar (unklar = nicht wiederholen, nachfragen).
   `AndroidNativeClick` geht von einem Fenster aus → für Mehrfenster Fenster-ID/Displaybereich der Ziel-App mitführen,
   Prüfungen nicht entfernen. Senden/Posten/Liken nur mit bestehendem ToolGate-Freigabetoken.
4. **Verständnis:** Parser „Bereite eine Nachricht an Anna vor: …“ → Plan (App, Schritte, Parameter, Modus
   Öffnen/Lesen/Entwurf/Senden); nur fehlende Angaben nachfragen; mehrdeutiger Kontakt → Rückfrage.
5. **Darstellung (Filament):** Monitor-Mesh mit Material, dessen Textur aus dem neuesten Frame gespeist wird
   (Stream/External Texture); LIVE nur bei frischem Frame der richtigen Sitzung, sonst PAUSIERT/VERDECKT/BEENDET.
   Klickring an der **verifizierten** Position, Blick/Schulter über `AvatarDirector` an dieselben Ereignisse.
6. **Erster Durchstich (Abnahme):** „Öffne WhatsApp und suche Anna“ auf Fold7 im Split-Screen: echte App-Pixel auf dem
   Monitor, echte Suche, Treffer sichtbar, Folgezustand bestätigt, kein Senden. Danach Facebook (nur lesen),
   Eigene Dateien/Downloads, Word, Excel über denselben Weg.
7. **Messen statt behaupten:** ThermalPhaseRecorder an Modell/Maya/Cinema/Capture anschließen; Wärme, FPS, Latenz
   auf dem Fold7 (Profil „Leicht“ vs. „Standard“ getrennt, ohne Audio-/Transkriptaufzeichnung).

## 4. Nächste Schritte in Reihenfolge
1. Agentenzweige A–E prüfen und integrieren (oben).
2. Volle Android-Suite auf dem Integrationsstand; unsignierte APK55 bauen und validieren (Validator wie Code54, Version 55/0.3.54).
3. Erster Live-Computer-Durchstich auf dem Fold7 (Gerätebeleg als Bildschirmaufnahme).
4. Erst danach Originalsignierweg (siehe `docs/release/CODE54-SIGNIERWEG-VORBEREITUNG-20261112.md` als Muster).
