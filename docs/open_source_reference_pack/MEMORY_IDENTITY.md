# Memory, Identity and Learning Patterns

Keep Device AI's typed memory layers: WORKING, EPISODIC, SEMANTIC, PROCEDURAL, SELF, MODEL and SOURCE.

## Letta patterns to adapt
- compact always-available identity/persona core;
- larger deferred/reference memory;
- separate skill/procedure memory;
- memory-maintenance pipeline separate from answer generation;
- conflict repair rather than silent overwrite;
- targeted edits preserve unrelated memory;
- never persist secrets or ephemeral task logs as durable memory.

Android canonical storage remains typed app-private data, not a Markdown filesystem.

## Mem0 patterns to adapt
Explainable retrieval fusion:
- lexical/BM25-like signal;
- semantic/embedding signal when a local embedding model exists;
- entity-link signal;
- temporal relevance;
- source/validation quality;
- privacy eligibility;
- usefulness/recency.

Return score components for debugging.

## Write policy
A conversation is not automatically a memory. Durable writes require an explicit reason such as accepted preference/correction, stable user fact, reusable successful procedure, evaluated experience, sourced fact, or validated self-model update.

Generated guesses remain inference/unverified until corroborated.
