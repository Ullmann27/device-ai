# Independent standalone-service process checks

The acceptance criteria were frozen at `d1a280b64e092e063339b44e3e9426b3a7dcf43e`
before reading a candidate implementation or its CLI. This directory contains
the independent observer and executable counterexamples. It does not change the
production service, replace its scheduler, inject model output, or mint receipts.

Required environment inputs:

- `SERVICE_REVIEW_CANDIDATE`: clean candidate checkout to execute.
- `SERVICE_REVIEW_CLASSPATH_FILE`: existing compiled Kotlin profile bridge.
- `SERVICE_REVIEW_RUNTIME_ENV`: existing owner-approved local runtime config.
- `SERVICE_REVIEW_ISOLATION`: existing owner-approved isolated executor config.
- `SERVICE_REVIEW_EVIDENCE`: output directory outside either source tree.

The fixture creates a separate clean Git repository and seeds disposable bot
profiles through the actual `ChiefBotProfileStore` bridge. Cloud credentials and
provider-selection environment are excluded from each CLI child. Native model
and Android work are not launched by setup. Results must identify exact candidate
source and distinguish admission/lifecycle proof from neural coding success.

`process-fixture.mjs` is a transparent observer. Its command result retains actual
exit code, signal, output, elapsed time and timeout/output-limit flags. A failed
prerequisite is an error, never an accepted negative test. A failed teardown keeps
the disposable state for investigation instead of deleting a live daemon's files.
