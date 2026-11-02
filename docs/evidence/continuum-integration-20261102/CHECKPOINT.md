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

Next: finish the full Node gate, fix and validate the reproduced ephemeral research-context reset, then publish a separate integration follow-up with an architecture matrix and executable handoff. This draft is not a merge instruction.
