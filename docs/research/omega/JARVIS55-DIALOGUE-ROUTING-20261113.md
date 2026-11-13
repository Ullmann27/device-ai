# Code55: ordinary app topics must reach conversation

Integrator: `work/jarvis55-master-20261113`; canonical target PR #809,
`integrate/code55-apk-fable-20261112`. Parent source:
`753067835c3b33ff850f5c3e8d079d9dcd5eb742`.

The 04:42 UTC review on #809 identified a real source defect after the
candidate was frozen. `Warum funktioniert meine Kamera nicht?` was rejected
by a substring-based negation check. `Schreibe mir einen Text über den
Kalender` was treated as an unsupported app message. `AgentActivity` consumed
both refusals before the normal conversation/model path.

The existing parser now uses its bounded command grammar to distinguish a
negated action from ordinary conversation. A temporary affirmative form is
used only for classification; it is never returned as an executable plan.
Negation in a message body remains literal draft text. Explicit negative
commands, including `kein/keine/...`, never produce a Ready result.

References to the five OPEN_ONLY app names in a writing topic return to
conversation unless an explicit message or app destination is present.
Actual unsupported messages/search/edit actions remain blocked. No new app
capability, dispatch path, permission, model or event bus is introduced.

Five additional pure parser tests cover ordinary questions/topics, genuine
negated commands, unsupported app messages and literal draft content. Two
additional real-Activity tests verify that topics reach controlled inference
without app lookup/launch, and an explicit negated command reaches neither.
The existing focused harness adds the whole parser test class; full QA remains
unfiltered and its skip policy is unchanged.

Validation at this checkpoint: `git diff --check` PASS; Kotlin/Android and
new UI test execution NOT_RUN locally (no Kotlin/Gradle/SDK installed).
The existing source-bound focused workflow is requested by the commit.
New full QA and ARM64 assembly/audit on the corrected final SHA are required;
the 75306783 results cannot certify this change.

EXP-05/07 compatibility: existing conversation inference and negative-test
contracts retained; no experiment or weights promoted. Revert this isolated
change on the development branch if validation fails; never rewrite a shared
branch. Source integration, host verification and Fold7 acceptance remain
separate. Original signing/publication/installation NOT_RUN.
