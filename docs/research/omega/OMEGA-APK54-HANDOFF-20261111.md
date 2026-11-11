# Omega APK continuation — 11 November 2026

## Resume source and owner request

Continue the owner's requested memory, logic, extension and APK work from PR #768.
Verified remote base: `3cb8df27c42fbc6db03d05b0f9993a8650be43af`,
tree `888938b1a9921a1c2043041d9dbfeecf4cf7d942`.
Canonical inherited protocol: `docs/handoff/OMEGA-WEITERBEARBEITUNG-20261111.md`.
This continuation is stacked on that source; it does not replace the existing evidence.

## Work ownership

- Integrator: `integrate/omega-apk54-20261111`. P1 baseline, integration,
  source-bound regression, lint and the next unsigned ARM64 candidate.
- Memory: `work/omega-memory-20261111`. Finish and independently check the
  prior C candidate (`02d9260c9`), especially issue #758, correction history,
  ambiguity, conversational recall and context budgeting.
- Logic: `work/omega-logic-20261111`. Reproduce prior B tests (`231b8bad2`);
  issues #767/#759/#765, safe diagnosis, negation and direct app lookup.
- Extensions: `work/omega-scene-20261111`. Verified Cinema switch defects
  first; bounded P3 layout/shared-scene preparation with existing fallback.

Each helper uses a separate worktree. Heavy Gradle runs are serialized.
The integrator first runs P1 on the unchanged inherited source while helpers
prepare P2 changes independently. New feature code enters the common candidate
only after its relevant checks. This concurrency is requested by the owner;
it does not waive the integration gates.

## Current evidence

The current host uses an unprivileged user, unlike the prior root baseline.
The previous 26 environment-dependent Node failures must therefore be measured
again, neither inherited as current failures nor declared fixed by code changes.
Backend: 64 PASS, 0 FAIL, 0 SKIP on the inherited source.
Core and Node contract runs are in progress; no suite success is claimed.
The strict PDF decoder stays strict. Physical Fold7/GPU/audio/thermal
verification remains NOT_RUN.

## Product and release boundaries

The installed reference reported in issue #758 is Code53 / 0.3.52,
source `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05`.
The next candidate is provisionally Code54 / 0.3.53; verify package, source,
version and ARM64 contents before offering it.
Original package, avatar, orb, user data and model weights stay under their
existing contracts. Experimental shared-scene rendering remains default off
until a real GPU check. An unsigned transport APK is not an installable update.
Exact-source original signing and any required owner environment review remain
separate from compilation. No main merge, installation or public release.

## Next checkpoint

Record complete P1 results and exact commands; integrate independently passing
helper changes; update the inherited matrix/register and this handoff.
Do not describe an interrupted run as passed. Do not mark a real device issue
fixed on the Fold7 without its new device result.
