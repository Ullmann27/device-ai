#!/usr/bin/env python3
"""Four source-bound direct-dispatch probes, without mocking decision methods.

Only subprocess.Popen is mocked. For the race probe a Python trace changes one
byte of a synthetic expanded checkpoint when the real authorize_training_call
returns. No function result, decision or model-runtime method is replaced.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import types
from unittest import mock


ROOT = Path(__file__).resolve().parent
SUPPORT_PATH = ROOT / "adversarial_driver_gate.py"
support = types.ModuleType("direct_dispatch_fixture_support")
support.__file__ = str(SUPPORT_PATH)
exec(compile(SUPPORT_PATH.read_bytes(), str(SUPPORT_PATH), "exec"), support.__dict__)


class ProcessLaunchBlocked(BaseException):
    pass


def train_arguments(f, label: str) -> list[str]:
    initial = f.baseline if label == "continued-small" else f.ladder["lastPassed"]
    return ["train", initial["path"], str(f.tokenizer), str(f.train), str(f.store)]


def seed_training_cache(f) -> None:
    candidate = f.candidate("continued-small", 132160)
    schedule = hashlib.sha256(b"synthetic schedule").hexdigest()
    text = f.source_record(f.baseline)
    text += support.tsv("TRAIN_INPUT", datasetSha256=support.digest(f.train),
                        tokenizerSha256=support.digest(f.tokenizer), scheduleSha256=schedule,
                        tokenScheduleSha256=schedule)
    text += support.tsv("CHECKPOINT", sha256=candidate["sha256"], path=candidate["path"],
                        parameters=candidate["parameters"], bytes=candidate["bytes"])
    text += support.tsv("TRAIN_RESULT", steps=16, tokens=64, changedWeights=1,
                        scheduleSha256=schedule, replicaEmbeddingMaxDelta=0.0, millis=1)
    f.receipt("continued-small-train", train_arguments(f, "continued-small"), text)
    f.edit_receipt("continued-small-train", lambda r: r.update(timeoutSeconds=600))


CASES = ["valid_direct_call_reaches_process_boundary", "failed_baseline_blocks_direct_call",
         "failed_baseline_blocks_direct_cached_call", "expanded_checkpoint_swap_after_authorization"]


def run_case(f, name: str) -> dict:
    if name == "failed_baseline_blocks_direct_cached_call":
        seed_training_cache(f)
    if name.startswith("failed_baseline_"):
        support.mutate(f, "baseline_failed_lastpassed_null")
    module = types.ModuleType("direct_dispatch_" + name)
    module.__file__ = str(f.script)
    exec(compile(f.script.read_bytes(), str(f.script), "exec"), module.__dict__)
    module.BASE_SHA = support.digest(f.base)
    module.TOKENIZER_SHA = support.digest(f.tokenizer)
    module.HOLDOUT_SHA = support.digest(f.holdout)
    events = []
    returned = False
    exception = None
    mutated = False
    previous_trace = sys.gettrace()

    def process(command, *args, **kwargs):
        events.append({"event": "PROCESS_ATTEMPT_BLOCKED", "command": [str(x) for x in command],
                       "trainingCommand": len(command) > 6 and command[6] == "train"})
        raise ProcessLaunchBlocked("Popen intercepted; no process launched")

    try:
        with mock.patch.object(module.subprocess, "Popen", process):
            experiment = module.Experiment(argparse.Namespace(inputs=f.inputs, output=f.out, jar=f.jar, holdout=f.holdout))
            label = "continued-expanded" if name == "expanded_checkpoint_swap_after_authorization" else "continued-small"
            arguments = train_arguments(f, label)
            if name == "expanded_checkpoint_swap_after_authorization":
                authorization_code = experiment.authorize_training_call.__func__.__code__
                target = Path(arguments[1])
                authorization_raised = False

                def trace(frame, event, arg):
                    nonlocal mutated, authorization_raised
                    if frame.f_code is authorization_code and frame.f_locals.get("self") is experiment:
                        if event == "exception":
                            authorization_raised = True
                        if event == "return" and not authorization_raised and not mutated:
                            before = target.read_bytes()
                            after = bytes([before[0] ^ 1]) + before[1:]
                            target.write_bytes(after)
                            mutated = True
                            events.append({"event": "SYNTHETIC_CHECKPOINT_CHANGED_AFTER_AUTHORIZATION",
                                           "path": str(target), "beforeSha256": hashlib.sha256(before).hexdigest(),
                                           "afterSha256": hashlib.sha256(after).hexdigest(),
                                           "byteLengthPreserved": len(before) == len(after),
                                           "authorizationReturnType": type(arg).__name__})
                    return trace

                sys.settrace(trace)
            experiment.call(label + "-train", arguments, timeout=600, allow_failure=True)
            returned = True
    except BaseException as error:
        exception = {"type": type(error).__name__, "message": str(error)}
    finally:
        sys.settrace(previous_trace)

    launches = [event for event in events if event["event"] == "PROCESS_ATTEMPT_BLOCKED"]
    rejected = exception is not None and exception["type"] in {"RuntimeError", "ValueError"} and not returned
    if name == "valid_direct_call_reaches_process_boundary":
        passed = len(launches) == 1 and launches[0]["trainingCommand"] and exception["type"] == "ProcessLaunchBlocked"
    else:
        passed = not launches and rejected
        if name == "expanded_checkpoint_swap_after_authorization":
            passed = passed and mutated
    return {"case": name, "pass": bool(passed), "processAttemptsBlocked": len(launches),
            "returnedWithoutValidationError": returned, "exception": exception,
            "checkpointMutatedOnlyInFixture": mutated, "events": events, "fixture": str(f.root)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, nargs="?", default=ROOT.parent / "run_experiment.py",
                        help="Driver source to audit; defaults to the adjacent project driver")
    parser.add_argument("--workspace", type=Path,
                        help="Optional output directory; defaults to a new temporary directory")
    args = parser.parse_args()
    raw = args.source.read_bytes()
    source_sha = hashlib.sha256(raw).hexdigest()
    workspace = args.workspace or Path(tempfile.mkdtemp(prefix="continuum-g1-direct-review-"))
    workspace.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="direct-" + source_sha[:12] + "-", dir=workspace))
    results = []
    for name in CASES:
        fixture = support.Fixture(run / name, raw)
        result = run_case(fixture, name)
        results.append(result)
        print(json.dumps(result), flush=True)
    report = {"task": "G1-INTEGRATE-WEIGHT-002", "sourcePath": str(args.source.resolve()),
              "sourceSha256": source_sha, "sourceHashAfterRun": support.digest(args.source),
              "directHarnessSha256": support.digest(Path(__file__)), "fixtureHarnessSha256": support.digest(SUPPORT_PATH),
              "instrumentation": ["Only subprocess.Popen is mocked", "Trace-triggered synthetic data mutation after actual authorization return"],
              "decisionMethodsMocked": [], "cases": results,
              "passed": sum(result["pass"] for result in results), "failed": sum(not result["pass"] for result in results),
              "realModelProcesses": 0, "realTrainingRuns": 0}
    target = run / "report.json"
    support.write_json(target, report)
    print(json.dumps({"report": str(target), "sha256": support.digest(target), "sourceSha256": source_sha,
                      "passed": report["passed"], "failed": report["failed"]}), flush=True)
    raise SystemExit(0 if report["failed"] == 0 else 1)


if __name__ == "__main__":
    main()
