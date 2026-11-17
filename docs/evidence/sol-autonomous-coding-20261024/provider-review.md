# Independent provider/context/orchestrator review

Reviewed uncommitted `/workspace/continuum` on 2026-10-24: `coding-task.mjs`, `coding-context.mjs`, `coding-provider.mjs`, `coding-orchestrator.mjs`, local-brain client/CLI and their tests, plus integration call sites. Read-only review: no production edits. Findings reflect the latest files read, not the earlier drafts.

## Result

No remaining P0/P1 blocker found in the assigned task/provider/context/client/orchestrator seams. General JS/Kotlin execution policy and independent verification belong to the pipeline agent's separate review. This statement is not full-suite/device/model validation.

### P2: UTF-8 stdin limit rejects valid bounded prompts

`coding-provider.mjs:29` and `continuum-local-brain.mjs:185` permit up to 28,000 UTF-16 prompt characters. `continuum-local-brain-cli.mjs:17` instead rejects the entire JSON request above 40,000 UTF-8 bytes. A valid multilingual source context can therefore be accepted by the direct client but rejected by the real subprocess before inference, reported as model unavailable rather than invalid transport budget.

Verified adversarial case: userPrompt = `汉`.repeat(14000), one `src/subject.mjs` path. Request is 42,076 bytes. Direct `queryLocalCodingBrain` with a fake bounded response accepts; the real CLI exits 1 with `LOCAL_PATCH_REQUEST_TOO_LARGE` without making a model request. Fix using one shared serialized-byte bound, or increase coding stdin's finite limit to accommodate the maximum prompt plus JSON escaping. Add a real-CLI multilingual stdin test; the current 8k ASCII fake-fetch test does not cover it.

## Earlier issues now corrected

- Earlier provider draft computed model timeout before repository retrieval. New `coding-provider.mjs:21-31` passes a deadline into retrieval and recomputes remaining model time afterward. Current context helper carries deadline into every Git read and checks between parses. This closes the earlier stale-budget request problem.
- Earlier context builder could return 31 ranges in a valid 23,911-character context, while scoped worker accepted only 24. Latest `coding-context.mjs:215` caps ranges at 24, and a regression test was added. The original adversarial fixture is retained in `/workspace/scratch/review-provider-adversarial.mjs`.

## Verified boundaries

- Task fields are exact/typed; unknown authority/command fields, traversal/protected paths, incompatible chain/source extension, missing acceptance, and overlarge limits are rejected. Canonical backlog JSON determines task fingerprint; task arrays/limits are frozen copies.
- Context reads immutable blobs from an exact full current commit, validates tracked ordinary file modes, avoids working-tree symlinks, excludes protected sources, parses rather than imports target code, hashes full blobs and excerpts, and bounds complete serialized output. Wider repository context is read-only and grants no write scope.
- Provider validates commit/chain, counts actual subprocess starts against a private maximum, uses only fixed `process.execPath + trusted CLI + coding` argv with shell:false, and independently revalidates returned edit paths/schema. Model JSON cannot select executable, URL, commands, strategy, permissions or verification state.
- Loopback endpoint is fixed, authenticated, and redirect:error; response body remains bounded and output is explicitly untrusted/autoApply:false. No API key is included in the prompt or ordinary trace fields. Optional evidence is an owner-private file outside source and records prompt/output for review; do not treat it as a shareable public log.
- Task prep checks exact source and clean tree, reserves grants exclusively, fingerprints exact task bytes, creates the existing CCC1 task with narrow chain permission, and retains a crash-before-journal reservation as an explicit inspection case. Execution uses existing CCC1 runTask, mobile job, Teamleader, worker and verifier; no parallel executor/journal/verifier was introduced by the adapter.
- Adapter rebases task id to deterministic mobile job id so worker matches the canonical backlog task. Result retains original task fingerprint, actual receipts, no merge/device/training claims. A model label and a request trace do not claim verified model execution.

## Material semantics and targeted test gaps

- Acceptance strings are prompt requirements, not executed acceptance tests. All kinds (FEATURE/REFACTOR/TEST/DEPENDENCY included) currently enter existing REPAIR semantics: an already-red fixed chain must be repaired; a green baseline returns no repair. A verified receipt proves the failing chain was fixed, not every free-text criterion or a general new feature. Preserve this limit in handoff and result wording.
- Provider tests should explicitly cover exhausted call budget with a spy ensuring zero extra subprocess starts, deadline exhaustion after retrieval, failed/invalid response traces, and real CLI input budgeting. Existing tests cover the happy fixed-CLI call and task binding but not these resource edges.
- `coding-orchestrator.mjs:65` creates the provider before `runMobileRepairJob` can replay an already-saved receipt. Therefore finishing journal recovery can require local auth even if no inference is needed. This is a resilience follow-up; normal completed CCC1 replay never enters the callback. A targeted crash-after-mobile-receipt/before-CCC1-receipt test would clarify it.
- Model source/code quality and physical Android/Termux transport are not established by fake-fetch or spy-process tests.

## Verification

No broad test suites were repeated. Ran two new adversarial fixture probes only: context range contract / retrieval deadline, and direct-client versus actual CLI multilingual-byte behavior. Context agent's subsequent fixes were checked in current source. No live model, external communication, production mutation, signing or install.
