# Continuum Ω – M5 Integration-, Reparatur- & Optimierungsstrang (03.11.2026)

Bearbeitung: Grok Bot (M5, Chief Integration/Repair) im Auftrag von Heinz Ullmann.
Branch: `continuum/m5-integration-repair`, Basis **`7316b1f5`** = Kopf von PR #369
(`continuum/m1-grok-multiturn-routing-20261103`, Kette #365 → #366 → #367 → #368 → #369).
Begründung der Basis: oberste, vollständig getestete Integrationsstufe der aktuellen Fold7-Kette
(Basis-Lauf hier: 1752 JUnit, 0 FAIL, 5 skip). `main`, Signatur, Paket, versionCode und Nutzerdaten unverändert.

Arbeitsweise je Fehler: Nachweis → Ursache → RED-Test (ausgeführt, Ausgabe gesichert) → minimaler Fix → GREEN →
volle Regression → unabhängige Prüfung (frischer Clone, `clean`). Alle Zahlen stammen aus hier ausgeführten Befehlen
(JDK Temurin 17.0.21, Android SDK 36, Gradle-Wrapper 8.11.1, Node 24.22.1). `HOST_VERIFIED`, **nicht** `DEVICE_OBSERVED`.

## Meilenstein 1 – ToolGate ↔ dauerhaftes Quittungsjournal: Totalausfall nach 512 Werkzeugaufrufen

- **Fehler:** `FileToolExecutionReceiptStore` ist hart auf 512 Quittungen begrenzt; im gesamten Code gibt es keine
  Aufbewahrungs-/Bereinigungsregel. Jeder ToolGate-Aufruf (auch abgelehnte) legt eine Quittung an. Ab Aufruf #513 einer
  Installation liefert **jedes** Gerätewerkzeug `REJECTED · "Durable journal unavailable; prior outcome UNKNOWN"`; im
  Bootstrap-Pfad (`BootstrapBrain → ReadOnlyToolPlan → BoundedAgentOrchestrator → ToolGate`) wirft der Orchestrator
  `IllegalArgumentException: Tool receipt store is full` → Owner sieht „Geräteprüfung fehlgeschlagen“.
- **Ursache:** `propose()` prüft `records.size < MAX_RECEIPTS` ohne jede Retention.
- **RED** (`992669f0`): `ToolReceiptJournalRetentionTest` 3 Tests, **2 FAIL** – „first non-success at call #513: Durable
  journal unavailable…“ bzw. „Tool receipt store is full“. E2E-Wächter `ToolJournalSaturationE2eTest` gegen die Quelle vor dem
  Fix: **1/1 FAIL** („orchestrator threw: … Tool receipt store is full“).
- **Fix** (`1a7ebb62`): Bei vollem Journal werden **nur** die ältesten Quittungen mit sicherem Endzustand
  (SUCCEEDED/FAILED/TIMED_OUT/CANCELLED/REJECTED **ohne** STARTED/DISPATCH_COMMITTED/UNKNOWN-Historie) entfernt.
  PROPOSED/APPROVED/START_PENDING und jede UNKNOWN-Quittung bleiben für Besitzerprüfung und Replay-Sperre; ein Journal voller
  UNKNOWN-Quittungen blockiert weiterhin (fail-closed, getestet).
- **GREEN:** `ToolReceiptJournalRetentionTest` 3/3, `ToolExecutionReceiptStoreTest` 5/5, `ToolJournalSaturationE2eTest` 1/1 (`27b5253d`).
- **Messwert nebenbei:** 515 ToolGate-Aufrufe gegen ein 512er-Journal: 7,0–11,5 s auf diesem Host (≈ 14–22 ms Journal-I/O
  pro Aufruf, 5 vollständige Neuschreibungen mit fsync je Aufruf). Kandidat für spätere Optimierung, bewusst **nicht** geändert
  (Dauerhaftigkeitssemantik).
- **Grenze:** Eine entfernte, sehr alte SUCCEEDED-Quittung schützt diese Call-ID nicht mehr vor Replay. Produktive Call-IDs sind
  UUID-/Turn-gebunden (`bootstrap-<UUID>`, `local-<turnId>`, `web-<UUID>`), daher kein realer Replay-Pfad bekannt.

## Meilenstein 2 – Sprache ↔ TTS ↔ Avatar: Barge-in und Halbduplex (Argon 4, Abschnitt 7.1)

- **Fehler A (Barge-in):** `startVoiceInput` stoppt nur `voiceEngine` – Avatar-Mund (`voiceFrame`/`TtsHologramSignal`) und
  `voiceUtteranceId` bleiben aktiv, bis der asynchrone TTS-`onStop` eintrifft; späte `onStart/onAudioAvailable` der
  verworfenen Äußerung können den Mund während des Zuhörens wieder öffnen.
- **Fehler B (Echo):** `speakBootstrap` (spricht jede Nicht-Modell-Antwort über `addAssistantMessage → addHomeGreeting`) prüft
  nicht, ob das lokale Mikrofon gerade zuhört – JARVIS' eigene Stimme kann vom On-Device-Erkenner ins Eingabefeld transkribiert werden.
- **RED** (`0c5701c2`): Node-Quellvertrag `.github/continuum/m5-voice-duplex-wiring.test.mjs` 2 Tests, **2 FAIL**; JUnit
  `VoiceDuplexBargeInTest`: Kompilierfehler (kein `microphoneActive`-Vertrag).
- **Fix** (`28adbc72`): `startVoiceInput` ruft nach `voiceEngine?.stop()` sofort `stopVoiceAnimation()`;
  `LocalSpeechInputPolicy.maySpeak(…, microphoneActive)` verweigert TTS bei aktivem Mikrofon, `speakBootstrap` übergibt `voiceListening`.
- **GREEN:** `VoiceDuplexBargeInTest` 2/2, `LocalSpeechInputPolicyTest` 6/6, Node-Vertrag 2/2.

## Meilenstein 3 – Sprache → Eingabefeld: Diktat überschreibt getippten Entwurf

- **Fehler:** `applyLocalSpeechText` setzt `input.setText(spoken)` – ein bereits getippter oder nach Neuaufbau wiederhergestellter
  Entwurf geht bei jeder Spracheingabe kommentarlos verloren (Kontextübergabe Sprache → Composer).
- **RED** (`2f…` siehe PR, Commit „test(voice): RED – Diktat überschreibt…“): Node-Vertrag 3 Tests, **1 FAIL**; JUnit
  `SpeechDictationDraftTest` (5 Tests): Kompilierfehler (kein `mergeDictation`).
- **Fix** (`eb4c5536`): `LocalSpeechInputPolicy.mergeDictation` fügt am Besitzer-Cursor ein (ersetzt nur eine echte Auswahl,
  Einzel-Leerzeichen an den Rändern); ohne Composer-Fokus wird angehängt. Weiterhin reine Vorschau, kein Senden/Ausführen.
  Der ältere Quellvertrag `coding-agent-speech-wiring.test.mjs` prüfte wörtlich `input.setText(spoken)` und wurde auf den neuen
  Vertrag angepasst (Sinn – nur Vorschau, keine Ausführung – unverändert geprüft).
- **GREEN:** `SpeechDictationDraftTest` 5/5, `LocalSpeechInputPolicyTest` 6/6, `VoiceDuplexBargeInTest` 2/2, Node (3 Dateien) 28/28.

## Regression (`HOST_VERIFIED`)

| Prüfung | Stand | Ergebnis |
|---|---|---|
| JUnit voll `:app:testDebugUnitTest --continue` | Basis `7316b1f5` | 1752 Tests, 0 FAIL, 5 skip |
| JUnit voll | nach M1 (`1a7ebb62`) | 1755, 0 FAIL, 5 skip |
| JUnit voll | nach M2 (`28adbc72`) | 1758, 0 FAIL, 5 skip |
| JUnit voll | nach M3 (`eb4c5536`) | 1763, 0 FAIL, 5 skip |
| Node, 58 Android-bezogene Testdateien (+ neue M5-Datei) | M1 / M2 / M3 | 362/359 pass · 364/361 pass · 365/362 pass, je 0 FAIL, 3 skip |
| `:app:lintDebug` | `eb4c5536` | „No issues found“ |
| Merge-Probe (`git merge-tree`) | gegen grok1-integration, m2-grok-context-state, m2-cognitive-memory, #360, #361, #362 | konfliktfrei |
| Merge-Probe | gegen #363, #364 | je 2 Konflikt-Hunks in `AgentActivity.kt` – **identisch schon auf der Basis #369**, durch M5 keine neuen |

Nicht ausgeführt / nicht möglich: Fold7-Gerätetest, APK-Build/Signatur, native LiteRT-LM/Gemma-Inferenz, TTS-/Mikrofon-Hardware.

## Gefundene, bewusst NICHT geänderte Befunde (für Folgearbeit)
- `cognitive/ContinuumLocalAgentModel.kt` (Gemma → BoundedAgent-Adapter) ist **nirgends im Laufzeitpfad** verdrahtet;
  `AgentActivity.runLocal` baut einen eigenen Inline-`AgentModel` – widersprüchliche Doppelimplementierung.
- `ConstrainedResourceFabric` (RAM/Thermik-Gate) und `ComputeResultVerifier`/`ComputeScheduler` werden nur in Tests verwendet;
  das Laden eines mehrere GB großen Modells hat kein gemessenes RAM-/Thermik-Gate (Gerätemessung nötig, sonst Risiko falscher Ablehnung).
- ToolGate meldet bei unlesbarem Journal `uncertain=true`; BoundedAgent sagt dem Besitzer dann „eine bereits ausgelöste Wirkung kann
  fortbestehen“, obwohl für eine frische Call-ID nichts dispatcht wurde.
- (`SOURCE_ONLY`, nicht per Test belegt) Ein großes, gültiges Werkzeugergebnis (≈ 7,9–8 k Zeichen mit vielen Zeilenumbrüchen) überschreitet nach JSON-Escaping das
  8.000-Zeichen-Budget von `LocalToolContinuation.encode` → ganzer Gemma-Turn endet mit `LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET`.

## Rückfall
Jeder Meilenstein ist ein eigenes RED-/Fix-Commit-Paar und einzeln per `git revert` rücknehmbar.
