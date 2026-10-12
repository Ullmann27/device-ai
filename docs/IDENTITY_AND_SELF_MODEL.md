# Identity and Self Model (Part 2)

This describes **identity representation** (persistent, versioned state). It is not consciousness, sentience or
self-awareness; nothing here claims the system has any of these.

## Hash vs. signature
`integrityHash` (SHA-256 over a deterministic canonical form) detects tampering. It does not authenticate the author.
Cryptographic signatures (e.g. Android Keystore) are planned, not implemented.

## BehaviorProfile
Eleven typed traits (`BehaviorTrait`), each 0..1, all start at 0.5 (neutral). Immutable; `with()` rejects NaN/Infinity/out-of-range.
Genome `BehaviorBounds` (default 0.05..0.95) are enforced by policy and validator.

## BehaviorEvidence / Proposal / Policy / History
A `BehaviorProposal` carries a trait, proposed value, reason and `BehaviorEvidence` (id, strength 0..1, measuredOutcome -1..1).
`BehaviorUpdatePolicy`: `step = learningRate * meanStrength * meanOutcome`, limited to `maxBehaviorStep` (0.05), never
overshooting the proposed value, clamped to bounds. Rejected when evidence is missing/duplicate/weak, for the wrong trait,
non-finite, or pointing in the opposite direction. One update can therefore never move 0.5 to 1.0.
Accepted changes are recorded in the append-only `BehaviorHistory` as `BehaviorChange` (previous, proposed, accepted, evidence ids,
strength, outcome, reason, timestamp, source and resulting generation).

## SelfModel and CapabilityEvidence
Ten `Capability` domains, each a `CapabilityEstimate` (competence, calibrationError, evaluatedTaskCount, knownLimitations).
Initially uncertain: competence 0.5, 0 tasks, confidence 0. Each `CapabilityEvidence` (task, predicted confidence, actual score,
evaluator, timestamp) moves competence and calibrationError by the genome's selfModel rate (0.1, an exponential moving average).
`confidence = 1 - calibrationError` once evaluated: repeated overconfidence raises calibrationError and lowers confidence;
accurate predictions improve it. Confidence is not certainty. `SelfModel.explain(capability)` returns the supporting evidence.

## IdentityDrift
Deterministic continuity metric between two states: 0.5*behavior + 0.3*capability(competence) + 0.2*communication drift.
Ignores memory/experience roots and session data. Allowed range: `UpdateLimits.maxDriftPerGeneration` (0.1).

## IdentityUpdateValidator
Returns `Valid` or `Invalid(failures)` with codes: INVALID_NUMBER, OUT_OF_BOUNDS, INSUFFICIENT_EVIDENCE, EVIDENCE_MISMATCH,
IDENTITY_MISMATCH, BROKEN_ANCESTRY, GENOME_MISMATCH, EXCESSIVE_DRIFT, FORBIDDEN_CAPABILITY, INTEGRITY_FAILURE.

## Generation lifecycle
Generation N -> proposal -> policy -> validator -> Generation N+1 (`AdaptiveIdentity`). Rejections change nothing; old
generations are never modified; `IdentityHistory.audit()` verifies the chain.

## Safety
Genome flags allowSelfReplication / allowHiddenPersistence / allowShutdownEvasion cannot be set true.
