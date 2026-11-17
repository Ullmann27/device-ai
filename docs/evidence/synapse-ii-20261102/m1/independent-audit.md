# Synapse II M1 independent read-only audit

Baseline: PR342 `7327367851df147fef6226233fc86a22efcfe2fc`, 2026-11-02. No shared app edits, branch mutations, owner-state changes, Android builds, full-host checks or additional agents. Synthetic dialogue files only under this scratch directory.

## Confirmed actual M1 defect

Confirmed owner records in chronological order:
1. `Anna wohnt in Graz.` at 1.
2. `Anna ist umgezogen.` at 2.
Question: `Wo wohnt Anna?`

`OwnerMoveRed.kt` compiles and invokes actual current `ContinuumMemoryDialogue.rank` and `reply`. Actual result: rank retains old residence, `grounded=true`, answer displays `Anna wohnt in Graz. (Bestätigte Nutzererinnerung)`. The check that old memory ID must be absent fails (exit 1), saved in `owner-move-red.txt`.

Cause: `OwnerAttributeEvidenceScope.select` LOCATION narrows heads to wohnen/leben verbs before selecting latest evidence. The move assertion's `ist` is dropped, allowing old positive residence to win. PR342's existing `CertifiedLocationReflex.answerFromConfirmedFacts` correctly returns null for this pair, but that only removes an optional exact prefix; it does not remove the stale retrieved quotation. Thus this is not a redo of PR342 helper protection.

Actual Android chain: `AgentActivity.runContinuumDialogue` -> `CognitiveDecisionBridge(ContinuumCognitiveRuntime(...), null).respond` -> `ContinuumCognitiveRuntime` -> `ContinuumLearningCore.replyWithEvidenceInConversation` -> `ContinuumMemoryDialogue.replyInConversation` / `rank` -> `OwnerAttributeEvidenceScope.select`. `ContinuumLearningCore.brainAnswerView` uses the same scope; `ContinuumBrain` checks it before accepting EWM answer IDs. Repairing the shared scope addresses both active memory gates.

Bounded minimal repair recommendation: retain explicitly named location move/retraction statements as invalidators before head/verb narrowing. Compare invalidator ledger time to the existing eligible assertion chronology; later/equal invalidator blocks old positive, a newer explicit assertion may restore it. Preserve existing owner provenance/status filters and revoked tombstones. Do not treat the invalidator as an answer or new fact. Do not assign an unnamed pronoun to a person. Test new move, named revocation, unrelated-person move, later reassertion, reordered records, revoked invalidator and explicit nonowner source controls. Assert rank IDs and grounded reply, not only helper-prefix null.

## Additional observed boundaries (not the primary repair recommendation)

Exact current-source helper probe (`ReviewProbe.kt`, `probe-baseline.txt`): session history `Anna wohnt in Graz.` then `Das stimmt nicht.` or `Sie zog nach Wien.` returns Proven Graz. Those unscoped invalidators are omitted before verifier because the current bounded unscopedCorrection expression lacks them. `Sie ist umgezogen.` and `Nein.` correctly Withheld; benign `Ich trinke Kaffee.` correctly preserves Proven Graz. A newer owner record `Anna wohnte früher in Wien.` is filtered by tense and also leaves old positive selected. These are separate bounded grammar/temporal follow-ups, not proof of generalized natural-language understanding.

## Evidence provenance and reproduction

Focused compiler: `/workspace/post-fable-toolchain/bin/kotlinc` (JDK21 wrapper). Sources compiled freshly: ContinuumMemoryDialogue, OwnerAttributeEvidenceScope, CertifiedLocationReflex, EvidenceBoundNaturalLocationProbe (includes verifier), EntityRelationLocationShadowAdapter, VerifiedLocationSpanResearch (includes snapshot), ContinuumResidualSpanCore. Remaining dependencies: existing `/workspace/continuum-checks/post-fable-age-full/main` with friend-path for internal dependencies. `git diff --name-only b06889f5..73273678 -- app/src/main/java` contains only AgentActivity, CertifiedLocationReflex, LocalConversationContext, LocalGenerationDeadlinePolicy; the actual changed reflex is freshly compiled. No stale cached PR335 reflex is used.

Runtime command for RED:
`/workspace/post-fable-toolchain/jdk21/bin/java -cp '/workspace/continuum-checks/synapse-m1-review/dialogue-classes:/workspace/continuum-checks/post-fable-age-full/main:/workspace/continuum-toolchain/*' at.heinz.deviceai.learning.OwnerMoveRedKt`

Baseline SHA256:
- CertifiedLocationReflex.kt `49806d82cd23e4753b2cb014b779e3743e0c48e99e9cd7af09db13302eb06bd3`
- OwnerAttributeEvidenceScope.kt `15366b8c4223e6809c3d65bd12313303f1908bfe8429adc52a23ce4cf1a870de`
- ContinuumMemoryDialogue.kt `3c05f03e1e62fac41f240636ff7de0fabebeff1de23e0ad4cb799d2d453b0953`

An initial partial compile failed on internal cached dependencies and cross-module smart cast. This was harness setup failure, not behavioral RED; corrected via friend-path and actual Residual core source before running the successful compile and failing behavior assertion.

## LCT1 / WSC1 / TTT1 actual state

All classes are in `app/src/test/java/at/heinz/deviceai/intelligence/maxultra`, not main Android source. `rg` finds no production Lct1/Wsc1/Ttt1 caller.
- `Lct1Reasoning.kt`: `Lct1.Reasoner`, `Verifier`, `VerifiedReasoner`, typed source-sealed facts, validity dates, maximum 3 hops. Invoked by Lct1Experiment/ReasoningTest and Wsc1 test components.
- `Wsc1Consolidation.kt`: `Wsc1.Snapshot` constructs Lct1 verifier and verified reasoner; `Habit` binds name/fact group/source versions and validity window; `Consolidated` uses it. Called by Wsc1Experiment/ConsolidationTest.
- `Ttt1WorkingMemory.kt`: `Ttt1.EntityStore` bounded 16-entity session RAM, `BoundedContext`, `StoreWithFastWeights`. Input turns already supply analyzed mentions/pronoun gender/predicate. Called by Ttt1Experiment/WorkingMemoryTest; uses Lct1.FastWeightGuide in research variant. It is not a parser for actual owner dialogue.

Reuse their principles (explicit identity, removal on unresolved revisions, dependency checks) in existing main gates; no justified direct production integration from this audit. Historical test counts in docs are not rerun evidence or device results.
