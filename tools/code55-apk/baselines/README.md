# Historical APK validator reference

Exact bytes from `67bf048a7d50a78b027da27a6b9814cbd2457372:docs/qa/apk53-a980/validate-unsigned-apk.py`; Git blob `90b8dcba60a62bf52b2d8fe1530d19f841328256`.

This test-only reference is pinned independently before the eight inherited gate functions and three protected constants are compared. It makes the existing comparisons available in a shallow CI checkout without network or orphan-history dependencies. It is never used to validate or sign the Code55 APK; `tools/code55-apk/validator.py` remains the runtime validator.

Observed failure: run 43353510013, job 131649935323 could not resolve the historical Git path. Its APK safety assertions are retained unchanged.
