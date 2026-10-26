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
