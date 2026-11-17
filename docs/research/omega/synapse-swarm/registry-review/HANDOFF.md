# Independent registry/coordinator review

**ACCEPT_SOURCE_SLICE / HOST_TESTED / INDEPENDENTLY_VERIFIED**, scoped to source
`2c5cd84ad7f4e19b9f52430856d311bfdf9dc18c`. Builder checkpoint
`e12c28f51c0f91f2dfb37dd4bc34d372358c8ab5` changes evidence/docs only.
No Android, Fold7, real-model, complete MΩ-1, S2, S4 or release acceptance.
Review branch `codex/synapse-registry-review-20261103`, draft PR422.

## Frozen contract and independence

Separate challenger session `synapse_registry_challenger`, not a product bot.
Baseline Chief `efe622dda00e620952483dedad19257efb3284e6` contains no equivalent
persistent profile API. Missing-feature baseline is NOT_IMPLEMENTED, not a fabricated
model failure percentage. Criteria froze at `c5075e649ef00f6addef338a31e25fd22edaf505`
before candidate API/implementation inspection. Public-API checks froze at `4af5119e`.
Owner roster steering changed only fixture names/IDs; see CONTRACT-STEERING.md.
Coordinator adversaries froze at `b0b92864`, before candidate source inspection.
The author did not provide expected test results before the independent run.

Historical coordinator from PR384 source `34543f9c6cb94c8dc471099dd656ad932d690b96`
was exercised with the real previously verified Chief pool: **0/4 passed**. These
were actual application-scope bugs, not shell/tool executions:

- Caller-owned approved profile sets could later add capabilities/tools.
- A profile returned by the listing API could mutate internal tool scope.
- Mutating requested tools after queue admission altered the queued brief.
- Rejected submission after pool shutdown left a phantom active assignment.

The corrected candidate passes the same **4/4** checks. These exposed repair cases
are regression material, not unseen model-generalization results.

## Actual independent results

| Check set | Result | Raw evidence |
|---|---:|---|
| Profile public contract | 11/11 groups | candidate-2c5cd84a/registry.log |
| Coordinator counterexamples | 4/4 | candidate-2c5cd84a/coordinator.log |
| Post-source-review mutations | 8/8 groups | supplemental-2c5cd84a/mutations.log |
| Same mutation checks rerun with cost probe | 8/8 repeated | supplemental-2c5cd84a-rerun/mutations.log |
| Preregistered metadata cost probe | exit 0 | supplemental-2c5cd84a-rerun/cost.jsonl |

The eleven groups include three profiles surviving a **separate JVM process**,
rename preserving namespace/workspace/rights/private context, twenty dormant
profiles, denied cross-bot note access, malformed IDs, stale-version rejection,
bounded writes, unknown schema/truncation/bitrot/trailing data, symlink targets,
concurrent threads and **two separate concurrent writer JVMs**, and 1,000 synthetic
profiles surviving paginated reopen. No models, tools, owners' files or devices
were used. Twenty profile records are not twenty verified running specialists.

The eight additional groups use **recomputed valid checksums** around invalid
identity/resource fields, reduced read budgets, missing/corrupt context objects,
and a saturated context store. Clearing the active context pointer still works
when that store is full and remains cleared after reopening. This is withdrawal
of these private working notes; it is not an independent retest of all LEX3/CCC1
owner revocation semantics.

Source inspection confirms immutable approved capability/tool snapshots and
request-tool snapshots, failed-submit cleanup, deterministic identity bindings,
strict profile/schema validation, process/file locking and atomic fsynced registry
replacement. Context payloads carry agent/namespace binding and use the existing
FileContinuumObjectStore, without admission into owner facts.

## Metadata cost experiment

Preregistered at `f8e98b7b`; no candidate optimization after measurement. Synthetic
records have the same field shape at each size. JDK 21.0.12.1; parent test JVM
`-Xmx512m`, restart JVMs `-Xmx256m`; host cgroup 4 CPUs and 16 GiB. No Gradle/compiler/
Blender process was observed in the captured start snapshot (the probe's `rg` is
listed). This is not a guarantee of an otherwise idle physical host.

Five warmups, thirty samples per in-process operation. Separate JVM restart uses
two warmups and ten samples; it includes JVM startup and full-catalogue pagination
plus a known-ID check. Percentiles are nearest rank of recorded samples.

| Profiles | Snapshot bytes | New store + get p50/p95 ms | First page(17) p50/p95 ms | Separate JVM restart p50/p95 ms |
|---:|---:|---:|---:|---:|
| 20 | 4,268 | 0.487 / 0.719 | 0.350 / 0.578 | 118.531 / 133.166 |
| 1,000 | 210,068 | 1.198 / 1.431 | 1.278 / 1.640 | 248.463 / 267.618 |

Each synthetic store contains its snapshot and zero-byte lock, no context objects.
The whole probe, **including fixture generation and restarted JVMs**, took 6.119 s;
Linux child resource observation reports 10.859 user CPU seconds, 1.226 system CPU
seconds and maximum child RSS 270,144 KiB. RSS is a process high-water mark, not the
sum of concurrent JVM memory and not the Android app's RAM cost. CPU can exceed wall
time through parallel runtime work. No GPU, device energy, temperature or battery
measurement exists. Zero model calls; no intelligence or inference speedup claim.

Initial cost invocation failed **127** because `/usr/bin/time` is unavailable,
before any measurement. The unchanged experiment was run with Python standard
`resource`/`subprocess` observation. The initial failure and empty output remain in
supplemental-2c5cd84a; this was a harness prerequisite failure, not silently passed.

## Reproduction and limits

Run `tools/synapse-registry-review/run-candidate.sh CHECKOUT FRESH_OUTPUT`, then
`run-supplemental.sh FRESH_OUTPUT/classes SECOND_FRESH_OUTPUT`. The scripts compile
actual candidate ProfileStore, Coordinator, WorkerPool, ContinuumState and
ContinuumSeed sources against official dependencies and the previously verified
real application dependency classes. No fake API classes or stubs are generated.
`SYNAPSE_BASE_CLASSES` and `CONTINUUM_FABRIC_JVM_DEPS` can point to equivalent rebuilt
application classes and official dependencies. Exact source/class hashes, command
exit codes and logs are retained. Root owns the final combined Android build and
independent UI review; this selected host compilation does not replace them.

- Pagination still scans and decodes the bounded whole metadata snapshot; no index
  or constant-cost lookup is claimed. No speed comparison to a nonexistent baseline.
- `requesterId` is a trusted application principal input. Same-UID code and directories
  are not strong bot/OS isolation. An untrusted tool must not supply its own principal.
- Private note objects are immutable and bounded; old unreferenced objects are
  retained, with no garbage collector or secure-delete claim.
- Registry schema v1 rejects unknown future schemas; no legacy owner-memory migration
  was attempted. All fixtures were synthetic.
- Empty profile permissions/capabilities and REGISTERED state remain metadata. Actual
  runtime status, task history, canonical execution grants and verified capabilities
  require their existing authoritative runtime/journal connections. This review
  does not complete those links or authorize tools.
- All reviewed tests are now exposed development/regression material. New claimed
  generalization requires new cases and real local model evidence.

Next: integrate the accepted slice into the isolated Chief basis, run third-party
UI/event review and exact combined regressions, then advance S2 only through the
canonical bounded task/CCC1 pathway with real available execution prerequisites.
Main, original signature, package, owner data and release state were untouched.
