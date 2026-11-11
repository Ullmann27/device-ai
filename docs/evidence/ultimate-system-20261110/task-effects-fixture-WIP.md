# ToolGate caller fixture checkpoint

WIP / UNVERIFIED. The full Android run on local ff28ae74 / remote 0d21f084 reproduced two NoSuchElementException failures in ContinuumTaskEffectsCallerUiTest. The reflection helper requests four parameters while createToolGate now has five (new optional systemCallId). Both callers now explicitly supply null for that parameter. All authorization, visible-arrival/departure and rejection assertions are unchanged. No production change. Targeted Gradle/native-host verification follows after the frozen full run finishes.

## Follow-up qualification

Resolved on remote source `3ae6e2252b00aa72ae42c9221cce648c8382ffbf`. All nine tests in the two corrected classes pass under Gradle. The exact-source full-suite comparison and original three failures remain preserved in `android-qualification.json`. No production/build source changed.
