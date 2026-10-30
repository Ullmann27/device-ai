# M1 · Integrationsmatrix Continuum (Stand 30.10.2026)

**Geprüfter Stapel:** #238 `a3fdc179` → #241 `39772c9b` → #242 `7924ba07` (Code `e06e17a4`) → #243 `ebba7e16` → dieser Zweig (#244).
Daneben: #236 `6bc54545` (Updater, eigener Zweig), #239 `30fbef96` (Alternative zu #238, nicht übernehmen), #240 `a2600c0e` (ARGON-Forschung, in #242 aufgegangen).
**Methode:** Für jeden Baustein wurde gezählt, in wie vielen anderen Dateien unter `app/src/main` er referenziert wird und in wie vielen Testdateien. Die entscheidenden Aufrufketten wurden im Code gelesen. Das ist keine vollständige Prüfung aller Zweige.

## Zwei Wege einer Nachricht (gelesen)

1. **Belegdialog:** `AgentActivity` → `LocalConversationContext.inspectRequest` (dort F1-Shadow `Argon4ConversationShadow` → `DialogStateKernel`) → `ContinuumCognitiveRuntime.reply` (dort `Argon4EvidenceShadow` → `JustifiedRelationStore`) → `ContinuumChatOutcome` → `ContinuumBrainBridge.decide` (EWM2, OFF/SHADOW/ON, Standard OFF).
2. **Gemma-Synthese:** `LocalFirstPolicy.afterMemory` = LOCAL_MODEL → `LocalGroundedRecall.boundedOrNull` → `runLocal` → `BoundedAgentOrchestrator` → `AiContextCompiler` (Gerätedatum seit #242) → `ChatReasoningRouter.advise` (dort `ComputeAllocationPolicy` als Shadow-Prognose) → LiteRT-LM (CPU) → **neu (#244): `LocalModelClaimAudit` → Trace-Stufe**.

## Matrix

| Baustein | Aufrufer im Hauptcode | Tests | Zustand | Bekannte Lücke | Priorität / Maßnahme |
|---|---|---|---|---|---|
| `LocalConversationContext` | 2 Dateien | 5 | produktiv | 4 Paare; F1-Shadow nur beobachtend | P1: F1 erst nach Fold7-Messung in den Prompt |
| `DialogStateKernel` / `Argon4ConversationShadow` | 1 | 3 / 1 | SHADOW (#242) | Ergebnis nur als Zahlen sichtbar | messen (M7: +60 µs/Zug Host) |
| `JustifiedRelationStore` / `Argon4EvidenceShadow` | 1 | 1 / 1 | SHADOW (#242) | keine Qualitätsmessung | P2 |
| `ComputeAllocationPolicy` (in `ChatReasoningRouter`) | 1 | 1 | SHADOW-Prognose | Prognose wird von niemandem gelesen | P1: in Trace aufnehmen, gegen tatsächliche Pässe vergleichen |
| `ContinuumBrainBridge` / `Ewm1Brain` | 1 | 2 / 2 | OFF (Besitzerschalter) | Aktivierung braucht Besitzerfreigabe | P0 nach Fold7 |
| `Ewm1ClaimCheck` | **0 vor #244**, jetzt 1 (über Audit) | 1 | vorher ungenutzt, jetzt SHADOW | Polaritätsprüfung global → im Audit satzweise | **in #244 verbunden** |
| `LocalModelClaimAudit` (neu) | 1 (`runLocal`) | 1 (6 Tests) | SHADOW | nur Token/Polarität, kein Entailment | Fold7: Flag-Rate gegen Besitzer-Feedback |
| `CognitiveDecisionBridge` | 1 | 2 | produktiv, Reasoning-Adapter `null` | Belegdialog ohne Modell | bewusst so lassen |
| `LocalModelReasoningAdapter` | 1 | 3 | vorhanden | — | — |
| `AiContextCompiler` | 1 | 2 | produktiv, Datum seit #242 | — | — |
| `HybridMemoryRetriever` | 5 | 1 | produktiv | semantischer Anteil laut ARGON-Befund 0 (in dieser Runde nicht neu geprüft) | P2: CRE1-Shadow-Messung (E1.5) |
| `MemoryEcology.kt` | nur `MemoryStore`, `InMemoryMemoryStore`, `ModelCapability`, Reputation (1–2 Dateien) | — | **größtenteils ungenutzt** | `MemoryGraph`, `ClaimGraph`, `MemoryRetriever`, `ModelRouter`, `RoutingPlan`, `ExperienceLookup` u. a.: 0 Aufrufer außerhalb der Datei | nicht ausbauen; Aufräumen nur mit Besitzerentscheidung |
| `ProofCarryingMemory.kt` | `SourceIdentity`/`SourceTrustProfile`/`SourceObservation` (1–2) | — | **teilweise ungenutzt** | `ProofCarryingMemoryMutation`, `MemoryMutationLedger`, `MemoryMutationPayload`: 0 Aufrufer | kein zweites Gedächtnis daraus bauen |
| `AuthorizedConversationMemoryShadow` / `MemoryResearchShadowComparator` | 1 / 1 | 1 / 2 | SHADOW | — | — |
| `BoundedAgentOrchestrator` | 1 | 7 | produktiv | — | — |
| `ToolGate` | 12 | 13 | produktiv, Quittungen | — | — |
| `DeviceTeamleader` / `…TeamleaderScheduler` | 2 / 1 | 1 / 1 | produktiv (JobScheduler) | — | — |
| `ContinuumAutonomousScheduler` | 3 | 1 | produktiv | — | — |
| `VirtualModelFabric.kt` | Exporter 1 | — | Planung, nicht ausführbar | F2 ohne Hardwarebeweis | P3, bleibt aus |
| `DeepResearchEngine` / `WebLearningInbox` / `ApprovedWebResearchToolRunner` | 2 / 3 / 1 | 4 / 1 / 2 | produktiv mit Freigabe (#238) | #239 würde Schutz entfernen | #239 nicht mergen |
| `ContinuumKnowledgeArchive` | 6 | 5 | produktiv | — | — |
| `ExperienceCompiler` | 1 | 3 | vorhanden | — | — |
| CCC1 `repair-loop` / `repair-ledger` / Worker / Teamleader | Node | viele | produktiv auf Termux/PC | Erfahrung bringt auf neuen Formen nichts (M3) | Ranking unverändert lassen |
| #243 Gegenbeispiel-Labor + Gates | Node | 20 → 34 | Forschung | 2 Fehler (FIFO-Hänger, Orakel) | **in #244 behoben** |
| Kotlin-Harness | Skript | — | Host-QA | 2 Host-Artefakte als rot | **in #244 robust + Diagnose** |

## Doppelstrukturen und Risiken

- **#239 gegen #238:** Beide implementieren Web-Lernstoff. #239 entfernt neuere Quittungs- und Ledger-Härtungen und darf nicht als Ganzes gemergt werden (Befund #242 bestätigt).
- **F1-Kern gegen `LocalConversationContext`:** kein Doppelspeicher. Der Kern ist ephemer, begrenzt (12 Entitäten, Neustart nach 64 Zügen) und schreibt nichts.
- **Ungenutzter Code:** Ein großer Teil von `MemoryEcology.kt`/`ProofCarryingMemory.kt` (Graph-, Claim-, Router- und Mutations-Klassen) hat keinen Aufrufer im Hauptcode. Das echte Gedächtnis ist `ContinuumLearningCore` + EWM2 + `HybridMemoryRetriever`. Diese Klassen dürfen nicht als zweites Gedächtnis reaktiviert werden. Ob sie gelöscht werden, entscheidet der Besitzer; in #244 wurde nichts gelöscht.
- **Kein zweites Gehirn, kein zweiter Worker, keine zweite Lerndatenbank** in #242/#243/#244.

## Priorisierte Integrationsreihenfolge

1. **P0:** Fold7-Abnahme (M6-Pfad) mit dem unsignierten QA-Lauf auf exakt diesem Commit. Danach Besitzerentscheidung über EWM2 ON.
2. **P1:** die Flag-Rate der Aussagenprüfung (neu) und die Router-Prognose gegen Besitzer-Feedback auf dem Fold7 auswerten, beide nur lesend.
3. **P1:** den F1-Gesprächszustand in den Prompt nehmen, erst nach P0 und mit A/B gegen OFF.
4. **P2:** CRE1-Semantik im Abruf (E1.5) und Begründungsgedächtnis nur messen.
5. **P3:** F2 (GPU, Expertenstreaming, Großmodelle) bleibt ohne Hardwarebeweis aus.
