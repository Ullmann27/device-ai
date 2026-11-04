# Bot 01 — bot roster storage repair

Status: SOURCE_INTEGRATED / focused host verification PASS; combined Gradle, AgentActivity integration and physical-device acceptance NOT_RUN by this lane. Local source checkpoint ddf8a6b43cf4b16aed355d07b5c57136ea358d07, followed by this evidence/fixture checkpoint. Integration base 6ae107d43e40ea9c87b0461400c9ad0156e84585; original source PR477 / 337ab0195434c8657bf131bf431f4c391aacc28c. Branch fix/continuum-bot01-storage-20261104. Direct push was attempted and rejected because CLI write authentication is unavailable; coordinator is publishing through the authorized GitHub connector. Do not interpret a local SHA as remote publication.

## Root cause and repair

The existing safeDirectory walked every ancestor from the filesystem root and rejected symbolic links above the app's filesDir. A real Linux temporary fixture mirroring an Android system-parent alias reproduced BOT_STORE_SYMLINK_REJECTED at seedDefaults, before any roster profiles could load. The existing roster discarded diagnostic details and displayed the same generic error for unsafe paths, unreadable data and unsupported schemas.

ChiefBotProfileStore now accepts an optional explicit trustedParentDirectory, supplied only by trusted application code as filesDir. The requested bot root must be strictly below that lexical boundary. The boundary itself must be an existing ordinary directory, never a symbolic link. Its system-managed ancestors may be aliases; they are resolved once to a canonical parent. The store root is resolved relative to that canonical parent without canonicalizing child paths. All root and descendant segments, registry and lock files retain NOFOLLOW checks. Calls without an explicit parent retain the original strict ancestor checks. Both canonical/alias instances hash the same canonical root for process locking.

The registry format and filename are unchanged: schema1 read compatibility, schema2 writes, stable agent IDs, namespaces and profile versions remain intact. No corruption recovery, data reset or unconditional reseeding was added. Existing invalid bytes remain untouched. Malformed profile payload errors become a fixed corruption diagnostic. Filesystem write exceptions after temp creation carry a fixed write-failure code; access-denied at temp creation carries a fixed access-denied code. A post-rename directory-sync failure remains an unconfirmed write; this API does not promise rollback after an atomic replacement.

ContinuumBotRosterDialog shows an accessible fixed allowlisted storage diagnostic and suitable advice while preserving the existing retry operation. It never prints exception messages, paths, private working notes or owner text. No scheduler, model/runtime, worker, permission or package/signature behavior changed. S01 native-execution barrier is untouched.

## Required coordinator wiring (not edited by Bot01)

Both AgentActivity constructions must use:

```kotlin
at.heinz.deviceai.chief.ChiefBotProfileStore(
    java.io.File(filesDir, "chief-bots"),
    trustedParentDirectory = filesDir
)
```

These are the lazy chiefBotProfileStore initialization around original line397 and the read-only Chief worker status snapshot around original line2227. AgentActivity is coordinator-owned. No application integration success is claimed until these calls and the combined gates are verified.

## Actual checks and results

1. Baseline actual production store + ChiefBotProfileStoreTest compiled using Kotlin2.4.0 and JUnit4.13.2: **10 executed, 9 PASS, 1 expected FAIL**. New systemParentAliasKeepsRosterDurable used the original constructor (no trusted-parent API yet) and failed BOT_STORE_SYMLINK_REJECTED. The final version of that test now supplies the explicit trusted parent. Raw output: team-bots-evidence/storage-baseline-red.log.
2. Final actual production store + ChiefBotProfileStoreTest + ChiefBotStorageBoundaryTest: **21 executed, 21 PASS, 0 SKIP**. Includes twenty-profile pagination/reopen/rename; process locking through both alias/canonical roots; legacy schema1 read bytes unchanged and explicit write retaining the owner profile; corrupt and unsupported registries preserved byte for byte; root/lock/primary/context/conversation links rejected; trusted-parent escape and linked-parent rejection; actual POSIX read-only-directory seed failure preserving the earlier three-profile registry; fixed-code privacy checks. Raw output: team-bots-evidence/storage-final-green.log.
3. Original roster caller independently compiled against the repaired store and the six new UI tests: **6 executed, 2 PASS, 4 expected FAIL**. All four failed because the original caller suppressed the required accessible diagnostic. This isolates the caller defect; it is not a claim of running the entire unmodified baseline app. Raw output: team-bots-evidence/roster-original-caller-red.log.
4. Final actual ContinuumBotRosterDialog + store + ContinuumBotRosterStorageUiTest: **6 executed, 6 PASS, 0 SKIP**, Robolectric4.14.1 SDK28 sandbox on Java21. Tests interact with real dialog buttons/EditText and a real background executor. They check alias-based seeding, 8/8/4 pagination, renaming/stable identity, corrupt/unsupported bytes and retry, actual read-only directory failure, unsafe root link, and non-revival after close. A plain Activity fixture avoids initializing any native/model path. Raw output: team-bots-evidence/roster-final-green.log.
5. git diff --check: PASS.

An initial standalone Robolectric harness attempt used the API28 jar on the parent classpath and failed during shadow cleanup with missing android.net.TransportInfo; the corrected harness uses the cached API35 framework for parent shadow type resolution and offline API28 sandbox execution. One corruption fixture initially contained a decodable wrong magic header, correctly yielding UNSUPPORTED_SCHEMA; it was corrected to truncated UTF bytes to actually exercise CORRUPT. Neither initial harness/fixture issue was reported as a production failure or a passing test.

Host dependency limitation: narrow checks recompiled the changed production store/dialog and test sources, but used existing compiled dependencies from /workspace/scratch/391c0f6de827/device-ai/app/build/tmp/kotlin-classes/debug. The corresponding sources for ContinuumState, ChiefBotConversation, ChiefDelegationCoordinator and ChiefWorkerPool were SHA256-compared with this branch and are byte-identical. This is focused host evidence, not an immutable whole-app Gradle build receipt. No Android device, APK signing, APK build, installation or real model test occurred in this lane.

## Reproduction

Host tools: /workspace/scratch/391c0f6de827/build-env/kotlinc/bin/kotlinc (2.4.0), its kotlin-stdlib.jar, jvm-deps/junit-4.13.2.jar and hamcrest-core-1.3.jar; Java21. Compile the three storage sources listed above with -Xfriend-paths pointing to the existing compiled dependency directory and -cp containing that directory plus JUnit. Run org.junit.runner.JUnitCore for at.heinz.deviceai.chief.ChiefBotProfileStoreTest and at.heinz.deviceai.chief.ChiefBotStorageBoundaryTest, putting newly compiled classes first on the runtime classpath. Exact transient commands/logs and the runnable UI harness are retained under /workspace/scratch/91435c40b766/bot01-evidence/ (run_narrow_ui.py and run_narrow_ui_red.py). UI harness uses existing cached Gradle dependency jars plus transformed AndroidX classes.jar files, offline Robolectric SDK35 parent jar and SDK28 sandbox; no network downloads.

Next combined command, after coordinator wiring and cross-lane integration, is the existing testDebugUnitTest task with -PcontinuumUiWidgetTests=true and filters for ChiefBotProfileStoreTest, ChiefBotStorageBoundaryTest, ContinuumBotRosterStorageUiTest plus AgentActivity caller tests, using the coordinator's established variant flags and Android environment. Do not replace that gate with these narrow results.

## Provenance, compatibility and rollback

EXP-11 specialist identity and EXP-12 identity continuity only; one existing Chief store, no parallel architecture. Read AGENTS.md, experiment contract, relevant EXP11/12 register entries and lane PLAN/TEAM requirements. Recovered older /workspace/scratch/952001316bdc/continuum-work/bots was inspected read-only: HEAD b86c91f5; no production source edits; one untracked ChiefBotStorageBoundaryTest.kt. Those boundary cases were preserved/adapted in this branch to the explicit trusted-parent contract; the old directory was untouched.

The contract trusts application-supplied filesDir and same-UID code as before. It is not an OS sandbox and does not claim immunity to a malicious same-UID actor swapping directory ancestry during an operation. Arbitrary untrusted/model text must never select the trusted parent. Registry/context/conversation schemas, owner privacy and original app signer stay unchanged.

Rollback: forward revert of this isolated repair and its bounded caller wiring. Never delete owner data, follow an unsafe bot-store link, silently replace an unsupported registry or promise an APK downgrade. Coordinator must publish these checkpoints/PR before calling the work remotely saved.
