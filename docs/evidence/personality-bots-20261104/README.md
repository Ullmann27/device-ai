# Personality bots: evidence index

Source checkpoint: `dbb8e2f7085b0140ed437502b27fac5f988ac834`.
Base: `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`.
Draft integration PR: <https://github.com/Ullmann27/device-ai/pull/493>.

## Final observed gates

| Gate | Result | Evidence |
| --- | --- | --- |
| Focused bot/profile/worker/widget/Activity suite | 114 tests, 114 passed, 0 skipped, 0 failures/errors | [Focused GREEN](focused-green/README.md) |
| Regular complete unit suite | 2,402 tests: 2,383 passed, 19 skipped, 0 failures/errors; 427 classes | [Full verification](full-verification/README.md) |
| Configured Android API lint | 0 NewApi issues; Gradle exit 0 | [Lint XML](full-verification/lint-results-debug.xml) |
| Source agreement | All 1,009 application/build/test inputs match the published Git tree | [Source comparison](published-source-verification.json) |
| Independent source review | No concrete important/blocking issue identified in scoped source | [Review](independent-review.md) |
| Native host widget renders | Phone, wide layout, font scale 2 and reduced-height checks passed; 10 PNGs | [Renders and limits](widget-renders/README.md) |
| Textual integration preview | Clean with pinned avatar and combined memory/avatar heads; no actual branch merge | [Preview](integration-preview.json) |

The focused run includes 31 Android-widget/Activity cases. Its unit tests overlap
with the full suite: these totals must not be added as distinct cases. This
feature adds 52 tests (31 unit and 21 widget/Activity) to the inspected base.

The real Activity/ToolGate tests use controlled local inference. They check a
ready-model task, the full request including its late internet prohibition,
exclusion of private bot/owner context, four actual model calls including
recovery, refusal of web dispatch, profile/session changes and retention of the
busy lane until simulated native generation actually ends.

## Recorded limits

The broad all-unit/all-widget invocation was interrupted after more than nine
minutes without visible progress and produced no final summary. Its cause was
not established. It is **UNVERIFIED**, not a passing gate; see
[the interrupted attempt](all-widget-suite-interrupted/README.md).

Physical Fold7 operation, real native LiteRT inference/quality, GPU, speech,
keyboard/insets, signed APK installation and the combined multi-branch release
remain **NOT_RUN**. The shared runtime still serves the bot personalities; no
independent model instances, new verified skills or model-weight improvements
are claimed.

## Development history

Earlier actual RED and intermediate runs remain in `baseline`, `personas-red`,
`task-api-red`, `ui-red-personas-green`, `address-red-task-green`, `activity-red`
and `focused-first-run`. Their failures are labelled; historical results are
not represented as final acceptance. The final source manifests and raw reports
bind the actual files and observations used for this delivery.
