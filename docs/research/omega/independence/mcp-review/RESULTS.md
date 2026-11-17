# Independent MCP and canonical read-path result

**PASS within the tested host scope:** 32/32 independent test groups on clean
combined source `c900dfa8ec022cb0db31f5f74a172000e6d6ca3d`.
This combines service candidate `0c54a7bc` and MCP candidate `5cf463a7`.
The exact source remained unchanged throughout the 19.414-second run.
Raw TAP, command, tool versions, before/after source identities and file hashes:
[`logs/full-c900dfa8.tap`](logs/full-c900dfa8.tap) and
[`logs/full-c900dfa8.json`](logs/full-c900dfa8.json).

This is independent protocol and read-path acceptance. It does **not** attest
a new neural repair, an executing coding daemon, a connected ChatGPT client,
Android/Fold7 performance, a release, or universal security.

## Actual execution and evidence boundaries

| Layer | Actual execution | Result |
|---|---|---|
| HTTP transport | 22 groups using real TCP/HTTP, counted synthetic read providers, no shared SDK/test helpers from production | PASS |
| Canonical reader | 8 groups using actual CCC1 configuration, authenticated journal, Kotlin profile store, current grant and source checks | PASS |
| Full read path | Real HTTP → fixed production MCP child process → canonical service reader → persisted profiles/journal | PASS |
| Profile bridge | Existing compiled Kotlin store, 20 seeds, pagination, real rename and private working notes, fresh metadata read | PASS |
| Neural coding | No inference or patch execution initiated by this reviewer | NOT_RUN |
| Chief final merge SHA | Requires rerun by separate integrator after integration | NOT_RUN here |
| ChatGPT, Android, Fold7 | No client connection, Android build, installation or physical device test by this reviewer | NOT_RUN |

Canonical fixtures create a real **ABSTAINED** journal in a temporary synthetic
repository and record zero model calls. Fixture setup has the runtime key so it
can construct adversarial authenticated data; that key is neither returned by
MCP nor supplied to any model/workspace. A deliberately fabricated CONFIRMED
journal verdict is used only as a negative test and is rejected because actual
execution evidence is absent. No invented terminal or model output is counted
as a successful repair. Fixture data and processes are cleaned up.

## Defects found and resolved

1. Independent transport test B02 found that `limit: null` was accepted despite
   an integer-only input schema. Builder `3ca1dd9a` rejects it, and also closes
   the analogous `arguments: null` case. Original and corrected raw runs remain
   in `logs/`; see `PROGRESS.md` for harness setup errors separately identified.
2. Read-path source review found `forge/lint/echo` instead of the actual stable
   `bot-forge/bot-lint/bot-echo` identities. Service `fd51d648` corrected the
   connection and the actual-store tests exercise it. This was a source-review
   finding, not a measured neural improvement.
3. MCP builder identified that unknown GET sessions returned405 before session
   validation. The independent GET probe reproduced RED on `1aae431d` and GREEN
   on the final combination with `5cf463a7`; unknown/terminated sessions now404,
   ordinary unsupported SSE GET remains405.
4. MCP builder identified missing fresh profile-classpath verification before
   read-time Java execution. Independent testing reproduced RED by adding a
   harmless file to a disposable copy of actual classes: the old reader still
   returned metadata, so this was not a class-loader failure. The final reader
   rejects the changed classpath for both profiles and evidence. Source
   `0c54a7bc` includes this fix.
5. Independent source review identified unchecked snapshot status/nested values
   and report interpretation. The service builder added a MAC using the
   existing CCC1 key, typed nested projections and canonical status derivation
   before this attack test ran. The final tests verify canonical ABSTAINED
   cannot become verified success through snapshot text, and injected synthetic
   secret fields are omitted. No earlier runtime RED is claimed for this item.

Preserved genuine counterexample logs:

- `logs/transport-83b805cb.tap` (contains one product schema failure and three
  separately documented harness interface mistakes).
- `logs/get-session-1aae431d-red.tap`.
- `logs/classpath-pin-1aae431d-red.tap`.

Initial `canonical-fd51d648.tap` failed fixture initialization because the full
development classpath included unnecessary compiler/Android jars beyond the
service budget. The maintained minimal runtime classpath resolves this without
relaxing product limits. Initial `canonical-1aae431d.tap` contained six passes
and one malformed negative-fixture envelope missing its required verification
ID; the fixed fixture is in the final unchanged acceptance test. These setup
failures remain visible and are not counted as product defects.

## Security/protocol coverage and limits

The 35 frozen cases from `cc3da6e1` are represented by 32 compound test groups
and source/document review. Authentication is required on each HTTP call;
session IDs and role strings are not credentials. Tests cover query/body token
attempts, duplicate headers, Origin/Host rebinding, unsupported verbs, malformed
JSON/UTF-8, forged/prototype tool names, null/unknown arguments, body/result/time
bounds, sessions/rotation/restart/expiry, read concurrency and per-session/rate
budgets, cancellation and cursors bound to both tool and session.

Current canonical source and grant checks reject stale reads. Integrity tests
cover altered metrics MAC, task fingerprint, journal head and content digest,
missing independent receipts, and symlinks/hardlinks across result, report,
grant, key and journal paths, including a symlinked ancestor. Read operations
leave canonical control-plane file contents unchanged. Profile reads expose
metadata without private working context, conversation IDs or capability
promotion. A registry entry remains REGISTERED, not an active model.

OS same-UID owner processes remain trusted. This review is not a sandbox test
for arbitrary owner code, nor a proof against every TOCTOU race or denial of
service. No energy or Fold7 latency measurements were taken. TAP durations are
host review times, not production model or coding performance metrics.

## ChatGPT compatibility review

`OWNER-ACCESS.md` accurately separates the running local bearer profile from
actual ChatGPT support. The reviewer independently fetched the three official
OpenAI pages on 2026-11-03; URL/status/hash provenance is in
`OPENAI-SOURCES.json`. The actual pages describe public HTTPS or Secure MCP
Tunnel reachability, OAuth2.1 for authenticated MCP, resource metadata/PKCE,
and separate Platform tunnel permissions and ChatGPT workspace access.

The present loopback bearer service implements neither that OAuth flow nor a
tunnel. No ChatGPT connection was configured or tested. Local owner calls may
work while ChatGPT remains **NOT_CONFIGURED**. No public listener, remote data
transfer, token purchase or additional subscription was established here.

## Repeat on the final integration source

Use a clean checkout containing the accepted service and MCP files, then set:

```sh
export CONTINUUM_SERVICE_CANDIDATE=/absolute/path/to/clean/checkout
export CONTINUUM_MCP_CANDIDATE="$CONTINUUM_SERVICE_CANDIDATE"
export CONTINUUM_PROFILE_CANDIDATE="$CONTINUUM_SERVICE_CANDIDATE"
export GENESIS_HANDOFF_CLASSPATH="$(cat /path/to/compiled/runtime-classpath.txt)"
export CONTINUUM_REVIEW_RUNTIME_ENV=/path/to/already-approved/runtime-env.json
node --test --test-concurrency=1 --test-reporter=tap \
  tools/independence-mcp-review/transport.test.mjs \
  tools/independence-mcp-review/profile-bridge.test.mjs \
  tools/independence-mcp-review/canonical-reader.test.mjs \
  tools/independence-mcp-review/canonical-http.test.mjs
```

A real JDK must be in PATH for the tiny Java fixture compiler; production
Kotlin is not recompiled by these tests. The exact run used the maintained
`/workspace/independence-profile-aa52e7e6/runtime-classpath.txt`. Model weights
are not read or invoked. The configuration pins the already approved manifest;
it grants no new model or network authority. Quota remains `NOT_VISIBLE`.
