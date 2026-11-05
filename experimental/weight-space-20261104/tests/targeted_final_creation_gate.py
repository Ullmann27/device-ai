#!/usr/bin/env python3
"""Six separate synthetic probes for reviewed final/creation-provenance gaps.

This does not change the frozen36-case matrix. All subprocesses, training and
holdout evaluation are intercepted. Fixture construction comes from the versioned
adversarial_driver_gate.py; its digest is part of each report.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import types
from unittest import mock


ROOT = Path(__file__).resolve().parent
SUPPORT_PATH = ROOT / "adversarial_driver_gate.py"
support = types.ModuleType("gate_fixture_support")
support.__file__ = str(SUPPORT_PATH)
exec(compile(SUPPORT_PATH.read_bytes(), str(SUPPORT_PATH), "exec"), support.__dict__)


class HoldoutEvaluationBlocked(BaseException):
    pass


def edit_creation(f, label: str, *, parent=None, argument=None) -> None:
    receipt_path = f.logs / (label + "-create.receipt.json")
    log_path = f.logs / (label + "-create.tsv")
    receipt = json.loads(receipt_path.read_text())
    if parent is not None:
        receipt["arguments"][1] = parent["path"]
        receipt["inputHashes"]["checkpointSha256"] = parent["sha256"]
        lines = log_path.read_text().splitlines(keepends=True)
        log_path.write_text(f.source_record(parent) + "".join(line for line in lines if not line.startswith("SOURCE\t")))
    if argument is not None:
        index, value = argument
        receipt["arguments"][index] = value
    receipt["command"] = receipt["command"][:6] + receipt["arguments"]
    receipt["logSha256"] = support.digest(log_path)
    support.write_json(receipt_path, receipt)


def direct_final(f, name: str) -> dict:
    support.mutate(f, "baseline_failed_lastpassed_null")
    support.write_json(f.out / "frozen-selection.json", {
        "orchestratorSha256": f.source_sha, "holdoutSha256": support.digest(f.holdout),
        "holdoutOpened": False, "developmentSha256": support.digest(f.development),
        "trainingSha256": support.digest(f.train), "lastCapacityPassed": None,
        "candidates": [deepcopy(f.baseline)], "developmentPreferred": None,
        "selectionStatus": "NO_CAPACITY_ELIGIBLE_CANDIDATE", "matchedTrainingInputs": False,
        "trainingFailures": [],
    })
    if name == "direct_final_cached_baseline_failure":
        support.write_json(f.out / "final.json", {"syntheticCachedResult": True})
    module = types.ModuleType("final_gap_probe_" + name)
    module.__file__ = str(f.script)
    exec(compile(f.script.read_bytes(), str(f.script), "exec"), module.__dict__)
    module.BASE_SHA = support.digest(f.base)
    module.TOKENIZER_SHA = support.digest(f.tokenizer)
    module.HOLDOUT_SHA = support.digest(f.holdout)
    events = []
    original_score, original_invoke, original_call = module.Experiment.score, module.Experiment.invoke, module.Experiment.call

    def score(self, candidate, suffix="dev", **options):
        if suffix == "holdout":
            events.append({"event": "HOLDOUT_SCORE_ATTEMPT_BLOCKED"})
            raise HoldoutEvaluationBlocked("holdout evaluation trapped before execution")
        return original_score(self, candidate, suffix, **options)

    def invoke(self, label, mode, checkpoint, *extra, **options):
        if mode == "train":
            events.append({"event": "TRAIN_ATTEMPT_BLOCKED", "boundary": "invoke"})
            raise support.TrainingBlocked("training forbidden")
        return original_invoke(self, label, mode, checkpoint, *extra, **options)

    def call(self, label, arguments, **options):
        if not options.get("benchmark", False) and arguments and arguments[0] == "train":
            events.append({"event": "TRAIN_ATTEMPT_BLOCKED", "boundary": "call"})
            raise support.TrainingBlocked("training forbidden")
        return original_call(self, label, arguments, **options)

    def process(command, *args, **kwargs):
        events.append({"event": "PROCESS_ATTEMPT_BLOCKED", "command": [str(x) for x in command]})
        raise support.ExternalProcessBlocked("all external processes forbidden")

    exception = None; returned = False
    try:
        with mock.patch.object(module.Experiment, "score", score), mock.patch.object(module.Experiment, "invoke", invoke), \
                mock.patch.object(module.Experiment, "call", call), mock.patch.object(module.subprocess, "Popen", process), \
                mock.patch.object(module.subprocess, "run", process):
            experiment = module.Experiment(argparse.Namespace(inputs=f.inputs, output=f.out, jar=f.jar, holdout=f.holdout))
            experiment.final()
            returned = True
    except BaseException as error:
        exception = {"type": type(error).__name__, "message": str(error)}
    return {"case": name, "pass": not returned and not events and exception is not None,
            "returnedWithoutValidationError": returned, "exception": exception, "events": events,
            "fixture": str(f.root)}


CASES = ["direct_final_fresh_baseline_failure", "direct_final_cached_baseline_failure",
         "missing_width_creation_receipt", "tampered_width_creation_parent",
         "tampered_width_creation_noise", "tampered_depth_creation_seed"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, nargs="?",
                        default=Path(__file__).resolve().parent.parent / "run_experiment.py",
                        help="Driver source to audit; defaults to the adjacent project driver")
    parser.add_argument("--workspace", type=Path,
                        help="Optional output directory; defaults to a new temporary directory")
    args = parser.parse_args()
    raw = args.source.read_bytes(); source_sha = hashlib.sha256(raw).hexdigest()
    workspace = args.workspace or Path(tempfile.mkdtemp(prefix="continuum-g1-targeted-review-"))
    workspace.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="targeted-" + source_sha[:12] + "-", dir=workspace))
    results = []
    for name in CASES:
        f = support.Fixture(run / name, raw)
        if name.startswith("direct_final_"):
            result = direct_final(f, name)
        else:
            if name == "missing_width_creation_receipt":
                (f.logs / "width-4-create.receipt.json").unlink()
            elif name == "tampered_width_creation_parent":
                edit_creation(f, "width-4", parent=f.calibration["candidates"][1])
            elif name == "tampered_width_creation_noise":
                edit_creation(f, "width-4", argument=(4, "0.002"))
            elif name == "tampered_depth_creation_seed":
                edit_creation(f, "depth-3", argument=(4, "20261105"))
            result = support.execute_case(f, name)
        results.append(result)
        print(json.dumps(result), flush=True)
    report = {"task": "G1-INTEGRATE-WEIGHT-002", "scope": "Separate targeted review-gap probes, not edits to frozen36 semantics",
              "sourcePath": str(args.source.resolve()), "sourceSha256": source_sha,
              "sourceHashAfterRun": support.digest(args.source), "targetedHarnessSha256": support.digest(Path(__file__)),
              "fixtureHarnessSha256": support.digest(SUPPORT_PATH), "cases": results,
              "passed": sum(r["pass"] for r in results), "failed": sum(not r["pass"] for r in results),
              "realModelProcesses": 0, "realTrainingRuns": 0, "realHoldoutEvaluations": 0}
    target = run / "report.json"
    support.write_json(target, report)
    print(json.dumps({"report": str(target), "sha256": support.digest(target), "sourceSha256": source_sha,
                      "passed": report["passed"], "failed": report["failed"]}), flush=True)
    raise SystemExit(0 if report["failed"] == 0 else 1)


if __name__ == "__main__":
    main()
