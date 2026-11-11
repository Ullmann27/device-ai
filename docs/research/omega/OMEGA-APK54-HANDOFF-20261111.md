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
Core is in progress on the unchanged source. The complete Node baseline is
1,612 PASS / 3 FAIL / 19 SKIP (1,634 total); no full PASS is claimed.
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

## Integration checkpoint

Merged without conflicts into the common candidate:

| Stream | Exact remote source | Verified worker evidence |
|---|---|---|
| Memory #770 | `f5ed3a17724d8943dfad51894f0e09b29d551060` | 78/78 Kotlin/JUnit; Android pending |
| Logic #772 | `e20ca3a5656f4ccd81625d8e930e2fea85f48eed` | 94/94 Kotlin/JUnit; Android pending |
| Cinema #771 | `965ad4c51ec4c5a90fdb25ce8fc09d1d1f65202e` | 13/13 Kotlin/JUnit + 13/13 Node; Android pending |
| Master #747 | `c00326cedc6b697d5634dbc33de9ffcdd29fe58c` | Document-only delta reviewed |

The workers' real Activity/Canvas checks are now part of the common source.
They remain NOT_RUN until the integrator executes them. Source review includes
negation scope, foreign app intents, role checks before correction/bot entry,
session/revision invalidation, no UNKNOWN retry, and existing consent paths.

The inherited core remains frozen at `3cb8df27` in a separate worktree.
Then run focused combined Activity/Canvas checks and the required full Android,
visual and configured NewApi lint gates on the actual published source.
This recorded sequencing avoids two redundant full-suite runs; it removes no
assertion, strict PDF probe or acceptance gate.

### Node failures and retained diagnostics

The three complete-baseline failures concern two scheduler tests and the owner
abort watchdog. All three pass unchanged in isolated reproduction. Their
original cause is not proven. A new run with diagnostic assertion messages
retained an actual `TIMEOUT` receipt from the finite scheduler's 1,000-ms fixture
budget. Its other two cases passed. Only assertion messages changed; production,
limits and expected results did not. Raw evidence is in
`docs/evidence/omega-apk54-20261111/`.

Final Node runs must avoid concurrent Gradle compilation. Do not erase the
baseline failures or call them fixed because an isolated rerun passed.

### Explicitly open scope

P3 is deferred for this immediate candidate: a real common Filament scene also
requires HomeView viewport, avatar mask and coordinate-origin changes. Existing
stage/native fallback stays intact. No unfinished renderer is enabled.
General prompt-budget adaptation is not claimed fixed; direct diagnosis/app
routes and object recall now avoid needless model entry in their supported cases.
A synthetic ledger boundary case now reproduces a new required-prompt overflow:
six objects with six attributes and 24 corrections yield 16,221 UTF-8 bytes for
an ordinary related request. Memory is preparing a bounded prompt representation
while preserving the internal history and explicit uncertainty. This checkpoint
is WIP, not a release candidate; the follow-up must land before Android QA.
P4–P6 device/account/team/thermal work is still open. Physical Fold7, native PDF,
GPU, audible speech and thermal acceptance remain NOT_RUN.
