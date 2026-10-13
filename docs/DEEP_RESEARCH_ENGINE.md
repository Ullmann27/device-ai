# Bounded Deep Research Engine

## Architecture and state machine

`DeepResearchEngine` is a provider-neutral read-only pipeline layered on `ResearchProvider`,
`SourceTrustStore`, `Claim`, and `MemoryWritePolicy`:

`PLAN → DISCOVERY → FETCH → EXTRACT → GRAPH → GAP_ANALYSIS → FOLLOW_UP → SYNTHESIZE → COMPLETE`

Failures or unavailable discovery produce `PARTIAL`; cooperative cancellation is checked between
queries and fetches. `ResearchRound` records bounded round/query/source metadata. The planner
interface accepts a future local-model adapter through `ResearchPlanGenerator`; the current app
uses deterministic question splitting and requires an explicit Web/QUICK, STANDARD, or DEEP
selection. Ordinary LOCAL/AUTO chat never invokes research implicitly.

## Hard budgets

Every mode resolves to fixed app-owned limits. A planner cannot change a mode's budget; paths
and follow-ups are truncated to the validated policy. HTTP requests carry the per-source byte,
request timeout, and redirect limits; an overall monotonic deadline, source/claim caps, and
per-domain fan-out are checked by the engine.

| Mode | Discovery paths | Rounds | Sources | Bytes/source | Total bytes | Claims | Request / deadline | Counter / follow-up |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| QUICK | 2 | 1 | 3 | 64 KB | 128 KB | 32 | 5 s / 15 s | 0 / 0 |
| STANDARD | 5 | 2 | 8 | 128 KB | 512 KB | 96 | 8 s / 45 s | 2 / 3 |
| DEEP | 10 | 3 | 16 | 256 KB | 1.5 MB | 192 | 10 s / 90 s | 8 / 8 |

All limits have additional absolute constructor ceilings. Redirects are limited to 1, 3, and 4
by mode respectively; per-domain fan-out is at most two sources.

## Discovery, sources, and trust

Discovery is separate from known-URL retrieval. The production UI currently configures no
general search provider and does not use a cloud search API or credential. Operators can include
direct HTTPS seed URLs; without seeds, the UI and dossier say that general discovery is
unavailable rather than claiming web search occurred. Candidate and redirected URLs pass the
existing HTTPS/SSRF validator, canonicalization, credential-query rejection, and per-domain
limits before source acceptance.

Canonical URLs discard fragments and common tracking parameters. Exact content hashes and a
deterministic near-duplicate grouping identify mirrors; a duplicate is not an independent
corroborating source. Dossier source metadata retains URL/domain, retrieval time, content hash,
bounded snippet, and extracted version/date applicability where present.

The evidence graph keeps claim nodes, source links, claim-to-claim SUPPORTS/CONTRADICTS edges,
source DUPLICATE_MIRROR/UNKNOWN relations, a coverage score, and independent-source count.
These lexical support scores are explainable heuristics, not calibrated probabilities. Source
trust snapshots distinguish evaluated profiles from unknown defaults and expose historical
authority, query relevance, freshness, corroboration, and duplicate penalty. Domain count alone
does not establish trust.

## Gaps, falsification, and synthesis boundary

After extraction, missing subquestion coverage and contradictions become explicit gaps. STANDARD
and DEEP can use the bounded follow-up wave when a general discovery provider exists. DEEP adds a
counter-evidence path and preserves contradiction edges instead of majority-voting them away.
With no configured discovery provider, direct seed research remains available; a second wave
cannot invent additional URLs.

Fetched page text is hostile evidence. `ResearchPromptBoundary` puts bounded source excerpts in
quoted untrusted-data sections, escapes role labels and delimiters, and expressly denies them
authority to request tools/actions, change budgets, or rewrite policy. Synthesis is a
provider-neutral text-only interface. The Android adapter uses the ready local LiteRT-LM model;
any generated structured tool proposal is discarded, never passed to ToolGate or an executor.
Actual citations are appended from the retrieved dossier, not invented by the model.

## Persistence, resume, and memory

`FileResearchDossierStore` uses explicit typed binary fields, schema versioning, a SHA-256
integrity envelope, a 1 MB cap, and atomic replacement. Malformed, oversized, tampered, and
unsupported-schema records fail closed. Checkpoints preserve the phase, completed URLs/content
hashes, and byte count. Partial dossiers preserve bounded source snippets. Resume requires a
matching, non-stale checkpoint/dossier pair and skips already completed canonical URLs; it may
re-extract from bounded stored snippets, so no claim is made that it retains full downloaded
pages. Dossiers are classified PRIVATE by `MemoryWritePolicy` because they contain user-provided
research queries.

Web claims are not automatically promoted to long-term memory. `storeValidatedKnowledge` writes
only claims that are independently verified, have at least two independent sources, are
explicitly selected for storage, and pass `MemoryWritePolicy`.

## Android path and validation boundary

The AgentActivity Web action explicitly arms exactly one QUICK/STANDARD/DEEP request. It displays
planning, fetching, checking, follow-up, synthesis, done, or partial state; cancellation is
cooperative between bounded provider operations. Research stays read-only and does not affect
ordinary local-first chat. A ready local model synthesizes evidence; otherwise a deterministic
summary is used and disclosed.

GitHub Actions run `38341592364` passed backend syntax, `:app:testDebugUnitTest`,
`:app:assembleDebug`, and upload of `DeviceAI-v0.3-debug-apk` (artifact `11787331710`) on the
pre-research head `adf399432dd43c4c37e39ac633d0eb58ee35dc71`. New research changes still need a
fresh CI run. Run `38349576117` for research commit `1550c53346e8f375f9d9948eb2098b74104310f6`
ended `action_required` with zero jobs; repository workflow approval is required. The sandbox
cannot resolve Android Gradle Plugin 8.10.0. The new engine's
standalone Kotlin compile and deterministic JVM tests are locally exercised with temporary
API/JUnit stubs, but this is not a substitute for Android Gradle CI. No live network, imported
model, physical-device inference, Fold layout, or on-device cancellation behavior has been
verified.
