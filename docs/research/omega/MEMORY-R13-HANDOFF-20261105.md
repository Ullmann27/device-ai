# Memory R13 — relative-cue consistency implementation plan

Status: WIP / NOT_RUN. Base R12 #548: e8102654a34d435eee115643b414158cd34c6f1d. C1 #519 observed at 13c37f55789bf67b07beeb37cc1a5035526e623e; #522 and the central board read. Current R12 discussion requests changes for other relative-time counts and leading-zero equivalence. No competing named R13 PR observed. A repository claim is not proof that another chat has acknowledged it.

Goal: preserve factual-number protection without treating relative durations as document facts; make the existing relative-day weighting agree with phrase boundaries and whitespace. Architecture: one shared phrase recognizer in the existing GermanText/TemporalCue file; no new memory store or common entrypoint. Tech: existing Kotlin/JVM and java.time. Spec: owner continuation, AGENTS.md, experiment contract/register and issue #41; EXP-06/07/12, no status promotion.

Only proposed production path: app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt. Original full blob dcaeed0b13eb3f4f88128e45c250d6d84a319c72 was reconstructed from the connector and independently hashed. Local git HTTPS failed with DNS/exit128; no full checkout, Android SDK or Gradle available. Host tests must explicitly identify any assertion/EventEmitting compile adapter; no Android or event-bus success may be inferred.

- [ ] Freeze new project JUnit regressions before any production edit; reproduce baseline failures on the full exact Kotlin source.
- [ ] Repair only demonstrated relative-time numeral and day-boundary recognition failures; keep scoring, snapshots, atomic index publication and confirmation metadata unchanged.
- [ ] Run unchanged R12 regression and existing 1000-document/44-query experiment with source hashes. Preserve its original assertions and report exact metrics, not merely its 0.80 threshold.
- [ ] Publish source-bound logs, reproducible command, compact JSON state, review limits and C1 handoff.

Review focus: singular/whitespace days; unsupported-unit fallback; mixed duration/factual digits; word-boundary false positives; overflow and leading-zero identifiers. Do NOT normalize every digit token globally: PIN 007 and PIN 7 may be distinct. Review's date-format concern remains open pending context-aware design and tests.

C1 alone integrates Activity/factory/voice/worker and the common candidate. R6–R12 remain prerequisite specialist changes, not implicitly accepted. #522 privacy/mapper and other teams' sources stay untouched. No model training, owner data, keys, package/signing/update/offline changes, paid service, Actions build, main merge, release or installation. Existing shared build locks remain mandatory on that host; no shared host build is launched here. Rollback only this isolated R13 delta; retain negative evidence.
