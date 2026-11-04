#!/usr/bin/env python3
"""Independently audit published V3 results and the pre-test choice at 9ceffc26.

Only pinned Git text blobs and previous immutable local audit files are read.
No model, JAR, tokenizer, Intent dataset or generator is opened or executed.
The sole child program is Git. An absent published test log is not proof about
unpublished processes; this review never authorizes a final test or promotion.
"""
from __future__ import annotations

import argparse
import csv
import datetime
import hashlib
import io
import json
import math
from pathlib import Path
import re
import subprocess
import types


HERE = Path(__file__).resolve().parent
PREVIOUS = HERE.parents[1] / "recovery-gate-repair/result-review"
PIN = "9ceffc26a2159c8dc726c1f13d2882119159c130"
PARENT = "e3c5aeadd6336f843bd625d56431208d3a03b5ad"
SOURCE = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
STUDY = EVIDENCE + "study-v3/"
RESEARCH = "experimental/lm1-training-recovery-20261104/"
PACKAGE = "at.heinz.deviceai.learning.lm."
TEST_KEY = RESEARCH + "intent-data/test.sealed.tsv"
BENCHMARK = "experimental/weight-space-20261104/LmCapacityBenchmark.kt"
FROZEN = {
    "audit_published_results.py": "def7392d9afbe3fa4e7086b42e1e4168d2b1b124c25d3f90f815a1bb633dab6b",
    "published-results.json": "6f6cd60b94a5b3191b60d284eb0eb676d921379bd26c3c0e36083e9eb237384d",
    "README.md": "b639ba726a0712e5f4dacb04b1a15626cf88169ef6491f8fd87cf65bc2bc5ff8",
    "audit_published_v2.py": "7f02e19ac4d465b96b231f6677c0dbe4274113d229e77287b958b699fd749e6c",
    "published-v2-results.json": "334c6f521c91bfd29433c50fc14a542f22e6c3b32102fbad04f908268082cc88",
    "V2_ADDENDUM.md": "ba0bace3d54a83506b616f576367108013d10f08b36998afc1f790a1be1a9a01",
}


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify_previous() -> dict:
    actual = {name: digest((PREVIOUS / name).read_bytes()) for name in FROZEN}
    require(actual == FROZEN, "previous audit files changed")
    return actual


verify_previous()
_helper_path = PREVIOUS / "audit_published_v2.py"
_helpers = types.ModuleType("immutable_v2_calculators")
_helpers.__file__ = str(_helper_path)
exec(compile(_helper_path.read_bytes(), str(_helper_path), "exec"), _helpers.__dict__)
records, single = _helpers.records, _helpers.single
score_cases, epoch_losses = _helpers.score_cases, _helpers.epoch_losses


def unique(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate field: " + key)
        result[key] = value
    return result


def numeric(value: object) -> float:
    result = float(value)
    require(math.isfinite(result) and result >= 0, "invalid measurement")
    return result


class Archive:
    def __init__(self, repo: Path):
        self.repo = repo
        self.cache: dict[str, bytes] = {}
        self.inventory: dict[str, dict] = {}
        self.paths = self.git("ls-tree", "-r", "--name-only", PIN, EVIDENCE, RESEARCH).decode().splitlines()

    def git(self, *args: str) -> bytes:
        return subprocess.run(["git", *args], cwd=self.repo, check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout

    def read(self, path: str) -> bytes:
        require(path.startswith((EVIDENCE, RESEARCH, "app/src/", "experimental/weight-space-20261104/",
                                 "docs/research/omega/")), "outside text review scope")
        require("/intent-data/" not in path and not path.endswith(("make_intent_data.py", "development.txt")),
                "dataset/generator access prohibited")
        require(path.endswith((".tsv", ".json", ".md", ".kt", ".py", ".log")), "binary access prohibited")
        if path not in self.cache:
            raw = self.git("show", PIN + ":" + path)
            raw.decode("utf-8")
            self.cache[path] = raw
            self.inventory[path] = {"sha256": digest(raw), "bytes": len(raw),
                "gitBlobSha1": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()}
        return self.cache[path]

    def json(self, path: str) -> dict:
        return json.loads(self.read(path), object_pairs_hook=unique)


def capacity(raw: bytes, checkpoint: dict, parameters: int, tokenizer_sha: str) -> dict:
    """Recompute resource/workload results directly from the published raw calls."""
    require(raw.endswith(b"\n"), "truncated capacity log")
    rows = []
    for line in raw.decode().splitlines():
        fields = line.split("\t")
        require(len(fields) > 1 and all("=" in field for field in fields[1:]), "malformed capacity row")
        rows.append((fields[0], unique([tuple(field.split("=", 1)) for field in fields[1:]])))
    protocol, model, result = (single(raw, kind) for kind in ("CAPACITY_PROTOCOL", "CAPACITY_MODEL", "CAPACITY_RESULT"))
    require(protocol == {"firstTokenLimitMs": "1500.0", "generation32LimitMs": "5000.0",
        "vmHwmLimitKiB": "786432", "requestMaxMillis": "30000", "warmups": "1", "measuredRepeats": "3",
        "ttftMethod": "SEPARATE_ONE_TOKEN_CALL_UPPER_BOUND", "processTimeoutRequired": "true",
        "promptSha256": "9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495"},
        "capacity protocol changed")
    require(model["checkpointPath"] == checkpoint["path"]
            and model["checkpointSha256"] == result["checkpointSha256"] == checkpoint["sha256"],
            "capacity checkpoint binding differs")
    require(model["parameters"] == result["parameters"] == str(parameters)
            and int(model["parameterBytes"]) == 4 * parameters, "capacity parameter identity differs")
    require(model["tokenizerSha256"] == tokenizer_sha and 0 < int(model["tokenizerBytes"]) <= 100000
            and 0 < int(model["checkpointBytes"]) <= 64 * 1024 * 1024, "capacity input identity/size differs")
    require(model["arch"] == "MINGRU" and model["fullLayoutLoaded"] == model["finiteWeights"] == "true"
            and model["promptTokens"] == "64" and model["promptIncludesBos"] == "false"
            and int(model["context"]) >= 97, "capacity model contract differs")
    trials = [row for kind, row in rows if kind == "CAPACITY_TRIAL"]
    memories = [row for kind, row in rows if kind == "CAPACITY_MEMORY"]
    expected = [(phase, trial, requested) for phase, count in (("warmup", 1), ("measured", 3))
                for trial in range(1, count + 1) for requested in (1, 32)]
    require([(r["phase"], int(r["trial"]), int(r["requestedNewTokens"])) for r in trials] == expected,
            "capacity calls missing, duplicated or reordered")
    memory_order = ["before_load", "after_load"] + [
        f"{phase}_{trial}_{'first' if requested == 1 else 'generation'}" for phase, trial, requested in expected] + ["final"]
    require([r["phase"] for r in memories] == memory_order, "memory observations incomplete")
    order = ["CAPACITY_PROTOCOL", "CAPACITY_MEMORY", "CAPACITY_MODEL", "CAPACITY_MEMORY"]
    order += [kind for _ in expected for kind in ("CAPACITY_TRIAL", "CAPACITY_MEMORY")]
    order += ["CAPACITY_MEMORY", "CAPACITY_RESULT"]
    require([kind for kind, _ in rows] == order, "capacity raw record order differs")
    reasons = set()
    for row in trials:
        actual, requested = int(row["actualNewTokens"]), int(row["requestedNewTokens"])
        elapsed = numeric(row["elapsedMs"])
        require(0 <= actual <= requested and 0 <= int(row["runtimeMillis"]) <= elapsed,
                "invalid token count or call duration")
        require(row["stop"] in {"EOS", "MAX_TOKENS", "CONTEXT", "TIME", "CANCELLED"}, "unknown generation stop")
        require(re.fullmatch(r"[0-9a-f]{64}", row["textSha256"]) is not None, "invalid output identity")
        if requested == 1 and actual == 1:
            require(numeric(row["ttftUpperBoundMs"]) == elapsed, "first-token upper bound differs")
        else:
            require(row["ttftUpperBoundMs"] == "UNAVAILABLE", "unobserved first token claimed")
        if row["stop"] in {"TIME", "CANCELLED"}:
            reasons.add("GENERATION_" + row["stop"])
        if row["promptTruncated"] != "false" or row["promptTokens"] != "64":
            reasons.add("PROMPT_CHANGED_OR_TRUNCATED")
        if row["stop"] == "CONTEXT":
            reasons.add("INSUFFICIENT_CONTEXT")
        if row["stop"] == "MAX_TOKENS" and actual != requested:
            reasons.add("INCOMPLETE_MAX_TOKENS_RESULT")
    measured = [r for r in trials if r["phase"] == "measured"]
    first = [r for r in measured if r["requestedNewTokens"] == "1"]
    generation = [r for r in measured if r["requestedNewTokens"] == "32"]
    first_count = sum(r["actualNewTokens"] == "1" for r in first)
    full_count = sum(r["actualNewTokens"] == "32" for r in generation)
    first_max = max(numeric(r["elapsedMs"]) for r in first)
    generation_max = max(numeric(r["elapsedMs"]) for r in generation)
    if first_count != 3:
        reasons.add("NO_FIRST_TOKEN_OBSERVATION")
    if full_count != 3:
        reasons.add("INCOMPLETE_32_TOKEN_WORKLOAD")
    if first_max > 1500:
        reasons.add("FIRST_TOKEN_LIMIT")
    if generation_max > 5000:
        reasons.add("GENERATION_32_LIMIT")
    peak = max(int(row["vmHwmKiB"]) for row in memories)
    require(all(0 <= int(row["heapUsedBytes"]) <= int(row["heapCommittedBytes"])
                <= int(row["heapMaxBytes"]) <= 1024 ** 3 and int(row["heapMaxBytes"]) > 0
                and int(row["vmRssKiB"]) >= 0 and int(row["vmHwmKiB"]) >= 0 for row in memories),
            "capacity heap/memory observations invalid")
    if peak > 768 * 1024:
        reasons.add("PROCESS_RSS_LIMIT")
    require(int(result["measuredRepeats"]) == 3 and int(result["firstTokenObservations"]) == first_count
            and int(result["full32TokenObservations"]) == full_count
            and numeric(result["maxFirstTokenCallMs"]) == first_max
            and numeric(result["maxGeneration32CallMs"]) == generation_max
            and int(result["vmHwmKiB"]) == peak
            and result["finalVmHwmKiB"] == memories[-1]["vmHwmKiB"]
            and result["rssMethod"] == "MAXIMUM_OBSERVED_VMHWM"
            and result["failure"] == "UNAVAILABLE"
            and numeric(result["loadMs"]) == numeric(model["loadMs"]), "capacity summary disagrees with raw measurements")
    reported = [] if result["reasons"] == "NONE" else result["reasons"].split(",")
    require(len(reported) == len(set(reported)) and set(reported) == reasons
            and result["pass"] == ("false" if reasons else "true"), "capacity verdict differs")
    return {"protocol": protocol, "model": model, "result": result, "trials": trials,
            "peakVmHwmKiBRecomputed": peak, "completeCalls": len(trials),
            "loadAndCallMilliseconds": numeric(model["loadMs"]) + sum(numeric(r["elapsedMs"]) for r in trials)}


def receipt(archive: Archive, label: str, reg: dict, reg_sha: str, after: bool = False) -> dict:
    stem = label + ("-capacity-after" if after else "")
    row = archive.json(STUDY + "logs/" + stem + ".receipt.json")
    raw = archive.read(STUDY + "logs/" + stem + ".tsv")
    require(row["label"] == stem and row["logSha256"] == digest(raw), "process receipt/log mismatch")
    require(row["sourceCommit"] == SOURCE and row["registrationSha256"] == reg_sha
            and row["jarSha256"] == reg["jarSha256"] and row["registeredBytesUnchanged"] is True,
            "process identity mismatch")
    require(row["timeout"] is False and type(row["returncode"]) is int
            and row["returncode"] == (1 if after else 0), "unexpected process outcome")
    limit = 120 if after else 900
    require(row["timeoutSeconds"] == limit and numeric(row["wallSeconds"]) <= limit,
            "process duration/budget mismatch")
    started = datetime.datetime.fromisoformat(row["startedUtc"])
    require(started.utcoffset() == datetime.timedelta(0), "process time is not UTC")
    return row


def expected_freeze(arms: dict, reg_sha: str, source: str, token_schedule: str, test_sha: str) -> dict:
    payload = {}
    for size, prefix in (("small", "S"), ("wide", "W")):
        arm = arms[size]
        initial = arm["checkpointRows"]["0"]
        selected = arm["checkpointRows"][arm["selection"]["step"]]
        payload[prefix + "0"] = {"path": initial["path"], "checkpointSha256": initial["sha256"], "step": 0}
        payload[prefix + "1"] = dict(arm["selection"], path=selected["path"])
    return {"status": "FROZEN_BEFORE_TEST", "arms": payload, "sourceCommit": source,
            "registrationSha256": reg_sha, "tokenScheduleSha256": token_schedule, "testSha256": test_sha,
            "rule": "Dev exact descending, answer NLL ascending, fewer steps; development threshold passed"}


def audit(repo: Path) -> dict:
    archive = Archive(repo)
    require(archive.git("show", "-s", "--format=%P", PIN).decode().strip() == PARENT, "unexpected ancestry")
    changes = archive.git("diff-tree", "--no-commit-id", "--name-status", "-r", PIN).decode().splitlines()
    require(all(line.startswith("A\t" + EVIDENCE) for line in changes), "snapshot changes more than added evidence")
    reg_path = RESEARCH + "suffix-study/registration.json"
    reg, reg_raw = archive.json(reg_path), archive.read(reg_path)
    reg_sha = digest(reg_raw)
    require(reg_sha == "04392b366746b1e6fca78eb66758e8c99e94b8392dbe013a02d2b416e2184728", "unreviewed V3 registration")
    skipped = [p for p in reg["files"] if "/intent-data/" in p or p.endswith(("make_intent_data.py", "development.txt"))]
    source_paths = [p for p in reg["files"] if p not in skipped]
    for path in source_paths:
        require(digest(archive.read(path)) == reg["files"][path], "registered source changed: " + path)
    require(digest(archive.read(BENCHMARK)) == "d88541b436089b6df04f50035cffa79378b939dabbf03ef77a9ca1acd52bbd9c",
            "benchmark contract changed")
    summary = archive.json(STUDY + "development-summary.json")
    expected_inputs = {"lm1-baseline.lm1": reg["inputs"]["lm1-baseline.lm1"],
                       "lm1-tokenizer.lmbp": reg["inputs"]["lm1-tokenizer.lmbp"],
                       "train.tsv": reg["files"][RESEARCH + "intent-data/train.tsv"],
                       "dev.tsv": reg["files"][RESEARCH + "intent-data/dev.tsv"]}
    arms = {}
    for size in ("small", "wide"):
        label = "intent-" + size
        log = archive.read(STUDY + "logs/" + label + ".tsv")
        train, post = receipt(archive, label, reg, reg_sha), receipt(archive, label, reg, reg_sha, after=True)
        command = train["command"]
        require(len(command) == 12 and command[:4] == ["java", "-Xms64m", "-Xmx3g", "-cp"]
                and command[5:7] == [PACKAGE + "LmIntentSuffixStudy", "train"] and command[-1] == size,
                "training command differs")
        manifest = single(log, "MANIFEST")
        parameters = 132160 if size == "small" else 6562304
        require(manifest["variant"] == size and int(manifest["parameters"]) == parameters
                and manifest["scope"] == "RESEARCH_SHADOW" and manifest["steps"] == "512"
                and manifest["batch"] == "4" and manifest["objective"] == "ANSWER_AND_EOS_ONLY"
                and manifest["testReadDuringTraining"] == "false", "training manifest mismatch")
        require(float(manifest["learningRate"]) == 0.0001 and manifest["warmup"] == "4"
                and float(manifest["weightDecay"]) == 0.01 and manifest["seed"] == "20261104"
                and manifest["workers"] == "2", "optimizer contract differs")
        inputs = records(log, "INPUT")
        require(len(inputs) == 4 and {Path(r["path"]).name: r["sha256"] for r in inputs} == expected_inputs,
                "reported input identity differs")
        input_paths = {Path(r["path"]).name: r["path"] for r in inputs}
        require(command[7] == input_paths["lm1-baseline.lm1"] and command[8] == input_paths["lm1-tokenizer.lmbp"]
                and str(Path(command[9]) / "train.tsv") == input_paths["train.tsv"]
                and str(Path(command[9]) / "dev.tsv") == input_paths["dev.tsv"], "command/input paths differ")
        steps = records(log, "TRAIN_STEP")
        require([int(r["step"]) for r in steps] == list(range(1, 513))
                and all(int(r["supervisedTargets"]) == 8 * int(r["step"]) for r in steps), "incomplete update/target sequence")
        require(all(math.isfinite(float(r[k])) for r in steps for k in ("answerEosNll", "gradientNormBeforeClip")),
                "nonfinite training observation")
        clocks = [int(r["millis"]) for r in steps]
        require(clocks == sorted(clocks) and clocks[0] >= 0 and clocks[-1] < 840000
                and clocks[-1] <= 1000 * float(train["wallSeconds"]), "training clock exceeds budget")
        scores = records(log, "SCORE")
        require([r["step"] for r in scores] == ["0", "128", "256", "512"], "incomplete score history")
        snapshots = score_cases(log, scores)
        selected = min(scores[1:], key=lambda r: (-int(r["correct"]), float(r["answerNll"]), int(r["step"])))
        selection = single(log, "SELECTION")
        require(selection["step"] == selected["step"] and selection["devCorrect"] == selected["correct"]
                and selection["devAnswerNll"] == selected["answerNll"] and selection["devTotal"] == "24"
                and selection["testRead"] == "false" and selection["inputsUnchanged"] == "true"
                and selection["generalModelPromotion"] == "NOT_PERMITTED", "development selection differs")
        saved = list(csv.DictReader(io.StringIO(archive.read(STUDY + label + "/selection.tsv").decode()), delimiter="\t"))
        require(len(saved) == 1 and saved[0]["variant"] == size
                and all(saved[0][k] == selection[k] for k in ("checkpointSha256", "step", "devCorrect", "devTotal", "devAnswerNll")),
                "saved selection differs")
        checkpoint_list = records(log, "CHECKPOINT")
        checkpoints = {r["step"]: r for r in checkpoint_list}
        require(len(checkpoint_list) == 4 and set(checkpoints) == {"0", "128", "256", "512"}, "checkpoint history differs")
        require(checkpoints[selection["step"]]["sha256"] == selection["checkpointSha256"]
                and checkpoints["0"]["sha256"] == saved[0]["initialSha256"], "checkpoint identity differs")
        require(all(row["path"] == str(Path(command[10]) / "checkpoints" / (row["sha256"] + ".lm1"))
                    for row in checkpoint_list), "checkpoint/output path differs")
        before_raw = archive.read(STUDY + label + "/capacity-before.tsv")
        after_raw = archive.read(STUDY + "logs/" + label + "-capacity-after.tsv")
        before = capacity(before_raw, checkpoints["0"], parameters, expected_inputs["lm1-tokenizer.lmbp"])
        after = capacity(after_raw, checkpoints[selection["step"]], parameters, expected_inputs["lm1-tokenizer.lmbp"])
        expected_post_command = command[:2] + ["-Xmx1g", "-cp", command[4], PACKAGE + "LmCapacityBenchmark",
                                              checkpoints[selection["step"]]["path"], command[8]]
        require(post["command"] == expected_post_command and post["returncode"] == (0 if after["result"]["pass"] == "true" else 1),
                "post-capacity command or exit differs")
        require(after["loadAndCallMilliseconds"] <= 1000 * float(post["wallSeconds"]) + 1,
                "raw capacity times exceed process wall time")
        gate = single(log, "INITIAL_CAPACITY_GATE")
        require(before["result"]["pass"] == gate["pass"] == "true"
                and gate["logSha256"] == digest(before_raw) and gate["checkpointSha256"] == checkpoints["0"]["sha256"]
                and log.index(b"INITIAL_CAPACITY_GATE\t") < log.index(b"TRAIN_STEP\t"), "initial gate binding/order differs")
        parity = records(log, "INITIAL_PARITY")
        require(len(parity) == 3 and all(0 <= float(r["probabilityDelta"]) <= 0.00001 for r in parity), "parity gate differs")
        require(summary["arms"][size]["scores"] == scores and summary["arms"][size]["selection"] == selection
                and summary["arms"][size]["capacity"] == after["result"], "V3 summary differs from raw evidence")
        arms[size] = {"manifest": manifest, "trainingReceipt": train, "capacityReceipt": post,
            "updates": len(steps), "supervisedTargets": int(steps[-1]["supervisedTargets"]),
            "lastLoggedTrainingMillis": clocks[-1], "snapshots": snapshots, "selection": selection,
            "checkpointRows": checkpoints, "capacityBefore": before, "capacityAfter": after,
            "initialParity": parity, "onlineEpochLossesRecomputed": epoch_losses(steps)}
    require(summary["sourceCommit"] == SOURCE and summary["testEvaluated"] is False and summary["modelPromotion"] is False,
            "V3 summary identity/boundary differs")
    require(len({arm["manifest"]["scheduleSha256"] for arm in arms.values()}) == 1
            and len({arm["manifest"]["tokenScheduleSha256"] for arm in arms.values()}) == 1,
            "V3 within-study schedules differ")
    prior = json.loads((PREVIOUS / "published-v2-results.json").read_text())
    reused = []
    for path, identity in prior["sourceInventory"].items():
        if path.startswith(EVIDENCE + "study-v2/"):
            require(digest(archive.read(path)) == identity["sha256"], "previously audited V2 evidence changed")
            reused.append(path)
    v2_arms = prior["arms"]
    for size, arm in v2_arms.items():
        rows = records(archive.read(EVIDENCE + "study-v2/logs/intent-" + size + ".tsv"), "CHECKPOINT")
        arm["checkpointRows"] = {r["step"]: r for r in rows}
    v2_reg = archive.json(RESEARCH + "long-study/registration.json")
    test_sha = reg["files"][TEST_KEY]
    require(test_sha == v2_reg["files"][TEST_KEY], "candidate test identities differ")
    v2_manifest, v3_manifest = v2_arms["wide"]["manifest"], arms["wide"]["manifest"]
    require(v2_manifest["scheduleSha256"] == v3_manifest["scheduleSha256"]
            and v2_manifest["tokenScheduleSha256"] != v3_manifest["tokenScheduleSha256"], "suffix schedules differ from protocol")
    v2_code = archive.read(RESEARCH + "long-study/LmIntentLongStudy.kt").decode()
    v3_code = archive.read(RESEARCH + "suffix-study/LmIntentSuffixStudy.kt").decode()
    expected_code = v2_code
    for before, after in (("object LmIntentLongStudy", "object LmIntentSuffixStudy"),
                          ("lm1-intent-long-20261104", "lm1-intent-suffix-20261104"),
                          ("\\nAktion:", "\\n")):
        require(expected_code.count(before) == 1, "unexpected controlled source replacement")
        expected_code = expected_code.replace(before, after)
    require(expected_code == v3_code, "additional V2-to-V3 harness changes")
    freeze_by_version = {
        "v2": expected_freeze(v2_arms, prior["registrationSha256"], prior["sourceCommitReported"], v2_manifest["tokenScheduleSha256"], test_sha),
        "v3": expected_freeze(arms, reg_sha, SOURCE, v3_manifest["tokenScheduleSha256"], test_sha),
    }
    for version, study_arms in (("v2", v2_arms), ("v3", arms)):
        wide = study_arms["wide"]
        require(int(wide["selection"]["devCorrect"]) >= 12
                and int(wide["selection"]["devCorrect"]) >= int(wide["snapshots"][0]["score"]["correct"]) + 4,
                version + " not development-eligible")
    choice_path = EVIDENCE + "final-selection/development-study-choice.json"
    freeze_path = EVIDENCE + "final-selection/selection-freeze.json"
    before_path = EVIDENCE + "final-selection/before-test-verification.json"
    choice, frozen, verified = archive.json(choice_path), archive.json(freeze_path), archive.json(before_path)
    expected_candidates = {name: {"status": "ELIGIBLE", "freezePayload": value} for name, value in freeze_by_version.items()}
    require(choice["candidates"] == expected_candidates, "choice candidates differ from the raw selections")
    expected_schedules = {version: {key: manifest[key] for key in ("scheduleSha256", "tokenScheduleSha256")}
                          for version, manifest in (("v2", v2_manifest), ("v3", v3_manifest))}
    require(choice["schedules"] == expected_schedules and choice["testSha256"] == test_sha, "choice input binding differs")
    chosen = min(freeze_by_version, key=lambda version: (-int(freeze_by_version[version]["arms"]["W1"]["devCorrect"]),
                 float(freeze_by_version[version]["arms"]["W1"]["devAnswerNll"]), 0 if version == "v2" else 1))
    require(chosen == choice["selectedStudy"] == "v3" and choice["status"] == "DEVELOPMENT_CHOICE_BEFORE_TEST"
            and choice["testEvaluated"] is False and choice["selectedFreezePayload"] == freeze_by_version[chosen] == frozen,
            "choice/freeze rule or payload differs")
    require(choice["rule"] == "Wide Dev exact descending, answer NLL ascending, then retain v2", "choice rule changed")
    require(verified == {"choiceSha256": digest(archive.read(choice_path)), "freezeSha256": digest(archive.read(freeze_path)),
                        "selectedStudy": chosen, "selectedSourceCommit": SOURCE,
                        "allFinalTestLogsAbsent": True, "choiceMatchesFreezeExactly": True}, "pre-test verification disagrees")
    final_test_paths = [p for p in archive.paths if p.startswith(EVIDENCE) and "/test-" in p]
    require(not final_test_paths, "new final-test evidence requires a separate no-tuning review")
    chosen_cases = {size: next(s["caseDecisions"] for s in arm["snapshots"] if s["step"] == int(arm["selection"]["step"]))
                    for size, arm in arms.items()}
    require(set(chosen_cases["small"]) == set(chosen_cases["wide"]), "V3 selected case identities differ")
    paired = {"bothCorrect": 0, "onlySmallCorrect": 0, "onlyWideCorrect": 0, "bothWrong": 0}
    for key in chosen_cases["small"]:
        small, wide = chosen_cases["small"][key], chosen_cases["wide"][key]
        paired["bothCorrect" if small and wide else "onlySmallCorrect" if small else "onlyWideCorrect" if wide else "bothWrong"] += 1
    handoff = archive.read("docs/research/omega/LM1-TRAINING-RECOVERY-HANDOFF-20261104.md").decode()
    return {"status": "PUBLISHED_V3_AND_LEGACY_FREEZE_CONSISTENT_CAPACITY_FAIL_FINAL_TEST_NOT_PUBLISHED",
        "reviewedCommit": PIN, "previousReviewedCommit": PARENT, "executedSourceCommitReported": SOURCE,
        "evidenceCommitTime": archive.git("show", "-s", "--format=%cI", PIN).decode().strip(),
        "addedEvidencePaths": changes, "executableSourcesChangedByEvidenceCommit": False,
        "registrationSha256": reg_sha, "jarSha256Reported": reg["jarSha256"],
        "registeredSourcesRehashed": source_paths, "registeredFilesIntentionallyNotRead": skipped,
        "frozenPreviousFiles": verify_previous(), "unchangedPreviouslyAuditedV2Evidence": reused,
        "sourceInventory": dict(sorted(archive.inventory.items())),
        "actualModelProcesses": 0, "modelJarTokenizerAndDatasetBytesRead": 0,
        "receiptsChecked": 4, "updatesChecked": 1024, "devCasesRescored": 192,
        "rawCapacityRunsRecomputed": 4, "onlineEpochMeansRecomputed": 32,
        "arms": arms, "selectedPairedDevResults": paired,
        "studyChoice": {"selectedStudy": chosen, "v2WideCorrect": int(v2_arms["wide"]["selection"]["devCorrect"]),
            "v3WideCorrect": int(arms["wide"]["selection"]["devCorrect"]), "choice": choice, "freeze": frozen,
            "beforeTestVerification": verified, "legacyFreezeMatchesRawSelection": True,
            "boundPostCapacityEvidencePresentInFreeze": "postTrainingCapacity" in frozen,
            "observedArmCapacityFields": {arm: frozen["arms"][arm].get("postTrainingCapacity") for arm in ("S1", "W1")},
            "newTestExecutionAuthorizedByThisAudit": False},
        "publishedFinalTestPaths": final_test_paths,
        "holdoutState": "NO_FINAL_TEST_PUBLICATION_AT_PIN; not a forensic claim about unpublished processes",
        "handoffStillCallsV3Running": "v3 running" in handoff,
        "interpretation": {"developmentSelectionValidUnderPublishedLegacyProtocol": True,
            "capacityPassed": False, "independentFinalGeneralizationEvidenceAvailable": False,
            "sourceBoundGateRepairAdoptedByThisAudit": False,
            "modelPromotion": False, "checkpointBinaryIntegrityIndependentlyRechecked": False,
            "sourceBoundLogIdentityIsNotSignedProcessAttestation": True,
            "noNewTrainingOrTuningRecommendationFromFinalTest": True,
            "selectedCheckpointTargets": {size: int(arm["selection"]["step"]) * 8 for size, arm in arms.items()},
            "onlineEpochMeansAreNotFixedCheckpointOrGeneralizationLosses": True,
            "wrapperChangeDoesNotIsolateForgetGateDamping": True}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = audit(args.repo.resolve())
    encoded = json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8")
        print(report["status"] + "; " + str(len(report["sourceInventory"])) + " pinned text sources verified")
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
