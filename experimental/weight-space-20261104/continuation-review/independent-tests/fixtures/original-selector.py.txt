#!/usr/bin/env python3
"""Choose one complete four-arm study using Dev only; never evaluate a model."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path


BASE = Path(__file__).resolve().parent.parent
TEST_KEY = "experimental/lm1-training-recovery-20261104/intent-data/test.sealed.tsv"


def controller(directory: str):
    spec = importlib.util.spec_from_file_location("controller_" + directory.replace("-", "_"),
                                                BASE / directory / "run_registered_study.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def audit_post_capacity(module, study, size: str, selected: dict) -> None:
    label = "intent-" + size + "-capacity-after"
    log = study.logs / (label + ".tsv")
    receipt = json.loads((study.logs / (label + ".receipt.json")).read_text())
    if (receipt["timeout"] or not receipt["registeredBytesUnchanged"]
            or receipt["returncode"] not in (0, 1) or receipt["logSha256"] != module.sha(log)
            or receipt["registrationSha256"] != study.registration_sha
            or receipt["sourceCommit"] != study.args.source_commit
            or receipt["jarSha256"] != study.registration["jarSha256"]):
        raise RuntimeError("POST_CAPACITY_RECEIPT_INVALID")
    result = module.rows(log, "CAPACITY_RESULT")
    if (len(result) != 1 or result[0]["checkpointSha256"] != selected["checkpointSha256"]
            or result[0]["pass"] not in ("true", "false")
            or (receipt["returncode"] == 0) != (result[0]["pass"] == "true")):
        raise RuntimeError("POST_CAPACITY_RESULT_INVALID")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for version in ("v2", "v3"):
        parser.add_argument("--" + version + "-output", type=Path, required=True)
        parser.add_argument("--" + version + "-jar", type=Path, required=True)
        parser.add_argument("--" + version + "-source", required=True)
    parser.add_argument("--inputs", type=Path, required=True)
    parser.add_argument("--stdlib", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    candidates = {}
    eligible = {}
    test_hashes = set()
    schedules = {}
    for version, directory in (("v2", "long-study"), ("v3", "suffix-study")):
        module = controller(directory)
        output = getattr(args, version + "_output")
        if not output.is_dir() or any((output / "logs").glob("test-*.tsv")):
            raise RuntimeError("STUDY_MISSING_OR_TEST_ALREADY_EVALUATED: " + version)
        study = module.Study(argparse.Namespace(output=output, jar=getattr(args, version + "_jar"),
                            stdlib=args.stdlib, inputs=args.inputs,
                            source_commit=getattr(args, version + "_source")))
        test_hashes.add(study.registration["files"][TEST_KEY])
        # Identity failures abort the choice rather than being relabelled as
        # ordinary model-quality failures.
        study.verify()
        selections = {size: study.selection(size) for size in ("small", "wide")}
        manifests = []
        for size in ("small", "wide"):
            audit_post_capacity(module, study, size, selections[size])
            log = study.logs / ("intent-" + size + ".tsv")
            manifest = module.rows(log, "MANIFEST")
            steps = module.rows(log, "TRAIN_STEP")
            if (len(manifest) != 1 or manifest[0]["steps"] != "512" or manifest[0]["batch"] != "4"
                    or [int(step["step"]) for step in steps] != list(range(1, 513))
                    or steps[-1]["supervisedTargets"] != "4096"):
                raise RuntimeError("INCOMPLETE_COMPARISON_TRAINING")
            manifests.append(manifest[0])
        if any(manifests[0][key] != manifests[1][key] for key in ("scheduleSha256", "tokenScheduleSha256")):
            raise RuntimeError("UNEQUAL_WITHIN_STUDY_SCHEDULES")
        schedules[version] = {key: manifests[0][key] for key in ("scheduleSha256", "tokenScheduleSha256")}
        try:
            evidence = study.freeze_evidence()
        except RuntimeError as exc:
            if str(exc) != "NO_DEVELOPMENT_SIGNAL":
                raise
            candidates[version] = {"status": "NO_DEVELOPMENT_SIGNAL", "selected": selections,
                                   "sourceCommit": study.args.source_commit,
                                   "registrationSha256": study.registration_sha}
        else:
            candidates[version] = {"status": "ELIGIBLE", "freezePayload": evidence}
            eligible[version] = evidence
    if len(test_hashes) != 1:
        raise RuntimeError("DIFFERENT_FINAL_TEST_IDENTITIES")
    if (schedules["v2"]["scheduleSha256"] != schedules["v3"]["scheduleSha256"]
            or schedules["v2"]["tokenScheduleSha256"] == schedules["v3"]["tokenScheduleSha256"]):
        raise RuntimeError("WRAPPER_ABLATION_SCHEDULE_MISMATCH")
    receipt = {"status": "NO_ELIGIBLE_STUDY", "testEvaluated": False,
               "rule": "Wide Dev exact descending, answer NLL ascending, then retain v2",
               "candidates": candidates, "schedules": schedules, "testSha256": next(iter(test_hashes))}
    if eligible:
        chosen = min(eligible, key=lambda name: (-int(eligible[name]["arms"]["W1"]["devCorrect"]),
                     float(eligible[name]["arms"]["W1"]["devAnswerNll"]), 0 if name == "v2" else 1))
        receipt.update(status="DEVELOPMENT_CHOICE_BEFORE_TEST", selectedStudy=chosen,
                       selectedFreezePayload=eligible[chosen])
    with args.receipt.open("x", encoding="utf-8") as stream:
        json.dump(receipt, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
    print(receipt["status"], receipt.get("selectedStudy", "none"))


if __name__ == "__main__":
    main()
