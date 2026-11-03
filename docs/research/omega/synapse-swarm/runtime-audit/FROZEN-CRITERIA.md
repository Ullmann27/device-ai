# Independent P0 runtime and lineage integration audit

Frozen 2026-11-03 before inspecting candidate implementation or hidden QA tasks.
Chief source: `5684efb1a2804fe28bbb0b52dc3904e1745afef1`.
Runtime PR427 head: `c78cf8a551a4d12f5dbb27ec3641fd4ae9e6f76d`.
Runtime tested production: `9e28d24bcf9ef11d9b19f5fec1be60efe2c6ab96`.
Review reference: `88a6e2fb1ce52ab090a8c6b13de7cd9bad4a7aaf`.

Scope: read-only production audit plus small existing Node contract execution.
No model download, inference run, heavy native compilation, new features or edits
to runtime/recall code. Do not inspect PR413 frozen task contents or solutions.

Acceptance requires actual owner approval matching the exact model/runtime pair;
source/build/binary/hash provenance and review scope agree; the live producer's
private immutable brand, request/source/model/context binding and HMAC-validation
remain at least as strict as the independently reviewed source. Transport injection,
clones, kind labels and arbitrary ELF/GGUF cannot mint genuine observations.
Cancellation and consumed/restart budgets remain bounded and fail closed.

Memory lineage admission must stay in the existing CCC1 recall/journal path,
recheck current grants and canonical revocations, preserve historical-evidence
labelling and restrict changed-source transfer to the reviewed lineage scope.
No independent model benchmark or learning gain may follow from fixture contracts.

Compare exact source trees rather than inheriting historical PASS. Record current
remote races, production equivalence, raw selected command output and exit codes.
The actual model acceptance is provenance only: reported blind proposal 6/8 with
two regressions is not MΩ-1 success. Full combined and device acceptance remain
Chief/QA-owned. Do not replace newer workspace/memory code with the older runtime
branch's copy during integration.
