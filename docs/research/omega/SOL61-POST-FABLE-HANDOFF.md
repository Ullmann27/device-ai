# Sol61 post-Fable — working handoff / integration gates

Branch `research/continuum-sol61-post-fable-20261101`, based on the freshly verified PR334 HEAD `702b57ae5c2724a819048bcc3b7775272b48f2b3`. Fable code checkpoint5a528e29 and later complete handoff702b57ae were distinguished. Original331/317 worktrees retained; no main/publisher/signing/version/owner-state/model-weight changes. Package remains at.heinz.deviceai.continuumlocal, research build0.3.35/code36.

Required reading completed in the owner's exact order: AGENTS, NEXT-AGENT-ENTRYPOINT, FABLE51-HANDOFF, CONTINUATION, MASTER-PROMPT, ARGON-MATRIX, CDX1-ORACLE-BOUND. Argon PDF read as planning input, not verified capacity/quality. Existing experiment contract/register unchanged (relevant EXP05/06/11: local reasoning, provenance, existing orchestration). No broad branch merge:327–330/332/333 already ancestors;331's8417 code is inherited, only historical release-handoff doccc9a is absent.

## S1 checkpoint — actual code and host result

- ResearchDialogueHandoff now passes the real offline eligibility flag to the EXISTING JarvisUnifiedEvidenceContract; historical text stays in a sanitized untrusted frame, without an online/currentness assertion when blocked/unusable.
- LocalConversationContext.render defaults OFF, accepts actual bounded mode and owner veto; ASK requires an active authorization supplied by the caller. No permission is inferred from saved research.
- AgentActivity rebuilds the frame per model call after TurnScopedInternetMode bounds the current mode/revision. Original owner veto forces OFF. Active ASK status comes from existing ApprovedWebResearchSession instances of this same agent turn, never digest metadata.
- Tests cover OFF, ASK without/with grant, ALWAYS_ON, owner veto overriding grant, revocation and OFF→ALWAYS roundtrip, expiry, unknown retrieval, own-session isolation and prompt firewall equality.
- Regression reproduced against the ACTUAL Fable full host compilation: same OfflineDefaultRegression body RED(1failure) before /GREEN(1test) after; raw source/results retained.
- Full existing scripts/android-kotlin-unit-check.sh: **1514JUnit/277classes +4Checks PASS**. Official Maven android-all15,OkHttp4.12/Okio3.6,Shizuku13.1.5; JDK21/Kotlin2.0.21. This uses the preexisting compile-only LiteRT stub and generated Android stubs: **HOST ONLY, not Gradle/lint/APK/device**. Nine Node architecture-proof tests PASS. No Android source excluded to obtain an APK claim.
- QA pipeline extended only by exact branch whitelist and mandatory ResearchDialogueHandoff/TurnScopedInternetMode/JarvisUnifiedEvidenceContract suites. Existing checks preserved.

### Open S1 transport issue — next immediate small patch

Independent review found that native respondToTools() reuses an existing conversation and ignores the freshly assembled prompt. Consent revision changes between native continuations/rejected-tool retries can therefore leave an old online/FRESH prompt resident. Existing modelWebSessions final checks do not cover a device-only tool loop. Next: fail closed on changed consent generation before/after native continuations and before publishing results; add regression via existing turn policy, then full host/Android QA. Do NOT call S1 fully accepted until this is closed. No new native runtime/permission store is required.

## Milestone matrix

|Milestone|Current status|Next gate|
|---|---|---|
|S1 B4 offline|Text/frame patch hostPASS; native transport issue above OPEN|Guard actual continuations; full host+independent Android|
|S2 app integration|Fresh DAG: existing compatible code inherited; Fable702 has no complete Android acceptance|Existing Render QA on own exact pushed HEAD with real LiteRT0.18.0, all main source, JUnit/lint/unsignedAPK+AAB/proof/binary identity|
|S3 E7/EWM2 attributes|Existing location session+confirmed fact specialist inherited|Inspect existing owner/attribute scope; small extra relation with conflict/revoke/identity/session regressions|
|S4 learning feedback|Existing SWB/CRE/ledger mechanisms retained|Small measured feedback connection; no productive weight writes outside existing candidate gates|
|S6 RIS|OGD/API paragraph discovery inherited|Verified official article/pagination fixtures before a bounded provider change; no guessed statute ids/API fields|
|S5 CDX2|Not begun; Oracle experiment consumed|Fresh independent Blind-C; isolated role/pointer model, rule/CDX baselines separate; no claim of generation improvement from extraction|
|S7 other B4|TemporalRoleEvidenceLedger/ConstrainedResourceFabric require actual callers/data contracts|Connect only where valid-time/resource measurements exist, otherwise explicit research artifact|
|S8 worker|Existing typed jobs/repair pipeline preserved|Read actual worker/scheduler, one bounded real repair proof through existing contracts; no alternate database/paid-API requirement|
|Device/release|DEVICE_NOT_RUN; signing/owner gates retained|No connected Fold7. Prepare matrix; current actual code36 owner install requires future monotone release contract, not a silent version change|

## Reproduction

Use JDK21 with javac and kotlinc2.0.21 on PATH; CONTINUUM_ANDROID_DEPS points to the official seven jars documented by scripts/android-kotlin-compile-check.sh; CONTINUUM_JUNIT_JARS contains JUnit4.13.2+Hamcrest1.3; CONTINUUM_KOTLIN_STDLIB points to2.0.21stdlib. Run `bash scripts/android-kotlin-unit-check.sh <fresh-out>`; artifacts under docs/evidence/sol61-post-fable-20261101/s1. `node --test --test-reporter=tap scripts/continuum-architecture-proof.test.mjs` checks strict receipts. Real Android gate remains `bash scripts/render-continuum-m8-qa.sh` on independent Render; no GitHub Actions/publisher changes.

No historical Oracle/27ablation or Blind-A/B corpus is retrained/relabelled. Later checkpoints will append exact commits, results, integration matrix and an explicit next-agent task; PR metadata records the commit generated after this document without a self-referential SHA.

## S1 follow-up — native continuation / ASK preference fixes

Native continuation/rejected-tool recovery calls now use pre/post consent-generation guards; orchestration completion and the final UI publication recheck the original revision. Actual fresh native stream recreates its conversation; tool response continues the old one, so an epoch change aborts rather than assuming a newly rendered prompt was delivered. Persisted OFF/ASK/ALWAYS_ON are preserved on reopen/restart; only null uses the existing fresh-install ALWAYS_ON default, unknown corrupt preference fails OFF. 13focused policy tests and full existing1518JUnit/277classes+4Checks PASS;9proof tests PASS. Independent source review confirmed both closed paths. No owner/model data change.

Further concrete review finding remains OPEN for next immediate patch: original owner offline text must also bind the local agent's web proposal approval AND actual ToolRunner. A rewritten model query currently lacks that veto and could mint a fresh AlwaysOn session. The prompt/renderer alone is not an authority gate. Add a per-turn wrapper to the existing NetworkTurnConstraint/ToolRunner and an early approval rejection, then rerun full host/Android. S1 is not fully accepted until this is closed.

Independent full Render12152f94 PASS (dep-dbjktldg1s2s7381v0r0, live14:46UTC), real LiteRT0.18.0 compilation, full Gradle JUnit/lint, unsigned APK/AAB/schema3binaryidentity36/0.3.35; mandatory14suites. This is real Android-host acceptance of12152 only, not the later native/preference changes and not a device result. No LiteRT dependency/version/repository repair was needed or made.
