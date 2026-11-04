#!/usr/bin/env python3
"""Verify the actual parity rejection, restoration, and blocked replay."""
import argparse
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tempfile
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
REG_SHA = "0b9b97874c42da9ee1f0c4f4f113790f6c50e960612a961dcac579b42d8daa78"
ARCHIVE_SHA = "428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inventory(root):
    return {str(p.relative_to(root)): digest(p) for p in sorted(root.rglob("*")) if p.is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("run", "archive", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    run = args.run.resolve(strict=True)
    require(digest(run / "registration.json") == REG_SHA, "registration changed")
    reg = json.loads((run / "registration.json").read_text())
    for name, expected in reg["sourceHashes"].items():
        require(digest(HERE / name) == expected, "source changed")
    receipt = json.loads((run / "receipt.json").read_text())
    require(receipt["status"] == "FAIL" and receipt["exitCode"] == 1 and receipt["timedOut"] is False, "unexpected process outcome")
    require(receipt["registrationSha256"] == REG_SHA and 0 < receipt["elapsedSeconds"] < 120
            and 0 < receipt["maxRssKiB"] < 3145728, "scope or resource failure")
    require(digest(run / "stdout.tsv") == receipt["stdoutSha256"] and digest(run / "stderr.txt") == receipt["stderrSha256"], "receipt mismatch")
    require("opened-path parity gate failed" in (run / "stderr.txt").read_text(), "wrong exception")
    data = []
    for line in (run / "stdout.tsv").read_text().splitlines():
        parts = line.split("\t")
        data.append({"kind": parts[0], **dict(p.split("=", 1) for p in parts[1:])})
    require([r["kind"] for r in data] == ["START"] + ["BASE_PARITY"] * 12 + ["OPENED"] + ["OPEN_PARITY"] * 3 + ["RESTORED"], "unexpected execution trace")
    require(all(r["pass"] == r["deepWideBitwiseParity"] == "true" and float(r["maximumProbabilityDelta"]) <= 1e-5 for r in data if r["kind"] == "BASE_PARITY"), "initial baseline gate")
    attempts = [r for r in data if r["kind"] == "OPEN_PARITY"]
    require([r["probe"] for r in attempts] == ["general-next", "general-note", "general-tool"]
            and [r["pass"] for r in attempts] == ["true", "true", "false"], "failed-probe order")
    require(all(float(r["maximumProbabilityDelta"]) <= 1e-5 for r in attempts[:2])
            and float(attempts[2]["maximumProbabilityDelta"]) > 1e-5, "rejected value does not cross bound")
    restored = data[-1]
    require(restored["pass"] == "true" and restored["completedLossProbes"] == "0"
            and restored["initialParameterIdentity"] == restored["finalParameterIdentity"] == data[0]["initialParameterIdentity"]
            == "d6d3d64b01a12fe8d0e8c81527aff276bfefb776f09c2ed37f7f6eb21fd24640", "restoration proof failed")
    command = receipt["command"]
    jars = command[command.index("-cp") + 1].split(":")
    paths = {"harness": Path(jars[0]), "runtime": Path(jars[1]), "stdlib": Path(jars[2]),
             "models": Path(command[5]), "tokenizer": Path(command[6]), "probes": Path(command[7])}
    actual = {key: digest(paths[key]) for key in ("harness", "runtime", "stdlib", "tokenizer", "probes")}
    for key, value in actual.items():
        require(value == reg[key + "Sha256"], "actual input changed")
    actual["modelHashes"] = {name: digest(paths["models"] / (name + ".lm1")) for name in ("S0", "W0")}
    require(actual["modelHashes"] == reg["models"] and digest(args.archive) == ARCHIVE_SHA, "original model/archive changed")
    spec = importlib.util.spec_from_file_location("failed_attempt_controller", HERE / "run_open_path.py")
    controller = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(controller)
    checks = []

    def rejected(label, root):
        before = inventory(root)
        reason = None
        with patch.object(controller.C, "launch", side_effect=AssertionError("failed attempt restarted")) as launch:
            with contextlib.redirect_stdout(io.StringIO()):
                try:
                    controller.execute(HERE / "open-path-registration.json", REG_SHA, paths, root)
                except ValueError as error:
                    reason = str(error)
        require(reason is not None and launch.call_count == 0 and inventory(root) == before, "replay rejection failed")
        checks.append({"case": label, "status": "PASS", "jvmLaunches": 0, "persistentFilesUnchanged": True, "reason": reason})

    rejected("actual_failed_attempt_cannot_resume", run)
    with tempfile.TemporaryDirectory(prefix="open-path-rejection-") as temporary:
        copy = Path(temporary) / "run"
        shutil.copytree(run, copy)
        synthetic = dict(receipt, status="PASS", exitCode=0)
        (copy / "receipt.json").write_bytes(controller.C.encoded(synthetic))
        rejected("relabelled_receipt_still_fails_raw_gate", copy)
        (copy / "receipt.json").unlink()
        rejected("deleted_receipt_cannot_restart_partial_attempt", copy)
    result = {"status": "PARITY_REJECTION_AND_EXACT_RESTORATION_VERIFIED", "registrationSha256": REG_SHA,
              "checkerSha256": digest(Path(__file__)), "passedInitialParityCases": 12,
              "openedParityCases": attempts, "remainingOpenedParityCases": "NOT_RUN_9",
              "backwardPasses": 0, "plannedLossProbes": "NOT_RUN_4", "optimizerUpdates": 0,
              "restoredParameterIdentity": restored["finalParameterIdentity"], "originalInputIdentities": actual,
              "originalArchiveSha256": ARCHIVE_SHA, "replayChecks": checks, "jvmLaunchesDuringVerification": 0,
              "processSeconds": receipt["elapsedSeconds"], "maxRssKiB": receipt["maxRssKiB"],
              "acceptedResearchState": "M5_UNCHANGED", "newModelExperimentsPermittedThisRound": False}
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({"status": result["status"], "backwardPasses": 0, "replayChecks": len(checks), "newModelExperimentsPermittedThisRound": False}))


if __name__ == "__main__":
    main()
