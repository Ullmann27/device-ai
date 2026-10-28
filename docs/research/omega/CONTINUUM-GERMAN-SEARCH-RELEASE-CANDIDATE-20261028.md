# Continuum German research contradiction gate — 28.10.2026

Separate Draft-PR stacked on #204, which itself is stacked on #203. Parent SHA: `f2a42ed28e59b3eb8acc1b1ee034c430c8310acc`. No main merge, no owner APK signing or installation. EXP-00/06/07, Issue #41 and AGENTS.md reviewed.

**Evidence-backed issue:** Existing `ResearchPipeline` classifies similarity ≥0.7 as `SUPPORTS` before looking for negation. Nearly identical claims differing only by German `nicht` can thereby falsely corroborate each other; both that pipeline and `DeepResearchEngine` only recognize English negation. Scope limited to a small deterministic German/English explicit-negation helper reused by both existing paths and actual negative integration tests. This does not grant new network access, tools, memory writes or model authority.

**Status at creation: WIP / NOT_RUN.** Real Android Gradle/Termux/Fold7 and owner P12 unavailable to this environment. Use the existing local release builder only after full suite, APK QA and owner-signing verification. An improvement to a concrete contradiction case is not a measured overall search-quality improvement.
