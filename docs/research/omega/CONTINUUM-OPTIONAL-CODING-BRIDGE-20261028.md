# Optional Coding Bridge – Device AI Continuum

Status: research prototype, **never merged or activated**. Date: 2026-10-28.

## Goal and verified existing architecture
Adds a strictly isolated experimental coding gateway beside the existing CCC1/repair pipeline, local health worker, Termux scheduler, Android app and Gemma. None is replaced or auto-started. Does not provide a persistent ChatGPT-to-Fold7 connection: GitHub source alone cannot trigger private local processes.

The bridge is a Node.js 20+ script using built-in modules only. No OpenCode package, npm install, shell-init modification, Termux permissions, key store or Android APK is involved. OpenCode is an alternative later client, not installed by this PR.

## External primary sources and model policies
- Experiential gateway: https://platform.experientiallabs.ai/docs – exact `https://api.experientiallabs.ai/v1/chat/completions`.
- Billing and verification: https://platform.experientiallabs.ai/docs/billing – card verification $1 credited; model availability and the user's quota must be checked on the actual account.
- Error reference: https://platform.experientiallabs.ai/docs/errors – `<slug>:free` is free-only, fails with `free_limit_reached` instead of spending credits; plain `<slug>` may become billable. Promotions and tool-calling availability are account-dependent.
- Auth: https://platform.experientiallabs.ai/docs/authentication – account API key is a sensitive organization credential with broader management permissions. Never send it to chat or GitHub, never save it to test reports; read from private environment only.
- Data controls: https://platform.experientiallabs.ai/docs/data-controls – new Free organizations capture prompts/responses by default. Until a separate privacy authorization, the bridge sends only known public synthetic data.
- OpenCode: https://opencode.ai/v2/docs/providers – compatible provider runtime can be used separately, not part of this script.

## Files
- `scripts/continuum-coding-bridge.mjs`: pre-approved public files, exact free-only models `claude-fable-5.1:free` and `gpt-6-astra:free`, fixed single HTTPS endpoint, controlled read_file/run_test tools and synthetic proposal verifier.
- `scripts/continuum-coding-bridge.test.mjs`: 13 deterministic Node tests, including actual file reads, actual failing and passing Node tests, cost/BYOK response rejection, wrong model, symlink, traversal, Git checkout and unapproved live-run rejection.

## Evidence, limits and trust boundaries
The offline test calls a simulated model responder labeled `MOCKED_OFFLINE`, not Fable or Astra. It reads exactly two public files and runs the real fixed baseline test: 2/3 pass, 1/3 intentionally fails. The suggested one-line JSON patch is validated against an **exact hardcoded allowlist**; only then is it applied to a NEW disposable copy of those synthetic files and a real Node test passes 3/3. Original file remains byte-identical. No arbitrary model-generated code is executed. No shell commands from the model are permitted.

A process boundary is not an OS sandbox. Here only the fixed public test code is launched, with restricted environment, time/output budgets and no owner secrets. Do not introduce untrusted tests or private source access without a real isolation/security review.

`usage.cost == 0` in a provider reply is not conclusive proof of cost. Result field `usageCostVerified:false` intentionally remains false until the separate account billing ledger is checked. A missing or base-model response identifier is not a verified serving provider; `modelIdentityExternallyVerified:false` remains false. A model that claims a different slug is rejected. No quota, personal account availability, Fold7-native execution or actual Fable/Astra tool-call support is verified.

## Reproducible local checks
Run on an owner-reviewed isolated checkout with Node 20+ and no secrets:

```sh
node --test scripts/continuum-coding-bridge.test.mjs
node scripts/continuum-coding-bridge.mjs --prepare "$HOME/continuum-public-coding-trial"
node scripts/continuum-coding-bridge.mjs --offline-proof "$HOME/continuum-public-coding-trial"
```

Live `--trial ... fable|astra` requires a separate, explicit `CONTINUUM_EXPERIENTIAL_TRIAL_APPROVED=SYNTHETIC_ONLY` and a locally held `EXPLABS_API_KEY`. Do not supply credentials in ChatGPT, repository files, chat screenshots, logs or shell history. Do not attempt the live mode before confirming user account eligibility, provider data-retention rules and key spending cap. An `EXPLABS_API_KEY` can manage organization resources; restrict network/host access and use a disposable, owner-approved key if available.

## Tested on 2026-10-28
Local Node.js v22, baseline test file 13/13 PASS, standalone offline proof returned `completed:true`, 2/3 baseline, 3/3 independent candidate check, zero repository accesses. GitHub source blobs are compared to exact local tested files: `scripts/continuum-coding-bridge.mjs` blob `f32b309682ccd62c48a61ef9e94bddcd823e2e6a`; `scripts/continuum-coding-bridge.test.mjs` blob `ae33c281f623f7a4782d3a16aacd4421022eb4db`. These are source-code/fixture tests, not model inference, quota or Android build.

## Explicit follow-up gate
1. Owner signs in to Experiential privately, reviews provider content retention, and if desired completes the allowed $1 card verification. Disable optional auto-recharge, paid plans and non-free fallbacks. Review account entitlements and free quota before a single test.
2. Permit one synthetically scoped `--trial`. Confirm actual tool calls, final model, account-based zero charges, rate and quota, and code result. Reject unknown billable provider routing.
3. Only **after successful proof and separate explicit owner approval** design an additional narrow GitHub repository reader, isolated branch and Draft PR. This PR does not contain such a reader.
4. Existing Continuum branches, main, installed apps, checkpoint memories, signing files, private model binaries and owner files remain untouched.

## Revert
Close this draft PR without merging. Any locally created, **separate synthetic** fixture may be manually reviewed and deleted by its owner. No package, service, cron job, agent profile, API key, payment or system setting was created, so there is nothing else to undo.
