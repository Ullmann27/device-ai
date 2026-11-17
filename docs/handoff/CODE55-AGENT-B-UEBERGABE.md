# Code55 · Agent B · Übergabe PDF-Dialog (Fehler B, Fold7 Code54)

Branch `claude/code55-agent-b-pdf-dialog-20261112` (Basis `5770a1b34`). Fold7: NOT_RUN. Kein Merge, kein Release.

## Ursache (Feldfehler B)
- `AgentActivity.presentDocumentScene` meldete „PDF gelesen …“ nur als Chat-Text; Name, Seiten und Text der PDF
  wurden nirgends an das Gespräch gebunden → `runLocal` bekam keinen Dokumentbezug („keine PDF vor mir“).
- `PdfDocumentIntake.extract` verlor die Seitenzuordnung (Text aller Seiten zusammengefügt).
- ALWAYS_ON: „Was steht in der Überschrift auf Seite 1?“ lief über `PublicKnowledgeTopicRouter` in eine
  Webrecherche; deren Digest („steht überschrift seite“) stand in der nächsten Runde als aktuelle EXTERNE
  RECHERCHEDATEN vor der neuen Frage → das Modell beantwortete die alte Frage erneut.
- Activity-Neuerstellung (SAF-Rückweg, Hintergrund) ohne Konfigurationswechsel ruft `forgetForExit()` → RAM-Kontext weg.

## Änderung
- `PdfDocumentIntake.boundedPages`: gleicher Text/Budget wie `boundedText`, plus Text je Seite (`Document.pageTexts`).
- `cognitive/OwnerDocumentDialogue.kt` (neu): `Reference` (Sitzung, Episode, URI, Name, Seiten, Größe, Textstatus,
  Seitentexte, Revision); deterministische Antworten (Name, Seitenzahl, Größe, erste Zeile/Überschrift einer Seite,
  Text einer Seite; nicht Gelesenes wird benannt); `promptBlock` (seitenattribuiert, ≤ 1.800 Zeichen, nicht fälschbar).
- `LocalConversationContext.recordDocument/document`: sitzungsgebunden, übersteht Themenwechsel/clear, nie andere Sitzung.
- `AgentActivity`: Bindung in `presentDocumentScene`; Dokumentfragen vor allen Web-Zweigen (kein Web, kein Picker);
  ohne Modell ehrliche Antwort; `runLocal` hängt DOKUMENTDATEN an; Saved-State `continuum_open_pdf_v1` (gleiche Chat-ID).
- `LocalDialogueQualityGate`: „keine PDF“-Behauptung trotz Dokumentdaten → ein begrenzter Neuversuch; Rahmen-Canary.
- `NonModelReplyKind.DOCUMENT_STATE` (gelabelter Verlaufseintrag ohne Dateitext).

## Tests (Host, Robolectric)
- RED auf Basis: `OwnerPdfDialogueFollowUpUiTest` 4/4 rot (Heading ging ans Modell ohne PDF; ALWAYS_ON 3 Web-Freigaben;
  ohne Modell Gedächtnis-Fallback).
- GREEN: `OwnerDocumentDialogueTest` 10/10, `PdfDocumentIntakeTextTest` 5/5, `OwnerPdfDialogueFollowUpUiTest` 4/5 im
  ersten Lauf; der 5. (Neuerstellung) scheiterte nur an Robolectric `recreate()` (NPE) – durch manuellen
  Konfigurationswechsel ersetzt; Ergebnis siehe Commit-Nachricht (Nachbarlauf).
- Netzwerkschutz im UI-Test: Freigaben werden gezählt und laufen vor jeder Verbindung ab.

## Offen / nächste Schritte
1. Fold7: echte PDF (16 S.), Fragen a–e, Falten/Entfalten, SAF-Rückweg mit Neuerstellung prüfen.
2. „Fasse die PDF zusammen“ bei offener PDF öffnet weiterhin den Picker (OwnerDocumentIntent) → ggf. Wiederverwendung.
3. Prozessende ohne Saved-State (App weggewischt) verliert den Bezug; Journal-Episode hätte nur Metadaten.
4. Computer-Intent-Aufgabe (`claude/code55-agent-b-computer-intent-20261112`) NICHT begonnen.
