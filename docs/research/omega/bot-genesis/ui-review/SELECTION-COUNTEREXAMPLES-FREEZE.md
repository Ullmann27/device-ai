# Selection callbacks — independent execution freeze

Status: SOURCE_IMPLEMENTED / NOT_RUN. Broad selection/privacy/lifecycle criteria were frozen before candidate inspection in4780ffa0. Exact constructor and button contract were inspected in Chief81dd53b29ac66af47745f47d84c3ef1d7d1e5e7d before writing these API-bound tests. These are independent counterexamples, not blind intelligence holdouts.

Six actual-widget tests use a real on-disk `ChiefBotProfileStore`, 320dp Robolectric Activity, and actual `ContinuumBotRosterDialog`. They check custom21st identity/rename/rights preservation with explicit selection; at-most-once delivery from a single button; no stale old-window callback closing or selecting into a replacement; no post-dismiss callback; no post-destroy callback; and a profile-detail rename followed by selection retaining the saved stable ID/version. Status callback invocation records real profile IDs but does not simulate or prove a loaded model.

Source-review hypothesis: Chief81dd actions call `close(); select(profile)` without the existing `live(token)` guard. Retained/queued clicks can therefore select twice or close a newer dialog. The tests preserve this as an explicit assertion to execute after the native inference lane releases CPU. Production edits remain Chief-owned.

Separate caller finding awaiting executable integration tests: `onPause` resets `botLookupPending` while the queued/running storage operation can survive. Presentation generation invalidation and actual storage admission should remain separate; otherwise repeated resume/select can admit additional outstanding work. Existing roster storagePending is an example of that distinction, not a request for a second runtime.

All future acceptance must distinguish actual widget behavior, source inspection, fake platform TTS callbacks, genuine local inference, and Fold7 execution. The6 selection +3 speech checks remain NOT_RUN; the prior15 roster PASS stays tied to4b881f81 and its own immutable logs.
