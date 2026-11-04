# Inworld Router integration — 2026-11-04

## Identity and authorization

- Official repository: `Ullmann27/device-ai`; Draft PR #504.
- Branch: `feat/continuum-inworld-router-20261104`.
- Frozen base: `0e3685a3bc6cb3a4af61988450371f11e9cb0f3c` (PR #500).
- Owner explicitly requested Inworld integration, server-only `INWORLD_API_KEY`,
  Basic authentication without re-encoding, and implementation before live testing.
- Applicable contract: EXP-10 optional remote language mentor.
- Existing model/tool/memory/security architecture stays authoritative.

## Design decisions

The existing optional mentor is the useful matching API surface: authenticated
Android → backend → one provider text response. It already has pairing,
one-question consent, input/output bounds and external-unverified provenance.
The OpenAI conversation/tool cloud-agent endpoint is a different contract and is
not rerouted. The integration does not replace local model inference.

One shared adapter now accepts INWORLD in addition to GROQ/OPENROUTER; its historic
`GroqMentor` class/file name is preserved to avoid unrelated migration. Inworld
uses the pinned HTTPS URL, Basic header, default Gemma model and medium effort
from the owner request. The backend chooses the model/effort; client fields cannot
override them. Response model identity is retained when valid; no reasoning,
provider metadata or tool proposals are returned.

Inworld has one provider attempt, a 45-second overall deadline and no retry or
provider switch. A disconnected HTTP client aborts fetch. The existing Groq and
OpenRouter empty-content recovery is retained. Transport abort is not proof of
provider-side cancellation or refunded billing.

Daily quotas are process-local and reset with a process restart. They are not a
durable account-wide spend limit. No startup task calls Inworld.

## Backend checkpoint (Android and independent HTTP review still in progress)

- Baseline backend suite: 64 passed, 0 failed, 0 skipped.
- New adapter tests first observed 10 failures: unsupported INWORLD provider.
- Cleanup regression first observed an unclosed transport; repaired and tested.
- Environment factory: 3 failing cases → 3 passed.
- Explicit smoke command: 5 failing cases → 5 passed.
- Current backend suite: 84 passed, 0 failed, 0 skipped.
- Syntax check of existing backend and new smoke command: running/awaiting final
  combined check at this checkpoint.
- Raw checkpoint evidence: `docs/evidence/inworld-router-20261104/`.

## Next steps and limits

1. Finish Android Inworld entry/client/tests, and independent real-Express/local-
   upstream HTTP tests; then review the complete diff.
2. Run the final combined backend tests, Android compile and scoped host tests.
3. Update this handoff and evidence with actual results before final publication.
4. Before a real Inworld call, verify only that `INWORLD_API_KEY` is configured.
   If unavailable, ask the owner to configure it in backend secrets; never request
   or persist a key from chat. `backend/README.md` has the exact configuration.

Live Inworld response, deployment, APK signing/installation and Fold7 checks are
**NOT_RUN**. No merge or deployment is included. The Render connector had no
selected workspace; no Render configuration was inspected or changed.

## Reproduction and rollback

`npm --prefix backend test` and `npm --prefix backend run check` run offline tests.
The explicit `npm run smoke:inworld -- --confirm-live` must run in the configured
backend environment, only after conscious live-call authorization; it can consume
provider credits and logs status flags only.

Disable via `INWORLD_MENTOR_ENABLED=false` and restart the backend. Reverting the
scoped PR returns the previous provider list. No state migration or model training
was introduced. Preserve sibling branches, release signatures and owner data.
