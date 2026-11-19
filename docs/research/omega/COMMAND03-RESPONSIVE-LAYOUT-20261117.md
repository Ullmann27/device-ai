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
are untouched. No experiment interface, data schema or model behavior changes.

Verified source: `32a8ed7807e22608800708ee119d07c4e322e8d9`.
[CI run 44004639205](https://github.com/Ullmann27/device-ai/actions/runs/44004639205)
completed successfully: 35 original assets unchanged, 45/45 DOM tests and 19/19
Chromium layout checks. Chromium 151.0.7922.34 ran 17 viewport/resize states from
320 to 1280 CSS pixels, multiline input, bottom scrolling and the instrument drawer.
The original baseline reproduced horizontal overflow, the collapsed RAM card,
hidden settings and an offscreen composer. At 412 CSS pixels the RAM card changes
from 8 to 186 pixels; page width stays 412 rather than expanding to 548 pixels.
The 412-pixel, 834-pixel and multiline screenshots were visually inspected.

Evidence: artifact `Code60-Command03-DOM-evidence`, ID `14293189798`, containing
the DOM report, layout-results.json and before/after screenshots. The later
Code61 packaging/documentation commit does not change the tested app assets.

Delivery: PR #858 targets PR #851's integration line. Its existing APK workflow
builds Code61 (versionCode 61, versionName 0.3.60) with the established package
and original certificate. Physical Fold7 behavior remains unverified until
owner testing; browser emulation is not an Android hardware test.
Rollback: revert this change; no state or data migration is involved.
