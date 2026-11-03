# MΩ-1 independent QA

WIP. Base ca93fb4f; independent acceptance owns only new QA tools/evidence and this handoff. No feature candidate inspected before suite freeze. Twelve distinct synthetic bug tasks and a related pair for later memory ablation are frozen in `tools/momega1-qa/FROZEN-MANIFEST.json`; its SHA256 is `1055d66300b0f4664978f103d1e5adb73a9619a337539ad41e898b7217a92407`. No solution patch or model answer is included. Authors must not inspect task sources/tests until their candidate is frozen; later exposure ends the unseen status for that author.

Actual unchanged source run: `node --test --test-reporter=tap tools/momega1-qa/frozen/tasks/*/acceptance.test.mjs`, exit1, 12 assertion failures, zero passes/skips. Raw TAP and exit retained under docs/evidence/momega1/qa. This is a real RED fixture baseline, not model execution. LOCAL_MODEL_HOST is BLOCKED_LOCAL_MODEL_UNAVAILABLE, Fold7 is NOT_RUN; no weights download permitted. No model-solved tasks or memory gain claimed.

Node and Java21 are available; SDK/adb/Gradle/JDK compiler absent at entry. Docker/bwrap binaries available; effective isolation is not yet proven. Reconstruct official pinned host build dependencies only under /workspace/momega1-build-env. One full Gradle job at a time coordinated by QA. Fresh broad host counts must be measured; prior Code40 counts are historical only.

Global limits: no Actions, Render mutation, signing, install, main merge, owner data or production model changes. EXP-00/01/02/03/05/07/08/11/12 apply. Final exact-source broad host QA awaits Chief integration freeze. Rollback is omission of QA-only commits.
