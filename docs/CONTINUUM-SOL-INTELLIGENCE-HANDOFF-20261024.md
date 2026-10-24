# Sol interruption checkpoint / superseded by master 0.3.20

Base f65181fe87244071ddb3286eccd6f11a23da2e52; public preservation branch sol/continuum-hologram-intelligence-20261024. This checkpoint preserves all unfinished local changes; it is NOT a release or approved merge candidate.

Conversation binding: one existing dialogue snapshot gains an optional validated conversation ID; atomic core reply/context/evidence handoff avoids cross-session local prompts. SDK-classpath Kotlin/JUnit targeted tests: 41 passed, exit 0. Red regressions reproduced cross-ID legacy-history borrowing and revival of stale topic after an unanswered turn.

Hologram work: frame-clock advancement, host lifecycle, larger float/light pulse; actual Gradle/Robolectric tests still FAILED (11 executed, 3 failed) because animation did not resume/progress in attached views. No visual/device-success claim. Later PR113 already includes further fixes including removal of cached visibility; do not overwrite them with this older implementation. Existing installed APK is unaffected by branch changes.

Changed files are enumerated by git show --stat of this checkpoint. No memory weights, owner keys, source pins, release services, main or production changed. Experimental CTX3/CRE1/CDX1 activation unchanged.

Continue from current PR115 (verified 9794761067e6bd1f8f2188426288f8134de53e4e). Create a descendant branch, read AGENTS.md/experiment contract/register, preserve its newer animation/session-topic fixes, carry only the missing durable conversation-binding implementation after reproduction against current source. Re-run real SDK tests and static contracts; do not treat this old checkpoint as device-verified or release-ready. Full 0.3.20 continuation report will be on that new branch.
