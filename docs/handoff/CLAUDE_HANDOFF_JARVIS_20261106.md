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
| d5c2d7f2 | „Nein, ich meinte den letzten Entwurf“ (auch: vorige PDF, Entwurf von gestern): Auflösung im Arbeitsjournal; ein laufender falscher Entwurf endet nach STOP-Regeln (ABGEBROCHEN), der gemeinte Entwurf erscheint, deine Korrektur bleibt am korrigierten Auftrag. |

## Bekannte, vorbestehende Fehler (nicht von heute, belegt)

- `ContinuumReferenceCockpitUiTest`: 5/12 rot („Tap must open Gedächtniszentrum“) – identisch auf `61ab95e1` (Tagesbeginn).
  Ursache: der Test erwartet ein `AlertDialog`, seit #565 öffnen Zentren ein Arbeitsfenster. Test an das Fenster anpassen.
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

## Offen (nächste Schritte)

1. **Fold7 (Besitzer):** echte PDF + geladenes Modell (Entwurf), STOP während des Entwurfs, Falten/Drehen/200 % Schrift mit
   Dokument- und Entwurfsfenster, Filament-Kopf innen/außen mit Kamerafahrt + Sprechmimik; Messplan im Recherche-Dokument.
2. App-Kristall/Prisma für das echte App-Symbol (noch nicht begonnen).
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
9. PR #572: 3 rote Fälle ursächlich klären (siehe oben), erst dann übernehmen.
