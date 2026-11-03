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
