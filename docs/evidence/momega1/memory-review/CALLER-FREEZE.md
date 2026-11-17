# Independent caller accounting / report freeze

Candidate989361a5, diff ba820fcf..989361a5 only coding-orchestrator/repair-loop. Tests frozen before execution and before root report-metadata fix. Exact caller function bodies are loaded from candidate source, with explicitly substituted unit dependencies (including synthetic CCC1 job/executor/runtime WeakSet brands) to exercise accounting/identity logic. These isolated caller tests are not integration, OS isolation or real inference evidence. Real repairSession tests execute harmless synthetic numeric fixture tests in Git worktrees, with ONLY runtime-brand dependency replaced by test WeakSet. No production file edited; no production private brand obtained; no actual model invoked.

I11/I12/I13/I15 explicitly assert correction of reported metadata when a synthetic original frozen trusted-tier observation is valid, including thrown rejected outputs and pre-span-check returned outputs; JSON clones/false tier/wrong task/mutable observations must not promote it. Fresh-ID dedup required. Caller tests cover clones/task/source/fingerprint/status/hash/freshness, verified failure versus executable proposal, cross-attempt model/runtime/reviewedPair identity changes and exact counters. Source/report weakness was identified before freeze and raised to root; no fix inspected.

Checksums caller-frozen-tests.sha256. Original frozen suites unchanged. Syntax check exit0. Raw RED and later corrected candidate runs must remain separate.

Executed unchanged989361a5:28tests24PASS4FAIL0SKIP exit1. Four expected report metadata assertions I11/I12/I13/I15 fail false!==true. All caller accounting/identity and real repair terminal-change controls pass. No fixture error. Raw caller-baseline-red.tap/.exit. Exact command:
MOMEGA1_CANDIDATE_ROOT=/tmp/momega1-memory-caller-989361a5 node --import ./tools/momega1-memory-review/tests/caller-brand-loader.mjs --test --test-reporter=tap tools/momega1-memory-review/tests/caller-accounting.test.mjs
