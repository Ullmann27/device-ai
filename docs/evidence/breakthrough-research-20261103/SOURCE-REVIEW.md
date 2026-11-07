# R1 source review and existing architecture

Read-only fetches on 2026-11-03, exact times, URLs and response hashes are in `external-source-fetches.json` and `alce-code-fetches.json`. No foreign code, dataset or model was imported into the product. Network fetches were fixed URLs with a 20-second timeout and 1 MB cap, plus the GitHub connector; no crawler or paid API.

1. Gao et al., *Enabling Large Language Models to Generate Text with Citations* (EMNLP 2023), https://aclanthology.org/2023.emnlp-main.398/ (ACL notice: CC-BY-4.0). Read the primary abstract and the authors' open `eval.py`, MIT, upstream commit `246c476a4edfc564266b7346b6e29ef4861ae937`. The implementation's `compute_autoais` first validates reference indices, then separately evaluates whether selected passages entail a claim. This separation supports our narrow attribution check. We did not run AutoAIS, download its model, reproduce ALCE, or borrow its scores. A valid URL or literal match is not semantic entailment or truth.
2. W3C, *Web Annotation Data Model*, Text Quote Selector, https://www.w3.org/TR/annotation-model/#text-quote-selector (2017; W3C document use rules). The primary specification separates the selected quote from its target resource and describes optional prefix/suffix context. R1 keeps the current extractive API and checks literal membership against the supplying document; it does not implement the W3C format, disambiguate repeated spans, or infer sentence meaning. Future paraphrase support needs an explicit entailment contract.

## Actual current caller path

The task's historical `boundedWebResearch` symbol is absent at baseline `64ec06a7`; the implemented bounded path is:

- `AgentActivity.runResearch` constructs `ApprovedWebResearchToolRunner`, with consent-scoped discovery and provider wrappers. The explicit research path supplies the local model synthesizer and RIS/Wikipedia providers.
- `AgentActivity.createToolGate` uses the same runner for model-requested `web_search` / `web_read`, consuming the existing approved session and rechecking consent. `ApprovedWebResearchToolRunner.execute` calls the existing `DeepResearchEngine.research` or `resume` and checks plan/session identity.
- `DeepResearchEngine` checks canonical HTTPS targets, content hashes, byte/source/time budgets, then extracts and aggregates claims. The sole production `ClaimExtractor` is `BaselineClaimExtractor`, which selects literal sentence fragments (up to 512 characters).
- `ResearchPromptBoundary` renders the graph and source snippets into local synthesis. Before R1, graph IDs and text were raw while titles, URLs and snippets used the bounded sanitizer. Local generated synthesis still has no general semantic entailment verifier.
- `ResearchDialogueHandoff`, the existing consent gates, source persistence, and memory promotion remain their existing implementations. R1 does not edit them or create another scheduler/store.

## R1 boundary

An extractor can propose text, but cannot supply source authority, confidence, time, validation state, or corroboration references. The engine admits a trimmed literal substring of its supplying document, records that canonical document URL, and uses external-source/unverified/default confidence metadata. Only exact matching admitted claim texts aggregate; case/whitespace folding could otherwise bind a representative quote to a document that never contained it. Existing domain/mirror corroboration remains heuristic, with the count made consistent with the distinct-domain rule. Domains are not proven independently owned organizations.

Graph strings now pass through the existing bounded sanitizer, with SOURCE numbers derived only from the displayed evidence URLs. This is defense in depth for a known raw surface, not proof of prompt injection immunity. Literal substrings can still omit surrounding negation or conditional context; neither R1 nor hashes establish truth. No fresh-content, real-model, device, latency or energy improvement is claimed.

Frozen development fixtures are the two Kotlin test files listed in `development-fixtures.sha256`; documents and malicious extractor output are embedded, synthetic and reproducible. The initial test harness had two List/Claim return-type mistakes, recorded in `initial-harness-compile-failure.log`, corrected before baseline evaluation. Baseline is recompiled from the exact original ResearchEngine.kt and produces 6 failures among 10 frozen development tests. Unknown QA cases remain with the independent quality lane until source freeze.
