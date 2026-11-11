# PDF JUnit progress checkpoint

Base: `f0349f4cbe4474e6df07ddf4685463688abb6ef2`.
Integration owner: `work/pdf-scene-20261111`, PR761.
Status: source checks PASS; Gradle/listener execution NOT_RUN.

The existing `tools/pdf-scene-review/focused.init.gradle` retains all25 test
classes. `-PcontinuumPdfQaScope=all` is the default. `core` selects the18
nonvisual classes; `visual` selects exactly the seven existing document-scene,
native-sequence, STOP and window UI classes requested by the coordinator.
Unknown scope values fail. No test cases or assertions change.

With the existing `-PcontinuumUiEvidenceDir`, the same Test task appends to
`junit-progress.ndjson`. Every invocation has a unique runId and one run_start
record containing actual Git HEAD/tree/status/diff hash, selected scope/classes
and explicitly selected JVM/Gradle/environment values. No credentials or full
environment dump are collected. Test timestamps are epoch milliseconds.

Each actual afterTest callback writes class/name/result/start/end/exceptions,
closes and synchronizes the file before continuing, and logs one lifecycle
line. The top-level afterSuite callback writes a separate suite_end record
with the actual counts. Without that record the journal remains partial;
completed methods do not imply a completed suite. Existing journals append
new runIds rather than replacing prior evidence.

Source verification: Groovy3.0.22 parser PASS; static comparison to the base
confirmed the identical25-class list, core18/visual7 disjointness and exact
union. `git diff --check` PASS. No Gradle build or test process was started.

Next: integrate this one commit and use the same established Android command,
adding the desired scope property. Root keeps the running checkout unchanged.
Rollback: revert this commit; production app behavior and QA task guards are
unchanged. Existing experiment compatibility is unchanged; this only records
the current test task's events and selects explicit subsets of its same scope.
