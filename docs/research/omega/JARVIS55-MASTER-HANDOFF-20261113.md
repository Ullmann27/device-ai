# Jarvis55 / BLACKSITE OMEGA integration

Integrator: `work/jarvis55-master-20261113`, PR #816. Canonical owner APK line: PR #809, `integrate/code55-apk-fable-20261112`, targeting #799 → #791. No main merge.

## Current source and acceptance

Reviewed implementation source before this record: `2c02cad5f3533388dd4a457d895dfceeefcc59bb`. It includes #811, Fable #808 mirror correction `472ef3fd6ceb137a10bbdd2090c0ae713a460bcf`, existing transform/uniform optimizations and the BLACKSITE asset/evidence branch #822. Canonical source remains `41306257ce03c1e22e998877bf769ce42b3a0486` until the guarded integration fast-forward.

| Source-bound execution | Actual result |
| --- | --- |
| Targeted Android/UI, e06fb3c0, run43352301870 | 317 passed, zero failures/errors/skips |
| Targeted Android/UI, 02c38cc0, run43353510013 | 323 passed, zero failures/errors/skips; thermal admission races included |
| Targeted Android/UI, bfef9db3, run43354998293 | 344 passed, 55 suites, zero failures/errors/skips; manual measurement lifecycle included |
| Complete Node, d1938053, run43356537474 | 2,498 total: 2,461 passed, zero failures, 37 reported skips; source unchanged |
| Critical Node, same source/run | 137 passed; subset, not additional to full Node |
| Backend, same source/run | 64 passed, zero failures/skips; type/check passed |
| Strict BLACKSITE geometry, six mutation cases, APK validator and five JUnit guard tests | Passed in Node job131659794565 |
| Native PDF + Filament, 2c02cad5, run43357310925 | PASS; actual two surfaces/materials/textures, render/upload/clear/release |
| Complete unfiltered Android/UI+lint on combined source | REQUIRED; not replaced by scoped runs |
| Fold7 live capture, audio, GPU, thermals | NOT_RUN |
| Original-signed new APK / installation | NOT_RUN |

Receipts: `docs/evidence/jarvis55-targeted-e06fb3c0/`, `jarvis55-targeted-02c38cc/`, `jarvis55-targeted-bfef9db3/`, `jarvis55-node-d1938053/`. Native receipt: `docs/evidence/jarvis55-native-2c02cad5/source.json`. The first two native preflight failures remain documented separately.

The older canonical41306257 ARM64 APK passed assembly and strict original-assets/package/16KiB audit in run43344572061. It is **UNSIGNED_NOT_INSTALLABLE** and does not contain the newer BLACKSITE/repair work. APK SHA256 `bade04a6d8dd7fd4f0366a8abceed39bf80272b4546b71261575680e0f3a3ebf`; [artifact14000694515](https://github.com/Ullmann27/device-ai/actions/runs/43344572061/artifacts/14000694515). Package remains `at.heinz.deviceai.continuumlocal`, versionCode55/versionName0.3.54.

## Connected behavior

- Real task/session/observation revision and the actual submitted capture binding drive existing CockpitWorkReducer → ComputerPanelState → CockpitAnimationBus/ComputerWorkAvatarBridge. Crop, letterboxing, monitor projection and the laid-out head determine work gaze. Missing, stale or foreign pixels never authorize an action or manufacture a gaze target.
- Listening, speaking, approval and blocked states preempt work. STOP retires cues and revokes/cancels work before fallible persistence. Render refreshes cannot renew the bounded work lease. Verified step and verified whole task remain distinct.
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
| 2 compact layout | Existing-layout compactPresentation repair integrated1fbe. Pure layout cases pass; two unchanged full Activity cases still require full QA. |
| 4 visual timing | Assertions use real140ms anchor hold, flight/data phases and retained settled/close geometry. Production timing unchanged; full UI rerun required. |

Historical17 explicit JUnit skips are catalogued; any new skip fails strict validation. Existing fold7InnerMotionClip now receives its evidence directory so it executes instead of being the18th skip. Full Android job has no case filters; targeted harness is separate. Mandatory aggregate requires Node/backend, unfiltered Android/lint and native PDF/Filament success.

Native PDF has passed on API35x86_64 using real production PdfDocumentIntake: provider query1/open1, page1,506 dark pixels, extracted synthetic marker/page attribution. That is not an owner Argon4 task. First Filament attempt43354416362 compiled and passed PDF, then the host SwiftShader emulator crashed without a completed probe. Second43356537474 failed before boot because a diagnostic version query selected a GUI binary missing libpulse. Current2c02 uses headless version query and official swangle emulator backend. Actual run43357310925/job131662313877 passed PDF and Filament: two quads/materials/textures, aggregate upload callback1, rendered frames2, presented1, output2, clear/panel-release/engine-release true, instrumentation code-1. Production Filament still uses OPENGL. Phase diagnostics, timeout/logcat capture and all render/upload/clear/release assertions are retained; no failure is converted to pass.

## Release and next executable steps

1. Native preflight passed. Freeze the reviewed common source and guarded-fast-forward #809. Run full Node/backend, unfiltered Android/UI+lint, native PDF/Filament and unsigned ARM64 assembly/audit on that exact source.
2. Resolve any remaining full-suite failures. Publish exact SHA, actual counts, APK hash and artifact link; neither a partial suite nor an unsigned APK establishes installable readiness.
3. Use the existing original signing route only with its exact-source approval and protected reviewer. Existing registered historical Code50 signing workflow is currently Code51-pinned and cannot be blindly dispatched forCode55. Never replace key/package or synthesize approval.
4. Authorized Fold7 acceptance still needs real capture/rotation/crop, document task and interruption, Maya/ASR lifecycle, folding/layout, frame-time median/p95/jank, memory/power and thermal phases. Reuse existing diagnosis/performance tools; report unavailable measurements explicitly.

No main merge, branch reset/force-push, extra renderer, parallel memory store, secret access or weight promotion. Preserve all upstream work. Roll back individual reviewed commits if required.
