# Historical object prompts — follow-up to PR #769

## WIP / NOT_RUN checkpoint

- Root integrator: PR #769, `integrate/omega-apk54-20261111`.
- Branch: `work/omega-memory-history-20261111`.
- Frozen base: `c36258a853bbc6dacd8fbfa0dbae64f0f95a0d14`.
- Review: <https://github.com/Ullmann27/device-ai/pull/769#discussion_r4540082835>.
- Scope: EXP-06 conversation provenance, EXP-12 session continuity; no new database, model, authority or persistence.

The compact prompt projection misses ordinary retrospective change/correction
requests, `vorhin`, and singular inflected `Verlaufs`/`Verlaufes`. The three
reported requests correctly avoid deterministic recall but therefore reach the
model without their retained history. `zuvor` already works through
`Mode.PREVIOUS`; this follow-up protects that behavior explicitly.

Twelve new pure Kotlin regression cases cover the three reports, inflections,
past correction/change requests, previously supported recall, compact normal
and future tasks, unrelated objects, uncertainty, unresolved references and
history truncation. A fourth real Activity case sends the three requests through
the actual send button after the original turns have left the bounded recent
window. All tests are **NOT_RUN** at this checkpoint.

No Kotlin/Node/Gradle execution before root releases the shared QA slot. Pure
Kotlin runs will use `flock /tmp/continuum-omega-gradle.lock`; the root integrator
alone owns the later Activity/Gradle run. No APK, device, signing or release claim.

Prepared repair: select retained history for clear retrospective wording;
keep planned corrections, future changes, current-value tasks and old-object
adjectives compact. Preserve full internal evidence, uncertainties, truncation
markers and existing budget gates. Do not map ambiguous `vorhin` onto an invented
single previous value or broaden deterministic answer authority.

The independent review added explicit old/current comparisons, past-state
questions before a correction, and descriptions of the state after a correction.
Old-attribute and before-correction references take precedence over the compact
current-value view. A bare after-correction phrase does not turn a current-state
description into a history request. `answer()`, owner-event acquisition, the full
renderer and prompt budget gates remain unchanged. Root released the shared lock
after its Node run; the first source-pinned RED probe is now queued/running.

Acceptance: same new tests RED on the frozen production source, then GREEN with
the patch and existing memory/budget neighbors; Activity regression remains
NOT_RUN here until the shared root gate. Rollback: revert this follow-up; no
stored data migration is needed.
