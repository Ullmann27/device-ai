# Gehirn-Runde 17.11.2026 – Gedächtnis, Kontext, Denken (Grok Chef-Architekt)

Auftrag Heinz 17.11.2026 (t154u): „mein Gehirn … extrem ausarbeiten“. Branch `code52/grok-brain-architect-20261117`,
Basis `integrate/continuum-command03-android-20261115` @ `0b8600d1f38017e05266eb8d2365b6047424746d`
(= Release-Tag `continuum-einfach-code62-44035893856` @ `00b3c8b8` + zwei Doku-Commits). Kein Merge, kein Force-Push,
keine fremden Branches berührt, Paketname unverändert, keine Cloud-/Bezahldienste, Internet-Steuerung unverändert.

## Ist-Zustand vorher (Datei:Zeile auf der Basis)

| Befund | Beleg |
|---|---|
| Produktiver Kontextpfad: `AgentActivity` → `AiContextCompiler(maxBytes=13_500, memoryLimit=8)` | `AgentActivity.kt:253` |
| Agent wählt Erinnerungen per reinem Top-8-Ranking | `cognitive/BoundedAgent.kt:383` |
| `RELEVANT MEMORY` wurde als EIN Block gerendert: passt er nicht ins Budget, verschwindet **jede** Erinnerung | `cognitive/AiContextCompiler.kt` (Abschnitt „Keep the selected group atomic“, Basis ~Z. 196–237) |
| Gleiche Aussage mehrfach gespeichert belegt mehrere der 8 Plätze | `cognitive/HybridMemoryRetriever.kt:83–170` (kein Duplikat-Schritt) |
| Veraltete/korrigierte Fakten sehen im Prompt aus wie aktuelle | kein Zeitleisten-Schritt im Kontextpfad |
| M8-Teile fehlten auf der Code62-Linie: `MemoryTimeline`, `MemoryQuestionResolver`, `JustifiedRelationStore`, `LocalModelClaimAudit` | `docs/handover/GROK-LEGACY-INTEGRATION-20261110.md` Z. 126–128 (Branch `code52/grok-bot01-legacy-integration-20261110`), Quelle PR #264 @ `e022249f` |

## Was jetzt neu ist

1. **Zeitleiste (M8 MemoryTimeline, portiert + erweitert)** – `cognitive/MemoryTimeline.kt`.
   `linksAmong(focus, facts, recordedAt)` vergleicht nur Paare mit einem ausgewählten Datensatz (einmal parsen).
   Neue Regel ohne Raten: eine **später** gespeicherte „jetzt/seit/inzwischen“-Aussage ersetzt eine unmarkierte
   („Ich wohne in Wien.“ → „Ich wohne jetzt in Graz.“). Umgekehrte Reihenfolge bleibt ein Widerspruch.
2. **Beziehungsfragen (M8 MemoryQuestionResolver + JustifiedRelationStore)** – „Welchen Beruf hat der Bruder von Ute?“
   wird zusätzlich als „Welchen Beruf hat Kurt?“ gesucht, wenn die aktiven Fakten genau eine Person ergeben.
   Nur die Suche ändert sich; die abgeleitete Beziehung wird nie gespeichert oder als Fakt bestätigt.
3. **MemoryContextPlanner** – `cognitive/MemoryContextPlanner.kt`: Konsolidierung gleicher Aussagen (Verneinung
   bleibt Teil des Wortlauts), Nachziehen des aktuellen Nachfolgers bzw. Widerspruchspartners, atomare Gruppen
   (Zeitleisten-Links + gleiche Namen/Entitäts-Tags). Kein Modell, kein Schreiben.
4. **Budgetbewusstes Packen** – `AiContextCompiler` packt ganze Gruppen nach Rang; eine zu große Gruppe wird
   komplett weggelassen, die übrigen Erinnerungen kommen trotzdem an. Marker im Prompt: `current=true supersedes=…`,
   `current=false superseded_by=…`, `conflicts_with=… (ask back, do not choose)`, `same_statement_also_in=…`.
   Der Recall-Observer meldet genau die gesendeten Datensätze.
5. **BoundedAgent** wählt über denselben Planer, damit Modell und Compiler den Nachfolger/Partner überhaupt sehen.
6. **Antwort-Prüfung (M8 LocalModelClaimAudit)** – nach jeder erfolgreichen lokalen Antwort mit Gedächtnisbeleg
   eine Metadaten-Trace-Stufe `CLAIM_AUDIT_SUPPORTED/UNSUPPORTED/CONFLICT/SKIPPED` (`AgentActivity.kt` vor
   `publishCognitiveTrace`). Antwort, Prompt, Werkzeuge und Gedächtnis bleiben unverändert.

## Unverändert eingehalten
- STALE/CONTRADICTED bleiben aus dem Antwortkontext; widersprochene gehen weiter in `UNRESOLVED CONTRADICTIONS`.
- Privatsphäre wird vor jedem Textzugriff gefiltert; ein verbotener Datensatz wird nie als Nachfolger nachgezogen
  (Test `aForbiddenRecordIsNeverPulledInAsSuccessorOrPartner`).
- Bestehender Test `AiContextIntegrityTest.overflowOmitsTheWholeSelectedMemoryGroupIncludingItsIds` bleibt grün
  (Korrektur ohne parsbares Subjekt reist über den gleichen Namen mit).

## Tests
Neu: `MemoryContextPlannerTest` (9), `MemoryContextPackingTest` (6), `MemoryTimelineRecordedAtTest` (3),
`BoundedAgentMemoryPlanTest` (2); portiert: `MemoryTimelineTest` (5), `LocalModelClaimAuditTest` (11).

## Offen (nächste Gehirn-Runde)
- Owner-Fakten-Pfad `learning/ContinuumTemporalMemory` (Ctx3/RecallHits) nutzt den Planer noch nicht.
- Claim-Audit ist Schatten-Signal; Auswertung erst zusammen mit Besitzer-Feedback auf dieselbe Trace.
- `ContinuumJarvisPresenceEngine`, `JarvisInitiativePolicy`, `JarvisSessionHandoff`, `ContinuumSelfInspectionQuery`,
  `NeuralTutorBatchHint` (M8/#273) sind weiterhin nicht portiert.
- Fold7-Gerätetest: NOT RUN.
