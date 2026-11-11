# Complete inherited Core confirmation

Source: `3cb8df27c42fbc6db03d05b0f9993a8650be43af`, tree
`888938b1a9921a1c2043041d9dbfeecf4cf7d942`. Source and clean worktree unchanged
from run start to finish. Full journal ends in `suite_end`.

**98 cases: 97 PASS, 1 FAIL, 0 SKIP.** All 30 `ContinuumDocumentFlowUiTest`
cases and all six `ContinuumPdfSceneAcceptanceUiTest` cases passed.
The one failure is the unchanged strict native PDF decoder probe:
`NoSuchMethodError: long java.io.FileDescriptor.getOwnerId$()` in host libcore.
This is a failed host gate; real Android PDF decoding remains NOT_RUN.
No allowance, assertion or skip configuration was relaxed.

The process finished naturally with Gradle exit 1 after 52m19s; test execution
1,580,127 ms. It was not stopped at a session boundary. Raw log, complete journal,
JUnit XML archive and summary are adjacent. Earlier interrupted runs remain
historical evidence; this result supersedes their incomplete core confirmation.

Exact command (run from the unchanged root checkout):

```sh
env CONTINUUM_CAPTURE_SOURCE_COMMIT=3cb8df27c42fbc6db03d05b0f9993a8650be43af \
  CONTINUUM_QA_HEAP=1536m CONTINUUM_QA_METASPACE=512m \
  flock /tmp/continuum-omega-gradle.lock \
  python /workspace/scratch/6139925ecf70/toolchain/run_gradle.py \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumOwnerArm64=true \
  -PcontinuumStandaloneVersionCode=53 -PcontinuumLocalStandaloneQa=true \
  -PcontinuumUiWidgetTests=true -PcontinuumPdfQaScope=core \
  -PcontinuumUiEvidenceDir=/workspace/scratch/2d62a799e417/qa/baseline-core \
  -I tools/capture/force_capture.gradle \
  -I tools/pdf-scene-review/focused.init.gradle :app:testDebugUnitTest
```

The next combined run uses the existing Code53 test JVM memory harness
(SerialGC, two visible CPUs, one test fork, 1 GiB heap), preserved under `harness/`.
That changes execution resources only, with unchanged tests and PDF requirements.
Full Node QA shares the heavy-process lock to avoid parallel Gradle interference.
