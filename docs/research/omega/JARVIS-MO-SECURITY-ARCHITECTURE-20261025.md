# Continuum JARVIS-MO – Owner-AI and defensive security research (25.10.2026)

## Mission and reality boundary

JARVIS-MO is a **target architecture**, not an already functioning general AI. The owner should be able to communicate naturally (text, later speech/vision), request research and coding jobs, inspect evidence and decide what may run or be released. The orchestration reuses **CCC1 / Autonomous Teamleader / Repair Loop / Memory / Research / ToolGate** in this repo. It does **not** introduce a second unaccountable agent or lift tool privileges.

A long-term consumer software project provisionally called **Auto Access Lizenz** may eventually manage large amounts of sensitive tenant information. It is a **separate future project**: do not imply its repositories, data stores or identities are currently connected to Continuum. Do not request or ingest production customer data into this research branch.

## Architecture – user intent -> evidence -> controlled action

1. **User interface:** existing Continuum Android chat and activity. Speech-to-text, interruptible audio and vision are distinct future adapters and need explicit user permissions, device tests and a privacy model. The existing Hologramm display must be device-tested before any animation is claimed to work.
2. **Intelligence and memory:** existing local-model adapters, SWB1, CDX1, LM1, Experience Memory and research components. Free autonomous speech/reasoning or continual general learning is **not** established by component existence.
3. **Coding:** CCC1 -> scoped context -> local Qwen model -> Repair Loop -> existing syntax/critic/fixed tests -> independent worktree -> `PATCH_PENDING_OWNER_REVIEW`. VH-SR is a separate **opt-in veto-only research experiment**. A proposed patch never modifies its own verifier or grants itself permission.
4. **Defensive intelligence:** approved public advisory snapshots (OSV; eventually CISA KEV for prioritization, with correct mapping) + owner-authorized versioned component inventory -> local schema checks -> precise matches or unresolved range evidence -> owner review -> controlled regression tests and patch proposal. No probe or exploit of third-party systems.
5. **Security authority:** deny-by-default grants, scoped tool execution, sensitive-data segregation, tenant-specific access checks, owner review and reproducible tests. Cryptographic integrity is not authorization or proof of correctness.

## First implemented slice: `experimental/omega/security/advisory-intake.mjs`

- `evaluateOwnedOsvSnapshot({inventory, advisories})` accepts only `CONTINUUM_OWNER_INVENTORY_V1` with a 40-digit pinned source commit, explicit ecosystem/package/version and direct/transitive classification; OSV-format `id`, `affected[].package`, optional `versions`/`ranges`, and optional `withdrawn`.
- **Exact enumerated affected versions** produce `EXACT_LISTED_VERSION`. A matching package with only version ranges produces `RANGE_REQUIRES_REVIEW`, never a guessed verdict. Nonmatching records are omitted from *findings* but **not** proof of safety.
- Withdrawn advisories do not produce active findings. Duplicate advisory IDs with conflicting normalized content fail closed. Input limits, package checks and source pinning are required. Arbitrary external descriptions/prompt-injection content are **not** copied into reports.
- Deterministic report includes source/inventory/advisory fingerprints, schema, scope and `SECURITY_FINDINGS_PENDING_OWNER_REVIEW`. It explicitly states no patches/actions and never claims vulnerability-free status. SHA-256 here is **not** a digital signature, proof of feed authenticity or a claim that the owner inventory matches real deployed binaries.
- An **explicit-consent OSV public querybatch adapter** now exists in `experimental/omega/security/osv-public-query.mjs`: fixed HTTPS endpoint, only ecosystem/package/version in requests, no credentials, bounded JSON response, no silent pagination truncation, fail closed on malformed/network failures, review-only findings. **Network calls have been tested with mocks only; no real OSV request was executed in this session.** There is no remote scanning, issue exploitation, APK creation, automatic patch, GitHub merge or production deployment. Real advisory authenticity, ecosystem-specific range resolution, transitive dependency provenance and deployment inventory verification remain separate tasks.
- **Isolated implementation checks:** Node.js 22.16.0, `node --test experimental/omega/security/*.test.mjs` -> **25/25 passed**, including input bounds, tampering, lockfile parsing and adapter-to-OSV integration. All six implementation/test file Git blob SHAs were compared against locally tested files. Full repo/Android CI was **not** run.
- `experimental/omega/security/npm-lock-inventory.mjs` derives a read-only npm v2/v3 inventory from a supplied lockfile, retains direct/transitive dependencies, deduplicates identical nested versions, and **flags unresolved workspace/link entries** instead of hiding them. An actual `backend/package-lock.json` v3 exists in Continuum with direct dependencies including `express`, `express-rate-limit`, and `helmet`; its full inventory has **not** been independently compared with live OSV data. The caller-supplied source commit is a claimed pin, **not** a verified Git attestation.

## Engineering order – executable milestones

**J0 (current):** secure branch and Draft PR; clarify goal and safety contracts. Completed: offline OSV advisory matching and isolated tests.

**J1 (partly implemented):** npm v2/v3 lockfile parser and opt-in OSV querybatch adapter are in this branch. Still required: Gradle/Android parsers, *real* source-commit/inventory binding to deployment artifacts, verified upstream response provenance, authorized live-query smoke test, pagination handling (currently fails closed), cache with retrieval timestamp, and detailed security review. Never include secrets or private customer records in feed requests.

**J2:** resolve ecosystem-specific version ranges with maintained parsers and tests; distinguish affected, unaffected-in-known-range, unknown, withdrawn and not-evaluated. Add CISA KEV only as prioritization after product-to-package applicability has been verified. Add human-review triage with owner impact and asset scope.

**J3:** feed findings into existing CCC1 *as a suggested security review task*; scoped code retrieval, regression/fuzz/property tests and independent verifier in sandbox. No automatic changes to another repo, no secret exfiltration, no self-edit of safety policies and no privilege expansion.

**J4:** VH-SR on existing Repair Loop, veto-only experimental callback; apply typed counterexamples to candidate proofs, not to mutable owner baseline. Prove the new callback cannot mutate candidates/gates or falsely upgrade status.

**J5:** install evidence-bound, interruptible local speech and optional image perception as separate permissioned Android adapters. Support clear user confirmation for any cross-app/device operation; full Fold7 acceptance required.

**J6:** versioned checkpoint/replay with restore tests, H-MAC evidence-to-skill promotion behind owner review and EIG-SC bounded research planner. ARCHETYP remains an isolated, finite, falsifiable representation/algorithm experiment; no live recursive self-replacement.

**J7 (future Auto Access Lizenz security):** explicit tenant isolation, encryption/key rotation, authenticated authorization, privacy by design, SBOM, scoped API tests, dependency updates, multi-party review, operational monitoring, backup/restore drills, penetration tests under written authorization and external assessment. No security-free-of-vulnerabilities claim.

## Release constraints and handoff

- Continue from Astra's last pushed PR #133, `4b9ead538514680427f3330ef6b6e1938f9123d5`, in separate branch `research/continuum-omega-resume-chatgpt-20261025`. His unpushed workspace code is not accessible and must not be reported recovered.
- Keep PR #132, PR #133, `main`, owner app signing, release 0.3.21/code 22, personal memories, owner keys and model weights untouched.
- Experimental findings are not owner patch approvals. No source from public advisories should be treated as instructions to the agent.
- A full CI and real Fold7 test remain release prerequisites. GitHub Actions account/billing runner failures do not imply product correctness.


## JARVIS-MO security review handoff – 25.10.2026, resumed session

New research-only review stage: `experimental/omega/security/security-review-proposal.mjs`.
It accepts **only** a structurally and hash-validated `CONTINUUM_SECURITY_REVIEW_V1` offline OSV report, selects an existing exact finding by package/version/advisory identity, and emits `CONTINUUM_SECURITY_REVIEW_PROPOSAL_V1`. An exact listed version means `REVIEW_EXACT_ADVISORY_MATCH`; a range-only match means `ANALYST_MUST_RESOLVE_AFFECTED_RANGE`. In either case: `ccc1Ready=false`, `authorized=false`, `executionAllowed=false`, `patchAllowed=false`. This document is **not** a `continuum-coding-task`, owner grant, permission, patch, or trusted feed attestation. No auto-promoting it to CCC1 until source/component/applicability and owner's actual instruction are independently verified.

Seven additional stream-defense tests were added in `bounded-osv-response.test.mjs`, together with the stream reader. The official OSV query adapter now requires a byte stream; it caps reads at 1,000,000 bytes, enforces strict UTF-8 and refuses to fall back to unbounded `response.text()`. Its mocks were converted to streaming fixtures, and an additional no-fallback regression test was added. No live OSV network request was made.

**Actual local checks performed this session**: `node --test security-review-proposal.test.mjs` passed **8/8** and `node --test bounded-osv-response.test.mjs` passed **7/7** on Node.js 22.16.0. These are **15 newly executed tests**, not a full updated suite. The source blobs for the new review module and its tests were compared byte-for-byte with their GitHub versions; the integrated OSV mock tests and full Android/backend suite still require execution on the precise current HEAD. GitHub Actions account billing/runner issue remains an external blocker. The pinned non-release QA launcher now includes `node-omega-security` so the new tests are not silently excluded once QA runs.

Security follow-up priorities: (1) complete full combined local/mock regression on exact HEAD and inspect QA status; (2) integrate authenticated owner/project inventory provenance and permissioned selection of source paths; (3) create reviewed **draft** CCC1 tasks using the existing `validateCodingTask` policy with no implicit grant; (4) add strict per-tenant authorization, secrets/privacy boundaries and independent professional security assessment before handling real Auto Access Lizenz personal data.
