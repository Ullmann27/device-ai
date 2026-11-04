# Memory context boundary result

Baseline application tree: PR #483 `578bdfd1`; 31 selected existing tests pass
under real Android Gradle compilation. The copied baseline classpath is frozen
in the session evidence directory; its application jar is not replaced during
focused tests.

New test commit `17812cdd`: **4 tests, 4 expected assertion failures**. The
unchanged compiler admitted extra USER REQUEST headings from both memory
content and IDs; it also omitted original/independent source references and
the evidence frame. Kotlin compilation succeeded before the RED run.

Candidate: AiContextCompiler now uses the existing complete research-text
sanitizer for memory IDs, content and source references. It frames each complete
record as evidence, keeps validation/provenance labels and original included
IDs, and preserves atomic selected-group budgeting. No source data is changed.

GREEN: **28 tests passed, 0 failures**, consisting of the 4 new regressions,
AiContextIntegrityTest, AiContextCompilerTest, ResearchPromptControlTokenTest
and ResearchSanitizerBudgetTest. This is direct Kotlin/JUnit over the frozen
real Android application/test classpath. Full combined Gradle/device acceptance
is separate. The input fixtures are synthetic; no owner memory was accessed.

Raw logs: `red-junit.log`, `green-junit.log`; exact compiled source hashes are
listed in `source-hashes.json`. The runner's source snapshots and complete
dependency/command provenance remain in the session QA directory for later
combined verification. No model-capability gain is claimed from these tests.
