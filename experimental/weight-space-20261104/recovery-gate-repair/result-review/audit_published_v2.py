#!/usr/bin/env python3
"""Audit the one published V2 evidence addendum at e3c5, without model/data access.

The previous be4e report and auditor remain immutable. Reuse only its pinned text
parsers and capacity calculator. All child processes run read-only Git commands.
"""
from __future__ import annotations

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import struct
import subprocess
import types


PIN = "e3c5aeadd6336f843bd625d56431208d3a03b5ad"
PARENT = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
SOURCE = "fc2eb797210bee25091caf734aacbfd694a3372e"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
STUDY = EVIDENCE + "study-v2/"
RESEARCH = "experimental/lm1-training-recovery-20261104/"
HELPER_SHA = "def7392d9afbe3fa4e7086b42e1e4168d2b1b124c25d3f90f815a1bb633dab6b"
FROZEN = {
    "audit_published_results.py": HELPER_SHA,
    "published-results.json": "6f6cd60b94a5b3191b60d284eb0eb676d921379bd26c3c0e36083e9eb237384d",
    "README.md": "b639ba726a0712e5f4dacb04b1a15626cf88169ef6491f8fd87cf65bc2bc5ff8",
}


def verify_frozen() -> dict[str, str]:
    root = Path(__file__).resolve().parent
    actual = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in FROZEN}
    if actual != FROZEN:
        raise ValueError("previous report or parser bytes changed")
    return actual


verify_frozen()
_helper_path = Path(__file__).with_name("audit_published_results.py")
_helpers = types.ModuleType("frozen_published_parsers")
# Loading the hash-checked text directly avoids creating a __pycache__ artifact.
exec(compile(_helper_path.read_bytes(), str(_helper_path), "exec"), _helpers.__dict__)
require, digest, records, single, capacity = (_helpers.require, _helpers.digest, _helpers.records,
                                            _helpers.single, _helpers.capacity)


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
        require(path.startswith((EVIDENCE, RESEARCH, "app/src/", "experimental/weight-space-20261104/")), "path outside review scope")
        require("/intent-data/" not in path and not path.endswith("make_intent_data.py"), "Intent data/generator access prohibited")
        require(path.endswith((".tsv", ".json", ".md", ".kt", ".py", ".txt", ".log")), "binary access prohibited")
        if path not in self.cache:
            raw = self.git("show", PIN + ":" + path)
            raw.decode("utf-8")
            self.cache[path] = raw
            self.inventory[path] = {"sha256": digest(raw), "bytes": len(raw),
                                    "gitBlobSha1": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()}
        return self.cache[path]

    def json(self, path: str) -> dict:
        return json.loads(self.read(path))


def receipt(archive: Archive, label: str, reg: dict, reg_sha: str, after: bool = False) -> dict:
    stem = label + ("-capacity-after" if after else "")
    rec = archive.json(STUDY + "logs/" + stem + ".receipt.json")
    raw = archive.read(STUDY + "logs/" + stem + ".tsv")
    require(rec["label"] == stem and rec["logSha256"] == digest(raw), "receipt/log mismatch")
    require(rec["sourceCommit"] == SOURCE and rec["registrationSha256"] == reg_sha
            and rec["jarSha256"] == reg["jarSha256"], "receipt identity mismatch")
    require(rec["returncode"] == (1 if after else 0) and rec["timeout"] is False
            and rec["registeredBytesUnchanged"] is True, "unexpected process failure or changed inputs")
    seconds = 120 if after else 900
    require(rec["timeoutSeconds"] == seconds and 0 <= rec["wallSeconds"] < seconds, "process budget mismatch")
    require(("-Xmx1g" if after else "-Xmx3g") in rec["command"], "heap bound mismatch")
    require(("at.heinz.deviceai.learning.lm.LmCapacityBenchmark" if after else
             "at.heinz.deviceai.learning.lm.LmIntentLongStudy") in rec["command"], "wrong harness")
    return rec


def score_cases(log: bytes, scores: list[dict]) -> list[dict]:
    all_cases = records(log, "CASE")
    require(len(all_cases) == 96, "missing V2 CASE rows")
    snapshots = []
    for score in scores:
        cases = [r for r in all_cases if r["step"] == score["step"]]
        require(len(cases) == 24 and len({r["id"] for r in cases}) == 24, "duplicate or missing Dev cases")
        matrix = [[0] * 5 for _ in range(4)]
        totals = [0] * 4
        correct = [0] * 4
        decisions = {}
        outputs = {}
        for row in cases:
            require(row["split"] == "dev", "unexpected split")
            output = base64.b64decode(row["textBase64"], validate=True).decode()
            label = int(row["expected"])
            valid = output in ("0", "1", "2", "3") and row["tokens"] == "1" and row["stop"] == "EOS"
            ok = valid and output == row["expected"]
            require((row["correct"] == "true") == ok, "free digit+EOS score mismatch")
            totals[label] += 1
            correct[label] += ok
            matrix[label][int(output) if valid else 4] += 1
            decisions[row["id"]] = ok
            outputs[row["id"]] = {"expected": row["expected"], "output": output,
                                   "tokens": int(row["tokens"]), "stop": row["stop"], "correct": ok}
        require(totals == [6, 6, 6, 6] == [int(n) for n in score["classTotal"].split(",")], "class counts mismatch")
        require(correct == [int(n) for n in score["classCorrect"].split(",")]
                and sum(correct) == int(score["correct"]) and int(score["total"]) == 24, "score totals mismatch")
        require(sum(int(r["tokens"]) for r in cases) == int(score["generatedTokens"]), "token count mismatch")
        require(sum(r["stop"] != "EOS" for r in cases) == int(score["missingEos"]), "missing EOS mismatch")
        require(sum(r["tokens"] == "0" and r["stop"] == "EOS" for r in cases) == int(score["prematureEos"]), "premature EOS mismatch")
        for key in ("answerNll", "eosNll"):
            value = sum(float(r[key]) for r in cases) / 24
            require(math.isfinite(value) and math.isclose(value, float(score[key]), rel_tol=1e-12), "NLL aggregation mismatch")
        mean_eos = sum(struct.unpack("<f", struct.pack("<f", float(r["firstEosProbability"])))[0] for r in cases) / 24
        require(math.isfinite(mean_eos) and mean_eos == float(score["meanFirstEosProbability"]), "Float32 EOS probability aggregation mismatch")
        snapshots.append({"step": int(score["step"]), "score": score, "validDigitEos": 24 - sum(row[4] for row in matrix),
                          "confusionMatrixWithInvalidLastColumn": matrix, "caseDecisions": decisions, "publishedOutputs": outputs})
    return snapshots


def epoch_losses(steps: list[dict]) -> list[dict]:
    epochs = []
    for index in range(16):
        batch = steps[index * 32:(index + 1) * 32]
        losses = [float(r["answerEosNll"]) for r in batch]
        require(len(batch) == 32, "incomplete epoch")
        epochs.append({"epoch": index + 1, "first_step": index * 32 + 1, "last_step": (index + 1) * 32,
                       "minibatches": 32, "supervised_targets": 256, "cumulative_supervised_targets": (index + 1) * 256,
                       "mean_online_nll": sum(losses) / 32, "minimum_minibatch_nll": min(losses),
                       "maximum_minibatch_nll": max(losses)})
    return epochs


def audit(repo: Path) -> dict:
    archive = Archive(repo)
    parents = archive.git("show", "-s", "--format=%P", PIN).decode().strip().split()
    require(parents == [PARENT], "unexpected evidence addendum ancestry")
    changes = archive.git("diff-tree", "--no-commit-id", "--name-status", "-r", PIN).decode().splitlines()
    require(all(line.split("\t")[-1].startswith((EVIDENCE, "docs/research/omega/")) for line in changes), "addendum changes executable sources")
    reg_path = RESEARCH + "long-study/registration.json"
    reg = archive.json(reg_path)
    reg_sha = digest(archive.read(reg_path))
    require(reg_sha == "7f87a5a6582f23cdf1d647dba4daed8c494afa970f5224329a78bc0337b854e6", "unexpected V2 registration")
    skipped = [p for p in reg["files"] if "/intent-data/" in p or p.endswith("make_intent_data.py")]
    sources = [p for p in reg["files"] if p not in skipped]
    needed = [p for p in archive.paths if p.startswith(STUDY)] + sources + [
        EVIDENCE + "REPRODUCE.md", EVIDENCE + "intent-epoch-losses.json",
        RESEARCH + "LmIntentStudy.kt", RESEARCH + "INTENT_PROTOCOL.md",
    ]
    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(archive.read, sorted(set(needed))))
    for path in sources:
        require(digest(archive.read(path)) == reg["files"][path], "registered source hash mismatch: " + path)
    summary = archive.json(STUDY + "intent-summary.json")
    epochs_published = archive.json(EVIDENCE + "intent-epoch-losses.json")
    expected_inputs = {"lm1-baseline.lm1": reg["inputs"]["lm1-baseline.lm1"],
                       "lm1-tokenizer.lmbp": reg["inputs"]["lm1-tokenizer.lmbp"],
                       "train.tsv": reg["files"][RESEARCH + "intent-data/train.tsv"],
                       "dev.tsv": reg["files"][RESEARCH + "intent-data/dev.tsv"]}
    arms = {}
    for size in ("small", "wide"):
        label = "intent-" + size
        log = archive.read(STUDY + "logs/" + label + ".tsv")
        train_rec = receipt(archive, label, reg, reg_sha)
        capacity_rec = receipt(archive, label, reg, reg_sha, after=True)
        manifest = single(log, "MANIFEST")
        require(manifest["variant"] == size and manifest["scope"] == "RESEARCH_SHADOW"
                and manifest["steps"] == "512" and manifest["batch"] == "4"
                and manifest["objective"] == "ANSWER_AND_EOS_ONLY" and manifest["testReadDuringTraining"] == "false",
                "V2 training manifest mismatch")
        require(float(manifest["learningRate"]) == 0.0001 and manifest["warmup"] == "4"
                and float(manifest["weightDecay"]) == 0.01 and manifest["seed"] == "20261104"
                and manifest["workers"] == "2", "optimizer settings differ")
        input_rows = records(log, "INPUT")
        bound_inputs = {Path(r["path"]).name: r["sha256"] for r in input_rows}
        require(len(input_rows) == 4 and bound_inputs == expected_inputs, "input bindings mismatch")
        steps = records(log, "TRAIN_STEP")
        require([int(r["step"]) for r in steps] == list(range(1, 513)), "missing V2 optimizer updates")
        require(all(int(r["supervisedTargets"]) == 8 * int(r["step"]) for r in steps), "supervised target count differs")
        require(all(math.isfinite(float(r[k])) for r in steps for k in ("answerEosNll", "gradientNormBeforeClip")), "nonfinite training observations")
        require(int(steps[-1]["millis"]) < 840000, "internal training clock exceeds limit")
        scores = records(log, "SCORE")
        require([r["step"] for r in scores] == ["0", "128", "256", "512"], "V2 snapshots incomplete")
        snapshots = score_cases(log, scores)
        selected = min(scores[1:], key=lambda r: (-int(r["correct"]), float(r["answerNll"]), int(r["step"])))
        selection = single(log, "SELECTION")
        require(selection["step"] == selected["step"] and selection["devCorrect"] == selected["correct"]
                and selection["devAnswerNll"] == selected["answerNll"] and selection["devTotal"] == "24",
                "V2 selection is not the best registered Dev candidate")
        require(selection["testRead"] == "false" and selection["inputsUnchanged"] == "true"
                and selection["generalModelPromotion"] == "NOT_PERMITTED", "invalid selection boundary")
        saved = list(csv.DictReader(io.StringIO(archive.read(STUDY + label + "/selection.tsv").decode()), delimiter="\t"))
        require(len(saved) == 1 and saved[0]["variant"] == size
                and all(saved[0][k] == selection[k] for k in ("checkpointSha256", "step", "devCorrect", "devTotal", "devAnswerNll")),
                "V2 selection artifact mismatch")
        checkpoint_rows = records(log, "CHECKPOINT")
        checkpoints = {r["step"]: r["sha256"] for r in checkpoint_rows}
        require(len(checkpoint_rows) == 4 and set(checkpoints) == {"0", "128", "256", "512"}, "checkpoint history incomplete")
        require(checkpoints[selection["step"]] == selection["checkpointSha256"] and checkpoints["0"] == saved[0]["initialSha256"], "checkpoint identity mismatch")
        parameters = 132160 if size == "small" else 6562304
        require(int(manifest["parameters"]) == parameters, "parameter count mismatch")
        before_raw = archive.read(STUDY + label + "/capacity-before.tsv")
        after_raw = archive.read(STUDY + "logs/" + label + "-capacity-after.tsv")
        before = capacity(before_raw, checkpoints["0"], parameters)
        after = capacity(after_raw, selection["checkpointSha256"], parameters)
        gate = single(log, "INITIAL_CAPACITY_GATE")
        require(before["result"]["pass"] == gate["pass"] == "true" and gate["logSha256"] == digest(before_raw)
                and gate["checkpointSha256"] == checkpoints["0"], "initial gate identity mismatch")
        require(log.index(b"INITIAL_CAPACITY_GATE\t") < log.index(b"TRAIN_STEP\t"), "V2 training precedes initial gate")
        parity = records(log, "INITIAL_PARITY")
        require(len(parity) == 3 and all(0 <= float(p["probabilityDelta"]) <= 0.00001 for p in parity), "initial parity gate mismatch")
        require(summary["arms"][size]["scores"] == scores and summary["arms"][size]["selection"] == selection
                and summary["arms"][size]["capacity"] == after["result"], "published V2 summary disagrees with raw evidence")
        epochs = epoch_losses(steps)
        published_runs = [r for r in epochs_published["runs"] if r["version"] == 2 and r["arm"] == size]
        require(len(published_runs) == 1, "missing published epoch summary")
        epoch_run = published_runs[0]
        require(epoch_run["epochs"] == epochs and epoch_run["raw_log_sha256"] == digest(log)
                and epoch_run["source_commit"] == SOURCE and epoch_run["registration_sha256"] == reg_sha
                and epoch_run["jar_sha256"] == reg["jarSha256"], "published epoch means or identity disagree")
        arms[size] = {"manifest": manifest, "inputHashesReported": bound_inputs, "updates": len(steps),
                      "supervisedTargets": int(steps[-1]["supervisedTargets"]), "initialParity": parity,
                      "trainingReceipt": train_rec, "capacityReceipt": capacity_rec,
                      "lastLoggedTrainingMillis": int(steps[-1]["millis"]), "snapshots": snapshots,
                      "selection": selection, "checkpointHashesReported": checkpoints, "onlineEpochLosses": epochs,
                      "capacityBefore": before, "capacityAfter": after,
                      "capacityAfterMeasuredTrials": [r for r in records(after_raw, "CAPACITY_TRIAL") if r["phase"] == "measured"]}
    require(len({v["manifest"]["scheduleSha256"] for v in arms.values()}) == 1, "unequal V2 example schedules")
    require(len({v["manifest"]["tokenScheduleSha256"] for v in arms.values()}) == 1, "unequal V2 token schedules")
    wide = arms["wide"]
    eligible = int(wide["selection"]["devCorrect"]) >= 12 and int(wide["selection"]["devCorrect"]) >= int(wide["snapshots"][0]["score"]["correct"]) + 4
    require(eligible and summary["sourceCommit"] == SOURCE and summary["testEvaluated"] is False
            and summary["modelPromotion"] is False, "V2 development status mismatch")
    chosen_cases = {}
    for size, arm in arms.items():
        chosen_cases[size] = next(s["caseDecisions"] for s in arm["snapshots"] if s["step"] == int(arm["selection"]["step"]))
    require(set(chosen_cases["small"]) == set(chosen_cases["wide"]), "selected Dev IDs differ")
    paired = {"bothCorrect": 0, "onlySmallCorrect": 0, "onlyWideCorrect": 0, "bothWrong": 0}
    for key in chosen_cases["small"]:
        s, w = chosen_cases["small"][key], chosen_cases["wide"][key]
        paired["bothCorrect" if s and w else "onlySmallCorrect" if s else "onlyWideCorrect" if w else "bothWrong"] += 1
    v1 = archive.read(RESEARCH + "LmIntentStudy.kt").decode()
    v2 = archive.read(RESEARCH + "long-study/LmIntentLongStudy.kt").decode()
    expected_v2 = v1
    changes_v1_v2 = [("object LmIntentStudy", "object LmIntentLongStudy"), ("val STEPS = 128", "val STEPS = 512"),
                     ("lm1-intent-20261104", "lm1-intent-long-20261104"), ("< 540)", "< 840)"),
                     ("listOf(32, 64, 128)", "listOf(128, 256, 512)")]
    for before, after in changes_v1_v2:
        require(expected_v2.count(before) == 1, "unexpected V1-to-V2 source replacement")
        expected_v2 = expected_v2.replace(before, after)
    require(expected_v2 == v2 and "(STEPS - 4)" in v2, "V1-to-V2 source differences changed")
    test_artifacts = [p for p in archive.paths if p.startswith(EVIDENCE) and
                      ("/test-" in p or p.endswith(("selection-freeze.json", "development-choice.json")))]
    require(not test_artifacts, "published final test or freeze requires a separate review")
    frozen = verify_frozen()
    return {"status": "PUBLISHED_V2_RAW_EVIDENCE_CONSISTENT_DEV_GATE_PASS_CAPACITY_FAIL_TEST_DEFERRED",
            "reviewedCommit": PIN, "previousReviewedCommit": PARENT, "sourceCommitReported": SOURCE,
            "addendumChanges": changes, "executableSourcesChangedByAddendum": False,
            "registrationSha256": reg_sha, "jarSha256Reported": reg["jarSha256"],
            "sourceInventory": dict(sorted(archive.inventory.items())), "registeredSourcesRehashed": sources,
            "registeredFilesIntentionallyNotRead": skipped, "frozenPreviousFiles": frozen,
            "modelProcessesStarted": 0, "modelAndIntentDatasetBytesRead": 0, "casesRescored": 192,
            "receiptsChecked": 4, "updatesChecked": 1024, "arms": arms, "selectedPairedDevResults": paired,
            "developmentEligible": eligible, "publishedStatus": summary["status"], "testEvaluatedReported": False,
            "publishedTestOrFreezeArtifacts": test_artifacts,
            "v3StudyEvidencePaths": [p for p in archive.paths if p.startswith(EVIDENCE + "study-v3/")],
            "interpretation": {"previousSummaryOnlyGapResolvedAtThisCommit": True,
                               "independentModelTrainingReplication": False,
                               "checkpointBinaryIntegrityIndependentlyRechecked": False,
                               "wholeStudyTrainingBudgetTargetsPerArm": 4096,
                               "selectedCheckpointTargets": {size: int(a["selection"]["step"]) * 8 for size, a in arms.items()},
                               "sameV1V2StepNumberDoesNotMeanSameLearningRatePrefix": True,
                               "onlineEpochMeansAreNotFixedCheckpointOrGeneralizationLosses": True,
                               "capacityFailureDoesNotBecomePassBecauseOneDigitWasGenerated": True,
                               "modelPromotion": False}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(args.repo.resolve())
    encoded = json.dumps(result, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8")
        print(result["status"] + "; " + str(len(result["sourceInventory"])) + " pinned text sources verified")
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
