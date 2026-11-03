# Independent MCP review checkpoint

Frozen requirements: `cc3da6e167ec88a97a7466f53300111dd4af7e0e`.
Draft PR: https://github.com/Ullmann27/device-ai/pull/460.

## Verified so far

- MCP transport candidate `3ca1dd9a`: **PASS**, 19 independent Node test groups
  using real loopback HTTP sockets, Node v24.19.0, Linux x86_64. Exact before/after
  HEAD, clean status and file digests in `logs/transport-3ca1dd9a-full.json`;
  raw output in corresponding `.tap`. These use counting read fixtures and
  prove transport behavior, not local coding, neural inference or actual service
  integration. Tests include rotating bearer credentials, per-call auth,
  Origin/Host rebinding, sessions, malformed/oversized input, strict arguments,
  pagination and cursor scopes, concurrency, cancellation, timeout and evidence
  content-hash rejection.
- Actual Kotlin profile metadata bridge from chief source `773fbc94`: **PASS**,
  one independent compound host test. It seeds the real store in a disposable
  directory, retrieves all 20 profiles in pages of three, renames `bot-forge`
  through the real store, stores synthetic private notes, then verifies fresh
  metadata, stable ID/workspace, no acquired capabilities/rights, absent private
  context/object references, invalid arguments, and unchanged primary bytes
  under reads. See `logs/profile-773fbc94-green.tap`. Classpath is provided by
  the chief's compiled exact-source harness; this review compiles only the small
  Java fixture writer, not production Kotlin.

## Reproduced failure and repair

Transport `83b805cb` used `args.limit ?? 20`; explicit `null` therefore invoked
the canonical reader despite the declared integer-only schema. Independent
case B02 caught this. Builder commit `3ca1dd9a` rejects explicit null and also
rejects explicit null `arguments`. Both are included in the final 19 groups.

The initial transport log contains three harness integration mistakes in
addition to that product defect: the evidence selector was not the actual
`result`/`report` enum; duplicate headers correctly returned400 rather than the
harness's overly-specific401; and a wrong endpoint correctly returned400
rather than the harness's overly-specific404. The frozen oracle required
rejection for the latter two and was not weakened. The corrected harness uses
the public candidate interface and exact evidence projection/hash format.

`transport-83b805cb-adapted-red.tap` is an unfortunately named intermediate log:
the builder had already advanced to `3ca1dd9a` before that run, so it is GREEN
and is **not** evidence for `83b805cb`. The final digest-bound run supersedes it.
It is retained to avoid hiding the chronology.

Initial profile runs failed on test setup (javac absent from default PATH, then
capitalization of editable display name). Neither was a production failure.
The final run prepends `/workspace/continuum-build-env/jdk17/bin` and checks
identity independently of name capitalization. Raw failures are retained.

## Open gates

- Actual canonical service view and fixed MCP subprocess adapter: **NOT_RUN**;
  candidate is still being built. Source inspection already found unprefixed
  `forge`/`lint`/`echo` references inconsistent with actual `bot-forge` etc. The
  service builder was notified. Projection of unvalidated snapshot values is
  also under independent attack testing.
- Combined exact chief SHA: **NOT_RUN**.
- Android, ARM64/Fold7, live ChatGPT: **NOT_RUN**. No public listener, tunnel,
  deployment, write tool or model call was introduced by this review.

## Reproduction

```sh
CONTINUUM_MCP_CANDIDATE=/path/to/candidate \
  node --test --test-reporter=tap tools/independence-mcp-review/transport.test.mjs
```

For the profile test, set `CONTINUUM_PROFILE_CANDIDATE` to the reviewed source
checkout, `GENESIS_HANDOFF_CLASSPATH` to its compiled harness dependencies,
and include a real JDK's `bin` directory in PATH. Run the analogous
`profile-bridge.test.mjs` command. Fixtures never use owner data.
