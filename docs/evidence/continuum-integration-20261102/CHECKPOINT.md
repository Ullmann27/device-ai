<!-- coderabbit-ai-generated:v1 -->

# Reliability follow-up checkpoint — 2 November 2026

Baseline: PR353 `0b7010461ccb510ed3e3885a7349fdc4e4fa89da`, based on PR351 `057474de37d6e20d993bd64f166b6585bc4a357a`. Remote checked again: no newer integration repair. User's master directive explicitly authorizes continued research beyond the historical release freeze. No release/signing/ToolGate changes.

## Proven repairs

- **Completion I/O:** Gitar's PR353 comment (not a CodeRabbit finding) was reproduced: ENOSPC while creating completion suppressed the owner-abort receipt. The owner receipt is now attempted first. Completion write failure always returns1, never overwrites an existing file, and emits bounded machine-readable outcome metadata to stderr so the actual child code/signal is retained diagnostically. That diagnostic is not completion authority. This deliberately extends the existing fail-closed exception to exit passthrough when completion delivery fails. Normal output/code/signal passthrough remains tested. No successful completion can follow an abort.
- **QA false success:** a real Kotlin compiler heap exhaustion returned137 but `android-kotlin-compile-check.sh` printed `KOTLIN_COMPILE_CHECKED` and exited0 because missing-LiteRT diagnostics were also present. Fatal exit statuses and compiler exception diagnostics now fail. Ordinary diagnostic exit1 solely in the unavailable LiteRT files retains the documented partial-check behavior. This remains neither an APK nor a full SDK compile.
- **Eleven Node failures:** all reproduced in64 selected source-contract cases (53PASS/11FAIL). They referenced code moved by earlier PRs: on-device speech callbacks, process-owned imports, unified mentor dispatch, layered legal/Wikipedia provider, centralized archive quotation, consent restoration and stricter scheduler gate. The same64 now pass. The security predicates are checked at the actual caller and implementation; tests are not proof of Android device behavior.

## Observed checks

| Check | Before | After |
|---|---|---|
| Nine affected Node contract files |53PASS/11FAIL, exit1|64PASS, exit0|
| Completion I/O cases |2FAIL, exit1|included in process gate|
| Process/consumer gate |prior35PASS reused baseline|37PASS, exit0|
| Host compile classifier |1PASS/1FAIL, exit1|2PASS, exit0; seven exit/diagnostic scenarios|

Raw byte-preserved logs are in this directory. Full expanded Node run and native Kotlin integration are still running at this checkpoint; no final green claimed. Storage/coding/architecture32PASS from PR353 reused unchanged. Storage's historical timeout cause remains unproven and its production implementation unchanged. Android Gradle/lint/APK/Fold7 NOT_RUN.

## Review and continuation

GitHub API inspection of PR352/353 found no completed CodeRabbit review. Gitar's completion finding is addressed here; its PR352 documentation finding belongs to a different unmerged documentation branch and is not silently edited. No independent CodeRabbit approval is claimed.

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:201128d7816feafd16c7d71f890dee281f92a47f87eb99585e0200dace6b1212 -->

## Broader Node follow-up

The expanded command `node --test --test-concurrency=2 .github/continuum/*test.mjs scripts/*.test.mjs` ran1944 cases:1926PASS/5FAIL/13SKIP, exit1. This adds all script tests beyond the previous narrower1367-case scope. Two failures were proven fixture leakage: BASH_ENV restored the sandbox gh wrapper ahead of fake gh/git, causing real authentication/network calls instead of synthetic cases. Only these test subprocesses now clear BASH_ENV; all access denial, origin preservation and secret-redaction assertions remain. `node --test scripts/continuum-local-access.test.mjs`:3PASS, exit0. Production GitHub access code and signing code unchanged.

Three historical live-release source-gate assertions remain failed in the broader run: one expects today's research tree to equal frozen20261027 source blobs; two expect later rejection messages even though this research branch is already rejected. On a detached PR351 worktree, that seven-case file itself gives6PASS/1FAIL (old inventory already mismatches app-navigation-wiring); the two other cases pass because detached HEAD bypasses only the branch-name check and still rejects missing approvals. These are source/branch-dependent historical fixtures, not evidence of a signing bypass. No release manifest, approval pin or signer was relaxed. Final integration follow-up will retain these failures explicitly.

Next: publish the independently tested ephemeral research-context reset and focused QA entry point in a separate dependent draft. This draft is not a merge instruction.

<!-- /coderabbit-ai-generated:v1 -->
