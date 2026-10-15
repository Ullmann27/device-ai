# Continuum Developer Agent

Deterministic, zero-cost repair agent for this repository. No LLM, no paid API, no
external service besides GitHub Actions itself. It **proposes** changes as draft
pull requests; it never merges and never pushes to a development or default branch.

## Repair loop (one session)

```
failure detected ─▶ reproduce (own check run) ─▶ diagnose ─▶ propose candidates
   ─▶ critic ─▶ apply in isolated git worktree ─▶ run fixed check chain
   ─▶ VERIFIED? ── yes ─▶ patch + report ─▶ (publish job) branch continuum-agent/* + draft PR
        └─ no ─▶ reset worktree, next candidate (budget: 4 tests, 2 per strategy, deadline)
   VERIFIED = every check of the chain passed AND tests were really executed
   (not skipped, not file placeholders) AND no fewer tests ran than passed in the
   failing baseline; a green exit code without that evidence is INSUFFICIENT_EVIDENCE
   ─▶ every attempt is written to the repair ledger (experience)
```

| Role | Module | What it really does |
|---|---|---|
| Diagnostician | `.github/continuum/repair-diagnostics.mjs` | Parses the agent's **own** reproduction run: Kotlin K1/K2 `e:` diagnostics, JUnit XML (Gradle and `node --test`), Node module-load errors from TAP. JS error headers are recognized structurally across Node releases (`ReferenceError: …`, `ReferenceError [Error]: …` of Node 20.20/22.16, `Uncaught …`, nested `[cause]:`, TAP YAML `name:`/`stack:`), each error only with frames of its **own** stack; generic `Error`/`AssertionError` text never becomes a code diagnostic. Real reporter output of Node 20.20.0, 22.16.0 and 22.22.0 is kept as fixtures in `fixtures/node-reporter/`. Counts total/failed/skipped/file placeholders/executed/passed tests. Paths outside the tree are dropped. Problem identity = fingerprint without line numbers. |
| Solution developer + code generator | `repair-strategies.mjs` | Deterministic strategies producing minimal unique hunks: `KOTLIN_ESCAPED_NEWLINE`, `KOTLIN_KNOWN_FIX:*` (rules in `repair-engine.mjs`), `KOTLIN_MISSING_IMPORT` (symbol index + JDK/Android table), `KOTLIN_TYPO_REFERENCE` (Damerau–Levenshtein over lexer identifiers), `KOTLIN_NULLABLE_STRING_DEFAULT`, `JS_MISSING_IMPORT`, `JS_TYPO_REFERENCE`, `REVERT_HUNK_TO_PARENT` (regression culprit search: undo one hunk of the failing commit, closest to a diagnostic first, so unrelated changes in the same file survive; review required), `REVERT_FILE_TO_PARENT` (fallback, review required), `EXPERIENCE_REPLAY` (re-apply a hunk verified for the same fingerprint). |
| Critic | `repair-critic.mjs` | Rejects before any test: workflows, Gradle files, manifest, signing material, consent/ToolGate/identity/credential files, the agent's own code, test sources (tests are the specification; only the syntax-only newline repair may touch them), >3 files, >40 changed lines, removed assertions, new `!!`, process execution, `eval`, `@Ignore`/`skip`, URLs, swallowed exceptions. |
| Test verifier | `repair-loop.mjs` | `git worktree add --detach` at the failing commit; fixed check chains only (`node`: `node --test` on `.github/continuum/*.test.mjs`; `android`: `gradle :app:compileDebugUnitTestKotlin` then `:app:testDebugUnitTest`; `jvm`: local JVM harness). Kotlin incremental compilation is disabled because its caches produced phantom errors after failed builds. Nested repair sessions are refused. |
| Experience learning | `repair-ledger.mjs` (+ `repair-experience.mjs`) | Every attempt with diagnosis, strategy, hunks, test outcome, base commit, patch SHA-256, confidence, branch/PR. Strategy statistics per `"<diagnostic kind>\|<strategy>"` rank future candidates (Laplace); candidates proven to fail for a fingerprint are never retried; verified hunks are replayed. Persisted on branch `continuum-agent-state` with stale-write protection. |
| Publisher | `repair-publish.mjs` | Runs in a job **without build execution** and with the write token. Re-validates patch hash, critic verdict, that every check of the chain passed and that the evidence rests on executed tests (flags in the report alone are not trusted), `git apply`s onto the exact base, pushes only `continuum-agent/*`, opens a **draft** PR. If Actions may not create PRs, records the HTTP status and a compare link instead. |
| Orchestrator / CLI | `developer-agent.mjs` | `triage`, `repair`, `benchmark`, `watch`, `publish`, `publish-demo`, `ledger-save`. |
| Structural analysis | `code-analyzer.mjs`, `kotlin-tokenizer.mjs` | Lexer-based symbol index, import/package resolution, reachability from AndroidManifest components, `UNREFERENCED`/`TEST_ONLY`/`DEAD_CLUSTER` findings with confidence, `--chain A B` call chains. Name-based member resolution is an over-approximation: findings are evidence, not proof. |

## Triggers

* **Failed Android build on `copilot/**`** → `android.yml` jobs `repair` (read-only) and `publish-repair` (write). Works on the development branch today.
* **Push of agent code** → `continuum-developer-agent.yml` job `agent`: all agent tests, structural analysis, CI triage and the JavaScript fault-injection benchmark through the real loop.
* **Manual** (`workflow_dispatch`, or a push to the development branch whose head commit message contains `[agent-kotlin-benchmark]` / `[agent-publish-demo]`): `kotlin_benchmark` runs the Kotlin faults through the real Android Gradle chain; `publish_demo` pushes one verified benchmark repair as `continuum-agent/demo/<run>/fault-*` + `.../repair-*` and opens a draft PR repair → fault (never into a real branch).
* **Hourly** (`cron: 23 * * * *`, job `watch`): API-only check of the development head; the heavy Android repair job starts only if the head's Android build failed and no session for that commit exists in the ledger.

### Activating the hourly schedule

GitHub evaluates `schedule` **only from the default branch**. The cron line in the
development branch therefore has no effect until the owner places this workflow
file on `main` (copy or merge of `.github/workflows/continuum-developer-agent.yml`;
the workflow checks out the development branch for all agent code). The agent does
not push to `main` itself.

Minutes: the repository is private. The hourly `watch` job is API-only (≈1 billed
minute per run, ≈720 per month); Android repairs only run for red, unrepaired heads.

### Allowing draft pull requests

Creating PRs with `GITHUB_TOKEN` requires *Settings → Actions → General → Workflow
permissions → "Allow GitHub Actions to create and approve pull requests"*. Without
it the agent still pushes the verified branch and stores a compare link
(`pullRequest.status = NOT_PERMITTED`). Note: branches pushed and PRs opened with
`GITHUB_TOKEN` do not trigger other workflows; the verification evidence is the
agent's own isolated test run recorded in the PR body and ledger.

## Fault-injection benchmark (acceptance check)

`node .github/continuum/developer-agent.mjs benchmark --chains node=node,kotlin=android --out <dir>`

Each fault is an exact edit committed on top of HEAD in an isolated worktree; the
agent is not told the fault. Catalog (`repair-benchmark.mjs`):

| Fault | Origin | Expected repair |
|---|---|---|
| `js-typo-reference` | synthetic | `JS_TYPO_REFERENCE` |
| `js-missing-import` | synthetic | `JS_MISSING_IMPORT` |
| `js-masked-missing-import` | synthetic; the `ReferenceError` is swallowed by a `try/catch` | `REVERT_FILE_TO_PARENT` (only visible as wrong behavior) |
| `kt-historical-escaped-newline-48bd150` | **real CI failure** of head 48bd150 | `KOTLIN_ESCAPED_NEWLINE` |
| `kt-historical-set-takelast-373bddb` | **real CI failure** fixed by 373bddb | `KOTLIN_KNOWN_FIX:SET_TAKELAST` or revert |
| `kt-typo-reference` | synthetic | `KOTLIN_TYPO_REFERENCE` |
| `kt-missing-import` | synthetic | `KOTLIN_MISSING_IMPORT` |
| `kt-behavior-regression` | synthetic; duplicate-key check removed, unit test fails | `REVERT_FILE_TO_PARENT` |

## Honest limits

* No code model is used. The agent repairs **diagnosable, local** defects
  (syntax, references, imports, known rules) and finds regressions by reverting
  single hunks, then whole files, of the failing commit. It does not understand intent and
  cannot implement features or fix logic errors that are not regressions.
* A green check proves only what the tests check. Every proposal stays a draft
  and needs human review; every `REVERT_*` repair is marked review-required.
* Research direction (not implemented): a small local code model ranking or
  generating candidates, trained on the ledger's verified hunks. The ledger format
  already stores the needed (diagnostic, before, after, outcome) tuples.
