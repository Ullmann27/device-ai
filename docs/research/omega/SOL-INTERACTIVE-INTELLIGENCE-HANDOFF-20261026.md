# Aktuelle Übergabe — experimentelle Intelligenz

**Maßgeblicher Abschluss:** [Ergebnisse, Grenzen, Prüfungen und Fortsetzung](SOL-INTERACTIVE-INTELLIGENCE-RESULT-20261026.md). PR174 enthält die komplette Forschung; PR175 (`08bfe94f21a9741a2144e9e4bae72b341fa68332`) nur den ausgewählten synthetischen Feedback-Lebenszyklus. Geprüfter Kandidat d7df7a45ea8002d8adaf2b45745b373416ae96dd. A v1 ist wegen4falscher adversarialer Bezüge abgelehnt. Kein neuraler Genauigkeitsgewinn, keine produktive Aktivierung. Finale Retention mit tatsächlich erforderlicher Prämisse72/84→84/84; frühere156/168-Summen zählen auch Fälle ohne Prämissenanforderung.

Native Prüfungen:15Labor+21bestehende+4Feedback-JUnitprüfungen PASS; Node1361PASS/1FAIL/8SKIP. Der FAIL ist die erwartete Freigabesperre des eingefrorenen v25-Release-Manifests. Kein vollständiger Android-/Gemma-/Gerätenachweis. Alle24Snapshots, Quellen und Gegenbeispiele sind veröffentlicht. Nächster Schritt: B-Review und neue unabhängige Sammlung für A, keine Nachoptimierung auf dem bereits geöffneten Holdout.

Nachfolgend das ursprüngliche chronologische Protokoll, dessen frühere Zwischenwerte durch den Abschlussbericht eingeordnet werden.

---

# Interactive intelligence laboratory — recoverable start

Status: START_CHECKPOINT / RESEARCH_SHADOW. Owner-requested branch `research/continuum-interactive-intelligence-lab-20261026`; audited starting SHA `b1d9556c74df9aa3c6e05defc9b4fb7d94181f17` (also current PR173 head). No release/source inventory edits or production activation.

Relevant experiments: EXP-04,05,06,12,13,14. Issue41 and current experiment contract read. Existing PR173 archive/follow-up correction is included, not replaced. PR172 grounding changes are a separate review and are not silently merged. Prior Sol/CCC1 hardening is ancestral.

Implementation choice: a bounded, opt-in Kotlin laboratory using the actual LocalConversationContext, CTX3, LearningExperience store, SWB1/SWB2/CRE1/CDX1 APIs. No second chat UI, memory database, tool gate or language model. Initial focus A/B: source-bound ephemeral reference/context selection and reversible feedback candidates. Tests use public synthetic conversations only. Phone Gemma success is owner-reported; no model weights or attached phone are available in this execution workspace.

## Pre-registration (before candidates)
- Train/development and held-out synthetic names, phrasings and seeds are separate; fixed seeds 73,101,211. Holdout metrics are opened only after candidate freeze. These are developer-designed tests, not independently authored blind human evaluation.
- A: exact reference decisions and clarification/abstention; original CTX3 is an existing OFF/SHADOW reference baseline. Separately measure actual current four-turn LocalConversationContext information retention. Context metrics are not Gemma answer accuracy. Raw, structured and adaptive packet sizes/latencies reported separately.
- B: classify question/model hypothesis/correction/confirmation/rejection/revocation; only explicit digest-bound review can admit a candidate in a separate research instance of the existing ledger. No owner facts or production weight writes. Compare untrained/trained small rankers on disjoint synthetic feedback.
- C: bounded information gain per question with explicit permission and cost; compare one oracle clarification with additional same-data updates in an intentionally ambiguous controlled task.
- D: typed causal interventions retain premises, hypothetical changes, consequences and unknowns separately. Exact finite independent test oracles; actual Gemma comparison remains NOT_RUN without weights/runtime.
- E: typed draft/critique/revision with separate oracle and strict budget. Synthetic drafts are labelled, never described as model inference.
- F: actual existing small encoder/decoder ablations, with exact checkpoint hashes; no enabling components solely because they exist.
- G: two additional candidates: evidence-removal sensitivity certificates and permutation-consensus rejection of order-sensitive retrieval.
- No threshold changes after holdout. No improved-cognition claim from loss/updates alone. Reject candidates with worse measured safety or no demonstrated gain. Full phone/model quality remains an open gate.

## Execution and next step
Initial native baseline and Kotlin runner next. All commands, raw outputs and source hashes go under `docs/evidence/interactive-intelligence-20261026`. SDK/Gradle/APK/device/LLM NOT_RUN. Commit/push each stable increment; final handoff will replace this start status.

## First native baseline (before candidate implementation)
Kotlin2.2.0 / Java21, actual current CTX3 and LocalConversationContext, 168 synthetic development scenarios: CTX3 exact reference/safe abstention 60/168; 156 abstentions, zero wrong non-abstaining references. Raw four-turn context retains required literal premise on156/168 (12long-dialogue losses). This measures a deliberately narrow registry/context task; it does not measure Gemma output accuracy. Baseline code and fixtures frozen in the next checkpoint. Seal includes168train/168dev/168holdout; holdout not evaluated before candidate freeze.

## A/B first native development result
Seven native regression checks pass (long retention, model-topic poisoning, explicit correction, session/revision isolation, aliases/bounds, durable review/revocation and eight-thread stale-review race). Structured and adaptive A arms168/168 on development; original raw context still156/168 premise retention. This is templated synthetic development, not a general language benchmark. No holdout opened yet. B uses a dedicated research instance of existing FileLearningExperienceStore with SYNTHETIC USER_FEEDBACK only; classification does not authorize storage/confirmation. No productive caller/weights/owner facts.

## Development experiments C–G (before final holdout)
All actual small-model calls ran natively: SWB1/SWB2/CRE1 before/after96updates on reviewed synthetic pairs, three seeds; CDX1 untrained generation. Development accuracy is unchanged by training; no checkpoint promotion. Synthetic corrections pass the existing ledger through digest-bound review, and derived checkpoint manifests bind the live active-training fingerprint. Revocation invalidates reuse; this is not weight unlearning. Multiple confirmed revisions are excluded until resolved.

C balanced ambiguity game: no question and400extra SWB1 updates each20/40, one truthful discriminating oracle answer40/40 (three seeds). D finite typed causal table16/16, declared pressure-only heuristic12/16; not model comparison. E synthetic claim critic removes60unsupported/wrong claims, keeps20/80, zero remaining wrong; generation NOT_RUN. G1 leave-one-out rejected: redundant evidence counterexample yields2/3. G2 order-consensus removes8wrong choices at cost of16abstentions/24 and2xrank calls. Mechanism outcomes do not prove natural-language reasoning.

Candidate freeze follows native regression and syntax checks. No held-out results consulted or thresholds revised. Existing source registry and ClaimGraph are required inputs; extracting them reliably from arbitrary real chat is still open. New routing adapter reuses actual archive veto and reports MODEL_NOT_CONFIGURED without pretending to infer.

## Frozen result — do not promote the dialogue candidate
Clean source d7df7a45ea8002d8adaf2b45745b373416ae96dd:15native research tests pass. Templated holdout: CTX3 60/168 correct decisions, candidate168/168; premise retention156→168. BUT separate8-case adversarial battery gives candidate3correct/4wrong/1abstention versus original CTX3 4correct/0wrong/8abstentions. **Dialogue candidate v1 is REJECTED FOR INTEGRATION.** Keep implementation/evidence for research; do not enable in AgentActivity. Failures: trailing negation, uncertainty, a clarification question misread as topic confirmation, topic revocation; quoted multi-mention correction also unresolved. No holdout-driven retuning in this round.

Neural holdout: SWB1 34/36 before AND after; SWB2/CRE1 36/36 before AND after; untrained CDX1 exact replies0/36. No learned-quality gain.24checkpoints exactly reproduce across development/frozen training. No productive promotion. FullNode1361PASS/1FAIL/8SKIP; failing v25 manifest correctly refuses new unattested sources. Pinned baseline v25control4PASS.21existing Kotlin tests pass. All raw data, synthetic checkpoints, source hashes and failed approaches saved in evidence directory. Next bounded step: separate source-review branch for B's safe synthetic feedback lifecycle only; no transfer of rejected dialogue/ranker/Gcandidates.
