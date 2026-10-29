# Continuum-Gehirn – technische Bestandsaufnahme (29.10.2026, Stand PR #216 @ `b47f4fac`)

Gelesen im Quelltext, nicht aus Dokumentation übernommen. Pfade relativ zu `app/src/main/java/at/heinz/deviceai/`.

## Weg eines normalen Chat-Zugs (tatsächlicher Datenfluss)

1. `AgentActivity` (≈ Z. 2790 ff.) synchronisiert Besitzerfakten und geprüfte Webnotizen in den Ledger
   (`learning/ContinuumLearningCore`) und ruft `cognitive/CognitiveDecisionBridge` → `learning/ContinuumCognitiveRuntime`
   → `learning/ContinuumMemoryDialogue.replyInConversation` auf.
2. **Abruf A (Ledger):** `ContinuumMemoryDialogue.rank` = Wort-Evidenz-Schwelle 0,6 + TF-IDF über Zeichen-3–5-Gramme,
   Top-3. Die SWB1-Ähnlichkeit wird berechnet und gespeichert, **fließt aber nicht ins Ranking ein** (Kommentar im Code:
   „Neural similarity is reported, not weighted“). CRE1 darf optional nur die Reihenfolge ändern (Forschungsmodus).
   Kontext: nur eine Regex für Folgefragen („darüber|dazu|damit|weiter|noch mehr|und warum|und wie“) hängt das letzte Thema an.
3. Kein Treffer → Titelabgleich im Wissensarchiv (`ContinuumArchiveAnswerPolicy`), sonst Gemma ohne Erinnerung.
   Treffer → die gerenderte Antwort (max. 3 × 250 Zeichen) geht als **ein** JSON-Textblock (`cognitive/LocalGroundedRecall`)
   in den Gemma-Prompt.
4. **Abruf B (Agent):** `runLocal` → `BoundedAgentOrchestrator` → `cognitive/HybridMemoryRetriever.retrieve` über
   `memoryStore.all() + ownerFactMemories()`: BM25 auf exakten Kleinwörtern ohne Stammformen; die Gewichte für
   Semantik (0,25) sind ohne übergebenen Scorer immer 0. Bis zu 8 Erinnerungen landen über `cognitive/AiContextCompiler`
   zusätzlich im Prompt – unabhängig von Abruf A, ohne Abgleich, Besitzerfakten können doppelt erscheinen.
5. Schwere Aufgaben: `ChatReasoningRouter` → `ContinuumIntelligenceCoordinator`/`Deliberation` (bis 4 Zusatzaufrufe,
   20 s) liefert einen als unbestätigt markierten Hinweis.
6. Nach der Antwort: `cognitive/LocalDialogueQualityGate` prüft Leere, Länge, Echo und Wiederholung –
   laut eigenem Text ausdrücklich **keine** sachliche Richtigkeit oder Beleglage.

## Module und ihr tatsächlicher Anschluss

| Modul | Zustand | Anschluss an den Chat |
|---|---|---|
| SWB1 (`TrainableSubwordEncoder`, 4 113 Parameter) | trainiert, Holdouts eingefroren | nur Hash/Anzeige im Dialog; kein Einfluss auf Ranking |
| SWB2 (`Swb2ExperimentalEncoder`) | Forschung, EXP-04: REJECTED | keiner |
| CRE1 (`Cre1*`) | Forschung, optionaler Reranker | nur im Shadow-/Forschungsmodus |
| CTX3 (`learning/context`) | TEST3 FAIL | OFF/SHADOW |
| `StructuredKnowledge` (Vorwärtsverkettung) | funktionsfähig | nur `BootstrapBrain` für ausdrücklich strukturierte Sätze |
| `MemoryEcology` (MemoryGraph, ClaimGraph), `ProofCarryingMemory` | Code vorhanden | **von keiner Datei verwendet** |
| LCT1/WSC1/TTT1 (Test-Quellset) | TEST bestanden (0 falsch) | nur auf strukturierten Tripeln, kein Leser für Besitzersätze |
| `HybridMemoryRetriever` | aktiv im Agentenpfad | ohne Semantik-Scorer, ohne Abgleich mit Abruf A |

## Engpässe (nach Wirkung auf die Antwortqualität)

1. **Falsche Erinnerung im Gespräch:** B0 zitiert auf dem unabhängigen TEST3 in 77 von 198 Zügen eine falsche erste
   Quelle (Pronomen, Wechsel, Korrektur, unbekannte Person). Gemma erhält dann plausible, aber falsche Belege.
2. **Keine Verknüpfung:** „Wo wohnt mein Bruder?“ braucht „Mein Bruder heißt Max.“ und „Max wohnt in Linz.“ –
   der Abruf kennt keine Entitäten; LCT1 kann Ketten prüfen, bekommt aber keine Tripel aus Besitzersätzen.
3. **Kein Zeitbewusstsein:** neuere und ältere Aussagen zum selben Merkmal stehen gleichrangig nebeneinander.
4. **Keine Belegprüfung der Gemma-Antwort:** erfundene Zahlen, Namen oder Orte bleiben unentdeckt.
5. **Zwei unverbundene Abrufe** im selben Prompt (A und B), ohne gemeinsame Schnittstelle.

## Gewählte Hebel (EWM1, siehe Vorregistrierung)

Ein gemeinsames **Evidenz-Arbeitsgedächtnis** zwischen Ledger und Gemma: Diskursfokus (1), Entitätsgraph (2),
Zeitordnung (3) und Aussagenprüfung (4). Es ersetzt keinen vorhandenen Abruf, ruft B0 auf und fällt bei Unsicherheit auf B0
zurück. Rein deterministisch, offline, ohne neue Abhängigkeit, ohne Gewichtsänderung. Hebel 5 (ein einziges
Evidenzpaket statt zweier Abrufe) ist eine Integrationsaufgabe für Codex und wird im Ergebnisbericht beschrieben.
