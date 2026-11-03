# Prospective independent Git-lineage policy cases

Frozen before author implementation. Reviewed baseline cf0f6a4276bfb9b4f61ab599f4ddbdf63e9f63bb remains immutable. Author confirmed proposed recall optional trusted project=null API; no-project exact-source boundary preserved. Caller-supplied project must be clean canonical root, exact approved HEAD and repository identity, existing real old/current commit objects with old actual ancestor, no replacement/graft-derived lineage. Existing path overlap/current authorization/proof/MAC/expiry/revocation gates remain.

27 cases (L07 has three variants), SHA in lineage-frozen-tests.sha256. Test syntax check exit0 only before freeze. Contract fixtures create ordinary temporary real Git commits containing harmless source text; source is never executed. Positive proofs are manually signed synthetic data using the previously frozen fixture-only tier loader. No real model/weights/inference, no network, no QA holdout content and no baseline benchmark changes.

Expected sourceLineage item: {kind:'GIT_ANCESTOR'|'EXACT_SOURCE',historicalSourceCommit,currentSourceCommit,ancestor:true}; existing sourceCommit stays historical, currentSourceCommit added, sourceFiles remain scoped. None of these labels grants execution authority or claims semantic transfer success. Real model/usefulness experiment remains QA-owned.

Author is holding implementation until notification of this freeze. Requirements and fixture API were coordinated before candidate inspection. Tests contain no dependency on the future implementation. Raw baseline RED and later candidate GREEN will be recorded separately, preserving any fixture failures and corrections.
