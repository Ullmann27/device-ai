#!/usr/bin/env python3
"""Bounded, resumable orchestration of the preregistered offline LM1 experiment."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time

BASE_SHA = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
HOLDOUT_SHA = "d5a5858f0eaff24495cd98c337fc662271c4a1b41027b1142764735c244942af"
SEED = "20261104"
MAX_CHECKPOINT = 64 * 1024 * 1024
PACKAGE = "at.heinz.deviceai.learning.lm."


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_new(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")


def records(path: Path) -> list[dict]:
    result = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        fields = line.split("\t")
        if len(fields) > 1 and all("=" in item for item in fields[1:]):
            result.append({"kind": fields[0], **dict(item.split("=", 1) for item in fields[1:])})
    return result


class Experiment:
    def __init__(self, args: argparse.Namespace):
        self.inputs = args.inputs.resolve()
        self.out = args.output.resolve()
        self.jar = args.jar.resolve()
        self.holdout = args.holdout.resolve()
        self.source = Path(__file__).resolve().parent
        self.tokenizer = self.inputs / "lm1-tokenizer.lmbp"
        self.base = self.inputs / "lm1-baseline.lm1"
        self.development = self.source / "development.txt"
        self.store = self.out / "checkpoints"
        self.logs = self.out / "logs"
        self.logs.mkdir(parents=True, exist_ok=True)
        self.store.mkdir(parents=True, exist_ok=True)
        if sha(self.base) != BASE_SHA or sha(self.tokenizer) != TOKENIZER_SHA:
            raise ValueError("input checkpoint/tokenizer hash mismatch")
        # Hashing bytes does not expose the sealed holdout text to selection.
        if sha(self.holdout) != HOLDOUT_SHA:
            raise ValueError("sealed holdout hash mismatch")
        if not (self.out / "environment.json").exists():
            write_new(self.out / "environment.json", {
                "createdUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "platform": list(os.uname()), "cpus": os.cpu_count(),
                "javaVersion": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
                "jarSha256": sha(self.jar), "protocolSha256": sha(self.source / "PROTOCOL.md"),
                "developmentSha256": sha(self.development), "holdoutSha256": HOLDOUT_SHA,
                "baseSha256": BASE_SHA, "tokenizerSha256": TOKENIZER_SHA,
                "sourceHashes": {p.name: sha(p) for p in sorted(self.source.glob("*.kt"))},
                "trainingHeap": "3g", "benchmarkHeap": "1g", "inferenceThreads": 1,
                "limits": {"firstTokenCallMs": 1500, "generation32Ms": 5000, "vmHwmKiB": 768 * 1024,
                           "benchmarkProcessSeconds": 120, "trainingProcessSeconds": 600},
            })
        else:
            previous = json.loads((self.out / "environment.json").read_text())
            if previous["jarSha256"] != sha(self.jar) or previous["protocolSha256"] != sha(self.source / "PROTOCOL.md"):
                raise ValueError("compiled program or frozen protocol changed; use a new experiment directory")

    def call(self, label: str, arguments: list[str], *, benchmark: bool = False,
             timeout: int = 180, allow_failure: bool = False) -> list[dict]:
        path = self.logs / f"{label}.tsv"
        receipt = self.logs / f"{label}.receipt.json"
        if path.exists() or receipt.exists():
            if not receipt.exists():
                raise RuntimeError(f"unfinished invocation requires explicit review: {label}")
            saved = json.loads(receipt.read_text())
            if saved["logSha256"] != sha(path) or saved["arguments"] != arguments:
                raise RuntimeError(f"invocation changed: {label}")
            if saved["returncode"] != 0 and not allow_failure:
                raise RuntimeError(f"previous invocation failed: {label}")
            return records(path)
        main = PACKAGE + ("LmCapacityBenchmark" if benchmark else "LmWeightExperiment")
        command = ["java", "-Xms64m", "-Xmx1g" if benchmark else "-Xmx3g", "-cp", str(self.jar), main, *arguments]
        print(f"START {label}", flush=True)
        start = time.monotonic()
        timed_out = False
        with path.open("xb") as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                returncode = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGKILL)
                returncode = process.wait()
        elapsed = time.monotonic() - start
        write_new(receipt, {"arguments": arguments, "command": command, "returncode": returncode,
                           "timeout": timed_out, "timeoutSeconds": timeout, "wallSeconds": elapsed,
                           "logSha256": sha(path)})
        result = records(path)
        highlights = [r for r in result if r["kind"] in {"EVALUATION", "CAPACITY_RESULT", "PARITY", "TRAIN_RESULT", "PROBE_RESULT", "CHECKPOINT", "CORPUS"}]
        print(json.dumps({"finished": label, "returncode": returncode, "seconds": elapsed,
                          "highlights": highlights}, ensure_ascii=False), flush=True)
        if returncode and not allow_failure:
            raise RuntimeError(f"{label} failed; inspect {path}")
        return result

    def invoke(self, label: str, mode: str, checkpoint: Path, *extra: object, **options) -> list[dict]:
        return self.call(label, [mode, str(checkpoint), str(self.tokenizer), *(str(x) for x in extra)], **options)

    @staticmethod
    def one(rows: list[dict], kind: str) -> dict:
        matching = [r for r in rows if r["kind"] == kind]
        if len(matching) != 1:
            raise RuntimeError(f"expected one {kind}, found {len(matching)}")
        return matching[0]

    def candidate(self, rows: list[dict], label: str) -> dict:
        row = self.one(rows, "CHECKPOINT")
        if sha(Path(row["path"])) != row["sha256"]:
            raise RuntimeError("generated checkpoint hash mismatch")
        return {"label": label, "path": row["path"], "sha256": row["sha256"],
                "parameters": int(row["parameters"]), "bytes": int(row["bytes"])}

    def score(self, candidate: dict, suffix: str = "dev") -> dict:
        dataset = self.development if suffix == "dev" else self.holdout
        score = self.one(self.invoke(f"{candidate['label']}-{suffix}", "evaluate", Path(candidate["path"]), dataset), "EVALUATION")
        candidate[f"{suffix}Bpb"] = float(score["bpb"])
        return score

    def benchmark(self, candidate: dict) -> bool:
        result = self.call(f"{candidate['label']}-capacity", [candidate["path"], str(self.tokenizer)],
                           benchmark=True, timeout=120, allow_failure=True)
        rows = [r for r in result if r["kind"] == "CAPACITY_RESULT"]
        receipt = json.loads((self.logs / f"{candidate['label']}-capacity.receipt.json").read_text())
        candidate["capacity"] = rows[0] if len(rows) == 1 else {"pass": "false", "reasons": "MISSING_FINAL_RESULT"}
        candidate["capacity"]["processReturncode"] = receipt["returncode"]
        candidate["capacity"]["processTimedOut"] = receipt["timeout"]
        return (receipt["returncode"] == 0 and candidate["capacity"]["pass"] == "true"
                and candidate["capacity"].get("full32TokenObservations") == "3")

    def calibration(self) -> None:
        if (self.out / "calibration.json").exists():
            return
        train = self.out / "train.txt"
        self.call("prepare-corpus", ["corpus", str(self.inputs / "original-train-docs"), str(train)])
        self.invoke("baseline-inspect", "inspect", self.base)
        baseline = {"label": "baseline", "path": str(self.base), "sha256": BASE_SHA,
                    "parameters": 132160, "bytes": self.base.stat().st_size}
        self.score(baseline)
        self.invoke("baseline-probes", "probes", self.base)
        self.benchmark(baseline)
        candidates = [baseline]
        for tensor in ["l0.n1", "l1.n1", "nf"]:
            for factor in ["0.75", "1.25"]:
                label = "calibration-" + tensor.replace(".", "-") + "-" + factor
                candidate = self.candidate(self.invoke(label + "-create", "rescale", self.base, tensor, factor, self.store), label)
                self.score(candidate)
                candidates.append(candidate)
        selected = min(candidates, key=lambda c: (c["devBpb"], c["label"] != "baseline"))
        write_new(self.out / "calibration.json", {"candidates": candidates, "selected": selected,
                                                 "rule": "minimum development BPB; identity wins exact tie"})

    def ladder(self) -> None:
        if (self.out / "ladder.json").exists():
            return
        selection = json.loads((self.out / "calibration.json").read_text())["selected"]
        source = Path(selection["path"])
        selected = dict(selection)
        passed = []
        attempted = []
        if not self.benchmark(selected):
            write_new(self.out / "ladder.json", {"stages": [selected], "lastPassed": None, "stop": "BASELINE_GATE"})
            return
        passed.append(selected)
        attempted.append(selected)
        stop = None
        for factor in [2, 4, 8]:
            label = f"width-{factor}"
            candidate = self.candidate(self.invoke(label + "-create", "widen", source, factor, "0.001", SEED, self.store), label)
            parity = self.invoke(label + "-parity", "parity", source, candidate["path"], allow_failure=True)
            parity_receipt = json.loads((self.logs / f"{label}-parity.receipt.json").read_text())
            candidate["parity"] = [r for r in parity if r["kind"] == "PARITY"]
            attempted.append(candidate)
            if parity_receipt["returncode"] != 0 or not self.benchmark(candidate):
                stop = "PARITY_OR_CAPACITY_FAILURE"
                break
            passed.append(candidate)
        if stop is None:
            width8 = Path(passed[-1]["path"])
            for layers in [3, 4, 5, 6]:
                parameters = 512 * 512 + 512 + layers * 3_149_824
                if parameters * 4 > MAX_CHECKPOINT:
                    stop = "CHECKPOINT_64_MIB_CONTRACT"
                    attempted.append({"label": f"depth-{layers}", "parameters": parameters,
                                      "parameterBytes": parameters * 4, "status": "PREFLIGHT_REJECTED",
                                      "reason": stop})
                    break
                label = f"depth-{layers}"
                candidate = self.candidate(self.invoke(label + "-create", "deepen", width8, layers - 2, SEED, self.store), label)
                parity = self.invoke(label + "-parity", "parity", source, candidate["path"], allow_failure=True)
                parity_receipt = json.loads((self.logs / f"{label}-parity.receipt.json").read_text())
                candidate["parity"] = [r for r in parity if r["kind"] == "PARITY"]
                attempted.append(candidate)
                if parity_receipt["returncode"] != 0 or not self.benchmark(candidate):
                    stop = "PARITY_OR_CAPACITY_FAILURE"
                    break
                passed.append(candidate)
        write_new(self.out / "ladder.json", {"stages": attempted, "lastPassed": passed[-1], "stop": stop})

    def continuation(self) -> None:
        if (self.out / "frozen-selection.json").exists():
            return
        calibration = json.loads((self.out / "calibration.json").read_text())
        ladder = json.loads((self.out / "ladder.json").read_text())
        original = calibration["candidates"][0]
        selected = calibration["selected"]
        expanded = ladder["lastPassed"]
        candidates = {c["sha256"]: dict(c) for c in [original, selected]}
        failures = []
        if expanded and expanded["parameters"] > selected["parameters"]:
            candidates[expanded["sha256"]] = dict(expanded)
        for label, initial in [("continued-small", selected), ("continued-expanded", expanded)]:
            if initial is None or (label == "continued-expanded" and initial["parameters"] == selected["parameters"]):
                continue
            rows = self.invoke(label + "-train", "train", Path(initial["path"]), self.out / "train.txt", self.store,
                               timeout=600, allow_failure=True)
            receipt = json.loads((self.logs / f"{label}-train.receipt.json").read_text())
            if receipt["returncode"] != 0:
                failures.append({"label": label, "receipt": receipt})
                continue
            candidate = self.candidate(rows, label)
            candidate["training"] = self.one(rows, "TRAIN_RESULT")
            candidate["trainingInput"] = self.one(rows, "TRAIN_INPUT")
            self.score(candidate)
            self.benchmark(candidate)
            candidates[candidate["sha256"]] = candidate
        for candidate in candidates.values():
            if "devBpb" not in candidate:
                self.score(candidate)
            self.invoke(candidate["label"] + "-probes", "probes", Path(candidate["path"]))
        eligible = [c for c in candidates.values() if c.get("capacity", {}).get("pass", "true") == "true"]
        minimum_bpb = min(c["devBpb"] for c in eligible)
        equivalent = [c for c in eligible if c["devBpb"] <= minimum_bpb + 0.00001]
        preferred = min(equivalent, key=lambda c: (c["parameters"], c["devBpb"]))
        write_new(self.out / "frozen-selection.json", {
            "frozenUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "holdoutSha256": HOLDOUT_SHA, "holdoutOpened": False,
            "candidates": list(candidates.values()), "developmentPreferred": preferred,
            "lastCapacityPassed": expanded, "trainingFailures": failures,
            "selectionRule": "minimum development BPB among completed eligible candidates; within 0.00001 BPB prefer fewer parameters",
        })
        print("FROZEN_SELECTION " + sha(self.out / "frozen-selection.json"), flush=True)

    def final(self) -> None:
        if (self.out / "final.json").exists():
            return
        frozen = self.out / "frozen-selection.json"
        selection = json.loads(frozen.read_text())
        if selection["holdoutSha256"] != sha(self.holdout):
            raise ValueError("holdout changed after selection")
        results = []
        for candidate in selection["candidates"]:
            copy = dict(candidate)
            copy["holdout"] = self.score(copy, "holdout")
            results.append(copy)
        if sha(self.base) != BASE_SHA or sha(self.tokenizer) != TOKENIZER_SHA:
            raise ValueError("original inputs changed")
        write_new(self.out / "final.json", {"selectionSha256": sha(frozen), "holdoutSha256": sha(self.holdout),
                                          "results": results, "originalsUnchanged": True,
                                          "androidValidation": "NOT_RUN", "appIntegration": "NOT_PERFORMED"})
        shutil.copyfile(self.holdout, self.out / "holdout-after-freeze.txt")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["calibration", "ladder", "continuation", "final", "all"])
    parser.add_argument("--inputs", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--holdout", type=Path, required=True)
    args = parser.parse_args()
    experiment = Experiment(args)
    for phase in (["calibration", "ladder", "continuation", "final"] if args.phase == "all" else [args.phase]):
        getattr(experiment, phase)()


if __name__ == "__main__":
    main()
