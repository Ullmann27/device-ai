# Existing experience recall: equal-relevance recency

Owner: current root integrator. Branch: research/continuum-learning-recall-20261110.
Frozen baseline: local515afc28af11b9befeb5a61f9c44411d423635a9,
remote28bee009ef54e8c60eb1468dc90c9f4a5a51edea,
Git tree735a663d522e16d56e979c78a908e9f3baa2f031.
This follow-up does not contain or promote the rejected OMEGA working-context prototype.

## Bounded defect and planned change

The ordinary chat enables existing experience recall (`AgentActivity`,
`recallPriorExperiences = botTask == null && codingPlanning == null`).
`BoundedAgentOrchestrator` calls `AgentExperienceRecall.hints`; that asks the
existing `InMemoryExperienceStore` for64 similar experiences, then applies
age/outcome/evidence filters. Equal lexical matches currently sort by ID.
Thus64 older equal matches can exclude a current success or the latest failure
warning before these filters run.

One planned production change only: sort by relevance descending, completion
instant descending, then ID ascending. Keep all existing filters and limits.
No lookup-contract change, memory migration, new database, event bus, model call,
model weight change, tool grant or autonomous skill promotion.

## Frozen checks

Initial probe:3tests,2fail. Expanded paired batch:28tests,3fail on unchanged
baseline, including9 new recency/negative controls and19 existing store/recall
regressions. The initial3 overlap the expanded tests and are not additive.
Expect both demonstrated starvation cases fixed, exact ties deterministic,
relevance still primary, caps unchanged and invalid/future outcomes excluded.

Preserved negative control:64 expired records with HIGHER lexical relevance can
still hide a lower-relevance current record. Recency only breaks equal scores;
this patch must not claim general eligibility-before-cap retrieval. A dense
batch of future-dated records can likewise consume candidate slots before the
existing future-data rejection. These broader lookup-policy problems are outside
this single fix.

## Validation boundary

The host script recompiles actual MemoryEcology, AgentExperienceRecall and
CognitiveCore sources plus tests against a frozen Android-class dependency
snapshot. Hashes and raw results are in `docs/evidence/learning-recall-recency-20261110`.
It is not a full Android build, model-quality test or Fold7 proof.

Use existing toolchain paths in KOTLINC, CONTINUUM_COMPILED_CLASSES,
CONTINUUM_JSON_JAR and CONTINUUM_JUNIT_JARS, then:

```bash
bash scripts/research/run-experience-recall-recency.sh /tmp/learning-recall
```

Current checkpoint: RED tests committed; production fix NOT_IMPLEMENTED.
Full Android and independent evaluation are root-owned follow-ups.
Fold7/model/thermal/energy/autonomous learned-skill application: NOT_RUN.
Rollback: revert only the comparator change; no data migration exists.
