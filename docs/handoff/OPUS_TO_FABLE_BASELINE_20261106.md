# Fortsetzungsprotokoll für Claude Fable 5.1 – JARVIS-Cockpit (Stand 06.11.2026, Sitzungsende)

**Repository:** `Ullmann27/device-ai` · **Branch:** `claude/continuum-jarvis-cockpit-z7bo60` · **Draft-PR:** #565 (bleibt Draft)
**Letzter Code-Commit:** `4d84caa8bbaefb7bf8d154edd6902e1b07ce2218` – dieses Protokoll liegt im unmittelbar folgenden
Dokumentations-Commit (`git log -1 -- docs/handoff/FABLE51_CONTINUATION_20261106.md`).
**Nachweisniveau:** alles **HOST** (Robolectric/JVM auf dem Linux-Build-Host). **REAL_DEVICE_NOT_RUN** für jeden
Fold7-Punkt; **MODEL_ANSWER_NOT_RUN** für jede Aussage über echte Modellantworten. Kein Bild in den Belegordnern ist ein
Gerätebild oder ein Filament-GPU-Bild.

> Lies vor jeder Änderung: `CLAUDE.md` (bindend, u. a. keine GitHub-Actions-Abnahme, Render-QA-Host, P12 nie auf GitHub),
> `AGENTS.md`, dieses Protokoll, `docs/handoff/CLAUDE_HANDOFF_JARVIS_20261106.md` (Commit-Tabelle des Tages),
> `docs/research/omega/JARVIS_ARCHITECTURE_MAP_20261106.md`, `docs/handoff/FOLD7_ABNAHME_JARVIS_20261106.md`.
> Bei Widerspruch gilt der **tatsächliche Code am Branch-HEAD** vor jeder Dokumentation.

---

## 1. Unverrückbare Grenzen (vom Besitzer, gelten weiter)

- Kein Merge nach `main`, keine Veröffentlichung/kein Release, keine APK-Signierung aus dieser Arbeit, keine Änderung
  an Paket-ID `at.heinz.deviceai.continuumlocal` oder Originalsignatur, keine kostenpflichtigen Dienste.
- Draft-PRs offen lassen (#565, #571, #572). PR #570 nicht anfassen. Kein Push auf andere Branches ohne Freigabe.
- Der separate „Gehirn“-Entwicklungschat arbeitet auf eigenen Branches: **keine konkurrierenden Änderungen**, keine
  Merges seiner Arbeit, keine Gewichtsübernahme (z. B. PR #530 = Speicherung von Modell-/Optimiererzustand, **kein**
  Trainings- oder Qualitätsnachweis).
- Nur der Original-GLB-Kopf (`app/src/main/assets/avatar3d/continuum-avatar.glb`, SHA-256
  `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`) und die originale Continuum-Kugel
  (`ContinuumHologramRenderer`); dunkle Glasoptik, Cyan/Blau/Violett/Magenta. Kein neues Design, keine Ersatzgrafik.
- Animationen sind Architektur: jede Funktion braucht Bedienbarkeit, Gedächtnisanschluss und holographische Darstellung,
  aber **nur aus echten Zustandsänderungen**. Nie „fertig“, während etwas läuft oder scheiterte. Keine zweite
  Animations-/Choreographer-Uhr.
- Host-/Blender-Bilder ausdrücklich kennzeichnen; sie ersetzen keine Android-Abnahme. Keine erfundenen
  Temperaturwerte, keine vorgetäuschten App-Verbindungen, keine erfundenen Vorführdaten, private Besitzerbilder nie ins
  Repository.
- Keine Behauptung einer Intelligenzsteigerung ohne Messung an echten Modellantworten auf unveränderten Kontrollaufgaben.

---

## 2. Was heute funktioniert (HOST-geprüft) – Funktionen und Animationen

### 2.1 Avatar (Original-3D-Kopf) – eine Absichtskette
- **Kette:** `AvatarDirector` (Ereignisse → Absichten) → `AvatarPoseReducer` (in `avatar/AvatarContract.kt`, faltet
  Ereignisse zu `AvatarPose`) → `avatar3d/AvatarMorphMapper` (Kopf-/Augen-/Kieferwinkel, Morph-Gewichte) → Filament-Kopf
  bzw. 2D-Vorschaukopf in `ContinuumHologramView`.
- **Blick:** Augen führen, Kopf folgt (τ = 0,18 s), Hals trägt 65 % der Drehung (`NECK_SHARE`), Begrenzung der Winkel.
- **Zustände:** IDLE, LISTENING, THINKING (fokussierter Ausdruck, kurze Denk-Mikrobewegung), RESEARCHING, LEARNING,
  WAITING_FOR_APPROVAL, SPEAKING, BLOCKED (`AvatarDirector.onRuntime`).
- **Fensterblick:** `windowOpened` (2,5-s-Blick), `windowMoved` (Blick folgt neu geplantem Fenster), `windowClosed`.
- **Lesen während echter Arbeit:** `windowWorkStarted/windowWorkEnded` halten den Blick lesend auf dem Fenster, solange
  das Modell wirklich arbeitet; Augen fahren Zeilen ab (`AvatarMorphMapper.readingYaw`, nur Augen, Kopf ruhig), bei
  reduzierter Bewegung keine Augenfahrt. Getrennte `workIntent`: nach Zuhören/Sprechen/direktem TTS-Start kehrt das
  Lesen zurück, solange die Arbeit läuft (`resumeWork`); STOP (`clearAnimationIntents`), Arbeitsende, Fensterschluss und
  verspätete Sprachereignisse beenden es endgültig.
- **Bestätigung:** `confirmationAccepted` → kurzes Nicken (z. B. verstandene Korrektur).
- **Sprechmimik:** aus TTS-Wortbereichen (`speechStarted`/`wordRange`/`speechEnded`), Frage-Brauen, Satzende-Nicken,
  Ausklingen, Blinzeln an Wortgrenzen; veraltete Äußerungs-IDs werden verworfen; Kiefer aus gemessener PCM-Hüllkurve nur,
  wo gemessen (`audioLevel(..., measuredPcmAvailable)`), Viseme aus Text **angenähert** (kein Phonem-Timing).
- **Kamera (#571):** `AvatarCameraMotion` über `ContinuumHologramView.setWorkWindowCamera(active, direction, immediate)`.

### 2.2 Originale Continuum-Kugel
- `ContinuumHologramRenderer` (Original), Signale über `CockpitAnimationBus` → `CockpitAnimationPresenter` →
  `JarvisHoloAvatar` → `JarvisSequenceView`/`JarvisSequenceEffects` (`recallLight`, Pulse, Hypothesenmarker).
- Erinnern sichtbar: Arbeitsverlauf-Rückbezug sendet `RecallHits` mit **echter** Trefferzahl (0 = kein Licht).

### 2.3 Holographisches Cockpit und Arbeitsfenster
- `ContinuumHomeView` (JARVIS-Shell-Bühne, Deck, Avatar-Slot), `JarvisShellView` (`observeMotionFrames` = die gemeinsame
  Frame-Uhr; `currentShell()`), `JarvisDeckClearance` (Deck räumt bei offenem Fenster, kehrt zurück).
- `JarvisWorkWindow` + `JarvisWorkWindowHost`: Fenster entfaltet sich auf einer Lichtbahn aus Kachel/neben dem Gesicht,
  Planung mit Schutzbereichen (Gesicht, Dialog, Kugel), Neuplanung bei erstem Layout in anderer Größe, Falten/Drehen.
  Titel wird einzeilig, wenn sonst die höchste Aktion nicht ganz passt (#572-Ursache). **Statusleiste** im Fensterkopf
  (`jarvis-work-window:status`, Live-Region) zeigt die Schritte des wirklich laufenden Zugs
  (`CockpitAnimationTriggers.activeTimeline`); bei zu wenig Platz weicht sie, statt Bedienelemente zu verdecken.
- **App-Kristall:** echtes App-Symbol (`ToolAppIdentity`, PackageManager) fliegt in einem Prisma (Facetten
  violett/magenta + cyan, Gürtelfacetten, Grat, Glanzstreif) auf der Lichtbahn; statische Pfade/Füllungen, keine
  Allokation pro Frame; Grat/Gürtel bleiben außerhalb des Symbols.
- **Statuskarten frei vom Avatar:** Karten docken bei offenem Fenster ins Fenster; ohne Fenster prüft
  `JarvisSequenceEffects` den Kartenstapel geometrisch gegen die echten Kopf-/Kugelbereiche (`setProtectedAreas`) und
  zeichnet ihn dort nicht; Schritte stehen dann im Panel „Aktueller Ablauf“. „Auftrag angenommen“ erscheint je Schritt
  nur einmal (`ActionStarted.stepKey`).

### 2.4 Dokumentablauf (eine durchgehende Szene)
- „Lies diese PDF …“ → nur Android-Dateiauswahl (SAF, dauerhafte Lesefreigabe) → `PdfDocumentIntake` (PdfRenderer; Text
  ab Android 15 über `PdfRenderer.Page.getTextContents()`) → Dokumentfenster (Seite auf der Lichtbahn, Name/Größe/Seiten,
  Auszug, zweispaltig ≥ 600 dp).
- Entwurf: `DocumentDraftPrompt` (nur Dokumenttext, Budget 7 900) über den **vorhandenen** `runLocal`-Pfad;
  JARVIS liest während des Laufs; ENTWURF ERSTELLEN gesperrt; Ergebnis entfaltet sich **aus** dem Dokumentfenster mit
  Quellenkarte (erste Seite); BEARBEITEN (Dialog) und ALS DATEI SPEICHERN (SAF).
- Ehrliche Enden: Modellfehler/Wärmestopp → FAILED + Text im Fenster, Cockpit bleibt; STOP → ABORTED, Fenster weg,
  „Entwurf mit STOP abgebrochen – nichts gespeichert.“; Doppeltipp ändert nichts; Spätantwort nach STOP verworfen;
  verbrauchte STOP-Markierung färbt keinen späteren echten Fehler um.
- „Nimm die letzte PDF …“: gemerkte Datei über das Arbeitsjournal; ist sie weg → Datei wird genannt, Fehlversuch FAILED
  aufgezeichnet, Auswahl geöffnet, Cockpit bleibt.

### 2.5 Arbeitsgedächtnis (getrennt vom Faktengedächtnis)
- `cognitive/CockpitWorkJournal` (app-privat `cockpit-work/journal-v1.jsonl`, 40 Episoden / 256 KB, atomar):
  Auftrag in Besitzerworten, Quelle, Zustand READ/DRAFTING/DRAFTED/SAVED/ABORTED/NO_MODEL/FAILED/INTERRUPTED,
  Bearbeitungen, Korrektur, nächster Schritt; `recoverInterrupted(processStartedAt)` macht Entwürfe früherer Prozesse
  zu INTERRUPTED; `openSteps` → Panel „Aktueller Ablauf“ („Offen · …“, nie als laufend).
- `WorkRecall` („Wo waren wir gestern/heute …“, „Was ist noch offen“, „Was haben wir gerade/eben gemacht“),
  `OwnerCorrectionIntent` („Nein, ich meinte den letzten Entwurf / die vorige PDF“), `OwnerDocumentIntent`.
- **Seit 4d84caa8:** alle Journal-Lesezugriffe laufen auf der seriellen Dokument-Spur (`documentIoExecutor`) nach allen
  vorher eingereihten Schreibvorgängen (`AgentActivity.withWorkJournal`), Fortsetzung nur bei unveränderter Sitzung;
  kein Datei-/JSON-Zugriff auf dem UI-Thread.
- Herkunftskennzeichnung: „Aus dem Arbeitsverlauf dieses Geräts – Erinnerung, kein Modelllernen.“ Journal-Episoden
  werden **nie** zu bestätigten Nutzerfakten (die bleiben in `learning/ContinuumTemporalMemory`).

### 2.6 Gesprächsverständnis (vorhandene Komponenten, nicht neu gebaut)
- `LocalConversationFollowUpPolicy` erkennt natürliche Anschlüsse (Kontrast „nicht vier, sondern fünf“, Pronomen nur
  kleingeschrieben – höfliches „Sie“ ausgenommen –, „nochmal“ nur nach Erinnerungsverb) → `LocalFollowUpTaskFrame` legt
  frühere Besitzeraussagen in die Modellaufgabe; `OwnerStatementTimeline` bleibt bei verneinten Zahlwörtern fail-closed.
- Katalog `ConversationUnderstandingCatalogTest` nach Schichten: Routing ✓, Kontext im Prompt ✓, **Modellantwort
  NOT_RUN**, Werkzeuge keine.

### 2.7 Lokales Modell, Wärme, Sprache
- `LocalModelRuntime` (LiteRT-LM, `LiteRtLmLocalModelRuntime`), `LocalModelThermalGuard` (STOPPED/PAUSED_THERMAL),
  `runLocal` → `generateLocal` → `deliverResult` in `AgentActivity` (Abbruch über `activeChatCancellation`).
- TTS: Antworten in Stücken (`JarvisVoiceProfile.speakableChunks`, max. 16, Hinweis „Der Rest der Antwort steht im
  Chat.“), `UtteranceProgressListener` → Avatar; STT: `AndroidOnDeviceSpeechInput`; Freisprechen
  (`CockpitConversationPresentation.keepHomeForResult`).

---

## 3. Heute erledigte Arbeiten (Auszug, chronologisch; vollständige Tabelle in `CLAUDE_HANDOFF_JARVIS_20261106.md`)

| Commit | Ergebnis |
|---|---|
| 5162de02, 6fd740a5 | Fenster überlebt erstes Layout in anderer Größe; Falten/Drehen gibt Deck zurück |
| de9d249c | PR #571 Kamerafahrt × Sprechmimik × STOP zusammengeführt |
| dfd30a38, 597603e7, 7433100a | Dokumentaufnahme, Entwurf über lokales Modell, Speichern, zweispaltiges Fenster |
| c8044585, d5c2d7f2, 4059f076 | Arbeitsjournal, Korrekturen, Erinnern sichtbar (RecallHits, Nicken) |
| 29962f3c | Blick folgt neu geplantem Fenster (`windowMoved`) |
| e23b92fb, 6c113503 | natürliche Anschlussfragen; höfliches „Sie“/„nochmal“ keine Anschlüsse |
| 3051bdee | Lese-Blick während echter Arbeit |
| 06dd8b1f, 4ac25d11 | #572-Ursache (Titel frisst Scrollhöhe bei 200 %) behoben; #572-Test übernommen (12/12) |
| 9c76c53e | INTERRUPTED nach Neustart; offene Schritte im Panel |
| 4e753b73, b48e6ffd | durchgehender Dokumentablauf, Fehler/STOP/Doppeltipp ehrlich, App-Kristall, Belege |
| f8af86c9, a5ec765e | Gitar-Befunde: STOP-Markierung, Kristallgrat über dem Symbol |
| b6236eaf | Rückkehr zum Lesen nach Unterbrechung |
| 79c757d9 | verschwundene gemerkte PDF: benannt, aufgezeichnet, Auswahl, Cockpit bleibt |
| 35f6b851 | Statusleiste im Fenster; doppeltes „Auftrag angenommen“ beseitigt |
| 6c598af8 | Fold7-Prüfanleitung (`FOLD7_ABNAHME_JARVIS_20261106.md`) |
| 70a78849 | direkter TTS-Start löschte `workIntent` (Lesen ging verloren) – behoben |
| 1a4adb60 | **Statuskarten-Korrektur abgeschlossen:** Kartenstapel geometrisch gegen echte Kopf-/Kugelbereiche, auch ohne Fenster |
| 4d84caa8 | Journal-Lesen auf serieller Spur (Erinnerung direkt nach STOP korrekt); „gerade/eben“ erkannt |

Jeder Fehler-Commit hat einen RED-Nachweis auf dem alten Code und ein GREEN danach (Commit-Texte nennen Test und
Meldung). Die zuvor „laufende“ Statuskarten-Korrektur ist **abgeschlossen** (35f6b851 + 1a4adb60); nichts davon ist
unfertig im Arbeitsbaum.

---

## 4. Tatsächlich ausgeführte Prüfungen (HOST)

| Lauf | Stand | Ergebnis |
|---|---|---|
| Breiter Regressionslauf | 4e753b73 | 201 Klassen / 1 154 Tests, 0 Fehler |
| #572-Klasse `ContinuumReferenceCockpitUiTest` | b48e6ffd und 35f6b851 | je 12/12 (≈ 885 s) |
| `ContinuumVisualEvidenceUiTest` | b48e6ffd | 4 bestanden, 1 übersprungen (Grund nicht ausgewertet), 0 Fehler |
| Status-/Sequenz-Slice | 35f6b851 | 25 Klassen / 145 Tests, 0 Fehler |
| Absicherung | 35f6b851 | #572 12/12, Statusfenster 10/10, Dokumentszene 8/8, Freisprechen 2/2 |
| TTS-Slice | 70a78849 | avatar.* + avatar3d.* 168/168; Sprach-/Avatar-UI 23/23 |
| Karten ohne Fenster | 1a4adb60 | 23 Klassen / 133 Tests, 0 Fehler |
| Gedächtnis-Slice | 4d84caa8 | 28/28 (Ablauf 8/8, Dokumentszene 8/8, Journal/Recall/Korrektur/Dokument-Intent) |
| **Abschluss-Regression** | 4d84caa8 | abgebrochen, kein Ergebnis (Abschnitt 4.1) |

### 4.1 Abschluss-Regression
**Abgebrochen, kein Ergebnis.** Die breite Abschluss-Regression auf `4d84caa8` (avatar.*, avatar3d.*, cognitive.*,
cockpit.*, Sequenz-, Fenster-, Dokument-, Sprach- und #572-Klassen) wurde beim Sitzungsende auf Besitzerwunsch
gestoppt (Exit 143), bevor Testergebnisse geschrieben waren. Auf `4d84caa8` belegt sind nur die 28/28 des
Gedächtnis-Slices; die Läufe davor siehe Tabelle. **Erste Aufgabe für Fable:** diese Regression erneut ausführen.

### 4.2 Nicht ausgeführt
- **Keine** Gerätetests (kein ADB-Gerät), **keine** echte Inferenz (kein LiteRT-Modell auf dem Host), **keine**
  echte Sprachausgabe/-erkennung, **kein** Filament-GPU-Bild, keine Framezeiten/Wärme-/Akku-/Speichermessung.
- Volle Android-Suite seit 4e753b73 nicht vollständig erneut gelaufen (nur die Läufe oben).
- Node-Quellverträge `.github/continuum/*.test.mjs`: zuletzt 1 498 pass / 53 fail / 23 skip – **identisch** auf
  25256632 (vorbestehend, nicht von heute); heute nicht erneut ausgeführt.
- GitHub Actions werden laut `CLAUDE.md` nicht als Abnahme gewertet (Checks auf PR #565: Gitar/CodeRabbit).

---

## 5. Bekannte Fehler, Schwächen, unvollständige Integrationen

1. **Keine installierbare APK dieses Stands.** Laut `CLAUDE.md` nur über den Render-Android-QA-Host
   (`continuum-m8-android-qa-20261031`), der noch auf einem älteren Branch steht → muss kontrolliert auf diesen Branch
   umgestellt werden (Besitzerentscheidung/Zugang nötig). Ohne APK bleibt jede Fold7-Zeile NOT_RUN.
2. **Modellqualität ungemessen:** Gesprächskatalog nur bis „Kontext im Prompt“; echte Antworten MODEL_ANSWER_NOT_RUN.
3. **Statusleiste weicht in engen Fenstern** (z. B. Telefonfenster 380 × 165 px bei 200 %): dann sind die Schritte bei
   offenem Fenster nirgends sichtbar (Deck geräumt). Verbesserung: kompakte Inline-Variante im Fenster.
4. **Kartengeometrie:** In der Shell-Bühne decken Kopf+Kugel die ganze Hologrammfläche → Karten werden dort nie
   gezeichnet (Schritte im Panel). In Layouts **ohne** `setCockpitRegions` (Legacy/Telefon) gibt es keine Schutzbereiche →
   Karten erscheinen wie früher mittig. Auf dem Gerät prüfen, ob dort Gesicht/Kugel verdeckt werden.
5. **Dokument-Hervorhebung nicht umgesetzt (bewusst):** geprüft – verfügbar sind Seitenzahl, Text je Seite, Länge,
   `coveredChars/totalChars` des Prompts; der Modellstrom liefert nur `TextDelta`/`Completed`/`Failed`/`Cancelled`,
   **keine** Rückmeldung je Abschnitt. Daher nur neutraler Bearbeitungszustand + Lese-Blick. Ehrlicher nächster Schritt:
   anzeigen, welche Seiten/Zeichen im Modellauftrag enthalten sind (nicht „analysiert“).
6. **Viseme angenähert:** Mundformen aus Text/Wortbereich, kein Phonem-Timing; bilabialer Verschluss (M/B/P) nicht aus
   echter Zeitinformation belegt. Turn-Taking (ausreden lassen, Unterbrechung erkennen, keine Selbsttranskription) offen.
7. **Gedächtnisbrücke fehlt:** `CockpitWorkJournal` ↔ `ContinuumTemporalMemory`/`HybridMemoryRetriever` (Projekt-
   entscheidungen, verworfene Überlegungen, überholte Angaben) nicht angebunden.
8. **Planen → Ausführen → Beobachten → Prüfen → Korrigieren** (Besitzer-Meilenstein 3, z. B. Speicher-Aufräumvorschlag)
   nicht begonnen; vorhandene Bausteine: `ChatReasoningRouter`, `BoundedAgentOrchestrator`, `ToolGate`, Quittungen.
9. **In-App-Diagnose/Abnahme** (Besitzer-Meilenstein 1, datensparsame lokale Prüfbelege) nicht gebaut.
10. **App-Kristall** nur im Flug; als ruhendes Prisma im Werkzeugfenster offen.
11. **Gitar-Review** der letzten Commits (70a78849, 1a4adb60, 4d84caa8, Protokoll) zum Sitzungsende noch nicht
    ausgewertet → zuerst PR #565 Review-Threads prüfen.
12. Volle `cognitive.*`-Suite und breite Regression nach 4d84caa8 nicht abgeschlossen (4.1) – zuerst nachholen.

---

## 6. Priorisierte nächste Aufträge (Reihenfolge einhalten)

1. **Stand sichern:** `git fetch`; HEAD = Protokoll-Commit prüfen; PR #565 offene Review-Threads (Gitar) lesen und jeden
   echten Befund RED → GREEN beheben. Nichts aus Abschnitt 2/3 neu bauen.
2. **Geräteabnahme ermöglichen:** Mit dem Besitzer den Render-QA-Host kontrolliert auf diesen Branch umstellen
   (keine Signaturänderung), APK bauen, `FOLD7_ABNAHME_JARVIS_20261106.md` Zeile für Zeile ausführen; Belege
   (Video, gfxinfo, meminfo, thermalservice) in einen neuen Evidenzordner, Ergebnisse in die Übergabe.
3. **Echte Modellantworten messen:** Katalog `ConversationUnderstandingCatalogTest` + neue unbekannte Aufgaben auf dem
   Gerät mit geladenem Modell; vier Ebenen getrennt (Absicht, Kontext im Prompt, Modellschluss, Werkzeugwirkung);
   Baseline vs. neu auf identischen Aufgaben, zurückgehaltene Kontrollaufgaben.
4. **Statusleiste in engen Fenstern** (Punkt 5.3) und Kartengeometrie in Layouts ohne Schutzbereiche (5.4) – je mit
   Activity-Test gegen echte Schutzbereiche.
5. **Sprache/Turn-Taking** (5.6) mit simulierten TTS/STT-Callbacks auf dem Host, echte Qualität nur am Gerät.
6. **Gedächtnisbrücke** (5.7) als schmale, belegsichere Schnittstelle; Journal-Episoden nie als bestätigte Fakten.
7. **Planen→Ausführen→Prüfen-Kette** (5.8) an einem sicheren, freigegebenen mehrstufigen Ablauf (zuerst nur lesend:
   Speicherwerte erheben, Vorschlag erklären, Löschen nur nach ausdrücklicher Freigabe, Zustände geplant/gestartet/
   bestätigt/gescheitert/unbekannt, keine Doppelausführung nach Wiederaufnahme).
8. **In-App-Abnahmefunktion** (5.9) ohne Änderung des signierten Veröffentlichungswegs.

---

## 7. Betroffene Dateien, Klassen, Schnittstellen

| Bereich | Dateien / Klassen | Wichtige Schnittstellen |
|---|---|---|
| Avatar | `avatar/AvatarDirector.kt`, `avatar/AvatarContract.kt` (`AvatarPoseReducer`, `AvatarPose.reading`, `AvatarEvent.AnimationIntentsChanged.windowReading`), `avatar3d/AvatarMorphMapper.kt`, `avatar3d/AvatarFraming.kt`, `avatar/JarvisHoloAvatar.kt` | `onRuntime`, `windowOpened/Moved/Closed`, `windowWorkStarted/Ended`, `confirmationAccepted`, `clearAnimationIntents`, `speechStarted/wordRange/audioLevel/speechEnded` |
| Hologramm/Kugel | `ContinuumHologramView.kt`, `ui/ContinuumHologramRenderer.kt`, `ui/JarvisSequenceView.kt`, `ui/JarvisSequenceEffects.kt`, `ui/BrainOrbAnimationRenderer.kt` | `setWorkWindowCamera`, `setCockpitRegions`, `setCockpitSequence`, `JarvisSequenceView.setCardsDocked`, `JarvisSequenceEffects.setProtectedAreas/docked/drawnCardBox` |
| Cockpit-Ereignisse | `cockpit/CockpitAnimationEvent.kt` (`ActionStarted.stepKey`), `cockpit/CockpitAnimationTriggers.kt` (`activeTimeline`), `ui/CockpitAnimationPresenter.kt`, `ui/ContinuumCockpitPresentation.kt` (`CockpitStatus.openWork`, `processRows`) | `CockpitAnimationBus.emit/subscribe` |
| Fenster | `ui/JarvisWorkWindow.kt` (`open(..., crystal)`, `setStatus`, `shownBounds`, `fitTitle`), `ui/JarvisWorkWindowHost.kt` (`show(..., crystal, fromCurrent)`, `workStarted/workEnded`, `showStatus`, `statusLine`), `ui/JarvisWorkWindowGeometry.kt`, `ui/JarvisActionScenes.kt`, `ui/JarvisDeckClearance.kt` | Tags: `jarvis-work-window`, `…:panel`, `…:scroll`, `…:close`, `…:status` |
| Dokument | `PdfDocumentIntake.kt`, `cognitive/DocumentDraftPrompt.kt`, `AgentActivity` (`presentDocumentScene`, `startDocumentDraft`, `onDocumentDraftReply/Failed`, `presentDraftScene`, `readPdfDocument(uri, remembered)`, `cancelLocalRequest`, `stoppedDraftRequest`) | Tags: `jarvis-document-*`, `jarvis-draft*` |
| Gedächtnis | `cognitive/CockpitWorkJournal.kt`, `cognitive/WorkRecall.kt`, `cognitive/OwnerCorrectionIntent.kt`, `cognitive/OwnerDocumentIntent.kt`, `AgentActivity` (`withWorkJournal`, `recordWork`, `publishOpenWork`, `recoverWorkJournal`, `presentWorkRecall`, `applyOwnerCorrection`, `openOrReuseDocument`) | serielle Spur `documentIoExecutor` |
| Gespräch | `cognitive/LocalConversationFollowUpPolicy.kt`, `LocalFollowUpTaskFrame`, `OwnerStatementTimeline`, `AiContextCompiler`, `ChatReasoningRouter`, `LocalModelReasoningAdapter`, `BoundedAgentOrchestrator` | nicht ändern ohne Abstimmung mit dem Gehirn-Branch |
| Modell | `localmodel/LiteRtLmLocalModelRuntime.kt`, `localmodel/LocalModelThermalGuard.kt` | `LocalInferenceEvent` (TextDelta/Completed/Cancelled/Failed) |

**Tests (Auswahl):** `ContinuumDocumentFlowUiTest` (8, kontrollierte Test-Laufzeit statt LiteRT), `ContinuumDocumentSceneUiTest` (8),
`JarvisWorkWindowLifecycleUiTest` (7), `JarvisWorkWindowUiTest` (7), `ContinuumSystemStatusWorkWindowUiTest` (10),
`ContinuumReferenceCockpitUiTest` (12, aus #572), `ui/JarvisSequenceTurnLifecycleUiTest`, `avatar/AvatarWindowWorkReadingTest` (8),
`avatar/AvatarWindowMovedTest` (2), `cognitive/CockpitWorkJournalTest` (4), `cognitive/WorkRecallTest` (4),
`cognitive/OwnerCorrectionIntentTest`, `cognitive/OwnerDocumentIntentTest`, `cognitive/ConversationUnderstandingCatalogTest` (5),
`ui/ContinuumCockpitPresentationTest` (5), `JarvisLiveAvatarWiringUiTest` (10), `ContinuumLongReplySpeechUiTest` (5),
`ContinuumCockpitHandsFreeUiTest` (2), `ContinuumVisualEvidenceUiTest` (5, 1 übersprungen).

**Belege (HOST, gekennzeichnet):** `docs/evidence/claude-jarvis-flow-20261106/` (Ablauf, Fehler, STOP, Kristall,
Statusleiste vorher/nachher, Karten ohne Fenster vorher/nachher), `docs/evidence/claude-jarvis-document-20261106/`.

---

## 8. Ausführen und Prüfen (Host)

```
# JVM-Tests (schnell)
./gradlew --no-daemon -q :app:testDebugUnitTest --tests 'at.heinz.deviceai.avatar.*' --tests 'at.heinz.deviceai.cognitive.*'
# Robolectric-UI-Tests (brauchen -PcontinuumUiWidgetTests=true; Laufzeit-JARs via prepareUiTestSdk nach app/build/ui-test-sdk)
./gradlew --no-daemon -q -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests 'at.heinz.deviceai.ContinuumDocumentFlowUiTest'
# Host-Bilder (immer als Testaufnahme kennzeichnen)
./gradlew --no-daemon -q -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=$PWD/docs/evidence/<ordner> :app:testDebugUnitTest --tests '…'
```
Ergebnisse: `app/build/test-results/testDebugUnitTest/TEST-*.xml`. JDK 21. Die #572-Klasse braucht ~15 min (im
Hintergrund starten). Robolectric-Fallen: nach `setQualifiers`+`configurationChange` meldet der ViewRoot den alten
Rahmen bis `ShadowViewRootImpl.callDispatchResized()`; Sync-Barrieren blockieren `post {}` bis zum nächsten Frame →
in Tests `advance(16)` statt `idle()`; JDK 19+ `\b` ist ASCII-only → Unicode-Lookarounds; Hintergrund-Executor + pausierter
Looper → in Tests mit Echtzeit-Schleife warten (`until(...)` in `ContinuumDocumentFlowUiTest`).

---

## 9. Sichere Fortsetzungsreihenfolge (Kurzfassung)

1. `git fetch origin claude/continuum-jarvis-cockpit-z7bo60` → HEAD prüfen (Protokoll-Commit nach `4d84caa8`).
2. `CLAUDE.md`, `AGENTS.md`, dieses Protokoll, Tages-Übergabe, Architekturkarte, Fold7-Anleitung lesen.
3. PR #565: Review-Threads/Gitar prüfen → echte Befunde RED → GREEN, sonst begründet antworten.
4. Schnelle Verifikation (Abschnitt 8) auf dem HEAD ausführen und Zahlen notieren.
5. Danach Abschnitt 6 von oben nach unten; jeden grünen Meilenstein einzeln committen und pushen; Übergabe mit SHA,
   Testzahlen und offenen Punkten nachführen. Kein Merge nach `main`, keine Veröffentlichung, keine Signaturänderung.
