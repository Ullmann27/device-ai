# First independent candidate run: RED

Candidate: 3f5c293b4db437c12659f2e02c8dd4557ff116bd.
Controller checks: 0/15 groups passed. Storage checks: 0/3 passed.
Existing AiContextCompiler regression tests: 4/4 passed.
Compiler succeeded using actual source and official/real app dependencies.

Every controller prepare failed with BOT_MANDATORY_CONTEXT_EXCEEDS_BUDGET because
it passed 16,000 to existing LocalPromptFrame.assemble, whose accepted maximum is
LocalChatPromptBudget.FULL_CHARACTERS = 15,500. Even a tiny request is rejected.
This is a real source integration failure, not a missing model or a harness issue.
Raw exit codes, hashes and all failures are retained in red-3f5c293b/.

The builder independently found the same defect and is correcting the caller to
the existing bound; no test or runtime budget is weakened. Original tests remain
unchanged for the rerun. A separate source audit identified that isCurrent did not
check changed profile/note revisions, including after completion; this gap was
masked by the universal prepare failure and is not claimed as an independently
executed second RED until it is actually observed.

The next run is temporarily deferred for the Chief's exclusive real-model CPU
measurement window. No success or remaining local model acceptance is asserted.
