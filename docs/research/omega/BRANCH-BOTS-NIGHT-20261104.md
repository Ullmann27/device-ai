# Branch bots — serial continuation 2026-11-04

Status: SOURCE_INTEGRATED / REVIEW_READY in PR #515. Final selected verification: 202/202 PASS, 0 FAIL/SKIP; Android/APK/device gates remain NOT_RUN. Starting source: PR #515 at 5373ec7c1399cb34fb8896f7318fdfa5437fa283. All 104 locally materialized code/test files match the exact remote blobs. This is a selected source snapshot, not a full Android checkout.

## Coordination

C1 #519 owns shared application entry points. PR #527 at d2aedf2c272bfa20d4e1930a0f65277c3f02be0f reports intake of nine exact #515 source/test files; those changes must be preserved. This continuation changes only its own branch-discovery/CLI/host-diagnostic slice and its tests. It does not integrate or edit another team's active branch.

## Sequential milestones

1. Reproduce bounded-discovery starvation and add source-bound page continuation. Every report must retain honest per-page coverage; no copied cursor becomes execution authority.
2. Connect continuation to the existing finite scheduler and optional single-pass resume, with private atomic state, safe restart and unchanged proposed-only backlog.
3. Exercise the APK-toolchain diagnostic against near-match versions and invalid file shapes. Repair only reproduced false admissions; do not install tools or claim an APK.
4. Verify the combined selected suite, published source identities and compatibility with the current integration intake. Save exact evidence and a ready-for-next-iteration handoff.

Relevant experiment contracts: EXP-00 (diagnosis only), EXP-01 (bounded local cadence), EXP-11 (canonical backlog), EXP-12 (source-bound recovery). Baseline 183 PASS remains historical until a new actual combined run. No parallel agents in this round.

## Authority and limits

No main merge, package/signing/model change, paid model API, Actions invocation, release or installation. No new daemon or competing task framework. The existing owner/CCC1 workflow still decides execution. Android compilation, device test and APK acceptance remain NOT_RUN.

## Checkpoint protocol

The accompanying state.json is ACTIVE during this session. A scheduled continuation must not edit an ACTIVE checkpoint or infer that elapsed time alone makes another session inactive. Once this round is complete, state becomes READY_FOR_NEXT_ITERATION with exact evidence and next scope. Missing access is a blocker, never an invented continuation.

Rollback: individually withdraw this round's bounded source changes; retain all earlier commits and evidence.

## M1 — bounded discovery progression

Reproduced the original starvation with five real Git fixture branches: three bounded passes repeatedly selected the same two sources. Three new regressions failed before the change; the full scanner/CLI subset now passes 27/27, zero failures/skips.

The scanner returns a source-root/checkout/target/ref-snapshot-bound nextCursor. Resumed pages report their own range and remaining sources. Even the final partial page never claims a full inventory. Malformed, out-of-range and stale cursors are rejected. Serialized navigation is not proposal authority; existing in-process report admission remains unchanged. CLI persistence is the next milestone, not yet implemented at this checkpoint.

## M2 — durable continuation

Five initial continuation regressions failed before the connection. After implementation and three additional boundary checks, the scanner/CLI/scheduler/preflight subset passes 61/61, no failures/skips. This includes real child-process restarts and SIGTERM during diagnosis, retained live-lock ownership, malformed/changed state and report bytes, source-ref changes, path truncation and deferred proposals.

Finite loops now save a private, hash-checked navigation cursor under the existing scheduler's lock implementation. The lock function is exported without changing its body or the scheduler's state/outcome vocabulary. Single invocations opt in with --once --continue-scan. Plain --once keeps its previous incomplete-inventory failure behavior.

A successful page has pageComplete=true but complete=false unless the report itself covers the whole source inventory. Path truncation stays ERROR and advances to subsequent sources; proposals skipped only by the per-pass budget are revisited on later sweeps. A full canonical backlog remains a reported capacity limit. Every task remains PROPOSED; there is no automatic execution.

The cursor is navigation only, not accumulated full-inventory or APK evidence. Ref/target drift restarts at the first page and records scanRestarted. Changed configuration, corruption and unsafe files fail closed. After a crash before cursor commit, canonical proposal identities deduplicate replay. Terminal scheduler budgets remain terminal; an explicit new single-pass command is a separate invocation, not a reset of an old budget.

Example from a complete approved checkout:

~~~sh
node .github/continuum/branch-bots-cli.mjs --root "$PWD" --state "$HOME/.local/state/continuum-branch-scan" --once --continue-scan --max-sources 40
node .github/continuum/branch-bots-cli.mjs --root "$PWD" --state "$HOME/.local/state/continuum-branch-scan" --loop --max-cycles 12 --interval-minutes 60 --max-sources 40
~~~

Use the same scan settings for the same private state directory. An exhausted loop session needs an explicitly separate session or a one-shot continuation; it does not silently acquire a new cycle budget. This session has not started such a device process.

## M3 — precise host tool profile

Three new tests reproduced false host admission: Gradle 8.11.10 matched an 8.11.1 substring, a directory satisfied the SDK platform check, and a non-executable AAPT2 file passed. The corrected diagnostic requires one exact stable Gradle 8.11.1 line, a nonempty readable regular platform file, and a nonempty executable regular AAPT2 file. Near-match prereleases, contradictory/duplicate Gradle banners, empty files and symlink loops are covered.

The relevant CLI/host/mobile-builder subset passes 72/72, no failures/skips. Existing positive fixture AAPT2 entries now have executable permissions. These shell fixtures are not real Android tooling. Executability does not prove AAPT2 ABI compatibility, SDK contents, dependency cache or successful compilation; hostReadyToAttemptQa remains only a preflight indication. The actual APK/device gates remain NOT_RUN.

## M4 — genuinely read-only source audit

Three actual Git fixture regressions were red before the fix. The old release source audit executed a configured fsmonitor helper and a configured clean filter (both harmless marker scripts), and accepted a current-worktree gitlink. The release audit now reuses assertBranchSourceSafe before obtaining cleanliness; unsafe configuration/submodules are rejected before status. A genuinely dirty checkout still returns the existing UNCLEAN_CHECKOUT blocker instead of changing the source-admission contract.

The combined scanner/release-preflight subset passes 42/42, no failures/skips. This is a connection to the existing reader policy, not a second Git sandbox. The module now intentionally imports the existing branch-discovery guard from the repository. No APK was built and no external helper beyond the controlled fixtures was run.

## M5 — bounded report volume

A real Git fixture with 81 distinct source commits reproduced SERVICE_RECORD_TOO_LARGE under the previous default scan: the private 4 MiB record writer rejected the completed report, so no continuation could advance. Discovery now budgets its source report at 3 MiB, leaving room for the CLI's small environment diagnostic within the existing 4 MiB storage contract. A byte boundary creates another source page rather than an oversized write.

The scanner/CLI subset passes 37/37, no failures/skips. It visits all 81 fixture source commits across actual process restarts. A separate 2,000-long-path source proves that a single oversized branch keeps its exact change totals while truncating detail explicitly; coverage remains incomplete and no APK or merge authority is granted. Oversized metadata or an individually unrepresentable record still fail closed. Git-output, time, proposal and canonical-backlog limits remain intact.

## Current integration intake comparison

Pinned PR #527 at ca80459a3f6695c8fecff4a217fbe93318452121 was compared against all 104 selected baseline code/test blobs. Exactly two differ: development-backlog.mjs contains the existing one-pass task-selection optimization, and the owner QA test arranges the same signing exclusions in its common negative-gate table. The seven files changed by this continuation have matching old preimages. Preserve both upstream files; never replace the complete current integration tree with this older branch base. The selected compatibility test and bounded patch are complete; details follow.

## Final verification and handoff

Verified code commit: **c216ebcd4726fefd221a6ee347699c9a1280fc47**. The later evidence checkpoint does not alter these source files.

- Own selected suite: **202 PASS, 0 FAIL, 0 SKIP**, 12 test files; source inputs unchanged. Raw TAP SHA256: ab52e0572fb5d3c3a2170bf2f40604b9a524ab1d50d2c0a1654bbc5e4756c925.
- Selected #527 compatibility overlay: **202 PASS, 0 FAIL, 0 SKIP**; source inputs unchanged. Raw TAP SHA256: af29df578e1f396af46b2bcdd3f06e96920923f19f496f75b945562dcfbc301d. This is the same case set on different selected inputs, not 404 unique tests.
- All **104** selected published code/test blobs were read back through the Git tree at the code commit and match the actual tested files. Seven changed JavaScript files pass syntax checks; own and intake diff checks pass.
- The current intake preimages for all seven changed paths match the previous #515 files. integration-delta.patch was applied with git apply --check --index and git apply --index in an isolated selected intake snapshot. Patch SHA256: 68a8b0bff7d3a09d7f58c4ac16a119e8d0cd84cdc2935e966d18eb0551bb0669.
- The two newer upstream files are retained exactly. Every baseline/intake/code input is listed in the corresponding source manifest. Later upstream advances require another source comparison before application.

This is a selected 104-file Node/Git source verification, not the full application tree. Git fixtures and executable fixture tools are real local processes but do not establish native Android tooling, model quality, APK compilation, rendering or device success. No independent second agent was started in this serial round; the compatibility run is a separate selected-source check by the same coordinator.

Actual local host diagnosis still reports EXPLICIT_JDK_NOT_FOUND, SDK36_NOT_FOUND, GRADLE8111_NOT_FOUND and NATIVE_AAPT2_NOT_FOUND. No service was retargeted, no build tool downloaded, no build/signing/install/release performed. The historical full Android evidence from other teams must be assessed on its own exact source.

### Published milestone commits

1. M1 page continuation: 030578c0aede7745b24915a50a14a2eac8f4859b
2. M2 durable cursor connection: 9423d5fb6978496ec843d0f7bcbe8285ee63677f
3. M3 exact host profile: 24544f970eb9061d6040cc2412cc2473fdfa15ba
4. M4 guarded source audit: ef2146e560452ac7fe65603a31930ecf234eedaf
5. M5 bounded report volume: c216ebcd4726fefd221a6ee347699c9a1280fc47

Raw RED/GREEN logs, manifests, final runs, integration patch and fresh host diagnosis are under docs/evidence/branch-bots-night-20261104/. The previous 183-test continuation and intermediate 200-test run remain historical and are not transferred to changed sources.

### Subsequent work

state.json is READY_FOR_NEXT_ITERATION. An enabled hourly scheduled development task was created and read back in Europe/Vienna. It must read the current branch head and this state, leave ACTIVE sessions untouched, proceed sequentially, and checkpoint every actual improvement. It is separate from the app and does not establish a continuously running local worker. No parallel subagents were used in this round.

C1 remains the shared application integrator. Adopt only the seven-path delta into the currently reviewed candidate, retain its newer backlog/QA sources, then obtain full Android QA on a complete functioning checkout. If that environment is unavailable, keep the gate explicit and take a different documented free integration issue. Do not silently merge this branch's older complete base over C1/#527.

## M6/M7 — 2026-11-04 published current-intake readback

Read the latest published #527 head `a6b314d3e8fac796ce7059a336bf5e79eb622ede` and independently compared all **seven** night-patch paths to the exact #515 preimage `5373ec7c1399cb34fb8896f7318fdfa5437fa283` and tested night source `c216ebcd4726fefd221a6ee347699c9a1280fc47`. All 7 current #527 blobs **still equal the original baseline**, and all 7 tested night blobs differ. Therefore the night patch has **not** yet been adopted in #527, and there is **no preimage mismatch** for that frozen integration target. The exact per-path SHA-1 entries are in `docs/evidence/branch-bots-night-20261104/live-intake-readback.json`. This is a pinned readback only, not a fresh patch application or execution on the current Android candidate; the previously verified 202-case selected overlay remains bound to the earlier #527 source `ca80459...`.

Current #527 handoff records the **3011-case Android FAIL** at `ca80459...` (2984 PASS, 8 FAIL, 19 SKIP), with later targeted Shell fixtures not yet a new full-suite PASS. Current C1 `4d74de6c5ca04582ff455356440046e9148acbb0` is separately addressing active UI and worker integration. The shared Android/UI ownership remains exclusively C1; no foreign branch was changed. No new Node, Android, model, GPU, APK or device tests were run in this readback. The next safe action is a C1-owned selective intake of the seven changed paths after fresh target verification, followed by complete common gates.

## M8/M9 — 2026-11-05: inherited Git trace destinations (source patch / targeted verification)

A fresh negative control reproduced an unintended side effect in the read-only Git scan: when the caller sets an absolute `GIT_TRACE` destination, Git writes a nonempty file while answering even the supposedly read-only status query. The same occurred for `GIT_TRACE_PERFORMANCE`, `GIT_TRACE2_EVENT`, and `GIT_TRACE2_PERF`. These are real Git subprocess effects, not evidence of a malicious caller in production. The original reader passed `process.env` through, and the existing scheduler's `sourceIdentity` performed its own Git reads with the same inherited trace variables *after* preflight. The guard's earlier safe-configuration checks do not remove these trace output destinations.

**M8:** the existing `branch-discovery.mjs` reader now removes only environment keys beginning with `GIT_TRACE` from the environment copy before the first Git invocation. The parent process environment and the existing repository/Git safety policies are unchanged. One actual Git fixture controls the emitted bytes and preserves clean-status output; all four trace families write in the original negative control but do not write after the candidate sanitizer.

**M9:** the existing `local-teamleader-loop.mjs` scheduler source-identity reader performs the same environment-copy sanitation before its two Git subprocesses. No new scheduler, task authority, work loop or lease was introduced. Two regression tests were appended to the existing real-Git scanner and scheduler tests. Their commands, fixture setup and expected no-write checks are in the committed test files.

**Source-bound targeted evidence:** Original exact source at `ffb193998233a3bb1b7c620e95129decfb0980e7`; patch at `ef48e63713fa966710647dc4eadd4da671df39a8`. The actual reader and source-identity function bodies were extracted separately from both immutable GitHub blobs and executed in a V8 injected-subprocess check. Both original functions passed all four trace destinations into the child environment; both patched functions passed **zero**. Unrelated `OTHER_SETTING` remained intact throughout. The independent Node v22.16.0 real-Git fixture reported **5/5 PASS, 0 FAIL/SKIP**, confirming original writes, sanitized absence of writes, identical clean Git output and no parent-env mutation. The source/test-file syntax check compiled **4/4** published patched files (imports/exports removed only for the parser check). The exact new commit modifies four files: two Git reader source files and their two regression files; no Android, package, signing, model, owner or release path changed.

These checks deliberately **do not claim** that `node --test .github/continuum/branch-discovery.test.mjs .github/continuum/local-teamleader-loop-source-preflight.test.mjs` was run on a full checkout. The current executor has no authenticated full repository/complete dependency snapshot, so the **integrated Node suites remain NOT_RUN**, as do Android, APK, physical Fold7 and real model runs. A separate fully configured checkout should execute those two real tests and the previous selected 202-case suite, followed by source-frozen combined gates before adopting the change.

Scope and source/evidence identities: [trace-env-source-check.json](../../evidence/branch-bots-night-20261104/trace-env-source-check.json). Integration #527 at `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3` still uses the old scanner and scheduler blobs. #519 at `13c37f55789bf67b07beeb37cc1a5035526e623e` continues to own common application entry points; it is not edited here. The original seven night-patch files and historical 202/202 receipt remain unchanged by this scoped repair. C1 and the combined integrator decide selective adoption only after their own review and gates.

## M10/M11 — 2026-11-05: scheduler source identity cannot be redirected to foreign Git checkout

The scheduled source-check checkpoint was marked ACTIVE before modifications. A real local Git fixture with two unrelated repositories reproduced **two distinct policy failures** in the existing scheduler's `sourceIdentity` Git subprocess: inherited `GIT_DIR` and `GIT_WORK_TREE` silently bound a clean source checkpoint to a foreign HEAD, and could present an actually dirty intended checkout as clean. The `-C` Git option did not cancel those environment overrides. This is a controlled reproduced boundary condition, **not evidence of an observed production attack**.

**M10 (smallest compatible repair):** published code commit `a03224f68a1e9f44bdbcbde6cd04b2808a75e4ec` changes only `.github/continuum/local-teamleader-loop.mjs` and appends two regression tests to `.github/continuum/local-teamleader-loop-source-preflight.test.mjs`. The existing `sourceIdentity` reader now clears inherited Git repository/index/object path selectors from its own environment copy, matching the earlier branch-discovery reader. The original parent environment, async caller sourcePreflight, git trace protections, canonical task queue, lock/cycle behavior and all permissions remain unchanged.

**Executed negative/positive controls:** a standalone Node v22.16.0/Git 2.47.3 fixture executing the source-matched Git-spawn logic reproduced **0 PASS / 2 FAIL / Exit 1** on the original and **2 PASS / 0 FAIL / Exit 0** with the new environment sanitation. These are actual subprocesses using temporary local repositories; the isolated harness is **not** an import of the complete GitHub module. New regressions were committed to the actual project test file, but that full Node test file and the prior 202-case suite remain **NOT_RUN** in this executor because its complete checkout is unavailable. Two exact updated project JS files parsed under a stripped-ESM V8 syntax check; not an actual Node module build.

**M11 (fresh frozen integration review):** C1 #519 `13c37f55789bf67b07beeb37cc1a5035526e623e` and combined #527 `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3` were reread. All **8/8** changed current branch paths have exact old preimages in the combined target; all eight target blobs are still the baseline versions and differ from this branch. The tested combined 3,032-pass Android/Node evidence at `80c01939...` predates these changes and is **not** acceptance of the new overlay. C1 retains the sole shared entrypoint ownership; neither shared branch was modified.

Full source/blob list, independent fixture outcomes and evidence limitations: [m10-source-identity-git-env-20261105.json](../../evidence/branch-bots-night-20261104/m10-source-identity-git-env-20261105.json). Next step: select only the eight changed files for C1-owned review, execute the two true Node regression files and 202-case selection in a complete checkout, then fresh combined full Android gates. No APK, signing, install, GPU/device or model test in this round. Rollback: revert the two M10 source/test files without altering the historical M1–M9 patches or owner channels.

## M12–M14 — 2026-11-05: Scheduler-Pfadgrenzen vor dem ersten Schreibversuch

Ausgehend von PR #515 `b221e33d3a78af3ac48683020e8ffb20d8fbefb7` wurde der eigene Checkpoint `ccf3b48482ae108c94c5ffe7306e6a65563b7367` vor Bearbeitung auf ACTIVE gesetzt. Die Git-/Dateisystem-Grenze der vorhandenen `runTeamleaderLoop`-Routine hatte drei getrennte, reproduzierbare Probleme: `git -C <Unterverzeichnis>` lieferte ein gültiges HEAD, obwohl der angeforderte SourceRoot nicht der Repo-Toplevel war; ein abgelehnter Checkpoint innerhalb des echten Quellbaums erzeugte zuerst sein Elternverzeichnis; über einen externen Symlink in denselben Quellbaum geschah das ebenfalls.

Der kleinste Patch `f9bd4e2949ef2d09d9c45c4c93924390650a1f81` betrifft ausschließlich `.github/continuum/local-teamleader-loop.mjs` und die vorhandene `local-teamleader-loop-source-preflight.test.mjs`. **M12** gleicht den kanonischen SourceRoot mit `git rev-parse --show-toplevel` ab. **M13** verweigert direkt im SourceRoot liegende Checkpointpfade vor `mkdir`. **M14** prüft zusätzlich den aufgelösten nächsten existierenden Vorfahren und den zukünftigen Pfad vor `mkdir`; die bestehende kanonische Prüfung nach dem Anlegen bleibt erhalten. Vier echte Projekt-Regressionstests (drei negative Fälle, ein erlaubter Außenpfad) wurden angehängt. Kein Backlog-, Modell-, App-, Signierungs- oder Autoritätsvertrag wurde geändert.

**Tatsächlich ausgeführt:** eigenständiger Node-v22.16/Git-2.47.3-Test mit realen temporären Git-Repositories und einem echten Symlink, auf den einschlägigen Quellabläufen nachgebildet: Original **1 PASS/3 FAIL**, neue Abläufe **4 PASS/0 FAIL**. Das war ausdrücklich ein **isolierter Quellablauf-Test**, **keine Ausführung des vollständigen importierten GitHub-Moduls**. Beide veröffentlichten neuen JS-Dateien wurden mit V8 nach Entfernen der ESM-Deklarationen syntaktisch geparst. Die vier in das echte Projekt eingefügten Regressionstests, die vollständige 202er-Node-Suite, Android/Gradle, Besitzer-APK und Fold7 wurden **nicht ausgeführt**. Der vorangegangene 202er-Erfolg bleibt historisch. Genaue Änderungen, zwei Remote-Datei-Hashes und Testgrenzen: [M12–M14-Belege](../../evidence/branch-bots-night-20261104/m12-checkpoint-path-gate-20261105.json).

**Integrationsabgleich:** C1 #519 `13c37f55789bf67b07beeb37cc1a5035526e623e` und der gemeinsame #527 `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3` enthalten bei beiden bearbeiteten Pfaden weiterhin die ursprünglichen #515-Dateiblobs (`f83e6a1d` und `13c56614`). Das ermöglicht eine spätere selektive Aufnahme der **kumulativen** Nacht-/M10-/M12-Änderungen durch die zuständige Integration, ersetzt aber keine Prüfung des dann aktuellen Zielstands und keine neue gemeinsame Android-Abnahme. Diese beiden fremden Zweige wurden nicht verändert.

Bei parallelen Pfadumbenennungen nach der Vorprüfung verbleibt ein allgemeines TOCTOU-Risiko; diese Reparatur ist keine beliebige Filesystem-Sandbox. Der Status ist wieder READY_FOR_NEXT_ITERATION. Nächste Prüfung: zwei echte Projekt-Node-Dateien und bisherige 202er-Auswahl im vollständigen Checkout laufen lassen, danach gezielte C1-Aufnahme und unveränderte Gesamtgates. Keine APK-Autorisierung oder Geräteabnahme.


## M15/M16 — 2026-11-05: inherited inline Git configuration cannot activate a source-identity monitor

The scheduler's read-only Git children previously removed `GIT_CONFIG`, repository selectors and trace destinations, but still inherited the different inline-configuration channels `GIT_CONFIG_COUNT` + indexed key/value fields and `GIT_CONFIG_PARAMETERS`. In two controlled local Git fixtures, a caller-set inline `core.fsmonitor` executable wrote a marker during `git status --porcelain` even though the checkout was clean. There is **no evidence** that such a caller or production attack occurred.

**M15** removes the counted inline Git configuration selectors from the private child-process environment copy. **M16** also removes the parameter-string selector. Exact source patch: `.github/continuum/local-teamleader-loop.mjs`; two regression tests appended to the existing `local-teamleader-loop-source-preflight.test.mjs`. No new worker, ledger, permission or background process; the trusted `sourcePreflight` call remains awaited and the parent environment is untouched.

Before modification, the original Git subprocess fixture wrote **2/2 unwanted markers**; with the candidate child environment it wrote **0/2**, while each `rev-parse` returned the expected repository/commit and both status results stayed clean. This was actually run with Node v22.16.0 and Git 2.47.3; a second invocation reproduced the fixture. A separate source-bound V8 check extracted and executed the exact published `sourceIdentity` function with stubbed Git transport: **four cases passed** (count: 3 forwarded selectors original → 0 patched; parameters: 1 → 0; three Git calls each). Both changed full JavaScript files parsed after stripping only module import/export declarations. The two new *project* regression cases and complete 202-case/full Node suite are **NOT_RUN** because this environment has no full authenticated checkout.

Code commit: `aa5255ea00da8f4753cbd7de48c849783acbb45a`. Published source/test Git blob SHA-1: `888e52cbbd1f8766625646f7ba0fe6fc8bf6c88c` and `835f7a7e158dec2c9598106b358f135009d6b5ec`. Source-bound evidence: `docs/evidence/branch-bots-night-20261104/m15-m16-inline-git-config-20261105.json`. The isolated real-Git reproducer is not a substitute for the full imported project tests.

C1 #519 remained at `13c37f55789bf67b07beeb37cc1a5035526e623e`; combined #527 remained at `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3` on fresh readback. Both still contain exactly the old scheduler and scheduler-test preimages; neither shared branch was edited. The previously reported 3,032-pass Android acceptance in #527 is historical and does not certify this new patch. Next integration must compare the *then-current* target source before selective adoption.

Checkpoint returns to `READY_FOR_NEXT_ITERATION`. APK compilation/signing/install, original-signature channel, native graphics and physical Fold7 acceptance remain **NOT_RUN**. No main merge, paid service or GitHub Actions invocation occurred.
