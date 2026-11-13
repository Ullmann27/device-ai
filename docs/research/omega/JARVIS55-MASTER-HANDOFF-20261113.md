# Jarvis55 / BLACKSITE OMEGA integration

Integrator: `work/jarvis55-master-20261113`, continuation PR #825 (original PR #816 integrated). Canonical owner APK line: PR #809, `integrate/code55-apk-fable-20261112`, targeting #799 → #791. No main merge.

## Current source and acceptance

Canonical source `058d754b82e134b3eec0e3e3f21b788c46c8c479` includes #811, Fable #808 mirror correction `472ef3fd6ceb137a10bbdd2090c0ae713a460bcf`, transform/uniform optimizations and BLACKSITE asset/evidence branch #822. Subsequent reviewed preparation adds original-signing compatibility (8ff22273 → a02ace57), visibility feedback (49a14458 → f505f973), closing geometry assertions (10e33eb9) and the completion-fixture event (cbdf8673). Final exact-source QA is still required; this record does not assert its outcome.

| Source-bound execution | Actual result |
| --- | --- |
| Targeted Android/UI, e06fb3c0, run43352301870 | 317 passed, zero failures/errors/skips |
| Targeted Android/UI, 02c38cc0, run43353510013 | 323 passed, zero failures/errors/skips |
| Targeted Android/UI, bfef9db3, run43354998293 | 344 passed, 55 suites, zero failures/errors/skips |
| Full unfiltered Android/UI, 058d754b, run43358315168 | 5,374 cases: 5,354 passed, 3 closing-geometry assertion failures, 17 documented skips, zero unexpected skips; lint PASS; overall FAIL |
| Complete Node, 058d754b, same run | 2,498 total: 2,461 passed, zero failures, 37 explicit skips; source unchanged |
| Critical Node / backend, same source/run | 137 critical (subset of full Node); 64 backend passed, zero failures/skips |
| Native PDF + Filament, same source/run | PASS; actual upload/render/clear/release on API35 software emulator |
| Scoped Android / ARM64 assembly, 058d754b, run43358315144 | 481 cases / 65 suites, zero failures/errors/skips; unsigned APK original-assets/package/16KiB audit PASS |
| Signer preparation, a02ace57, run43362097107 | Full Node 2,474 passed / 37 existing skips; backend64 passed; includes 18 functional preflight tests with mocked service/tool boundaries |
| Visibility integration, f505f973, run43364020806 | Android371 passed / 1 incomplete-fixture failure / 0 skips; Node2473 passed / 1 outdated method-name contract / 37 skips |
| Corrected combined source | Focused rerun and final complete QA/APK still required |
| Fold7 capture/action/audio/GPU/thermals | NOT_RUN |
| Original-signed new APK / installation | NOT_RUN |

The three full058 failures came from one shared assertion requiring scaleX<1 on return to the source. Digest-pinned diagnostic43364305019 proves the source bars are wider on Fold/tablet (anchor scaleX1.33125/2.6809268). The replacement asserts actual closing phase, faded data, frame visibility, source-bound width/height/horizontal translation and measurable motion. Protected regions and final cleanup remain asserted. Production geometry and timing are unchanged. Receipt: `../../evidence/jarvis55-full-058d754b/source.json`.

The new completion fixture initially omitted an accessibility content-change event, leaving revision0; the actual workflow rejected it. It now emits the real service event before capture and asserts a positive observed revision, retaining all terminal-result and cleanup assertions. The Node lifecycle contract now follows stopForVisibilityLoss and explicitly checks consent exemption, cleanup before notification, completed-work exclusion and a silent reply. These corrections do not waive the final full run.

Existing receipts remain in `docs/evidence/jarvis55-targeted-*/`, `jarvis55-node-d1938053/`, `jarvis55-native-2c02cad5/` and `jarvis55-code55-058d754b/`. Do not add overlapping suite totals.

The actual058 ARM64 APK is **UNSIGNED_NOT_INSTALLABLE**: SHA256 `99eee7b63887d40154c5d7f40723e5784d786246ccb3a5ce12e7a4d7e82df5ef`, [artifact14007177702](https://github.com/Ullmann27/device-ai/actions/runs/43358315144/artifacts/14007177702). Package remains `at.heinz.deviceai.continuumlocal`, versionCode55/versionName0.3.54. It is not the final corrected candidate.

## Connected behavior

- Real task/session/observation revision and the actual submitted capture binding drive existing CockpitWorkReducer → ComputerPanelState → CockpitAnimationBus/ComputerWorkAvatarBridge. Crop, letterboxing, monitor projection and the laid-out head determine work gaze. Missing, stale or foreign pixels never authorize an action or manufacture a gaze target.
- Listening, speaking, approval and blocked states preempt work. STOP retires cues and revokes/cancels work before fallible persistence. Render refreshes cannot renew the bounded work lease. Verified step and verified whole task remain distinct.
- The live computer requires Continuum and the target app visibly side by side in valid non-overlapping windows on the standard display. Fullscreen visibility loss revokes/cancels work and ends capture. Pending Android consent remains exempt. New silent transcript feedback explains interrupted work once; already completed work keeps its original result while retained capture closes. Owner STOP/correction and explicit voice PAUSED remain distinct. Software frame submission is not proof of physical display presentation.
- Panel restoration revalidates task, package, capture session and active source. Stop/revocation clears retention; delayed updates and Fold/Home rebuilds cannot resurrect stale work.
- Native context panels use actual planning/result rows and actual running-agent state. `CockpitPanelContent` feeds two existing document-plane instances in the same Filament Scene, with separate material instances and reusable bounded textures. Visible-content/density/font keys avoid unchanged uploads; newer observations require actual frame submission. Canvas remains until native presentation is confirmed or side planes are unreadably small.
- Replacement ASR clears the old readiness flag before starting the new recognition scope. Only current Android READY lights the indicator. Stale callbacks cannot re-arm it, and explicit PAUSED never auto-resumes.
- “Stopp, das meine ich nicht” cancels current work/cues/speech, preserves an unsent draft and requests one short clarification. Existing conversation consent survives; OFF/PAUSED stays off/paused. Negated, quoted, reported and conditional stop text is excluded.
- Verified-experience eligibility is applied before memory ranking/64-candidate limiting and checked again after lookup. Recent PARTIAL dialogue cannot displace older eligible experience. Existing provenance, zero-confidence historical hints, two-success/two-failure bounds and signed archive continuity remain. Legacy lookup providers without the optional prefilter retain their bounded compatibility behavior.
- Existing 273-weight classifier practice checks opt-in, global pause, cancellation and shared resource/deadline budgets at entry, update boundaries and before save. Cancellation remains latched. This is not Gemma training; no experimental model weights were promoted.
- LiteRT admission retains ownership through native cancellation and suppresses late or duplicate callbacks after thermal stop. A stale cooler sample cannot erase a heat stop, and an old cancel cannot cancel the next generation. Callbacks run outside the guard lock; thresholds and no-auto-retry behavior are unchanged.
- Systemdiagnose now connects the existing ThermalPhaseRecorder through an explicit “NEUE MESSREIHE STARTEN” action and actual successful fresh diagnosis results. Only reported thermal status/battery temperature is recorded; unknown GPU/CPU temperature, headroom and model timings remain unknown. Stop/cancel/onPause/capacity end the series; there is no extra sampler, timer, scheduler or inference call.
- Compact font-scaled layout retains the existing workstation composition instead of switching to a taller legacy reflow. Eight navigation routes, input/status/dialog/header accessibility remain. Small monitors are hidden rather than made unreadable.

The phone ChiefBotTeam remains bounded chief plus two sequential text specialists, with empty tool rights in that lane. Three external development workers actually contributed source/review; this is not evidence that three phone workers are simultaneously running. Detailed actual model, scheduler, memory and coding paths: `BLACKSITE-MODEL-ANDROID-EXECUTION-INVENTORY-20261113.md`. Gemma LoRA/GPU/NPU/latency or months-long autonomous improvement are not measured by this milestone.

## BLACKSITE scene and visual evidence

Five static opaque PBR batches under the existing `cockpit-room` add concrete, titanium structures/bulkhead, conduits and cyan/violet light channels: 16,836 vertices, 8,668 triangles. Workstation GLB645,592bytes; SHA256 `1a958f2b9fcfb4e6dca19fceb9424c1ad920be35e471646e45ecde7e9b031712`. Original avatar, orb/five rings, seed, central pixel plane/UVs and document asset pins remain unchanged. Fable environment is byte-identical to472ef3fd; mirror=true remains.

Asset/evidence branch final `a6073f08078a4a894bc69dfe3aa51885a56a137c` is integrated. `docs/evidence/blacksite-room-20261113/` contains six stills, three-second60-frame MP4, decoded15-frame contact sheet, geometry/occlusion checks and exact-source/toolchain manifest. Recovery run43355720723 verified the original artifact digest and regenerated identical production assets before committing the files; render provenance was not rewritten.

Original render input95df8a74/local asset commit05ece592 is preserved in the manifest. This is component evidence, not a complete preview of later Activity/compact-layout changes. Host export20FPS is an encoding choice, not device performance. Content is explicitly synthetic example PDF/panel data; native contextual-panel connection was not exercised by these renders. Root inspected the decoded contact sheet and phone-working image. Geometry/parallax and layered room structure are visible; very dark concrete and CPU sampling noise remain visual limitations. Finished Hollywood quality is not claimed.

## Full regression closure and native boundary

The previous full Android run43344572298 on41306257 had5,321 tests,18 failures,18 skips; configured lint passed. All18 XML failures were independently extracted in diagnostic43350986680 with exact artifact digest/source verification.

| Previous failures | Reviewed change |
| --- | --- |
| 3 input + 7 scroll | Fixtures now bind initial observation and render receipt to the required revision. All22 original methods/71 assertions retained; four final-dispatch negatives added. Actual targeted pass. |
| 1 native PDF host bridge | Unsupported host FileDescriptor method migrated to the independently passing mandatory Android instrumentation. Host negatives/pure tests retained. |
| 1 replacement ASR | Readiness reset plus current/stale callback regression. Actual targeted pass. |
| 2 compact layout | Existing-layout compactPresentation repair integrated1fbe. Both unchanged full Activity cases pass in full058. |
| 4 visual timing | Real140ms anchor, flight/data and settled checks pass in full058. Three broad-layout cases then exposed the separate incorrect closing-width assumption above. Production timing unchanged. |

Historical17 explicit JUnit skips are catalogued; any new skip fails strict validation. Existing fold7InnerMotionClip now receives its evidence directory so it executes instead of being the18th skip. Full Android job has no case filters; targeted harness is separate. Mandatory aggregate requires Node/backend, unfiltered Android/lint and native PDF/Filament success.

Native PDF has passed on API35x86_64 using real production PdfDocumentIntake: provider query1/open1, page1,506 dark pixels, extracted synthetic marker/page attribution. That is not an owner Argon4 task. First Filament attempt43354416362 compiled and passed PDF, then the host SwiftShader emulator crashed without a completed probe. Second43356537474 failed before boot because a diagnostic version query selected a GUI binary missing libpulse. Current2c02 and058 use headless version query and official swangle emulator backend. Actual run43357310925/job131662313877 passed PDF and Filament: two quads/materials/textures, aggregate upload callback1, rendered frames2, presented1, output2, clear/panel-release/engine-release true, instrumentation code-1. Production Filament still uses OPENGL. Phase diagnostics, timeout/logcat capture and all render/upload/clear/release assertions are retained; no failure is converted to pass.

## Objective quality contract

Reuse the existing BenchmarkTask / TaskMeasurement / BenchmarkReport and task/observation identities. Do not create a second benchmark or memory architecture.

| Dimension | Independent observation and denominator | Current baseline |
| --- | --- | --- |
| Memory / temporal retrieval | Correct source-and-validity-labelled answers / all held-out questions; recall@k over independently labelled relevant records; corrections/revocations and distractors included | Host regressions verified; device benchmark NOT_RUN |
| Evidence / hallucination | Unsupported checkable claims / all checkable claims, plus verified task success / attempted tasks; model text alone never counts as evidence | Device benchmark NOT_RUN |
| Reasoning / repair | Correct final answers and successful recovery / fixed held-out tasks; deterministic arithmetic and contradictory-tool negatives separate | Device benchmark NOT_RUN |
| Agent work / continuity | Independently verified results / dispatched tasks; unauthorized or repeated external actions counted separately, including restart/STOP tests | Host regression evidence only |
| Voice | Monotonic ASR-ready, final recognition, response and audible onset timestamps; median/p95 and interruptions | Fold7/Maya NOT_RUN |
| Rendering | Frame pacing/jank, CPU submission and GPU durations separately; median/p95 with actual device/source/camera/workload | Native functional emulator proof; Fold7 performance NOT_RUN |
| Resources | Peak/steady memory, energy and Android thermal/battery readings for idle, speech, inference, capture and combined phases | Manual diagnostic recording connected; sustained device baseline NOT_RUN |

Keep held-out questions, corrections and adversarial cases out of training, retrieval-index construction and candidate selection. Compare the same source/model SHA, inputs, context, display settings and thermal start; report sample counts, failures and unavailable values. Unit-test pass counts are not accuracy/hallucination-rate benchmarks. The 60FPS goal and months-long learning benefit remain unmeasured.

## Release and next executable steps

1. Complete the focused combined-source regressions. Guarded-fast-forward the canonical #809 development line only after reviewed fixes pass. Run all mandatory Node/backend, unfiltered Android/UI+lint, native PDF/Filament and ARM64 assembly/audit again on the exact final SHA.
2. Resolve failures without new skips or reduced full-suite coverage. Keep the final release source frozen; record post-freeze receipts in PR metadata or a separate evidence branch.
3. Original signing uses the existing registered workflow `.github/workflows/continuum-code50-sign-release.yml`. Its Code51 path remains intact; reviewed Code55 support accepts only the canonical branch and independent full-QA/APK run IDs from the exact source and attempt. It checks artifact digests/sizes/paths, full JUnit/Node/native evidence, original assets/package, actual APK hash/16KiB alignment and the pinned original-signed predecessor before key use. Functional preflight tests are not an actual signing run.
4. Exact-source owner approval and the existing protected reviewer/environment remain required for original signing **and publication as the latest GitHub release**. Environment policies were not changed here. No key was read, no original signing or release publication occurred, and approval must never be synthesized.
5. Authorized Fold7 acceptance still needs real capture/rotation/crop, document task/interruption, Maya/ASR, folding/layout and controlled performance/thermal phases. Reuse existing tools and report unavailable measurements.

No main merge, branch reset/force-push, extra renderer, parallel memory store, permission bypass or weight promotion. All upstream work remains preserved.
