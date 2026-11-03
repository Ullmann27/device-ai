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
