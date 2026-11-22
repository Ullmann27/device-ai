# Continuum NVIDIA research bridge – isolated research candidate

**Not deployed. Do not merge or deploy blindly.** This uses the existing Cloudflare Worker **continuum-jarvis-scheduler** and the existing D1 database **continuum-jarvis-memory**. The deployed Worker v2.0.0 source export was not accessible through GitHub; back up and compare it before replacement.

Purpose: Cloudflare Cron invokes NVIDIA Nemotron 3 Ultra directly, without depending on GitHub Actions runners, then saves **untrusted** research proposals privately in D1. It never modifies GitHub, builds an APK, applies patches or accesses personal memory.

## Guarantees in code
- Default `NVIDIA_ENABLED="false"` prevents outgoing provider requests.
- Rotated credential is required in the Cloudflare Workers secret `NVIDIA_API_KEY`, never GitHub or source code.
- Single atomic D1 reservation enforces at most 3 **attempts** in a rolling 24-hour window, at least four hours between attempts, and 100 lifetime attempts.
- Failed responses count against limits; no retries. Model output is capped at 8,192 tokens; private result excerpt at 12,000 characters.
- Public `/health` and `/db-status` are read-only. `/db-test` returns HTTP 403. No endpoint exposes the research text.
- Only public-safe, general research questions are sent. No real repo source or device data is included.

## Controlled installation
1. Back up the actual Cloudflare-deployed v2.0.0 Worker first. Diff its routes, `fetch`/`scheduled` handlers and D1 usage against this candidate. This standalone example is **not** a verified safe drop-in replacement.
2. Apply `schema.sql` as an **additive D1 migration** in the Cloudflare dashboard. Verify that old rows persist.
3. Run `npm test` with Node.js 22 or newer. Verify both `GET /health` and `GET /db-status` in the Cloudflare preview environment with `NVIDIA_ENABLED="false"`.
4. Deploy only after comparing the current live code and ensuring `DB` binding and Cron stay intact. The `wrangler.toml` targets the production Worker name and would **replace** its code if deployed without review.
5. Rotate all NVIDIA credentials previously pasted into chats. Add the new key in Cloudflare Dashboard → Worker → Settings → Variables and Secrets as **secret** `NVIDIA_API_KEY`. Never paste it into a public chat, code file, or Cloudflare AI Agent prompt.
6. Verify the NVIDIA free-trial credit/charging policy before making calls. Only then explicitly set `NVIDIA_ENABLED="true"`; on false/missing key all ticks stay offline.
7. Query last receipts in authenticated D1 console: `SELECT topic, status, requested_at_utc FROM continuum_nvidia_runs ORDER BY requested_at_ms DESC LIMIT 10;`
8. View a proposal only in private D1 console: `SELECT topic, result_excerpt FROM continuum_nvidia_runs WHERE status='proposal_unreviewed' ORDER BY requested_at_ms DESC LIMIT 1;`
9. Once Cloudflare’s **real** scheduled call is proven, a separate reviewed PR should disable the redundant GitHub Actions ten-minute schedule.

## Abnahme
- Health 200, D1 status 200, unauthenticated DB write 403.
- At least one **real** Cloudflare scheduled event recorded.
- With flag false: exactly zero NVIDIA requests.
- With rotated secret and explicit opt-in: one genuine NVIDIA 2xx response saved as `proposal_unreviewed`; second tick inside four hours sends no request.
- Independent CCC1 RED/GREEN/FRESH GREEN is still required before accepting proposed patches.

The user's Cloudflare account is **not** available through this ChatGPT GitHub connector. GitHub source creation is real; Cloudflare deployment and NVIDIA calls are not claimed.
