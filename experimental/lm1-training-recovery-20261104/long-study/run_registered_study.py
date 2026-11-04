#!/usr/bin/env python3
"""Run only the prospectively registered LM1 comparisons, with bounded processes."""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import math
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / "experimental/lm1-training-recovery-20261104/intent-data"
EXPERIMENT = ROOT / "experimental/lm1-training-recovery-20261104/long-study"
PACKAGE = "at.heinz.deviceai.learning.lm."


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_new(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")


def rows(path: Path, kind: str) -> list[dict]:
    result = []
    for line in path.read_text().splitlines():
        fields = line.split("\t")
        if fields[0] == kind:
            result.append(dict(field.split("=", 1) for field in fields[1:]))
    return result


class Study:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.out = args.output.resolve()
        self.jar = args.jar.resolve()
        self.stdlib = args.stdlib.resolve()
        self.inputs = args.inputs.resolve()
        self.registration_path = EXPERIMENT / "registration.json"
        self.registration = json.loads(self.registration_path.read_text())
        self.registration_sha = sha(self.registration_path)
        if not re.fullmatch(r"[a-f0-9]{40}", args.source_commit):
            raise ValueError("full published source commit required")
        self.out.mkdir(parents=True, exist_ok=True)
        self.logs = self.out / "logs"
        self.logs.mkdir(exist_ok=True)
        self.verify()

    def verify(self) -> None:
        if sha(self.registration_path) != self.registration_sha:
            raise RuntimeError("REGISTRATION_CHANGED")
        for relative, expected in self.registration["files"].items():
            if sha(ROOT / relative) != expected:
                raise RuntimeError("SOURCE_OR_DATA_CHANGED: " + relative)
        for name, expected in self.registration["inputs"].items():
            if sha(self.inputs / name) != expected:
                raise RuntimeError("INPUT_CHANGED: " + name)
        if sha(self.jar) != self.registration["jarSha256"] or sha(self.stdlib) != self.registration["stdlibSha256"]:
            raise RuntimeError("EXECUTABLE_CHANGED")

    def run(self, label: str, main: str, arguments: list[str], *, capacity: bool = False) -> subprocess.CompletedProcess:
        self.verify()
        log = self.logs / (label + ".tsv")
        receipt = self.logs / (label + ".receipt.json")
        if log.exists() or receipt.exists():
            raise RuntimeError("NO_IMPLICIT_REPLAY: " + label)
        command = ["java", "-Xms64m", "-Xmx1g" if capacity else "-Xmx3g", "-cp",
                   str(self.jar) + os.pathsep + str(self.stdlib), PACKAGE + main, *map(str, arguments)]
        started = datetime.datetime.now(datetime.timezone.utc).isoformat()
        start = time.monotonic()
        timed_out = False
        print("START", label, flush=True)
        with log.open("xb") as stream:
            process = subprocess.Popen(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            try:
                code = process.wait(timeout=120 if capacity else 900)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGKILL)
                code = process.wait()
        unchanged = True
        try:
            self.verify()
        except (OSError, RuntimeError):
            unchanged = False
        write_new(receipt, {"label": label, "command": command, "startedUtc": started,
                           "wallSeconds": time.monotonic() - start, "returncode": code,
                           "timeout": timed_out, "timeoutSeconds": 120 if capacity else 900,
                           "sourceCommit": self.args.source_commit, "registrationSha256": self.registration_sha,
                           "jarSha256": sha(self.jar), "logSha256": sha(log), "registeredBytesUnchanged": unchanged})
        print("FINISHED", label, "exit", code, "timeout", timed_out, flush=True)
        if not unchanged or timed_out or (code != 0 and not capacity):
            raise RuntimeError("RUN_REJECTED: " + label)
        return subprocess.CompletedProcess(command, code)

    def benchmark(self, label: str, checkpoint: Path) -> dict:
        checkpoint_sha = sha(checkpoint)
        process = self.run(label, "LmCapacityBenchmark", [str(checkpoint), str(self.inputs / "lm1-tokenizer.lmbp")], capacity=True)
        result = rows(self.logs / (label + ".tsv"), "CAPACITY_RESULT")
        if len(result) != 1 or result[0].get("checkpointSha256") != checkpoint_sha:
            raise RuntimeError("INVALID_CAPACITY_EVIDENCE: " + label)
        result = result[0]
        if (process.returncode == 0) != (result["pass"] == "true"):
            raise RuntimeError("CAPACITY_STATUS_MISMATCH")
        return result

    def intent(self) -> None:
        for variant in ["small", "wide"]:
            directory = self.out / ("intent-" + variant)
            self.run("intent-" + variant, "LmIntentLongStudy", ["train", str(self.inputs / "lm1-baseline.lm1"),
                     str(self.inputs / "lm1-tokenizer.lmbp"), str(DATA), str(directory), variant])
            selection = self.selection(variant)
            self.benchmark("intent-" + variant + "-capacity-after", Path(selection["path"]))

    def selection(self, variant: str) -> dict:
        log = self.logs / ("intent-" + variant + ".tsv")
        receipt = json.loads((self.logs / ("intent-" + variant + ".receipt.json")).read_text())
        if (receipt["returncode"] != 0 or receipt["timeout"] or not receipt["registeredBytesUnchanged"]
                or receipt["logSha256"] != sha(log) or receipt["registrationSha256"] != self.registration_sha
                or receipt["sourceCommit"] != self.args.source_commit
                or receipt["jarSha256"] != self.registration["jarSha256"]):
            raise RuntimeError("INVALID_TRAINING_RECEIPT")
        chosen = rows(log, "SELECTION")
        if len(chosen) != 1:
            raise RuntimeError("MISSING_SELECTION")
        result = dict(chosen[0])
        if (int(result["step"]) not in [128, 256, 512] or result["testRead"] != "false"
                or result["inputsUnchanged"] != "true" or result["devTotal"] != "24"):
            raise RuntimeError("INVALID_SELECTION")
        scores = [s for s in rows(log, "SCORE") if s["split"] == "dev"]
        if (sorted(int(s["step"]) for s in scores) != [0, 128, 256, 512]
                or any(s["total"] != "24" or not math.isfinite(float(s["answerNll"]))
                       or not 0 <= int(s["correct"]) <= 24 for s in scores)):
            raise RuntimeError("INVALID_DEVELOPMENT_EVIDENCE")
        best = min((s for s in scores if s["step"] != "0"),
                   key=lambda s: (-int(s["correct"]), float(s["answerNll"]), int(s["step"])))
        if (result["step"] != best["step"] or result["devCorrect"] != best["correct"]
                or float(result["devAnswerNll"]) != float(best["answerNll"])):
            raise RuntimeError("SELECTION_RULE_MISMATCH")
        checkpoints = rows(log, "CHECKPOINT")
        if sorted(int(c["step"]) for c in checkpoints) != [0, 128, 256, 512]:
            raise RuntimeError("INCOMPLETE_CHECKPOINT_HISTORY")
        chosen_checkpoint = next(c for c in checkpoints if c["step"] == result["step"])
        if chosen_checkpoint["sha256"] != result["checkpointSha256"]:
            raise RuntimeError("SELECTION_CHECKPOINT_MISMATCH")
        gates = rows(log, "INITIAL_CAPACITY_GATE")
        initial = next(c for c in checkpoints if c["step"] == "0")
        if (len(gates) != 1 or gates[0]["pass"] != "true"
                or gates[0]["checkpointSha256"] != initial["sha256"]
                or gates[0]["logSha256"] != sha(self.out / ("intent-" + variant) / "capacity-before.tsv")):
            raise RuntimeError("INVALID_INITIAL_GATE")
        checkpoint = self.out / ("intent-" + variant) / "checkpoints" / (result["checkpointSha256"] + ".lm1")
        if sha(checkpoint) != result["checkpointSha256"]:
            raise RuntimeError("CHECKPOINT_CHANGED")
        result["path"] = str(checkpoint)
        return result

    def freeze_evidence(self) -> dict:
        self.verify()
        selected = {variant: self.selection(variant) for variant in ["small", "wide"]}
        wide_scores = rows(self.logs / "intent-wide.tsv", "SCORE")
        wide_initial = next(s for s in wide_scores if s["split"] == "dev" and s["step"] == "0")
        if int(selected["wide"]["devCorrect"]) < 12 or int(selected["wide"]["devCorrect"]) < int(wide_initial["correct"]) + 4:
            raise RuntimeError("NO_DEVELOPMENT_SIGNAL")
        arms = {}
        schedules = []
        for variant, prefix in [("small", "S"), ("wide", "W")]:
            log = self.logs / ("intent-" + variant + ".tsv")
            initial = next(c for c in rows(log, "CHECKPOINT") if c["step"] == "0")
            if sha(Path(initial["path"])) != initial["sha256"]:
                raise RuntimeError("INITIAL_CHECKPOINT_CHANGED")
            arms[prefix + "0"] = {"path": initial["path"], "checkpointSha256": initial["sha256"], "step": 0}
            arms[prefix + "1"] = selected[variant]
            manifest = rows(log, "MANIFEST")
            if len(manifest) != 1:
                raise RuntimeError("INVALID_MANIFEST")
            schedules.append(manifest[0]["tokenScheduleSha256"])
        if len(set(schedules)) != 1:
            raise RuntimeError("UNEQUAL_TRAINING_SCHEDULES")
        return {"status": "FROZEN_BEFORE_TEST", "arms": arms,
                  "sourceCommit": self.args.source_commit, "registrationSha256": self.registration_sha,
                  "tokenScheduleSha256": schedules[0], "testSha256": self.registration["files"][
                  "experimental/lm1-training-recovery-20261104/intent-data/test.sealed.tsv"],
                  "rule": "Dev exact descending, answer NLL ascending, fewer steps; development threshold passed"}

    def freeze(self) -> None:
        try:
            evidence = self.freeze_evidence()
        except RuntimeError as exc:
            if str(exc) != "NO_DEVELOPMENT_SIGNAL":
                raise
            write_new(self.out / "no-development-signal.json", {"status": "NO_DEVELOPMENT_SIGNAL", "testEvaluated": False,
                       "registrationSha256": self.registration_sha})
            print("NO_DEVELOPMENT_SIGNAL; sealed test not evaluated", flush=True)
            return
        write_new(self.out / "selection-freeze.json", evidence)
        print("FROZEN_BEFORE_TEST", sha(self.out / "selection-freeze.json"), flush=True)

    def test(self) -> None:
        self.verify()
        frozen = self.out / "selection-freeze.json"
        frozen_sha = sha(frozen)
        selection = json.loads(frozen.read_text())
        if selection != self.freeze_evidence():
            raise RuntimeError("INVALID_TEST_AUTHORIZATION")
        outputs = {}
        for arm in ["S0", "W0", "S1", "W1"]:
            value = selection["arms"][arm]
            if sha(Path(value["path"])) != value["checkpointSha256"] or sha(frozen) != frozen_sha:
                raise RuntimeError("FROZEN_CHECKPOINT_CHANGED")
            self.run("test-" + arm, "LmIntentLongStudy", ["test", value["path"], str(self.inputs / "lm1-tokenizer.lmbp"),
                     str(DATA / "test.sealed.tsv")])
            reported_input = rows(self.logs / ("test-" + arm + ".tsv"), "TEST_INPUT")
            if (len(reported_input) != 1 or reported_input[0]["checkpointSha256"] != value["checkpointSha256"]
                    or reported_input[0]["testSha256"] != selection["testSha256"]
                    or sha(Path(value["path"])) != value["checkpointSha256"]):
                raise RuntimeError("TEST_INPUT_MISMATCH")
            score = rows(self.logs / ("test-" + arm + ".tsv"), "SCORE")
            if len(score) != 1 or score[0]["total"] != "48":
                raise RuntimeError("INVALID_TEST_RESULT")
            outputs[arm] = score[0]
        if sha(frozen) != frozen_sha:
            raise RuntimeError("FREEZE_CHANGED_DURING_TEST")
        w = outputs["W1"]
        limited = int(w["correct"]) >= 36 and min(map(int, w["classCorrect"].split(","))) >= 8
        limited = limited and all(int(w["correct"]) >= int(outputs[a]["correct"]) + 8 for a in ["S0", "W0"])
        write_new(self.out / "intent-summary.json", {"selectionSha256": frozen_sha, "scores": outputs,
                  "limitedBenchmarkLearningCriterionPassed": limited,
                  "widerSelectedCheckpointHasFourCaseAdvantage": int(w["correct"]) >= int(outputs["S1"]["correct"]) + 4,
                  "generalIntelligenceClaim": False, "modelPromotion": False, "androidValidation": "NOT_RUN"})
        print(json.dumps(outputs), flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["intent", "freeze", "test"])
    for name in ["jar", "stdlib", "inputs", "output"]:
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    getattr(Study(args), args.phase)()


if __name__ == "__main__":
    main()
