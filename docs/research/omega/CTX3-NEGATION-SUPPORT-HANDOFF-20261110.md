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

## Reproduced causes and small correction

The contrast parser accepted relative OTHER/PREVIOUS selectors, which resolve
against the old selected focus instead of the explicitly rejected ordinal.
Only two different absolute ordinals are now admitted in a contrast. Relative
or self-replacing contrasts clarify and clear the candidate set; standalone
OTHER/PREVIOUS keep their existing behavior.

Every trailing `nicht` previously rejected an ordinal, including ordinary
`startet`/`geht` predicate questions. Those bounded predicate forms now retain
their source and the candidate set. Explicit choice rejection and unsupported
trailing-negation forms remain conservative. Sentence-initial `Warum`/`Geht`
are recognized as ordinary words; unknown named entities remain blocked.
This is not unrestricted German grammar: other predicates or added modifiers
can still require clarification. No inferred model interpretation was added.

An initial broader correction passed 111 existing/new methods but a further
negative control exposed `ich nehme die erste nicht` selecting the rejected
candidate. The retained review-red log records that failure. The final
correction only admits the named predicate forms; it does not generally allow
other phrases ending in `nicht`.

## Actual validation

Kotlin 2.4.0 / OpenJDK 21, actual production source, no substitutes or stubs.
Evidence directory: `docs/evidence/ctx3-negation-support-20261110/`.

- Initial RED: 20 methods, 4 failures.
- Self-review RED: 21 methods, 1 failure on the intermediate correction.
- Frozen baseline with final tests: 21 methods, 4 failures.
- Final directed checks: 21/21 PASS.
- Existing source-bound context/voice harness: 112/112 PASS, no skips.
  The 21 directed methods are included in 112; do not add the totals.
- `git diff --check`: PASS.
- Full repository/Gradle/Android/model/Ctx3Shadow E2E/Fold7: NOT_RUN.

The baseline and candidate used the identical final test files. Input hashes,
tool versions, compiler output, initial/review failures and the final results
are retained. The recorded input HEAD is the documentation checkpoint with
the tested diff; hashes bind the actual source that was compiled. These are
authored regression cases, not a new independent language holdout or an
intelligence/latency improvement measurement.

Reproduce the source-bound harness without downloading dependencies:

```sh
KOTLINC=/path/to/kotlinc \
CONTINUUM_JUNIT_JARS=/path/to/junit-4.13.2.jar:/path/to/hamcrest-core-1.3.jar \
bash scripts/continuum-context-cinema-check.sh /tmp/ctx3-negation-check
```

For the directed baseline, compile the base commit's Ctx3Context.kt together
with this branch's Ctx3ContextTest.kt and Ctx3OrderedCorrectionTest.kt. Run both
JUnit classes against that output; the four recorded failures must return.
For the candidate, compile those same tests with this branch's production file.

PORTIERT: existing opt-in SHADOW resolver in this support branch; #716/#715
integration remains for their named owners.
VERIFIZIERT: 112 pure-JVM methods on the recorded source hashes.
GERÄTEBESTÄTIGT: NOT_RUN. No ordinary-chat improvement is claimed.
Rollback: revert only this support slice; no persisted-data migration.
