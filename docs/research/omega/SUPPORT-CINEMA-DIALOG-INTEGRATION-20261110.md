# Cinema/dialog integration support

Continuation checkpoint: #724's queued-result fix, latest #720 ASR binding and final #726 outcome fix are included. Combined verification of this frozen source is pending. The earlier 91-test result below belongs only to its explicitly pinned source.

Owner: Continuum support root. Canonical development target: `cinema/fluid-light-stage-20261110` / PR #715. This isolated candidate integrates the current #720 source; the target branch remains untouched.

## Frozen inputs

- Cinema remote `5d36c5a57c031308c8de91049e19e9e8cb8365d5`, tree `970445b70dcd57d344f43ff95bfd58fd505f42ab`.
- Local Cinema equivalent `47a86df300210de7298e7768959cd5bf0c96fb4f`, exactly the same complete Git tree.
- Dialog remote `e99530750f4b471d54dc397b7a7b7f8b2977f6d1`, tree `d2380771bffe595f7371963a283b0a882c8974e3`.
- Local dialog equivalent `a2c8e7cf827bb2f9651520b20d49980f8ffdbcac`, exactly the same complete Git tree.

Both remote PRs were read before integration. Compare showed diverged histories: three dialog commits and eight later Cinema commits. No newer equivalent integration was present among the latest open PRs. A fresh isolated checkout merges both pinned source trees; `AgentActivity.kt` merges without conflict. Other Cinema changes, original avatar and existing STOP/return methods remain intact.

## Verification checkpoint

PORTIERT: combined source in Draft #725. VERIFIZIERT: **91/91 targeted Android host tests PASS**, 15 suites, 0 failures/errors/skips. GERÄTEBESTÄTIGT: NOT_RUN.

Combined source: remote `ca839c0555c121b248b843c4030c0e928603a535`, local equivalent `d33bdc713bc617ef7002f522847c1d37bb356360`, identical complete tree `a1ca894c4ea395f8807d5d158475cd753cd293a6`. The final evidence commit changes documentation only.

Full main/test Android compilation completed, followed by the 13 existing dialog/consent/context suites plus `CinemaStopReturnUiTest` and `AgentCancellationRegressionTest`. This uses actual Activity dialogs/input and the existing STOP touch path on the combined source. Five Cinema cancellation/scene methods remain byte-identical to the frozen Cinema input. Prior #720 results remain separately labelled upstream evidence.

Two retained build attempts ended with a disappearing Gradle daemon: the first during main Kotlin compilation, the second after main/test compilation at test start. No source change was made between attempts. The final bounded run passed using the completed outputs: Gradle 512 MiB and one test JVM 768 MiB, SerialGC/two CPUs, one Gradle worker. The precise termination cause of the first two processes was not established; no test was removed or skipped.

Evidence: `docs/evidence/support-cinema-dialog-integration-20261110/RESULT.json`, both aborted logs and final green log. The receipt pins source hashes, suite counts, compiled JARs, invocation and preservation checks. Main/test compilation used JDK21.0.13 and Gradle8.11.1 in an isolated checkout with a dedicated copied dependency cache and the existing exclusive checkout lease.

## Reproduction and remaining boundary

Use the `finalAttempt.args` and suite list from RESULT.json with local JDK/Android SDK paths. Replace the recorded scratch init-script path with `docs/evidence/support-cinema-dialog-integration-20261110/test-memory.gradle`; it preserves the exact test memory/CPU settings. The original init script additionally recorded the runtime classpath to scratch, which does not affect test behavior. Use a dedicated Gradle home and acquire the repository's existing checkout lease before execution.

This is targeted Android/JUnit/Robolectric host acceptance, not full repository QA or actual ASR/network/Gemma/Fold7 acceptance. Existing tests use synthetic recognized text and recorded consent callbacks. Native Canvas in the STOP test does not establish phone GPU/Filament behavior.

Next integration action belongs to the support root and #715 owner: review this source-bound candidate, incorporate it with other approved source changes, then verify the resulting exact source before any release decision. No newer speculative context/memory/research experiment was activated here.

Relevant boundaries: EXP-07 source-bound regression evidence and EXP-12 session invalidation. Existing ApprovedWebToolGate remains the sole permission authority; chat wording does not grant generic or destructive tool actions. No new store, scheduler, model loop or training.

No main/RC/signer modification, new signing run, APK publication or installation. Rollback is to discard this integration candidate or revert its integration commit before promotion.

## Frozen support continuation

The support root explicitly froze the Cinema product baseline at `5d36c5a57c031308c8de91049e19e9e8cb8365d5` for this integration run. Newly observed Cinema #715 `31f61c6f1ff24f116135824403f0a6a57721c787` includes separate material/pressure/ocular changes and remains marked WIP by its owner; those changes are outside this candidate. The target branch will be compared again before final incorporation.

- Prior verified support HEAD: `12115355b3d1e198f9c073cac87dc318003a4aa7`.
- Queued-result input #724: `e61458f4c46f7af118614d01e71060b73a0c5521`, local equivalent `6e9fc1b96ef92dfc3b31d562e20a508233ba643f`, identical tree `f33e36003af84a12d16e952c52b1d067266eb4fb`. Merged without conflict. Only its existing View lifecycle, tests and evidence are added.
- Authorized ASR input #720: `de831d06034c46c6ae039a6df32d52a4392a3521`, local equivalent `deaf1e4db9c96b3513bc0ffe45dc83290bbffd30`, tree `d6f6eac90d8c093035dbdd16035a45226f0087b8`. Included without conflict: recognition captures the original approval identity, rejects a changed-ticket result after existing generation/session checks, and preserves a newer recognition generation. Its API35 test shadow affects only host test setup.
- Final authorized outcome input #726: `d3185e72967832c7e0a5aee4a1b77ddad522caec`, local equivalent `94294881d312787da44b3bee52e2d6365fb40adb`, identical tree `492c11df8707c165d83b5df5ac495ed3134317ae`. All product changes and its evidence are included, retaining the original remote parent. The final root manifest's 14 source hashes matched before integration. Thirteen files remain byte-identical; AgentActivity contains both independent changes, with both complete diffs preserved after ignoring line-number offsets. Source audit: `docs/evidence/combined-support-20261110/source-audit.json`.

Upstream #724 has 46 scoped checks; upstream latest #720 has 91 ASR/approval checks. These counts overlap existing suites and are not summed or represented as a combined result. No Gradle build was started during the root's outcome compilation.

The final outcome source has 49/49 scoped checks upstream. It distinguishes response delivery from verified task outcome, clears unresolved approval/presentation state and preserves existing recovery lifecycle rules. The source merged without conflict. Next: separate bounded Android compilation and test processes, with the 27 deduplicated context/consent/ASR/queued-result/outcome/cancellation suites. The root released its build lease before this combined run.
