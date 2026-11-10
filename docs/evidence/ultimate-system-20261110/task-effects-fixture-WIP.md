# ToolGate caller fixture checkpoint

WIP / UNVERIFIED. The full Android run on local ff28ae74 / remote 0d21f084 reproduced two NoSuchElementException failures in ContinuumTaskEffectsCallerUiTest. The reflection helper requests four parameters while createToolGate now has five (new optional systemCallId). Both callers now explicitly supply null for that parameter. All authorization, visible-arrival/departure and rejection assertions are unchanged. No production change. Targeted Gradle/native-host verification follows after the frozen full run finishes.
