# Independent bot roster review — secured interruption handoff

Status: SOURCE_REVIEWED / HOST_TESTED on the exact last tested source below; queue repair NOT_RUN. No final integration, Fold7, GPU, model or release acceptance.

Review branch: codex/synapse-ui-review-20261103, draft PR428. Last executed evidence checkpoint: 2c00db420d7bf3dff4b138024ac6e8b7bd35e379. Frozen acceptance44fca738 preceded source inspection. This handoff is a documentation-only successor written through the authorized GitHub connector after shell access failed; no production changes.

## Last actual execution

Production source c50266b27ba65a7e253be75c67b5f4d7f89a6973.
Tests SHA256 8fca3157f599d473bfa2d058c252e9c973b7487f602e2372f03ef18ec4fef7d3.
Actual Android production/test compile PASS. Robolectric13 executed:12 PASS,1 FAIL,0 SKIP.
The sole failure is repeatedOpeningHonorsOneInFlightStorageBudget:16 repeated opens queued16 storage jobs against the agreed bound1.
Twenty profiles, creation of profile21, pagination, rename/private-note persistence, restart, async I/O, stale/dismiss/destroy callback suppression, corruption fail-closed, optimistic version conflict, rejected executor and320px/fontScale2 scrolling/accessibility passed in this host widget suite.

Raw evidence:
- c502-first-ui/gradle.log, gradle.exit, junit.xml, before.json, after.json.
- a479-corrected-invocation/: original real Kotlin compile RED, Drawable-vs-Int outer color ambiguity, no tests executed.
- a479-initial/: configuration-only invocation failure; retained QA allowlist was not weakened.
- RUNS.md, SHA256SUMS.json and tools/synapse-ui-review/run-scoped.sh.

## Root repairs and source-only review

Root fixed the compile ambiguity at c50266b27; that repair was actually compiled above.
Root queue repair407cdea413dbc8f31e5e662e0317eec5daf897dc was fetched and reviewed through GitHub after the outage. It reuses one controller per Activity, admits at most one storage operation with AtomicBoolean, reports blocked navigation with an explicit retry, resets admission on completion/rejection, and invalidates presentation on close without cancelling accepted writes or unrelated coding jobs. These are inspected code properties, not executed acceptance. Repaired UI tests remain NOT_RUN.

Chief status addition6edd529da098c175515a12dfc6666572acf1aae1 reads only page(limit20) and displayName from the profile store on the existing ChiefWorkerPool. It never calls workingContext or owner-memory retrieval. It explicitly distinguishes registration from running work and preserves the existing bounded pool(1 active,2 queued). Source privacy review found no new private-note transfer. Actual Activity execution of this new status path is NOT_RUN.

Original orb,3D/model/TTS files were unchanged by the roster slice. Activity adds routes and reuses its existing executor; no second model, speech listener or renderer is introduced. This does not establish final whole-app behavior.

## Environment interruption

The selected execution environment became unavailable. Actual shell startup failed with exec-server transport disconnected and45-second recovery timeout. The cloud status connector still reported running, which did not restore shell access. No local recovery tool was available; no heavy retry/new test or model download was attempted. GitHub reads/writes remained available and the remote evidence checkpoint was verified. No Gradle job from this reviewer was running at the outage; its last process had already completed.

## Resume in order

1. Restore a working execution environment and fetch actual Chief/registry/runtime/reviewer HEADs; do not assume old local paths or processes survived.
2. Preserve the old13-test source and raw RED. Two scheduling-specific expectations require transparent adaptation for the now-approved busy/retry design: olderLoadCannotReplaceNewRosterAndCallbacksStayBounded previously expected two queued reads; lateSaveWritesOnlySelectedIdentityAndDoesNotReopenItsProfile previously expected a queued replacement page. In the repaired design assert one admitted operation, visible busy/retry, no stale presentation, eventual fresh load after explicit retry, and that the accepted save still writes only its selected stable ID. These adaptations were planned but NOT IMPLEMENTED before interruption.
3. Keep the repeated16-open/bound1 counterexample unchanged. Add focused close-during-save and show-after-destroy controls for the new close API as appropriate.
4. Run scoped real Robolectric tests using the exact repaired source and source-init injection; one Gradle job at a time. Record all source/test hashes and raw results. Do not call the repair GREEN until these pass.
5. Run unfiltered final Node/Android/lint/parity acceptance on the exact final combined Chief SHA. Omit synapseUiReviewOnly for full QA. Existing source-specific C03 memory failure and new genuine-runtime changes need their own explicit handling; do not inherit ancestor counts.
6. Continue true local-model MΩ work from the approved runtime source and its independent evidence, without silently converting this UI proof into cognitive-loop acceptance. Fold7/device/energy remain NOT_RUN.

No Main merge, signing, APK installation/publication, owner-data change, paid service or production weight change occurred. Quota NOT_VISIBLE. Rollback omits this reviewer-only branch.
