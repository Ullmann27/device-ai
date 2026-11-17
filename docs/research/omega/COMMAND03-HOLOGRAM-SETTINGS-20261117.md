# Command03 hologram and settings

Base: Code61, `5e995146354cc4f338fbff564b39b349b7aa74f5`.
Target: `integrate/continuum-command03-android-20261115` / PR #851.
Owner: PR #859. Status: implemented; native verification rerun pending.

Requested: cyan/violet neon glass, restrained light motion, visible settings
and direct buttons for existing configuration. Preserve the responsive repair
and the 35 original assets.

Finding: the settings route opens only personalisation; model, voice, network
and device controls are elsewhere. Expose the existing native handlers through
a grouped settings menu and named shortcuts. No automatic permission, model,
network or signing changes.

Local checks: 49/49 DOM tests, JS syntax, workflow YAML/shell and all 35
original assets PASS. Added a 21-entry searchable native settings hub, four
scoped shortcuts, persistent neon controls and cyan/violet light layers.
Choice dialogs now keep their lists visible alongside explanatory text.

CI 44034378404 at `0e9e2688`: 49/49 DOM and 19/19 Chromium layout cases PASS;
appearance preference, reduced motion and unobstructed settings hit targets PASS.
Screenshots at 320, 412 and 834 px reviewed. Native: 13/14 PASS; final assertion
ran before Android's queued dismissal callback. The test now drains that callback
and separately proves an unknown route creates no dialog. Small-screen shortcut
labels increased to 11 px after visual review.

Pending: rerun native and rendered checks, then the existing Code62 APK workflow.
No experiment interface,
data schema or model behavior change. Physical Fold7 testing is still open.
Rollback: revert this change; no stored-content migration.
