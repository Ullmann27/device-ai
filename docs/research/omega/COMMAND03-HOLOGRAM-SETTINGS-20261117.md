# Command03 hologram and settings

Base: Code61, `5e995146354cc4f338fbff564b39b349b7aa74f5`.
Target: `integrate/continuum-command03-android-20261115` / PR #851.
Owner: this change. Status: WIP / UNVERIFIED.

Requested: cyan/violet neon glass, restrained light motion, visible settings
and direct buttons for existing configuration. Preserve the responsive repair
and the 35 original assets.

Finding: the settings route opens only personalisation; model, voice, network
and device controls are elsewhere. Expose the existing native handlers through
a grouped settings menu and named shortcuts. No automatic permission, model,
network or signing changes.

Verification pending: native navigation, setting persistence, DOM transport,
responsive Chromium screenshots and the existing APK build.
Rollback: revert this change; no stored-content migration.
