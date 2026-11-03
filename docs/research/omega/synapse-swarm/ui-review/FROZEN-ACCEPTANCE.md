# Synapse bot roster — independent UI acceptance freeze

Freeze before reading the new roster implementation. Baseline parent `76cfebda994c158ae1156748d661f1f2ae1d5713`; root candidate is not yet frozen. Reviewer owns only this evidence directory and `tools/synapse-ui-review/`. Root owns Activity/roster production files; registry builder owns storage. No production edits or self-certification.

## Prospective behavioral acceptance

1. Actual persisted profiles: roster obtains the real registry data; initial twenty names/roles map to twenty stable unique IDs, not twenty active model sessions. Paging reaches every record exactly once. Adding a twenty-first profile works through the actual store; editing and reload preserve it.
2. Rename continuity: select a profile, rename it, save a bounded working note, recreate the store/dialog and verify the same ID, memory namespace, workspace assignment, permissions and content. Another profile must not show that private note. Display names never authorize or index private storage.
3. Honest status: registered/dormant profiles display registered/dormant status, not model/tool activity, verified skills or invented results. Display workspace association and unverified execution state without claiming a directory is an operating system or an active computer.
4. Asynchronous storage: instrument a real-store executor boundary; directory listing, reads, writes and migration work must leave the main looper free. A delayed load/save cannot apply data to a different selected ID.
5. Lifecycle races: dismiss the roster or finish/destroy its Activity while background load/save is blocked; releasing that operation must not revive windows, change the newly opened roster, or crash. Reopening requires fresh valid bindings. Duplicate late callbacks cannot overwrite a later selection.
6. Error handling: corrupted or unsupported store data produces a visible non-success state and preserves evidence/data. Failed saves must not be displayed as durable success. Missing data must not become fabricated profiles, notes or capabilities.
7. Small screen/font scaling: render/measure a narrow portrait viewport at fontScale 2.0. Actions and profile details are reachable by scrolling with usable hit targets and meaningful accessibility labels; title/status/error content remains available. No broad-layout shrink assumption.
8. Architecture: opening, paging, selecting or renaming the roster must not load a second model, issue TTS, start a new avatar clock or infer a task. The original sphere/selected 3D view and existing owner/developer routes remain reachable. This requires actual call-path review in addition to widget execution.
9. Authority: profile registration/name/style edits never grant tools or add verified capability. Context shown must belong to the chosen ID and current allowed scope. UI errors must not reset the registry to seed defaults.
10. Resource bounds: pages, note/input lengths and queued operations remain bounded; repeated navigation cannot grow unbounded pending work or bypass the original concurrency budget. Exact limits will be evaluated against the existing contracts, not invented after a failure.

## Method and evidence

Use independently authored Robolectric tests against actual production widgets/store with controlled executors where the public constructor permits; no fake production API or success receipt. Record exact candidate SHA and source hashes, test hashes, commands, exit codes and raw failures. Candidate-specific harness adaptation must not change these criteria. Host widget evidence is not Android lifecycle/device or full 3D GPU proof. QA coordinates any Gradle build; no parallel full build. Root has observed actual SDK `adb devices -l` with zero attached devices; adb absent from PATH is not adb unavailable.

Unknown device behavior remains FOLD7_NOT_RUN. MΩ-1 local model evidence remains separate. Existing quotas are NOT_VISIBLE. No main merge, model downloads, signing, publishing, install or owner data changes. Rollback omits the reviewer branch.

## Later owner steering supplied by Chief before execution

The parent received a newer owner roster instruction that was not present in this child's inherited transcript: Atlas, Mira (conversation), Echo (context), Mnemos, Prisma, Nova, Sigma (experiments), Kora, Forge (build), Lint, Argus, Pulse, Link, Chronos (tasks), Vox, Iris, Orbit, Archiv, Scout, Helix. Initial three: Atlas/Kora/Lint. This supersedes the older NOA/DELTA/etc. names without altering the independent behavioral criteria above. Root records the steering in its integration handoff. Test API inspected at registry2c5cd84a and roster f48c9d48; no candidate test has run yet.
