# Continuum: real shared neural learning

The scheduled runner now performs gradient updates to the same 4,113-parameter
SWB1 encoder used by Android. The initial weights come from the project's own
seeded Java-compatible random initializer. No pretrained weights or paid model
API is involved. This is a small intent/representation learner, not a generative
language model or evidence of consciousness.

## Data and promotion

Each round alternates supervised greeting/question examples with positive and
negative semantic pairs. Public research adds weak title/excerpt supervision at
one eighth of the semantic steps. Public source pairs do not become verified
facts. Private owner lessons and generated replies never enter weight training.

A 96-entry hash-priority replay reservoir retains older source experiences.
Every record carries source URL, timestamp, SHA-256, trust and replay status.
The fixed CPU budget is 12,000 attempted steps (hard cap 24,000), at most 30
seconds. Candidate checkpoints are examined every 1,000 steps. Both task
accuracies, both losses, replay retention and the existing Android validation
slice constrain promotion. Rejected candidates leave active weights byte-identical.

A separate 20-intent/20-semantic audit is evaluated for reporting only. It is not
used for gradient updates or checkpoint selection. All phrases are authored
in this repository; this is a small internal benchmark, not independent external
certification. Repeatedly reused validation can overfit over days. Future research
must add fresh, independently authored audit sets before claiming broader gains.
Activity, source counts and internal rewards are not model intelligence scores.

## Persistence and operation

`.continuum/state.json` on `continuum-state` remains the checksummed canonical
state, including active and previous model weights, experiences and 64 reports.
The workflow commits state and the public export together, serializes writers
and fails on a failed state fetch instead of replacing existing memory.

The existing four-hour schedule must live on the default branch. Its checkout
should be pinned to a CI-verified implementation commit. A PR workflow alone
cannot activate scheduled execution. `workflow_dispatch` and a change to this
workflow on the development branch also exercise one complete learning cycle.

Copilot installation and the `copilot-requests` permission have been removed;
`CONTINUUM_COPILOT=0`. GitHub-hosted runner availability and private-repository
included minutes depend on the account. Four-minute job bounds and 7-day export
retention reduce usage, but do not establish a billing hard limit. No billing
settings are changed. Schedules can be delayed by GitHub.

## Console on Issue 20

Only the repository owner's comments mutate state. Ordinary messages and
`/chat <text>` use bounded contextual retrieval with neural reranking. Follow-up
references reuse the last topic. Replies quote owner lessons or explicitly cited
source observations and identify their origin. The dialogue is template-based;
it does not pretend to be a large generative model. Unknown topics are admitted.

- `/research <question>`, `/ask <question>`, `/deep <question>`: explicit research.
- `/learn <text>`: private owner lesson (memory only).
- `/neural`, `/progress`, `/status`: model and evidence measurements.
- `/train`: one bounded neural round, respecting pause.
- `/pause`, `/resume`: pause/resume scheduled research and training.

Every third autonomous target may use the learned encoder's uncertainty to
choose an under-explored source for independent follow-up. Existing Baby Core
and World Model planning remain responsible for the other target selections.
Uncertainty is a heuristic, not calibrated epistemic confidence.

## Android

Download `neural-public.bin` from the workflow's public-model artifact (or the
state branch). Open **Continuum Learning Core v2 → GEWICHTE IMPORTIEREN** and
select that file. The importer reads at most 32,961 bytes, verifies SWB1/checksum,
reproduces both local holdouts and checks confirmed personal intent replay.
It installs only an improving nonregressing candidate. Personal memories,
command mappings and permissions remain on the device and are never uploaded.
Checksum proves integrity, not publisher authenticity; import is owner-selected.
The imported model feeds the existing local intent/uncertainty path and remains
available offline. Local training now protects the other task sharing embeddings.

## Reproduce

Run `node --test .github/continuum/*.test.mjs` and
`gradle :app:testDebugUnitTest :app:assembleDebug`.
The committed SWB1 fixture has cross-runtime numeric inference, tokenization,
round-trip, corruption and personal-memory preservation tests. The example
report in `neural-benchmark-example.json` is a deterministic offline run, not
an assertion about the current live state. Re-run `neural-benchmark.mjs` to
refresh it and the JavaScript-generated Kotlin fixture.
