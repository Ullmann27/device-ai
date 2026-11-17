# Recall repair — exposed regression result

Status: **PASS_EXPOSED_REGRESSION, 8/8 controls**.
This is the single authorized repair run after the failed first candidate. These
cases were known to the implementer by this stage. It is **not a fresh holdout**,
and does not erase the initial independent rejection.

Source: `c1757d01701b49409c86c1dfe04993fe905599d3`.
Tree: `fd9ae2016e9334e0c5c5ce248dab2a23f73f0b27`.
Tests and runner: unchanged from the preceding evaluation; SHA-256 equality across
all three measured source versions is recorded in `exposed-repair/fixture-identity.txt`.
The evaluator added no scenarios and made no implementation change.

| Stage | Correct controls | Interpretation |
|---|---|---|
| Baseline `515afc28` | 5/8 | Existing recency failures R1/R2/R7 |
| First candidate `ad995a37` | 7/8 | REJECT_PROMOTION; new R6 future-window regression |
| One repair `c1757d01` | 8/8 | Exposed regression passed; no new holdout claim |

The repair adds explicit inclusive time bounds to the existing ExperienceLookup
interface. The active InMemoryExperienceStore filters those bounds before lexical
ranking, recency ordering and the 64-record limit. AgentExperienceRecall passes
its existing 30-day lower bound and the supplied `nowMillis` upper bound; no hidden
system clock is introduced. Thus R6 again retains the valid episode when the
remaining 64 equal-relevance episodes are future dated. R1/R2/R7 retain their
improvements. Relevance-first ordering, deterministic equal-time ordering, age
boundaries and unchanged unauthorized-tool behavior pass the same controls.

Scope limitation: the default interface adapter filters the legacy lookup's
already bounded shortlist. It cannot recover omitted eligible records. The
pre-ranking/pre-limit guarantee observed here applies to the existing active
InMemoryExperienceStore override. No stronger guarantee for every third-party
ExperienceLookup is claimed. Existing unverified-hint semantics and ToolGate
remain intact; no executable skill, model-weight update or new database is added.

Reproduction uses the unchanged
`docs/evidence/continuum-learning-recall-eval-20261110/run.sh` with this exact source
checkout and the same frozen dependency snapshot. Unedited compiler/JUnit output,
source hashes and toolchain identity are under the adjacent `exposed-repair/`
evidence directory. The one run reported `OK (8 tests)` and JUnit elapsed time
0.217 s. This elapsed time is not a CPU, memory, response-latency or device benchmark.

The initial 5/8 and 7/8 results, initial fixture correction and preregistration
remain unchanged. No additional tuning or evaluation round is proposed here.
Parent integrator `/root` owns the candidate branch and full production-source
compilation. That separate build result is not inferred from these eight JVM
controls. Android/Fold7/native model execution: **NOT_RUN by this evaluator**.
No release, signing, main merge, installation or protected Build 52 change.
