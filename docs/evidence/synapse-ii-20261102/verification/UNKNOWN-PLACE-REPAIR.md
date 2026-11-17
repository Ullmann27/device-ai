# Caller-only unknown-place correction repair

Owned changes only:
- app/src/main/java/at/heinz/deviceai/cognitive/CertifiedLocationReflex.kt
- app/src/test/java/at/heinz/deviceai/cognitive/UnscopedLocationCorrectionTest.kt

Actual explicit route for `Beleg: Anna ist in Graz; seit gestern ist der Ort unbekannt.\nFrage: Wo ist Anna?`: baseline Proven -> patched Withheld (unknown-route-before.txt / unknown-route-after.txt). New tests RED 3/4 failures, GREEN with existing evidence suites 26/26. Test groups cover exact explicit source, same-source session, separate session turn before person filtering, multiple names/relations/places, unknown/unclear Ort/Wohnort/Aufenthaltsort, other-person location and unrelated benign controls, unchanged input list.

Minimal change: one REMOVE-ONLY regex for place-labelled unknown/unclear statements, checked both against the bounded session window before person filtering and at private prove entry across selected source items. The guard withholds; it never resolves a person, selects a new location, writes a fact or promotes a model. Ambiguous place corrections can conservatively withhold the whole bounded proof. It does not claim general German pronoun/retraction understanding or global confirmed-ledger revision resolution.

Focused compilation used fresh current CertifiedLocationReflex and LocalConversationContext together with the five test classes. Dependency precedence: unknown-green -> previously source-exact PR342 dialogue/probe/snapshot classes -> unchanged BASE335 dependencies. Baseline RED likewise freshly compiled the pre-edit current reflex. Source hashes in unknown-source.sha256; git diff --check passed.

Frozen probe/verifier, candidate, checkpoint and corpus all retain identical SHA256 (unknown-frozen-before.sha256). Frozen N08 prediction rerun into scratch retained exactly identical RAW, VERIFIED and RULE objects. RULE still has its original false positive; saved ten-case score remains 9/10. No benchmark/code/corpus tuning or training. Comparison evidence: unknown-frozen-N08-comparison.txt and unknown-frozen-N08-output.json.

No complete host build, Android APK, or device acceptance claimed here. Root owns final full-host rerun after this source change.
