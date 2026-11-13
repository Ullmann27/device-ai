# Jarvis55 / BLACKSITE OMEGA integration

Integrator: `work/jarvis55-master-20261113`. Canonical owner APK line: PR #809, `integrate/code55-apk-fable-20261112`, targeting #799 → #791. No main merge.

## Source and status

The canonical source tested in run 43344572061 / 43344572298 is `41306257ce03c1e22e998877bf769ce42b3a0486` (tree `3a16a64f4d831ba51d54f24cbb952d850e8d433e`). It includes #811, Fable #808 mirror correction `472ef3fd6ceb137a10bbdd2090c0ae713a460bcf`, and the existing transform/uniform optimizations.

Current reviewed integration before this record: `c02292f43ebc072db304f3f8ba558c88ae72796f`. Subsequent commits migrate the unsupported host native-PDF bridge test to the independently passing mandatory Android instrumentation, connect contextual text to the existing Filament side surfaces, repair owner interruption and replacement ASR readiness, filter experience eligibility before retrieval limits, and enforce the existing shared learning pause/thermal budget. The combined SHA requires a fresh unfiltered run.

| Evidence on 41306257 | Observed result |
| --- | --- |
| Scoped Android Kotlin/UI | PASS, job 131620672911 |
| ARM64 release APK build | PASS, job 131620672824 |
| Strict original-assets/package/16 KiB APK audit | PASS |
| Native Android PDF provider/render/text | PASS, API35 emulator, job 131620674180 |
| Complete Android JUnit/UI | FAIL: 5,321 tests, 18 failures, 18 skipped; job 131620673839 |
| Configured Android lint | PASS, same job |
| New-source full Node/backend | Pending integration run |
| Fold7 live capture, audio, GPU, thermals | NOT_RUN |

APK SHA256: `bade04a6d8dd7fd4f0366a8abceed39bf80272b4546b71261575680e0f3a3ebf`. Package `at.heinz.deviceai.continuumlocal`, versionCode55/versionName0.3.54. [Artifact 14000694515](https://github.com/Ullmann27/device-ai/actions/runs/43344572061/artifacts/14000694515) is **UNSIGNED_NOT_INSTALLABLE**; original signer was not used.

Native PDF proof: real production PdfDocumentIntake, provider queries1/opens1, page1, rendered dark pixels506, extracted synthetic marker and page attribution. Exact source bindings and assertion migration are in `docs/evidence/jarvis55-native-pdf-20261113/` and `JARVIS55-NATIVE-PDF-GATE-MIGRATION-20261113.md`. This is synthetic-document Android instrumentation, not a completed owner Argon4 task.

## Integrated behavior

- Geometry-bound work attention uses the actually submitted capture binding, image crop/letterboxing and projected monitor frame relative to the laid-out head. Missing/stale/foreign task/session/revision pixels cannot produce a work target.
- Listening, speaking, approval and blocked states preempt computer gaze. STOP retires pending input cues; actual accepted work events can resume attention. Render refreshes do not renew the bounded work lease.
- Controller → existing CockpitWorkReducer → ComputerPanelState → existing dashboard/side-panel path carries actual app, goal, stage, action and verification state. Verified step and verified whole task are distinct.
- Panel restoration revalidates current task, package, capture session and active source. Stop/revocation clears retention; queued stale updates and Fold/Home rebuilds cannot resurrect old panels.
- Android actions require the exact rendered observation revision. Frames crossing revisions are rejected and binding is rechecked before dispatch.
- Team stop revokes admission/cancels work before fallible persistence, preventing continued inference or late responses after storage errors.
- Replacement ASR clears the old readiness flag before starting the new recognition scope. Real current ASR readiness still controls the indicator; stale callbacks cannot re-arm it. Explicit PAUSED is not automatically resumed.
- A direct owner correction such as “Stopp, das meine ich nicht” cancels current work and cues, keeps an unsent draft, and asks one short clarification. The existing active conversation consent is preserved; PAUSED/OFF sessions are never reactivated. Negated/quoted/reported stop text does not authorize cancellation.
- Verified-experience eligibility is applied before memory ranking/64-candidate limiting and rechecked afterwards. Newer PARTIAL dialogue records cannot crowd out eligible older experience. Existing provenance, zero-confidence historical hints, two success/two failure bounds and signed archive continuity remain.
- Existing 273-weight classifier practice now checks opt-in, global autonomy pause, cancellation and the shared resource/deadline budget at entry, update boundaries and before saving. Cancellation stays latched for the run. This is not Gemma training, and no model weights were promoted by this work.
- Existing workstation geometry now has chamfered surfaces and matched titanium/clearcoat/glass materials. The original avatar, orb and five rings remain; central pixel plane/UVs are unchanged.
- Existing tile pressure feedback restored. No extra renderer, cognition store, event bus, model-weight promotion or permission bypass.

Native contextual panel source is now connected through `CockpitPanelContent` → `FilamentCockpitStage.CockpitContextPanels`, using two instances of the existing verified document plane, independent material instances and reusable textures in the same Scene. Actual rows are rasterized only when their visible content/density/font key changes. A newer observation is acknowledged only after the corresponding native frame submission; stale callbacks cannot replace current content. Canvas remains until native presentation is confirmed or side panels are too small. Kotlin/native device execution of this new connection is still NOT_RUN; this is source integration, not a measured GPU result.

The phone's ChiefBotTeam remains the existing bounded, sequential ANALYZE → REVIEW model lane with zero tool rights. Three external development workers are available; that does not establish that three phone agents are running.

## Verification history and limits

Before the local runtime became unavailable, source-bound host checks passed: 65 attention/action cases, 12 panel/fidelity cases, 11 pressure-feedback cases, 39 team/worker cases and 37 speech cases. These are scoped receipts, not full device acceptance. Code and published evidence are in this Git history; local-only temporary runners/render files are not represented as uploaded.

Six host frames and a three-second clip were rendered from the tree corresponding to remote `ef767adf16ea555117a1043a4117129ff8ececdd`. Their recovery commands/hashes are in `JARVIS55-GRAPHICS-RECOVERY-20261113.md`; the files were not uploaded before the runtime failed. These previews use explicitly labelled example content and are not Android screenshots or device FPS measurements.

The first recovered full run on84268 failed at Kotlin test compilation and emulator AVD discovery. Those compile fixtures and the common ANDROID_AVD_HOME were corrected; the second scoped/native runs passed. Complete QA remains mandatory. No test-result filters, ignored failures or missing-native assumptions are used as acceptance.

## Full QA regression closure

All 18 XML failures were independently extracted without rerunning tests in [diagnostic run 43350986680](https://github.com/Ullmann27/device-ai/actions/runs/43350986680). Its download verified artifact 14004060039 SHA256 `516a90e1925d1ae443a49d9b15348139dbd72535a4aa4487d3ff229423573366` and exact source41306257. Raw bounded output and source/result manifest: `docs/evidence/jarvis55-full-qa-41306257/`.

| Cases | Source diagnosis and integration |
| --- | --- |
| 3 input + 7 scroll | Old fixtures omitted the mandatory observation revision. Initial observation and frozen matching render receipt now supplied; all 22 original methods/71 assertions retained, four final-dispatch negatives added. Integrated5dbe7625 via c02292f4; rerun pending. |
| 1 native PDF host bridge | Unsupported host FileDescriptor method. Equivalent and stronger native instrumentation already passed; host duplicate migrated, negative/pure tests retained, native lane mandatory. |
| 1 replacement ASR | Old readiness retained before new READY. Two-line presentation reset and stale callback regression integrated6644a72a. |
| 2 compact layout | Font1.5 switched workstation to taller legacy reflow. Existing-layout repair in progress. |
| 4 visual timing | Actual anchor holds flight at zero at80ms. Preserve anchor checks and verify flight/data in their real phases; repair in progress. |

## Next concrete work

1. Integrate reviewed compact/timeline corrections; run unfiltered Android+lint, native PDF and source-bound Node/backend on a frozen canonical SHA.
2. Continue existing workstation BLACKSITE geometry: batched real architectural meshes under the current room, original avatar/orb/monitor UV preservation, pinned generator and real host comparison evidence. Graphics worker owns this isolated generation step; no new renderer.
3. Build and strictly audit the same-source ARM64 APK. Use existing original-signing approval route; never replace key/package or fabricate SHA-specific approval.
4. On authorized Fold7: capture/rotation/crop, actual task and owner interruption, Maya/ASR, layouts, frame-time percentiles and thermal phases. All hardware results remain NOT_RUN.

EXP-00/05/06/07/08/11/12 compatibility maintained. Preserve all upstream work and branches. Rollback individual reviewed commits; do not reset or force-push shared branches.
