# Command03 responsive layout repair

Target: `integrate/continuum-command03-android-20261115` (PR #851), starting at
`920a9f72f42490e3b496a0687406d999a39c7bf6`. Repair owner: this change.

The old container rules position the right instrument in column 2 inside the
new nested wing grid. That creates an implicit column and collapses the RAM
host. Android-only CSS resets child placement, supplies one bounded track per
wing, stacks the narrowest layouts, keeps settings reachable, bounds horizontal
overflow and reserves space for the multiline composer.

All 35 original artwork/HTML/JS/CSS assets remain byte-identical. Existing native
routes, model, tool gates, APK identity, original signing and animation controllers
are untouched. EXP-15 presentation compatibility is preserved; no experiment or
model capability is being promoted.

Local checks: JS syntax and the original 35-asset source lock PASS. Chromium
launch in the local executor is unavailable (socket policy); this is not a layout
PASS. The existing scoped workflow now runs the browser/DOM job for this repair
branch, including real Chromium geometry and screenshots at 17 viewport/resize
states, multiline input and the instrument drawer. It also checks the unmodified
baseline for the collapsed RAM card. Results are pending at this checkpoint.

Next: inspect source-bound CI evidence, repair any remaining geometry failures,
merge the verified change into PR #851's integration line and use its existing
APK workflow. Physical Fold7 behavior remains unverified until owner testing.
Rollback: revert this change; no state or data migration is involved.
