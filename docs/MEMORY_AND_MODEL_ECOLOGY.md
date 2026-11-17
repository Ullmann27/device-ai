# Memory and Model Ecology

This document describes the provider-neutral cognitive foundation in
`at.heinz.deviceai.cognitive`. It is deliberately not connected to `MainActivity`,
the Shizuku bridge, or a real model provider.

## Memory, provenance, and claims

`MemoryRecord` is a typed record with provenance, confidence, timestamps, tags,
parent IDs, optional source reference, validation state, and privacy class.
`InMemoryMemoryStore` rejects duplicate IDs. `MemoryRetriever` uses deterministic
token overlap and stable ID tie-breaking; callers can restrict its privacy classes.
`MemoryGraph` stores directed typed relations.

Model-generated records and claims default to `UNVERIFIED`; they cannot be
constructed as verified facts without an explicit validation path. `Claim.validated`
requires independent evidence references for verification. Claim relationships
distinguish support, contradiction, and supersession.

`MemoryWritePolicy` prevents routine interactions from becoming durable memory.
Explicit requests and reusable/durable knowledge can be retained; personal,
sensitive, and device information is assigned `PRIVATE`, `SENSITIVE`, or
`DEVICE_LOCAL_ONLY`, respectively.

## Experiences, adapters, and routing

`Experience` is an immutable evaluation record containing task/request summaries,
memory/model/source/tool references, plan and result summaries, failures, retries,
scores, feedback, confidence changes, outcome, and timestamps. The in-memory store
retrieves similar requests within the same task type using deterministic Jaccard
similarity.

`ModelAdapter` is provider-neutral. `MockModelAdapter` and `TestModelAdapter` are
local deterministic test implementations; no network adapter is included.
`ModelReputationStore` applies bounded exponential updates only to evaluated
evidence, partitioned by model, capability, and domain. `ModelRouter` selects but
does not invoke adapters. Routing plans expose strategy, model IDs, rationale,
confidence, and evidence. Self-model confidence influences whether uncertain
capabilities use multiple adapters.

## Source trust

`SourceIdentity` includes a domain, and `SourceTrustStore` maintains separate,
initially uncertain profiles per identity/domain. Accuracy, freshness,
corroboration, contradiction, and correction evidence update gradually, and
unevaluated observations are rejected. Profile confidence starts at zero and grows
gradually with evaluated claims.

## Local persistence and integrity

`CognitiveArchiveStore` accepts a caller-supplied app-private directory (Android
callers should supply `Context.filesDir`). Its versioned binary format serializes
only explicitly enumerated `IdentityState`, `CognitiveGenome`, memory, and
experience fields; it does not deserialize arbitrary classes. It validates
archive size, schema, identity/genome binding, integrity hash, forbidden genome
flags, duplicate IDs, enum values, and numeric ranges. Writes use a temporary
file and atomic replacement where supported.
The prior valid primary file is retained as last-known-good state, and loading
falls back to it when the primary is corrupt. Import/export is deterministic
Base64 over the validated archive.

`IdentityState.integrityHash` and `contentHash` are deterministic SHA-256
integrity/content hashes, not signatures. `IdentitySigner` separates authenticity
from hashing; no production signing key or Android Keystore adapter is included.
A fixed-key signer exists only in JVM tests.

## Status and limitations

- Implemented: in-memory memory/experience stores, deterministic retrieval,
  memory and claim relations, provenance/validation, privacy write policy,
  provider-neutral test adapters, evaluated reputation and source trust,
  explainable routing, and versioned archive persistence/recovery.
- Not implemented: Android Keystore signing, production model adapters,
  persistence adapters for standalone model/source trust stores, automatic
  background retention/compaction, or production UI wiring.
- Trust values and retrieval ranking are deliberately simple initial policies,
  not calibrated production recommendations.
