# First combined implementation run

Actual result: **114 tests, 3 failures, 0 errors, 0 skipped**. Application and tests compile.
The three native render failures are an invalid test assumption (`ScrollView.single()`
matched the platform AlertDialog scroll widget as well as the composer); all remaining
111 tests passed, including four actual Activity/orchestrator/ToolGate fake-runtime
integration cases. This is not a whole-suite GREEN result. Fix the fixture selection
and repeat the same scoped gate. No native model, Fold7 or release acceptance.
