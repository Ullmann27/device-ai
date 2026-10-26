# Sol — JARVIS-MO open integration handoff

Status: **WIP / UNVERIFIED**, 2026-10-26. Repository: `Ullmann27/device-ai` only.
Branch: `sol/continuum-jarvis-open-fabric-20261026`.
Pinned starting point: PR #168, `78ceeeb863a3806b0bc088a4a2c2544d96be24cd`.

## Mission and boundaries

Connect existing components; keep ComputeNodeRegistry, owner grants, ToolGate,
canonical backlog, CodingProvider and CCC1 authoritative. No main merge, signing,
installation, paid resource creation, model promotion or owner-memory writes.
The owner's 2026-10-26 mission authorizes this research integration and bounded
local tests. No new permission is inferred from a resource offer.

## Initial recovery and audit

- Fresh workspace: no prior checkout or uncommitted NTW1 work found in workspace,
  `/tmp` or `/root` (bounded directory scan). Existing source is preserved remotely:
  original WIP `2abfee480d826e58ce6a80b2ce685a438d8b0329`, integrated PR #165
  `da81db718612aa6f12ba27bd6db043e57c739e5f`. Do not reconstruct from memory.
- PR #151–#168 heads queried anew through authenticated GitHub connector; all open
  drafts. Main remains `190f5eabaa60fc59292b2b80d3086aafd7743a80`.
- #166: `736aa0d67f8045d055e15098a5c414525a6660fd`.
- #167: `9a4f0632af2ef6d5ecc017eee009fccfe6969172` (scorecard sibling).
- #168: `78ceeeb863a3806b0bc088a4a2c2544d96be24cd` (fabric sibling).
- #154: `2fb604609d8f7c86ec49fe5ebd4d9158b80beb5a` (separate bridge).
- #154/#166/#167/#168 discussion timelines read. No blind sibling/file overwrite.
- AGENTS.md, experiment contract/register, Issue #41 and both master handoffs read.
  Active experiments: EXP-02/03/05/08/10/11/12; preserve Android-first capability.

## Environment and evidence

Native host Node `v24.19.0`; Java `21.0.12.1`. Docker CLI exists; executor
availability not yet established. No Android/model/owner-signing capabilities
declared by cloud runtime. Tests on this branch: **NOT_RUN at initial checkpoint**.
Historical V8 or native reports are upstream evidence, not this checkout's results.

## Next executable steps

1. Run focused native model/numeric/translator/CCC1/A-B-C/fabric tests from complete
   checkout; capture actual outputs and exit status without dependency doubles.
2. Compare #154/#166/#167/#168 ancestry and file differences. Integrate only reviewed
   nonconflicting scorecard changes and compatible bridge hunks.
3. Trace actual ComputeFabric registry APIs, signing and revocation; implement the
   smallest read-only Kotlin→Node planning contract with shared fixtures and negative
   tests. A planned route stays non-executable.
4. Probe genuine isolated executor and installed model before claiming a real coding
   cycle. Missing resources produce BLOCKED/NOT_CONFIGURED, never mock success.
5. Commit/push each stable slice and update this document with exact test evidence.

## Recovery / rollback

`gh repo clone Ullmann27/device-ai && cd device-ai && git switch sol/continuum-jarvis-open-fabric-20261026`

Read this handoff and `git log -6`. Continue only on this branch. Revert individual
integration commits on a new review branch if necessary; do not reset or force-push
any shared ref. No production state has been changed.
