# TEST_RESULTS — continuum/m2-cognitive-memory (Grok 2 / Continuum Gedächtnis)

Stand: 2026-11-03 14:42 CET, Host-JVM (JDK 17.0.21, Gradle-Wrapper, --no-daemon). Alles HOST_VERIFIED, kein Gerätelauf.

## Vollständige Läufe (Code-Stand 09e17de3)
| Lauf | Ergebnis |
|---|---|
| `:app:testDebugUnitTest` (alle) | **1811 Tests, 0 Fehler, 5 übersprungen** (333 Klassen), BUILD SUCCESSFUL |
| `:app:lintDebug` | **No issues found** |
| Node (`node --test`, 58 Dateien aus nodetests2.txt) | **362 Tests: 359 bestanden, 0 fehlgeschlagen, 3 übersprungen** |

## RED → GREEN dieser Runde
| Schritt | RED | GREEN | Beleg |
|---|---|---|---|
| M2.1 Sicht-Cache | 2 rot (Budget, Wiederverwendung) | 9/9 | docs/evidence/m2-cognitive-memory-20261103/cache-*.txt |
| M2.3 Signierte Korrekturen | 5/6 rot | 6/6 | signed-corrections.txt |
| Punkt 4 Gedächtnisschichten | 5/5 rot | 5/5 | memory-layers.txt |
| Punkt 5 Fähigkeiten-Bibliothek | 4/6 rot | 6/6 | skill-library.txt |

## Messungen
- 10-Jahres-Benchmark, warme Abfragen (n=819): p95 **30,1 ms → 0,6–3,4 ms** (3 Läufe), p50 17,3 → 0,45–0,47 ms; 819/819 richtig.
- Genauigkeit unverändert: Spezialist 273/273, Baseline replyFromMemory 67/273; hypothetische Lecks 0/25.
- **Blinder deutscher LongMemEval-Holdout** (40 Fälle, eingefroren in 0cccf5f6 vor dem Lauf, nicht getunt):
  **34 richtig (85 %), 5 enthalten, 1 falsch, 0 verbotene Werte beantwortet.**
  Je Fähigkeit: Zeit 6/8, Korrekturen 6/8, Hypothetisches 8/8, Multi-Session 7/8, Enthaltung 7/8 (1 falsch: A8 „Ich habe gekündigt.“).
- LEX2-Ledger byteidentisch mit und ohne Signatur (Test). Kapazität 256 unverändert (Grenze per Test dokumentiert).

## Nicht ausgeführt
- Kein Lauf auf Gerät/Emulator (Fold7), keine instrumentierten Android-Tests.
- Avatar-Gedächtnissignale (ehem. Punkt 4 der alten Liste): bewusst nicht gebaut (Vertrag bei Continuum HAUPT, PR #373); nur Feld-Abbildung in GROK2-HANDOFF.md §10.
- Holdout-Fehler nicht nachgebessert (Dev-Set + neuer Holdout nötig).

## Update 2026-11-04 (M2.5–M2.7)
- Volle Unit-Suite (`:app:testDebugUnitTest --rerun-tasks`): **1820 Tests, 0 Fehler, 5 übersprungen**.
- M2.5 LEX3 (8bdba30e + Benchmark-Test-Anpassung): RED 3 → GREEN. Kapazität 4096 (gemessen: `owner_fact_capacity=4096`, 4097. wirft `IllegalStateException`). 1000 Einträge Round-Trip verlustfrei; Kaltlesen p95 11,3–15,7 ms (3 Läufe). LEX2 (256) verlustfrei gelesen, beim nächsten Schreiben migriert, LKG behält LEX2. Signierte Korrekturen grün, Sicht-Cache invalidiert korrekt, Lotto-Hypothese bleibt unbestätigt bei 300 Einträgen. Warm p95 819 Lookups: 1,43 ms.
- M2.6 ContinuumThinkingMemory (736d19cc): 4/4 grün (Reflexionen nur HYPOTHESIS, überschreiben keine bestätigten Fakten, Journal begrenzt und neustartfest). Hinweis: WIP-Tests liefen beim ersten Lauf direkt grün (kein separates RED in dieser Sitzung).
- M2.7 ContinuumMemoryStats (a7365d47): RED (Kompilierfehler) → 2/2 grün (leer = 0/null; Zählung aus echtem Ledger; newToday ab Mitternacht Europe/Vienna).

## Update 2026-11-04 (M2.8–M2.9)
- M2.8 LEX4 append-only: RED 2/4 → GREEN 4/4. Schreib-p95 100/1000/4000 Einträge: 9,5–11,3 / 24,7–27,0 / 83,3–85,7 ms → 0,47–0,60 / 1,7–1,8 / 4,4–4,9 ms.
- M2.9 Abruf: B Zeichen-n-Gramm-Hash + Zeit gewinnt (recall@1 0,886, recall@3 0,977, MRR 0,924, p95 0,63 ms, ~625 KB bei 1000), vs. A BM25-de + Zeit (0,818 / 0,841 / 0,830, 0,08 ms, ~119 KB). Details: docs/evidence/m2-retrieval-20261104/RESULTS.md.
- Volle Unit-Suite: **1827 Tests, 0 Fehler, 5 übersprungen**.

## Update 2026-11-04 (M2.10 LEX4-Recovery)
- RED: LedgerRecoveryTest 5 Tests (Kompilier-RED, bae57986) → GREEN 5/5. 4 alte „fail closed“-Tests auf Recovery-Policy umgestellt (Absicht beibehalten: kein Übernehmen manipulierter Daten, Beweise bleiben erhalten, keine Wiederbelebung widerrufener Fakten).
- Beschädigung: verifizierter Ketten-Präfix wird geladen, Rest unverändert in `experiences-v2.quarantine-<Zeitstempel>.bin`, `LedgerRecoveryReport` (Offset, verworfene Frames, ungeprüfte Frames danach, Quarantäne-Pfad, Sicherung) + Log. Kopf kaputt → neueste gültige Sicherung (bak1, dann bak2). Widerrufe aus ungeprüften Frames werden nur in restriktiver Richtung übernommen.
- Rollierende Sicherung bak1/bak2: bei jedem vollständig verifizierten Öffnen (Cache-Miss) und alle 256 Anhänge.
- Schreib-p95 100/1000/4000: vorher 0,47–0,60 / 1,7–1,8 / 4,4–4,9 ms; nachher 1,04–4,25 / 1,97–4,52 / 6,0–8,2 ms (2 Läufe, starkes Rauschen; der Pro-Anhang-Pfad hat nur einen Zähler mehr).
- Volle Unit-Suite: **1832 Tests, 0 Fehler, 5 übersprungen**.

## Update 2026-11-04 (M2.11–M2.12)
- M2.11 ContinuumMemoryEvents: RED 7 (Kompilier-RED, 74b73626) → GREEN 7/7 (63611a75): MemoryStored inkl. HYPOTHETICAL (Lotto), DocumentLearned (Knowledge-Archiv), RecallHits mit gesalzenem Hash, nichts nach close(), werfender Listener stört Ledger nicht, kein Klartext, Zustellung nicht auf Aufrufer-Thread, eine Instanz pro kanonischem Pfad.
- M2.12: RED 3 (Kompilier-RED, 37e37676) → GREEN 3/3 (5a84142e): Report übersteht Neustart ohne Inhalte; Quarantäne-IO-Fehler → Laden klappt, Report vermerkt, Schreiben abgelehnt bis Reparatur; Sicherungs-IO-Fehler blockiert Öffnen nicht.
- Schreib-p95 100/1000/4000: 1,41 / 2,09 / 4,56 ms (1 Lauf).
- Volle Unit-Suite: **1842 Tests, 0 Fehler, 5 übersprungen**.

## Update 2026-11-04 (M2.13)
- Event-Queue/Thread: RED 3 (Kompilier-RED, 6830123d) → GREEN 3/3; bestehende 7 Event-Tests grün. Überlauf: 256 bleiben, 44 von 300 verworfen (älteste), Zähler stimmt, emit < 500 ms für 300 Ereignisse bei blockiertem Listener; Thread Daemon + Name, endet nach letztem close(), Neustart bei addListener; 200× close/add-Race ohne Verlust.
- Volle Unit-Suite: **1845 Tests, 0 Fehler, 5 übersprungen**.
