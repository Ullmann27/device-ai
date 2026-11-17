# Code55 · Agent A · Übergabe Gedächtnis/Wiederöffnen (12.11.2026)

Zweig `claude/code55-agent-a-memory-reopen-20261112` (Basis `5770a1b34`). Fold7: NOT_RUN – kein Geräteergebnis behauptet.

## Erledigt (Host/Robolectric, RED→GREEN belegt)
- **D – Sachaussage als Werkzeugsuche:** Kein Router kapert „Meine Werkzeugkiste ist blau, steht im Keller und enthält
  zwölf Schrauben.“ (alle Vor-Modell-Router geprüft). Ursache: `OwnerObjectStateLedger.supportedStatement` kannte nur
  „Ich habe …“; Satz galt als Frage, Modell bekam keinen GESPRÄCHSSTAND. Fix: `ownObjectSubject` (mein/unser/der/die/das
  [Farbe] Nomen + ist/steht/liegt bzw. enthält/hat + Zahl). Prompt-Hinweis „kein Werkzeug, keine Suche“ bei neuen Angaben.
  „Was hatte ich vorher gesagt?“ → Verlauf (HISTORY); „… vorher?“ zu einem Merkmal → vorheriger Wert.
- **Zu-/Abgänge:** „17 Schrauben … 5 dazu bekommen, drei blaue und zwei rote“ ergab vorher „Anzahl 2“ (17 → 2). Jetzt
  17 + 5 = 22, Teilmengen-Farben nie Objektfarbe („Nicht zugeordnet“); unklar (keine Zahl, zwei Zahlen, „bekommen“ ohne
  Richtung, anderer Ort, < 0) → „unklar … bitte nenne die aktuelle Gesamtzahl“, nichts geraten.
- **E – Begrüßung/Restore:** `AgentActivity.onCreate` fügte die Begrüßung bei jedem onCreate ein (auch nach SAF-Picker-
  Rückkehr, Fold7-Screenshot). Jetzt `ConversationStartupNotice`: Begrüßung nur für neue leere Sitzung, sonst nichts;
  Timeout/Fehler beim Restore → kurzer ehrlicher „Hinweis: …“ (nicht gespeichert). `restoreModelContext` spielt alle
  Kontext-Turns seit letztem Reset ein (`LocalChatTranscriptStore.loadContextHistory`, max. 256, sonst Unvollständig-
  Platzhalter) → Objektgedächtnis überlebt das 4-Turn-Fenster nach Wiederöffnen; `holdsSession` verhindert Doppel-Replay.
  Dateiformat unverändert, Legacy-Zeilen lesbar, nichts gelöscht.

## Tests (Zahlen siehe Commit)
- Neu: `OwnerObjectStateFieldStatementTest` (8), `OwnerObjectStateReopenMemoryTest` (6), `OwnerObjectStateCountChangeTest` (6),
  `AgentActivityReopenLifecycleUiTest` (7, inkl. Picker-Recreate: System-Destroy und Konfigurationswechsel).
- Nachbarn grün: 690 Tests (*ObjectState*, *Ledger*, *Memory*, *BootstrapBrain*, *M3EvalGaps*, *Intelligence55*,
  *OwnerDeviceRequestSafety*, *Recall*, *ChatTranscript*, *ChatPersistence*, …) und 137 UI-Tests (19 Klassen, 1 bestehender Skip).

## Offen / nächste Schritte
1. Fold7: Aussage → App schließen/öffnen → Rückfrage; PDF-Picker-Rückkehr ohne Begrüßung; Zeitmessung Replay (256 Turns, UI-Thread).
2. Bei Timeout der Sitzungs-ID-Abfrage bleibt der aktive Zeiger auf dem alten Gespräch (kein Nachladen); ggf. spätes Nachladen.
3. Sprach-Cue „Hallo. Device AI ist bereit.“ bei jedem Kaltstart unverändert (nicht Teil des Auftrags).
4. Teilmengen-Farben werden nur als „nicht zugeordnet“ geführt, nicht als „davon 3 blau“.
5. Jarvis-Bewegung (`claude/code55-agent-a-jarvis-motion-20261112`): NICHT begonnen.

## Dateien
`app/src/main/java/at/heinz/deviceai/AgentActivity.kt`, `cognitive/OwnerObjectStateLedger.kt`,
`cognitive/LocalChatTranscriptStore.kt`, `cognitive/LocalConversationContext.kt`, `cognitive/ConversationStartupNotice.kt` (neu)
