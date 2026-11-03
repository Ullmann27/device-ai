# Breakthrough research handoff — 2026-11-03

Baseline: `64ec06a73f50cae9ee49035c840710d87777d50f`; branch `codex/breakthrough-research-20261103`; base `codex/team-chief/omega-20261103`.

EXP-06/R1 is preregistered, implementation/tests NOT_RUN. Existing path: AgentActivity.runResearch → ApprovedWebResearchToolRunner → DeepResearchEngine → claim graph → ResearchPromptBoundary → local synthesis. Permission/session/network gates remain unchanged. There is no new scheduler or memory store.

Concrete inspection found that extractClaims trusts extractor sourceReferences and provenance, and the prompt appends raw graph text/IDs while sanitizing surrounding evidence. Chief approved exact production ownership of ResearchEngine.kt plus a narrow admission helper. Research is extractive only: arbitrary paraphrases require a future entailment contract and are not silently admitted.

Pre-registration: `docs/evidence/breakthrough-research-20261103/preregistration.json`. Source manifests, baseline/candidate raw logs and independent QA receipts will be added. No paid API, owner data, weights, release/signing/install work. Quota NOT_VISIBLE; model/device tests NOT_RUN.

Rollback: revert this isolated R1 implementation commit; no format migration or state mutation. Next: freeze synthetic development fixtures, execute baseline, implement and rerun; freeze source SHA before independent QA.
