# EWM1-H5 – Quellenabruf mit Evidenzpaket auf dem Gen16-Archiv · Vorregistrierung (29.10.2026)

Festgelegt **vor** jeder Zeile H5-Code. TEST4 (H1–H4) bleibt unberührt: keine Quelle, kein Gewicht und keine Frage aus
dem Gen16-Archiv wird für H1–H4 verwendet, kein TEST4-Inhalt fließt in H5.

## Daten (lokal, nicht im Repository)

`Continuum_Gen16_Quellen_556.zip`, SHA-256 `92e95ac2708d192bd2596cd649db7491fc66ab59481d8861ef906ac2d91c24dc`,
556 `CKA1`-Datensätze. Bereits geprüft mit dem **unveränderten** Produktionscodec
`ContinuumKnowledgeArchive.importZip` (JVM, Temp-Verzeichnis): 556/556 gültig, erneuter Import idempotent,
alle `WIKIPEDIA_EXCERPT`, Lizenz „Wikipedia-Mitwirkende · CC BY-SA 4.0“, kein Quelldatum, gesammelt 20.–28.10.2026,
Auszüge auf 390 Zeichen gekappt (391 enden mitten im Satz), 4 Gruppen wortgleicher Texte (Weiterleitungen),
Klassen: 490 Artikel, 30 Kalendertage, 13 Jahresseiten, 8 Begriffsklärungen, 15 sehr kurz.
Quelltexte und Titel werden **nicht** ins Repository übernommen; dort stehen nur Code und aggregierte Zahlen.

## Fragen (zur Laufzeit erzeugt, deterministisch)

Ziel-Datensätze: Klassen Artikel und sehr kurz. Titel ohne Klammerzusatz. Je Ziel fünf Formen:
F1 „Was ist {T}?“, F2 „Was weißt du über {T}?“, F3 „Erkläre mir bitte {T}.“,
F4 „Kannst du mir etwas über {T} sagen?“, F5 F1 in Kleinschreibung. Gold: der Datensatz oder ein wortgleicher Zwilling.
Unbekannt: 40 fest vorgegebene Themen (Liste im Generator), jeweils „Was ist {T}?“; ein Thema, das im Archiv
vorkommt, wird übersprungen. Erwartung dort: kein Zitat.

**Split nach Datensatz-ID:** erstes Hex-Zeichen `0–7` = DEV (Entwicklung), `8–f` = **TEST** (einmal nach Code-Freeze).
Der Index enthält immer alle 556 Datensätze; geteilt wird nur, welche Datensätze Fragen erhalten.
Die Unbekannt-Liste wird je zur Hälfte (gerade/ungerade Position) DEV und TEST zugeordnet.

## Systeme

- **A0 (heute):** `ContinuumKnowledgeArchive.search(q, limit = 20)` + `ContinuumArchiveAnswerPolicy.select(q, hits)`.
- **S1 (EWM1-Quellen):** neues Forschungsmodul im Test-Quellset: zerlegungs- und beugungstoleranter Titelabgleich,
  Weiterleitungs-Zwillinge als Aliasse, Begriffsklärungen/Kalender-/Jahresseiten nie als erste Antwort,
  Evidenzpaket mit Herkunft, Lizenz, Vertrauensklasse `SOURCE_OBSERVATION` und Hinweis „Auszug gekappt“.

## Messgrößen und Entscheidung (TEST, einmalig)

ANSWERED (erstes Zitat ∈ Gold), WRONG (erstes Zitat ∉ Gold), MISSED (nichts zitiert) für Zielfragen;
FALSE_CITE / ABSTAINED für unbekannte Themen. **S1 bestanden**, wenn alle gelten:
(1) ANSWERED(S1) ≥ ANSWERED(A0) + 10 Prozentpunkte der Zielfragen; (2) WRONG(S1) ≤ WRONG(A0);
(3) FALSE_CITE(S1) ≤ FALSE_CITE(A0); (4) S1-Abruf p95 ≤ 50 ms je Frage (JVM, 556 Datensätze).

## Grenzen

Generierte Fragen prüfen das Finden und Zuordnen, nicht die sachliche Richtigkeit der Auszüge (Wikipedia-Stand
20.–28.10.2026, ungeprüft). Kein Gemma-Lauf, kein Gerät.
