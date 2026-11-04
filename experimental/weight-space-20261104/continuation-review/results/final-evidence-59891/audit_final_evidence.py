#!/usr/bin/env python3
"""Recompute published evaluation logs at one immutable commit; never run models.

Only Python's standard library and Git object reads are used.  Neither original
datasets, their generator, checkpoints, JARs nor tokenizer binaries are opened.
The prior audit JSON is hash-pinned evidence, not executable imported code.
"""

import argparse
import base64
from collections import Counter
from datetime import datetime, timedelta
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess


PIN = "59891cfad6dd015f47c187cc688ea94b0026ad7d"
PREVIOUS = "9ceffc26a2159c8dc726c1f13d2882119159c130"
SOURCE = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
BASE = "docs/evidence/lm1-training-recovery-20261104/"
EXPERIMENT = "experimental/lm1-training-recovery-20261104/"
HERE = Path(__file__).resolve().parent
PRIOR = HERE.parent
ROOT = next(p for p in HERE.parents if (p / ".git").exists())
PRIOR_HASHES = {
    "audit_published_v3.py": "31af55ca4ce3c4d282a8269c9f9b0d287f92a295ce9493a0441903d633c63c1e",
    "published-v3-results.json": "51342b48ac0288370df43f8b763873b878cbcfbdb702129eb246005ee4e7e5ac",
    "README.md": "eea810b52a83cb3a5d659b030a327f57c607799c014ccede8765fbda569d1ca1",
}
FREEZE_SHA = "6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4"
REGISTRATION_SHA = "04392b366746b1e6fca78eb66758e8c99e94b8392dbe013a02d2b416e2184728"
TEST_SHA = "cb75fb0be812116d8bbde95fbe5f82cf8c175d09ba0c49c51090c6cbb3d18a6a"
TRAIN_SHA = "a474d13be70162fb5c8452df43777f06681fec91ba1d4498ea10d453180a718b"
ARMS = ("S0", "W0", "S1", "W1")
INPUT_FIELDS = {"checkpointSha256", "testSha256", "parameters"}
CASE_FIELDS = {"split", "step", "id", "expected", "textBase64", "tokens", "stop",
               "correct", "answerNll", "eosNll", "firstEosProbability"}
SCORE_FIELDS = {"split", "step", "correct", "total", "answerNll", "eosNll",
                "prematureEos", "classCorrect", "classTotal",
                "meanFirstEosProbability", "missingEos", "generatedTokens"}
JVM_WARNING = "[0.001s][warning][perf,memops] Cannot use file /tmp/hsperfdata_agent/26 because it is locked by another process (errno = 11)"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


class Audit:
    def __init__(self):
        self.assertions = 0
        self.inventory = {}
        self.cache = {}
        self.allowed = set()

    def require(self, condition, detail):
        self.assertions += 1
        if not condition:
            raise AssertionError(detail)

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(ROOT), *args])

    def blob(self, path):
        self.require(path in self.allowed, "path outside explicit text inventory: " + path)
        self.require("/intent-data/" not in path and not path.endswith(
            ("make_intent_data.py", "development.txt", ".lm1", ".lmbp", ".jar", ".zip")),
            "excluded data or binary path: " + path)
        if path not in self.cache:
            raw = self.git("show", PIN + ":" + path)
            raw.decode("utf-8", errors="strict")
            self.cache[path] = raw
            self.inventory[path] = {
                "sha256": sha(raw), "bytes": len(raw),
                "gitBlobSha1": self.git("rev-parse", PIN + ":" + path).decode().strip(),
            }
        return self.cache[path]

    def obj(self, path):
        return json.loads(self.blob(path))

    def close(self, actual, expected, detail):
        self.require(math.isfinite(float(actual)) and math.isfinite(float(expected)),
                     detail + ": non-finite number")
        self.require(math.isclose(float(actual), float(expected), rel_tol=1e-12, abs_tol=1e-14),
                     detail + f": {actual!r} != {expected!r}")

    def records(self, path):
        result = []
        for number, line in enumerate(self.blob(path).decode().splitlines(), 1):
            if number == 1 and path == BASE + "study-v3/logs/test-W0.tsv" and line == JVM_WARNING:
                continue
            fields = line.split("\t")
            pairs = [field.split("=", 1) for field in fields[1:]]
            self.require(all(len(pair) == 2 for pair in pairs), f"{path}:{number}: bad field")
            row = dict(pairs)
            self.require(len(row) == len(pairs), f"{path}:{number}: duplicate key")
            self.require(fields[0] in {"TEST_INPUT", "CASE", "SCORE"},
                         f"{path}:{number}: unexpected record or training event")
            wanted = {"TEST_INPUT": INPUT_FIELDS, "CASE": CASE_FIELDS,
                      "SCORE": SCORE_FIELDS}[fields[0]]
            self.require(set(row) == wanted, f"{path}:{number}: unexpected schema")
            result.append((fields[0], row))
        return result

    def evaluate_log(self, label, arm, dataset, frozen, registration, prior_arm):
        log_path = BASE + "study-v3/logs/" + label + ".tsv"
        receipt_path = BASE + "study-v3/logs/" + label + ".receipt.json"
        rows = self.records(log_path)
        size = 48 if dataset == "TEST" else 128
        input_hash = TEST_SHA if dataset == "TEST" else TRAIN_SHA
        filename = "test.sealed.tsv" if dataset == "TEST" else "train.tsv"
        prefix = "test" if dataset == "TEST" else "train"
        self.require([kind for kind, _ in rows] == ["TEST_INPUT"] + ["CASE"] * size + ["SCORE"],
                     label + ": incomplete/reordered log")
        test_input, reported = rows[0][1], rows[-1][1]
        parameters = int(prior_arm["manifest"]["parameters"])
        self.require(test_input == {"checkpointSha256": frozen["checkpointSha256"],
                     "testSha256": input_hash, "parameters": str(parameters)},
                     label + ": checkpoint/input/parameter binding")
        receipt = self.obj(receipt_path)
        training_command = prior_arm["trainingReceipt"]["command"]
        command_prefix = training_command[:6]
        self.require(command_prefix[:4] == ["java", "-Xms64m", "-Xmx3g", "-cp"],
                     label + ": JVM bound differs")
        tokenizer_path = prior_arm["capacityAfter"]["model"]["tokenizerPath"]
        dataset_path = "/workspace/scratch/3112c86b5191/device-ai-training-research/" + EXPERIMENT + "intent-data/" + filename
        self.require(receipt["command"] == command_prefix + ["test", frozen["path"], tokenizer_path, dataset_path],
                     label + ": evaluation command differs from frozen inputs")
        for key, expected in {
            "label": label, "sourceCommit": SOURCE, "registrationSha256": REGISTRATION_SHA,
            "jarSha256": registration["jarSha256"], "logSha256": sha(self.blob(log_path)),
            "returncode": 0, "timeout": False, "timeoutSeconds": 900,
            "registeredBytesUnchanged": True,
        }.items():
            self.require(receipt[key] == expected, label + ": receipt " + key)
        self.require(type(receipt["returncode"]) is int and receipt["timeout"] is False
                     and receipt["registeredBytesUnchanged"] is True, label + ": receipt boolean types")
        self.require(0 < receipt["wallSeconds"] <= receipt["timeoutSeconds"], label + ": wall budget")
        self.require(datetime.fromisoformat(receipt["startedUtc"]).utcoffset() == timedelta(0),
                     label + ": UTC timestamp required")

        decisions, counts, totals, matrix = [], [0] * 4, [0] * 4, [[0] * 5 for _ in range(4)]
        for _, row in rows[1:-1]:
            self.require(row["split"] == "test" and int(row["step"]) == int(frozen["step"]),
                         label + ": scorer split or frozen step")
            self.require(row["expected"] in "0123" and len(row["expected"]) == 1,
                         label + ": invalid label")
            text = base64.b64decode(row["textBase64"], validate=True).decode("utf-8", errors="strict")
            tokens = int(row["tokens"])
            self.require(0 <= tokens <= 4 and row["stop"] in {"EOS", "MAX_TOKENS"},
                         label + ": invalid generation/stop")
            valid = text in {"0", "1", "2", "3"} and tokens == 1 and row["stop"] == "EOS"
            correct = valid and text == row["expected"]
            self.require(row["correct"] == str(correct).lower(), label + ": incorrect CASE verdict")
            numeric = {key: float(row[key]) for key in ("answerNll", "eosNll", "firstEosProbability")}
            self.require(all(math.isfinite(v) and v >= 0 for v in numeric.values())
                         and numeric["firstEosProbability"] <= 1, label + ": invalid numeric case value")
            label_id = int(row["expected"])
            totals[label_id] += 1
            counts[label_id] += correct
            matrix[label_id][int(text) if valid else 4] += 1
            decisions.append({
                "id": row["id"], "expectedLabelFromPublishedLog": row["expected"],
                "generatedText": text, "textBase64": row["textBase64"],
                "generatedTokens": tokens, "stop": row["stop"],
                "formatValid": valid, "correct": correct, **numeric,
                "firstEosProbabilityFloat32": struct.unpack("<f", struct.pack("<f", numeric["firstEosProbability"]))[0],
            })
        self.require([r["id"] for r in decisions] == [f"{prefix}-{i:04d}" for i in range(1, size + 1)],
                     label + ": missing, duplicate, or reordered case IDs")
        self.require(totals == [size // 4] * 4, label + ": class totals")
        recomputed = {
            "correct": sum(counts), "total": len(decisions), "classCorrect": counts,
            "classTotal": totals, "formatValid": sum(r["formatValid"] for r in decisions),
            "invalidFormat": sum(not r["formatValid"] for r in decisions),
            "prematureEos": sum(r["stop"] == "EOS" and r["generatedTokens"] == 0 for r in decisions),
            "missingEos": sum(r["stop"] != "EOS" for r in decisions),
            "generatedTokens": sum(r["generatedTokens"] for r in decisions),
            "answerNll": sum(r["answerNll"] for r in decisions) / size,
            "eosNll": sum(r["eosNll"] for r in decisions) / size,
            "meanFirstEosProbability": sum(r["firstEosProbabilityFloat32"] for r in decisions) / size,
        }
        self.require(reported["split"] == "test" and int(reported["step"]) == int(frozen["step"]),
                     label + ": SCORE split or step")
        for key in ("correct", "total", "prematureEos", "missingEos", "generatedTokens"):
            self.require(int(reported[key]) == recomputed[key], label + ": SCORE " + key)
        for key in ("classCorrect", "classTotal"):
            self.require([int(x) for x in reported[key].split(",")] == recomputed[key], label + ": SCORE " + key)
        for key in ("answerNll", "eosNll", "meanFirstEosProbability"):
            self.close(reported[key], recomputed[key], label + ": SCORE " + key)
        return {
            "logicalDataset": dataset, "rawScorerSplit": "test", "inputSha256": input_hash,
            "arm": arm, "frozen": frozen, "parameters": parameters,
            "float32ParameterStorageBytes": parameters * 4,
            "testInput": test_input, "receipt": receipt, "reportedScore": reported,
            "processWarnings": [JVM_WARNING] if self.blob(log_path).decode().startswith(JVM_WARNING + "\n") else [],
            "recomputedScore": recomputed, "confusionMatrixRows0To3Columns0To3Invalid": matrix,
            "outputCounts": dict(sorted(Counter(r["generatedText"] for r in decisions).items())),
            "stopCounts": dict(sorted(Counter(r["stop"] for r in decisions).items())),
            "tokenCountHistogram": dict(sorted(Counter(str(r["generatedTokens"]) for r in decisions).items())),
            "cases": decisions,
        }


def paired(first_name, second_name, arms):
    first, second = arms[first_name]["cases"], arms[second_name]["cases"]
    groups = {"bothCorrect": [], "firstOnlyCorrect": [], "secondOnlyCorrect": [], "bothIncorrect": []}
    for a, b in zip(first, second):
        name = ("bothCorrect" if b["correct"] else "firstOnlyCorrect") if a["correct"] else (
            "secondOnlyCorrect" if b["correct"] else "bothIncorrect")
        groups[name].append(a["id"])
    return {"first": first_name, "second": second_name,
            **{key: len(ids) for key, ids in groups.items()},
            "netFirstCorrect": len(groups["firstOnlyCorrect"]) - len(groups["secondOnlyCorrect"]),
            "firstOnlyIds": groups["firstOnlyCorrect"], "secondOnlyIds": groups["secondOnlyCorrect"]}


def run_audit():
    audit = Audit()
    previous_files = {}
    for name, expected in PRIOR_HASHES.items():
        raw = (PRIOR / name).read_bytes()
        audit.require(sha(raw) == expected, "previous audit changed: " + name)
        previous_files[name] = {"sha256": sha(raw), "bytes": len(raw)}
    previous = json.loads((PRIOR / "published-v3-results.json").read_bytes())
    audit.require(previous["reviewedCommit"] == PREVIOUS, "prior report commit")
    inventory = previous["sourceInventory"]
    changed_handoff = "docs/research/omega/LM1-TRAINING-RECOVERY-HANDOFF-20261104.md"
    audit.allowed.update(set(inventory) - {changed_handoff})
    new_files = [BASE + path for path in (
        "study-v3/intent-summary.json", "study-v3/training-fit-summary.json",
        "final-selection/publication-metadata.json", "final-case-audit.json", "RESULTS.md")]
    for name in [*("test-" + arm for arm in ARMS), "train-fit-S1", "train-fit-W1"]:
        new_files += [BASE + "study-v3/logs/" + name + suffix for suffix in (".tsv", ".receipt.json")]
    audit.allowed.update(new_files)
    audit.require(audit.git("rev-parse", PIN + "^1").decode().strip() == PREVIOUS, "not direct successor of freeze")
    changes = audit.git("diff-tree", "--no-commit-id", "--name-status", "-r", PIN).decode().splitlines()
    audit.require(all(line.split("\t")[-1].startswith("docs/") for line in changes),
                  "final evidence commit contains non-document changes")
    immutable = {}
    for path in sorted(audit.allowed - set(new_files)):
        raw = audit.blob(path)
        audit.require(sha(raw) == inventory[path]["sha256"] and len(raw) == inventory[path]["bytes"],
                      "previously audited immutable evidence changed: " + path)
        immutable[path] = sha(raw)

    reg_path = EXPERIMENT + "suffix-study/registration.json"
    reg = audit.obj(reg_path)
    audit.require(sha(audit.blob(reg_path)) == REGISTRATION_SHA, "registration SHA")
    registered_sources, intentionally_unread = {}, {}
    for path, expected in sorted(reg["files"].items()):
        if path not in audit.allowed:
            audit.require("/intent-data/" in path or path.endswith(("make_intent_data.py", "development.txt")),
                          "unexpected unverified registration path: " + path)
            intentionally_unread[path] = expected
            continue
        audit.require(sha(audit.blob(path)) == expected, "registered source hash: " + path)
        audit.require(audit.git("rev-parse", SOURCE + ":" + path).decode().strip() == audit.inventory[path]["gitBlobSha1"],
                      "registered source differs from executed source commit: " + path)
        registered_sources[path] = expected
    audit.require(reg["files"][EXPERIMENT + "intent-data/test.sealed.tsv"] == TEST_SHA
                  and reg["files"][EXPERIMENT + "intent-data/train.tsv"] == TRAIN_SHA,
                  "registered logical dataset identities")

    freeze_path = BASE + "final-selection/selection-freeze.json"
    freeze = audit.obj(freeze_path)
    audit.require(sha(audit.blob(freeze_path)) == FREEZE_SHA and freeze == previous["studyChoice"]["freeze"],
                  "pre-test freeze differs")
    choice = audit.obj(BASE + "final-selection/development-study-choice.json")
    audit.require(choice == previous["studyChoice"]["choice"] and choice["selectedStudy"] == "v3"
                  and choice["selectedFreezePayload"] == freeze, "development-only choice differs")
    audit.require(freeze["sourceCommit"] == SOURCE and freeze["registrationSha256"] == REGISTRATION_SHA
                  and freeze["testSha256"] == TEST_SHA, "freeze registration/source/test binding")

    scorer = audit.blob(EXPERIMENT + "suffix-study/LmIntentSuffixStudy.kt").decode()
    expected_source = [
        'result.text == label && result.newTokens == 1 && result.stop == LmRuntime.Stop.EOS',
        'runtime.generate(LmRuntime.Request(prompt(e), maxNewTokens = 4, maxMillis = 30000))',
        'check(!result.promptTruncated && result.stop !in setOf(LmRuntime.Stop.TIME, LmRuntime.Stop.CANCELLED, LmRuntime.Stop.CONTEXT))',
        'when (args[0]) { "train" -> train(args); "test" -> test(args); else -> error("train or test required") }',
        'check(sha(read(checkpoint, LmRuntime.MAX_CHECKPOINT_BYTES)) == sha(raw))',
    ]
    audit.require(all(s in scorer for s in expected_source), "frozen scorer behavior binding")
    test_body = scorer.split("    private fun test(args: Array<String>) {", 1)[1].split("    @JvmStatic", 1)[0]
    audit.require("evaluate(runtime.model, runtime.tokenizer, rows(bytes)" in test_body
                  and "require(sha(tokenizer) == TOKENIZER)" in test_body
                  and all(s not in test_body for s in ("train(", "LmAdamW", ".update(", "Files.write", "save(")),
                  "test dispatch contains no optimizer/write operation")
    protocol = audit.blob(EXPERIMENT + "suffix-study/SUFFIX_PROTOCOL.md").decode()
    audit.require(all(s in protocol for s in ("36/48", "8/12", "at least eight more", "at least four more",
                  "all 128 known training examples", "does not select a model")), "unchanged predeclared criteria")

    final, train_fit = {}, {}
    for arm in ARMS:
        final[arm] = audit.evaluate_log("test-" + arm, arm, "TEST", freeze["arms"][arm], reg,
                                       previous["arms"]["small" if arm[0] == "S" else "wide"])
    for arm in ("S1", "W1"):
        train_fit[arm] = audit.evaluate_log("train-fit-" + arm, arm, "TRAIN", freeze["arms"][arm], reg,
                                           previous["arms"]["small" if arm[0] == "S" else "wide"])
    for dataset, arms in (("TEST", final), ("TRAIN", train_fit)):
        reference = [(r["id"], r["expectedLabelFromPublishedLog"]) for r in next(iter(arms.values()))["cases"]]
        audit.require(all([(r["id"], r["expectedLabelFromPublishedLog"]) for r in arm["cases"]] == reference
                          for arm in arms.values()), dataset + ": aligned case identities and labels")

    summary = audit.obj(BASE + "study-v3/intent-summary.json")
    fit_summary = audit.obj(BASE + "study-v3/training-fit-summary.json")
    audit.require(summary["selectionSha256"] == fit_summary["selectionSha256"] == FREEZE_SHA,
                  "summary freeze bindings")
    audit.require(summary["scores"] == {a: d["reportedScore"] for a, d in final.items()}, "final summary scores")
    audit.require(fit_summary["scores"] == {a: d["reportedScore"] for a, d in train_fit.items()}, "TRAIN fit scores")
    for key, expected in {"scope": "POST_SELECTION_TRAIN_FIT_ONLY", "dataset": "TRAIN", "datasetSha256": TRAIN_SHA,
                          "sourceCommit": SOURCE, "affectsSelection": False,
                          "independentGeneralizationEvidence": False}.items():
        audit.require(fit_summary[key] == expected, "TRAIN fit separation: " + key)
    criteria = {
        "wideAtLeast36Of48": final["W1"]["recomputedScore"]["correct"] >= 36,
        "wideEachClassAtLeast8Of12": min(final["W1"]["recomputedScore"]["classCorrect"]) >= 8,
        "wideAtLeast8MoreThanS0": final["W1"]["recomputedScore"]["correct"] - final["S0"]["recomputedScore"]["correct"] >= 8,
        "wideAtLeast8MoreThanW0": final["W1"]["recomputedScore"]["correct"] - final["W0"]["recomputedScore"]["correct"] >= 8,
    }
    learning = all(criteria.values())
    advantage = final["W1"]["recomputedScore"]["correct"] - final["S1"]["recomputedScore"]["correct"] >= 4
    audit.require(summary["limitedBenchmarkLearningCriterionPassed"] is learning
                  and summary["widerSelectedCheckpointHasFourCaseAdvantage"] is advantage,
                  "final criterion conclusions")
    audit.require(summary["generalIntelligenceClaim"] is False and summary["modelPromotion"] is False
                  and summary["androidValidation"] == "NOT_RUN", "summary limitations")

    metadata = audit.obj(BASE + "final-selection/publication-metadata.json")
    commit_fields = audit.git("show", "-s", "--format=%H%n%T%n%aI%n%cI%n%P%n%s", PREVIOUS).decode().splitlines()
    # Raw headers preserve actual parents even when this object is a shallow boundary.
    commit_headers = audit.git("cat-file", "commit", PREVIOUS).decode().split("\n\n", 1)[0].splitlines()
    actual_parents = [line.split(" ", 1)[1] for line in commit_headers if line.startswith("parent ")]
    audit.require(metadata["commitSha"] == commit_fields[0] == PREVIOUS and metadata["treeSha"] == commit_fields[1]
                  and metadata["parents"] == actual_parents and metadata["message"] == commit_fields[5],
                  "publication metadata versus immutable Git object")
    commit_time = datetime.fromisoformat(commit_fields[3])
    audit.require(datetime.fromisoformat(metadata["committerDate"].replace("Z", "+00:00")) == commit_time,
                  "publication commit timestamp")
    audit.require(metadata["commitSigned"] is False and not any(line.startswith("gpgsig ") for line in commit_headers),
                  "unsigned timestamp qualification")
    timeline = []
    for arm in list(final.values()) + list(train_fit.values()):
        receipt = arm["receipt"]
        start = datetime.fromisoformat(receipt["startedUtc"])
        finish = start + timedelta(seconds=receipt["wallSeconds"])
        audit.require(start > commit_time, receipt["label"] + ": receipt predates publication metadata")
        if timeline:
            audit.require(start >= datetime.fromisoformat(timeline[-1]["endUtcDerived"]), "overlapping/reordered receipt sequence")
        timeline.append({"label": receipt["label"], "startedUtc": start.isoformat(),
                         "endUtcDerived": finish.isoformat(), "wallSeconds": receipt["wallSeconds"]})
    first = timeline[0]["startedUtc"]
    audit.require(metadata["firstFinalTestStartedUtc"] == first and metadata["commitDatePrecedesTest"] is True,
                  "publication first-test metadata")

    public_audit = audit.obj(BASE + "final-case-audit.json")
    comparisons = [paired(a, b, final) for a, b in (("W1", "S1"), ("S1", "S0"), ("W1", "W0"),
                                                   ("W1", "S0"), ("S1", "W0"), ("S0", "W0"))]
    audit.require(public_audit["pairedComparisons"] == comparisons, "published paired comparisons")
    audit.require(public_audit["primaryWideCriteria"] == criteria
                  and public_audit["primaryWideCriterionPassed"] is learning
                  and public_audit["widerSelectedCheckpointHasFourCaseAdvantage"] is advantage,
                  "published audit learning criteria")
    audit.require(public_audit["finalCaseCount"] == len(public_audit["caseAudit"]) == 192,
                  "published final audit coverage")
    indexed = {(c["arm"], c["id"]): c for c in public_audit["caseAudit"]}
    audit.require(len(indexed) == 192, "published audit duplicated case")
    rounding = {}
    for arm, result in final.items():
        published_arm = public_audit["arms"][arm]
        audit.require(published_arm["frozen"] == result["frozen"]
                      and published_arm["receipt"] == result["receipt"]
                      and published_arm["reportedScore"] == result["reportedScore"]
                      and published_arm["confusionMatrix4x5"]["counts"] == result["confusionMatrixRows0To3Columns0To3Invalid"],
                      arm + ": published audit arm bindings")
        for key, value in result["recomputedScore"].items():
            if key == "meanFirstEosProbability":
                printed_mean = sum(r["firstEosProbability"] for r in result["cases"]) / 48
                audit.close(published_arm["recomputedScore"][key], printed_mean, arm + ": published decimal probability mean")
                rounding[arm] = {"publishedAuditDecimalMean": published_arm["recomputedScore"][key],
                                 "float32RestoredMean": value,
                                 "absoluteDifference": abs(published_arm["recomputedScore"][key] - value)}
            elif isinstance(value, float):
                audit.close(published_arm["recomputedScore"][key], value, arm + ": published audit " + key)
            else:
                audit.require(published_arm["recomputedScore"][key] == value, arm + ": published audit " + key)
        for case in result["cases"]:
            p = indexed[arm, case["id"]]
            audit.require(p["expectedLabel"] == p["loggedExpectedLabel"] == case["expectedLabelFromPublishedLog"],
                          arm + ": published audit label")
            for key in ("generatedText", "generatedTokens", "stop", "formatValid", "answerNll", "eosNll", "firstEosProbability"):
                audit.require(p[key] == case[key], arm + ": published audit case " + key)
            audit.require(p["recomputedCorrect"] == p["loggedCorrect"] == case["correct"], arm + ": public case decision")
    outputs_identical = all((a["generatedText"], a["generatedTokens"], a["stop"]) ==
                            (b["generatedText"], b["generatedTokens"], b["stop"])
                            for a, b in zip(final["S0"]["cases"], final["W0"]["cases"]))
    audit.require(public_audit["untrainedControlOutputsIdenticalForAll48Cases"] is outputs_identical,
                  "initial-output parity in final logs")
    audit.blob(BASE + "RESULTS.md")

    capacity = {}
    for size in ("small", "wide"):
        capacity[size] = {phase: previous["arms"][size][phase] for phase in ("capacityBefore", "capacityAfter")}
    result = {
        "schemaVersion": 1,
        "status": "PASS_PUBLISHED_EVIDENCE_CONSISTENCY_WITH_FAILED_PREDECLARED_QUALITY_AND_CAPACITY",
        "reviewedCommit": PIN, "previousReviewedCommit": PREVIOUS, "executedSourceCommitReported": SOURCE,
        "evidenceCommitChanges": changes, "frozenPreviousFiles": previous_files,
        "unchangedPreviouslyAuditedEvidence": immutable,
        "updatedPreviousDocumentNotUsedForBaseline": changed_handoff,
        "registeredSourceFilesRehashedAndMatchedToSourceCommit": registered_sources,
        "registeredFilesIntentionallyNotRead": intentionally_unread,
        "sourceInventory": dict(sorted(audit.inventory.items())),
        "binding": {"registrationSha256": REGISTRATION_SHA, "selectionSha256": FREEZE_SHA,
                    "jarSha256Reported": reg["jarSha256"], "stdlibSha256Reported": reg["stdlibSha256"],
                    "tokenizerSha256Registered": reg["inputs"]["lm1-tokenizer.lmbp"],
                    "originalCheckpointSha256Registered": reg["inputs"]["lm1-baseline.lm1"],
                    "testFunctionBodySha256": sha(test_body.encode()),
                    "frozenArms": freeze["arms"]},
        "finalTest": final, "trainingFit": train_fit, "pairedFinalComparisons": comparisons,
        "primaryWideCriteria": criteria, "limitedBenchmarkLearningCriterionPassed": learning,
        "widerSelectedCheckpointHasFourCaseAdvantage": advantage,
        "untrainedControlOutputsIdentical": outputs_identical,
        "capacityFromUnchangedPreviouslyAuditedLogs": capacity,
        "publicationChronology": {"metadata": metadata, "receiptTimeline": timeline,
                                  "reportedCommitDateLeadSeconds": (datetime.fromisoformat(first) - commit_time).total_seconds(),
                                  "independentCryptographicTimestampAttestation": False},
        "publishedAuditDecimalFloatMeanDifference": rounding,
        "counts": {"finalArmCasesRecomputed": 192, "knownTrainingArmCasesRecomputed": 256,
                   "evaluationReceiptsChecked": 6, "modelProcessesExecutedByThisAudit": 0,
                   "newOptimizerUpdatesRepresentedByTheseSixLogs": 0},
        "holdoutState": "CONSUMED: published final predictions read for acceptance only",
        "actualDatasetFilesOrGeneratorRead": False, "binaryModelJarTokenizerBytesRead": False,
        "modelPromotion": False, "selectionChanged": False, "newTrainingAuthorized": False,
        "limitations": [
            "Source-bound published logs were recomputed; model execution was neither rerun nor independently attested.",
            "Expected labels and IDs were checked across published logs, not against the original dataset file; prompt hashes and train/test disjointness were not independently recomputed.",
            "Reported checkpoint/JAR/tokenizer identities are text bindings; this audit does not hash binary artifacts.",
            "Unsigned commit dates and process receipts are consistent with freeze-before-test; they are not independent timestamp notarization.",
            "NLL means are recalculated from logged case losses, not recomputed from model distributions.",
            "The registered 48-case test comprises 16 template families; no independent-draw significance claim is made.",
            "Train-fit uses scorer mode test with TRAIN path and hash; these 256 judgments are not additional final-test cases or optimizer updates.",
            "The historical freeze lacks the later repaired bound post-capacity evidence; this follow-up does not retroactively approve that controller.",
            "Capacity results are inherited only after rehashing their unchanged raw logs; no new resource benchmark or Android validation was run.",
        ],
    }
    for name, expected in PRIOR_HASHES.items():
        audit.require(sha((PRIOR / name).read_bytes()) == expected, "prior audit mutated during read-only audit")
    result["passedInternalAssertions"] = audit.assertions
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Write JSON under this new audit directory; otherwise print JSON")
    args = parser.parse_args()
    output = args.output.resolve() if args.output else None
    if output and (output.parent != HERE or output.name == Path(__file__).name):
        parser.error("output must be a different file directly inside final-evidence-59891")
    result = run_audit()
    text = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=False, allow_nan=False) + "\n"
    if output:
        output.write_text(text, encoding="utf-8")
        print(json.dumps({"status": result["status"], "output": str(output.relative_to(ROOT)),
                          "sha256": sha(text.encode()), "counts": result["counts"],
                          "passedInternalAssertions": result["passedInternalAssertions"]}))
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
