#!/usr/bin/env python3
"""Synthetic integration probes for the continuation training gate.

Loads an exact frozen source copy. Real constructor, calibration loading, receipt
verification, scoring, continuation and dispatch logic run against synthetic files.
Only the three original-content SHA constants are replaced in module memory to
name the synthetic inputs. No gate, loader or decision helper is mocked.
Training is recorded and blocked at invoke, call and process boundaries. All other
process launches are also blocked. A positive control must reach the training trap
so a universal failure or an unusable fixture cannot masquerade as a fixed gate.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import time
import types
from unittest import mock


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def tsv(kind: str, **fields: object) -> str:
    return "\t".join([kind, *(f"{key}={str(value).lower() if isinstance(value, bool) else value}"
                                for key, value in fields.items())]) + "\n"


class TrainingBlocked(BaseException):
    """Raised before any train dispatch, even if production catches Exception."""


class ExternalProcessBlocked(BaseException):
    """No subprocess is permitted in this test harness."""


class Fixture:
    def __init__(self, root: Path, source_bytes: bytes):
        self.root = root
        self.source = root / "source"
        self.inputs = root / "inputs"
        self.out = root / "results"
        self.logs = self.out / "logs"
        self.store = self.out / "checkpoints"
        for directory in (self.source, self.inputs, self.logs, self.store):
            directory.mkdir(parents=True)
        self.script = self.source / "run_experiment.py"
        self.script.write_bytes(source_bytes)
        self.source_sha = digest(self.script)
        (self.source / "PROTOCOL.md").write_text("SYNTHETIC GATE TEST ONLY\n")
        self.development = self.source / "development.txt"
        self.development.write_text("Synthetic development fixture.\n")
        self.holdout = self.inputs / "holdout.txt"
        self.holdout.write_text("Synthetic sealed fixture.\n")
        self.base = self.inputs / "lm1-baseline.lm1"
        self.base.write_bytes(b"SYNTHETIC NONEXECUTABLE BASE CHECKPOINT\n" * 4)
        self.tokenizer = self.inputs / "lm1-tokenizer.lmbp"
        self.tokenizer.write_bytes(b"SYNTHETIC NONEXECUTABLE TOKENIZER\n" * 4)
        self.jar = self.inputs / "synthetic-never-executed.jar"
        self.jar.write_bytes(b"NOT A JAR. EXECUTION IS FORBIDDEN.\n")
        self.train = self.out / "train.txt"
        self.train.write_text("Synthetic continuation corpus.\n")
        self.docs = self.inputs / "original-train-docs"
        self.docs.mkdir()
        (self.docs / "paths.tsv").write_text("synthetic.md\tsynthetic/train/source\n")
        (self.docs / "synthetic.md").write_text("Synthetic corpus source.\n")
        self.environment = {
            "jarSha256": digest(self.jar), "protocolSha256": digest(self.source / "PROTOCOL.md"),
            "orchestratorSha256": self.source_sha, "developmentSha256": digest(self.development),
            "holdoutSha256": digest(self.holdout), "baseSha256": digest(self.base),
            "tokenizerSha256": digest(self.tokenizer), "sourceHashes": {},
            "trainingHeap": "3g", "benchmarkHeap": "1g", "inferenceThreads": 1,
            "limits": {"firstTokenCallMs": 1500, "generation32Ms": 5000, "vmHwmKiB": 786432,
                       "benchmarkProcessSeconds": 120, "trainingProcessSeconds": 600},
        }
        write_json(self.out / "environment.json", self.environment)
        self.corpus_receipt()
        self.baseline = self.candidate("baseline", 132160, self.base)
        self.evaluate_receipt(self.baseline, 2.0)
        self.capacity_receipt(self.baseline)
        calibrations = [self.baseline]
        for name in ("l0-n1-0.75", "l0-n1-1.25", "l1-n1-0.75", "l1-n1-1.25", "nf-0.75", "nf-1.25"):
            c = self.candidate("calibration-" + name, 132160)
            tensor, factor = name.rsplit("-", 1)
            self.create_receipt(c, self.baseline, "rescale", [tensor.replace("-", "."), factor])
            self.evaluate_receipt(c, 3.0)
            calibrations.append(c)
        self.calibration = {"candidates": calibrations, "selected": deepcopy(self.baseline),
                            "rule": "minimum development BPB; identity wins exact tie"}
        write_json(self.out / "calibration.json", self.calibration)
        self.stages = [self.baseline]
        for label, count in (("width-2", 460928), ("width-4", 1708288), ("width-8", 6562304),
                             ("depth-3", 9712128), ("depth-4", 12861952), ("depth-5", 16011776)):
            c = self.candidate(label, count)
            self.stage_create_receipt(c, self.baseline)
            self.capacity_receipt(c)
            self.parity_receipt(c)
            self.stages.append(c)
        self.ladder = {"stages": deepcopy(self.stages) + [{"label": "depth-6", "parameters": 19161600,
                        "parameterBytes": 76646400, "status": "PREFLIGHT_REJECTED",
                        "reason": "CHECKPOINT_64_MIB_CONTRACT"}],
                       "lastPassed": deepcopy(self.stages[-1]), "stop": "CHECKPOINT_64_MIB_CONTRACT"}
        self.save_ladder()

    def candidate(self, label: str, count: int, path: Path | None = None) -> dict:
        if path is None:
            content = ("SYNTHETIC NONEXECUTABLE CHECKPOINT " + label + "\n").encode() * 4
            path = self.store / (hashlib.sha256(content).hexdigest() + ".lm1")
            path.write_bytes(content)
        return {"label": label, "path": str(path), "sha256": digest(path),
                "parameters": count, "bytes": path.stat().st_size}

    def receipt(self, label: str, args: list[str], text: str, *, benchmark: bool = False,
                returncode: int = 0, input_hashes: dict | None = None) -> None:
        log = self.logs / (label + ".tsv")
        log.write_text(text)
        if input_hashes is None:
            index = 0 if benchmark else 1
            input_hashes = {"checkpointSha256": digest(Path(args[index])),
                            "tokenizerSha256": digest(Path(args[index + 1]))}
            if not benchmark and args[0] in ("evaluate", "train"):
                input_hashes["datasetSha256"] = digest(Path(args[3]))
            if not benchmark and args[0] == "parity":
                input_hashes["comparisonCheckpointSha256"] = digest(Path(args[3]))
        command = ["java", "-Xms64m", "-Xmx1g" if benchmark else "-Xmx3g", "-cp", str(self.jar),
                   "at.heinz.deviceai.learning.lm." + ("LmCapacityBenchmark" if benchmark else "LmWeightExperiment"), *args]
        write_json(self.logs / (label + ".receipt.json"), {
            "arguments": args, "command": command, "returncode": returncode, "timeout": False,
            "timeoutSeconds": 120 if benchmark else 180, "wallSeconds": 0.1,
            "logSha256": digest(log), "inputHashes": input_hashes,
            "orchestratorSha256": self.source_sha, "identityValidationFailure": None,
        })

    def source_record(self, c: dict) -> str:
        return tsv("SOURCE", sha256=c["sha256"], tokenizerSha256=digest(self.tokenizer), parameters=c["parameters"])

    def create_receipt(self, c: dict, parent: dict, mode: str, settings: list[str]) -> None:
        arguments = [mode, parent["path"], str(self.tokenizer), *settings, str(self.store)]
        self.receipt(c["label"] + "-create", arguments,
                     self.source_record(parent) + tsv("CHECKPOINT", sha256=c["sha256"], path=c["path"],
                                                      parameters=c["parameters"], bytes=c["bytes"]))

    def stage_create_receipt(self, c: dict, selected: dict) -> None:
        if c["label"].startswith("width-"):
            factor = c["label"].removeprefix("width-")
            self.create_receipt(c, selected, "widen", [factor, "0.001", "20261104"])
        else:
            width8 = next(stage for stage in self.stages if stage["label"] == "width-8")
            additional = str(int(c["label"].removeprefix("depth-")) - 2)
            self.create_receipt(c, width8, "deepen", [additional, "20261104"])

    def evaluate_receipt(self, c: dict, bpb: float) -> None:
        c["devBpb"] = bpb
        self.receipt(c["label"] + "-dev", ["evaluate", c["path"], str(self.tokenizer), str(self.development)],
                     self.source_record(c) + tsv("EVALUATION", parameters=c["parameters"], segments=1,
                         bits=bpb * 10, bytes=10, tokens=5, bpb=bpb,
                         datasetSha256=digest(self.development), tokenizerSha256=digest(self.tokenizer), millis=1))

    def capacity_receipt(self, c: dict, *, passed: bool = True) -> None:
        label = c["label"] + "-capacity"
        dim = int(c["label"].split("-")[1]) * 64 if c["label"].startswith("width-") else 512 if c["label"].startswith("depth-") else 64
        layers = int(c["label"].split("-")[1]) if c["label"].startswith("depth-") else 2
        result = {"pass": "true" if passed else "false", "reasons": "NONE" if passed else "INCOMPLETE_32_TOKEN_WORKLOAD",
                  "checkpointSha256": c["sha256"], "parameters": str(c["parameters"]), "loadMs": "5.0",
                  "measuredRepeats": "3", "maxFirstTokenCallMs": "10.0", "maxGeneration32CallMs": "20.0",
                  "firstTokenObservations": "3" if passed else "0", "full32TokenObservations": "3" if passed else "0",
                  "vmHwmKiB": "131072", "failure": "UNAVAILABLE"}
        text = tsv("CAPACITY_PROTOCOL", firstTokenLimitMs=1500.0, generation32LimitMs=5000.0,
                   vmHwmLimitKiB=786432, requestMaxMillis=30000, warmups=1, measuredRepeats=3,
                   ttftMethod="SEPARATE_ONE_TOKEN_CALL_UPPER_BOUND", processTimeoutRequired=True,
                   promptSha256="9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495")
        def memory(phase):
            return tsv("CAPACITY_MEMORY", phase=phase, vmHwmKiB=131072, vmRssKiB=131072,
                       heapUsedBytes=100663296, heapCommittedBytes=134217728, heapMaxBytes=1073741824)
        text += memory("before_load")
        text += tsv("CAPACITY_MODEL", checkpointPath=c["path"], checkpointSha256=c["sha256"],
                    checkpointBytes=c["bytes"], tokenizerPath=self.tokenizer, tokenizerSha256=digest(self.tokenizer),
                    tokenizerBytes=self.tokenizer.stat().st_size, parameters=c["parameters"], parameterBytes=4*c["parameters"],
                    arch="MINGRU", vocab=512, dim=dim, layers=layers, heads=4, context=256, mlp=4*dim,
                    loadMs=5.0, fullLayoutLoaded=True, finiteWeights=True, promptTokens=64, promptIncludesBos=False)
        text += memory("after_load")
        for phase, repeats in (("warmup", 1), ("measured", 3)):
            for index in range(1, repeats + 1):
                for requested in (1, 32):
                    text += tsv("CAPACITY_TRIAL", phase=phase, trial=index, requestedNewTokens=requested,
                                actualNewTokens=requested if passed else 0, promptTokens=64, promptTruncated=False,
                                stop="MAX_TOKENS" if passed else "EOS", elapsedMs=10.0 if requested == 1 else 20.0,
                                runtimeMillis=10 if requested == 1 else 20,
                                ttftUpperBoundMs=10.0 if requested == 1 and passed else "UNAVAILABLE",
                                textSha256=hashlib.sha256(b"synthetic").hexdigest())
                    text += memory(f"{phase}_{index}_{'first' if requested == 1 else 'generation'}")
        text += memory("final")
        text += tsv("CAPACITY_RESULT", **result)
        self.receipt(label, [c["path"], str(self.tokenizer)], text, benchmark=True, returncode=0 if passed else 1)
        c["capacity"] = {"kind": "CAPACITY_RESULT", **result, "processReturncode": 0 if passed else 1,
                         "processTimedOut": False}
        c["capacityEligible"] = passed

    def parity_receipt(self, c: dict, original: dict | None = None) -> None:
        original = self.baseline if original is None else original
        parity = {"maxLogitDelta": "0.0", "maxProbabilityDelta": "0.0", "maxStepwiseLogitDelta": "0.0",
                  "identicalGenerations": "3", "originalParameters": "132160", "expandedParameters": str(c["parameters"])}
        self.receipt(c["label"] + "-parity", ["parity", original["path"], str(self.tokenizer), c["path"]],
                     self.source_record(original) + tsv("PARITY", **parity))
        c["parity"] = [{"kind": "PARITY", **parity}]

    def corpus_receipt(self) -> None:
        d = hashlib.sha256()
        for doc in sorted(self.docs.glob("*.md")):
            d.update(doc.name.encode() + b"\0" + digest(doc).encode() + b"\0")
        self.receipt("prepare-corpus", ["corpus", str(self.docs), str(self.train)],
                     tsv("CORPUS", documents=1, segments=1, bytes=self.train.stat().st_size, sha256=digest(self.train)),
                     input_hashes={"corpusManifestSha256": digest(self.docs / "paths.tsv"),
                                   "corpusDocumentsSha256": d.hexdigest()})

    def save_ladder(self) -> None:
        write_json(self.out / "ladder.json", self.ladder)

    def edit_receipt(self, label: str, change) -> None:
        path = self.logs / (label + ".receipt.json")
        value = json.loads(path.read_text()); change(value); write_json(path, value)

    def edit_log(self, label: str, change, *, rehash: bool = True) -> None:
        path = self.logs / (label + ".tsv")
        path.write_text(change(path.read_text()))
        if rehash:
            self.edit_receipt(label, lambda r: r.update(logSha256=digest(path)))


def mutate(f: Fixture, name: str) -> None:
    if name == "valid_gate_positive_control":
        return
    if name == "valid_earlier_stage_after_later_failure":
        failed = deepcopy(f.stages[2]); f.capacity_receipt(failed, passed=False)
        f.ladder = {"stages": deepcopy(f.stages[:2]) + [failed],
                    "lastPassed": deepcopy(f.stages[1]), "stop": "PARITY_OR_CAPACITY_FAILURE"}
    elif name == "valid_fully_legacy_capacity_positive_control":
        f.edit_receipt("baseline-capacity", lambda r: (r.pop("inputHashes"), r.pop("orchestratorSha256")))
    elif name == "original_fails_even_selected_calibration_passes":
        f.capacity_receipt(f.baseline, passed=False)
        selected = f.calibration["candidates"][1]
        f.evaluate_receipt(selected, 1.0); f.capacity_receipt(selected)
        f.calibration["selected"] = deepcopy(selected)
        write_json(f.out / "calibration.json", f.calibration)
        for c in f.stages[1:]:
            f.parity_receipt(c, selected)
            f.stage_create_receipt(c, selected)
        f.ladder["stages"][0] = deepcopy(selected)
    elif name == "original_reordered_fails_even_selected_passes":
        mutate(f, "original_fails_even_selected_calibration_passes")
        f.calibration["candidates"] = f.calibration["candidates"][1:] + [f.calibration["candidates"][0]]
        write_json(f.out / "calibration.json", f.calibration)
    elif name == "original_missing_from_calibration":
        selected = f.calibration["candidates"][1]
        f.evaluate_receipt(selected, 1.0); f.capacity_receipt(selected)
        f.calibration["candidates"] = f.calibration["candidates"][1:]
        f.calibration["selected"] = deepcopy(selected)
        write_json(f.out / "calibration.json", f.calibration)
        for c in f.stages[1:]:
            f.parity_receipt(c, selected)
            f.stage_create_receipt(c, selected)
        f.ladder["stages"][0] = deepcopy(selected)
    elif name == "original_duplicated_in_calibration":
        f.calibration["candidates"].append(deepcopy(f.baseline))
        write_json(f.out / "calibration.json", f.calibration)
    elif name == "baseline_failed_lastpassed_null":
        f.capacity_receipt(f.baseline, passed=False)
        f.calibration["selected"] = deepcopy(f.baseline)
        write_json(f.out / "calibration.json", f.calibration)
        f.ladder = {"stages": [deepcopy(f.baseline)], "lastPassed": None, "stop": "BASELINE_GATE"}
    elif name == "baseline_failed_receipt_but_eligible_flags_true":
        f.capacity_receipt(deepcopy(f.baseline), passed=False)
    elif name == "lastpassed_null_but_baseline_passes":
        f.ladder["lastPassed"] = None; f.ladder["stop"] = "BASELINE_GATE"
    elif name == "baseline_gate_stop_but_stale_lastpassed":
        f.ladder["stop"] = "BASELINE_GATE"
    elif name == "missing_baseline_capacity_receipt":
        (f.logs / "baseline-capacity.receipt.json").unlink()
    elif name == "missing_baseline_capacity_log":
        (f.logs / "baseline-capacity.tsv").unlink()
    elif name == "tampered_baseline_capacity_log":
        f.edit_log("baseline-capacity", lambda text: text + "TAMPERED\n", rehash=False)
    elif name == "missing_baseline_capacity_result":
        f.edit_log("baseline-capacity", lambda text: "\n".join(x for x in text.splitlines() if not x.startswith("CAPACITY_RESULT")) + "\n")
    elif name == "baseline_capacity_process_failed":
        f.edit_receipt("baseline-capacity", lambda r: r.update(returncode=1))
    elif name == "baseline_capacity_process_timed_out":
        f.edit_receipt("baseline-capacity", lambda r: r.update(timeout=True))
    elif name == "capacity_wrong_checkpoint_in_log":
        f.edit_log("baseline-capacity", lambda text: text.replace(f.baseline["sha256"], "0" * 64))
    elif name == "capacity_stale_checkpoint_input_hash":
        f.edit_receipt("baseline-capacity", lambda r: r["inputHashes"].update(checkpointSha256="0" * 64))
    elif name == "capacity_wrong_compiled_program":
        f.edit_receipt("baseline-capacity", lambda r: r["command"].__setitem__(4, str(f.inputs / "unrelated.jar")))
    elif name == "capacity_wrong_orchestrator_source":
        f.edit_receipt("baseline-capacity", lambda r: r.update(orchestratorSha256="0" * 64))
    elif name == "capacity_missing_input_binding":
        f.edit_receipt("baseline-capacity", lambda r: r.pop("inputHashes"))
    elif name == "capacity_first_token_over_limit_but_pass_claimed":
        f.edit_log("baseline-capacity", lambda text: text.replace("elapsedMs=10.0", "elapsedMs=1500.001").replace("maxFirstTokenCallMs=10.0", "maxFirstTokenCallMs=1500.001"))
    elif name == "capacity_generation_over_limit_but_pass_claimed":
        f.edit_log("baseline-capacity", lambda text: text.replace("elapsedMs=20.0", "elapsedMs=5000.001").replace("maxGeneration32CallMs=20.0", "maxGeneration32CallMs=5000.001"))
    elif name == "capacity_intermediate_memory_over_limit_but_terminal_pass":
        f.edit_log("baseline-capacity", lambda text: text.replace("phase=after_load\tvmHwmKiB=131072", "phase=after_load\tvmHwmKiB=786433"))
    elif name == "capacity_nonfinite_latency_but_pass_claimed":
        f.edit_log("baseline-capacity", lambda text: text.replace("elapsedMs=10.0", "elapsedMs=NaN").replace("maxFirstTokenCallMs=10.0", "maxFirstTokenCallMs=NaN"))
    elif name == "lastpassed_not_in_stages":
        f.ladder["lastPassed"] = deepcopy(f.calibration["candidates"][1])
        f.ladder["lastPassed"]["capacityEligible"] = True
    elif name == "missing_lastpassed_capacity_receipt":
        (f.logs / "depth-5-capacity.receipt.json").unlink()
    elif name == "tampered_lastpassed_checkpoint":
        path = Path(f.ladder["lastPassed"]["path"]); path.write_bytes(path.read_bytes() + b"TAMPERED")
    elif name == "stale_lastpassed_parity_binding":
        f.edit_receipt("depth-5-parity", lambda r: r["inputHashes"].update(comparisonCheckpointSha256="0" * 64))
    elif name == "frozen_resume_malformed_json":
        (f.out / "frozen-selection.json").write_text("{not valid json")
    elif name == "frozen_resume_stale_incomplete_record":
        write_json(f.out / "frozen-selection.json", {"orchestratorSha256": "0" * 64,
                   "candidates": [deepcopy(f.baseline)], "holdoutOpened": False})
    elif name == "missing_ladder_negative_control":
        (f.out / "ladder.json").unlink(); return
    elif name == "malformed_ladder_negative_control":
        (f.out / "ladder.json").write_text("{invalid json"); return
    elif name == "missing_development_receipt_negative_control":
        (f.logs / "baseline-dev.receipt.json").unlink()
    elif name == "changed_jar_negative_control":
        f.jar.write_bytes(b"CHANGED SYNTHETIC NONEXECUTABLE JAR")
    else:
        raise ValueError(name)
    f.save_ladder()


CASES = [
    "valid_gate_positive_control", "valid_earlier_stage_after_later_failure", "valid_fully_legacy_capacity_positive_control",
    "original_fails_even_selected_calibration_passes", "original_reordered_fails_even_selected_passes",
    "original_missing_from_calibration", "original_duplicated_in_calibration", "baseline_failed_lastpassed_null",
    "baseline_failed_receipt_but_eligible_flags_true", "lastpassed_null_but_baseline_passes",
    "baseline_gate_stop_but_stale_lastpassed", "missing_baseline_capacity_receipt",
    "missing_baseline_capacity_log", "tampered_baseline_capacity_log", "missing_baseline_capacity_result",
    "baseline_capacity_process_failed", "baseline_capacity_process_timed_out", "capacity_wrong_checkpoint_in_log",
    "capacity_stale_checkpoint_input_hash", "capacity_wrong_compiled_program", "capacity_wrong_orchestrator_source",
    "capacity_missing_input_binding", "capacity_first_token_over_limit_but_pass_claimed",
    "capacity_generation_over_limit_but_pass_claimed", "capacity_intermediate_memory_over_limit_but_terminal_pass",
    "capacity_nonfinite_latency_but_pass_claimed", "lastpassed_not_in_stages", "missing_lastpassed_capacity_receipt",
    "tampered_lastpassed_checkpoint", "stale_lastpassed_parity_binding", "frozen_resume_malformed_json",
    "frozen_resume_stale_incomplete_record", "missing_ladder_negative_control", "malformed_ladder_negative_control",
    "missing_development_receipt_negative_control", "changed_jar_negative_control",
]


def execute_case(f: Fixture, name: str) -> dict:
    module = types.ModuleType("synthetic_gate_review_" + name)
    module.__file__ = str(f.script)
    exec(compile(f.script.read_bytes(), str(f.script), "exec"), module.__dict__)
    # Replace data identities only, not any decision, gate or validation function.
    module.BASE_SHA = digest(f.base)
    module.TOKENIZER_SHA = digest(f.tokenizer)
    module.HOLDOUT_SHA = digest(f.holdout)
    events = []
    original_invoke = module.Experiment.invoke
    original_call = module.Experiment.call

    def invoke(self, label, mode, checkpoint, *extra, **options):
        if mode == "train":
            events.append({"event": "TRAIN_ATTEMPT_BLOCKED", "boundary": "invoke", "label": label,
                           "checkpoint": str(checkpoint)})
            raise TrainingBlocked("training trapped before dispatch")
        return original_invoke(self, label, mode, checkpoint, *extra, **options)

    def call(self, label, arguments, **options):
        if not options.get("benchmark", False) and arguments and arguments[0] == "train":
            events.append({"event": "TRAIN_ATTEMPT_BLOCKED", "boundary": "call", "label": label})
            raise TrainingBlocked("direct training call trapped")
        return original_call(self, label, arguments, **options)

    def process(command, *args, **kwargs):
        is_train = isinstance(command, (list, tuple)) and "train" in command
        events.append({"event": "TRAIN_ATTEMPT_BLOCKED" if is_train else "OTHER_PROCESS_BLOCKED",
                       "boundary": "process", "command": [str(x) for x in command] if not isinstance(command, str) else command})
        if is_train:
            raise TrainingBlocked("training process trapped")
        raise ExternalProcessBlocked("all external processes are disabled")

    exception = None; returned = False
    try:
        with mock.patch.object(module.Experiment, "invoke", invoke), mock.patch.object(module.Experiment, "call", call), \
                mock.patch.object(module.subprocess, "Popen", process), mock.patch.object(module.subprocess, "run", process):
            e = module.Experiment(argparse.Namespace(inputs=f.inputs, output=f.out, jar=f.jar, holdout=f.holdout))
            e.continuation()
            returned = True
    except BaseException as error:
        exception = {"type": type(error).__name__, "message": str(error)}
    attempts = sum(event["event"] == "TRAIN_ATTEMPT_BLOCKED" for event in events)
    positive = name.startswith("valid_")
    needs_rejection = name.startswith("frozen_resume_")
    passed = attempts == 1 if positive else attempts == 0 and (not needs_rejection or not returned)
    return {"case": name, "pass": passed, "positiveControl": positive,
            "trainAttempts": attempts, "returnedWithoutValidationError": returned,
            "exception": exception, "events": events, "fixture": str(f.root)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, nargs="?",
                        default=Path(__file__).resolve().parent.parent / "run_experiment.py",
                        help="Driver source to audit; defaults to the adjacent project driver")
    parser.add_argument("--workspace", type=Path,
                        help="Optional output directory; defaults to a new temporary directory")
    args = parser.parse_args()
    source_bytes = args.source.read_bytes()
    source_sha = hashlib.sha256(source_bytes).hexdigest()
    if args.workspace is None:
        args.workspace = Path(tempfile.mkdtemp(prefix="continuum-g1-gate-review-"))
    args.workspace.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="audit-" + source_sha[:12] + "-", dir=args.workspace))
    results = []
    for name in CASES:
        f = Fixture(run / name, source_bytes)
        mutate(f, name)
        result = execute_case(f, name)
        results.append(result)
        print(json.dumps({key: result[key] for key in ("case", "pass", "trainAttempts", "exception")}), flush=True)
    report = {"task": "G1-INTEGRATE-WEIGHT-002", "sourcePath": str(args.source.resolve()),
              "sourceSha256": source_sha, "sourceHashAfterRun": digest(args.source),
              "harnessSha256": digest(Path(__file__)), "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
              "scope": "Synthetic orchestration and real receipt-validation logic; zero model execution",
              "realProcessLaunches": 0, "realTrainingRuns": 0,
              "dataIdentityConstantsReboundToSyntheticInputs": ["BASE_SHA", "TOKENIZER_SHA", "HOLDOUT_SHA"],
              "decisionHelpersMocked": [], "cases": results,
              "passed": sum(x["pass"] for x in results), "failed": sum(not x["pass"] for x in results)}
    path = run / "report.json"
    write_json(path, report)
    print(json.dumps({"report": str(path), "sha256": digest(path), "sourceSha256": source_sha,
                      "passed": report["passed"], "failed": report["failed"], "realTrainingRuns": 0}), flush=True)
    raise SystemExit(0 if report["failed"] == 0 else 1)


if __name__ == "__main__":
    main()
