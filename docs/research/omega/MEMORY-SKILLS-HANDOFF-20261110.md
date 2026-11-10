# Memory/skill research — role B

Status: IMPLEMENTED / HOST VERIFIED. Local base: `4ac9c9b94b9fb5e391646e2e99e986db32ec7997`.
Canonical remote base: `ae3ce34b5aff285ed8c569ca2844975e34131a86` (same Git tree).
Integration owner: current Cinema integrator. Target: `cinema/fluid-light-stage-20261110` / PR #715.
Research branch: `research/continuum-memory-skills-20261110`.

## Inventory before implementation

| Existing path | Capability and boundary |
| --- | --- |
| `cognitive/MemoryEcology.kt` | WORKING/EPISODIC/SEMANTIC/PROCEDURAL records, privacy, immutable snapshots, lexical retrieval and graph; no new database needed. |
| `cognitive/LocalConversationContext.kt`, `learning/Ctx3*` | Working conversation/context research; owned by separate context work. |
| `learning/ContinuumMemoryLayers.kt` | Existing LEX2 profile, chronological journal and read-only shared facts. |
| `cognitive/HybridMemoryRetriever.kt` | Existing lexical/semantic retrieval. |
| `cognitive/ExperienceCompiler.kt` | Repeated task/tool sequences become candidates; distinct IDs, score/rate thresholds, destructive-tool veto and mandatory owner approval. |
| `cognitive/VerifiedSkillReview.kt` | Opt-in independent verifier, default deny, conflict/replay checks. |
| `cognitive/ProvenancePreservingConsolidation.kt` | Navigation summaries resolve original evidence; summary itself cannot prove a claim. |
| `cognitive/ProofCarryingMemory.kt` | Signed mutation ledger with provenance and contradiction references. |
| `learning/ContinuumSkillLibrary.kt` | DRAFT/APPROVED/TAMPERED text skills; explicit named approval, content binding; no execution. |
| `learning/TinyContextDecoder.kt` | Byte-level surprisal research; not a verified episode priority gate. |

History inventory read: PR catalog contains #143 (read-only skill review), #207 (evidence-bound transfer), #241 (verified restart transfer). These are existing work, not new inventions. Current open PRs #715/#716 and historic #287 were read; no claim of exhaustive line-by-line historical audit. Remote #715 at inspection was `11f756e6006936450c4dba06f44ac3f06a5f8619`; local authorized base includes later Cinema work. No protected #706 or main edits.

## Implemented bounded milestone

Read-only `cognitive/research/ContinuumMemorySkillResearch.kt` adapts existing `Experience` and `VerifiedSkillReview`. Explicit owner, memory/model/policy/source revisions, consent and external verification precede salience. SHA-256 binds all Experience fields with length-delimited encoding. These metadata are supplied by trusted application code; a digest, consent boolean or source label is not authentication. The independent verifier defaults to deny.

Surprise is the absolute residual of observed binary success versus `Experience.confidenceBefore`, not token surprisal. It ranks bounded recall (default 8, maximum 64); compilation sees all eligible episodes, including negative experience, so surprise filtering cannot inflate success rates. Current-scope unresolved negative evidence vetoes its task/tool family. Replay/conflict and reused-receipt checks fail closed. Default input cap is 256, maximum 4096, with a million-character total cap and bounded metadata collections. Output snapshots are immutable.

Output is existing owner-review candidate metadata, never executable instructions, tool authority, persisted skill or memory deletion. There is no AgentActivity/controller/contextlab/UI change. The real call path exercised here is host harness → adapter → existing VerifiedSkillReview → existing ExperienceCompiler → existing ToolRegistry. Agent A's MEMORY route remains scheduling classification only; it does not authorize this adapter. No second store, conversation core or skill library was created.

## Reproduction and measured results

```sh
bash scripts/research/run-continuum-memory-skills.sh /tmp/memory-skill-check
```

The runner defaults to the already installed `/tmp/continuum-toolchain`; environment overrides are in the script. No dependency install, Gradle, cloud inference or training occurs. It compiles actual transitive project sources with Kotlin 2.4.0, JDK 21 and JUnit 4.13.2; the Android API jar supplies types, not an Android runtime. No substituted project API classes are used. Initial runner setup lacked HybridMemoryRetriever; the source graph was corrected before the successful runs.

**44/44 host JUnit methods PASS**, including 20 new tests and 24 existing skill-review, memory-ecology and immutable-snapshot regressions. Checks cover default deny, every revision boundary, consent/privacy, stale/contradicted/model evidence, tampered fields, receipt replay, negative denominator, unresolved-negative veto, failure threshold, destructive sequence, size limits and caller mutation. The unresolved-negative test demonstrates that existing VerifiedSkillReview alone omits an unverified failure while this adapter blocks its family. This is a conservative research wrapper, not a change to existing production behavior.

Four matched **deterministic synthetic** arms use frozen training and 12 authored holdouts (8 positive, 4 abstention controls). Clean fixture: 56 episodes. Poisoned fixture: same 56 plus 4 false success labels. All four arms receive the same task inputs per condition. Holdout IDs and request strings are disjoint; no holdout enters consolidation. Independent verification is a fixture oracle over training IDs, **not real external execution evidence**. Task categories are supplied, so this does not test language understanding. Holdouts and tests were authored by the implementer, not independently held out by a third party.

| Fixture / arm | Positive correct | Abstention controls | Exposed selection text |
| --- | --- | --- | --- |
| Clean / none | 0/8 | 4/4 | 0 characters |
| Clean / raw | 8/8 | 4/4 | 1966 characters |
| Clean / episodic | 8/8 | 4/4 | 984 characters |
| Clean / procedural candidate | 8/8 | 4/4 | 186 characters |
| Poisoned / raw | 0/8 | 3/4 | 3027 characters |
| Poisoned / episodic | 8/8 | 4/4 | 984 characters |
| Poisoned / procedural candidate | 8/8 | 4/4 | 186 characters |

There are 192 case decisions (12 × 4 × 2 fixtures × 2 surprise settings). Surprise-on selects 16 episode IDs versus 56 with surprise-off; selection accuracy and candidate denominators are identical. **No measured accuracy benefit from surprise**, and no latency, whole-prompt token, power or device benefit is claimed. Text counts exclude retained provenance. Raw versus validated arms differ in validation as well as representation; poisoning results cannot be attributed to consolidation alone. Procedural output is tool-name metadata, not executing a newly learned capability.

The final benchmark ran twice byte-identically. The final exact-source run on `8037215` and source hashes are under `docs/evidence/memory-skills-20261110`. Full Android Gradle, live local Gemma, Fold7 and independent external holdout: NOT_RUN.

## Integration decision

Accept only as opt-in research candidate: source-bound host regression gate passed and no memory/tool authority is granted. The Cinema integrator can port these owned files after exact-tree review. Promotion to a real owner-approved capability remains blocked on trusted receipt integration, separately authorized real-model trials, independent holdout and device checks. Do not change the existing production skill review call path from these synthetic results.

PORTIERT into canonical Cinema: no. VERIFIZIERT: host scope only. GERÄTEBESTÄTIGT: no.
Rollback: remove only these new opt-in research files; no existing store modified.
