# Brain lab handoff — controlled negative result retained

Status: **RESEARCH_SHADOW / publication pending verification**.
Branch: `research/brain-lab-controlled-20261110`.
Base: `4ac9c9b94b9fb5e391646e2e99e986db32ec7997`.
Integration owner: Cinema root integrator; target:
`cinema/fluid-light-stage-20261110`. Protected #706 and main unchanged.

Read `experimental/brain-lab-20261110/RESULTS.md` and `PROTOCOL.md`.
Protocol frozen at `e2ddc53`; measured code at
`ff01682a952792cad83b09468e12d49252819d04`. Raw report pins source hashes and
clean-tree status. No model call, app import, training or owner data used.

- 43,200 exact numeric outputs, three arms, four workloads, six paired processes.
- 69/69 Node checks; unchanged Kotlin batch 17/17 JUnit plus 768 prior cases,
  two 96-case diagnostic sets and 26 controls. Sequential ablation remains RED.
- Adaptive admission passes mixed holdout but fails epoch-churn CPU overhead
  gate (+9.4%, maximum allowed +5%): **REJECT_PROMOTION**.
- Event accumulator yields 2 false wakes versus 0 for threshold-only; keep
  advisory lab only. No biological/AGI claim.
- Existing #649 broker/oracle, #648 diagnostic/overlay and #287 logical routing
  are reused. No second controller, memory DB, scheduler or repair pipeline.

PORTIERT: isolated lab sources and repeatable protocol only.
VERIFIZIERT: specified host checks only; all samples and failures retained.
GERÄTEBESTÄTIGT: NOT_RUN. No release/signing/installation requested.

Canonical fast/slow API is `CognitiveBudgetController.decide` →
`CognitiveBudgetResearchRunner.run` in cognitive_budget's separate work.
Canonical memory API is `ContinuumMemorySkillResearch.review` in memory_skills.
Their receipt adapters are NOT_RUN here and their results are not added to this
lab's counts. Hidden-state Gemma recurrence and learned MoE need architecture
support/training; prompt loops do not supply it.

Publication history: initial automatic review rejected the research push for
missing public-disclosure authorization. The owner then explicitly authorized
immediate GitHub publication of all assigned development/research branches.
The normal push retry was allowed but failed because Git CLI credentials were
unavailable (`could not read Username`). The authorized GitHub connector is
now used on this same official repository and isolated branch; no main/release
merge, signing or private data disclosure is included. Remote SHA will differ
from local hashes because the connector uses the canonical equivalent-tree
parent. Compare full Git tree and recorded source hashes, not SHA spelling.

Next: verify the authorized connector publication; the prepared draft body is
`experimental/brain-lab-20261110/DRAFT-PR.md`. Retain this negative result on the
one canonical integration line as research evidence; do not activate the
candidate. Future real-workload evaluation requires new protocol and independent
holdouts. Rollback: remove lab directory and this handoff; no app state changes.
