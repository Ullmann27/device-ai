# Jarvis55 connected cockpit integration

Integrator: `work/jarvis55-master-20261113`. Canonical owner APK line: PR #809, `integrate/code55-apk-fable-20261112`, targeting #799 → #791. No main merge.

## Source and status

The canonical source tested in run 43344572061 / 43344572298 is `41306257ce03c1e22e998877bf769ce42b3a0486` (tree `3a16a64f4d831ba51d54f24cbb952d850e8d433e`). It includes #811, Fable #808 mirror correction `472ef3fd6ceb137a10bbdd2090c0ae713a460bcf`, and the existing transform/uniform optimizations.

Subsequent root commits migrate the unsupported host native-PDF bridge test to the independently passing mandatory Android instrumentation and add the existing source-bound Node/backend checks to the completion gate. Their combined SHA still requires a fresh run.

| Evidence on 41306257 | Observed result |
| --- | --- |
| Scoped Android Kotlin/UI | PASS, job 131620672911 |
| ARM64 release APK build | PASS, job 131620672824 |
| Strict original-assets/package/16 KiB APK audit | PASS |
| Native Android PDF provider/render/text | PASS, API35 emulator, job 131620674180 |
| Complete Android JUnit/UI | Running when recorded; not accepted yet |
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
- Speech UI readiness fixtures now require the real ASR readiness callback. Explicit PAUSED is not automatically resumed.
- Existing workstation geometry now has chamfered surfaces and matched titanium/clearcoat/glass materials. The original avatar, orb and five rings remain; central pixel plane/UVs are unchanged.
- Existing tile pressure feedback restored. No extra renderer, cognition store, event bus, model-weight promotion or permission bypass.

The current contextual side text uses the existing Canvas foreground. It is not yet evidence of text mapped onto native Filament side surfaces. A narrow connection audit is ongoing; do not describe it as complete.

The phone's ChiefBotTeam remains the existing bounded, sequential ANALYZE → REVIEW model lane with zero tool rights. Three external development workers are available; that does not establish that three phone agents are running.

## Verification history and limits

Before the local runtime became unavailable, source-bound host checks passed: 65 attention/action cases, 12 panel/fidelity cases, 11 pressure-feedback cases, 39 team/worker cases and 37 speech cases. These are scoped receipts, not full device acceptance. Code and published evidence are in this Git history; local-only temporary runners/render files are not represented as uploaded.

Six host frames and a three-second clip were rendered from the tree corresponding to remote `ef767adf16ea555117a1043a4117129ff8ececdd`. Their recovery commands/hashes are in `JARVIS55-GRAPHICS-RECOVERY-20261113.md`; the files were not uploaded before the runtime failed. These previews use explicitly labelled example content and are not Android screenshots or device FPS measurements.

The first recovered full run on84268 failed at Kotlin test compilation and emulator AVD discovery. Those compile fixtures and the common ANDROID_AVD_HOME were corrected; the second scoped/native runs passed. Complete QA remains mandatory. No test-result filters, ignored failures or missing-native assumptions are used as acceptance.

## Next concrete work

1. Finish and inspect the unfiltered Android suite on41306257; repair remaining failures from actual evidence.
2. Review any ongoing stop-phrase/side-surface patch, integrate only reviewed source, freeze a combined SHA.
3. Fast-forward the canonical development branch without force; require full Android+lint, native PDF and source-bound Node/backend on that SHA.
4. Build and audit the same-source ARM64 APK. Use the existing original-signing approval route only; never replace the key/package or fabricate SHA-specific owner approval.
5. On authorized hardware: real capture/rotation/crop, action verification, owner interruption, Maya/ASR, Fold/phone layouts, frame-time percentiles and thermals. All remain NOT_RUN until observed.

EXP-00/05/06/07/08/11/12 compatibility maintained. Preserve all upstream work and branches. Rollback individual reviewed commits; do not reset or force-push shared branches.
