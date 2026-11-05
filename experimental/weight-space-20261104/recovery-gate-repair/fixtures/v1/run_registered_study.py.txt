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


ROOT = Path(__file__).resolve().parents[2]
EXPERIMENT = ROOT / "experimental/lm1-training-recovery-20261104"
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
                code = process.wait(timeout=120 if capacity else 600)
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
                           "timeout": timed_out, "timeoutSeconds": 120 if capacity else 600,
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

    def diagnostics(self) -> None:
        summaries = {}
        for variant in ["small-standard", "wide-standard", "wide-nf-scaled", "wide-rate-scaled"]:
            self.verify()
            try:
                summaries[variant] = self.diagnostic_variant(variant)
            except (OSError, RuntimeError, ValueError, KeyError, StopIteration) as exc:
                self.verify()
                summaries[variant] = {"status": "REJECTED", "error": str(exc), "limitedContinuationSuccess": False}
                print("DIAGNOSTIC_ARM_REJECTED", variant, str(exc), flush=True)
        write_new(self.out / "diagnostic-summary.json", {"registrationSha256": self.registration_sha,
                  "variants": summaries, "independentHeldOutGeneralizationClaim": False})

    def diagnostic_variant(self, variant: str) -> dict:
        directory = self.out / ("diagnostic-" + variant)
        self.run("diagnostic-" + variant, "LmRecoveryDiagnostic", [
            str(self.inputs / "lm1-baseline.lm1"), str(self.inputs / "lm1-tokenizer.lmbp"),
            str(self.inputs / "train.txt"), str(ROOT / "experimental/weight-space-20261104/development.txt"),
            str(directory), variant])
        log = directory / "diagnostic.tsv"
        final = next(c for c in rows(log, "CHECKPOINT") if c["step"] == "16")
        checkpoint = Path(final["path"])
        if sha(checkpoint) != final["sha256"]:
            raise RuntimeError("CHECKPOINT_CHANGED")
        capacity = self.benchmark("diagnostic-" + variant + "-capacity-after", checkpoint)
        metrics = rows(log, "FIXED_DEV")
        before = next(m for m in metrics if m["step"] == "0")
        after = next(m for m in metrics if m["step"] == "16")
        return {"status": "COMPLETED", "checkpointSha256": final["sha256"], "before": before, "after": after,
                "fixedFirstBatch": rows(log, "FIXED_FIRST_BATCH"), "eosProbes": rows(log, "EOS_PROBE"),
                "capacity": capacity, "limitedContinuationSuccess":
                capacity["pass"] == "true" and float(after["contentBpb"]) < float(before["contentBpb"]) - 0.00001}

    def intent(self) -> None:
        for variant in ["small", "wide"]:
            directory = self.out / ("intent-" + variant)
            self.run("intent-" + variant, "LmIntentStudy", ["train", str(self.inputs / "lm1-baseline.lm1"),
                     str(self.inputs / "lm1-tokenizer.lmbp"), str(EXPERIMENT / "intent-data"), str(directory), variant])
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
        if (int(result["step"]) not in [32, 64, 128] or result["testRead"] != "false"
                or result["inputsUnchanged"] != "true" or result["devTotal"] != "24"):
            raise RuntimeError("INVALID_SELECTION")
        scores = [s for s in rows(log, "SCORE") if s["split"] == "dev"]
        if (sorted(int(s["step"]) for s in scores) != [0, 32, 64, 128]
                or any(s["total"] != "24" or not math.isfinite(float(s["answerNll"]))
                       or not 0 <= int(s["correct"]) <= 24 for s in scores)):
            raise RuntimeError("INVALID_DEVELOPMENT_EVIDENCE")
        best = min((s for s in scores if s["step"] != "0"),
                   key=lambda s: (-int(s["correct"]), float(s["answerNll"]), int(s["step"])))
        if (result["step"] != best["step"] or result["devCorrect"] != best["correct"]
                or float(result["devAnswerNll"]) != float(best["answerNll"])):
            raise RuntimeError("SELECTION_RULE_MISMATCH")
        checkpoints = rows(log, "CHECKPOINT")
        if sorted(int(c["step"]) for c in checkpoints) != [0, 32, 64, 128]:
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

    def post_capacity_evidence(self, variant: str, selected: dict) -> dict:
        """Validate saved, complete capacity evidence; a measured FAIL stays a FAIL.

        This reads existing evidence only. Missing/invalid evidence is not an
        ordinary capacity FAIL and must never authorize a frozen test selection.
        """
        label = "intent-" + variant + "-capacity-after"

        def require(condition: bool, message: str) -> None:
            if not condition:
                raise ValueError(message)

        def unique(pairs: list[tuple[str, object]]) -> dict:
            result = {}
            for key, value in pairs:
                require(key not in result, "duplicate field: " + key)
                result[key] = value
            return result

        def number(value: str) -> float:
            result = float(value)
            require(math.isfinite(result) and result >= 0, "invalid nonnegative measurement")
            return result

        def integer(value: str) -> int:
            require(isinstance(value, str) and re.fullmatch(r"0|[1-9][0-9]*", value) is not None,
                    "invalid nonnegative integer")
            return int(value)

        def optional_integer(value: str):
            return None if value == "UNAVAILABLE" else integer(value)

        try:
            self.verify()
            require(variant in ("small", "wide"), "unknown capacity variant")
            checkpoint = Path(selected["path"])
            tokenizer = self.inputs / "lm1-tokenizer.lmbp"
            checkpoint_sha = selected["checkpointSha256"]
            require(sha(checkpoint) == checkpoint_sha, "selected checkpoint changed")
            log = self.logs / (label + ".tsv")
            receipt_path = self.logs / (label + ".receipt.json")
            raw_log, raw_receipt = log.read_bytes(), receipt_path.read_bytes()
            log_sha = hashlib.sha256(raw_log).hexdigest()
            receipt_sha = hashlib.sha256(raw_receipt).hexdigest()
            receipt = json.loads(raw_receipt, object_pairs_hook=unique)
            require(set(receipt) == set("label command startedUtc wallSeconds returncode timeout timeoutSeconds "
                    "sourceCommit registrationSha256 jarSha256 logSha256 registeredBytesUnchanged".split()),
                    "incomplete or unexpected capacity receipt fields")
            expected_command = ["java", "-Xms64m", "-Xmx1g", "-cp",
                                str(self.jar) + os.pathsep + str(self.stdlib), PACKAGE + "LmCapacityBenchmark",
                                str(checkpoint), str(tokenizer)]
            require(receipt["label"] == label and receipt["command"] == expected_command,
                    "capacity process identity mismatch")
            require(receipt["timeout"] is False and receipt["registeredBytesUnchanged"] is True
                    and type(receipt["timeoutSeconds"]) is int and receipt["timeoutSeconds"] == 120
                    and type(receipt["returncode"]) is int and receipt["returncode"] in (0, 1),
                    "capacity process did not complete under the registered budget")
            require(type(receipt["wallSeconds"]) in (int, float)
                    and 0 <= number(str(receipt["wallSeconds"])) <= 120, "capacity wall budget mismatch")
            started = datetime.datetime.fromisoformat(receipt["startedUtc"])
            require(started.utcoffset() == datetime.timedelta(0), "capacity start time is not UTC")
            require(receipt["sourceCommit"] == self.args.source_commit
                    and receipt["registrationSha256"] == self.registration_sha
                    and receipt["jarSha256"] == self.registration["jarSha256"]
                    and receipt["logSha256"] == log_sha, "unbound capacity receipt")

            records = []
            require(raw_log.endswith(b"\n"), "truncated capacity log")
            for line in raw_log.decode("utf-8").splitlines():
                fields = line.split("\t")
                require(len(fields) > 1 and all("=" in field for field in fields[1:]),
                        "malformed capacity record")
                records.append((fields[0], unique([tuple(field.split("=", 1)) for field in fields[1:]])))

            def one(kind: str) -> dict:
                found = [record for name, record in records if name == kind]
                require(len(found) == 1, "missing or duplicate " + kind)
                return found[0]

            protocol = one("CAPACITY_PROTOCOL")
            require(protocol == {"firstTokenLimitMs": "1500.0", "generation32LimitMs": "5000.0",
                    "vmHwmLimitKiB": "786432", "requestMaxMillis": "30000", "warmups": "1",
                    "measuredRepeats": "3", "ttftMethod": "SEPARATE_ONE_TOKEN_CALL_UPPER_BOUND",
                    "processTimeoutRequired": "true",
                    "promptSha256": "9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495"},
                    "capacity protocol changed")
            model = one("CAPACITY_MODEL")
            require(set(model) == set("checkpointPath checkpointSha256 checkpointBytes tokenizerPath tokenizerSha256 "
                    "tokenizerBytes parameters parameterBytes arch vocab dim layers heads context mlp loadMs promptTokens "
                    "promptIncludesBos fullLayoutLoaded finiteWeights".split()), "incomplete capacity model fields")
            require(model["arch"] == "MINGRU"
                    and all(integer(model[key]) > 0 for key in ("vocab", "dim", "layers", "heads", "context", "mlp")),
                    "invalid capacity model configuration")
            manifests = rows(self.logs / ("intent-" + variant + ".tsv"), "MANIFEST")
            expected_parameters = 132160 if variant == "small" else 6562304
            require(len(manifests) == 1 and manifests[0]["variant"] == variant
                    and integer(manifests[0]["parameters"]) == expected_parameters,
                    "training parameter identity mismatch")
            require(model["checkpointPath"] == str(checkpoint) and model["checkpointSha256"] == checkpoint_sha
                    and integer(model["checkpointBytes"]) == checkpoint.stat().st_size
                    and 0 < checkpoint.stat().st_size <= 64 * 1024 * 1024
                    and model["tokenizerPath"] == str(tokenizer)
                    and model["tokenizerSha256"] == self.registration["inputs"]["lm1-tokenizer.lmbp"]
                    and integer(model["tokenizerBytes"]) == tokenizer.stat().st_size
                    and 0 < tokenizer.stat().st_size <= 100000
                    and integer(model["parameters"]) == expected_parameters
                    and integer(model["parameterBytes"]) == 4 * expected_parameters
                    and model["fullLayoutLoaded"] == "true" and model["finiteWeights"] == "true"
                    and integer(model["promptTokens"]) == 64 and model["promptIncludesBos"] == "false"
                    and integer(model["context"]) >= 97,
                    "capacity model/checkpoint/tokenizer identity mismatch")
            number(model["loadMs"])
            trials = [record for name, record in records if name == "CAPACITY_TRIAL"]
            memories = [record for name, record in records if name == "CAPACITY_MEMORY"]
            expected_trials = [(phase, trial, requested) for phase, count in (("warmup", 1), ("measured", 3))
                               for trial in range(1, count + 1) for requested in (1, 32)]
            require([(r["phase"], integer(r["trial"]), integer(r["requestedNewTokens"])) for r in trials]
                    == expected_trials, "missing, duplicate or reordered capacity trials")
            expected_memory = ["before_load", "after_load"] + [
                f"{phase}_{trial}_{'first' if requested == 1 else 'generation'}"
                for phase, trial, requested in expected_trials] + ["final"]
            require([r["phase"] for r in memories] == expected_memory, "incomplete capacity memory observations")
            expected_order = ["CAPACITY_PROTOCOL", "CAPACITY_MEMORY", "CAPACITY_MODEL", "CAPACITY_MEMORY"]
            expected_order += [kind for _ in expected_trials for kind in ("CAPACITY_TRIAL", "CAPACITY_MEMORY")]
            expected_order += ["CAPACITY_MEMORY", "CAPACITY_RESULT"]
            require([name for name, _ in records] == expected_order, "incomplete or unexpected capacity log")
            reasons = set()
            for record, (_, _, requested) in zip(trials, expected_trials):
                require(set(record) == set("phase trial requestedNewTokens actualNewTokens promptTokens promptTruncated "
                        "stop elapsedMs runtimeMillis ttftUpperBoundMs textSha256".split()), "incomplete capacity trial fields")
                elapsed, actual = number(record["elapsedMs"]), integer(record["actualNewTokens"])
                require(actual <= requested, "invalid actual capacity token count")
                require(integer(record["runtimeMillis"]) <= elapsed, "runtime duration exceeds complete call duration")
                require(re.fullmatch(r"[a-f0-9]{64}", record["textSha256"]) is not None, "invalid output hash")
                require(record["stop"] in {"EOS", "MAX_TOKENS", "CONTEXT", "TIME", "CANCELLED"}
                        and record["promptTruncated"] in {"true", "false"}, "invalid generation status")
                if requested == 1 and actual == 1:
                    require(number(record["ttftUpperBoundMs"]) == elapsed, "first-token measurement mismatch")
                else:
                    require(record["ttftUpperBoundMs"] == "UNAVAILABLE", "unobserved first token claimed")
                if record["stop"] in {"TIME", "CANCELLED"}:
                    reasons.add("GENERATION_" + record["stop"])
                if record["promptTruncated"] == "true" or integer(record["promptTokens"]) != 64:
                    reasons.add("PROMPT_CHANGED_OR_TRUNCATED")
                if record["stop"] == "CONTEXT":
                    reasons.add("INSUFFICIENT_CONTEXT")
                if record["stop"] == "MAX_TOKENS" and actual != requested:
                    reasons.add("INCOMPLETE_MAX_TOKENS_RESULT")
            measured = [r for r in trials if r["phase"] == "measured"]
            first = [r for r in measured if r["requestedNewTokens"] == "1"]
            generation = [r for r in measured if r["requestedNewTokens"] == "32"]
            first_observations = sum(integer(r["actualNewTokens"]) == 1 for r in first)
            full_observations = sum(integer(r["actualNewTokens"]) == 32 for r in generation)
            first_max = max(number(r["elapsedMs"]) for r in first)
            generation_max = max(number(r["elapsedMs"]) for r in generation)
            if first_observations != 3:
                reasons.add("NO_FIRST_TOKEN_OBSERVATION")
            if full_observations != 3:
                reasons.add("INCOMPLETE_32_TOKEN_WORKLOAD")
            if first_max > 1500:
                reasons.add("FIRST_TOKEN_LIMIT")
            if generation_max > 5000:
                reasons.add("GENERATION_32_LIMIT")
            require(number(model["loadMs"]) + sum(number(r["elapsedMs"]) for r in trials)
                    <= 1000 * number(str(receipt["wallSeconds"])) + 1,
                    "capacity measurements exceed recorded process duration")
            hwm_samples = []
            for record in memories:
                require(set(record) == set("phase vmHwmKiB vmRssKiB heapUsedBytes heapCommittedBytes heapMaxBytes".split()),
                        "incomplete capacity memory fields")
                hwm = optional_integer(record["vmHwmKiB"])
                optional_integer(record["vmRssKiB"])
                require(0 <= integer(record["heapUsedBytes"]) <= integer(record["heapCommittedBytes"])
                        <= integer(record["heapMaxBytes"]) <= 1024 ** 3 and integer(record["heapMaxBytes"]) > 0,
                        "capacity heap contract mismatch")
                if hwm is not None:
                    hwm_samples.append(hwm)
            peak_hwm = max(hwm_samples, default=None)
            if peak_hwm is None:
                reasons.add("PROCESS_RSS_UNAVAILABLE")
            elif peak_hwm > 768 * 1024:
                reasons.add("PROCESS_RSS_LIMIT")
            result = one("CAPACITY_RESULT")
            require(set(result) == set("pass reasons checkpointSha256 parameters loadMs measuredRepeats maxFirstTokenCallMs "
                    "maxGeneration32CallMs firstTokenObservations full32TokenObservations vmHwmKiB finalVmHwmKiB rssMethod "
                    "failure".split()), "incomplete capacity summary fields")
            require(result["checkpointSha256"] == checkpoint_sha and integer(result["parameters"]) == expected_parameters
                    and number(result["loadMs"]) == number(model["loadMs"])
                    and integer(result["measuredRepeats"]) == 3
                    and integer(result["firstTokenObservations"]) == first_observations
                    and integer(result["full32TokenObservations"]) == full_observations
                    and number(result["maxFirstTokenCallMs"]) == first_max
                    and number(result["maxGeneration32CallMs"]) == generation_max
                    and optional_integer(result["vmHwmKiB"]) == peak_hwm
                    and optional_integer(result["finalVmHwmKiB"]) == optional_integer(memories[-1]["vmHwmKiB"])
                    and result["rssMethod"] == "MAXIMUM_OBSERVED_VMHWM" and result["failure"] == "UNAVAILABLE",
                    "capacity summary does not match the complete measurements")
            reported_reasons = [] if result["reasons"] == "NONE" else result["reasons"].split(",")
            require(len(reported_reasons) == len(set(reported_reasons)) and set(reported_reasons) == reasons
                    and result["pass"] == ("false" if reasons else "true")
                    and receipt["returncode"] == (1 if reasons else 0), "capacity verdict mismatch")
            self.verify()
            require(sha(checkpoint) == checkpoint_sha and sha(log) == log_sha and sha(receipt_path) == receipt_sha,
                    "capacity evidence changed while verifying")
            return {"status": "FAIL" if reasons else "PASS", "logSha256": log_sha, "receiptSha256": receipt_sha,
                    "receipt": receipt, "protocol": protocol, "model": model, "result": result,
                    "stdlibSha256": self.registration["stdlibSha256"]}
        except (OSError, ValueError, KeyError, TypeError, OverflowError) as exc:
            raise RuntimeError("INVALID_POST_CAPACITY_EVIDENCE: " + label + ": " + str(exc)) from exc

    def freeze_evidence(self) -> dict:
        self.verify()
        selected = {variant: self.selection(variant) for variant in ["small", "wide"]}
        post_capacity = {arm: self.post_capacity_evidence(variant, selected[variant])
                         for variant, arm in (("small", "S1"), ("wide", "W1"))}
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
        return {"status": "FROZEN_BEFORE_TEST", "arms": arms, "postTrainingCapacity": post_capacity,
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
            if selection != self.freeze_evidence():
                raise RuntimeError("INVALID_TEST_AUTHORIZATION")
            value = selection["arms"][arm]
            if sha(Path(value["path"])) != value["checkpointSha256"] or sha(frozen) != frozen_sha:
                raise RuntimeError("FROZEN_CHECKPOINT_CHANGED")
            self.run("test-" + arm, "LmIntentStudy", ["test", value["path"], str(self.inputs / "lm1-tokenizer.lmbp"),
                     str(EXPERIMENT / "intent-data/test.sealed.tsv")])
            reported_input = rows(self.logs / ("test-" + arm + ".tsv"), "TEST_INPUT")
            if (len(reported_input) != 1 or reported_input[0]["checkpointSha256"] != value["checkpointSha256"]
                    or reported_input[0]["testSha256"] != selection["testSha256"]
                    or sha(Path(value["path"])) != value["checkpointSha256"]):
                raise RuntimeError("TEST_INPUT_MISMATCH")
            score = rows(self.logs / ("test-" + arm + ".tsv"), "SCORE")
            if len(score) != 1 or score[0]["total"] != "48":
                raise RuntimeError("INVALID_TEST_RESULT")
            outputs[arm] = score[0]
        if sha(frozen) != frozen_sha or selection != self.freeze_evidence():
            raise RuntimeError("FREEZE_CHANGED_DURING_TEST")
        w = outputs["W1"]
        limited = int(w["correct"]) >= 36 and min(map(int, w["classCorrect"].split(","))) >= 8
        limited = limited and all(int(w["correct"]) >= int(outputs[a]["correct"]) + 8 for a in ["S0", "W0"])
        write_new(self.out / "intent-summary.json", {"selectionSha256": frozen_sha, "scores": outputs,
                  "postTrainingCapacity": selection["postTrainingCapacity"],
                  "limitedBenchmarkLearningCriterionPassed": limited,
                  "widerSelectedCheckpointHasFourCaseAdvantage": int(w["correct"]) >= int(outputs["S1"]["correct"]) + 4,
                  "generalIntelligenceClaim": False, "modelPromotion": False, "androidValidation": "NOT_RUN"})
        print(json.dumps(outputs), flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["diagnostics", "intent", "freeze", "test"])
    for name in ["jar", "stdlib", "inputs", "output"]:
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    getattr(Study(args), args.phase)()


if __name__ == "__main__":
    main()
