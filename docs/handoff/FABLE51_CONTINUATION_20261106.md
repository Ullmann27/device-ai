# Fable 5.1 – Fortsetzung der JARVIS-Gesamtentwicklung (ab Opus 5.5 `4d84caa8`, 06.11.2026)

Branch `claude/continuum-fable51-continuation-20261106` (Draft-PR #574, Basis = Opus' `claude/continuum-jarvis-cockpit-z7bo60`
@ `4d84caa8`, PR #565). Eigener Branch, weil Opus' letzter Commit nur Minuten alt war – keine konkurrierenden Schreibzugriffe.
Alles HOST; Fold7/One UI = REAL_DEVICE_NOT_RUN. Kein Main-Merge, keine Signatur-/Paket-/Versionsänderung, keine APK.

## Übernommen und unangetastet

Alle Komponenten aus `CLAUDE_HANDOFF_JARVIS_20261106.md` (Avatar-Absichten, Lese-Blick, Arbeitsfenster, Lichtbahn, App-Kristall,
Dokument-/Entwurfsablauf, Arbeitsjournal, Rückbezug/Korrektur, Statusleiste, Kamerafahrt, Sprechmimik) wurden nicht verändert.
Host-Regressionslauf vor und nach jeder Änderung: `scripts/android-kotlin-unit-check.sh` (JVM-Unit-Tests; Robolectric-UI-Tests
aus `src/uiTest` sind hier ohne Gradle nicht ausführbar und bleiben auf Opus' Stand).

## Meilensteine

| Commit | Schwachstelle (nachgewiesen) | Änderung | Test |
|---|---|---|---|
| `96cb70cf`, `533c9159`, `27d343cc` | Host-Check kompilierte die Hauptquelle nicht (Filament/gltfio, OpenCV, `RequiresApi` ohne Gradle); `*Checks`-Helfer ohne `main` wurden als Fehler gezählt | optionale classes.jar aus Maven Central, Annotation-Stub, `javap`-Prüfung vor dem Start eines Checks-Programms | Compile 0 Fehler außerhalb LiteRT-LM; Checks 4 PASS + 1 übersprungen |
| `fddc94e7` | Stimme: `maxBy { quality }` über alle deutschen Offline-Stimmen – kein `de-AT`-Vorzug, instabil bei Gleichstand, keine Besitzerwahl (Android kennt kein Stimm-Geschlecht), kein Englisch, `pitch 0.86` verfremdete | `cognitive/JarvisVoiceSelection` (rein), Langdruck auf TON = Stimmenauswahl + Deutsch/English, Persistenz `voice_name`/`voice_language`, Pitch 1.0 / Rate 0.96; Recherche `docs/research/omega/JARVIS_VOICE_ENGINES_20261106.md` | `JarvisVoiceSelectionTest` 7/7; Suite OK (3032) |
| `984fef54` | Lippen: Engine ohne `onRangeStart` **und** ohne PCM → jeder Tick `PCM_ENVELOPE 0` → Mund blieb bei hörbarer Sprache geschlossen (Code gelesen: `AvatarDirector.audioLevel` kannte nur den Wortgrenzen-Fallback) | `TEXT_PACE_FALLBACK`: nach 250 ms ohne Wortgrenze wird der gesprochene Text mit 360 ms/Wort getaktet (Visemes + Satzenden + grobe Pegel, klar als Schätzung markiert); echte Wortgrenze übernimmt sofort und wird nicht als veraltet abgewiesen; endet ehrlich nach der geschätzten Dauer, bei STOP, Sprechende | `AvatarTextPaceFallbackTest` 6/6 (auf altem Code: Enum-Wert fehlt, Verhalten = nur `PCM_ENVELOPE 0`); avatar.* unverändert grün; Suite OK (3038 Tests, 546 Klassen) |
| `be0e2b3c` (M3, Priorität 1) | Gedächtnis: „Was haben wir gestern entschieden?“ / „Nein, ich meinte die Entscheidung davor.“ / „Was davon haben wir schon umgesetzt?“ / „Dann mach beim letzten offenen Punkt weiter.“ – alle vier Sätze lieferten `WorkRecall`, `OwnerCorrectionIntent` und `OwnerDocumentIntent` `null` (Hostlauf 06.11.) und gingen ans lokale Modell, das weder Arbeitsjournal noch Besitzer-Ledger kennt; das Journal hatte keinen Zeitpunkt der Korrektur | `cognitive/WorkDecisionRecall` (rein, **nur lesende Sicht** – kein dritter Speicher): Entscheidungs-Einträge aus `CockpitWorkJournal` (gespeichert / STOP / Korrektur / unfertig, Journalzeit) **und** aus den bestätigten Besitzeraussagen des Ledgers (`ownerStatementHistory`, nur Sätze mit Entscheidungsbezug, Widerruf mit Zeit sichtbar); Episoden werden nie Besitzerfakt, Aussagen nie als „umgesetzt“ gemeldet (nicht prüfbar); Folgefragen nur gegen das in diesem Gespräch Gezeigte (`Focus`, sitzungsgebunden) – nach Neustart Rückfrage statt Raten; `Episode.correctedAt` (im Journal gesetzt, Altzeilen ohne); Kugel: `RecallHits` mit echter Trefferzahl; Avatar-Nicken nur bei verstandener „davor“-Korrektur / Weiterarbeit; Weiterarbeit nur über bestehende Pfade (Entwurfsfenster, PDF neu lesen + Auftrag neu starten, Modell fehlt → gesagt) | `WorkDecisionRecallTest` 8/8 (RED: 65 Compile-Fehler, Komponente fehlte); Journal-/Recall-/Korrektur-Tests unverändert grün; Suite OK (3046 Tests, 547 Klassen), Checks 4 PASS + 1 übersprungen; Fold7 REAL_DEVICE_NOT_RUN |

## Nicht geschehen / nur am Gerät prüfbar

- Keine Hörprobe, keine Latenz-/RAM-/Akkumessung einer Stimme; welche Engine auf dem Fold7 Wortgrenzen bzw. PCM liefert, ist unbekannt
  (Diagnose: in `AvatarEvent.AudioLevel.source` sichtbar – `PCM_ENVELOPE` / `WORD_RANGE_FALLBACK` / `TEXT_PACE_FALLBACK`).
- Keine phonemgenaue Synchronisation behauptet; Wortgrenzen bleiben die einzige echte Zeitquelle.
- Keine Änderung an Modellgewichten, Gedächtnisformaten, Agentenarchitektur, Paket-ID, Signatur, Version.

## Nächste Schritte (in dieser Reihenfolge)

1. Fold7: Standard-TTS-Engine wählen, TON lang drücken, weibliche Stimme wählen; Mundbewegung bei Samsung-/Google-/sherpa-Engine
   beobachten und die gemeldete `source` notieren → `TEXT_PACE_WORD_MS` ggf. an die gehörte Sprechrate anpassen.
2. Opus-Abnahmeliste `FOLD7_ABNAHME_JARVIS_20261106.md` (14 Schritte) auf einer APK aus genau diesem Stand.
3. Danach Opus' offene Punkte 4–8 (E-Mail-Entwurf auf Dokumentpfad, Kognition am Gerät); Punkt 6 (Zeitgedächtnis ↔ Arbeitsverlauf) ist mit M3 als
   schmale, lesende Verbindung umgesetzt – am Gerät: Entscheidungsfragen nach echtem Journal + Ledger stellen, Kugel-Reaktion und Nicken prüfen.
4. Priorität 2 (Kontextverständnis in Schichten), Priorität 3 (lesende Werkzeugoperation), dann 4 und 5 laut Auftrag 06.11.
