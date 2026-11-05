#!/usr/bin/env python3
"""Independent synthetic integration tests for the registered 6–10M study.

Real constructors, receipt verification, preflights, training authorization and
joint freezes execute. Only original-content SHA constants are rebound to opaque
synthetic files. Popen is always blocked; the constructor's java-version probe is
answered synthetically. Holdout scoring is intercepted at its execution boundary.
The established 36+6+4 suites and their fixture support remain unchanged.
"""
from __future__ import annotations

import argparse
from copy import copy, deepcopy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import types
from unittest import mock


ROOT = Path(__file__).resolve().parent
SUPPORT_PATH = ROOT / "adversarial_driver_gate.py"
support = types.ModuleType("registered_profile_fixture_support")
support.__file__ = str(SUPPORT_PATH)
exec(compile(SUPPORT_PATH.read_bytes(), str(SUPPORT_PATH), "exec"), support.__dict__)

# Independent expected values from the registered plan, not production helpers.
PROFILES = {
    "core-6562304": {"parameterBudget": 6562304, "targetLabel": "width-8",
                     "targetParameters": 6562304, "nextLabel": "depth-3", "nextParameters": 9712128},
    "core-9712128": {"parameterBudget": 10000000, "targetLabel": "depth-3",
                     "targetParameters": 9712128, "nextLabel": "depth-4", "nextParameters": 12861952},
}
SMALL, LARGE = PROFILES


class ProcessBlocked(BaseException):
    pass


class HoldoutBlocked(BaseException):
    pass


def document_sha(value: dict) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def replace_paths(value, before: Path, after: Path):
    return json.loads(json.dumps(value).replace(str(before), str(after)))


class StudyFixture:
    def __init__(self, root: Path, source: bytes, plan: bytes, events: list):
        self.root = root
        self.common = support.Fixture(root / "common", source)
        self.study = root / "study"
        self.study.mkdir()
        self.plan = self.common.source / "CORE_6_10M_PLAN.md"
        self.plan.write_bytes(plan)
        self.module = types.ModuleType("registered_profiles_" + root.name)
        self.module.__file__ = str(self.common.script)
        exec(compile(source, str(self.common.script), "exec"), self.module.__dict__)
        for name, path in (("BASE_SHA", self.common.base), ("TOKENIZER_SHA", self.common.tokenizer),
                           ("HOLDOUT_SHA", self.common.holdout), ("CORE_HOLDOUT_SHA", self.common.holdout),
                           ("CORE_JAR_SHA", self.common.jar)):
            setattr(self.module, name, support.digest(path))
        self.children = {}
        self.experiments = {}
        self.events = events
        for profile in PROFILES:
            experiment = self.module.Experiment(self.args(profile))
            self.experiments[profile] = experiment
            self.children[profile] = self.populate_child(profile, experiment)
        registration = json.loads((self.study / "study-registration.json").read_bytes())
        expected_profiles = {}
        for profile, spec in PROFILES.items():
            descriptor = {"profileId": profile, **spec, "studyPlanSha256": support.digest(self.plan)}
            expected_profiles[profile] = {**descriptor, "profileSha256": document_sha(descriptor),
                                          "output": str(self.study / profile)}
        if registration["profiles"] != expected_profiles:
            raise AssertionError("constructor registration differs from independent fixed profiles")

    def args(self, profile: str):
        f = self.common
        return argparse.Namespace(profile=profile, study_root=self.study, inputs=f.inputs,
                                  output=self.study / profile, jar=f.jar, holdout=f.holdout)

    def identity(self, profile: str) -> dict:
        descriptor = {"profileId": profile, **PROFILES[profile], "studyPlanSha256": support.digest(self.plan)}
        return {"profileId": profile, "profileSha256": document_sha(descriptor),
                "studyPlanSha256": descriptor["studyPlanSha256"],
                "studyRegistrationSha256": support.digest(self.study / "study-registration.json")}

    def runtime(self, profile: str) -> dict:
        return {"jarSha256": support.digest(self.common.jar),
                "protocolSha256": support.digest(self.common.source / "PROTOCOL.md"), **self.identity(profile)}

    def stamp(self, profile: str, label: str | None = None) -> None:
        f = self.children[profile]
        paths = [f.logs / (label + ".receipt.json")] if label else sorted(f.logs.glob("*.receipt.json"))
        for path in paths:
            receipt = json.loads(path.read_bytes())
            receipt["runtimeIdentity"] = self.runtime(profile)
            receipt["logSha256"] = support.digest(path.with_name(path.name.removesuffix(".receipt.json") + ".tsv"))
            support.write_json(path, receipt)

    def populate_child(self, profile: str, experiment):
        original = self.common
        f = copy(original)
        f.out = self.study / profile
        f.root, f.logs, f.store, f.train = f.out, f.out / "logs", f.out / "checkpoints", f.out / "train.txt"
        for entry in original.out.iterdir():
            if entry.name == "environment.json":
                continue
            if entry.is_dir():
                shutil.copytree(entry, f.out / entry.name, dirs_exist_ok=True)
            else:
                shutil.copyfile(entry, f.out / entry.name)
        for path in f.out.rglob("*"):
            if path.is_file() and path.suffix in {".json", ".tsv"} and path.name != "environment.json":
                path.write_text(path.read_text().replace(str(original.out), str(f.out)))
        f.environment = deepcopy(experiment.environment)
        f.baseline = deepcopy(original.baseline)
        f.calibration = replace_paths(original.calibration, original.out, f.out)
        f.calibration.update(self.identity(profile))
        support.write_json(f.out / "calibration.json", f.calibration)
        count = 4 if profile == SMALL else 5
        f.stages = replace_paths(original.stages[:count], original.out, f.out)
        spec = PROFILES[profile]
        terminal = {"label": spec["nextLabel"], "parameters": spec["nextParameters"],
                    "parameterBytes": spec["nextParameters"] * 4, "status": "PREFLIGHT_REJECTED",
                    "reason": "BUDGET_LIMIT", "parameterLimit": spec["parameterBudget"], **self.identity(profile)}
        f.ladder = {"stages": deepcopy(f.stages) + [terminal], "lastPassed": deepcopy(f.stages[-1]),
                    "stop": "BUDGET_LIMIT", **self.identity(profile)}
        f.save_ladder()
        self.children[profile] = f
        self.stamp(profile)
        return f

    def complete_children(self, *, small_wins: bool = False) -> None:
        schedule = hashlib.sha256(b"synthetic registered schedule:16x4x63").hexdigest()
        for profile, f in self.children.items():
            initial = deepcopy(f.ladder["lastPassed"])
            f.evaluate_receipt(initial, 2.0 if small_wins else 1.9)
            candidates = [deepcopy(f.baseline), initial]
            for label, parent in (("continued-small", f.baseline), ("continued-expanded", initial)):
                content = ("SYNTHETIC NONEXECUTABLE " + profile + " " + label + "\n").encode() * 4
                path = f.store / (hashlib.sha256(content).hexdigest() + ".lm1")
                path.write_bytes(content)
                c = f.candidate(label, parent["parameters"], path)
                training_input = {"kind": "TRAIN_INPUT", "datasetSha256": support.digest(f.train),
                                  "tokenizerSha256": support.digest(f.tokenizer),
                                  "scheduleSha256": schedule, "tokenScheduleSha256": schedule}
                training = {"kind": "TRAIN_RESULT", "steps": "16", "tokens": "256", "changedWeights": "1",
                            "scheduleSha256": schedule, "replicaEmbeddingMaxDelta": "0.0", "millis": "1"}
                text = f.source_record(parent)
                text += support.tsv(**training_input)
                text += support.tsv("CHECKPOINT", sha256=c["sha256"], path=c["path"],
                                    parameters=c["parameters"], bytes=c["bytes"])
                text += support.tsv(**training)
                arguments = ["train", parent["path"], str(f.tokenizer), str(f.train), str(f.store)]
                f.receipt(label + "-train", arguments, text)
                f.edit_receipt(label + "-train", lambda receipt: receipt.update(timeoutSeconds=600))
                c["trainingInput"], c["training"] = training_input, training
                f.evaluate_receipt(c, 3.0 if small_wins else (1.95 if label == "continued-small" else 1.8))
                f.capacity_receipt(c)
                if label == "continued-expanded":
                    layers = 2 if profile == SMALL else 3
                    f.edit_log(label + "-capacity", lambda text: text.replace(
                        "\tdim=64\tlayers=2\theads=4\tcontext=256\tmlp=256",
                        f"\tdim=512\tlayers={layers}\theads=4\tcontext=256\tmlp=2048"))
                candidates.append(c)
            self.stamp(profile)
            minimum = min(c["devBpb"] for c in candidates if c["capacityEligible"])
            preferred = min((c for c in candidates if c["capacityEligible"] and c["devBpb"] <= minimum + 0.00001),
                            key=lambda c: (c["parameters"], c["devBpb"]))
            frozen = {"frozenUtc": "SYNTHETIC", "holdoutSha256": support.digest(f.holdout), "holdoutOpened": False,
                      "orchestratorSha256": f.source_sha, "developmentSha256": support.digest(f.development),
                      "trainingSha256": support.digest(f.train), "candidates": candidates,
                      "developmentPreferred": preferred, "selectionStatus": "SELECTED", "matchedTrainingInputs": True,
                      "lastCapacityPassed": deepcopy(f.ladder["lastPassed"]), "trainingFailures": [],
                      "selectionRule": "minimum development BPB among completed eligible candidates; within 0.00001 BPB prefer fewer parameters",
                      **self.identity(profile)}
            support.write_json(f.out / "frozen-selection.json", frozen)

    def freeze(self) -> None:
        self.experiments[SMALL].study_freeze()
        value = json.loads((self.study / "study-frozen-selection.json").read_bytes())
        if set(value["profiles"]) != set(PROFILES) or value["holdoutOpenedAtFreeze"] is not False:
            raise AssertionError("joint freeze does not bind both sealed children")
        for profile, f in self.children.items():
            if value["profiles"][profile]["selectionSha256"] != support.digest(f.out / "frozen-selection.json"):
                raise AssertionError("joint freeze omitted the complete child-selection file")

    def seed_final_cache(self, profile: str) -> None:
        f = self.children[profile]
        selection = json.loads((f.out / "frozen-selection.json").read_bytes())
        support.write_json(f.out / "final.json", {
            "selectionSha256": support.digest(f.out / "frozen-selection.json"),
            "holdoutSha256": support.digest(f.holdout), "originalsUnchanged": True,
            "studySelectionSha256": support.digest(self.study / "study-frozen-selection.json"),
            "results": deepcopy(selection["candidates"]), "syntheticNeverEvaluated": True})

    def change_peer_nonwinning_candidate(self, profile: str) -> None:
        peer = self.children[LARGE if profile == SMALL else SMALL]
        path = peer.out / "frozen-selection.json"
        value = json.loads(path.read_bytes())
        control = next(c for c in value["candidates"] if c["label"] == "continued-small")
        control["devBpb"] += 0.125
        support.write_json(path, value)
        self.events.append({"event": "PEER_NONWINNING_CHILD_MUTATED", "profile": peer.out.name})


CASES = [
    "valid_budget_6562304_direct_train", "valid_budget_9712128_direct_train",
    "valid_joint_freeze_then_final_6562304", "valid_joint_freeze_then_final_9712128",
    "unchanged_quality_rule_can_prefer_small_control",
    "wrong_stop_blocks_train", "wrong_target_blocks_train", "wrong_budget_blocks_train",
    "wrong_profile_hash_blocks_train", "historical_receipt_blocks_train", "foreign_profile_receipt_blocks_train",
    "changed_plan_blocks_train", "changed_registration_blocks_train", "baseline_failure_blocks_direct_train",
    "target_failure_blocks_small_control_train", "missing_global_freeze_blocks_final",
    "malformed_global_freeze_blocks_final", "incomplete_global_freeze_blocks_final",
    "missing_peer_child_blocks_study_freeze", "changed_peer_nonwinner_blocks_final",
    "global_freeze_blocks_train_after_child_loss", "invalid_global_freeze_blocks_train_after_child_loss",
    "global_freeze_blocks_missing_child_environment", "cached_final_requires_global_freeze",
    "peer_child_change_during_final_prevents_publication", "peer_child_change_during_cached_final_rejects",
    "global_freeze_blocks_historical_downgrade_after_child_loss",
    "dangling_global_freeze_blocks_train_after_child_loss",
    "global_freeze_blocks_historical_downgrade_through_child_symlink",
]


def run_case(root: Path, source: bytes, plan: bytes, name: str) -> dict:
    events = []
    exception = None
    returned = False
    setup_complete = False
    fixture = None
    initial_final_bytes = None
    previous_trace = sys.gettrace()

    def popen(command, *args, **kwargs):
        events.append({"event": "PROCESS_ATTEMPT_BLOCKED", "command": [str(v) for v in command],
                       "train": len(command) > 6 and command[6] == "train"})
        raise ProcessBlocked("synthetic process boundary; no process launched")

    def run(command, *args, **kwargs):
        if command == ["java", "-version"]:
            events.append({"event": "ENVIRONMENT_VERSION_STUB"})
            return subprocess.CompletedProcess(command, 0, "", "SYNTHETIC JVM VERSION; no JVM executed")
        return popen(command, *args, **kwargs)

    profile = LARGE if name in {"valid_budget_9712128_direct_train", "valid_joint_freeze_then_final_9712128"} else SMALL
    full = ("final" in name or "freeze" in name or name == "unchanged_quality_rule_can_prefer_small_control")
    try:
        with mock.patch.object(subprocess, "Popen", popen), mock.patch.object(subprocess, "run", run):
            fixture = StudyFixture(root, source, plan, events)
            f, experiment = fixture.children[profile], fixture.experiments[profile]
            if full:
                fixture.complete_children(small_wins=name == "unchanged_quality_rule_can_prefer_small_control")
            global_path = fixture.study / "study-frozen-selection.json"
            if full and name not in {"missing_global_freeze_blocks_final", "missing_peer_child_blocks_study_freeze"}:
                fixture.freeze()
            if "cached_final" in name:
                fixture.seed_final_cache(profile)
                initial_final_bytes = (f.out / "final.json").read_bytes()

            if name == "wrong_stop_blocks_train":
                f.ladder["stop"] = "CHECKPOINT_64_MIB_CONTRACT"
                f.save_ladder()
            elif name == "wrong_target_blocks_train":
                f.ladder["lastPassed"] = deepcopy(f.stages[-2])
                f.save_ladder()
            elif name == "wrong_budget_blocks_train":
                f.ladder["stages"][-1]["parameterLimit"] += 1
                f.save_ladder()
            elif name == "wrong_profile_hash_blocks_train":
                f.ladder["profileSha256"] = "0" * 64
                f.save_ladder()
            elif name == "historical_receipt_blocks_train":
                f.edit_receipt("baseline-capacity", lambda r: (r.pop("runtimeIdentity"),
                    r.update(orchestratorSha256=fixture.module.HISTORICAL_ORCHESTRATOR_SHA)))
            elif name == "foreign_profile_receipt_blocks_train":
                f.edit_receipt("baseline-capacity", lambda r: r.update(runtimeIdentity=fixture.runtime(LARGE)))
            elif name == "changed_plan_blocks_train":
                fixture.plan.write_bytes(plan + b"\nSYNTHETIC CHANGE\n")
            elif name == "changed_registration_blocks_train":
                path = fixture.study / "study-registration.json"
                value = json.loads(path.read_bytes())
                value["profiles"][SMALL]["parameterBudget"] += 1
                support.write_json(path, value)
            elif name == "baseline_failure_blocks_direct_train":
                f.capacity_receipt(f.baseline, passed=False)
                fixture.stamp(profile, "baseline-capacity")
            elif name == "target_failure_blocks_small_control_train":
                failed = deepcopy(f.stages[-1])
                f.capacity_receipt(failed, passed=False)
                fixture.stamp(profile, failed["label"] + "-capacity")
                f.ladder.update(stages=deepcopy(f.stages[:-1]) + [failed], lastPassed=deepcopy(f.stages[-2]),
                                stop="PARITY_OR_CAPACITY_FAILURE")
                f.save_ladder()
            elif name == "malformed_global_freeze_blocks_final":
                global_path.write_text("{invalid JSON")
            elif name == "incomplete_global_freeze_blocks_final":
                value = json.loads(global_path.read_bytes())
                del value["profiles"][LARGE]
                support.write_json(global_path, value)
            elif name == "missing_peer_child_blocks_study_freeze":
                (fixture.children[LARGE].out / "frozen-selection.json").unlink()
            elif name == "changed_peer_nonwinner_blocks_final":
                fixture.change_peer_nonwinning_candidate(profile)
            elif name in {"global_freeze_blocks_train_after_child_loss", "invalid_global_freeze_blocks_train_after_child_loss"}:
                (f.out / "frozen-selection.json").unlink()
                if name.startswith("invalid_"):
                    global_path.write_text("{invalid JSON")
            elif name == "global_freeze_blocks_missing_child_environment":
                (f.out / "environment.json").unlink()
            elif name in {"global_freeze_blocks_historical_downgrade_after_child_loss",
                          "global_freeze_blocks_historical_downgrade_through_child_symlink"}:
                if name.endswith("through_child_symlink"):
                    f.out.rename(fixture.root / "saved-original-child")
                    redirected = fixture.root / "empty-redirected-child"
                    redirected.mkdir()
                    f.out.symlink_to(redirected, target_is_directory=True)
                else:
                    (f.out / "environment.json").unlink()
                legacy_holdout = f.inputs / "historical-holdout.txt"
                legacy_holdout.write_bytes(b"Distinct synthetic historical holdout; never evaluated.\n")
                fixture.module.HOLDOUT_SHA = support.digest(legacy_holdout)
            elif name == "dangling_global_freeze_blocks_train_after_child_loss":
                (f.out / "frozen-selection.json").unlink()
                for suffix in (".tsv", ".receipt.json"):
                    (f.logs / ("continued-small-train" + suffix)).unlink()
                global_path.unlink()
                global_path.symlink_to(fixture.study / "missing-freeze-target.json")
                if global_path.exists() or not global_path.is_symlink():
                    raise AssertionError("dangling-freeze fixture is not a dangling symbolic link")
            elif name == "cached_final_requires_global_freeze":
                global_path.unlink()

            original_score = fixture.module.Experiment.score

            def score(self, candidate, suffix="dev", **options):
                if suffix != "holdout":
                    return original_score(self, candidate, suffix, **options)
                events.append({"event": "SYNTHETIC_HOLDOUT_BOUNDARY", "candidate": candidate["label"]})
                if name == "peer_child_change_during_final_prevents_publication":
                    if not any(e["event"] == "PEER_NONWINNING_CHILD_MUTATED" for e in events):
                        fixture.change_peer_nonwinning_candidate(profile)
                    return {"kind": "SYNTHETIC_RESULT_NO_EVALUATION", "bpb": "1.0"}
                raise HoldoutBlocked("sealed synthetic scoring boundary reached; no evaluation executed")

            if name == "peer_child_change_during_cached_final_rejects":
                code = experiment.final.__func__.__code__

                def trace(frame, event, arg):
                    if (frame.f_code is code and event == "line" and "completed" in frame.f_locals
                            and not any(e["event"] == "PEER_NONWINNING_CHILD_MUTATED" for e in events)):
                        fixture.change_peer_nonwinning_candidate(profile)
                    return trace

                sys.settrace(trace)
            setup_complete = True
            with mock.patch.object(fixture.module.Experiment, "score", score):
                if name == "missing_peer_child_blocks_study_freeze":
                    experiment.study_freeze()
                elif name == "global_freeze_blocks_missing_child_environment":
                    fixture.module.Experiment(fixture.args(profile))
                elif name in {"global_freeze_blocks_historical_downgrade_after_child_loss",
                              "global_freeze_blocks_historical_downgrade_through_child_symlink"}:
                    args = fixture.args(profile)
                    args.profile, args.study_root, args.holdout = "historical", None, legacy_holdout
                    fixture.module.Experiment(args)
                elif "final" in name or name == "unchanged_quality_rule_can_prefer_small_control":
                    experiment.final()
                else:
                    target = f.ladder["lastPassed"] if name.startswith("valid_budget") else f.baseline
                    label = "continued-expanded" if name.startswith("valid_budget") else "continued-small"
                    experiment.call(label + "-train", ["train", target["path"], str(f.tokenizer), str(f.train), str(f.store)],
                                    timeout=600, allow_failure=True)
                returned = True
    except BaseException as error:
        exception = {"type": type(error).__name__, "message": str(error)}
    finally:
        sys.settrace(previous_trace)

    processes = [e for e in events if e["event"] == "PROCESS_ATTEMPT_BLOCKED"]
    holdout = [e for e in events if e["event"] == "SYNTHETIC_HOLDOUT_BOUNDARY"]
    mutated = any(e["event"] == "PEER_NONWINNING_CHILD_MUTATED" for e in events)
    final_path = fixture.children[profile].out / "final.json" if fixture else root / "not-created"
    rejected = exception is not None and exception["type"] in {"RuntimeError", "ValueError", "FileNotFoundError", "JSONDecodeError"}
    if name.startswith("valid_budget"):
        passed = setup_complete and len(processes) == 1 and processes[0]["train"] and not holdout
    elif name.startswith("valid_joint") or name == "unchanged_quality_rule_can_prefer_small_control":
        passed = setup_complete and not processes and len(holdout) == 1 and exception and exception["type"] == "HoldoutBlocked"
        if name == "unchanged_quality_rule_can_prefer_small_control" and fixture:
            frozen = json.loads((fixture.study / "study-frozen-selection.json").read_bytes())
            passed = passed and frozen["developmentPreferred"]["parameters"] == 132160
    elif name == "peer_child_change_during_final_prevents_publication":
        passed = setup_complete and not processes and bool(holdout) and mutated and rejected and not final_path.exists()
    elif name == "peer_child_change_during_cached_final_rejects":
        passed = setup_complete and not processes and not holdout and mutated and rejected and final_path.read_bytes() == initial_final_bytes
    else:
        passed = setup_complete and not processes and not holdout and rejected and not returned
    return {"case": name, "pass": bool(passed), "fixtureSetupComplete": setup_complete,
            "processAttemptsBlocked": len(processes), "syntheticHoldoutBoundaryCalls": len(holdout),
            "returnedWithoutValidationError": returned, "exception": exception, "events": events,
            "fixture": str(root)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, nargs="?", default=ROOT.parent / "run_experiment.py")
    parser.add_argument("--plan", type=Path, default=ROOT.parent / "CORE_6_10M_PLAN.md")
    parser.add_argument("--workspace", type=Path)
    parser.add_argument("--case", action="append", choices=CASES, dest="selected_cases",
                        help="Run only this named case; repeat for a focused source-bound comparison")
    args = parser.parse_args()
    raw, plan = args.source.read_bytes(), args.plan.read_bytes()
    source_sha = hashlib.sha256(raw).hexdigest()
    workspace = args.workspace or Path(tempfile.mkdtemp(prefix="continuum-core-profile-review-"))
    workspace.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="profiles-" + source_sha[:12] + "-", dir=workspace))
    results = []
    selected_cases = args.selected_cases or CASES
    for name in selected_cases:
        result = run_case(run / name, raw, plan, name)
        results.append(result)
        print(json.dumps(result), flush=True)
    report = {"task": "G1-INTEGRATE-WEIGHT-002-CORE-6-10M", "sourcePath": str(args.source.resolve()),
              "sourceSha256": source_sha, "sourceHashAfterRun": support.digest(args.source),
              "planSha256": hashlib.sha256(plan).hexdigest(), "planHashAfterRun": support.digest(args.plan),
              "profileHarnessSha256": support.digest(Path(__file__)), "fixtureHarnessSha256": support.digest(SUPPORT_PATH),
              "expectedProfiles": PROFILES, "cases": results,
              "selectedCases": selected_cases,
              "passed": sum(r["pass"] for r in results), "failed": sum(not r["pass"] for r in results),
              "decisionMethodsMocked": [], "realModelProcesses": 0, "realTrainingRuns": 0, "realHoldoutEvaluations": 0,
              "instrumentation": ["Original input/JAR SHA constants rebound to synthetic bytes",
                  "Popen blocked for every process; java-version environment probe stubbed",
                  "Only holdout score execution intercepted; development scoring reads verified cached evidence",
                  "Cached-final race uses a trace-triggered fixture mutation, without replacing decisions"]}
    target = run / "report.json"
    support.write_json(target, report)
    print(json.dumps({"report": str(target), "sha256": support.digest(target), "sourceSha256": source_sha,
                      "passed": report["passed"], "failed": report["failed"]}), flush=True)
    raise SystemExit(0 if report["failed"] == 0 else 1)


if __name__ == "__main__":
    main()
