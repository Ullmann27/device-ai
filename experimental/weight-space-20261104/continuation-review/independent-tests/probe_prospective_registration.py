#!/usr/bin/env python3
"""Check that repaired controllers cannot silently reuse historical identities.

All registrations, inputs and training receipts here are synthetic. The actual
Study constructors, verify/selection and freeze paths run unchanged. No process
may start, and no historical repository receipt is modified.
"""
from __future__ import annotations

import argparse
from contextlib import redirect_stdout
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import tempfile
import types
from unittest.mock import patch


HERE = Path(__file__).resolve().parent
BINDING_PROBE_SHA = "5954cf5cfd6effe29738f9dae5213287b73430b85f9f076d5d6e3481670cd4a9"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=("capacity-repair", "choice-repair"), required=True)
    parser.add_argument("--report", type=Path, required=True)
    for kind in ("long", "suffix"):
        parser.add_argument("--" + kind + "-runner", type=Path)
        parser.add_argument("--" + kind + "-sha256")
    args = parser.parse_args()
    path = HERE / "probe_choice_binding.py"
    if digest(path) != BINDING_PROBE_SHA:
        raise RuntimeError("UNREVIEWED_FIXTURE_ORCHESTRATOR")
    binding = types.ModuleType("pinned_choice_fixture")
    binding.__file__ = str(path)
    exec(compile(path.read_bytes(), str(path), "exec"), binding.__dict__)
    sources = binding.subjects(args)
    fixture = binding.load(binding.OLD / "independent-tests/probe_post_capacity_gate.py",
                           binding.FIXTURE_SHA, "pinned_capacity_fixture")
    fixture.REVIEWED_COMMIT = binding.PIN
    results = []
    for kind in ("long", "suffix"):
        for mode in ("old_registration", "new_registration_old_receipts", "fresh_registration_and_receipts"):
            with tempfile.TemporaryDirectory(prefix="registration-contract-", dir=HERE) as temporary:
                root = Path(temporary)
                repaired_path, repaired_sha = sources[kind]
                old_source = HERE / "fixtures" / ("original-" + kind + ".py.txt")
                if mode == "fresh_registration_and_receipts":
                    module = binding.load(repaired_path, repaired_sha, "fresh_repaired_controller")
                    study = fixture.fixture(module, root, repaired_path, kind)
                else:
                    original = binding.load(old_source, binding.ORIGINAL[kind], "historical_controller")
                    study = fixture.fixture(original, root, old_source, kind)
                    current_path = root / fixture.SOURCE_PATHS[kind]
                    current_path.write_bytes(repaired_path.read_bytes())
                    if mode == "new_registration_old_receipts":
                        registration = json.loads(study.registration_path.read_text())
                        registration["files"][fixture.SOURCE_PATHS[kind]] = repaired_sha
                        binding.write_json(study.registration_path, registration)
                    module = binding.load(current_path, repaired_sha, "changed_controller")
                before = {str(p.relative_to(root)): digest(p) for p in root.rglob("*") if p.is_file()}
                attempts = []

                def block_process(command, *unused, **kwargs):
                    attempts.append(command)
                    raise AssertionError("REGISTRATION_CHECK_MUST_NOT_START_PROCESS")

                error = None
                try:
                    with patch("subprocess.Popen", side_effect=block_process), redirect_stdout(io.StringIO()):
                        verified = module.Study(study.args)
                        verified.freeze()
                except (OSError, RuntimeError, ValueError, KeyError, TypeError, AssertionError) as exc:
                    error = type(exc).__name__ + ": " + str(exc)
                frozen = study.out / "selection-freeze.json"
                if mode == "old_registration":
                    passed = error is not None and "SOURCE_OR_DATA_CHANGED:" in error and not frozen.exists()
                elif mode == "new_registration_old_receipts":
                    passed = error is not None and "INVALID_TRAINING_RECEIPT" in error and not frozen.exists()
                else:
                    passed = error is None and frozen.exists()
                    if passed:
                        selection = json.loads(frozen.read_text())
                        passed = selection["status"] == "FROZEN_BEFORE_TEST"
                        passed = passed and selection["registrationSha256"] == digest(study.registration_path)
                        passed = passed and set(selection.get("postTrainingCapacity", {})) == {"S1", "W1"}
                unchanged = all(digest(root / p) == value for p, value in before.items())
                results.append({"runner": kind, "case": mode, "passed": bool(passed and unchanged and not attempts),
                    "testedSourceSha256": repaired_sha, "existingFixtureBytesUnchangedBySubject": unchanged,
                    "freezeCreated": frozen.exists(), "processBoundaryCalls": len(attempts), "actualModelProcesses": 0,
                    "error": error.replace(str(root), "<SYNTHETIC_ROOT>") if error else None})
    failed = sum(not row["passed"] for row in results)
    report = {"schemaVersion": 1, "observedUtc": datetime.now(timezone.utc).isoformat(),
              "phase": args.phase, "reviewedOriginalCommit": binding.PIN,
              "probeSha256": digest(Path(__file__)), "fixtureOrchestratorSha256": BINDING_PROBE_SHA,
              "scope": "SYNTHETIC_PROSPECTIVE_REGISTRATION_AND_OLD_RECEIPT_REJECTION",
              "actualModelProcesses": 0, "realHoldoutRead": False,
              "historicalRegistrationOrReceiptsModified": False,
              "passed": len(results) - failed, "failed": failed, "cases": results,
              "status": "GREEN" if not failed else "FAILED", "exitCode": int(bool(failed))}
    binding.write_json(args.report, report)
    print(json.dumps({key: report[key] for key in ("phase", "passed", "failed", "status", "probeSha256", "exitCode")}))
    return report["exitCode"]


if __name__ == "__main__":
    raise SystemExit(main())
