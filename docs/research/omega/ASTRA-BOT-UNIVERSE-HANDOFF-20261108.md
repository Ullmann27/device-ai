# Continuum Bot Universe - first vertical slice

Branch: `astra/continuum-bot-universe-20261108`
Base: SOL #658, remote checked at `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`.
Draft: #662. Initial checkpoint: `350ceaf2aea4ccde68645645ef6c92bac729c2f2`.
Implementation checkpoint: `48d2150d946ad90d0c939e2c5141e75a8ad487e3`.
Tested source: [`5a16c1efa01f83a3b181b712fb7eca5874ea5a02`](https://github.com/Ullmann27/device-ai/commit/5a16c1efa01f83a3b181b712fb7eca5874ea5a02).
Status: HOST CONTRACTS PASS / ANDROID AND CODING PROOF BLOCKED. No device
acceptance, merge, signing or release. The requested real-model Kotlin repair
proof is NOT fulfilled by text review.

## Inventory and integration boundary

- `ChiefBotProfileStore` already preserves the 20 stable IDs, owner edits,
  isolated working notes and conversations. Profiles grant no tools or skills.
- `ChiefBotConversation` uses the shared local runtime with private context.
- `ChiefBotTaskIntake` connects explicit single-bot assignments to the owner
  execution lane; its ticket and UI status currently disappear on restart.
- `ChiefDelegationCoordinator` and `ChiefWorkerPool` provide bounded delegation,
  but are not yet connected to a persistent owner-selected team.
- `ChiefJobs` reuses `BoundedAgentOrchestrator` and `ToolGate`.
- `ChiefBotHandoff` / `ChiefWorkspaceWiring` bind coding work to CCC1 authority.
  Their job IDs and grants must not be fabricated for ordinary bot conversations.
- Existing roster/task dialogs already share JARVIS typography, palette and
  responsive columns. No new navigation stack, avatar or rendering loop needed.

## Bounded implementation

EXP-11 (existing specialist orchestration), EXP-12 (restart continuity).
First owner-approved job: analyze the supplied text, then critically review
that result with a different selected bot. Use the existing coordinator/pool
and orchestrator, two serial calls to the shared local model, no tools,
network, private conversation sharing, memory promotion or training.
The initial decomposition is an explicit two-stage template, not a claim of
general autonomous planning. Runtime capability is tool-free text generation,
not inferred professional competence from a profile name.

Persist chief/team selection and bounded task records in the existing bot
store; preserve existing profile/conversation schemas. Stable request identity,
bound profiles/model/attempt, cancellation, stale-result refusal and interrupted
records without automatic re-execution. Coding jobs remain exclusively CCC1.
Model execution receipts prove generation, NOT factual correctness or tool
success. UI must preserve that distinction.

## Connected implementation

- The existing `agents` route is now "Mein KI-Team"; 20 profiles remain on the
  first page, with existing search, private conversations, owner role edits and
  workspaces. Original avatar and energy sphere are untouched.
- Chief and two text specialists persist in `ChiefBotProfileStore`, using its
  existing locks, bounded explicit binary encoding and atomic file writer.
  Existing profile/conversation schemas and IDs are unchanged.
- `ChiefBotTeam` adapts `ChiefDelegationCoordinator` / `ChiefWorkerPool` /
  `ChiefJobs` / `BoundedAgentOrchestrator`. It uses one shared local runtime,
  at most two serial model calls, 120 seconds total, zero tools and no memory
  promotion. Output receipts are generation evidence, NOT correctness evidence.
- Tasks bind the owner UI, team revision, three profiles, model hash and request.
  Restart marks unfinished tasks INTERRUPTED without replay. Late results,
  changed profiles/models and cancelled admissions cannot become new success.
  The shared Activity lane remains busy until workers and native generation end.
- Explicit owner-confirmed history deletion restores the 40-record capacity,
  rotates the team revision and revokes old drafts. No automatic pruning that
  would permit stale `(requestId, revision)` replay; no private chats/CCC1 deletion.
- Reuses `ContinuumSkillLibrary` at private `filesDir/skills`: metadata in the
  profile, at most one relevant approved instruction (<=2000 UTF-8 bytes) in a
  text-stage prompt. Draft/tampered/oversized instructions are not loaded.
  A skill never grants tools or independently verified bot competence.
- "Lerne, Kotlin-Fehler zuverlaessig zu reparieren" (German umlaut spelling in UI)
  and the profile practice button open the EXISTING reviewed CCC1 coding draft.
  Source SHA, allowed files and acceptance criteria remain empty, not guessed.
  This prepares a plan only; it does not compile, learn weights or claim success.

## Coding supplement: explicit missing proof

The existing Node service already binds Forge/Lint/Echo to canonical CCC1
workspaces and independent receipts. Its participants remain fixed in
`coding-service.mjs`, `coding-service-worker.mjs`, and `coding-service-reader.mjs`.
Android's `continuum-typed-coding-bridge.mjs` does not yet carry an owner-reviewed
dynamic bot assignment. Do not copy the text-team selection into a CCC1 grant,
fabricate task/source/receipt identities or create another execution service.

Actual host probe of the existing bridge returned
`BLOCKED / CODING_ISOLATION_UNAVAILABLE` before any model/job execution.
Docker daemon is reachable but has no images; no owner-provisioned isolated
coding executor is configured. No actual local model or Android/Termux compiler
runtime was executed. Therefore the required model-led Kotlin patch -> real
test -> independent second-bot verification remains **BLOCKED / NOT_PROVEN**.
Never convert scripted host fixtures or a second model's text into that proof.

## Validation and reproducibility

The repository already provides the SDK-less fallback scripts. Install Kotlin
2.2.21 on PATH and JDK 17+; provide JUnit 4.13.2 and Hamcrest 1.3. Restore the
public Maven dependencies listed at the top of
`scripts/android-kotlin-compile-check.sh` into `$DEPS` (including Filament,
gltfio and OpenCV `classes.jar` from their AARs). No model weights or signing
material are needed. Paths below are caller-owned, not session-specific:

```sh
CONTINUUM_ANDROID_DEPS="$DEPS" \
CONTINUUM_JUNIT_JARS="$JUNIT:$HAMCREST" \
JAVA_OPTS=-Xmx4g \
bash scripts/android-kotlin-unit-check.sh "$OUT"

node --test .github/continuum/ccc1-bot-assignment.test.mjs \
  .github/continuum/ccc1-bot-report.test.mjs \
  .github/continuum/coding-service.test.mjs \
  .github/continuum/coding-task.test.mjs

# Only on a provisioned Android build host; no signing or Actions dispatch:
./gradlew :app:testDebugUnitTest -PcontinuumUiWidgetTests=true \
  --tests 'at.heinz.deviceai.ContinuumBotRosterStorageUiTest' \
  --tests 'at.heinz.deviceai.ContinuumBotTaskRoutingUiTest'
```

Final results for `5a16c1efa01f83a3b181b712fb7eca5874ea5a02`:
- Complete main-source SDK-less compile: PASS (535 source files, JVM target17;
  compile-only LiteRT stub, not native runtime). Main source was compiled at
  `18d8a42935f966a80f4599dc58e720fbe0223db7`; `git diff` confirms no production
  changes between that SHA and the tested source above.
- All 637 host JUnit classes: **3599 PASS** with Temurin27, Kotlin2.2.21,
  JUnit4.13.2, `LANG=C.UTF-8 LC_ALL=C.UTF-8`, non-root user and a writable
  tracked-source snapshot. Four executable Checks programs passed; one helper
  has no main and is not an executable test. Injected model ports are contracts,
  not real local-model intelligence or independent coding proof.
- Existing canonical CCC1 assignment/report/service/task Node tests: **29 PASS**
  on Node26.12.1. Node sources are unchanged across the tested checkpoints.
  This is host regression evidence, not the pinned Node20/22/24 release gate.
- Eight focused navigation tests PASS, including "Mein KI-Team", Bot Universe,
  TeamViewer, negation and PDF boundaries.
- Initial mixed-source test compilation was INVALID and discarded. The first
  frozen host run found three obsolete "Assistenten" label expectations, now
  corrected, plus root/ASCII-runner failures. Those environment failures were
  resolved by a genuine non-root UTF-8 rerun, not skipping/changing the tests.
- Gradle/widget QA was **attempted, BLOCKED**: JDK27 is incompatible with
  Gradle8.11.1; retried with verified Temurin21.0.13. That retry cannot resolve
  `com.android.application:8.10.0` from Google/Maven/plugin repositories.
  New Robolectric cases are checked in but **NOT_RUN**. SDK-less JUnit does not
  execute `src/uiTest`, Android lint, Gradle, native LiteRT or APK packaging.
- Real local model, Fold7, thermal measurements, Android restart, owner
  package/certificate/installed-version checks: **NOT_RUN**. No release work.

Machine-readable result and log hashes:
[`ASTRA-BOT-UNIVERSE-HOST-20261108.json`](evidence/ASTRA-BOT-UNIVERSE-HOST-20261108.json).
For permission tests, run the command above as an ordinary non-root user in
that user's writable checkout, not root in another user's build directory.
The host run's cognitive fixture scores are not new generalization claims.

## Next bounded step and integration

1. SOL: independently review #662 against unchanged integration
   `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`. No foreign branch was modified.
   The three current review findings (handoff reproducibility, stuck active
   result rejection, permanent full history) are addressed.
2. On a provisioned host/device, run the widget tests and real shared-model
   text-team slice. Do not mark Fold7/native QA from a JVM run.
3. Independently design the reviewed CCC1 participant binding in the existing
   typed Android/Termux bridge before claiming selected-bot coding execution.
   Then require a real model-led RED -> GREEN Kotlin test and a distinct
   verifier workspace. No additional autonomous system, main merge or APK.

Rollback: revert this branch's changes as a unit after preserving task data.
Never delete or rewrite existing profiles, conversations or CCC1 receipts.
