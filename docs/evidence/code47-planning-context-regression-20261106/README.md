# Code47 Planning context regression — RED checkpoint

Production baseline: `c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e`.
Test continuation parent: `ee0c2e28bcb4aae30bc94f6b44c81c0903cbf3b3`.
Experiments: EXP-05 / EXP-12. No production changes on this branch.

## Observed result

Six real Robolectric Activity tests: four PASS, two intended FAIL.
`planningPromptContainsCurrentRequestOnceAndOnlyEarlierTurnsAsHistory` sees the
untouched owner request twice in the prompt actually submitted to LocalModelRuntime.
`completedPlanningTurnIsRecordedOnceThroughActivityAndTranscript` sees two matching
owner context rows in the real JSONL store after actual result delivery.

Passing controls cover planning clarification, EMPTY runtime, READY runtime with
missing model identity, and direct vague app clarification. Each retains the
complete original once, preserves the earlier owner turn, starts no inference,
and persists explicit unanswered/non-model metadata.

## Minimal correction owned by Bot1

Remove only the pre-run `recordUnanswered(sessionId, intake.original)` in the
`Intake.Planning` branch. Both non-model routes already call `clarifyAppRequest`,
which records the original. Model success/failure/partial delivery owns recording;
the separately reviewed STOP guard also preserves the unanswered original.

GREEN has not run at this checkpoint. Bot1 is the sole Activity writer.

## Reproduction and boundaries

`invocation.json` records the exact Kotlin compilation and JUnit invocation with
the existing 77-entry real Gradle worker classpath and real Android resources.
The only test replacement is the local inference boundary; Activity, routing,
prompt assembly, agent evaluation, callback recorder, JSONL store and restore
are real production classes. The fake returns synthetic text without tool calls.
No Gradle, device model, APK, deployment, signing or device-quality claim.

`fixture-first-run.txt` preserves the initial fixture setup failure: focus was
requested before the first paused-looper layout. Advancing the real looper before
focus fixed fixture initialization. This is not a product RED.
`red.txt` contains the subsequent two intended product failures.
