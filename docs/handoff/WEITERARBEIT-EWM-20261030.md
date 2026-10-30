# Weiterarbeitsauftrag EWM-Ersetzungsfix v2 – Übergabe von Opus 5.5, 30.10.2026

**Gesamtplan:** `docs/handoff/OPUS-WEITERARBEIT-20261030.md` auf `claude/continuum-jarvis-exp20-p2-kxrxy2`.

**Ergebnisdokument:** `docs/research/exp20/EXP-20-M6-B4-EWM-FIX-20261030.md` (gleicher Branch), Belege unter `docs/evidence/exp20-m6-20261031/ewm-fix/`.

**Branch:** `claude/ewm-owner-subject-fix-kxrxy2` @ `a3705004`, Patchkandidat mit Produktivcode. **Nicht gemergt.** Übernahme nur nach Besitzer-Review.

## Inhalt
- **`Ewm1Fact.subject`** ist jetzt der grammatische Halter (ich/wir, oder die erste Nennung ohne Satzanfangs-Adverb).
  - `subjectKnown=false` bei Zitat, berichteter Rede und koordiniertem Subjekt.
- **Ersetzung** nur bei `SUPERSEDES_STATE` oder `CORRECTS_EVENT`; alles andere koexistiert.
  - Vergangene und zukünftige Aussagen beenden keinen gegenwärtigen Zustand.
- **`Ewm1Workspace.current()`** folgt bekannten Ersetzungen, auch wenn das Retrieval den Nachfolger nicht lieferte.
- **Belege:**
  - Neue Tests 38 + `Ewm1WorkspaceTest` 9 = 47/47; vorher 14 rot.
  - Host-Regression 788/788 (Ausgangsbasis 750/750).
  - Entwicklungsorakel mit 50 976 Paaren: Precision und Recall 1,0, vorher 0,219 und 0,387. Das ist **kein Holdout**.

## Aufträge in dieser Reihenfolge
1. **Review (Besitzer, ChatGPT-6):**
   - Wortlisten für die Zeitform in `Ewm1Lexicon` (`PAST_CUES`, `FUTURE_CUES`, `PRESENT_CUES`, `DEGREE`, `SAYING`).
   - `subjectKnown`.
   - Neues Verhalten von `current()`.
   - Entscheidung R7: Soll „Anna wohnte … → wohnt inzwischen …“ ablösen? Heute koexistiert es.
2. **Fremder Ersetzungs-Holdout (ChatGPT-6 schreibt, versiegelt, einmal geöffnet):**
   - Mindestens 200 frei formulierte Satzpaare mit Sollwert SUPERSEDES/CORRECTS/COEXISTS, vorab registriert.
   - **Abnahme:** Precision ≥ 0,95 und keine verlorene Unbeteiligten-Erinnerung.
3. **Android-Build und volle Gradle-Testsuite (Besitzer-PC):**
   - `./gradlew testDebugUnitTest`.
   - Danach Fold7 mit EWM2 AN: „Wo wohne ich?“ nach Umzug, nach Besuch und nach Zukunftsankündigung.
4. **Restfehler** als eigene, vorab registrierte Aufgaben:
   - R2 Alias (Halter über `aliases` verbinden).
   - R3 Pronomen-Koreferenz.
   - R4 Nebensatz zuerst.
   - R5 Preisdomänen.
5. **Gültigkeitsversionierung (R6):**
   - Erfassungszeit, Gültigkeitsbeginn, eingeschränkte frühere Aussage, weiter gültige Geschichte.
   - Innerhalb von EWM, **ohne zweiten Speicher**, mit eigenem Vertrag.
6. Erst danach selektiver Reset oder Predictive Coding (M6). Diese müssen die deterministische Baseline auf dem fremden Holdout schlagen.

**Reproduktion:** `scripts/exp20-ewm-host-regression.sh` vom Forschungsbranch; die Listen liegen unter `docs/evidence/exp20-m6-20261031/ewm-fix/`.
