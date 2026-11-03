# Continuum 30-step checkpoint – copy into the assigned lane's reports directory

Use one new report per completed or interrupted work cycle, e.g. `docs/codex-team/reports/03/cycle-0001.md`. Never edit another lane's report. Chief alone integrates the architecture register. Do not record simulated commands as executed.

- Lane ID / Codex task ID:
- Report UTC timestamp:
- Starting branch / SHA:
- Ending branch / SHA:
- Current main SHA / time checked:
- Steps **actually executed** (1–30 maximum): for each, record action, affected files, verification reference, result. Mark unexecuted steps as NOT_RUN.
- Concrete files changed:
- Commit URL / draft PR URL (or NONE):
- Evidence: test command, exit status, environment, exact checkout SHA, log path or excerpt, and limits.
- Verification class: SOURCE_ONLY | HOST_TESTED | ANDROID_BUILT | SIGNED_APK | FOLD7_DEVICE_TESTED (multiple only when each proven).
- Cross-lane inspection: 01 branch/SHA/PR, 02, 03, 04, 05, Chief. Where unavailable, say UNKNOWN. Reference only branches actually found.
- Architecture bird's-eye view: reasoning → memory → planning → worker/ToolGate → Android UX → verified release. Mark each connection OBSERVED / INTEGRATED / BLOCKED / NOT_TESTED.
- Cross-lane interface conflicts and owner/lease:
- Dependencies discovered and proposed handoffs:
- Risk/security blockers:
- Next cycle: up to 30 independently verifiable actions, sorted by dependency and impact.
- Chief review decision: PENDING | ACCEPTED | CHANGES_REQUESTED (Chief writes decision on Chief-owned surface, not by modifying specialist report).

A report is a communication artifact, not a proof of successful run without trustworthy associated logs or test data.
