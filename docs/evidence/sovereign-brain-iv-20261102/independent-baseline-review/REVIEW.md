# Fresh independent baseline review

Reviewed source: PR357 `d49031843b03af6ce21427a41e761d671aa05b58`. Verdict **CHANGES_REQUESTED**. No Critical finding established; three Important findings and one Minor. Independent reviewer did not edit the root/index, post external comments or resolve GitHub threads.

Important:

1. `AgentToolLayer.kt:280–291`: lease/cancel check, then clock check, then backend call are not an atomic start decision. Controlled-clock pause after lease check reproduces backend start after terminal TIMED_OUT and CANCELLED. aabd8ea7 fixed the earlier observer-interruption example but not this interleaving. Reproducer reflects possible scheduling, not measured Android frequency.
2. `ReadOnlyToolPlan.kt:38,51–52` and `BoundedAgent.kt:38`: a legal8000-character result plus labels violates AgentModelOutput's8000-character cap. IllegalArgumentException instead of AgentRunResult/TaskEvidence, zero episodes, BLOCKED event. This predates357, inherited from354.
3. `AgentActivity.kt:6295,6410–6412,6446` and `AiContextCompiler.kt:106`: the up-to7900-character task is duplicated; final15500-character cap cannot contain both copies plus context. Source-confirmed, Android Activity not executed.

Minor: original final-verification.json uses placeholder commands, not exact executable environment arguments. The new handoff/probe script supplies actual current paths and identifies their availability limits.

Verified in source: aabd8ea7 whole-task guard, router no-clipping checks and post-observer recheck; babba332 stronger source contract. Their presence and historical PASS records do not imply independent approval of final357.

Read-only fresh exact-source reproductions produced:

```text
TIMEOUT: returned=TIMED_OUT, backendBeforeRelease=false
TIMEOUT: backendAfterTerminal=true
CANCEL: returned=CANCELLED, backendBeforeRelease=false
CANCEL: backendAfterTerminal=true
OUTPUT: error=java.lang.IllegalArgumentException: Failed requirement., result=null, experiences=0, terminal=BLOCKED
```

Reviewer limits: did not rerun complete suites or independently recount the corpus. Oversized mandatory contradictions deliberately abort compiler; actual Activity presentation unexercised. Broader parallel/restart behavior and historical intermittent failures unresolved. Native SDK/Gradle/LiteRT/Fold7/APK/signing/install, S4–S6 NOT_RUN/NOT_IMPLEMENTED. Root independently reran these bounded probes and the72/54 reanalysis; original logs and source hashes are adjacent. No approval or completed repair claimed.
