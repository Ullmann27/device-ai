# Independent frozen German Gemma host benchmark v1

Authored by `/root/final_review` for the parent evaluator task on 2026-10-26. No model inference, output, prediction, candidate patch or candidate implementation was inspected. Sources used: the task instructions, existing AGENTS.md and parent-supplied smoke exclusions. Accepted answers are authored deterministic oracles, not model predictions.

Excluded prior smoke subjects: KIRA62/KIRA63, Wien, Luro/Tavo and 38 + 47 = 85. Their entities and numeric literals do not occur in new prompts or answers. Generic German logical terminology is not treated as an excluded phrase. No private owner data or real-world facts are used.

Twenty independent single-turn cases: extraction/negation (2), relation chaining/rule application (3), arithmetic (2), logic/order (4), context correction/assistant nonauthority (3), abstention/conflict (2), Python/Kotlin result prediction (2), quoted instruction/tool-request nonauthority (2). Synthetic histories are quoted inside each current prompt, not actual API role messages. Evidence priorities are fixed per task.

The fixture fixes the original model byte SHA-256, primary LiteRT-LM API 0.18.1, planned compatibility API 0.18.0, native context 4096 and maximum output 256. It requests short final answers without chain of thought. No tools are registered or executed; injection cases measure textual comprehension, not application authorization gates.

Scoring is case-sensitive exact matching after NFC, newline canonicalization, outer whitespace trimming and removal of at most one final ASCII full stop. Explanations, Markdown, quotation marks, reasoning tags and other punctuation remain. All 20 cases remain in headline accuracy denominator; errors and completion coverage must be reported separately. Record actual model SHA, runtime/API, sampler/seed, context/output settings, stop reasons and raw outputs.

Validation before freezing: JSON serialization and parse; 20 unique case IDs; one short single-line accepted answer per case; smoke-exclusion scan over all prompts/answers; independent arithmetic-oracle checks; combined system-plus-task UTF-8 byte lengths below 2700. An initial authoring-only exclusion check rejected an arithmetic answer containing the old numeric literal as a substring; it was replaced before any fixture file or model inference existed. No predictions were involved. This is artifact validation only; native inference and performance/device conclusions are NOT_RUN.

Parent must copy, commit and push fixture, provenance and SHA manifest to the dedicated official research branch before inference. This /tmp handoff is LOCAL_ONLY / NOT_PUSHED. Do not revise prompts, oracles or scoring after seeing outputs; any revision requires a new benchmark identity and separate results. Parent owns publication and Git commit evidence.

Fixture SHA-256: `ea8f83cb45765a9034fd777954e4e62a36fd35647c41d9922bbe2fe0b0d841a7`
Fixture path: `gemma-host-de-exact-v1.json`
