# TEST_RESULTS – continuum/m5-integration-repair (Draft PR #372)

Stand 03.11.2026, Host-Lauf (JDK 17, Gradle 8.11.1, AGP 8.10, Node 24) im Klon `/workspace/device-ai-m5`.
Alle Zahlen aus echten Läufen (`app/build/test-results/testDebugUnitTest/*.xml`, `node --test`, `lint-results-debug.txt`).

## Endstand (Code-HEAD `8ed274fc`)
| Suite | Ergebnis |
|---|---|
| JUnit `:app:testDebugUnitTest --continue` | **1777 Tests, 0 Fehler, 5 übersprungen** (Basis #369 `7316b1f5`: 1752/0/5) |
| Node (58 Android-Vertragsdateien + `m5-voice-duplex-wiring.test.mjs`) | **367 Tests, 364 pass, 0 fail, 3 skip** |
| `:app:lintDebug` | **No issues found** |

## Pro Meilenstein (RED → Fix → GREEN)
| # | Defekt | RED | Fix | GREEN | Voll-JUnit danach |
|---|---|---|---|---|---|
| M1 | ToolGate-Totalausfall nach 512 Quittungen | `992669f0` | `1a7ebb62` (+E2E `27b5253d`) | 3/3 + E2E 1/1 | 1755/0/5 |
| M2 | Barge-in/Echo (TTS spricht ins Mikrofon) | `0c5701c2` | `28adbc72` | 2/2 + Node 2/2 | 1758/0/5 |
| M3 | Diktat überschreibt getippten Entwurf | `3bcfa34d` | `eb4c5536` | 5/5 | 1763/0/5 |
| M4 | Mehrere-GB-Modell ohne RAM/Thermik-Prüfung geladen | `9b0b129b` (Kompilierfehler + Node 1 FAIL) | `e915129b` | 6/6 + Node 4/4 | 1769/0/5 |
| M5 | Journal unlesbar → falsche Meldung „Wirkung kann fortbestehen“ | `1c79c0e0` (1 FAIL) | `5ed42596` | 2/2 (+JournalAvailability 2/2) | 1771/0/5 |
| M6 | Live-Turn ohne Modellidentitätsprüfung pro Schritt | `04d8ed90` | `99372a41` | 2/2 + Node 5/5 | 1773/0/5 |
| S3 | Owner-Ja an exakten Aufruf gebunden (Argumenttausch falsch gemeldet) | `344093b8` (1 FAIL von 4) | `8ed274fc` | 4/4 | 1777/0/5 |

JSON-Escape-Überlauf (M5): per Sonde nachgewiesen (8.000 Rohzeichen → 8.064 kodiert → verworfen), bewusst nicht geändert (Vertrag „ganz verwerfen“); Details im Handoff.

## Nicht getestet / nicht möglich
- Fold7-Gerät, APK-Build/Signatur, native LiteRT-LM/Gemma-Inferenz, echte RAM-/Thermikwerte, TTS-/Mikrofon-Hardware, Robolectric/Instrumentation.
- S4 Prompt-Injection-Guard und S5 Limitwächter: nicht umgesetzt (Abbruch wegen Nutzungslimit); RED-Entwurf nur auf der Box.

Details: `docs/research/omega/M5-INTEGRATION-REPAIR-HANDOFF-20261103.md`.

## Nachtrag S4 – Schutz gegen eingeschleuste Befehle (Code-HEAD `afa2c7e5`)
- RED `2254d6ef`: `ExternalContentInjectionGuardTest` 3 Tests, 2 rot (`injectedDeleteInAToolResultNeverReachesExecution`, `externalContentIsMarkedUntrustedInTheModelContext`).
- Fix `afa2c7e5`: `ExternalContentActionGuard` an der Werkzeug-Schnittstelle von `BoundedAgent`; Tool-Beobachtungen werden im Modellkontext als nicht vertrauenswürdig markiert.
- GREEN: `ExternalContentInjectionGuardTest` 4/4.
- Volle Regression auf `afa2c7e5`: JUnit 1781 Tests, 0 Fehler, 5 übersprungen; Node 367 Tests, 364 bestanden, 0 Fehler, 3 übersprungen; Lint "No issues found".
- Offen (bei Limit-Abschluss nicht begonnen): Kürzung der Werkzeug-Ausgabe auf ~3900 Zeichen; Einbau des Freigabe-/S4-Gates in ChiefWorkerPool (HAUPT) und Lumo-Aufgabenschleife.

## Addendum 2026-11-04 – B5 (S4-Guard nach Herkunft/Risiko) + Tool-Ausgabe-Cap 3900

Box: JDK 17.0.21, Gradle `testDebugUnitTest lintDebug`, Node `node --test .github/continuum/*.test.mjs`. Kein Fold7/APK/Inferenz-Test.

**B5 (Fehler):** `ExternalContentActionGuard.ownerRequested` war reine Verb-Wortliste: `move_file` nach einer Beobachtung wurde verweigert, wenn „verschieb/move“ nicht im Auftrag stand; negationsblind („ohne etwas zu loeschen“ = Lösch-Absicht); „teil“ zu breit.
- RED `8c31ecaa`: 7 B5-Tests aus `FirstThinkingRunE2ETest` (integration @68a34bd9) ohne `@Ignore` übernommen (PENDING-Platzhalter fremder Bots weggelassen; eine `OwnerStatementModality`-Assertion entfernt, Klasse aus #371 fehlt auf diesem Zweig). Ergebnis vor Fix: 9 Tests, 7 failed (`/workspace/m5-logs/b5-red-e2e.log`). Guard-Unit-Test auf neue Semantik umgeschrieben (Signaturänderung → Compile-RED).
- Fix `0c2d5a16`: Entscheidung nach Herkunft + Aktionsrisiko. Lesen/Per-Request-Consent/noch keine Beobachtung → normaler ToolGate. Reversible lokale Aktionen (move/stop/restart) → normale Owner-Freigabe. High-Impact (jedes DESTRUCTIVE-Tool, install/uninstall, send/post/share/upload, kaufen/zahlen) nach externem Inhalt → jedes Ziel-Argument (Pfad/Paket/Empfänger) muss aus dem Owner-Auftrag stammen, sonst Verweigerung ohne Rückfrage; Nachrichtentext ist Nutzlast. S4-Garantien bleiben (injiziertes delete: 0 Ausführungen, 0 Rückfragen).
- GREEN gezielt: FirstThinkingRunE2ETest 9/9, ExternalContentInjectionGuardTest 4/4, BoundedAgent* 8/8.

**Cap 3900:** RED `8c7f0416`, Fix `51cac209` – `ToolOutputCap` (Gewicht = JSON-escaped Länge, Surrogat-sicher, Marker „[gekürzt: … Limit 3900]“). 4/4 grün.
- `ChiefWorkerPool` ist NICHT auf diesem Zweig (gehört HAUPT, #379). Statt fremden Code zu übernehmen: Patch `docs/research/omega/M5-CHIEFWORKERPOOL-TOOL-OUTPUT-CAP.patch` (WorkerScope.runner + WorkerReport.summary). In temporärem Worktree auf 68a34bd9 angewendet: ChiefWorkerPoolTest 3/3, ChiefMemoryAndJobTest 4/4, ToolOutputCapTest 4/4 grün (`/workspace/m5-logs/cwp-patch-check.log`).
- Verworfen: Cap in `LocalToolContinuation.observationData` – bricht 4 bestehende Verträge (Caller-Gate weist Übergröße bewusst fail-closed ab).

**Volle Regression:** JUnit 1794 Tests, 0 Fehler, 5 übersprungen (vorher 1781/0/5). Lint: „No issues found“. Node: 1306 Tests, 1293 pass, 0 fail, 13 skipped (Aufruf mit allen `*.test.mjs`).
