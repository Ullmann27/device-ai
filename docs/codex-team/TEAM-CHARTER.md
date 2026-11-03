# Continuum – 5 parallel Codex developers + 1 Chief Engineer

Status: **repository-side preparation only**. No Codex Cloud environment or five live cloud tasks is asserted by this file.

## Proven starting point (2026-11-03)

Repository: `Ullmann27/device-ai`. Setup originated from `main` SHA `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a`. The repository contains Kotlin/Android (`app/`), Node services (`backend/`) and existing Node research/worker tools under `.github/continuum/`. The default-branch README is old; do not use its feature list as the sole truth for current experimental PRs.

Related experimental, **unmerged** PR chain to inspect before any adoption:
- #384 Chief team staging (source-only; its base is not main)
- #385 Chief task DAG with path-conflict detection, independent verification required
- #386 proof-gated strategy ledger
- #387 CCC1 alpha-squad CLI; four checkpoints, **not four independent language models**

These are distinct stacked PRs and cannot safely be merged into `main` merely because their source exists. Source-only does not mean Android, emulator, host, signed APK or Fold7 verified.

## Live team roles and branch namespaces

| Lane | Codex task | Primary responsibility | Branch prefix | Reporting location |
| --- | --- | --- | --- | --- |
| 1 | Intelligence | local inference, reasoning, German dialogue, model binding | `codex/team-01-intelligence` | `docs/codex-team/reports/01/` |
| 2 | Memory | durable memory, retrieval, verified learning, research provenance | `codex/team-02-memory` | `docs/codex-team/reports/02/` |
| 3 | CCC1 Coding | local coding worker, RED→GREEN repair, sandbox, receipts | `codex/team-03-ccc1` | `docs/codex-team/reports/03/` |
| 4 | Android | Fold7 UI, actions, voice, avatar, accessibility | `codex/team-04-android` | `docs/codex-team/reports/04/` |
| 5 | Quality | independent acceptance tests, threat model, build/release readiness | `codex/team-05-quality` | `docs/codex-team/reports/05/` |
| 6 | Chief | architecture graph, task dependencies, conflicts, review and integration proposals | `coordination/codex-six-agent-team-20261103` initially | `docs/codex-team/reports/chief/` |

Agent processes and ChatGPT chats are NOT synonymous. Five Codex tasks must be launched separately (or via an approved orchestration runtime) to perform real parallel work. Team membership and model concurrency remain unknown until the Codex Cloud environment is configured and tasks actually start.

## Branch isolation and integration safety

1. Each lane uses its own branch/workspace. The five named team branches are initial boot branches, not proof that any coding model is running.
2. File ownership is **dynamic**, not falsely inferred from directory names. At the start, Chief and each lane inventory touched files and agree on an exact file/path lease recorded in that lane's PR or report. A lane may read any source, but must not write to a leased path of another lane. Paths are evaluated by canonical repository-relative path, not broad string prefixes.
3. Shared files such as AndroidManifest.xml, Gradle files, `.github/continuum/` cross-cutting worker code, security policies, and integration wiring must have one writer at a time, agreed with Chief. A competing PR must wait or be rebased only after reviewing current cross-lane changes.
4. No direct commit to `main`. Code changes remain in draft PRs. Chief may propose integration PRs only after independent QA, conflict review and exact-SHA test receipts. Actual merge/release follows owner- and repository-required approval gates.
5. Tests and receipts must bind checkout SHA. A test of PR #375 does not prove PR #384 or #387.
6. No production package/signing/publisher/version changes, no new unaudited CI workflows, no automatic Play Store publication. No bypass of CCC1 approvals and no independent worker access to production.

## Thirty-step checkpoint algorithm

Before cycle: inspect own HEAD, latest `main`, relevant stacked PRs, other team branches and last accepted architecture register.

During cycle:
- Steps 1–10: inventory, plan, write focused implementation/tests as appropriate.
- Steps 11–20: integrate only within own leased surface, seek contract handoffs from other lanes.
- Steps 21–30: run feasible tests; collect machine-verifiable receipts and regression findings.

A step is counted only if an actual verifiable operation took place. Do not manufacture numbers; stop early for safety, environment failures, missing approvals or substantial new conflicting commits.

At checkpoint:
- Produce a top-down architectural map (brain → memory → planner → CCC1/Android ToolGate → UX → QA/release).
- Requery **all** five lanes and the Chief's current PR/commit state. Update the dependency matrix and mark cross-lane changes as OBSERVED, INTEGRATED, BLOCKED or NOT_TESTED.
- Write a lane-specific evidence report using `CHECKPOINT.md` and propose the next up-to-30-step plan.
- Proceed only within the current task's actual execution permissions; do not claim perpetual background activity.

## Cloud setup – what must happen in the Codex interface

1. From Codex on web or desktop, create/publish a cloud environment including GitHub repository `Ullmann27/device-ai`. GitHub account/repository permissions must be granted to that environment. Check available cloud access and account limits.
2. Configure only necessary build dependencies. Confirm Node.js availability for `.github/continuum/` tests; for Android compilation install/enable a compatible JDK, Android SDK and Gradle/wrapper only if the project actually provides/needs them. Do not fabricate installed versions or Gradle wrapper files.
3. Start **five separate tasks** using the exact role prompts in `STARTPROMPTS.md`, each from its own boot branch. Verify each task's actual workspace, initial HEAD and write permissions. They must not share an unisolated working directory.
4. Start the sixth Chief task with the coordination prompt. It must monitor PRs but must not merge unverified code.
5. At first real run, verify five distinct task identifiers and at least five isolated Git workspaces. If only one task or virtual roles exist, report that the five-agent requirement is **not yet achieved**.
6. Do not conflate OpenAI API Agents/subagents (potential API usage cost) with included Codex Cloud tasks. Codex Cloud task availability and quotas depend on plan/workspace configuration.

Official setup reference: https://help.openai.com/en/articles/20001545-using-codex-cloud

## Chief acceptance checklist

- Five independent Codex task IDs, one Chief task ID, each with proven execution state and correct repository checkout.
- Exactly one active writer for each overlapping source path, with recorded leases and nonconflicting branches.
- Per-lane evidence report with commit and actual test results; independent QA for modified runtime code.
- Architecture register reconciles new interfaces and blocked dependencies across all lanes.
- Chief proposes next cycle based on evidence and never labels source-only, host-verified, signed APK, and Fold7-tested as the same milestone.
