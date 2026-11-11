# Maya owner-turn network veto

Base: `fd1de22cf3f6ed159601c9b944cc25c86f0fc221`, PR #747 integration branch.

## Cause and connection

The existing research boundary understood explicit offline requests, but the
Maya response path checked only global internet mode. An online global setting
therefore did not preserve an explicit offline/without-cloud owner turn at the
speech boundary.

`NetworkTurnConstraint.forbidsExternalSpeech` now identifies explicit offline
answer or transport prohibitions. It deliberately excludes implicit local
device routing and research-only restrictions, such as "without internet
research". Existing research restrictions are unchanged. The method never
grants access: the caller must still enforce Maya selection, local credentials,
global internet consent and the bound consent revision.

The companion Activity patch binds this predicate to the original owner turn
and uses it before selecting Maya. This policy commit alone is not the Activity
integration; both patches must be combined before frozen-source APK QA.

## Verification

Kotlin 2.2.21 / OpenJDK 21 / JUnit 4.13.2: 16 tests PASS in `MayaSpeechTest`
and `CloudSpeechTurnConstraintTest`. This includes no provider construction
for an explicitly offline turn, resumption on a later unrestricted turn,
existing provider cancellation, stale callbacks, long answer chunks and
research-only contrast cases. The first policy iteration incorrectly blocked
"Suche nicht im Internet." and its failing contrast test was corrected before
the passing run. This was a development regression, not an Android baseline
execution.

Independent review added normal trailing negation, double-negated offline
requests, clear quoted terminology and explicit corrections. The final six
policy tests run against initial policy commit `9f53e69f` reproduce two
failures: "Nutze das Internet nicht" and "Bitte nicht ohne Internet arbeiten".
The corrected policy passes all six plus the ten provider tests. Only clearly
framed quoted explanations/translations are excluded; a quoted instruction
or an explicit veto outside the quoted text remains effective. This remains
a bounded phrase policy, not a claim of complete German understanding.

Compile the six pure sources `MayaSpeech`, `ApprovedSpeechChunkQueue`,
`JarvisVoiceProfile`, `NetworkTurnConstraint`, `OwnerChatIntentRouter` and
`LocalClockIntent`, plus the two test classes, against JUnit/Hamcrest; run both
test classes with `org.junit.runner.JUnitCore`. No Android dependency is needed
for this scoped check. Full Android integration and actual ElevenLabs/Fold7
playback remain separate checks.

Maya voice/model/settings are unchanged. No paid request, key access, model
change, signing, publication, installation or main merge occurred. Rollback is
the scoped policy/test commit together with its Activity caller patch.

## Android ICU correction

Android review found an unsupported embedded `U` flag in the new policy.
The Java host accepted it; native ICU 76 rejected the actual flag combination
with error **66315**, offset 5. Android documents that `UNICODE_CHARACTER_CLASS`
is unsupported and Unicode classes are always used. The correction removes
only the embedded `U` and makes the initial letter boundary explicit in the
two patterns that can begin with an umlaut. Other policy semantics are retained.

Official references:
https://developer.android.com/reference/java/util/regex/Pattern#UNICODE_CHARACTER_CLASS
https://unicode-org.github.io/icu/userguide/strings/regexp.html

Reproduce with `python3 scripts/continuum-maya-icu-regex-check.py`. It loads
native ICU, confirms rejection of the old embedded flag, then compiles all
22 actual raw policy patterns. Current result: **22 PASS**, negative-control
error 66315. Missing ICU fails explicitly; this is not a silent skipped gate.
The companion JVM regression inspects every instantiated policy regex and
rejects reintroduction of embedded `U`. The combined Maya/provider/timing/policy
JVM run now has **25 PASS**. No full Android or physical Fold7 result is implied.
