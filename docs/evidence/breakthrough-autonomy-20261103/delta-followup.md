# Bounded public DELTA follow-up

Independent first assessment frozen at `b1bca66f54f80ef627ab17201e29335ee3210f86`
(PR407), case manifest `e12c9dc8`. Chief authorized one bounded follow-up in the
existing verifier and tests. These cases are now consumed development material,
not a new blind or independent evaluation.

Seventeen findings: two target aliases resolve to one testcase (1); supplied
run command/exit/timeout/cancelled/skipped/passed-target metadata contradicts the
genuine executions (11); patch mode accepts invalid schema/version or prohibited
authority flags (5). None demonstrates a fabricated execution: actual RED and
GREEN still execute and their observed counts are true. Parser-only truncated
XML diagnostics are outside this bounded repair.

Caller inspection: the only JavaScript callers are the verifier CLI and tests.
`Ccc1PatchReceipt.toJson()` emits schema/version and every metadata field, but
the old direct JavaScript API accepted omitted ancillary run fields. Preserve
those omissions; reject contradictions whenever such fields are present. Both
modes must require the known receipt schema/version and deny supplied authority
claims. Canonical Kotlin receipt parsing remains unchanged. Gradle claims cannot
be certified by this fixed Node-only executor.

Plan frozen before implementation: reproduce the 17 public mutation cases with
actual fixed-profile detached-worktree runs and save RED. Then bind supplied
metadata to observations, require unique resolved targets, and share envelope
validation. Preserve genuine repairs, extension, unique aliases and omitted
optional metadata. Run all CCC1 suites, freeze/push new source SHA, and request
DELTA-B consumed-case rerun plus separate Quality reserve/acceptance. No new
executor, cryptography, scheduler, model, owner data or Android source changes.
