# Frozen independent holdout and combined components

Source checkpoint: cbb9b18b. Candidate retriever frozen by its implementer at
c3e52210ba80307e53e70265cad7d90be4ad680b before holdout disclosure. Independent
fixture frozen at d698969f11a00806286c7864640d9af5bad69471; SHA-256 remains
088a7fc5a42017d68f21ce019d067d278c7b58f055321b3bf0931691eebeb339.

The independent baseline was 25 cases: 7 passed, 18 failed. The unchanged
holdout now passes all 25. Combined focused compilation and JUnit succeeded:
**98 tests passed, 0 failures**. All five changed production components were
compiled together against the frozen real Android baseline dependency classes.
The warning lines are expected deliberately failing listener tests.

These results establish synthetic identity/privacy/provenance behavior and
component integration. They are not model generalization, Activity, device,
GPU, audio or complete Gradle acceptance. Full commands, source hashes and
raw output are alongside this receipt. No holdout test was weakened or edited.
