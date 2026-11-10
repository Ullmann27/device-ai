# CTX3 negation review support

Base: PR #716, remote `00cf15d925d2c114d8ce57ee23dac473a89e6e0f`.
The isolated local base `1d71e4dfe4a801157bcc73a0a40f7e1bac90d008`
has the identical tree `13445d2b227e561c06bcada82eb6584fe3bbb6a0`.
Branch: `fix/ctx3-negation-support-20261110`.

## Bounded assignment

Reproduce the two open #716 review findings against actual production sources:
relative contrast must never select the rejected option; a negated predicate
must not erase a valid ordered referent. Preserve explicit rejection, source
invalidation, ambiguous-reference and impossible-replacement controls.

Review anchors: `discussion_r4529363622`, `discussion_r4529363821`.
The latest Gitar response reports both unresolved and auto-apply disabled.
No newer repair was found when this checkpoint was created.

Expected examples: relative contrasts ask for an explicit alternative;
`warum startet die zweite nicht?` retains source s2; explicit `die erste nicht`
rejects the choice; invalid replacements cannot revive focus on follow-up.
Use the existing Kotlin classes and JUnit cases. No duplicate context core.

## Integration and limits

Immediate reviewer/integrator: the existing context owner of #716.
Canonical eventual target: Cinema owner, #715, after exact-source verification.
Experiments: EXP-06 source identity, EXP-07 negative regression evidence,
EXP-12 invalidation, EXP-14 existing CTX3 OFF/SHADOW boundary.
No API, schema, memory, permission, model, renderer or default-activation change.
No main, protected #706, signing, release or installed-device mutation.

Initial checkpoint: WIP; reproduction and candidate tests NOT_RUN.
PORTIERT: no candidate yet. VERIFIZIERT: NOT_RUN.
GERÄTEBESTÄTIGT: NOT_RUN.
Rollback: revert only this support slice; no persisted-data migration.
