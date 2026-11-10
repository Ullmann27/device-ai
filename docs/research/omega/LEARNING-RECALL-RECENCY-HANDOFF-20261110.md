# Existing experience recall: equal-relevance recency

Historical first-candidate handoff: REJECT_PROMOTION after independent future-data
regression. See `LEARNING-RECALL-WINDOW-REPAIR-20261110.md` for the one authorized
repair and current status. The original result below remains preserved.

Owner: current root integrator. Branch: research/continuum-learning-recall-20261110.
Frozen baseline: local 515afc28af11b9befeb5a61f9c44411d423635a9,
remote 28bee009ef54e8c60eb1468dc90c9f4a5a51edea,
Git tree 735a663d522e16d56e979c78a908e9f3baa2f031.
This follow-up does not contain or promote the rejected OMEGA working-context prototype.

## Bounded defect and change

The ordinary chat enables existing experience recall (`AgentActivity`,
`recallPriorExperiences = botTask == null && codingPlanning == null`).
`BoundedAgentOrchestrator` calls `AgentExperienceRecall.hints`; that asks the
existing `InMemoryExperienceStore` for 64 similar experiences, then applies
age/outcome/evidence filters. Equal lexical matches previously sorted by ID.
Thus 64 older equal matches could exclude a current success or the latest failure
warning before these filters run.

One production change only: sort by relevance descending, completion
instant descending, then ID ascending. Keep all existing filters and limits.
No lookup-contract change, memory migration, new database, event bus, model call,
model weight change, tool grant or autonomous skill promotion.

## Frozen checks

Initial probe: 3 tests, 2 failures. Expanded paired batch: 28 tests, 3 failures
on unchanged baseline; the same batch passes 28/28 after the comparator change.
It contains 9 new recency/negative controls and 19 existing store/recall
regressions. The initial 3 overlap the expanded tests and are not additive.
Both demonstrated starvation cases are fixed; exact ties remain deterministic,
relevance remains primary, caps remain unchanged and invalid/future outcomes
remain excluded from hints. This is a synthetic correctness result, not a
measurement of general intelligence or end-to-end user-task success.

Preserved negative control: 64 expired records with HIGHER lexical relevance can
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

RED checkpoint: 74e85f021d0e5f638b2a376d365eb493c751e888.
GREEN evidence records that checkpoint plus the tested uncommitted comparator
diff and source hashes; `candidate-green/candidate.patch` identifies the exact
production change. Only trailing blank output lines were normalized when copying
logs. Full Android and independent evaluation are root-owned follow-ups.
Fold7/model/thermal/energy/autonomous learned-skill application: NOT_RUN.
Rollback: revert only the comparator change; no data migration exists.
