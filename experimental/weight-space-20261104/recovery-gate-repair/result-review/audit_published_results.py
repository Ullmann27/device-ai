#!/usr/bin/env python3
"""Recalculate published PR #508 evidence without loading or executing a model.

Only pinned Git text blobs are read. No model binary, Intent dataset, data generator,
training process or test evaluation is opened or run. The sole child program is
Git. Reports are written only when --output is explicitly supplied.
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


SNAPSHOT = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
RUN_SOURCE = "def5acf9cbba82ee51a53cc06fecc52e9cd0324d"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
RESEARCH = "experimental/lm1-training-recovery-20261104/"
V1 = EVIDENCE + "study-v1/"
VARIANTS = ("small-standard", "wide-standard", "wide-nf-scaled", "wide-rate-scaled")
SIZES = ("small", "wide")
PROMPT = "9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise ValueError(message)


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def records(raw: bytes, kind: str) -> list[dict[str, str]]:
    result = []
    for line in raw.decode().splitlines():
        fields = line.split("\t")
        if fields[0] != kind:
            continue
        row = {}
        for field in fields[1:]:
            key, value = field.split("=", 1)
            require(key not in row, "duplicate TSV field")
            row[key] = value
        result.append(row)
    return result


def single(raw: bytes, kind: str) -> dict[str, str]:
    rows = records(raw, kind)
    require(len(rows) == 1, "expected exactly one " + kind)
    return rows[0]


class Archive:
    def __init__(self, repo: Path):
        self.repo = repo
        self.cache: dict[str, bytes] = {}
        self.inventory: dict[str, dict] = {}
        self.paths = self.git("ls-tree", "-r", "--name-only", SNAPSHOT,
                              EVIDENCE, RESEARCH).decode().splitlines()

    def git(self, *args: str) -> bytes:
        return subprocess.run(["git", *args], cwd=self.repo, check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout

    def read(self, path: str) -> bytes:
        require(path.startswith((EVIDENCE, RESEARCH, "app/src/", "experimental/weight-space-20261104/")),
                "path outside text review scope")
        require("/intent-data/" not in path and not path.endswith("make_intent_data.py"),
                "dataset and generator bytes are intentionally excluded")
        require(path.endswith((".tsv", ".json", ".md", ".kt", ".py", ".txt", ".log")),
                "binary input prohibited")
        if path not in self.cache:
            raw = self.git("show", SNAPSHOT + ":" + path)
            raw.decode("utf-8")
            self.cache[path] = raw
            self.inventory[path] = {
                "sha256": digest(raw), "bytes": len(raw),
                "gitBlobSha1": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest(),
            }
        return self.cache[path]

    def json(self, path: str) -> dict:
        return json.loads(self.read(path))


def capacity(raw: bytes, checkpoint: str, parameters: int) -> dict:
    protocol = single(raw, "CAPACITY_PROTOCOL")
    model = single(raw, "CAPACITY_MODEL")
    result = single(raw, "CAPACITY_RESULT")
    require(protocol["promptSha256"] == PROMPT, "capacity prompt changed")
    require([float(protocol[k]) for k in ("firstTokenLimitMs", "generation32LimitMs", "vmHwmLimitKiB")]
            == [1500.0, 5000.0, 786432.0], "capacity requirements changed")
    require(protocol["measuredRepeats"] == "3" and protocol["warmups"] == "1", "capacity repeats changed")
    require(model["checkpointSha256"] == result["checkpointSha256"] == checkpoint, "checkpoint binding mismatch")
    require(model["parameters"] == result["parameters"] == str(parameters), "parameter count mismatch")
    require(int(model["parameterBytes"]) == 4 * parameters, "parameter byte count mismatch")
    require(model["fullLayoutLoaded"] == model["finiteWeights"] == "true", "model load validation missing")
    trials = records(raw, "CAPACITY_TRIAL")
    measured = [r for r in trials if r["phase"] == "measured"]
    require(len(trials) == 8 and len(measured) == 6, "incomplete capacity trials")
    require(sorted(int(r["requestedNewTokens"]) for r in measured) == [1, 1, 1, 32, 32, 32], "capacity workload changed")
    require(all(r["promptTokens"] == "64" and r["promptTruncated"] == "false" for r in trials), "capacity prompt was shortened")
    first = sum(r["requestedNewTokens"] == "1" and r["actualNewTokens"] == "1" for r in measured)
    full = sum(r["requestedNewTokens"] == "32" and r["actualNewTokens"] == "32" for r in measured)
    require((first, full) == (int(result["firstTokenObservations"]), int(result["full32TokenObservations"])), "capacity counter mismatch")
    passed = (first == full == 3 and float(result["maxFirstTokenCallMs"]) <= 1500
              and float(result["maxGeneration32CallMs"]) <= 5000 and int(result["vmHwmKiB"]) <= 786432)
    require((result["pass"] == "true") == passed, "capacity decision disagrees with workload/resources")
    return {"result": result, "model": model, "measuredTrials": len(measured),
            "allTrialsImmediateEos": all(r["stop"] == "EOS" and r["actualNewTokens"] == "0" for r in trials)}


def receipt(archive: Archive, label: str, registration: dict, registration_sha: str, after: bool = False) -> dict:
    stem = label + ("-capacity-after" if after else "")
    raw = archive.read(V1 + "logs/" + stem + ".tsv")
    rec = archive.json(V1 + "logs/" + stem + ".receipt.json")
    require(rec["label"] == stem and rec["logSha256"] == digest(raw), "receipt/log binding mismatch")
    require(rec["sourceCommit"] == RUN_SOURCE and rec["registrationSha256"] == registration_sha
            and rec["jarSha256"] == registration["jarSha256"], "source/registration/JAR binding mismatch")
    require(rec["timeout"] is False and rec["registeredBytesUnchanged"] is True, "process/identity failed")
    require(rec["returncode"] == (1 if after else 0), "unexpected process exit")
    require(rec["timeoutSeconds"] == (120 if after else 600), "process limit changed")
    require(0 <= rec["wallSeconds"] < rec["timeoutSeconds"], "process exceeded registered budget")
    require(("-Xmx1g" if after else "-Xmx3g") in rec["command"], "heap bound mismatch")
    return rec


def audit(repo: Path) -> dict:
    archive = Archive(repo)
    reg_raw = archive.read(RESEARCH + "registration.json")
    reg = json.loads(reg_raw)
    reg_sha = digest(reg_raw)
    skip = [p for p in reg["files"] if "/intent-data/" in p or p.endswith("make_intent_data.py")]
    sources = [p for p in reg["files"] if p not in skip]
    files = [p for p in archive.paths if p.startswith(V1)] + sources + [
        RESEARCH + "long-study/registration.json", RESEARCH + "long-study/INTENT_LONG_PROTOCOL.md",
        RESEARCH + "long-study/LmIntentLongStudy.kt", RESEARCH + "suffix-study/registration.json",
        RESEARCH + "suffix-study/SUFFIX_PROTOCOL.md", RESEARCH + "suffix-study/LmIntentSuffixStudy.kt",
        RESEARCH + "suffix-study/choose_development_study.py", EVIDENCE + "study-v2/intent-summary.json",
    ]
    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(archive.read, sorted(set(files))))
    for path in sources:
        require(digest(archive.read(path)) == reg["files"][path], "registered source changed: " + path)

    diagnostic = {}
    summary = archive.json(V1 + "diagnostic-summary.json")
    for variant in VARIANTS:
        label = "diagnostic-" + variant
        log = archive.read(V1 + label + "/diagnostic.tsv")
        outer = archive.read(V1 + "logs/" + label + ".tsv")
        require(outer == log or (variant == "small-standard" and outer.split(b"\n", 1)[1] == log), "internal and outer log disagree")
        train_receipt = receipt(archive, label, reg, reg_sha)
        receipt(archive, label, reg, reg_sha, after=True)
        manifest = single(log, "MANIFEST")
        train = single(log, "TRAIN_RESULT")
        steps = records(log, "TRAIN_STEP")
        require([int(r["step"]) for r in steps] == list(range(1, 17)), "missing diagnostic update")
        require(train["tokens"] == manifest["targetTokens"] == "3255" and manifest["eosTargets"] == "26", "diagnostic budget differs")
        require(train["steps"] == manifest["steps"] == "16" and manifest["batch"] == "4", "diagnostic step count differs")
        require(train["inputsUnchanged"] == train["sourcesUnchanged"] == "true", "diagnostic input identity failed")
        parameters = int(manifest["parameters"])
        checkpoints = {r["step"]: r["sha256"] for r in records(log, "CHECKPOINT")}
        before_raw = archive.read(V1 + label + "/capacity-before.tsv")
        before = capacity(before_raw, checkpoints["0"], parameters)
        after = capacity(archive.read(V1 + "logs/" + label + "-capacity-after.tsv"), train["checkpointSha256"], parameters)
        require(checkpoints["16"] == train["checkpointSha256"], "diagnostic final checkpoint mismatch")
        require(single(log, "CAPACITY_GATE")["logSha256"] == digest(before_raw), "initial log hash mismatch")
        require(before["result"]["pass"] == "true" and single(log, "INITIAL_GATES")["pass"] == "true", "initial gate did not pass")
        require(log.index(b"INITIAL_GATES\t") < log.index(b"TRAIN_STEP\t"), "training precedes gate")
        dev = records(log, "FIXED_DEV")
        fixed = records(log, "FIXED_FIRST_BATCH")
        probes = records(log, "EOS_PROBE")
        require([r["step"] for r in dev] == ["0", "1", "4", "16"] and len(fixed) == 4 and len(probes) == 28, "missing diagnosis snapshot")
        require(all(r["contentTargets"] == "839" and r["eosTargets"] == "12" for r in dev), "development budget differs")
        require(all(r["targets"] == "234" for r in fixed), "fixed minibatch budget differs")
        for row in dev + fixed + probes + steps:
            for key, value in row.items():
                if key.endswith(("Nll", "Bpb", "Probability", "Logit", "Margin", "UpdateL2")) or key == "nll":
                    require(math.isfinite(float(value)), "nonfinite diagnostic statistic")
        delta = float(dev[-1]["contentBpb"]) - float(dev[0]["contentBpb"])
        success = after["result"]["pass"] == "true" and delta < -0.00001
        published = summary["variants"][variant]
        require(published["before"] == dev[0] and published["after"] == dev[-1]
                and published["fixedFirstBatch"] == fixed and published["eosProbes"] == probes
                and published["capacity"] == after["result"] and published["limitedContinuationSuccess"] is success,
                "diagnostic summary disagrees with raw observations")
        ratios = [float(r["nfAppliedUpdateL2"]) / float(r["nfProposedUpdateL2"]) for r in steps]
        diagnostic[variant] = {
            "parameters": parameters, "steps": 16, "targetTokens": 3255, "eosTargets": 26,
            "manifest": manifest, "checkpointSha256Reported": train["checkpointSha256"],
            "changedWeightsReported": int(train["changedWeights"]), "processWallSeconds": train_receipt["wallSeconds"],
            "beforeBpb": float(dev[0]["contentBpb"]), "afterBpb": float(dev[-1]["contentBpb"]), "bpbDelta": delta,
            "fixedFirstBatch": fixed, "nfAppliedToProposedRatioRange": [min(ratios), max(ratios)],
            "capacityProbes": [p for p in probes if p["position"] == "capacity"],
            "prefixLargestEosMargin": max(float(p["eosMargin"]) for p in probes if p["position"] == "prefix"),
            "prefixLargestEosProbability": max(float(p["eosProbability"]) for p in probes if p["position"] == "prefix"),
            "capacityBefore": before, "capacityAfter": after, "limitedContinuationSuccess": success,
        }
    require(len({d["manifest"]["tokenScheduleSha256"] for d in diagnostic.values()}) == 1, "diagnostic token schedules differ")
    require(len({d["manifest"]["scheduleSha256"] for d in diagnostic.values()}) == 1, "diagnostic example schedules differ")

    intent = {}
    intent_summary = archive.json(V1 + "intent-summary.json")
    for size in SIZES:
        label = "intent-" + size
        log = archive.read(V1 + "logs/" + label + ".tsv")
        rec = receipt(archive, label, reg, reg_sha)
        receipt(archive, label, reg, reg_sha, after=True)
        manifest = single(log, "MANIFEST")
        steps = records(log, "TRAIN_STEP")
        require([int(r["step"]) for r in steps] == list(range(1, 129)), "intent missing optimizer step")
        require(all(int(r["supervisedTargets"]) == 8 * int(r["step"]) for r in steps), "intent supervised budget differs")
        require(all(math.isfinite(float(r[k])) for r in steps for k in ("answerEosNll", "gradientNormBeforeClip")), "nonfinite intent metric")
        scores = records(log, "SCORE")
        require([r["step"] for r in scores] == ["0", "32", "64", "128"], "intent snapshot missing")
        cases = records(log, "CASE")
        snapshots = []
        for score in scores:
            rows = [r for r in cases if r["step"] == score["step"]]
            require(len(rows) == 24 and len({r["id"] for r in rows}) == 24, "intent cases missing/duplicate")
            matrix = [[0] * 5 for _ in range(4)]
            correct = [0] * 4
            totals = [0] * 4
            decisions = {}
            for row in rows:
                require(row["split"] == "dev", "unexpected test evaluation in development log")
                output = base64.b64decode(row["textBase64"], validate=True).decode()
                label_id = int(row["expected"])
                valid = output in ("0", "1", "2", "3") and row["tokens"] == "1" and row["stop"] == "EOS"
                ok = valid and output == row["expected"]
                require((row["correct"] == "true") == ok, "free generation case incorrectly scored")
                matrix[label_id][int(output) if valid else 4] += 1
                correct[label_id] += ok
                totals[label_id] += 1
                decisions[row["id"]] = ok
            require(totals == [6, 6, 6, 6], "class balance differs")
            require(totals == [int(v) for v in score["classTotal"].split(",")], "reported class totals differ")
            require(sum(correct) == int(score["correct"]) and correct == [int(v) for v in score["classCorrect"].split(",")], "score aggregate differs")
            require(sum(int(r["tokens"]) for r in rows) == int(score["generatedTokens"]), "generated token total differs")
            require(sum(r["stop"] != "EOS" for r in rows) == int(score["missingEos"]), "EOS score differs")
            require(sum(r["stop"] == "EOS" and r["tokens"] == "0" for r in rows) == int(score["prematureEos"]), "premature EOS score differs")
            for key in ("answerNll", "eosNll"):
                value = sum(float(r[key]) for r in rows) / 24
                require(math.isfinite(value) and math.isclose(value, float(score[key]), rel_tol=1e-12), "NLL aggregate differs")
            # Kotlin prints each Float using its shortest round-tripping decimal,
            # then accumulates the original binary Float values in a Double.
            first_eos = sum(struct.unpack("<f", struct.pack("<f", float(r["firstEosProbability"])))[0]
                            for r in rows) / 24
            require(math.isfinite(first_eos) and first_eos == float(score["meanFirstEosProbability"]), "EOS probability aggregate differs")
            snapshots.append({"step": int(score["step"]), "score": score, "confusionMatrixWithInvalidLastColumn": matrix,
                              "validDigitEos": 24 - sum(r[4] for r in matrix), "caseDecisions": decisions})
        chosen = min(scores[1:], key=lambda s: (-int(s["correct"]), float(s["answerNll"]), int(s["step"])))
        selection = single(log, "SELECTION")
        require(selection["step"] == chosen["step"] and selection["devCorrect"] == chosen["correct"]
                and selection["devAnswerNll"] == chosen["answerNll"], "selection rule disagrees")
        saved = list(csv.DictReader(io.StringIO(archive.read(V1 + label + "/selection.tsv").decode()), delimiter="\t"))
        require(len(saved) == 1 and all(saved[0][key] == selection[key] for key in ("checkpointSha256", "step", "devCorrect", "devTotal", "devAnswerNll")), "selection artifact differs")
        checkpoints = {r["step"]: r["sha256"] for r in records(log, "CHECKPOINT")}
        require(checkpoints[selection["step"]] == selection["checkpointSha256"] and saved[0]["initialSha256"] == checkpoints["0"], "selected checkpoint identity differs")
        parameters = int(manifest["parameters"])
        before_raw = archive.read(V1 + label + "/capacity-before.tsv")
        before = capacity(before_raw, checkpoints["0"], parameters)
        after = capacity(archive.read(V1 + "logs/" + label + "-capacity-after.tsv"), selection["checkpointSha256"], parameters)
        gate = single(log, "INITIAL_CAPACITY_GATE")
        require(gate["logSha256"] == digest(before_raw) and gate["checkpointSha256"] == checkpoints["0"] and gate["pass"] == "true", "initial intent gate binding invalid")
        require(log.index(b"INITIAL_CAPACITY_GATE\t") < log.index(b"TRAIN_STEP\t"), "intent training precedes gate")
        published = intent_summary["arms"][size]
        require(published["scores"] == scores and published["selection"] == selection and published["capacity"] == after["result"], "intent summary disagrees with raw observations")
        intent[size] = {"manifest": manifest, "updates": len(steps), "supervisedTargets": int(steps[-1]["supervisedTargets"]),
                        "processWallSeconds": rec["wallSeconds"], "snapshots": snapshots, "selection": selection,
                        "capacityBefore": before, "capacityAfter": after}
    require(len({i["manifest"]["tokenScheduleSha256"] for i in intent.values()}) == 1, "intent token schedules differ")
    require(len({i["manifest"]["scheduleSha256"] for i in intent.values()}) == 1, "intent example schedules differ")
    wide = intent["wide"]
    eligible = int(wide["selection"]["devCorrect"]) >= 12 and int(wide["selection"]["devCorrect"]) >= int(wide["snapshots"][0]["score"]["correct"]) + 4
    no_signal = archive.json(V1 + "no-development-signal.json")
    require(not eligible and no_signal["status"] == intent_summary["status"] == "NO_DEVELOPMENT_SIGNAL"
            and no_signal["testEvaluated"] is False and intent_summary["testEvaluated"] is False, "development stop inconsistent")

    v2_paths = [p for p in archive.paths if p.startswith(EVIDENCE + "study-v2/")]
    v2_summary = archive.json(EVIDENCE + "study-v2/intent-summary.json")
    v2_selection = {}
    for size, arm in v2_summary["arms"].items():
        chosen = min(arm["scores"][1:], key=lambda s: (-int(s["correct"]), float(s["answerNll"]), int(s["step"])))
        require(arm["selection"]["step"] == chosen["step"] and arm["selection"]["devCorrect"] == chosen["correct"], "V2 summary internally inconsistent selection")
        v2_selection[size] = {"selected": arm["selection"], "scores": arm["scores"], "capacityReported": arm["capacity"]}
    regs = {}
    for version, relative in (("v1", "registration.json"), ("v2", "long-study/registration.json"), ("v3", "suffix-study/registration.json")):
        data = archive.json(RESEARCH + relative)
        for path, expected in data["files"].items():
            if path in archive.cache:
                require(digest(archive.read(path)) == expected, "reviewed registered source changed: " + path)
        regs[version] = {"sha256": digest(archive.read(RESEARCH + relative)), "jarSha256": data["jarSha256"],
                         "trainingProcessSeconds": data["trainingProcessSeconds"], "capacityProcessSeconds": data["capacityProcessSeconds"],
                         "testSha256ReportedOnly": data["files"][RESEARCH + "intent-data/test.sealed.tsv"]}
    require(len({r["testSha256ReportedOnly"] for r in regs.values()}) == 1, "versions register different final tests")
    v1_source = archive.read(RESEARCH + "LmIntentStudy.kt").decode()
    v2_source = archive.read(RESEARCH + "long-study/LmIntentLongStudy.kt").decode()
    v3_source = archive.read(RESEARCH + "suffix-study/LmIntentSuffixStudy.kt").decode()
    expected_v2 = v1_source
    for before, after in (("object LmIntentStudy", "object LmIntentLongStudy"),
                          ("val STEPS = 128", "val STEPS = 512"),
                          ("lm1-intent-20261104", "lm1-intent-long-20261104"),
                          ("< 540)", "< 840)"), ("listOf(32, 64, 128)", "listOf(128, 256, 512)")):
        require(expected_v2.count(before) == 1, "unexpected V1-to-V2 source match")
        expected_v2 = expected_v2.replace(before, after)
    require(expected_v2 == v2_source, "additional V1-to-V2 code differences")
    expected_v3 = v2_source
    for before, after in (("object LmIntentLongStudy", "object LmIntentSuffixStudy"),
                          ("lm1-intent-long-20261104", "lm1-intent-suffix-20261104"),
                          ("\\nAktion:", "\\n")):
        require(expected_v3.count(before) == 1, "unexpected V2-to-V3 source match")
        expected_v3 = expected_v3.replace(before, after)
    require(expected_v3 == v3_source, "additional V2-to-V3 code differences")
    require("(STEPS - 4)" in v1_source and "(STEPS - 4)" in v2_source, "cosine schedule denominator missing")
    tested_paths = [p for p in archive.paths if p.startswith(EVIDENCE) and ("/test-" in p or p.endswith("selection-freeze.json"))]
    require(not tested_paths, "pinned snapshot contains a newly published freeze/test requiring separate review")
    return {
        "status": "PUBLISHED_V1_EVIDENCE_CONSISTENT_V2_RAW_EVIDENCE_MISSING", "reviewedCommit": SNAPSHOT,
        "executedSourceCommitReported": RUN_SOURCE, "modelProcessesStarted": 0, "modelAndIntentDatasetBytesRead": 0,
        "scope": "TEXT_ONLY_INDEPENDENT_RECALCULATION_OF_PUBLISHED_EVIDENCE_NOT_TRAINING_REPLICATION",
        "diagnostic": diagnostic, "intentV1": {"arms": intent, "developmentEligible": eligible, "noSignal": no_signal},
        "intentV2": {"evidenceLevel": "PUBLISHED_SUMMARY_ONLY", "publishedPaths": v2_paths, "status": v2_summary["status"],
                     "testEvaluatedReported": v2_summary["testEvaluated"], "arms": v2_selection,
                     "missing": ["TRAIN_STEP and CASE raw logs", "training and capacity process receipts", "initial and post capacity raw logs", "selection artifact", "freeze/choice receipts"]},
        "intentV3": {"status": "REGISTERED_NO_PUBLISHED_STUDY_RESULTS_AT_REVIEWED_COMMIT",
                     "publishedStudyPaths": [p for p in archive.paths if p.startswith(EVIDENCE + "study-v3/")]},
        "registrations": regs, "registeredSourcesRehashed": sources, "registeredFilesIntentionallyNotRead": skip,
        "sourceComparison": {
            "v1ToV2ExactDiff": ["class name", "128 to 512 updates", "experiment name", "540 to 840 internal seconds", "32/64/128 to 128/256/512 snapshots"],
            "v2ToV3ExactDiff": ["class name", "experiment name", "prompt suffix from newline plus Aktion: to newline alone"],
            "cosineDenominator": "STEPS - 4", "sameStep128DoesNotMeanSameLearningRatePrefix": True,
            "responseMaskAndFreeGenerationCodeUnchangedAcrossThreeHarnesses": True,
        },
        "publishedFreezeOrTestPaths": tested_paths,
        "checkpointIntegrityLimit": "Identifiers and logged decoder validation cross-checked; checkpoint/JAR/tokenizer binary bytes not independently read or hashed.",
        "sourceInventory": dict(sorted(archive.inventory.items())),
    }


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
