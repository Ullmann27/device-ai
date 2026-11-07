# Code47 PDF wording repair — 2026-11-06

Status: SOURCE_AUDIT / RED_NOT_RUN / NO_RELEASE_CLAIM.

Base: c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e, PR578.
Branch: codex/continuum47-pdf-word-order-20261106.
Owner: primary integrator. Scope: cognitive/OwnerDocumentIntent.kt and its tests only.
AgentActivity remains exclusively owned by the coordinated action/UI helper.

## Finding and connections
The existing PDF parser accepts read/open verbs before PDF/document. Polite object-first requests such as "Kannst du mir bitte die PDF öffnen?" do not match that regex; OwnerAppLookupIntent deliberately excludes file targets. Reproduce before changing the parser. Preserve Android ACTION_OPEN_DOCUMENT, explicit file choice, read grants and the actual work-window path. A parse is not a device execution.

## Work and evidence
Add focused real Kotlin/JUnit RED/GREEN checks for direct and polite requests, negation, informational questions, unsupported destructive/sharing actions and multi-step safety. No new document store, model runtime, tool authority, renderer or Activity entry point. Existing original intent tests and connected-dialogue controls must remain valid.

## Boundaries and rollback
EXP-02/07 repair tests and existing EXP-05 dialogue routing. Original package, signer, assets and offline gates stay unchanged. No main merge, real messages, device install or signing. Drop this isolated branch before integration, or revert its reviewed commit afterward; keep live Code46. Full frozen-source SDK/Node/backend/lint/package and physical Fold7/GPU acceptance are separate gates. Next entry: reproduce parser failure against actual base source, then a narrow repair.

## Verified parser repair

Focused actual Kotlin/JUnit: polite/reuse RED6/2 -> GREEN8/8; additional app-step RED9/1 -> final GREEN9/9. Raw commands, hashes and logs: docs/evidence/code47-pdf-wording-20261106/. One preliminary compiler-access fixture failure is retained and explicitly excluded from product RED.

Only OwnerDocumentIntent and access to the existing OwnerAppLookupIntent.stripAddress helper changed. The optional configured name is binary-compatible through @JvmOverloads. The Activity writer must pass continuumState?.archive?.identityState?.chosenName to both existing parse calls. No new Activity dispatcher or authority. Actual picker routing UI belongs to that writer's combined check, not this isolated parser test.

Rollback: revert this branch together with its two Activity argument updates. The original one-argument API remains valid; no stored documents or grants are migrated or deleted. The original Code46 publisher remains unchanged. Next: integrate the exact reviewed parser and Activity changes, then freeze and run the complete gates.
