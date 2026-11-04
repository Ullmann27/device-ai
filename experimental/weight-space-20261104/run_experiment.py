#!/usr/bin/env python3
"""Bounded, resumable orchestration of the preregistered offline LM1 experiment."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
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
        self.orchestrator_sha = sha(Path(__file__).resolve())
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
                "orchestratorSha256": self.orchestrator_sha,
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
        self.environment = json.loads((self.out / "environment.json").read_text())
        self.development_sha = self.environment["developmentSha256"]
        if sha(self.development) != self.development_sha:
            raise ValueError("frozen development data changed")
        if (self.logs / "prepare-corpus.receipt.json").exists():
            self.training_hash()

    def training_hash(self) -> str:
        """The original successful corpus receipt seals the continuation dataset."""
        path = self.logs / "prepare-corpus.tsv"
        receipt = json.loads((self.logs / "prepare-corpus.receipt.json").read_text())
        expected_arguments = ["corpus", str(self.inputs / "original-train-docs"), str(self.out / "train.txt")]
        if (receipt["arguments"] != expected_arguments or receipt["logSha256"] != sha(path)
                or receipt["returncode"] != 0 or receipt.get("timeout", False)
                or receipt.get("identityValidationFailure")):
            raise RuntimeError("invalid original training-corpus receipt")
        expected = self.one(records(path), "CORPUS")["sha256"]
        if sha(self.out / "train.txt") != expected:
            raise RuntimeError("continuation data no longer matches the original CORPUS record")
        return expected

    def dataset_hash(self, path: Path) -> str:
        path = path.resolve()
        if path == self.development.resolve():
            expected = self.development_sha
        elif path == (self.out / "train.txt").resolve():
            expected = self.training_hash()
        elif path == self.holdout:
            expected = HOLDOUT_SHA
        else:
            raise ValueError("unregistered experiment dataset")
        if sha(path) != expected:
            raise RuntimeError("dataset differs from its frozen identity")
        return expected

    def input_hashes(self, arguments: list[str], benchmark: bool) -> dict[str, str]:
        if not benchmark and arguments[0] == "corpus":
            root = Path(arguments[1])
            digest = hashlib.sha256()
            for document in sorted(root.glob("*.md")):
                digest.update(document.name.encode("utf-8") + b"\0")
                digest.update(sha(document).encode("ascii") + b"\0")
            return {"corpusManifestSha256": sha(root / "paths.tsv"),
                    "corpusDocumentsSha256": digest.hexdigest()}
        offset = 0 if benchmark else 1
        checkpoint = Path(arguments[offset])
        result = {"checkpointSha256": sha(checkpoint), "tokenizerSha256": sha(Path(arguments[offset + 1]))}
        if result["tokenizerSha256"] != TOKENIZER_SHA:
            raise RuntimeError("tokenizer changed")
        if checkpoint.resolve() == self.base.resolve() and result["checkpointSha256"] != BASE_SHA:
            raise RuntimeError("baseline changed")
        if not benchmark and arguments[0] in {"evaluate", "train"}:
            result["datasetSha256"] = self.dataset_hash(Path(arguments[3]))
        if not benchmark and arguments[0] == "parity":
            result["comparisonCheckpointSha256"] = sha(Path(arguments[3]))
        return result

    def verify_records(self, rows: list[dict], arguments: list[str], benchmark: bool,
                       inputs: dict[str, str], *, successful: bool, legacy: bool) -> None:
        """Legacy receipts need identity evidence in the JVM log, never just matching paths."""
        def check_record(kind: str, expected: dict[str, str], required: bool) -> None:
            matching = [row for row in rows if row["kind"] == kind]
            if not matching and not required:
                return
            row = self.one(rows, kind)
            if any(row.get(key) != value for key, value in expected.items()):
                raise RuntimeError(f"{kind} input identity mismatch")

        required = successful or legacy
        if benchmark:
            check_record("CAPACITY_MODEL", {key: inputs[key] for key in ("checkpointSha256", "tokenizerSha256")}, required)
        elif arguments[0] == "corpus":
            if required:
                check_record("CORPUS", {"sha256": sha(Path(arguments[2]))}, True)
        else:
            check_record("SOURCE", {"sha256": inputs["checkpointSha256"],
                                    "tokenizerSha256": inputs["tokenizerSha256"]}, required)
            if arguments[0] in {"evaluate", "train"}:
                kind = "EVALUATION" if arguments[0] == "evaluate" else "TRAIN_INPUT"
                check_record(kind, {key: inputs[key] for key in ("datasetSha256", "tokenizerSha256")}, required)
            if arguments[0] == "parity" and legacy:
                raise RuntimeError("legacy parity receipt does not bind the comparison checkpoint; explicit review required")

    def call(self, label: str, arguments: list[str], *, benchmark: bool = False,
             timeout: int = 180, allow_failure: bool = False) -> list[dict]:
        path = self.logs / f"{label}.tsv"
        receipt = self.logs / f"{label}.receipt.json"
        inputs = self.input_hashes(arguments, benchmark)
        if path.exists() or receipt.exists():
            if not receipt.exists():
                raise RuntimeError(f"unfinished invocation requires explicit review: {label}")
            saved = json.loads(receipt.read_text())
            if saved["logSha256"] != sha(path) or saved["arguments"] != arguments:
                raise RuntimeError(f"invocation changed: {label}")
            if saved.get("identityValidationFailure"):
                raise RuntimeError(f"previous invocation has invalid identities: {label}")
            legacy = "inputHashes" not in saved
            if not legacy and saved["inputHashes"] != inputs:
                raise RuntimeError(f"invocation input bytes changed: {label}")
            result = records(path)
            successful = saved["returncode"] == 0 and not saved.get("timeout", False)
            self.verify_records(result, arguments, benchmark, inputs, successful=successful, legacy=legacy)
            if not successful and not allow_failure:
                raise RuntimeError(f"previous invocation failed: {label}")
            return result
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
        result = records(path)
        identity_failure = None
        try:
            if self.input_hashes(arguments, benchmark) != inputs:
                raise RuntimeError("input bytes changed during invocation")
            self.verify_records(result, arguments, benchmark, inputs,
                                successful=returncode == 0 and not timed_out, legacy=False)
        except (OSError, ValueError, RuntimeError, KeyError) as error:
            identity_failure = f"{type(error).__name__}: {error}"
        write_new(receipt, {"arguments": arguments, "command": command, "returncode": returncode,
                           "timeout": timed_out, "timeoutSeconds": timeout, "wallSeconds": elapsed,
                           "logSha256": sha(path), "inputHashes": inputs,
                           "orchestratorSha256": self.orchestrator_sha,
                           "identityValidationFailure": identity_failure})
        highlights = [r for r in result if r["kind"] in {"EVALUATION", "CAPACITY_RESULT", "PARITY", "TRAIN_RESULT", "PROBE_RESULT", "CHECKPOINT", "CORPUS"}]
        print(json.dumps({"finished": label, "returncode": returncode, "seconds": elapsed,
                          "highlights": highlights}, ensure_ascii=False), flush=True)
        if identity_failure:
            raise RuntimeError(f"{label} identity verification failed: {identity_failure}")
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

    @staticmethod
    def matched_training_inputs(candidates: list[dict]) -> bool:
        inputs = [c["trainingInput"] for c in candidates if "trainingInput" in c]
        fields = ("datasetSha256", "tokenScheduleSha256", "tokenizerSha256")
        if any(not row.get(key) for row in inputs for key in fields):
            raise RuntimeError("continuation lacks a complete TRAIN_INPUT identity")
        if len(inputs) > 1 and any(row[key] != inputs[0][key] for row in inputs[1:] for key in fields):
            raise RuntimeError("small and expanded continuation used different training data or token schedules")
        return len(inputs) == 2

    @staticmethod
    def preferred_candidate(candidates: list[dict]) -> dict | None:
        eligible = [c for c in candidates if c.get("capacityEligible") is True]
        if not eligible:
            return None
        minimum_bpb = min(c["devBpb"] for c in eligible)
        equivalent = [c for c in eligible if c["devBpb"] <= minimum_bpb + 0.00001]
        return min(equivalent, key=lambda c: (c["parameters"], c["devBpb"]))

    def candidate(self, rows: list[dict], label: str) -> dict:
        row = self.one(rows, "CHECKPOINT")
        if sha(Path(row["path"])) != row["sha256"]:
            raise RuntimeError("generated checkpoint hash mismatch")
        return {"label": label, "path": row["path"], "sha256": row["sha256"],
                "parameters": int(row["parameters"]), "bytes": int(row["bytes"])}

    @staticmethod
    def verify_candidate(candidate: dict) -> None:
        if sha(Path(candidate["path"])) != candidate["sha256"]:
            raise RuntimeError(f"candidate checkpoint changed: {candidate['label']}")

    def score(self, candidate: dict, suffix: str = "dev") -> dict:
        if suffix not in {"dev", "holdout"}:
            raise ValueError("unknown evaluation split")
        self.verify_candidate(candidate)
        dataset = self.development if suffix == "dev" else self.holdout
        expected_dataset = self.dataset_hash(dataset)
        rows = self.invoke(f"{candidate['label']}-{suffix}", "evaluate", Path(candidate["path"]), dataset)
        source = self.one(rows, "SOURCE")
        score = self.one(rows, "EVALUATION")
        if (source["sha256"] != candidate["sha256"] or source["tokenizerSha256"] != TOKENIZER_SHA
                or score["datasetSha256"] != expected_dataset or score["tokenizerSha256"] != TOKENIZER_SHA):
            raise RuntimeError("score does not belong to the frozen candidate and dataset")
        value = float(score["bpb"])
        if not math.isfinite(value):
            raise RuntimeError("non-finite BPB")
        candidate[f"{suffix}Bpb"] = value
        return score

    def benchmark(self, candidate: dict) -> bool:
        self.verify_candidate(candidate)
        result = self.call(f"{candidate['label']}-capacity", [candidate["path"], str(self.tokenizer)],
                           benchmark=True, timeout=120, allow_failure=True)
        rows = [r for r in result if r["kind"] == "CAPACITY_RESULT"]
        receipt = json.loads((self.logs / f"{candidate['label']}-capacity.receipt.json").read_text())
        candidate["capacity"] = rows[0] if len(rows) == 1 else {"pass": "false", "reasons": "MISSING_FINAL_RESULT"}
        candidate["capacity"]["processReturncode"] = receipt["returncode"]
        candidate["capacity"]["processTimedOut"] = receipt["timeout"]
        candidate["capacityEligible"] = (receipt["returncode"] == 0 and not receipt["timeout"]
                and candidate["capacity"]["pass"] == "true"
                and candidate["capacity"].get("checkpointSha256") == candidate["sha256"]
                and candidate["capacity"].get("measuredRepeats") == "3"
                and candidate["capacity"].get("firstTokenObservations") == "3"
                and candidate["capacity"].get("full32TokenObservations") == "3")
        return candidate["capacityEligible"]

    def load_calibration(self) -> dict:
        calibration = json.loads((self.out / "calibration.json").read_text())
        for candidate in calibration["candidates"]:
            if not (self.logs / f"{candidate['label']}-dev.receipt.json").exists():
                raise RuntimeError("completed calibration lacks a development receipt")
            recorded = candidate["devBpb"]
            self.score(candidate)
            if candidate["devBpb"] != recorded:
                raise RuntimeError("calibration score differs from its verified evaluation")
        expected = min(calibration["candidates"], key=lambda c: (c["devBpb"], c["label"] != "baseline"))
        if expected["sha256"] != calibration["selected"]["sha256"]:
            raise RuntimeError("calibration selection differs from the fixed selection rule")
        calibration["selected"] = dict(expected)
        return calibration

    def calibration(self) -> None:
        if (self.out / "calibration.json").exists():
            self.load_calibration()
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
        selection = self.load_calibration()["selected"]
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
        ladder = json.loads((self.out / "ladder.json").read_text())
        if not isinstance(ladder, dict):
            raise RuntimeError("CONTINUATION_GATE_REJECTED: INVALID_LADDER")
        expanded = ladder.get("lastPassed")
        if ladder.get("stop") == "BASELINE_GATE":
            raise RuntimeError("CONTINUATION_GATE_REJECTED: BASELINE_GATE")
        if not isinstance(expanded, dict):
            raise RuntimeError("CONTINUATION_GATE_REJECTED: NO_PASSING_CANDIDATE")
        if expanded.get("capacityEligible") is not True:
            raise RuntimeError("CONTINUATION_GATE_REJECTED: LAST_PASSED_INELIGIBLE")
        # Resume must obey the same mandatory gate as a fresh continuation.
        if (self.out / "frozen-selection.json").exists():
            return
        calibration = self.load_calibration()
        original = calibration["candidates"][0]
        selected = calibration["selected"]
        candidates = {c["sha256"]: dict(c) for c in [original, selected]}
        failures = []
        if expanded and expanded["parameters"] > selected["parameters"]:
            candidates[expanded["sha256"]] = dict(expanded)
        for label, initial in [("continued-small", selected), ("continued-expanded", expanded)]:
            if initial is None or (label == "continued-expanded" and initial["parameters"] == selected["parameters"]):
                continue
            self.verify_candidate(initial)
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
        matched_training = self.matched_training_inputs(list(candidates.values()))
        for candidate in candidates.values():
            self.score(candidate)
            self.benchmark(candidate)
            self.invoke(candidate["label"] + "-probes", "probes", Path(candidate["path"]))
        preferred = self.preferred_candidate(list(candidates.values()))
        write_new(self.out / "frozen-selection.json", {
            "frozenUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "holdoutSha256": HOLDOUT_SHA, "holdoutOpened": False,
            "orchestratorSha256": self.orchestrator_sha,
            "developmentSha256": self.development_sha, "trainingSha256": self.training_hash(),
            "candidates": list(candidates.values()), "developmentPreferred": preferred,
            "selectionStatus": "SELECTED" if preferred else "NO_CAPACITY_ELIGIBLE_CANDIDATE",
            "matchedTrainingInputs": matched_training,
            "lastCapacityPassed": expanded, "trainingFailures": failures,
            "selectionRule": "minimum development BPB among completed eligible candidates; within 0.00001 BPB prefer fewer parameters",
        })
        print("FROZEN_SELECTION " + sha(self.out / "frozen-selection.json"), flush=True)

    def final(self) -> None:
        if (self.out / "final.json").exists():
            return
        frozen = self.out / "frozen-selection.json"
        frozen_bytes = frozen.read_bytes()
        selection_sha = hashlib.sha256(frozen_bytes).hexdigest()
        selection = json.loads(frozen_bytes)
        if selection["holdoutSha256"] != sha(self.holdout):
            raise ValueError("holdout changed after selection")
        results = []
        for candidate in selection["candidates"]:
            copy = dict(candidate)
            copy["holdout"] = self.score(copy, "holdout")
            results.append(copy)
        if sha(self.base) != BASE_SHA or sha(self.tokenizer) != TOKENIZER_SHA:
            raise ValueError("original inputs changed")
        if sha(frozen) != selection_sha:
            raise RuntimeError("frozen selection changed during final evaluation")
        write_new(self.out / "final.json", {"selectionSha256": selection_sha, "holdoutSha256": sha(self.holdout),
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
