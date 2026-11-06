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
