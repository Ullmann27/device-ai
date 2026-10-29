# Continuum Generation 16 / Gemma recovery — working checkpoint

Baseline: PR #216, b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6, descendant of PR #215. Branch: sol/continuum-generation16-gemma-20261029. Target: at.heinz.deviceai.continuumlocal. Historical at.heinz.deviceai.mentorpaired remains untouched.

Owner authorizes isolated source development and tests, not production merges, installation, key replacement or automatic training/research. Relevant experiments: EXP-00/01/02/05/06/12. No new dependency, model or paid provider is planned.

Priorities: (1) read-only authorized ADB/run-as recovery of the original 256-record ledger, with honest failure when non-debuggable; (2) validate that ledger using the actual project codec without restoring consent; (3) improve proven Gemma/worker defects in existing call paths. Personal imports remain review-gated; revocations must dominate. Existing SWB1 holdouts remain unchanged.

Observed environment failure: initial GitHub/git inventory completed. The shell execution service then disconnected and reported environment UNAVAILABLE before new baseline or JUnit/Node tests. GitHub connector remains usable. Any new test is NOT_RUN until a real environment executes it. Fold7 remote access is not configured. Device-only checks are NOT_RUN. User reports native Gemma direct inference on installed code27 in 33217 ms; this is owner evidence, not a measurement from this session.

Plan: independent narrow rescue/Gemma/worker reviews, root integration review and personal-ledger validation; small Git commits, no blind history merges. Working source is WIP/UNVERIFIED until execution evidence is published. No raw private backups, facts, models, credentials or signing material may enter GitHub.
