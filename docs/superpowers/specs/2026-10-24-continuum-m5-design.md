# M5 – Kontext, Rückfrage und bestätigter Projektplan

Start: 331e2d25c655b07e578ce4fa49da64fbc4f97c61; alle M4-Commits enthalten, zehn zusätzliche Commits.
Eigener Zweig sol/continuum-m5-context-coding-experiments-20261024. EXP-08/09/14.

Autonomer Besitzerauftrag: Implementierung und begründete Architekturentscheidungen ohne erneute Designfreigabe.

## Auswahl
Eine größere CTX2-Regelsammlung wäre günstig, aber macht Zustand/Quellen weiterhin implizit. Ein neuer lokaler
LLM-Decoder hätte ohne Modell-/Hardwaremessung keinen Beleg. Gewählt: CTX2 gezielt reparieren und CTX3 als
kleinen typisierten Referenz-/Quellengate vor vorhandener Retrieval-Logik untersuchen. Keine zweite Dialogpipeline.

## Grenzen und Komponenten
1. CTX2: ausschließlich generalized=true erhält Negations-/Mehrdeutigkeitsprüfung; CTX1 default unverändert.
   Vollständig negierte Referenten werden nicht salient, Rückfragen invalidieren widersprochene Rückbezüge.
2. CTX3: begrenzte typisierte Entitäten, Aliaslisten, grammatisches Geschlecht und aktuelle Source-IDs;
   positive/negative Kandidatenmengen statt Reihenfolge als Gewissheit. Unklare Referenz fragt nach.
   Quelländerung/Widerruf invalidiert Sitzungskontext. Keine dauerhaften Besitzerfakten aus Turns.
3. Schmaler optionaler Shadow-Adapter in ContinuumLearningCore: OFF standardmäßig, SHADOW opt-in, sichtbare
   Baselineantwort unverändert. Nur aktuell bestätigte Besitzerbelege, IDs/Gründe statt privater Rohtexte protokollieren.
   Abweichung ist kein nachgewiesener Qualitätsgewinn. Keine automatische produktive Promotion.
4. M5-Auftragsdialog: nur die zwei unveränderten M4-Pläne. Projektart und lokale Speicherung ausdrücklich klären;
   nicht vorhandene Abhaken-/Lösch-/Sync-Funktionen ablehnen/erfragen. Typisierte explizite/abgeleitete Slots,
   begrenzter Dialog, Planrevision und Hash. Besitzer sieht Vorschau und bestätigt diese Revision.
   Freigabe übergibt nur die feste Projektwahl an vorhandenen Builder. Dessen Bestätigungen, Termux, Lease,
   CCC1 und Buildbelege bleiben unverändert wirksam. Keine Shell/Dateipfade aus Chat.
5. Android: eigene M5-Laborseite aus bestehendem Builder, klar als Forschung markiert; Shadow-Schalter AUS,
   Quelle/Grund sichtbar; natürlicher begrenzter Auftragsdialog und bestätigte Weitergabe an Builder.
6. ÜBEN/PRÜFEN/LERNEN werden dokumentiert getrennt; kein neuer Gewichtslerner. Ein Vordergrund-Prototyp nur,
   falls A–C verifiziert sind. Hintergrundjobs bleiben OS-gesteuert, kein garantiertes 30-Sekunden-Intervall.

## Forschung
TEST2 ist geöffnet: nur bekannte Regression, keine frische Blindvalidierung. Originalberichte bleiben bytegleich.
Neuen unabhängig geschriebenen TEST3 vor Freeze nur hashen, nicht ansehen/ausführen. Registrierung friert
Datensatzhash, Codecommit und Erfolgskriterien vor einmaliger finaler Messung. B0/B1/B2/CTX3 vergleichen;
Fehler, bekannte ausgelassene Belege, nötige/unnötige Rückfragen, zusätzliche falsche Quellen, Latenz und begrenzter
Zustand. Keine Anpassung nach TEST3. Erfolg: weniger falsche Belege als B0, kein Rückgang korrekter Belege,
keine Quellen außerhalb aktueller Freigabe; andernfalls nur Shadow und dokumentiertes negatives Ergebnis.

## Prüfgegenstände
Negative Namen/Pronomen und gemischte bekannte/unbekannte Entitäten; fünf und längere Turns; Quellwiderruf;
konkurrierende Aliase; bedingte/indirekte Aufträge; Tippfehler sind keine Autorisierung; veraltete Bestätigung;
Pause/Reset; keine Schreibaktionen aus Forschungsdaten. TDD pro Modul, reale SDK/JUnit/UI/Lint und Node/Backend.
Keine Besitzer-APK/Installation/Signierung, kein Main-Merge, kein Force-Push. Regelmäßige Commits/Pushes.
