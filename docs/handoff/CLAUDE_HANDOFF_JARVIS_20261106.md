# Übergabe JARVIS-Cockpit – 06.11.2026 (Branch `claude/continuum-jarvis-cockpit-z7bo60`, Draft-PR #565)

Alles unten ist **HOST-geprüft**; nichts davon ist auf dem Fold7 bestätigt (REAL_DEVICE_NOT_RUN). Kein Release,
keine Signatur-/Paketänderung, kein Main-Merge. PR #571 bleibt als Draft offen (als ein Commit übernommen).

## Angeschlossen (in der App verdrahtet, mit Tests)

| Commit | Ergebnis |
|---|---|
| 6fd740a5, 5162de02 | Arbeitsfenster: Falten/Drehen bei offenem Fenster gibt das Deck zurück; der Test verlangt das Wiederöffnen nach jedem Wechsel. Ein Fenster, dessen erstes Layout in einer anderen Größe kommt, plant neu, statt lautlos zu verschwinden (RED auf altem Code belegt). Korrektur: Der Ausfall in 6fd740a5 war ein Robolectric-Artefakt (fehlende Größenmeldung). |
| 8699a6c5 (Bot) | Sprechmimik aus dem gesprochenen Text: Frage-Brauen, Satzende-Nicken, Ausklingen, Blinzeln an Wortgrenzen |
| de9d249c | PR #571 Kamerafahrt × Sprechmimik × STOP zusammengeführt; Zusammenspieltest (Sprechen hält Kamera/Fenster, STOP beendet alles sofort, nichts spielt nach) |
| 25256632 | Lichtbahn/Emblem allokationsfrei pro Frame; Recherche-Transfer `docs/research/omega/JARVIS_RESEARCH_TRANSFER_20261106.md` mit Fold7-Messplan |
| dfd30a38 | „Lies diese PDF …“ → nur Android-Dateiauswahl → echte Seite (PdfRenderer), Name, Größe, Seiten, Text ab Android 15 → Dokumentfenster mit Seite auf der Lichtbahn, Blick, Deck-Räumung |
| 597603e7 | Entwurf über den vorhandenen lokalen Modellpfad (nur Dokumenttext, Budget 7 900), Ergebnisfläche, Bearbeiten-Dialog, Speichern über Android; ehrliche Zustände ohne Modell/Text; eigener Dokument-Executor |
| 7433100a | Dokumentfenster zweispaltig, dunkle Unterlage unter dem Fensterglas, Uhr/Datum nie gekürzt; Host-Belege `docs/evidence/claude-jarvis-document-20261106/` |
| c8044585 | Arbeitsjournal (`CockpitWorkJournal`): Auftrag in deinen Worten, Quelle, tatsächlich erreichter Stand (gelesen / Entwurf in Arbeit / erstellt / gespeichert / abgebrochen / kein Modell / fehlgeschlagen), Bearbeitungen, nächster offener Schritt – überlebt den Neustart. „Wo waren wir gestern/heute …“, „Was ist noch offen“ → Antwort + VERLAUF-Fläche, Herkunft „Erinnerung, kein Modelllernen“. „Nimm die ausgewählte PDF …“ nutzt die zuvor gewählte Datei (dauerhafte Android-Lesefreigabe). Cockpit-Szenen bleiben auf START. |
| 4059f076 | Erinnern sichtbar: Rückbezug sendet `RecallHits` (echte Trefferzahl) auf den Cockpit-Bus; verstandene Korrektur → Bestätigungsnicken (`confirmationAccepted`); Entwurfszeile im Dokumentfenster zeigt immer den erreichten Stand (nie „läuft“ nach Fehler) |
| b2c88773 | Technische Karte `docs/research/omega/JARVIS_ARCHITECTURE_MAP_20261106.md` + geprüfte KI-Aussagen (Summarization ohne Deutsch, Fold7/Prompt API nur Vordergrund bestätigt; Samsung-SDK-Partnerpflicht nicht belegt) |
| 29962f3c | #565-Review (Gitar): Blick folgt dem neu geplanten Fenster – `AvatarDirector.windowMoved` (RED→GREEN 2/2); alle 11 Review-Threads erledigt |
| 3051bdee | Avatar liest: solange das lokale Modell wirklich am Fensterinhalt arbeitet, hält JARVIS den Blick auf dem Fenster (`AvatarDirector.windowWorkStarted/Ended`), die Augen fahren die Zeilen ab, der Kopf bleibt; Ende mit Ergebnis, Fehler, STOP, Sprechen oder Fensterschluss; reduzierte Bewegung ohne Augenfahrt |
| 06dd8b1f, 4ac25d11 | **#572 erledigt und übernommen.** Ursache der 3 roten Fälle war die echte Fenstergeometrie, nicht der Test-Scrollpfad: der umbrechende Fenstertitel ließ bei 200 % Schrift 101 px (Telefon 68 px) Scrollhöhe für 128 px (95 px) hohe Aktionen. Titel bleibt jetzt einzeilig, wenn sonst die höchste Aktion nicht passt. RED→GREEN; #572-Klasse 12/12 (vorher 9/12); Commit von #572 per cherry-pick (Urheber erhalten) |
| 9c76c53e | Arbeitsjournal: Entwurf eines früheren App-Prozesses wird `INTERRUPTED` („unterbrochen (App wurde beendet)“) statt 10 min „in Arbeit“; offene Schritte erscheinen im Panel „Aktueller Ablauf“ als „Offen · …“ (nie als laufend) |
| 4e753b73 und Folgecommit „second draft tap…“ | Durchgehender Dokumentablauf auf dem echten `runLocal`-Pfad (`ContinuumDocumentFlowUiTest`, kontrollierte Test-Laufzeit): Lesen während der Arbeit; Entwurf entfaltet sich aus dem Dokumentfenster und behält die erste Seite als Quellenkarte (zwei Ebenen); **behobene Fehler:** Modellfehler ließ Journal auf „in Arbeit“, Status „wird erstellt“ und schaltete auf CHAT (Fenster weg); STOP meldete „Lokaler Modellfehler … Ursache nicht bestimmt“ und schaltete auf CHAT; zweiter Tipp auf ENTWURF ERSTELLEN überschrieb die Statuszeile mit „nicht gestartet“, obwohl der Entwurf lief (alle RED belegt). App-Kristall um das echte App-Symbol auf der Lichtbahn (allokationsfrei). Belege `docs/evidence/claude-jarvis-flow-20261106/` |
| d5c2d7f2 | „Nein, ich meinte den letzten Entwurf“ (auch: vorige PDF, Entwurf von gestern): Auflösung im Arbeitsjournal; ein laufender falscher Entwurf endet nach STOP-Regeln (ABGEBROCHEN), der gemeinte Entwurf erscheint, deine Korrektur bleibt am korrigierten Auftrag. |

## Bekannte, vorbestehende Fehler (nicht von heute, belegt)

- ~~`ContinuumReferenceCockpitUiTest`: 5/12 rot~~ – erledigt: Testanpassung aus #572 übernommen (4ac25d11) und die
  Fenstergeometrie bei 200 % Schrift behoben (06dd8b1f) → 12/12.
- Node-Quellverträge `.github/continuum/*.test.mjs`: 1498 pass / 53 fail / 23 skip – identische Fehlermenge auf `25256632`.
- Robolectric meldet nach `setQualifiers` + `configurationChange` den alten Fensterrahmen bis zur Größenmeldung
  (`ShadowViewRootImpl.callDispatchResized`); Sync-Barrieren blockieren `post {}` bis zum nächsten Frame – in Tests `advance()` statt `idle()`.

## Stand 06.11.2026, 09:40 UTC (HEAD `e23b92fb` + diese Übergabe)

- Zuletzt gezielt grün (HOST, Robolectric/JVM): avatar.* (16 Klassen) inkl. `AvatarWindowMovedTest` 2/2,
  `ContinuumSystemStatusWorkWindowUiTest` 10/10, `JarvisWorkWindowLifecycleUiTest` 4/4, `ContinuumDocumentSceneUiTest` 8/8,
  `ContinuumCockpitHandsFreeUiTest` 2/2, `JarvisLiveAvatarWiringUiTest` 10/10, `ContinuumLongReplySpeechUiTest` 5/5,
  Journal/Recall/Korrektur/Intent-JVM-Tests. Volle Suite NICHT ausgeführt.
- DEVICE (Fold7/One UI 9): nichts ausgeführt – kein adb-Gerät in dieser Umgebung.
- Kognitiver Nachweis (Besitzerauftrag 08:48 auf #565): Paraphrasen/Pronomen/Negationen/mehrstufige Fragen gegen den realen
  lokalen Modellpfad – **NOT_RUN** (kein LiteRT-Modell auf dem Host). `WorkRecall`/`OwnerCorrectionIntent` sind regelbasiert
  (Regex) und decken nur die getesteten Formulierungen ab; keine Aussage über allgemeine Semantik.
- PR #572 (test-only, `ContinuumReferenceCockpitUiTest`, Kopf `7b814747`) gegen #565 `29962f3c`: 12 Tests, **3 rot**
  (vorher 5), Laufzeit 853 s (darum Lauf 1 mit 595-s-Limit abgebrochen; Stack-Aufnahme: kein Hänger, nur langsam).
  Alle 3: „Target must not be a clipped sliver: CODING-AUFTRAG ENTWERFEN“ bei 200 % Schrift (Breit, Fold-quer, Telefon) –
  Knopf im scrollbaren Fensterinhalt teilweise verdeckt; vermutete Ursache: Tipp-Helfer scrollt das Cockpit, nicht den
  Fensterinhalt (unbewiesen). **NICHT übernommen.** Nächster Schritt: im Test den Fenster-ScrollView zum Ziel scrollen
  (`requestRectangleOnScreen`) oder Produktursache belegen.
- Kognitiver Nachweis (e23b92fb): `LocalConversationFollowUpPolicy` erkennt jetzt natürliche Anschlüsse („Nicht vier,
  sondern fünf.“, „Wo arbeitet er?“, „Fass sie mir zusammen.“, „Das stimmt nicht, …“, „… nochmal?“), damit
  `LocalFollowUpTaskFrame` deine früheren Aussagen in die Modellaufgabe legt. Katalog `ConversationUnderstandingCatalogTest`
  nach Schichten: (1) deterministisch: Zeitleiste bleibt bei verneinten Zahlwörtern fail-closed; (2) Kontext im Prompt:
  JA für alle Katalogfälle, Kontrollfälle unberührt, keine Werkzeug-/Geheimnis-Wiederholung; (3) Modellantwort korrekt:
  **NOT_RUN** (kein Modell auf dem Host) – keine Intelligenzverbesserung behauptet; (4) Werkzeuge: keine ausgeführt.
  Mehrstufige Aufträge (`ChatReasoningRouter`/`BoundedAgentOrchestrator`) brauchen das Modell → NOT_RUN.
  Review-Korrektur (Gitar ⚠️): höfliches „Sie/Ihnen“ und „nochmal“ ohne Erinnerungsverb lösten den Anschluss aus –
  als erste Nachricht ohne Vorgeschichte hätte JARVIS dann mit LOCAL_FOLLOWUP_HISTORY_UNAVAILABLE abgelehnt.
  Behoben (Pronomen nur kleingeschrieben, „nochmal“ nur nach hieß/sagtest/meintest/erwähnt), RED→GREEN, 50/50.

## Stand 06.11.2026, ~12:00 UTC (Fortsetzungsauftrag „nahtlose Fortsetzung“)

- HOST grün: `ContinuumDocumentFlowUiTest` 3/3, `ContinuumDocumentSceneUiTest` 8/8, `JarvisWorkWindowLifecycleUiTest` 6/6,
  `AvatarWindowWorkReadingTest` 6/6, Regressionslauf 201 Klassen / 1154 Tests ohne Fehler (u. a. cognitive.*, avatar.*,
  Statusfenster 10/10, Live-Avatar 10/10, Freisprechen 2/2, lange Antworten 5/5), #572-Klasse 12/12.
- Kognitive Anschlüsse aus e23b92fb/6c113503 unverändert (cognitive.* im Regressionslauf grün). Keine Aussage über
  Modellantwortqualität (keine reale Inferenz auf dem Host).
- DEVICE: **NOT_RUN** (kein Fold7 angeschlossen). Abnahmeliste unten.
- Beobachtet, nicht geändert: Die Sequenz-Pillen eines Modellzugs („Auftrag angenommen“ als Aktionspille **und** als
  Zeitleistenschritt, „Bearbeitung“) liegen während der Dokumentarbeit über Kopf und Kugel (`flow-1`); für die filmische
  Szene sollten sie bei offenem Arbeitsfenster neben das Fenster wandern oder entfallen (JarvisSequenceView, gemeinsam
  mit allen Modellzügen – nicht im Alleingang geändert).

## Fold7-Abnahme (vorbereitet, NOT_RUN)

| # | Schritt | Erwartung | Messung |
|---|---|---|---|
| 1 | Innen, Hochformat: „JARVIS, öffne meine letzte PDF und fasse sie zusammen“ (Datei vorher einmal gewählt) | Deck räumt, Seite fliegt auf der Lichtbahn ein, Fenster, Kopf/Augen zum Fenster, Kamera lehnt sich | Bildschirmvideo |
| 2 | Während des Entwurfs | Augen fahren Zeilen ab, Kopf ruhig auf dem Fenster, ENTWURF ERSTELLEN gesperrt, Status „wird lokal erstellt“ | Video, `adb shell dumpsys gfxinfo at.heinz.deviceai.continuumlocal framestats` |
| 3 | Ergebnis | Entwurf entfaltet sich aus dem Dokumentfenster, Quellenkarte, Sprechen mit Mimik, danach Cockpit vollständig zurück | Video |
| 4 | STOP mitten im Entwurf | sofort: Fenster weg, Blick frei, Chat „mit STOP abgebrochen“, START bleibt; Verlauf zeigt „abgebrochen“ | Video + „Wo waren wir heute?“ |
| 5 | Falten innen↔außen und Drehen bei offenem Dokument-/Entwurfsfenster | Fenster plant neu (nie falsche Position), Blick folgt (`windowMoved`) | Video |
| 6 | 200 % Schrift, Außendisplay | alle Fensteraktionen ganz sichtbar und tippbar (Titel ggf. einzeilig) | Screenshots |
| 7 | App-Start über Werkzeug („Öffne Kalender“) | echtes Symbol im Kristall auf der Lichtbahn | Video |
| 8 | App während Entwurf beenden, neu starten | Verlauf: „Entwurf unterbrochen (App wurde beendet)“, Panel „Offen · …: Entwurf neu starten“ | Screenshot |
| 9 | 10 min Dauerlauf mit Filament-Kopf | keine Ruckler > 3 %, Wärmestatus, reduzierte Blüte bei Wärme | Perfetto-Trace (`gfx`, `view`, `thermal`) |

## Offen (nächste Schritte)

1. **Fold7 (Besitzer):** echte PDF + geladenes Modell (Entwurf), STOP während des Entwurfs, Falten/Drehen/200 % Schrift mit
   Dokument- und Entwurfsfenster, Filament-Kopf innen/außen mit Kamerafahrt + Sprechmimik; Messplan im Recherche-Dokument.
2. App-Kristall: auf der Lichtbahn umgesetzt (Host); als ruhendes Prisma im Werkzeugfenster und auf dem Gerät offen.
3. Avatar-Bot: Paket 2 (Zuhören/Denken-Blick) gestoppt am Nutzungslimit; Arbeitsstand in seiner Worktree, fortsetzbar.
4. E-Mail-Antwortentwurf auf derselben Grundlage (Dokument → Analyse → Entwurf → Speichern) – noch nicht angeschlossen.
5. Lasseter-Quelle als Textfassung beschaffen (Scan ohne Textebene); ADPF-Details (Headroom/PerformanceHint) aus der Referenz belegen.
6. Gedächtnis: Projekte/Entscheidungen/überholte Angaben über den Arbeitsverlauf hinaus (vorhandene `ContinuumTemporalMemory`
   anbinden), gelernte Handlungsregeln aus Korrekturen mit Herkunft, offene Aufgaben dezent in Kugel/Status, Hervorheben
   erst verarbeiteter Passagen, Dokument tritt hinter den Entwurf zurück (zwei Ebenen) – nicht begonnen.
7. One UI 9: ML Kit GenAI (Gemini Nano) als On-Device-Zusammenfassung prüfen; AppFunctions erst mit Gemini-Freigabe – siehe Recherche-Dokument.
8. Kognition auf dem Fold7 (Modell geladen): Katalogfälle aus `ConversationUnderstandingCatalogTest` als echte Gespräche
   führen und die Modellantworten bewerten (richtig/falsch/Rückfrage); mehrstufige Aufträge mit Werkzeugen: geplant vs.
   tatsächlich ausgeführt (ToolGate-Belege). Danach Avatar-Anbindung: Nachdenken/Erinnern/Korrektur sichtbar machen.
9. ~~PR #572~~ erledigt (Ursache Fenstergeometrie, behoben, 12/12, übernommen). PR #572 bleibt als Draft offen.
10. Sequenz-Pillen bei offenem Arbeitsfenster neben das Fenster (siehe Beobachtung oben).
11. Dokument-Passagen erst nach Verarbeitung hervorheben (braucht Modell-Rückmeldung je Abschnitt – nicht vortäuschen).
