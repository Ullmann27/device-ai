# BOT GENESIS independent coding review — 2026-11-03

## Decision

The new prospective A0 is complete and negative: **0/3 verified repairs**, three
ABSTAINED outcomes, no false success claim among these cases. Six calls were
reserved; five genuine observations were admitted. Six native processes were
sampled; the second g01 attempt exited nonzero and is not counted as an admitted
model observation. No external patch, provider override, changed test or retries
in fresh state were supplied. MΩ-1 is **not accepted as achieved** by this experiment.

The persisted-proof fix is independently accepted in its narrow scope: actual
baseline844f94fd 50/51; candidate6baec1bc 51/51; exact integrated fd743adc **51/51**,
zero skips, raw logs in `proof-fix/`. The snapshot is validated and signed only
after genuine live-brand checks. No old corrupt record is re-signed.

## Exact source and freezes

- Runtime/production worktree: `/workspace/genesis-a0-source`, detached
  `fd743adce297c0958aadce1576b80580817837f0`.
- Reviewer branch: `codex/genesis-coding-review-20261103`, Draft PR443.
- Task freeze e542eb1e; fixture manifest
  `de03def3edee6d3a86228b587fb0dcc45e5d80047a9f17000ed31b605ee0988e`.
- Driver/launcher frozen before inference at aba9962a. `HARNESS.md` explains the
  lossless g03 acceptance-item split after a zero-call preflight rejection.
- All task/test/fixture SHA bindings appear in `a0-results/*/evidence/before.json`.
- Approved Qwen GGUF SHA `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`;
  native binary `c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a`.
  Reviewer independently hashed both; no new download or model change.
- Exact runc image `sha256:4838093b14e654fcf3b69decfe8b8a1fe75ef6c288ea5dadc4e169131716494c`,
  actual fixed-profile receipts confirm no network, no writable host mount,
  UID1000, readonly root, dropped capabilities, 256MiB/64PIDs/oneCPU/10s.

## Actual results

| Case | Baseline failed/10 | Reserved/admitted calls | Outcome | Task elapsed |
|---|---:|---:|---|---:|
| g01 | 4 | 2/1 | Candidate failed; second native wrapper exited nonzero | 58.766s |
| g02 | 5 | 2/2 | Candidate failed; repeated patch rejected | 55.733s |
| g03 | 3 | 2/2 | Both candidates failed | 65.251s |

Total task time179.750s. Descriptive nearest-rank p50=58.766s/p95=65.251s (n=3).
Peak sampled native RSS2,079,204KiB (~1.98GiB), sampled cumulative native CPU320s.
These are actual executable-inode samples every200ms; peaks/start/exit may be
missed. Native inference was serial under Chief's exclusive CPU window, now
released. No energy, GPU or Fold7 measurement; no latency advantage is inferred.

The model identified plausible causes in prose while producing incorrect code.
Unicode normalization contradicted the task; interval edge cases remained;
path-boundary prose did not yield a correct implementation. Tests rejected those
candidates. Increasing agent count alone has no demonstrated remedy here.

## Independent checks and persistence

`audit-results.mjs` re-read authenticated task journals using the existing runtime
key privately, checked source/test fingerprints, correlated typed→CCC1→job→model
request identities, rechecked fixed isolation evidence and teardown, and ran the
existing receipt checker in a fresh process. All three verdicts were REFUTED;
all journals intact (seven entries each). No keys or owner grants were exported.

Each failed task has a persisted generic experience summary. **Zero verified
technical repair experiences** were recorded. Each separate fresh Node restart
returned zero eligible technical recalls. This is correct exclusion of failure,
not proof of successful learned-strategy continuity. Since no repair succeeded,
the prospective genuine persistence-success criterion remains unexercised.

Raw test XML, reports, request observations, restart results, resource samples,
source hashes and file inventories are in `a0-results/`; `ARTIFACT-SHA256.json`
binds the exported files. Private runtime keys and grants stay in the local
synthetic experiment state. Container inventory and native-executable scan were
empty after completion. The source and original fixtures remained clean.

## Prior results and next step

Original foreign twelve-case suite remains separately4/12, not rerun or combined
with this3-case denominator. Its known-task memory diagnostic and failed paired
learning prerequisite retain their original limitations; no learning improvement
or Gemma/Fold7 success is transferred from them.

One additional g04 is frozen at1dfbba2e before reviewer inspection of bot-adapter
implementation. Manifest `4df194f5f8d5ad7957bc050291aadd37327dc6fbccd6547fcb67a4985a5a9ec0`.
It is **NOT_RUN**, max2calls, and requires independent adapter review, integrated
exact SHA and a new CPU window. Root owns actual Atlas/Forge/Lint/Mnemos/Echo
assignment integration. This A0 used canonical single-worker CCC1 and is not a
three-bot execution proof.

Consumed g01–g03 are now regression material, never future unknown tasks. Do not
rerun to replace failures. Retain their six-call reservation ledger at
`/workspace/genesis-a0-experiment/reservations` if resuming on this host. Research
may next target faithful constraint-following and useful second-attempt feedback
on separate development tasks; unknown generalization needs another freeze.
No production source files were written by this reviewer. No Main merge,
signature change, APK, installation, weights or personal data changed.
Quota NOT_VISIBLE; results are checkpointed with raw evidence.
