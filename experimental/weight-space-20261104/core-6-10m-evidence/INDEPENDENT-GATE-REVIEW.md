# Independent registered-profile gate evidence

The final driver passed **75/75 synthetic integration checks**: 29 registered-profile cases and the unchanged 36+6+4 historical gate cases. No model process, training run, or real holdout evaluation executed in this review.

| Bound artifact | SHA-256 |
| --- | --- |
| `run_experiment.py` | `8f2f49bda56ce1f63e86fecf262229acff3d6f5d1ada1d97270b6c1731cda9be` |
| `CORE_6_10M_PLAN.md` | `abbc40f6128637072630b38685a933dab5528f2ec458696798ce5abba01750fd` |
| `tests/registered_profiles_gate.py` | `e6afccf4ae5bf0c243ecd8e92452c0c25c928912d704bf6977d9018fda2fd937` |

The accepted parent was commit `8e2a2370a9392aeff6bb68a8e2b4b0c1caaa68b2`. These source hashes identify the reviewed working artifacts; they do not themselves assert a later Git commit or deployment.

## What the tests establish

Both registered budgets authorize the real central training dispatcher when their existing creation, parity, capacity, calibration, and corpus proofs are complete. The 6,562,304-parameter profile ends at `width-8` and rejects `depth-3` under its exact budget. The 9,712,128-parameter profile ends at `depth-3` and rejects `depth-4` under its 10,000,000-parameter budget. The positive controls intercept the resulting `Popen` call before a process starts.

The negative cases cover changed stop reasons, targets, budgets and profile hashes; historical or foreign-profile receipts; modified plans and registration; failed original baselines; and failed expanded targets even when the small control would otherwise be usable.

A complete common freeze must bind both child selections before either final evaluation can reach the synthetic holdout boundary. Missing, malformed, incomplete or altered freeze evidence is rejected. A nonwinning candidate changed in the peer child also invalidates the freeze. Mutations during fresh and cached final calls are detected; the fresh race case publishes no `final.json`.

Once a global freeze marker exists, training remains closed after a child freeze or environment is lost. The review also covers historical-mode reuse of a reserved child path, dangling global freeze symlinks, and a registered child directory replaced by a symlink to a fresh location. Each of these three concrete path-related findings was reproduced against an immutable pre-fix source before checking the repair.

The quality-selection rule remains unchanged. One positive case deliberately makes the small baseline best and confirms that it remains the development preference. The mandatory large target controls the authorized expanded training arm; it does not force a larger model to win the quality comparison.

## Results and preserved provenance

| Suite | Final outcome |
| --- | ---: |
| Registered profiles, including three concrete review additions | 29/29 |
| Existing continuation matrix, unchanged | 36/36 |
| Existing final/creation checks, unchanged | 6/6 |
| Existing direct-dispatch checks, unchanged | 4/4 |

The historical 46 cases were run once each on the final source. The original 26 profile cases remain present; two concrete cases were appended, followed by the one child-symlink case. The earlier 26/26 and 28/28 outcomes describe those earlier, narrower scopes only. The two added cases failed on source `8a11da08533496c14cd7a5ca60ce44f914aab50401609baa48955d7ae22e7e2b`; the child-symlink case failed on source `e69dd110b35208b6e056d68fa0d7dad6ded724f079aa89ca16bd494e206a93bc`.

[`independent-profile-gates.json`](independent-profile-gates.json) contains exact harness hashes, all final case outcomes, pre-fix observations, and raw-report hashes. The compact records preserve observed outcomes while replacing generated fixture path prefixes and reducing events to their kinds. The evidence also contains verified line splices that reconstruct both pre-fix driver snapshots from the pinned final source.

## Reproduce the final checks

Use Python 3.11 or later, from the repository root. The tests require only the standard library and create isolated temporary fixtures. No repository models, private training documents, or real holdout contents are inputs.

```bash
python3 experimental/weight-space-20261104/tests/registered_profiles_gate.py
python3 experimental/weight-space-20261104/tests/adversarial_driver_gate.py
python3 experimental/weight-space-20261104/tests/targeted_final_creation_gate.py
python3 experimental/weight-space-20261104/tests/direct_dispatch_gate.py
```

Each script accepts an explicit source path and `--workspace` directory. The profile suite also accepts `--plan` and repeatable `--case` selectors. Every report records the source hash before and after the run. Compare those hashes with the table above before treating a new execution as a reproduction of this review.

To reconstruct the two earlier driver snapshots without changing the working driver:

```python
from pathlib import Path
import hashlib
import json
import tempfile

base = Path("experimental/weight-space-20261104")
evidence = json.loads((base / "core-6-10m-evidence/independent-profile-gates.json").read_bytes())
source = (base / "run_experiment.py").read_bytes()
assert hashlib.sha256(source).hexdigest() == evidence["finalDriverSha256"]
output = Path(tempfile.mkdtemp(prefix="continuum-pre-fix-sources-"))
for expected, snapshot in evidence["redSourceReconstruction"].items():
    lines = source.decode("utf-8").splitlines(keepends=True)
    for splice in reversed(snapshot["lineSplices"]):
        lines[splice["startLineZeroBased"]:splice["endLineExclusive"]] = splice["replacement"].splitlines(keepends=True)
    reconstructed = "".join(lines).encode("utf-8")
    assert hashlib.sha256(reconstructed).hexdigest() == expected
    target = output / (expected + "-run_experiment.py")
    target.write_bytes(reconstructed)
    print(target)
```

The corresponding case names are recorded under `reviewHistory`; select those cases with `--case` when testing the reconstructed snapshots. Their expected pre-fix outcome is a failing gate test, with every model process still blocked.

## Test boundary

Production constructors, registration, cached-receipt validation, preflights, authorization and freeze decisions run against independently constructed synthetic evidence. The fixed profile values are asserted independently of the driver's profile helpers. Only original-content hash constants are rebound to synthetic files. All `Popen` calls are blocked; constructor Java-version probes receive synthetic responses. The holdout scoring execution boundary is intercepted, and the cached-final race changes a synthetic peer file through tracing without replacing a decision result.

The checkpoints, tokenizer and JAR are deliberately nonexecutable synthetic bytes. These checks validate orchestration and evidence handling. They do not validate neural checkpoint decoding, empirical model quality, actual latency or memory consumption, Android operation, or a model promotion. Those require separate real execution evidence.
