# Independent native context/correction component evaluation

Registered before component execution. This is an addendum to `PROTOCOL.md`;
the 64-case LM1 TEST, labels and screening gates are unchanged.

`ContextCorrectionEvaluation.kt` freezes 24 synthetic cases and their expected
resolution, focus and effective text. A invokes the real existing
`LocalConversationContext.inspectRequest`; B invokes the research bridge and
`LocalConversationVoiceAdapter` over separate fresh instances of that same
context class. All transitive sources come from the repository; no placeholder
context, hand-coded replacement memory or fake Android API implementations.

Report these strata separately: automatic discourse handling; human-confirmed
transcript/meaning repair; review/input integrity; text-preservation controls.
Automatic broad-language benefit cannot be inferred from human-confirmed input
replacement, bounds checks, or a feature absent from the old API. `PASS_THROUGH`
does not mean the later language model understood a statement or performed the
correct action. Questions and multipart instructions remaining unchanged are
preservation-only controls, not semantic successes.

Measure A/B resolution, focus, effective text, context counts and basic host
latency. B latency includes constructing a fresh context/review; A latency starts
after context construction. These tiny times are diagnostic, not a comparative
performance benchmark. Preserve source hashes and raw per-case output.

Each case is also exercised with each possible LM1 advisory label 0..3. Every
non-advisory decision must equal B, tool grants and training starts must remain
false. This tests policy invariance, not C with actual newly trained weights.
Full app A/B/C, ASR, Gemma and physical Fold7 remain NOT_RUN.

Cases deliberately include paraphrases and Austrian phrasing outside the
adapter's narrow canonicalizer. Report failures as limitations; do not delete
or tune them after seeing outcomes. Any implementation repair requires a distinct
result version and the original failure receipt stays intact. This consumed
small suite is not a new unseen test after repair.

The component recommendation gate is: no regression on cases A already passed,
zero incorrect target selection and zero action/training grants. A syntax-only
gain with failed other paraphrases is reported narrowly. Confirmed review changes
remain attributed to the human review plus deterministic bridge, not learning.
