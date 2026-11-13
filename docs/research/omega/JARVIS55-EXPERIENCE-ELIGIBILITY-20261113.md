# Experience recall eligibility before ranking

Source: `626309c49474e45b1d6b1371868e53b3249d235f`.

## Demonstrated gap

`AgentExperienceRecall.hints` requested at most 64 time-bounded matches, then excluded PARTIAL, missing-evidence and other ineligible outcomes. `InMemoryExperienceStore` ranked and limited those matches first. More than 64 newer or more lexically relevant ineligible entries could therefore hide a still-current success hint or failure warning.

The normal Activity evaluator does not independently prove task success (`evaluationConfirmsOutcome = false`). Its resulting PARTIAL records correctly persist across checkpoints. They must remain ineligible for positive recall while no longer displacing eligible history.

## Change

- Add a backwards-compatible eligibility overload to the existing `ExperienceLookup`.
- In `InMemoryExperienceStore`, apply the caller's eligibility predicate before lexical ranking and limit. Existing time bounds, ordering and the five-argument API remain available.
- Keep one recall policy: the same common task/time/relevance and existing SUCCESS/failure predicates drive preselection and final revalidation.
- Limit inspected provider results to 64, retain two success hints and two failure warnings.
- Keep the legacy-provider fallback bounded to the requested shortlist. Legacy providers can still omit eligible history; they must implement the overload for archive-wide eligible selection.
- Preserve all returned hints as MODEL_GENERATED, UNVERIFIED, confidence zero, DEVICE_LOCAL_ONLY. Historical evaluator reports remain fallible; no new fact, authority, tool permission or verified diagnosis is inferred.
- No archive schema, owner correction, expiry, task restart, weight, or agent-permission changes.

## Regression coverage added

- Real `BoundedAgentOrchestrator` model input receives the older success hint and failure warning despite 96 higher-ranked PARTIAL or missing-evidence records.
- Signed archive checkpoint/reopen retains the same eligible hints behind 96 newer PARTIAL records.
- Filtering preserves relevance, recency and ID ordering and the 64-candidate limit.
- The legacy fallback neither expands the request limit nor examines records beyond it.
- A provider ignoring eligibility is revalidated, including PARTIAL, absent/malformed evidence, missing tools, low score, wrong task type, irrelevant content, expiry, future time and missing failure observations.
- A provider returning more than 64 records cannot force recall beyond that bound.
- Existing model-assumption exclusions, window endpoints, failure-warning and tool-authority tests are retained.

## Verification status

Source review completed. Kotlin/Gradle tests: NOT_RUN in this connector-only worker. APK, Android hardware, Fold7, speech, frame time and thermal tests: NOT_RUN by this worker.

Required integration suites: `AgentExperienceRecallTest`, `ExperienceRecallWindowTest`, `ExperienceRecallRecencyTest`, `ContinuumRuntimeStoreTest`, existing cognitive and Android QA gates. Parent integration must record actual CI outcomes.
