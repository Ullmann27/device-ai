# Code46 complete Android regression: explicit skips

Source: `577f0931e7937ffcac02dc855a540f093facdb3b`.
Result: 3559 total, 3539 passed, 20 skipped, 0 failures/errors, 626 suites.
XML entries contain `<skipped/>` without explanatory text. The reasons below come from the
corresponding source annotations and assumptions, not inferred error messages.

| Count | Class | Actual prerequisite or unfinished evidence |
|---:|---|---|
| 6 | `ChiefPatternE2ETest` | Explicit `@Ignore("PENDING ...")` cases for worker instructions/tool scope/reporting, shared memory layers, on-demand skills, novelty-aware routines, urgent/info mailbox handling and resource admission. Test bodies are empty or comments. |
| 6 | `FirstThinkingRunE2ETest` | Explicit pending cases for one-time turn acknowledgement, shared-memory recall, scoped worker execution, instruction protection for tool output, reflection/diary and a structured next step. Test bodies are empty or comments. |
| 2 | `ChiefWorkspaceWiringHostTest` | `CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF` is not `1`; actual pool/mailbox/Node/Docker integration and cancellation were not activated. This does not establish that Docker is unavailable. |
| 2 | `ContinuumContrastiveParityTest` | `CONTINUUM_CONTRASTIVE_PARITY_DIR` is unconfigured; SWB1/SWB2 prepared Node training data required. |
| 1 | `Cre1InferenceEncoderTest` | `CONTINUUM_CRE1_PARITY_DIR` is unconfigured; prepared CRE1 checkpoint data required. |
| 1 | `ContinuumVisualEvidenceUiTest.fold7InnerMotionClip` | `continuum.ui.evidenceDir` is unset; optional Robolectric/2D frame export, not a physical-device test. |
| 2 | `LocalModelReloadLifecycleTest` | The shared setup requires `java.specification.version >= 21` for LiteRT 0.18 native-type host loading. The Android build runs JDK17. Both bodies remain enabled behind that assumption. |

The two model cases are `failureBeforeAnyModelWasLoadedCanRetryWithoutUnload` and
`failedRuntimeWithRetainedModelRefusesReloadUntilExplicitUnload`. Their skip does not establish
a model reload defect or successful runtime execution. Actual model-answer quality remains
outside this host suite.

The twelve Chief cases are open E2E proofs. Their historical dependency comments are partly
outdated: `TurnLifecycle`, `ChiefWorkerPool`, memory layers, the skill library, routines, mailbox
and resource admission already have code and wiring. Do not turn these skipped empty cases into
either passing tests or an unsupported claim that all twelve functions are absent.

Sources are under `app/src/test/java/at/heinz/deviceai/{chief,learning,localmodel}` and
`app/src/uiTest/java/at/heinz/deviceai/ContinuumVisualEvidenceUiTest.kt`. Exact skipped method
names and original JUnit evidence are preserved in the companion JSON and ZIP.
