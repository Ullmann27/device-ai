# Broad all-unit/all-widget attempt — interrupted, not a passing gate

The combined `testDebugUnitTest` + `lintDebug` invocation with
`continuumUiWidgetTests=true` made no visible class-level progress for more than
nine minutes after entering the test task. Root interrupted it with exit 130
to release the shared builder. The log contains no final test summary and lint
was not reached. The old XML files still belonged to the prior 114-test focused
run and are deliberately not counted as this attempt's result.

This does not establish a specific failing test or a pre-existing cause. The
regular full unit suite and configured NewApi lint are run separately with
per-test progress logging; the exact bot/widget scope already passed 114/114.
The broad all-widget suite remains UNVERIFIED unless a later recorded run
completes. No physical-device/native-model/release claim.
