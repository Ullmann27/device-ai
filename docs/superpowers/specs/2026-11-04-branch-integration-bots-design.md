# Continuum branch integration bots

Status: implemented for the documented selected Node scope; the serial follow-up passes 202 targeted tests on `c216ebcd4726fefd221a6ee347699c9a1280fc47`. Earlier 183-test independent review remains historical. APK and device acceptance remain NOT_RUN. Owner request: discover unexplored, hidden and completed but unconnected branches, connect useful existing technology and progress towards a verifiable Android APK.

## Existing architecture and source

Product baseline: `Ullmann27/device-ai`, `codex/astra-jarvis-takeover-20261104`, commit `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`. Implementation branch: `codex/continuum-branch-integration-bots-20261104`. The existing local Teamleader cadence, development backlog, CCC1 execution, independent verifier and ECHO reporting remain the integration points. Existing App bot profiles are not duplicated.

## Design decisions

Add a bounded, deterministic branch discovery operation that reads locally available Git refs, pins source and target commits and compares actual source trees. It may propose review work in the existing development backlog. It does not execute code from candidate branches, create a second task ledger, auto-merge, infer that a newer branch is better or assert that a static finding proves runtime integration.

The operation uses the existing local Teamleader loop for optional repeated execution. Single-run mode is explicit. Repeated operation requires a finite cycle budget and uses external state, the existing locking/restart rules and a fixed interval. No network fetch, paid AI API or GitHub Actions dependency is added.

The serial continuation adds source-bound navigation cursors and a 3 MiB source-report budget within the existing 4 MiB private record contract. Finite loops advance pages; explicit `--once --continue-scan` permits separate one-pass invocations. Private atomic cursor storage reuses the existing loop lock and verifies its previous immutable report. It stores navigation, not task authority or cumulative verification. Changed source refs restart pagination; malformed state and changed settings fail closed. Partial pages retain `complete=false`; truncated paths remain errors. See `docs/research/omega/BRANCH-BOTS-NIGHT-20261104.md` for compatibility and remaining limits.

Inspect local branches, remote tracking refs and available pull-request/archive refs; report which refs were actually available. Hidden means discoverable Git references, not bypassing access controls. Deduplicate identical source commits, distinguish already inherited, byte-identical, missing and divergent work. A source/target pair is the unit of evidence and queue idempotency. Quality and APK readiness remain unverified until separate real tests/builds establish them.

Fix the existing strict-model failure path where a completed CCC1 attempt throws before its negative deterministic ECHO report is written. Preserve the rejection and all admission, source, profile and verifier checks. Do not turn a rejected model invocation into success, learning or promotion.

Independent development helpers inspect current feature branches for small existing fixes. Only concrete, reviewed changes with regression evidence may enter this integration branch. The live App bot service and device are separate from the development agents in this session.

## Constraints

- Work on isolated worktrees and a dedicated Draft PR; never update main or another team's branch.
- Use existing CCC1, BacklogStore, repair and reporting mechanisms.
- Candidate source is data during discovery. No checkout, merge, hooks, shell interpolation or execution of candidate build scripts.
- Never auto-accept a proposed integration task or weaken protected-path/test policies.
- Keep package identity, signing keys, update channel and owner data unchanged.
- Count real checks and publish exact source hashes and failed/unavailable gates.
- This executor currently has Node 24 and Git, but no verified JDK compiler, Gradle or Android SDK. No APK build, signing, install or Fold7 success is implied.

## Acceptance

Real Git fixtures must cover remote/archive refs, aliases, already-integrated work, missing source, divergent changes, invalid/oversized input, repeated scans and queue idempotency. Repeated execution must use the established Teamleader loop. A strict-model rejection must remain rejected while the permitted negative reporting path runs. Independent review must check that reports cannot create execution authority or upgrade unverified evidence.

## Alternatives considered

A second independent bot scheduler/task store would conflict with the existing canonical pipeline. Automatic merging of every non-ancestor branch would conflate missing history, divergent work and quality. The chosen bounded scout plus existing backlog makes the evidence and the next allowed step explicit while development helpers integrate verified fixes.

## Implemented review refinements

The scanner rejects configurations that could execute clean/process filters or nested submodule status helpers and checks them immediately before status operations. A trusted awaited source-preflight hook is bound at all existing scheduler identity boundaries. The CLI adds the existing `inspectHostEnvironment()` diagnostic to the immutable scan artifact; exact saved bytes define its SHA-256. Tool availability remains separate from actual APK build evidence. Full operating instructions, pinned source findings and limitations are in the handoff.

## Android-first continuation

The CLI reuses the existing Termux environment resolver before the existing APK inspector; version probes receive the selected environment explicitly. The current PR505 QA entry-gate correction is adopted selectively with its original regression tests. These changes preserve the same single backlog, repair pipeline, source authority and release gates. Two fresh environment regressions and the prior QA failures were observed before their corresponding corrections; final targeted combined acceptance is 183/183. APK and device gates remain NOT_RUN.
