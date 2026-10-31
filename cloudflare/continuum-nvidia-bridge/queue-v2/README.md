# Continuum NVIDIA Queue v2 — direct, event-driven research (DRAFT)

**Not deployed.** Research code only. Back up and compare the *currently live* Cloudflare Worker v2.0.0 before replacing any production code.

## Operation

Cron `*/10 * * * *` only starts/restarts a chain. A Queue consumer runs one NVIDIA Nemotron 3 Ultra call, stores its output as `proposal_unreviewed`, and immediately enqueues the next research question when successful. There is **no 4-hour or hourly fixed delay**. The same D1 table atomically blocks overlapping jobs.

Default is `NVIDIA_ENABLED=false`. The new, rotated secret `NVIDIA_API_KEY` must be configured only in Cloudflare Workers Secrets. No private user memory or repo data is sent to the model. No generated code is executed and nothing merges into main.

**Free-safety budgets:** 48 call *attempts* per rolling 24 hours and 400,000 reserved output tokens per rolling day (configurable variables; still bound by NVIDIA's own more restrictive allowances). On HTTP 429, honor Retry-After; on 400/401/402/403/422, disable new provider calls. Failed requests do not immediately generate further messages.

## Cloudflare deployment gates

1. Back up actual deployed Worker source and D1 data; compare and integrate handlers rather than blind replacement.
2. Create Queue `continuum-nvidia-research` in the Free account.
3. The Queue has already been created by the Cloudflare Agent. Existing Worker must have producer binding `RESEARCH_QUEUE`; consumer on the same Worker with `max_batch_size=1`, `max_batch_timeout=1`, `max_concurrency=1`, `max_retries=0`.
4. Preserve existing D1 `DB` binding to `continuum-jarvis-memory`, Cron and public /health + /db-status (read-only). Keep /db-test disabled.
5. The Worker now runs additive self-initialization through its existing D1 `DB` binding on the first Cron/Queue event (only CREATE TABLE/INDEX IF NOT EXISTS and INSERT OR IGNORE). This can work even if the Cloudflare Agent's separate API token cannot write to D1 via REST. Alternatively execute queue-v2/schema.sql manually in the D1 console. Verify existing rows survive.
6. Run tests with Node 22+ `node --test tests/queue.test.mjs`. These use real in-memory SQLite via Node's built-in experimental sqlite module.
7. With NVIDIA flag **false**, confirm Cron, queue delivery, and zero external model requests in live Cloudflare logs.
8. Verify NVIDIA Developer Program free prototyping credits, rotate previously exposed credentials, store the **new** key as a Cloudflare secret. Only with user-approved free usage enable the flag.
9. Verify actual HTTP 2xx from NVIDIA, `proposal_unreviewed` in D1, a *second* Queue message generated directly after the first finishes, and no concurrent duplicate.
10. Separately fix or disable the obsolete failed GitHub Actions schedule *after* the Cloudflare chain actually works.

## Authenticated D1 status queries

```sql
SELECT state, blocked_until_ms, last_error FROM continuum_nvidia_control WHERE id=1;
SELECT sequence, requested_at_utc, topic, status FROM continuum_nvidia_jobs ORDER BY sequence DESC LIMIT 20;
SELECT status, COUNT(*) FROM continuum_nvidia_jobs GROUP BY status;
```

## Known limits

Cloudflare Workers Free currently has a 10 ms CPU-time limit per invocation and 50 external subrequests per invocation. Queues Free has 10,000 operations per day and 24-hour retention. The handler and real NVIDIA response must be measured in Cloudflare, not inferred from local tests. No guarantee of exactly-once *provider* execution after crashes; stale jobs are marked as unknown, not blindly replayed. Every output remains an untrusted hypothesis until independent CCC1 proof.

## Verification

16 local SQLite-backed tests passed on 2026-10-31 for the equivalent queue-v2 implementation. PR code and test suite are staged but the GitHub Actions runner problem currently prevents an independent hosted CI proof. **No direct NVIDIA invocation, Cloudflare deployment, or device test has been independently observed.**

## Private ZIP transfer to Cloudflare Agent
The Agent cannot access the private GitHub PR. Download `Continuum_Cloudflare_NVIDIA_Agent_Integration.zip` from the ChatGPT conversation and attach it to the Cloudflare-Agent thread. It includes the tested source and instructions. Never send the NVIDIA key in chat: store a rotated new key only in Worker Secrets. The Worker can self-bootstrap DB schema through `env.DB` but does **not** grant D1 Edit rights to the Agent's separate API token.

## Safety fix: time-limited explicit owner authorization (research branch)

The original Queue v2 accepted `NVIDIA_ENABLED=true` plus a provider key as enough to reserve and execute remote calls. Unlike the GitHub research executor in PR #273, this path had **no separate owner authorization**. This branch fails closed until **all** conditions are met:

- `NVIDIA_ENABLED="true"` is explicitly set after review.
- A rotated `NVIDIA_API_KEY` exists only in Cloudflare Secrets.
- `NVIDIA_FREE_TIER_CONFIRMED="true"` is set by an operator **only after independently verifying that actual NVIDIA requests are authorized and non-billable**. This flag does not check NVIDIA billing or entitlement.
- `CONTINUUM_OWNER_GRANT_AT_UTC` and `CONTINUUM_OWNER_GRANT_UNTIL_UTC` are valid UTC ISO-8601 timestamps, with an approval window of at most 24 hours. Example *format* only: `2026-10-31T10:00:00.000Z` / `2026-10-31T11:00:00.000Z`. These are not active grants.

Missing, invalid, expired, future-dated, or >24-hour owner grants prevent **both** reservation and Queue consumption. The worker checks again after claiming a queued job and before the outbound provider request. Changing a flag or expiry does not retroactively cancel an HTTP request already in flight. An approval is an **operational control**, not a cryptographic proof of the human identity behind a dashboard change.

The 10-minute cron is only a recovery mechanism; after a successful result the next approved job is still enqueued immediately, subject to the preexisting daily quota and consent expiry.

### Reproducibility

Run `node --test cloudflare/continuum-nvidia-bridge/queue-v2/tests/queue.test.mjs` from the repository root using Node 22+ with built-in SQLite support. The branch stores 21 test cases; they have **not** been run by GitHub Actions. Ten separate deterministic mock-D1/provider checks were executed in an isolated JavaScript runtime against the exact GitHub worker source blob; all ten passed. They are **not** evidence of live Cloudflare deployment or NVIDIA availability.

**Deployment remains blocked** until the existing Worker v2.0.0 source is backed up and diffed, the operator has Cloudflare D1 write privileges, and a real protected NVIDIA secret plus verified free entitlement exists. Do not merge this PR or replace the existing Worker without those gates.
