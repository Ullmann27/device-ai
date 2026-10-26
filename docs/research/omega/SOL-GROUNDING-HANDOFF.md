# Sol grounding eligibility continuation — 2026-10-26

Status: WIP / UNVERIFIED. Official repository branch `sol/grounding-relations-20261026`, isolated worktree `/workspace/device-ai-memory-20261026`, starts at `c391b83244bd2cc945a1b37bcdd829da27e15ba6`. Integration target: `sol/continuum-brain-breakthroughs-20261026`; PR150 base `ad14f8b82f9be2cd0026bc16eef71a16bec41424` remains preserved.

## Bounded task and connections

Read `AGENTS.md`, the current experiment contract/register, Issue41, `SOL-BRAIN-HANDOFF.md`, current Node retrieval and Kotlin dialogue sources before code changes. Scope: mirrored `.github/continuum/dialogue-retrieval.mjs`, `ContinuumMemoryDialogue.kt`, related tests, and research evidence/handoff. Root owns main-dialogue/Gemma routing; no AgentActivity/localmodel edits.

Affected experiments: EXP-04 (retain CRE1 immutable admitted candidate pool), EXP-06 (source provenance/eligibility), EXP-07 (independent negative/holdout evidence). No new memory architecture, schema migration, weight training or promotion. CTX3/LM1 failed benchmark paths remain inactive. Existing facts, weights, revocation and conversation context contracts must remain intact.

## Current evidence and intended next step

Known open cases are multiple explicit identifier mismatches and unsupported ordinary subjects/relations: `Wo wohnen ROBOT-36 und MOTOR-52?` must not admit `ROBOT-36 und MOTOR-53 wohnen in Graz.`; `Wo wohnt Nemo?` must not admit `Leo wohnt in Graz.`; `Wo arbeitet Leo?` must not admit `Leo wohnt in Graz.`. Existing single-identifier fix is already included in the base; preserve its independent 74/96 positive recall and all no-ID golden scores where applicable.

Next: reproduce failures with RED tests, read existing LCT1/CSX proof/relation sources and their boundaries, choose the smallest supported eligibility rule, mirror exact Node/Kotlin behavior, run focused and broader regressions. Freeze a candidate before requesting independent newly authored benchmark cases from parent; do not tune against those heldouts.

## Tests / limits

At initial checkpoint: code changes NONE; tests NOT_RUN; Gradle/device/APK tests NOT_RUN. Parent will integrate and run heavy Gradle verification. Host standalone Kotlin checks may use actual precompiled classes with JDK17/kotlinc from `/workspace/continuum-toolchain`; distinguish host checks from Android/device validation.

Rollback: revert individual new source commits. No main merges, APK signing/installation, owner data writes, failed-path activation or foreign branch changes.

## RED reproduction checkpoint

Baseline source `a7aebe1ecab4fe7a63470fa004a65c8184454027` passes the existing focused retrieval baseline (25/25 Node). The new shared 30-case fixture executes both owner/source policy arms: Node42/60 PASS,18/60 FAIL as expected. Both new Kotlin behavioral tests fail on the known grounding errors; all17 existing identifier/dialogue tests pass (19run,2fail). Raw logs are under `docs/evidence/sol-grounding-20261026/`.

Root cause: the original-text ID helper explicitly bypasses every query with more than one distinct ID. Owner admission then requires only the best shared content word; a generic predicate or a person mentioned as a value can qualify. The old owner-subject guard applies only to `Was/Wer ist` at dialogue level, not to `Wo wohnt/lebt/arbeitet`.

Reuse analysis: LCT1 is test-source-only, requiring typed facts and exact subject+relation per verified hop; it cannot safely be activated on unstructured owner prose. `ContinuumEvidenceSpanProbe` (CSX1) already uses anchored subject/relation templates, bounded robot IDs and original literal spans. CSX3 remains a confidence-uncalibrated extractive proposal. The candidate will apply only the narrow subject/relation eligibility principle in the existing mirrored retriever, without importing a new reasoner, learned semantics, evidence authority or memory store.

Tests remain RED / implementation NOT_STARTED at this checkpoint. Added positive short-ID cases explicitly include a shared ordinary content word: the task does not silently broaden lexical recall for cases with no original lexical evidence.

## Candidate implementation / host validation in progress

The mirrored original-text gate now requires every explicit queried ID within the same candidate. A narrow anchored location-question check requires exact requested subjects under the requested residence/work relation, keeping existing direct, inverted, home and moved-to forms. It rejects a subject present only as an object, a known wrong relation, negated location and a profession-only work statement. Other question forms and resolved follow-ups retain their existing policy. No new reasoning runtime or evidence authority is activated; TF-IDF and corpus IDF are unchanged.

Node focused91/91 and broader16/16 PASS. Previously consumed regression fixtures retain74/74 previously retrieved ID positives (absolute74/96),8/8 no-ID golden objects/scores unchanged, and136/136 TRAIN,46/46 DEV,62/62 TEST previously retrieved relational true positives. Initial Kotlin19/19 PASS; an additional real NBSP parity regression exposed JVM default-whitespace behavior, now mirrored explicitly with Node. Expanded5-class Kotlin revalidation is RUNNING, full Gradle/device checks NOT_RUN. Candidate not frozen yet; independent new parent-authored benchmark remains next.

## Frozen candidate / independent evaluation requested

Source candidate `4bf74b1b827aab56bbe03e032c21c03c508484c8` is pushed in Draft PR153: https://github.com/Ullmann27/device-ai/pull/153 . The complete actual5-class Kotlin run finished42/42 PASS (exit0), including conversation identity/revocation and runtime CRE1 pool integrity. All33 shared fixture cases execute under both trust classes. Source/fixture SHA256 identities are in `candidate-freeze.json`.

Parent was asked to commission a newly authored benchmark AFTER this freeze against baseline `c391b83244bd2cc945a1b37bcdd829da27e15ba6`. No tuning after that request. The whole Node project suite is RUNNING with concurrency2; the raw final result will be archived separately. Root still owns full new Android Gradle integration and routing. Candidate source changes remain limited to the2 mirrored retrieval files.

## Shared-host resource coordination

Parent requested stopping heavy Node work while the native three-arm Gemma/Qwen matrix occupies the2-core host. The whole Node run was stopped immediately, including its child test processes, and its partial output is retained as `node-full-interrupted.tap`. Status: INTERRUPTED_RESOURCE_COORDINATION, exit1, no whole-suite success claim; full integrated Node validation is deferred to parent. No reported `not ok` occurred in the retained partial log. No heavy process remains on this worktree. Source candidate remains unchanged/frozen at `4bf74b1b827aab56bbe03e032c21c03c508484c8`.
